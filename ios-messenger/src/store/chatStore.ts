// ============================================================
// WorldMates Messenger — Chat Zustand Store
//
// Full-featured port of Android ChatStore / PresenceTracker.
//
// Features:
//   • Chat list + per-conversation message arrays
//   • Optimistic send + offline queue (flush on reconnect)
//   • Typing auto-reset 6 s (mirrors Android)
//   • Recording indicator + auto-clear 8 s
//   • Reactions, pinned messages
//   • Group events: messages, typing, edits, deletes, history clear
//   • Signal E2EE async decryption (cipherVersion=3)
//   • Bulk seen receipts (can_seen=1)
//   • lastSeen via presenceService
//   • presenceService wired for online/offline
// ============================================================

import { create } from 'zustand';
import { immer } from 'zustand/middleware/immer';
import * as chatApi from '../api/chatApi';
import { socketService } from '../services/socketService';
import { presenceService } from '../services/presenceService';
import type { Chat, Message } from '../api/types';
import {
  SOCKET_EVENT_NEW_MESSAGE,
  SOCKET_EVENT_PRIVATE_MESSAGE,
  SOCKET_EVENT_TYPING,
  SOCKET_EVENT_TYPING_DONE,
  SOCKET_EVENT_RECORDING,
  SOCKET_EVENT_USER_ONLINE,
  SOCKET_EVENT_USER_OFFLINE,
  SOCKET_EVENT_MESSAGE_SEEN,
  SOCKET_EVENT_LAST_SEEN,
  SOCKET_EVENT_MESSAGE_EDITED,
  SOCKET_EVENT_MESSAGE_DELETED,
  SOCKET_EVENT_MESSAGE_REACTION,
  SOCKET_EVENT_MESSAGE_PINNED,
  SOCKET_EVENT_GROUP_MESSAGE,
  SOCKET_EVENT_GROUP_TYPING,
  SOCKET_EVENT_GROUP_TYPING_DONE,
  SOCKET_EVENT_GROUP_MESSAGE_EDITED,
  SOCKET_EVENT_GROUP_MESSAGE_DELETED,
  SOCKET_EVENT_GROUP_HISTORY_CLEARED,
  SOCKET_EVENT_PRIVATE_HISTORY_CLEARED,
  USER_ACTION_RECORDING,
  SOCKET_EVENT_USER_ACTION,
  SOCKET_EVENT_GROUP_USER_ACTION,
  CIPHER_VERSION_SIGNAL,
} from '../constants/api';

const TYPING_AUTO_RESET_MS = 6_000;
const RECORDING_AUTO_RESET_MS = 8_000;

// ─────────────────────────────────────────────────────────────
// OFFLINE QUEUE
// ─────────────────────────────────────────────────────────────

interface QueuedMessage {
  tempId: string;
  toId: string;
  text: string;
  replyToId?: string;
  groupId?: string;
}

// ─────────────────────────────────────────────────────────────
// STATE SHAPE
// ─────────────────────────────────────────────────────────────

interface ChatState {
  chats: Chat[];
  activeChat: Chat | null;
  /** Messages keyed by peer userId (1-on-1) or groupId */
  messages: Record<string, Message[]>;
  isLoadingChats: boolean;
  isLoadingMessages: boolean;
  hasMoreMessages: Record<string, boolean>;
  /** chatId → array of typing userIds */
  typingUsers: Record<string, string[]>;
  /** chatId → array of recording userIds */
  recordingUsers: Record<string, string[]>;
  /** chatId → pinned message */
  pinnedMessages: Record<string, Message>;
  /** Messages that failed to send while offline */
  offlineQueue: QueuedMessage[];

  // ── Actions ─────────────────────────────────────────────────
  loadChats(): Promise<void>;
  loadMessages(userId: string): Promise<void>;
  loadMoreMessages(userId: string): Promise<void>;
  sendMessage(toId: string, text: string, replyToId?: string, groupId?: string): Promise<void>;
  receiveMessage(msg: Message): void;
  editMessage(msgId: string, newText: string): Promise<void>;
  deleteMessage(msgId: string, chatId: string): Promise<void>;
  reactToMessage(msgId: string, emoji: string, chatId: string): Promise<void>;
  pinMessage(msgId: string, chatId: string): Promise<void>;
  setTyping(chatId: string, userId: string, isTyping: boolean): void;
  setRecording(chatId: string, userId: string, isRecording: boolean): void;
  markChatRead(chatId: string): void;
  setActiveChat(chat: Chat | null): void;
  updateChatLastMessage(chatId: string, msg: Message): void;
  archiveChat(userId: string, archive: boolean): Promise<void>;
  muteChat(userId: string, mute: boolean): Promise<void>;
  pinChat(userId: string, pin: boolean): Promise<void>;
  deleteConversation(userId: string): Promise<void>;
  clearHistory(chatId: string, isGroup?: boolean): void;
  flushOfflineQueue(): Promise<void>;
  initSocketListeners(): void;
}

// ─────────────────────────────────────────────────────────────
// TIMER MAPS (module-level to avoid Zustand serialisation issues)
// ─────────────────────────────────────────────────────────────

const _typingTimers: Record<string, ReturnType<typeof setTimeout>> = {};
const _recordingTimers: Record<string, ReturnType<typeof setTimeout>> = {};

// ─────────────────────────────────────────────────────────────
// HELPERS
// ─────────────────────────────────────────────────────────────

function chatKey(msg: Message): string {
  return msg.groupId ?? msg.toId;
}

function upsertMessage(arr: Message[], incoming: Message): Message[] {
  const idx = arr.findIndex((m) => m.id === incoming.id);
  if (idx === -1) return [...arr, incoming];
  const copy = [...arr];
  copy[idx] = incoming;
  return copy;
}

/** Attempt Signal decryption; on failure return original message unchanged. */
async function tryDecryptSignal(msg: Message): Promise<Message> {
  if (
    msg.cipherVersion !== CIPHER_VERSION_SIGNAL ||
    !msg.text ||
    !msg.signalHeader
  ) {
    return msg;
  }
  try {
    const { signalEncryptionService } = await import('../crypto/signal');
    const plain = await signalEncryptionService.decryptIncoming(
      msg.fromId,
      msg.text,
      msg.iv ?? '',
      msg.tag ?? '',
      msg.signalHeader,
    );
    return { ...msg, decryptedText: plain };
  } catch {
    return msg;
  }
}

/** Fire-and-forget: send bulk seen receipt for all unread messages. */
async function sendSeenReceipts(msgs: Message[], peerId: string): Promise<void> {
  const hasUnseen = msgs.some((m) => m.fromId !== 'me' && !m.isSeen);
  if (!hasUnseen) return;
  try {
    await chatApi.markSeen(peerId);
  } catch {
    // Non-fatal
  }
}

// ─────────────────────────────────────────────────────────────
// STORE
// ─────────────────────────────────────────────────────────────

export const useChatStore = create<ChatState>()(
  immer((set, get) => ({
    chats: [],
    activeChat: null,
    messages: {},
    isLoadingChats: false,
    isLoadingMessages: false,
    hasMoreMessages: {},
    typingUsers: {},
    recordingUsers: {},
    pinnedMessages: {},
    offlineQueue: [],

    // ── loadChats ──────────────────────────────────────────────
    loadChats: async () => {
      set((s) => { s.isLoadingChats = true; });
      try {
        const chats = await chatApi.getChatsList(1);
        set((s) => {
          s.chats = chats;
          s.isLoadingChats = false;
        });
      } catch {
        set((s) => { s.isLoadingChats = false; });
      }
    },

    // ── loadMessages ───────────────────────────────────────────
    loadMessages: async (userId) => {
      set((s) => { s.isLoadingMessages = true; });
      try {
        const msgs = await chatApi.getMessages(userId, 1);
        const decrypted = await Promise.all(msgs.map(tryDecryptSignal));
        set((s) => {
          s.messages[userId] = decrypted;
          s.hasMoreMessages[userId] = decrypted.length >= 30;
          s.isLoadingMessages = false;
        });
        sendSeenReceipts(decrypted, userId).catch(() => {});
      } catch {
        set((s) => { s.isLoadingMessages = false; });
      }
    },

    // ── loadMoreMessages ───────────────────────────────────────
    loadMoreMessages: async (userId) => {
      if (!get().hasMoreMessages[userId]) return;
      const existing = get().messages[userId] ?? [];
      const lastMsg = existing[0];
      if (!lastMsg) return;
      try {
        const older = await chatApi.loadMoreMessages(userId, lastMsg.id);
        const decrypted = await Promise.all(older.map(tryDecryptSignal));
        set((s) => {
          s.messages[userId] = [...decrypted, ...(s.messages[userId] ?? [])];
          s.hasMoreMessages[userId] = decrypted.length >= 30;
        });
      } catch {
        // Non-fatal
      }
    },

    // ── sendMessage ────────────────────────────────────────────
    sendMessage: async (toId, text, replyToId, groupId) => {
      const tempId = `tmp_${Date.now()}_${Math.random()}`;
      const key = groupId ?? toId;
      const tempMsg: Message = {
        id: tempId,
        fromId: 'me',
        toId,
        groupId,
        text,
        type: 'text',
        isEdited: false,
        isDeleted: false,
        isSeen: false,
        isPinned: false,
        replyToId,
        isLocalPending: true,
        createdAt: Date.now(),
      };

      set((s) => {
        s.messages[key] = [...(s.messages[key] ?? []), tempMsg];
      });

      if (!socketService.isConnected()) {
        set((s) => {
          s.offlineQueue.push({ tempId, toId, text, replyToId, groupId });
        });
        return;
      }

      try {
        const realMsg = groupId
          ? await chatApi.sendGroupMessage(groupId, text, replyToId)
          : await chatApi.sendMessage(toId, text, replyToId);
        set((s) => {
          const arr = s.messages[key] ?? [];
          const idx = arr.findIndex((m) => m.id === tempId);
          if (idx !== -1) arr[idx] = { ...realMsg, isLocalPending: false };
        });
        get().updateChatLastMessage(realMsg.groupId ?? realMsg.toId, realMsg);
      } catch {
        set((s) => {
          const arr = s.messages[key];
          if (!arr) return;
          const idx = arr.findIndex((m) => m.id === tempId);
          if (idx !== -1) arr[idx].isLocalPending = false;
          s.offlineQueue.push({ tempId, toId, text, replyToId, groupId });
        });
      }
    },

    // ── flushOfflineQueue ──────────────────────────────────────
    flushOfflineQueue: async () => {
      const queue = get().offlineQueue;
      if (!queue.length) return;
      set((s) => { s.offlineQueue = []; });
      for (const item of queue) {
        await get().sendMessage(item.toId, item.text, item.replyToId, item.groupId);
      }
    },

    // ── receiveMessage ─────────────────────────────────────────
    receiveMessage: (msg) => {
      const key = chatKey(msg);
      tryDecryptSignal(msg).then((decrypted) => {
        set((s) => {
          s.messages[key] = upsertMessage(s.messages[key] ?? [], decrypted);
          const chatIdx = s.chats.findIndex(
            (c) => c.id === key || c.userId === decrypted.fromId,
          );
          if (chatIdx !== -1) {
            s.chats[chatIdx].lastMessage = decrypted;
            const [chat] = s.chats.splice(chatIdx, 1);
            s.chats.unshift(chat);
          }
        });
      });
    },

    // ── editMessage ────────────────────────────────────────────
    editMessage: async (msgId, newText) => {
      set((s) => {
        for (const key of Object.keys(s.messages)) {
          const idx = s.messages[key].findIndex((m) => m.id === msgId);
          if (idx !== -1) {
            s.messages[key][idx].text = newText;
            s.messages[key][idx].isEdited = true;
            break;
          }
        }
      });
      await chatApi.editMessage(msgId, newText);
    },

    // ── deleteMessage ──────────────────────────────────────────
    deleteMessage: async (msgId, chatId) => {
      set((s) => {
        const arr = s.messages[chatId];
        if (!arr) return;
        const idx = arr.findIndex((m) => m.id === msgId);
        if (idx !== -1) {
          arr[idx].isDeleted = true;
          arr[idx].text = '';
        }
      });
      await chatApi.deleteMessage(msgId);
    },

    // ── reactToMessage ─────────────────────────────────────────
    reactToMessage: async (msgId, emoji, _chatId) => {
      await chatApi.reactToMessage(msgId, emoji);
      // Server confirms via MESSAGE_REACTION socket event
    },

    // ── pinMessage ─────────────────────────────────────────────
    pinMessage: async (msgId, _chatId) => {
      await chatApi.pinMessage(msgId);
      // Server confirms via MESSAGE_PINNED socket event
    },

    // ── setTyping (6 s auto-reset) ─────────────────────────────
    setTyping: (chatId, userId, isTyping) => {
      const timerKey = `${chatId}:${userId}`;
      if (isTyping) {
        if (_typingTimers[timerKey]) clearTimeout(_typingTimers[timerKey]);
        _typingTimers[timerKey] = setTimeout(() => {
          get().setTyping(chatId, userId, false);
        }, TYPING_AUTO_RESET_MS);
      } else {
        if (_typingTimers[timerKey]) {
          clearTimeout(_typingTimers[timerKey]);
          delete _typingTimers[timerKey];
        }
      }
      set((s) => {
        const current = s.typingUsers[chatId] ?? [];
        if (isTyping) {
          if (!current.includes(userId)) s.typingUsers[chatId] = [...current, userId];
        } else {
          s.typingUsers[chatId] = current.filter((u) => u !== userId);
        }
      });
    },

    // ── setRecording (8 s auto-clear) ─────────────────────────
    setRecording: (chatId, userId, isRecording) => {
      const timerKey = `rec:${chatId}:${userId}`;
      if (isRecording) {
        if (_recordingTimers[timerKey]) clearTimeout(_recordingTimers[timerKey]);
        _recordingTimers[timerKey] = setTimeout(() => {
          get().setRecording(chatId, userId, false);
        }, RECORDING_AUTO_RESET_MS);
      } else {
        if (_recordingTimers[timerKey]) {
          clearTimeout(_recordingTimers[timerKey]);
          delete _recordingTimers[timerKey];
        }
      }
      set((s) => {
        const current = s.recordingUsers[chatId] ?? [];
        if (isRecording) {
          if (!current.includes(userId)) s.recordingUsers[chatId] = [...current, userId];
        } else {
          s.recordingUsers[chatId] = current.filter((u) => u !== userId);
        }
      });
    },

    // ── markChatRead ───────────────────────────────────────────
    markChatRead: (chatId) => {
      set((s) => {
        const idx = s.chats.findIndex((c) => c.id === chatId || c.userId === chatId);
        if (idx !== -1) s.chats[idx].unreadCount = 0;
      });
    },

    setActiveChat: (chat) => { set((s) => { s.activeChat = chat; }); },

    updateChatLastMessage: (chatId, msg) => {
      set((s) => {
        const idx = s.chats.findIndex((c) => c.id === chatId || c.userId === chatId);
        if (idx !== -1) s.chats[idx].lastMessage = msg;
      });
    },

    clearHistory: (chatId) => {
      set((s) => {
        s.messages[chatId] = [];
        const idx = s.chats.findIndex((c) => c.id === chatId || c.userId === chatId);
        if (idx !== -1) s.chats[idx].lastMessage = undefined;
      });
    },

    archiveChat: async (userId, archive) => {
      await chatApi.archiveChat(userId, archive);
      set((s) => {
        const idx = s.chats.findIndex((c) => c.userId === userId);
        if (idx !== -1) s.chats[idx].isArchived = archive;
      });
    },

    muteChat: async (userId, mute) => {
      await chatApi.muteChat(userId, mute);
      set((s) => {
        const idx = s.chats.findIndex((c) => c.userId === userId);
        if (idx !== -1) s.chats[idx].isMuted = mute;
      });
    },

    pinChat: async (userId, pin) => {
      await chatApi.pinChat(userId, pin);
      set((s) => {
        const idx = s.chats.findIndex((c) => c.userId === userId);
        if (idx !== -1) s.chats[idx].isPinned = pin;
      });
    },

    deleteConversation: async (userId) => {
      await chatApi.deleteConversation(userId);
      set((s) => {
        s.chats = s.chats.filter((c) => c.userId !== userId);
        delete s.messages[userId];
      });
    },

    // ── initSocketListeners ────────────────────────────────────
    initSocketListeners: () => {
      // ── Private messages ──────────────────────────────────────
      const handleIncoming = (data: unknown) => {
        const msg = data as Message;
        if (msg?.id) get().receiveMessage(msg);
      };
      socketService.on(SOCKET_EVENT_NEW_MESSAGE, handleIncoming);
      socketService.on(SOCKET_EVENT_PRIVATE_MESSAGE, handleIncoming);

      // ── Group messages ─────────────────────────────────────────
      socketService.on(SOCKET_EVENT_GROUP_MESSAGE, (data: unknown) => {
        const msg = data as Message;
        if (msg?.id) get().receiveMessage(msg);
      });

      // ── Typing — private (6 s auto-reset) ────────────────────
      socketService.on(SOCKET_EVENT_TYPING, (data: unknown) => {
        const d = data as { user_id?: string; recipient_id?: string };
        if (d.recipient_id && d.user_id) get().setTyping(d.recipient_id, d.user_id, true);
      });
      socketService.on(SOCKET_EVENT_TYPING_DONE, (data: unknown) => {
        const d = data as { user_id?: string; recipient_id?: string };
        if (d.recipient_id && d.user_id) get().setTyping(d.recipient_id, d.user_id, false);
      });

      // ── Typing — group ────────────────────────────────────────
      socketService.on(SOCKET_EVENT_GROUP_TYPING, (data: unknown) => {
        const d = data as { user_id?: string; group_id?: string };
        if (d.group_id && d.user_id) get().setTyping(d.group_id, d.user_id, true);
      });
      socketService.on(SOCKET_EVENT_GROUP_TYPING_DONE, (data: unknown) => {
        const d = data as { user_id?: string; group_id?: string };
        if (d.group_id && d.user_id) get().setTyping(d.group_id, d.user_id, false);
      });

      // ── Recording (voice/video) ───────────────────────────────
      socketService.on(SOCKET_EVENT_RECORDING, (data: unknown) => {
        const d = data as { user_id?: string; chat_id?: string };
        if (d.chat_id && d.user_id) get().setRecording(d.chat_id, d.user_id, true);
      });
      socketService.on(SOCKET_EVENT_USER_ACTION, (data: unknown) => {
        const d = data as { user_id?: string; recipient_id?: string; action?: string };
        if (d.action === USER_ACTION_RECORDING && d.recipient_id && d.user_id) {
          get().setRecording(d.recipient_id, d.user_id, true);
        }
      });
      socketService.on(SOCKET_EVENT_GROUP_USER_ACTION, (data: unknown) => {
        const d = data as { user_id?: string; group_id?: string; action?: string };
        if (d.action === USER_ACTION_RECORDING && d.group_id && d.user_id) {
          get().setRecording(d.group_id, d.user_id, true);
        }
      });

      // ── Presence ──────────────────────────────────────────────
      socketService.on(SOCKET_EVENT_USER_ONLINE, (data: unknown) => {
        const d = data as { user_id?: string };
        if (d.user_id) presenceService.setOnline(d.user_id);
      });
      socketService.on(SOCKET_EVENT_USER_OFFLINE, (data: unknown) => {
        const d = data as { user_id?: string; last_seen?: string };
        if (d.user_id) {
          presenceService.setOffline(d.user_id);
          if (d.last_seen) presenceService.setLastSeen(d.user_id, d.last_seen);
        }
      });

      // ── lastSeen ping ─────────────────────────────────────────
      socketService.on(SOCKET_EVENT_LAST_SEEN, (data: unknown) => {
        const d = data as { user_id?: string; last_seen?: string };
        if (d.user_id && d.last_seen) presenceService.setLastSeen(d.user_id, d.last_seen);
      });

      // ── Seen receipts ─────────────────────────────────────────
      socketService.on(SOCKET_EVENT_MESSAGE_SEEN, (data: unknown) => {
        const d = data as { sender_id?: string };
        const chatId = d.sender_id ?? '';
        if (!chatId) return;
        set((s) => {
          const arr = s.messages[chatId];
          if (arr) for (const m of arr) m.isSeen = true;
          const idx = s.chats.findIndex((c) => c.id === chatId || c.userId === chatId);
          if (idx !== -1) s.chats[idx].unreadCount = 0;
        });
      });

      // ── Remote edits ──────────────────────────────────────────
      socketService.on(SOCKET_EVENT_MESSAGE_EDITED, (data: unknown) => {
        const d = data as { msgId?: string; text?: string };
        if (!d.msgId) return;
        set((s) => {
          for (const key of Object.keys(s.messages)) {
            const idx = s.messages[key].findIndex((m) => m.id === d.msgId);
            if (idx !== -1) {
              if (d.text !== undefined) s.messages[key][idx].text = d.text;
              s.messages[key][idx].isEdited = true;
              break;
            }
          }
        });
      });
      socketService.on(SOCKET_EVENT_GROUP_MESSAGE_EDITED, (data: unknown) => {
        const d = data as { msgId?: string; text?: string; groupId?: string };
        if (!d.msgId || !d.groupId) return;
        set((s) => {
          const arr = s.messages[d.groupId!];
          if (!arr) return;
          const idx = arr.findIndex((m) => m.id === d.msgId);
          if (idx !== -1) {
            if (d.text !== undefined) arr[idx].text = d.text;
            arr[idx].isEdited = true;
          }
        });
      });

      // ── Remote deletes ────────────────────────────────────────
      socketService.on(SOCKET_EVENT_MESSAGE_DELETED, (data: unknown) => {
        const d = data as { msgId?: string };
        if (!d.msgId) return;
        set((s) => {
          for (const key of Object.keys(s.messages)) {
            const idx = s.messages[key].findIndex((m) => m.id === d.msgId);
            if (idx !== -1) {
              s.messages[key][idx].isDeleted = true;
              s.messages[key][idx].text = '';
              break;
            }
          }
        });
      });
      socketService.on(SOCKET_EVENT_GROUP_MESSAGE_DELETED, (data: unknown) => {
        const d = data as { msgId?: string; groupId?: string };
        if (!d.msgId || !d.groupId) return;
        set((s) => {
          const arr = s.messages[d.groupId!];
          if (!arr) return;
          const idx = arr.findIndex((m) => m.id === d.msgId);
          if (idx !== -1) {
            arr[idx].isDeleted = true;
            arr[idx].text = '';
          }
        });
      });

      // ── Reactions ─────────────────────────────────────────────
      socketService.on(SOCKET_EVENT_MESSAGE_REACTION, (data: unknown) => {
        const d = data as {
          msgId?: string;
          chatId?: string;
          emoji?: string;
          userId?: string;
          action?: 'add' | 'remove';
        };
        if (!d.msgId || !d.chatId) return;
        set((s) => {
          const arr = s.messages[d.chatId!];
          if (!arr) return;
          const idx = arr.findIndex((m) => m.id === d.msgId);
          if (idx === -1) return;
          const reactions = [...(arr[idx].reactions ?? [])];
          if (d.action === 'remove') {
            arr[idx].reactions = reactions.filter(
              (r) => !(r.userId === d.userId && r.emoji === d.emoji),
            );
          } else if (d.emoji && d.userId) {
            const exists = reactions.some((r) => r.userId === d.userId && r.emoji === d.emoji);
            if (!exists) arr[idx].reactions = [...reactions, { userId: d.userId, emoji: d.emoji }];
          }
        });
      });

      // ── Pinned messages ───────────────────────────────────────
      socketService.on(SOCKET_EVENT_MESSAGE_PINNED, (data: unknown) => {
        const d = data as { chatId?: string; message?: Message };
        if (!d.chatId || !d.message) return;
        set((s) => {
          s.pinnedMessages[d.chatId!] = d.message!;
          const arr = s.messages[d.chatId!];
          if (arr) {
            const idx = arr.findIndex((m) => m.id === d.message!.id);
            if (idx !== -1) arr[idx].isPinned = true;
          }
        });
      });

      // ── History cleared ───────────────────────────────────────
      socketService.on(SOCKET_EVENT_GROUP_HISTORY_CLEARED, (data: unknown) => {
        const d = data as { groupId?: string };
        if (d.groupId) get().clearHistory(d.groupId, true);
      });
      socketService.on(SOCKET_EVENT_PRIVATE_HISTORY_CLEARED, (data: unknown) => {
        const d = data as { chatId?: string };
        if (d.chatId) get().clearHistory(d.chatId, false);
      });

      // ── Reconnect: flush offline queue ────────────────────────
      socketService.onConnect(() => {
        get().flushOfflineQueue();
      });
    },
  })),
);

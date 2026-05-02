// ============================================================
// WorldMates Messenger — Chat Zustand Store
//
// Manages all chat & message state:
//   • chats list with unread counts
//   • per-conversation message arrays (keyed by userId/chatId)
//   • typing indicators, online presence
//   • socket event wiring (call initSocketListeners() once after login)
// ============================================================

import { create } from 'zustand';
import { immer } from 'zustand/middleware/immer';
import * as chatApi from '../api/chatApi';
import { socketService } from '../services/socketService';
import type { Chat, Message } from '../api/types';
import {
  SOCKET_EVENT_NEW_MESSAGE,
  SOCKET_EVENT_PRIVATE_MESSAGE,
  SOCKET_EVENT_TYPING,
  SOCKET_EVENT_TYPING_DONE,
  SOCKET_EVENT_USER_ONLINE,
  SOCKET_EVENT_USER_OFFLINE,
  SOCKET_EVENT_MESSAGE_SEEN,
  SOCKET_EVENT_MESSAGE_EDITED,
  SOCKET_EVENT_MESSAGE_DELETED,
} from '../constants/api';

// ─────────────────────────────────────────────────────────────
// TYPES
// ─────────────────────────────────────────────────────────────

interface ChatState {
  chats: Chat[];
  activeChat: Chat | null;
  /** Messages keyed by peer userId (1-on-1) or chatId */
  messages: Record<string, Message[]>;
  isLoadingChats: boolean;
  isLoadingMessages: boolean;
  hasMoreMessages: Record<string, boolean>;
  /** chatId → array of typing userIds */
  typingUsers: Record<string, string[]>;
  onlineUsers: Set<string>;

  // ── Actions ─────────────────────────────────────────────────
  loadChats(): Promise<void>;
  loadMessages(userId: string): Promise<void>;
  loadMoreMessages(userId: string): Promise<void>;
  sendMessage(toId: string, text: string, replyToId?: string): Promise<void>;
  receiveMessage(msg: Message): void;
  editMessage(msgId: string, newText: string): Promise<void>;
  deleteMessage(msgId: string, chatId: string): Promise<void>;
  setTyping(chatId: string, userId: string, isTyping: boolean): void;
  setUserOnline(userId: string, isOnline: boolean): void;
  markChatRead(chatId: string): void;
  setActiveChat(chat: Chat | null): void;
  updateChatLastMessage(chatId: string, msg: Message): void;
  archiveChat(userId: string, archive: boolean): Promise<void>;
  muteChat(userId: string, mute: boolean): Promise<void>;
  pinChat(userId: string, pin: boolean): Promise<void>;
  deleteConversation(userId: string): Promise<void>;
  initSocketListeners(): void;
}

// ─────────────────────────────────────────────────────────────
// HELPERS
// ─────────────────────────────────────────────────────────────

/** Derive the store key for a message (peer userId for 1-on-1, groupId for groups). */
function chatKey(msg: Message): string {
  return msg.groupId ?? msg.toId;
}

/** Replace a message by id within an array, or append if not found. */
function upsertMessage(arr: Message[], incoming: Message): Message[] {
  const idx = arr.findIndex((m) => m.id === incoming.id);
  if (idx === -1) return [...arr, incoming];
  const copy = [...arr];
  copy[idx] = incoming;
  return copy;
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
    onlineUsers: new Set<string>(),

    // ── loadChats ──────────────────────────────────────────────
    loadChats: async () => {
      set((s) => {
        s.isLoadingChats = true;
      });
      try {
        const chats = await chatApi.getChatsList(1);
        set((s) => {
          s.chats = chats;
          s.isLoadingChats = false;
        });
      } catch (err) {
        console.error('[chatStore] loadChats error:', err);
        set((s) => {
          s.isLoadingChats = false;
        });
      }
    },

    // ── loadMessages ───────────────────────────────────────────
    loadMessages: async (userId: string) => {
      set((s) => {
        s.isLoadingMessages = true;
      });
      try {
        const msgs = await chatApi.getMessages(userId, 1);
        set((s) => {
          s.messages[userId] = msgs;
          s.hasMoreMessages[userId] = msgs.length > 0;
          s.isLoadingMessages = false;
        });
      } catch (err) {
        console.error('[chatStore] loadMessages error:', err);
        set((s) => {
          s.isLoadingMessages = false;
        });
      }
    },

    // ── loadMoreMessages ───────────────────────────────────────
    loadMoreMessages: async (userId: string) => {
      const existing = get().messages[userId] ?? [];
      if (existing.length === 0) return;

      const lastMsg = existing[0];
      try {
        const older = await chatApi.loadMoreMessages(userId, lastMsg.id);
        set((s) => {
          s.messages[userId] = [...older, ...(s.messages[userId] ?? [])];
          s.hasMoreMessages[userId] = older.length > 0;
        });
      } catch (err) {
        console.error('[chatStore] loadMoreMessages error:', err);
      }
    },

    // ── sendMessage ────────────────────────────────────────────
    sendMessage: async (toId: string, text: string, replyToId?: string) => {
      const tempId = `tmp_${Date.now()}_${Math.random()}`;
      const tempMsg: Message = {
        id: tempId,
        fromId: 'me',
        toId,
        text,
        type: 'text',
        isEdited: false,
        isDeleted: false,
        isSeen: false,
        isPinned: false,
        createdAt: Date.now(),
        isLocalPending: true,
        ...(replyToId ? { replyToId } : {}),
      };

      // Optimistic insertion
      set((s) => {
        const prev = s.messages[toId] ?? [];
        s.messages[toId] = [...prev, tempMsg];
      });

      try {
        const realMsg = await chatApi.sendMessage(toId, text, replyToId);
        set((s) => {
          const arr = s.messages[toId] ?? [];
          const idx = arr.findIndex((m) => m.id === tempId);
          if (idx !== -1) {
            s.messages[toId][idx] = realMsg;
          } else {
            s.messages[toId] = [...arr, realMsg];
          }
          get().updateChatLastMessage(toId, realMsg);
        });
      } catch (err) {
        // Remove failed optimistic message
        set((s) => {
          s.messages[toId] = (s.messages[toId] ?? []).filter((m) => m.id !== tempId);
        });
        throw err;
      }
    },

    // ── receiveMessage ─────────────────────────────────────────
    receiveMessage: (msg: Message) => {
      const key = chatKey(msg);
      set((s) => {
        const arr = s.messages[key] ?? [];
        s.messages[key] = upsertMessage(arr, msg);

        // Update chat's lastMessage + unreadCount
        const chatIdx = s.chats.findIndex((c) => c.id === key || c.userId === msg.fromId);
        if (chatIdx !== -1) {
          s.chats[chatIdx].lastMessage = msg;
          if (!msg.isSeen) {
            s.chats[chatIdx].unreadCount = (s.chats[chatIdx].unreadCount ?? 0) + 1;
          }
          // Move to top (most recent)
          const [chat] = s.chats.splice(chatIdx, 1);
          s.chats.unshift(chat);
        }
      });
    },

    // ── editMessage ────────────────────────────────────────────
    editMessage: async (msgId: string, newText: string) => {
      await chatApi.editMessage(msgId, newText);
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
    },

    // ── deleteMessage ──────────────────────────────────────────
    deleteMessage: async (msgId: string, chatId: string) => {
      await chatApi.deleteMessage(msgId);
      set((s) => {
        const arr = s.messages[chatId];
        if (arr) {
          const idx = arr.findIndex((m) => m.id === msgId);
          if (idx !== -1) {
            s.messages[chatId][idx].isDeleted = true;
            s.messages[chatId][idx].text = '';
          }
        }
      });
    },

    // ── setTyping ──────────────────────────────────────────────
    setTyping: (chatId: string, userId: string, isTyping: boolean) => {
      set((s) => {
        const current = s.typingUsers[chatId] ?? [];
        if (isTyping) {
          if (!current.includes(userId)) {
            s.typingUsers[chatId] = [...current, userId];
          }
        } else {
          s.typingUsers[chatId] = current.filter((id) => id !== userId);
        }
      });
    },

    // ── setUserOnline ──────────────────────────────────────────
    setUserOnline: (userId: string, isOnline: boolean) => {
      set((s) => {
        if (isOnline) {
          s.onlineUsers.add(userId);
        } else {
          s.onlineUsers.delete(userId);
        }
        // Sync onto the chats list entry for immediate UI update
        const chatIdx = s.chats.findIndex((c) => c.userId === userId);
        if (chatIdx !== -1) {
          s.chats[chatIdx].isOnline = isOnline;
        }
      });
    },

    // ── markChatRead ───────────────────────────────────────────
    markChatRead: (chatId: string) => {
      set((s) => {
        const idx = s.chats.findIndex((c) => c.id === chatId || c.userId === chatId);
        if (idx !== -1) {
          s.chats[idx].unreadCount = 0;
        }
      });
    },

    // ── setActiveChat ──────────────────────────────────────────
    setActiveChat: (chat: Chat | null) => {
      set((s) => {
        s.activeChat = chat;
      });
    },

    // ── updateChatLastMessage ──────────────────────────────────
    updateChatLastMessage: (chatId: string, msg: Message) => {
      set((s) => {
        const idx = s.chats.findIndex((c) => c.id === chatId || c.userId === chatId);
        if (idx !== -1) {
          s.chats[idx].lastMessage = msg;
          s.chats[idx].updatedAt = msg.createdAt;
        }
      });
    },

    // ── archiveChat ────────────────────────────────────────────
    archiveChat: async (userId: string, archive: boolean) => {
      await chatApi.archiveChat(userId, archive);
      set((s) => {
        const idx = s.chats.findIndex((c) => c.userId === userId || c.id === userId);
        if (idx !== -1) s.chats[idx].isArchived = archive;
      });
    },

    // ── muteChat ───────────────────────────────────────────────
    muteChat: async (userId: string, mute: boolean) => {
      await chatApi.muteChat(userId, mute);
      set((s) => {
        const idx = s.chats.findIndex((c) => c.userId === userId || c.id === userId);
        if (idx !== -1) s.chats[idx].isMuted = mute;
      });
    },

    // ── pinChat ────────────────────────────────────────────────
    pinChat: async (userId: string, pin: boolean) => {
      await chatApi.pinChat(userId, pin);
      set((s) => {
        const idx = s.chats.findIndex((c) => c.userId === userId || c.id === userId);
        if (idx !== -1) s.chats[idx].isPinned = pin;
      });
    },

    // ── deleteConversation ─────────────────────────────────────
    deleteConversation: async (userId: string) => {
      await chatApi.deleteConversation(userId);
      set((s) => {
        s.chats = s.chats.filter((c) => c.userId !== userId && c.id !== userId);
        delete s.messages[userId];
      });
    },

    // ── initSocketListeners ────────────────────────────────────
    initSocketListeners: () => {
      // New private message (two event names used by server)
      const handleIncoming = (data: unknown) => {
        const msg = data as Message;
        if (msg && msg.id) {
          get().receiveMessage(msg);
        }
      };

      socketService.on(SOCKET_EVENT_NEW_MESSAGE, handleIncoming);
      socketService.on(SOCKET_EVENT_PRIVATE_MESSAGE, handleIncoming);

      // Typing indicators
      socketService.on(SOCKET_EVENT_TYPING, (data: unknown) => {
        const d = data as { user_id?: string; recipient_id?: string };
        const chatId = d.recipient_id ?? '';
        const userId = d.user_id ?? '';
        if (chatId && userId) get().setTyping(chatId, userId, true);
      });

      socketService.on(SOCKET_EVENT_TYPING_DONE, (data: unknown) => {
        const d = data as { user_id?: string; recipient_id?: string };
        const chatId = d.recipient_id ?? '';
        const userId = d.user_id ?? '';
        if (chatId && userId) get().setTyping(chatId, userId, false);
      });

      // Presence
      socketService.on(SOCKET_EVENT_USER_ONLINE, (data: unknown) => {
        const d = data as { user_id?: string };
        if (d.user_id) get().setUserOnline(d.user_id, true);
      });

      socketService.on(SOCKET_EVENT_USER_OFFLINE, (data: unknown) => {
        const d = data as { user_id?: string };
        if (d.user_id) get().setUserOnline(d.user_id, false);
      });

      // Seen receipts — mark messages in a conversation as read
      socketService.on(SOCKET_EVENT_MESSAGE_SEEN, (data: unknown) => {
        const d = data as { sender_id?: string; message_id?: string };
        const chatId = d.sender_id ?? '';
        if (!chatId) return;
        set((s) => {
          const arr = s.messages[chatId];
          if (!arr) return;
          for (const m of arr) {
            m.isSeen = true;
          }
        });
      });

      // Remote edits
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

      // Remote deletes
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
    },
  })),
);

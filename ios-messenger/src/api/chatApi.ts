// ============================================================
// WorldMates Messenger — Chat REST API
//
// Key differences from initial iOS port:
//   • ALL endpoints use POST (not GET) — matches Android/Windows
//   • Parameter names match Android @Field annotations:
//       recipient_id, before_message_id, chat_id, etc.
//   • Server returns snake_case — normalised to camelCase here
//   • Messages list is in `messages` field, not `data`
//   • Chat list is in `data` field
// ============================================================

import { nodeApi } from './apiClient';
import type { Chat, Message, MessageReaction } from './types';
import {
  NODE_CHATS_LIST,
  NODE_CHAT_GET,
  NODE_CHAT_SEND,
  NODE_CHAT_SEND_MEDIA,
  NODE_CHAT_EDIT,
  NODE_CHAT_DELETE,
  NODE_CHAT_SEEN,
  NODE_CHAT_REACT,
  NODE_CHAT_PIN,
  NODE_CHAT_PINNED,
  NODE_CHAT_FORWARD,
  NODE_CHAT_DEL_CONV,
  NODE_CHAT_ARCHIVE,
  NODE_CHAT_MUTE,
  NODE_CHAT_PIN_CHAT,
  NODE_CHAT_LOADMORE,
  NODE_CHAT_SEARCH,
  NODE_CHAT_FAV,
  NODE_CHAT_FAV_LIST,
  NODE_SAVED_SAVE,
  NODE_SAVED_LIST,
  NODE_USER_STATUS,
  NODE_SEARCH_USERS,
  CHATS_PAGE_SIZE,
  MESSAGES_PAGE_SIZE,
  MESSAGE_TYPES,
  CIPHER_VERSION_SIGNAL,
} from '../constants/api';

// ─────────────────────────────────────────────────────────────
// HELPERS
// ─────────────────────────────────────────────────────────────

function str(v: unknown, fallback = ''): string {
  if (v === null || v === undefined) return fallback;
  return String(v);
}

function num(v: unknown, fallback = 0): number {
  const n = Number(v);
  return isNaN(n) ? fallback : n;
}

function bool(v: unknown): boolean {
  return v === true || v === 1 || v === '1' || v === 'true';
}

// ─────────────────────────────────────────────────────────────
// NORMALIZERS — server snake_case → TypeScript camelCase
// Mirrors Windows api.ts normaliseMessage / normaliseChatItem
// ─────────────────────────────────────────────────────────────

/** Derive display text from a raw message object */
function extractText(raw: Record<string, unknown>): string {
  const cv = num(raw.cipher_version);
  // Signal E2EE (cipher_version=3) — hide ciphertext, decrypt async in chatStore
  if (cv === CIPHER_VERSION_SIGNAL) return '';
  // Prefer server-decrypted text, then or_text (AES plaintext field), then raw text
  return str(raw.decrypted_text ?? raw.or_text ?? raw.text ?? '');
}

/** Extract last-message preview from a chat list item */
function lastMsgPreview(raw: unknown): string {
  if (!raw) return '';
  if (typeof raw === 'object') return extractText(raw as Record<string, unknown>);
  return str(raw);
}

/** Derive iOS MessageType from server type / media_type fields */
function deriveType(raw: Record<string, unknown>): string {
  const serverType = str(raw.type ?? '').toLowerCase();
  const mediaType = str(raw.media_type ?? '').toLowerCase();
  const combined = mediaType || serverType;
  const MEDIA = ['image', 'video', 'audio', 'voice', 'file', 'sticker', 'gif', 'location', 'call'];
  if (MEDIA.includes(combined)) return combined;
  if (MEDIA.includes(serverType)) return serverType;
  if (raw.stickers) return 'sticker';
  if (raw.media) return mediaType || 'file';
  if (raw.lat && raw.lng) return MESSAGE_TYPES.LOCATION;
  return MESSAGE_TYPES.TEXT;
}

/** Build reply-to fields from server reply object or flat fields */
function extractReplyTo(raw: Record<string, unknown>) {
  const reply = raw.reply as Record<string, unknown> | undefined;
  if (reply) {
    return {
      replyToId: str(reply.id ?? reply.message_id ?? '') || undefined,
      replyToText: str(reply.decrypted_text ?? reply.text ?? '') || undefined,
      replyToName: str(reply.sender_name ?? reply.name ?? '') || undefined,
      replyToType: str(reply.type ?? '') || undefined,
    };
  }
  return {
    replyToId: str(raw.reply_id ?? raw.message_reply_id ?? '') || undefined,
    replyToText: str(raw.reply_to_text ?? '') || undefined,
    replyToName: str(raw.reply_to_name ?? '') || undefined,
  };
}

export function normaliseMessage(raw: Record<string, unknown>): Message {
  const cv = num(raw.cipher_version);
  const isSignal = cv === CIPHER_VERSION_SIGNAL;
  const displayText = extractText(raw);

  // Server sends time as Unix seconds; multiply to ms for JS Date
  const timeRaw = raw.time;
  const createdAt = timeRaw
    ? num(timeRaw) > 1_000_000_000 ? num(timeRaw) * 1000 : num(timeRaw)
    : str(raw.time_text ?? raw.created_at ?? '');

  return {
    id: str(raw.id ?? raw.message_id ?? ''),
    fromId: str(raw.from_id ?? raw.sender_id ?? raw.user_id ?? ''),
    toId: str(raw.to_id ?? raw.recipient_id ?? raw.receiver_id ?? ''),
    groupId: raw.group_id ? str(raw.group_id) : undefined,
    text: displayText,
    type: deriveType(raw),
    typeTwo: raw.type_two ? str(raw.type_two) : undefined,
    mediaUrl: raw.media ? str(raw.media) : undefined,
    mediaType: str(raw.media_type ?? '') || undefined,
    fileName: raw.media_file_name ? str(raw.media_file_name) : undefined,
    mediaDuration: raw.media_duration ? num(raw.media_duration) : undefined,
    senderName: raw.sender_name ? str(raw.sender_name) : undefined,
    senderAvatar: raw.sender_avatar ? str(raw.sender_avatar) : undefined,
    ...extractReplyTo(raw),
    isEdited: bool(raw.is_edited),
    isDeleted: bool(raw.deleted ?? raw.is_deleted),
    isSeen: bool(raw.is_read ?? raw.seen ?? raw.is_seen),
    isPinned: bool(raw.pinned ?? raw.is_pinned),
    reactions: (raw.reactions as MessageReaction[] | undefined) ?? [],
    cipherVersion: cv || undefined,
    iv: isSignal ? (raw.iv as string | undefined) : undefined,
    tag: isSignal ? (raw.tag as string | undefined) : undefined,
    signalHeader: isSignal ? (raw.signal_header as string | undefined) : undefined,
    decryptedText: raw.decrypted_text ? str(raw.decrypted_text) : undefined,
    lat: raw.lat ? str(raw.lat) : undefined,
    lng: raw.lng ? str(raw.lng) : undefined,
    stickers: raw.stickers ? str(raw.stickers) : undefined,
    createdAt,
    isLocalPending: false,
  };
}

function normaliseChat(raw: Record<string, unknown>): Chat {
  const firstName = str(raw.first_name ?? '');
  const lastName = str(raw.last_name ?? '');
  const name = str(
    (raw.name ?? raw.full_name ?? raw.username ??
      [firstName, lastName].filter(Boolean).join(' ').trim()) ||
      'User',
  );

  let lastMessage: Message | undefined;
  if (raw.last_message && typeof raw.last_message === 'object') {
    lastMessage = normaliseMessage(raw.last_message as Record<string, unknown>);
  } else if (raw.last_message && typeof raw.last_message === 'string') {
    const preview = lastMsgPreview(raw.last_message);
    if (preview) {
      lastMessage = {
        id: str(raw.last_message_id ?? ''),
        fromId: '',
        toId: str(raw.user_id ?? raw.id ?? ''),
        text: preview,
        type: MESSAGE_TYPES.TEXT,
        isEdited: false,
        isDeleted: false,
        isSeen: true,
        isPinned: false,
        createdAt: str(raw.last_activity ?? raw.time ?? ''),
      };
    }
  }

  return {
    id: str(raw.user_id ?? raw.id ?? ''),
    type: bool(raw.is_group) ? 'group' : str(raw.chat_type ?? 'user'),
    name,
    avatar: raw.avatar ? str(raw.avatar) : undefined,
    lastMessage,
    unreadCount: num(raw.count ?? raw.unread_count ?? 0),
    isArchived: bool(raw.archived ?? raw.is_archived),
    isMuted: bool(raw.muted ?? raw.is_muted),
    isPinned: bool(raw.pinned ?? raw.is_pinned),
    isHidden: bool(raw.hidden ?? raw.is_hidden),
    userId: str(raw.user_id ?? raw.id ?? '') || undefined,
    username: raw.username ? str(raw.username) : undefined,
    isOnline: bool(raw.is_online ?? raw.online),
    lastSeen: raw.last_seen ? str(raw.last_seen) : undefined,
    updatedAt: str(raw.last_activity ?? raw.updated_at ?? raw.time ?? ''),
    isBot: bool(raw.is_bot),
    botDescription: raw.bot_description ? str(raw.bot_description) : undefined,
  };
}

// ─────────────────────────────────────────────────────────────
// CHATS LIST
// ─────────────────────────────────────────────────────────────

/**
 * Fetch paginated list of conversations.
 * POST api/node/chat/chats  { limit, offset, show_archived }
 * Response: { api_status, data: [...] }
 */
export async function getChatsList(page = 1): Promise<Chat[]> {
  const offset = (page - 1) * CHATS_PAGE_SIZE;
  const res = await nodeApi.post<Record<string, unknown>>(NODE_CHATS_LIST, {
    limit: CHATS_PAGE_SIZE,
    offset,
    show_archived: 'false',
  });
  const raw = (res.data?.data ?? []) as Record<string, unknown>[];
  return Array.isArray(raw) ? raw.map(normaliseChat) : [];
}

// ─────────────────────────────────────────────────────────────
// MESSAGES
// ─────────────────────────────────────────────────────────────

/**
 * Fetch first page of messages for a 1-on-1 conversation.
 * POST api/node/chat/get  { recipient_id, limit, before_message_id }
 * Response: { api_status, messages: [...] }
 */
export async function getMessages(userId: string, _page = 1): Promise<Message[]> {
  const res = await nodeApi.post<Record<string, unknown>>(NODE_CHAT_GET, {
    recipient_id: userId,
    limit: MESSAGES_PAGE_SIZE,
    before_message_id: 0,
  });
  const raw = (res.data?.messages ?? []) as Record<string, unknown>[];
  return Array.isArray(raw) ? raw.map(normaliseMessage) : [];
}

/**
 * Load older messages before a given message ID (pagination).
 * POST api/node/chat/loadmore  { recipient_id, before_message_id, limit }
 */
export async function loadMoreMessages(userId: string, lastMsgId: string): Promise<Message[]> {
  const res = await nodeApi.post<Record<string, unknown>>(NODE_CHAT_LOADMORE, {
    recipient_id: userId,
    before_message_id: lastMsgId,
    limit: MESSAGES_PAGE_SIZE,
  });
  const raw = (res.data?.messages ?? []) as Record<string, unknown>[];
  return Array.isArray(raw) ? raw.map(normaliseMessage) : [];
}

/**
 * Send a private text message.
 * POST api/node/chat/send  { recipient_id, text, reply_id? }
 * Response: { api_status, message_data: {...} }
 */
export async function sendMessage(
  toId: string,
  text: string,
  replyToId?: string,
): Promise<Message> {
  const res = await nodeApi.post<Record<string, unknown>>(NODE_CHAT_SEND, {
    recipient_id: toId,
    text,
    ...(replyToId ? { reply_id: replyToId } : {}),
  });
  const raw = (res.data?.message_data ?? res.data) as Record<string, unknown> | null;
  if (!raw || typeof raw !== 'object') throw new Error('Failed to send message');
  return normaliseMessage(raw);
}

/**
 * Send a text message to a group chat.
 * POST api/node/group/messages/send  { group_id, text, reply_id? }
 */
export async function sendGroupMessage(
  groupId: string,
  text: string,
  replyToId?: string,
): Promise<Message> {
  const res = await nodeApi.post<Record<string, unknown>>('api/node/group/messages/send', {
    group_id: groupId,
    text,
    ...(replyToId ? { reply_id: replyToId } : {}),
  });
  const raw = (res.data?.message_data ?? res.data) as Record<string, unknown> | null;
  if (!raw || typeof raw !== 'object') throw new Error('Failed to send group message');
  return normaliseMessage(raw);
}

/**
 * Send a media message (multipart/form-data).
 */
export async function sendMedia(toId: string, formData: FormData): Promise<Message> {
  const res = await nodeApi.post<Record<string, unknown>>(NODE_CHAT_SEND_MEDIA, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    params: { recipient_id: toId },
  });
  const raw = (res.data?.message_data ?? res.data) as Record<string, unknown> | null;
  if (!raw || typeof raw !== 'object') throw new Error('Failed to send media');
  return normaliseMessage(raw);
}

/**
 * Edit an existing message text.
 * POST api/node/chat/edit  { message_id, text }
 */
export async function editMessage(msgId: string, text: string): Promise<void> {
  await nodeApi.post(NODE_CHAT_EDIT, { message_id: msgId, text });
}

/**
 * Delete a message.
 * POST api/node/chat/delete  { message_id }
 */
export async function deleteMessage(msgId: string): Promise<void> {
  await nodeApi.post(NODE_CHAT_DELETE, { message_id: msgId });
}

/**
 * Mark all messages from `fromId` as seen.
 * POST api/node/chat/seen  { sender_id }
 */
export async function markSeen(fromId: string): Promise<void> {
  await nodeApi.post(NODE_CHAT_SEEN, { sender_id: fromId });
}

/**
 * React to a message with an emoji.
 * POST api/node/chat/react  { message_id, emoji }
 */
export async function reactToMessage(msgId: string, emoji: string): Promise<void> {
  await nodeApi.post(NODE_CHAT_REACT, { message_id: msgId, emoji });
}

/**
 * Pin or unpin a message.
 * POST api/node/chat/pin  { message_id }
 */
export async function pinMessage(msgId: string): Promise<void> {
  await nodeApi.post(NODE_CHAT_PIN, { message_id: msgId });
}

/**
 * Get all pinned messages for a conversation.
 * POST api/node/chat/pinned  { recipient_id }
 */
export async function getPinnedMessages(userId: string): Promise<Message[]> {
  const res = await nodeApi.post<Record<string, unknown>>(NODE_CHAT_PINNED, {
    recipient_id: userId,
  });
  const raw = (res.data?.data ?? res.data?.messages ?? []) as Record<string, unknown>[];
  return Array.isArray(raw) ? raw.map(normaliseMessage) : [];
}

/**
 * Forward a message to one or more users.
 * POST api/node/chat/forward  { message_id, recipients }
 */
export async function forwardMessage(msgId: string, toIds: string[]): Promise<void> {
  await nodeApi.post(NODE_CHAT_FORWARD, { message_id: msgId, recipients: toIds });
}

/**
 * Full-text search within a conversation.
 * POST api/node/chat/search  { recipient_id, q }
 */
export async function searchMessages(userId: string, query: string): Promise<Message[]> {
  const res = await nodeApi.post<Record<string, unknown>>(NODE_CHAT_SEARCH, {
    recipient_id: userId,
    q: query,
  });
  const raw = (res.data?.messages ?? res.data?.data ?? []) as Record<string, unknown>[];
  return Array.isArray(raw) ? raw.map(normaliseMessage) : [];
}

// ─────────────────────────────────────────────────────────────
// CONVERSATION MANAGEMENT
// ─────────────────────────────────────────────────────────────

export async function deleteConversation(userId: string): Promise<void> {
  await nodeApi.post(NODE_CHAT_DEL_CONV, { chat_id: userId });
}

export async function archiveChat(userId: string, archive: boolean): Promise<void> {
  await nodeApi.post(NODE_CHAT_ARCHIVE, { chat_id: userId, archive: archive ? 1 : 0 });
}

export async function muteChat(userId: string, mute: boolean): Promise<void> {
  await nodeApi.post(NODE_CHAT_MUTE, { chat_id: userId, mute: mute ? 1 : 0 });
}

export async function pinChat(userId: string, pin: boolean): Promise<void> {
  await nodeApi.post(NODE_CHAT_PIN_CHAT, { chat_id: userId, pin: pin ? 1 : 0 });
}

// ─────────────────────────────────────────────────────────────
// FAVORITES & SAVED MESSAGES
// ─────────────────────────────────────────────────────────────

export async function saveFavorite(msgId: string): Promise<void> {
  await nodeApi.post(NODE_CHAT_FAV, { message_id: msgId });
}

export async function getFavorites(): Promise<Message[]> {
  const res = await nodeApi.post<Record<string, unknown>>(NODE_CHAT_FAV_LIST, {});
  const raw = (res.data?.data ?? []) as Record<string, unknown>[];
  return Array.isArray(raw) ? raw.map(normaliseMessage) : [];
}

export async function saveMessage(msgId: string): Promise<void> {
  await nodeApi.post(NODE_SAVED_SAVE, { message_id: msgId });
}

export async function getSavedMessages(): Promise<Message[]> {
  const res = await nodeApi.post<Record<string, unknown>>(NODE_SAVED_LIST, {});
  const raw = (res.data?.data ?? []) as Record<string, unknown>[];
  return Array.isArray(raw) ? raw.map(normaliseMessage) : [];
}

// ─────────────────────────────────────────────────────────────
// USER STATUS
// ─────────────────────────────────────────────────────────────

/**
 * Get online presence and last-seen timestamp for a user.
 * POST api/node/user/status  { user_id }
 */
export async function getUserStatus(
  userId: string,
): Promise<{ isOnline: boolean; lastSeen: string }> {
  const res = await nodeApi.post<Record<string, unknown>>(NODE_USER_STATUS, {
    user_id: userId,
  });
  const d = (res.data ?? {}) as Record<string, unknown>;
  return {
    isOnline: bool(d.is_online ?? d.online),
    lastSeen: str(d.last_seen ?? d.lastseen ?? ''),
  };
}

export interface UserSearchResult {
  id: string;
  username: string;
  name: string;
  avatar?: string;
  isOnline?: boolean;
}

export async function searchUsers(query: string): Promise<UserSearchResult[]> {
  const res = await nodeApi.post<Record<string, unknown>>(NODE_SEARCH_USERS, {
    query,
    limit: 30,
  });
  const raw = res.data;
  const list = (raw?.users ?? raw?.data ?? raw?.results ?? []) as Record<string, unknown>[];
  if (!Array.isArray(list)) return [];
  return list.map((u) => {
    const firstName = str(u.first_name ?? '');
    const lastName = str(u.last_name ?? '');
    const fullName = str(
      (u.name ?? u.full_name ?? [firstName, lastName].filter(Boolean).join(' ').trim()) || u.username || '',
    );
    return {
      id: str(u.user_id ?? u.id ?? ''),
      username: str(u.username ?? ''),
      name: fullName || str(u.username ?? ''),
      avatar: u.avatar ? str(u.avatar) : undefined,
      isOnline: bool(u.is_online ?? u.online),
    };
  }).filter((u) => u.id);
}

// ============================================================
// WorldMates Messenger — Chat REST API
// All requests go to the Node.js backend via nodeGet / nodePost /
// nodePut / nodeDelete from apiClient.
// ============================================================

import { nodeGet, nodePost, nodePut, nodeDelete } from './apiClient';
import type { Chat, Message } from './types';
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
  CHATS_PAGE_SIZE,
  MESSAGES_PAGE_SIZE,
} from '../constants/api';

// ─────────────────────────────────────────────────────────────
// CHATS LIST
// ─────────────────────────────────────────────────────────────

/**
 * Fetch the paginated list of conversations for the current user.
 * GET api/node/chat/chats?page=N&limit=50
 */
export async function getChatsList(page = 1): Promise<Chat[]> {
  const res = await nodeGet<Chat[]>(NODE_CHATS_LIST, {
    params: { page, limit: CHATS_PAGE_SIZE },
  });
  return res.data ?? [];
}

// ─────────────────────────────────────────────────────────────
// MESSAGES
// ─────────────────────────────────────────────────────────────

/**
 * Fetch the first page of messages for a 1-on-1 conversation.
 * GET api/node/chat/get?userId=X&page=N
 */
export async function getMessages(userId: string, page = 1): Promise<Message[]> {
  const res = await nodeGet<Message[]>(NODE_CHAT_GET, {
    params: { userId, page, limit: MESSAGES_PAGE_SIZE },
  });
  return res.data ?? [];
}

/**
 * Send a private text message.
 * POST api/node/chat/send
 */
export async function sendMessage(
  toId: string,
  text: string,
  replyToId?: string,
): Promise<Message> {
  const res = await nodePost<Message>(NODE_CHAT_SEND, {
    toId,
    text,
    ...(replyToId ? { replyToId } : {}),
  });
  if (!res.data) throw new Error(res.error ?? res.message ?? 'Failed to send message');
  return res.data;
}

/**
 * Send a text message to a group chat.
 * POST api/node/group/messages/send
 */
export async function sendGroupMessage(
  groupId: string,
  text: string,
  replyToId?: string,
): Promise<Message> {
  const res = await nodePost<Message>('api/node/group/messages/send', {
    groupId,
    text,
    ...(replyToId ? { replyToId } : {}),
  });
  if (!res.data) throw new Error(res.error ?? res.message ?? 'Failed to send group message');
  return res.data;
}

/**
 * Send a media message (image, video, audio, file).
 * POST api/node/chat/send-media   (multipart/form-data)
 */
export async function sendMedia(toId: string, formData: FormData): Promise<Message> {
  const res = await nodePost<Message>(NODE_CHAT_SEND_MEDIA, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    params: { toId },
  });
  if (!res.data) throw new Error(res.error ?? res.message ?? 'Failed to send media');
  return res.data;
}

/**
 * Edit an existing message text.
 * PUT api/node/chat/edit
 */
export async function editMessage(msgId: string, text: string): Promise<void> {
  await nodePut(NODE_CHAT_EDIT, { msgId, text });
}

/**
 * Delete a message.
 * DELETE api/node/chat/delete?msgId=X
 */
export async function deleteMessage(msgId: string): Promise<void> {
  await nodeDelete(NODE_CHAT_DELETE, { params: { msgId } });
}

/**
 * Mark all messages from `fromId` as seen.
 * POST api/node/chat/seen
 */
export async function markSeen(fromId: string): Promise<void> {
  await nodePost(NODE_CHAT_SEEN, { fromId });
}

/**
 * React to a message with an emoji.
 * POST api/node/chat/react
 */
export async function reactToMessage(msgId: string, emoji: string): Promise<void> {
  await nodePost(NODE_CHAT_REACT, { msgId, emoji });
}

/**
 * Pin or unpin a message in a conversation.
 * POST api/node/chat/pin
 */
export async function pinMessage(msgId: string): Promise<void> {
  await nodePost(NODE_CHAT_PIN, { msgId });
}

/**
 * Get all pinned messages for a conversation.
 * GET api/node/chat/pinned?userId=X
 */
export async function getPinnedMessages(userId: string): Promise<Message[]> {
  const res = await nodeGet<Message[]>(NODE_CHAT_PINNED, { params: { userId } });
  return res.data ?? [];
}

/**
 * Forward a message to one or more users.
 * POST api/node/chat/forward
 */
export async function forwardMessage(msgId: string, toIds: string[]): Promise<void> {
  await nodePost(NODE_CHAT_FORWARD, { msgId, toIds });
}

/**
 * Load more (older) messages before a given message ID.
 * GET api/node/chat/loadmore?userId=X&lastMsgId=Y
 */
export async function loadMoreMessages(userId: string, lastMsgId: string): Promise<Message[]> {
  const res = await nodeGet<Message[]>(NODE_CHAT_LOADMORE, {
    params: { userId, lastMsgId, limit: MESSAGES_PAGE_SIZE },
  });
  return res.data ?? [];
}

/**
 * Full-text search within a conversation.
 * GET api/node/chat/search?userId=X&q=query
 */
export async function searchMessages(userId: string, query: string): Promise<Message[]> {
  const res = await nodeGet<Message[]>(NODE_CHAT_SEARCH, { params: { userId, q: query } });
  return res.data ?? [];
}

// ─────────────────────────────────────────────────────────────
// CONVERSATION MANAGEMENT
// ─────────────────────────────────────────────────────────────

/**
 * Permanently delete an entire conversation (for self only).
 * DELETE api/node/chat/delete-conversation?userId=X
 */
export async function deleteConversation(userId: string): Promise<void> {
  await nodeDelete(NODE_CHAT_DEL_CONV, { params: { userId } });
}

/**
 * Archive or un-archive a chat.
 * POST api/node/chat/archive
 */
export async function archiveChat(userId: string, archive: boolean): Promise<void> {
  await nodePost(NODE_CHAT_ARCHIVE, { userId, archive });
}

/**
 * Mute or un-mute push notifications for a chat.
 * POST api/node/chat/mute
 */
export async function muteChat(userId: string, mute: boolean): Promise<void> {
  await nodePost(NODE_CHAT_MUTE, { userId, mute });
}

/**
 * Pin or un-pin a chat at the top of the chats list.
 * POST api/node/chat/pin-chat
 */
export async function pinChat(userId: string, pin: boolean): Promise<void> {
  await nodePost(NODE_CHAT_PIN_CHAT, { userId, pin });
}

// ─────────────────────────────────────────────────────────────
// FAVORITES
// ─────────────────────────────────────────────────────────────

/**
 * Save a message to the Favorites collection.
 * POST api/node/chat/fav
 */
export async function saveFavorite(msgId: string): Promise<void> {
  await nodePost(NODE_CHAT_FAV, { msgId });
}

/**
 * Get all messages saved as Favorites.
 * GET api/node/chat/fav-list
 */
export async function getFavorites(): Promise<Message[]> {
  const res = await nodeGet<Message[]>(NODE_CHAT_FAV_LIST);
  return res.data ?? [];
}

// ─────────────────────────────────────────────────────────────
// SAVED MESSAGES
// ─────────────────────────────────────────────────────────────

/**
 * Save a message to the Saved Messages cloud folder.
 * POST api/node/chat/saved/save
 */
export async function saveMessage(msgId: string): Promise<void> {
  await nodePost(NODE_SAVED_SAVE, { msgId });
}

/**
 * Get all messages in the Saved Messages cloud folder.
 * GET api/node/chat/saved/list
 */
export async function getSavedMessages(): Promise<Message[]> {
  const res = await nodeGet<Message[]>(NODE_SAVED_LIST);
  return res.data ?? [];
}

// ─────────────────────────────────────────────────────────────
// USER STATUS
// ─────────────────────────────────────────────────────────────

/**
 * Get the online presence and last-seen timestamp for a user.
 * GET api/node/user/status?userId=X
 */
export async function getUserStatus(
  userId: string,
): Promise<{ isOnline: boolean; lastSeen: string }> {
  const res = await nodeGet<{ isOnline: boolean; lastSeen: string }>(NODE_USER_STATUS, {
    params: { userId },
  });
  return res.data ?? { isOnline: false, lastSeen: '' };
}

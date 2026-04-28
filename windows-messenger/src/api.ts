/**
 * WorldMates API client — Node.js REST API only (port 449).
 *
 * Mirrors the Kotlin/Android client exactly:
 *   - All POST requests are application/x-www-form-urlencoded  (@FormUrlEncoded)
 *   - Auth header: "access-token: <token>"
 *   - No PHP API, no Windows API fallbacks
 */

import type {
  AuthResponse,
  ChannelItem,
  ChannelPost,
  ChannelPostsResponse,
  ChatItem,
  ChatListResponse,
  GenericListResponse,
  GroupItem,
  MediaUploadResponse,
  MessageItem,
  MessagesResponse,
  StoryItem
} from './types';
import type { NodeApiShim, PreKeyBundle } from './signalService';

// ─── Config ───────────────────────────────────────────────────────────────────

export const API_BASE_URL         = 'https://worldmates.club/api/v2/'; // kept for compat
export const NODE_BASE_URL        = 'https://worldmates.club:449';
export const SOCKET_URL           = 'https://worldmates.club:449/';
export const REGISTER_PATH        = '';
export const WINDOWS_APP_BASE_URL = '';
export const SITE_ENCRYPT_KEY     = '2ad9c757daccdfff436dc226779e20b719f6d6f8';
export const SERVER_KEY           = '';

export const TURN_FALLBACK: RTCIceServer[] = [
  { urls: 'stun:stun.l.google.com:19302' },
  { urls: 'stun:stun1.l.google.com:19302' },
  {
    urls: [
      'turn:worldmates.club:3478?transport=udp',
      'turn:worldmates.club:3478?transport=tcp',
      'turns:worldmates.club:5349?transport=tcp'
    ]
  }
];

// ─── HTTP helpers ─────────────────────────────────────────────────────────────

export class AuthError extends Error {
  constructor() { super('HTTP 401'); this.name = 'AuthError'; }
}

async function doRequest(url: string, options: RequestInit): Promise<string> {
  const ipc = window.desktopApp?.request;

  if (ipc) {
    const headers = (options.headers ?? {}) as Record<string, string>;
    const result  = await ipc({
      url,
      method:  options.method ?? 'GET',
      headers,
      body:    typeof options.body === 'string' ? options.body : undefined
    });
    if (result.status === 401) throw new AuthError();
    if (!result.ok) throw new Error(`HTTP ${result.status}`);
    return result.text;
  }

  // Browser (non-Electron) context
  const res  = await fetch(url, options);
  const text = await res.text();
  if (res.status === 401) throw new AuthError();
  if (!res.ok) throw new Error(`HTTP ${res.status}: ${text.slice(0, 200)}`);
  return text;
}

// Uploads a file as multipart/form-data, routing through Electron IPC to avoid CORS.
async function doUpload(
  url:    string,
  token:  string,
  fields: Record<string, string>,
  file:   File
): Promise<string> {
  const ipcUpload = window.desktopApp?.upload;
  if (ipcUpload) {
    const arrayBuffer = await file.arrayBuffer();
    const result = await ipcUpload({
      urlStr:   url,
      token,
      fields,
      fileName: file.name,
      fileMime: file.type || 'application/octet-stream',
      fileData: arrayBuffer,
    });
    if (result.status === 401) throw new AuthError();
    if (!result.ok) throw new Error(`HTTP ${result.status}`);
    return result.text;
  }
  // Dev browser fallback (no CORS restriction)
  const form = new FormData();
  for (const [k, v] of Object.entries(fields)) form.append(k, v);
  form.append('file', file);
  const res  = await fetch(url, { method: 'POST', headers: { 'access-token': token }, body: form });
  const text = await res.text();
  if (res.status === 401) throw new AuthError();
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return text;
}

async function parseJson<T>(text: string): Promise<T> {
  try { return JSON.parse(text) as T; }
  catch { throw new Error(`Non-JSON response: ${text.slice(0, 200)}`); }
}

// ─── Node.js API helpers (form-encoded, matching Kotlin @FormUrlEncoded) ──────

function buildForm(data: Record<string, unknown>): string {
  const p = new URLSearchParams();
  for (const [k, v] of Object.entries(data)) {
    if (v !== undefined && v !== null) p.set(k, String(v));
  }
  return p.toString();
}

async function nodePost<T>(path: string, token: string, data: Record<string, unknown>): Promise<T> {
  const text = await doRequest(`${NODE_BASE_URL}${path}`, {
    method:  'POST',
    headers: { 'access-token': token, 'Content-Type': 'application/x-www-form-urlencoded' },
    body:    buildForm(data)
  });
  return parseJson<T>(text);
}

async function nodeGet<T>(path: string, token: string): Promise<T> {
  const text = await doRequest(`${NODE_BASE_URL}${path}`, {
    method:  'GET',
    headers: { 'access-token': token }
  });
  return parseJson<T>(text);
}

// ─── Normalizers ──────────────────────────────────────────────────────────────

function toStr(v: unknown, fb = ''): string {
  if (v == null) return fb;
  if (typeof v === 'string') return v;
  if (typeof v === 'number' || typeof v === 'boolean') return String(v);
  if (typeof v === 'object') return String((v as Record<string, unknown>).text ?? fb);
  return fb;
}

function normaliseAuth(p: Record<string, unknown>): AuthResponse {
  return {
    api_status:   String(p.api_status ?? ''),
    access_token: p.access_token as string | undefined,
    user_id:      p.user_id ? Number(p.user_id) : undefined,
    message:      toStr(p.message ?? p.error_message ?? p.messages)
  };
}

/**
 * Normalise a single raw message object.
 * For Signal (cipher_version=3) the server stores the ciphertext in the `text`
 * field, so we move it to `text_encrypted` and clear `text` so the UI doesn't
 * show the raw base64 blob.
 * Also handles alternate socket-event field names (sender_id, recipient_id, …).
 * Exported so App.tsx can normalise real-time socket events the same way.
 */
export function normaliseMessage(m: Record<string, unknown>): MessageItem {
  const cipherV   = m.cipher_version ? Number(m.cipher_version) : undefined;
  const rawText   = toStr(m.decrypted_text ?? m.text ?? m.or_text, '');
  const isSignal  = cipherV === 3;

  return {
    id:             Number(m.id ?? m.message_id),
    from_id:        Number(m.from_id ?? m.sender_id),
    to_id:          Number(m.to_id   ?? m.recipient_id ?? m.receiver_id),
    // For Signal, rawText is the base64 ciphertext — hide it from display
    text:           isSignal ? '' : rawText,
    time_text:      toStr(m.time_text ?? m.time, ''),
    time:           m.time ? Number(m.time) : undefined,
    media:          m.media ? toStr(m.media) : undefined,
    media_type:     m.media_type as MessageItem['media_type'],
    media_filename: m.media_file_name as string | undefined,
    reply_to:       m.reply as MessageItem['reply_to'],
    reactions:      m.reactions as MessageItem['reactions'],
    is_edited:      Boolean(m.is_edited),
    is_seen:        Boolean(m.is_read ?? m.seen ?? m.is_seen),
    cipher_version: cipherV,
    // Signal ciphertext: may be in 'text_encrypted' (old format) or 'text' (Kotlin)
    text_encrypted: isSignal ? (m.text_encrypted as string | undefined ?? rawText) || undefined : undefined,
    iv:             m.iv as string | undefined,
    tag:            m.tag as string | undefined,
    signal_header:  m.signal_header as string | undefined
  };
}

function normaliseMessages(payload: Record<string, unknown>): MessagesResponse {
  const arr = (payload.messages ?? []) as Record<string, unknown>[];
  return {
    api_status: String(payload.api_status ?? '200'),
    messages:   Array.isArray(arr) ? arr.map(normaliseMessage) : []
  };
}

/** Extract last-message preview from the chat list item's last_message (object or string). */
function lastMsgPreview(raw: unknown): string {
  if (!raw) return '';
  if (typeof raw === 'object') {
    const m  = raw as Record<string, unknown>;
    const cv = m.cipher_version ? Number(m.cipher_version) : 0;
    // Signal encrypted — return empty so the UI shows a translated placeholder
    // and updates to plaintext once the first decrypted message arrives via socket.
    if (cv === 3) return '';
    return toStr(m.decrypted_text ?? m.text, '');
  }
  // Raw string from server: if it looks like a base64 ciphertext return empty
  const s = toStr(raw, '');
  if (s.length > 30 && !/\s/.test(s) && /^[A-Za-z0-9+/=]+$/.test(s)) return '';
  return s;
}

function normaliseChatItem(m: Record<string, unknown>): ChatItem {
  const nameStr = toStr(
    m.name ?? m.username ?? `${m.first_name ?? ''} ${m.last_name ?? ''}`.trim(),
    'User'
  );
  return {
    user_id:      Number(m.user_id ?? m.id),
    name:         nameStr,
    avatar:       m.avatar as string | undefined,
    last_message: lastMsgPreview(m.last_message),
    time:         m.last_activity ? String(m.last_activity) : toStr(m.lastseen ?? m.time, '')
  };
}

// ─── Auth ─────────────────────────────────────────────────────────────────────

export async function login(username: string, password: string): Promise<AuthResponse> {
  const resp = await nodePost<Record<string, unknown>>('/api/node/auth/login', '', {
    username, password, device_type: 'windows'
  });
  return normaliseAuth(resp);
}

export async function loginByPhone(phone: string, password: string): Promise<AuthResponse> {
  return login(phone, password);
}

export async function registerAccount(input: {
  username: string; email?: string; phoneNumber?: string; password: string;
}): Promise<AuthResponse> {
  const resp = await nodePost<Record<string, unknown>>('/api/node/auth/register', '', {
    username:         input.username,
    email:            input.email       ?? '',
    phone_number:     input.phoneNumber ?? '',
    password:         input.password,
    confirm_password: input.password,
    gender:           'male',
    device_type:      'windows'
  });
  return normaliseAuth(resp);
}

// ─── Chats ────────────────────────────────────────────────────────────────────

export async function loadChats(token: string, _userId?: number): Promise<ChatListResponse> {
  const resp = await nodePost<Record<string, unknown>>('/api/node/chat/chats', token, {
    limit: 80, offset: 0, show_archived: 'false'
  });
  const raw  = (resp.data ?? []) as Record<string, unknown>[];
  return {
    api_status: '200',
    data:       Array.isArray(raw) ? raw.map(normaliseChatItem) : []
  };
}

// ─── Messages ─────────────────────────────────────────────────────────────────

export async function loadMessages(token: string, recipientId: number, _userId?: number): Promise<MessagesResponse> {
  const resp = await nodePost<Record<string, unknown>>('/api/node/chat/get', token, {
    recipient_id: recipientId, limit: 40, before_message_id: 0
  });
  return normaliseMessages(resp);
}

export async function loadMoreMessages(token: string, recipientId: number, beforeId: number): Promise<MessagesResponse> {
  const resp = await nodePost<Record<string, unknown>>('/api/node/chat/loadmore', token, {
    recipient_id: recipientId, before_message_id: beforeId, limit: 40
  });
  return normaliseMessages(resp);
}

export async function sendMessage(
  token:          string,
  recipientId:    number,
  text:           string,
  _userId?:       number,
  signalPayload?: {
    ciphertext:   string; // base64 — goes in 'text' field (Kotlin convention)
    iv:           string;
    tag:          string;
    signalHeader: string;
  },
  replyToId?: number
): Promise<{ id?: number }> {
  const body: Record<string, unknown> = {
    recipient_id: recipientId,
    text:         signalPayload ? signalPayload.ciphertext : text,
    ...(replyToId ? { reply_id: replyToId } : {})
  };

  if (signalPayload) {
    body.cipher_version = 3;
    body.iv             = signalPayload.iv;
    body.tag            = signalPayload.tag;
    body.signal_header  = signalPayload.signalHeader;
  }

  const resp    = await nodePost<Record<string, unknown>>('/api/node/chat/send', token, body);
  const msgData = resp.message_data as Record<string, unknown> | undefined;
  return { id: msgData ? Number(msgData.id) : undefined };
}

export async function editMessage(token: string, messageId: number, text: string): Promise<void> {
  await nodePost('/api/node/chat/edit', token, { message_id: messageId, text });
}

export async function deleteMessage(token: string, messageId: number, type: 'for_me' | 'for_all' = 'for_all'): Promise<void> {
  // Kotlin: delete_type = "just_me" | "everyone"
  await nodePost('/api/node/chat/delete', token, {
    message_id:  messageId,
    delete_type: type === 'for_all' ? 'everyone' : 'just_me'
  });
}

export async function reactToMessage(token: string, messageId: number, emoji: string): Promise<void> {
  // Kotlin field: 'reaction' not 'emoji'
  await nodePost('/api/node/chat/react', token, { message_id: messageId, reaction: emoji });
}

export async function pinMessage(token: string, messageId: number, pin: boolean): Promise<void> {
  await nodePost('/api/node/chat/pin', token, {
    message_id: messageId,
    pin:        pin ? 'yes' : 'no'
  });
}

export async function markSeen(token: string, recipientId: number, _lastId?: number): Promise<void> {
  try { await nodePost('/api/node/chat/seen', token, { recipient_id: recipientId }); }
  catch { /* non-critical */ }
}

export async function searchMessages(token: string, recipientId: number, query: string): Promise<MessagesResponse> {
  const resp = await nodePost<Record<string, unknown>>('/api/node/chat/search', token, {
    recipient_id: recipientId, query, limit: 50, offset: 0
  });
  return normaliseMessages(resp);
}

// ─── Media ────────────────────────────────────────────────────────────────────

export async function uploadMedia(token: string, file: File): Promise<MediaUploadResponse> {
  const type = file.type.startsWith('image') ? 'image'
    : file.type.startsWith('video') ? 'video'
    : file.type.startsWith('audio') ? 'audio' : 'file';
  const text = await doUpload(`${NODE_BASE_URL}/api/node/chat/upload`, token, { type }, file);
  return parseJson<MediaUploadResponse>(text);
}

export async function sendMessageWithMedia(
  token:       string,
  recipientId: number,
  text:        string,
  file:        File,
  _userId?:    number
): Promise<void> {
  // Step 1: upload the file
  const upload = await uploadMedia(token, file);
  const mediaUrl = upload.image_src ?? upload.video_src ?? upload.audio_src ?? upload.file_src ?? '';
  const mediaType = file.type.startsWith('image') ? 'image'
    : file.type.startsWith('video') ? 'video'
    : file.type.startsWith('audio') ? 'audio' : 'file';

  // Step 2: send media message
  await nodePost('/api/node/chat/send-media', token, {
    recipient_id:    recipientId,
    group_id:        0,
    media_url:       mediaUrl,
    media_type:      mediaType,
    media_file_name: file.name,
    caption:         text,
    message_hash_id: `${Date.now()}-${Math.random().toString(16).slice(2, 8)}`
  });
}

export async function sendVoiceMessage(token: string, recipientId: number, voiceFile: File): Promise<void> {
  const text   = await doUpload(`${NODE_BASE_URL}/api/node/chat/upload`, token, { type: 'voice' }, voiceFile);
  const upload = await parseJson<MediaUploadResponse>(text);
  const mediaUrl = upload.audio_src ?? upload.file_src ?? '';
  await nodePost('/api/node/chat/send-media', token, {
    recipient_id:    recipientId,
    group_id:        0,
    media_url:       mediaUrl,
    media_type:      'voice',
    media_file_name: voiceFile.name,
    caption:         '',
    message_hash_id: `${Date.now()}-${Math.random().toString(16).slice(2, 8)}`,
  });
}

// ─── Chat management ──────────────────────────────────────────────────────────

export async function muteChat(token: string, userId: number, mute: boolean): Promise<void> {
  await nodePost('/api/node/chat/mute', token, {
    chat_id:   userId,
    notify:    mute ? 'no' : 'yes',
    call_chat: 'yes'
  });
}

export async function pinChat(token: string, userId: number, pin: boolean): Promise<void> {
  await nodePost('/api/node/chat/pin-chat', token, { chat_id: userId, pin: pin ? 'yes' : 'no' });
}

export async function archiveChat(token: string, userId: number, archive: boolean): Promise<void> {
  await nodePost('/api/node/chat/archive', token, { chat_id: userId, archive: archive ? 'yes' : 'no' });
}

export async function clearHistory(token: string, userId: number): Promise<void> {
  await nodePost('/api/node/chat/clear-history', token, { recipient_id: userId });
}

export async function deleteConversation(token: string, userId: number): Promise<void> {
  await nodePost('/api/node/chat/delete-conversation', token, { user_id: userId, delete_type: 'me' });
}

export async function setChatColor(token: string, userId: number, color: string): Promise<void> {
  await nodePost('/api/node/chat/color', token, { user_id: userId, color });
}

// ─── Groups ───────────────────────────────────────────────────────────────────

export async function loadGroups(token: string): Promise<GenericListResponse<GroupItem>> {
  const resp = await nodePost<Record<string, unknown>>('/api/node/group/list', token, { limit: 50, offset: 0 });
  const raw  = (resp.groups ?? resp.data ?? []) as Record<string, unknown>[];
  const data: GroupItem[] = Array.isArray(raw) ? raw.map(g => ({
    id:           Number(g.id ?? g.group_id),
    group_name:   toStr(g.group_name ?? g.name, 'Group'),
    avatar:       g.avatar as string | undefined,
    members_count: Number(g.members_count ?? 0),
    description:  toStr(g.description, '')
  })) : [];
  return { api_status: '200', data };
}

export async function createGroup(token: string, groupName: string): Promise<void> {
  await nodePost('/api/node/group/create', token, { group_name: groupName, parts: '' });
}

export async function searchGroups(token: string, query: string): Promise<GenericListResponse<GroupItem>> {
  const resp = await nodePost<Record<string, unknown>>('/api/node/group/search', token, { query, limit: 30, offset: 0 });
  const raw  = (resp.groups ?? resp.data ?? []) as Record<string, unknown>[];
  const data: GroupItem[] = Array.isArray(raw) ? raw.map(g => ({
    id:            Number(g.id ?? g.group_id),
    group_name:    toStr(g.group_name ?? g.name, 'Group'),
    avatar:        g.avatar as string | undefined,
    members_count: Number(g.members_count ?? 0),
    description:   toStr(g.description, '')
  })) : [];
  return { api_status: '200', data };
}

export async function searchChannels(token: string, query: string): Promise<GenericListResponse<ChannelItem>> {
  const resp = await nodePost<Record<string, unknown>>('/api/node/channel/list', token, { limit: 50, offset: 0, query });
  const raw  = (resp.channels ?? resp.data ?? []) as Record<string, unknown>[];
  const data: ChannelItem[] = Array.isArray(raw) ? raw.map(c => ({
    id:                Number(c.id ?? c.channel_id),
    name:              toStr(c.name, 'Channel'),
    username:          c.username as string | undefined,
    avatar_url:        (c.avatar_url ?? c.avatar) as string | undefined,
    subscribers_count: Number(c.subscribers_count ?? 0),
    description:       toStr(c.description, '')
  })) : [];
  return { api_status: '200', data };
}

export async function loadGroupMessages(token: string, groupId: number): Promise<MessagesResponse> {
  const resp = await nodePost<Record<string, unknown>>('/api/node/group/messages/get', token, {
    group_id: groupId, limit: 40, before_message_id: 0
  });
  return normaliseGroupMessages(resp);
}

export async function loadMoreGroupMessages(token: string, groupId: number, beforeId: number): Promise<MessagesResponse> {
  const resp = await nodePost<Record<string, unknown>>('/api/node/group/messages/loadmore', token, {
    group_id: groupId, before_message_id: beforeId, limit: 40
  });
  return normaliseGroupMessages(resp);
}

export async function sendGroupMessage(
  token:    string,
  groupId:  number,
  text:     string,
  replyToId?: number
): Promise<{ id?: number }> {
  const body: Record<string, unknown> = {
    group_id: groupId,
    text,
    ...(replyToId ? { reply_id: replyToId } : {})
  };
  const resp = await nodePost<Record<string, unknown>>('/api/node/group/messages/send', token, body);
  const msgData = resp.message_data as Record<string, unknown> | undefined;
  return { id: msgData ? Number(msgData.id) : undefined };
}

export async function editGroupMessage(token: string, messageId: number, text: string): Promise<void> {
  await nodePost('/api/node/group/messages/edit', token, { message_id: messageId, text });
}

export async function deleteGroupMessage(token: string, messageId: number): Promise<void> {
  await nodePost('/api/node/group/messages/delete', token, {
    message_id: messageId, delete_type: 'everyone'
  });
}

export async function reactToGroupMessage(token: string, messageId: number, emoji: string): Promise<void> {
  await nodePost('/api/node/group/messages/react', token, { message_id: messageId, reaction: emoji }).catch(() => {});
}

export async function markGroupSeen(token: string, groupId: number): Promise<void> {
  try { await nodePost('/api/node/group/messages/seen', token, { group_id: groupId }); }
  catch { /* non-critical */ }
}

function normaliseGroupMessage(m: Record<string, unknown>): MessageItem {
  const base = normaliseMessage(m);
  return {
    ...base,
    group_id:    m.group_id ? Number(m.group_id) : undefined,
    sender_name: m.sender_name ? toStr(m.sender_name) : m.user_data
      ? toStr((m.user_data as Record<string, unknown>).name ?? (m.user_data as Record<string, unknown>).username, '')
      : undefined,
  };
}

function normaliseGroupMessages(payload: Record<string, unknown>): MessagesResponse {
  const arr = (payload.messages ?? []) as Record<string, unknown>[];
  return {
    api_status: String(payload.api_status ?? '200'),
    messages: Array.isArray(arr) ? arr.map(normaliseGroupMessage) : []
  };
}

// ─── Channels ─────────────────────────────────────────────────────────────────

export async function loadChannels(token: string): Promise<GenericListResponse<ChannelItem>> {
  const resp = await nodePost<Record<string, unknown>>('/api/node/channel/list', token, {
    type: 'get_subscribed', limit: 50, offset: 0
  });
  const raw  = (resp.channels ?? resp.data ?? []) as Record<string, unknown>[];
  const data: ChannelItem[] = Array.isArray(raw) ? raw.map(c => ({
    id:                Number(c.id ?? c.channel_id),
    name:              toStr(c.name, 'Channel'),
    username:          c.username as string | undefined,
    avatar_url:        (c.avatar_url ?? c.avatar) as string | undefined,
    subscribers_count: Number(c.subscribers_count ?? 0),
    description:       toStr(c.description, '')
  })) : [];
  return { api_status: '200', data };
}

export async function createChannel(token: string, name: string, description: string): Promise<void> {
  await nodePost('/api/node/channel/create', token, { name, description });
}

export async function loadChannelPosts(token: string, channelId: number): Promise<ChannelPostsResponse> {
  const resp = await nodePost<Record<string, unknown>>('/api/node/channel/posts', token, {
    channel_id: channelId, limit: 30, offset: 0
  });
  const raw = (resp.posts ?? resp.data ?? []) as ChannelPost[];
  return { api_status: String(resp.api_status ?? '200'), posts: Array.isArray(raw) ? raw : [] };
}

export async function loadMoreChannelPosts(token: string, channelId: number, offset: number): Promise<ChannelPostsResponse> {
  const resp = await nodePost<Record<string, unknown>>('/api/node/channel/posts', token, {
    channel_id: channelId, limit: 30, offset
  });
  const raw = (resp.posts ?? resp.data ?? []) as ChannelPost[];
  return { api_status: String(resp.api_status ?? '200'), posts: Array.isArray(raw) ? raw : [] };
}

export async function createChannelPost(
  token:      string,
  channelId:  number,
  text:       string,
  mediaUrl?:  string,
  mediaType?: string
): Promise<void> {
  const body: Record<string, unknown> = { channel_id: channelId, text };
  if (mediaUrl)  body.media      = mediaUrl;
  if (mediaType) body.media_type = mediaType;
  await nodePost('/api/node/channel/create-post', token, body);
}

export async function deleteChannelPost(token: string, postId: number): Promise<void> {
  await nodePost('/api/node/channel/delete-post', token, { post_id: postId });
}

export async function reactToChannelPost(token: string, postId: number, emoji: string): Promise<void> {
  await nodePost('/api/node/channel/post-reaction', token, { post_id: postId, reaction: emoji }).catch(() => {});
}

export async function markChannelPostViewed(token: string, postId: number): Promise<void> {
  try { await nodePost('/api/node/channel/post-view', token, { post_id: postId }); }
  catch { /* non-critical */ }
}

// ─── Stories ──────────────────────────────────────────────────────────────────

export async function loadStories(token: string): Promise<GenericListResponse<StoryItem>> {
  const resp = await nodePost<Record<string, unknown>>('/api/node/stories/get', token, { limit: 35 });
  const raw  = (resp.stories ?? resp.data ?? []) as Record<string, unknown>[];
  const data: StoryItem[] = Array.isArray(raw) ? raw.map(s => {
    const ud      = (s.user_data    ?? {}) as Record<string, unknown>;
    const images  = (s.images       ?? []) as Record<string, unknown>[];
    const videos  = (s.videos       ?? []) as Record<string, unknown>[];
    const mItems  = (s.mediaItems   ?? []) as Record<string, unknown>[];
    const vidItem = videos[0] ?? mItems.find(m => m.type === 'video');
    const imgItem = images[0] ?? mItems.find(m => m.type === 'image');
    const file    = toStr(vidItem?.filename ?? imgItem?.filename ?? s.thumbnail, '');
    const fileType: 'image' | 'video' = vidItem ? 'video' : 'image';
    const firstName = toStr(ud.first_name, '');
    const lastName  = toStr(ud.last_name, '');
    const username  = toStr(ud.username, '');
    const displayName = (firstName + ' ' + lastName).trim() || username || `User ${s.user_id}`;
    return {
      id:          Number(s.id),
      user_id:     Number(s.user_id),
      user_name:   displayName,
      user_avatar: toStr(ud.avatar, undefined),
      file,
      thumbnail:   toStr(s.thumbnail, undefined),
      file_type:   fileType,
      created_at:  s.posted ? new Date(Number(s.posted) * 1000).toLocaleDateString() : '',
      expire_time: Number(s.expire ?? 0),
      is_seen:     Number(s.is_viewed ?? 0) === 1,
      views_count: Number(s.view_count ?? 0),
    };
  }) : [];
  return { api_status: '200', data };
}

export async function markStorySeen(token: string, storyId: number): Promise<void> {
  try { await nodePost('/api/node/stories/mark-viewed', token, { story_id: storyId }); }
  catch { /* non-critical */ }
}

export async function createStory(token: string, file: File, fileType: 'image' | 'video'): Promise<void> {
  await doUpload(`${NODE_BASE_URL}/api/node/stories/create`, token, { file_type: fileType }, file);
}

// ─── Calls / ICE servers ──────────────────────────────────────────────────────

export async function getIceServers(_userId: number): Promise<RTCIceServer[]> {
  try {
    const text = await doRequest(`${NODE_BASE_URL}/api/ice-servers/`, { method: 'GET' });
    const payload = await parseJson<{ iceServers?: RTCIceServer[] } | RTCIceServer[]>(text);
    return Array.isArray(payload) ? payload : (payload.iceServers ?? []);
  } catch {
    return TURN_FALLBACK;
  }
}

export async function initiateCall(token: string, userId: number, type: 'audio' | 'video'): Promise<void> {
  await nodePost('/api/node/calls/initiate', token, { user_id: userId, call_type: type });
}

export async function answerCall(token: string, userId: number, sdp: string): Promise<void> {
  await nodePost('/api/node/calls/answer', token, { user_id: userId, sdp });
}

export async function sendIceCandidate(token: string, userId: number, candidate: RTCIceCandidateInit): Promise<void> {
  await nodePost('/api/node/calls/ice-candidate', token, { user_id: userId, candidate: JSON.stringify(candidate) });
}

export async function endCall(token: string, userId: number): Promise<void> {
  await nodePost('/api/node/calls/end', token, { user_id: userId });
}

// ─── Signal Protocol API ──────────────────────────────────────────────────────

export async function registerSignalKeys(token: string, payload: {
  identity_key:       string;
  signed_prekey_id:   number;
  signed_prekey:      string;
  signed_prekey_sig:  string;
  prekeys:            string;
}): Promise<boolean> {
  try {
    const resp = await nodePost<Record<string, unknown>>('/api/node/signal/register', token, payload);
    return (resp.api_status === 200 || resp.api_status === '200');
  } catch { return false; }
}

export async function getSignalBundle(token: string, userId: number): Promise<PreKeyBundle | null> {
  try {
    const resp = await nodeGet<Record<string, unknown>>(`/api/node/signal/bundle/${userId}`, token);
    if (!resp.identity_key || !resp.signed_prekey) return null;
    return {
      identity_key:        resp.identity_key as string,
      signed_prekey_id:    Number(resp.signed_prekey_id),
      signed_prekey:       resp.signed_prekey as string,
      signed_prekey_sig:   (resp.signed_prekey_sig ?? '') as string,
      one_time_prekey_id:  resp.one_time_prekey_id !== undefined ? Number(resp.one_time_prekey_id) : undefined,
      one_time_prekey:     resp.one_time_prekey as string | undefined
    };
  } catch { return null; }
}

export async function replenishSignalPreKeys(token: string, prekeys: string): Promise<void> {
  try { await nodePost('/api/node/signal/replenish', token, { prekeys }); }
  catch { /* non-critical */ }
}

export async function getSignalPreKeyCount(token: string): Promise<number> {
  try {
    const resp = await nodeGet<Record<string, unknown>>('/api/node/signal/prekey-count', token);
    return Number(resp.count ?? 0);
  } catch { return 0; }
}

export async function getSignalIdentityKey(token: string, userId: number): Promise<string | null> {
  try {
    const resp = await nodeGet<Record<string, unknown>>(`/api/node/signal/identity/${userId}`, token);
    return (resp.identity_key as string | undefined) ?? null;
  } catch { return null; }
}

// ─── NodeApiShim factory ──────────────────────────────────────────────────────

export function createNodeApiShim(token: string): NodeApiShim {
  return {
    registerSignalKeys:    (payload) => registerSignalKeys(token, payload),
    getSignalBundle:       (userId)  => getSignalBundle(token, userId),
    replenishSignalPreKeys:(prekeys) => replenishSignalPreKeys(token, prekeys),
    getSignalIdentityKey:  (userId)  => getSignalIdentityKey(token, userId)
  };
}

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
  BotItem,
  CallHistoryItem,
  ChannelComment,
  ChannelItem,
  ChannelPoll,
  ChannelPost,
  ChannelPostsResponse,
  ChatItem,
  ChatListResponse,
  GenericListResponse,
  GifItem,
  GroupItem,
  MediaUploadResponse,
  MessageItem,
  MessageReaction,
  MessagesResponse,
  PollOption,
  PrivacySettings,
  Sticker,
  StickerPack,
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
];

// coturn static-auth-secret — matches worldmates.club server config and Android client
const TURN_SECRET  = 'ad8a76d057d6ba0d6fd79bbc84504e320c8538b92db5c9b84fc3bd18d1c511b9';
const TURN_IPS     = ['195.22.131.11', '46.232.232.38', '93.171.188.229'];
const TURNS_DOMAIN = 'worldmates.club';

/**
 * Generate time-limited TURN credentials client-side using coturn HMAC-SHA1.
 * Exact port of Android CallsViewModel.buildHmacTurnServers().
 * username = "<expiry>:<userId>", credential = Base64(HMAC-SHA1(secret, username))
 */
export async function createTurnIceServers(userId: number): Promise<RTCIceServer[]> {
  try {
    const expiry   = Math.floor(Date.now() / 1000) + 86400;        // valid 24 h
    const username = `${expiry}:${userId}`;
    const keyMaterial = await crypto.subtle.importKey(
      'raw',
      new TextEncoder().encode(TURN_SECRET),
      { name: 'HMAC', hash: 'SHA-1' },
      false,
      ['sign']
    );
    const sig        = await crypto.subtle.sign('HMAC', keyMaterial, new TextEncoder().encode(username));
    const credential = btoa(String.fromCharCode(...new Uint8Array(sig)));

    return [
      ...TURN_IPS.map(ip => ({ urls: `stun:${ip}:3478` } as RTCIceServer)),
      ...TURN_IPS.map(ip => ({
        urls:       [`turn:${ip}:3478?transport=udp`, `turn:${ip}:3478?transport=tcp`],
        username,
        credential,
      } as RTCIceServer)),
      {
        urls:       `turns:${TURNS_DOMAIN}:5349?transport=tcp`,
        username,
        credential,
      } as RTCIceServer,
    ];
  } catch {
    return TURN_FALLBACK;
  }
}

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

// Large-file upload: gets the OS path via webUtils.getPathForFile, then streams
// the file from disk in the main process — renderer stays responsive.
const LARGE_FILE_THRESHOLD = 50 * 1024 * 1024; // 50 MB

async function doUploadLarge(
  url:    string,
  token:  string,
  fields: Record<string, string>,
  file:   File
): Promise<string> {
  const ipcLarge    = window.desktopApp?.uploadLargeFile;
  const getFilePath = window.desktopApp?.getFilePath;
  if (ipcLarge && getFilePath) {
    const filePath = getFilePath(file);
    if (filePath) {
      const result = await ipcLarge({
        urlStr:   url,
        token,
        fields,
        fileName: file.name,
        fileMime: file.type || 'application/octet-stream',
        filePath,
      });
      if (result.status === 401) throw new AuthError();
      if (!result.ok) throw new Error(`HTTP ${result.status}`);
      return result.text;
    }
  }
  // Fallback: use the in-memory path (will OOM on very large files, but handles all envs)
  return doUpload(url, token, fields, file);
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

async function nodePut<T>(path: string, token: string, data: Record<string, unknown> = {}): Promise<T> {
  const text = await doRequest(`${NODE_BASE_URL}${path}`, {
    method:  'PUT',
    headers: { 'access-token': token, 'Content-Type': 'application/x-www-form-urlencoded' },
    body:    buildForm(data)
  });
  return parseJson<T>(text);
}

async function nodeDelete<T>(path: string, token: string): Promise<T> {
  const text = await doRequest(`${NODE_BASE_URL}${path}`, {
    method:  'DELETE',
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
    // Server may use `type` field (not `media_type`) for voice/image/video/audio messages
    media_type:     (() => {
      const mt = toStr(m.media_type, '').toLowerCase();
      if (mt) return mt as MessageItem['media_type'];
      const t = toStr(m.type, '').toLowerCase();
      return (['image','video','audio','voice','file','sticker','gif'].includes(t) ? t : undefined) as MessageItem['media_type'];
    })(),
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
  // Detect type by MIME first, then fall back to extension (handles .mp3/.m4a with octet-stream MIME)
  const ext = file.name.split('.').pop()?.toLowerCase() ?? '';
  const AUDIO_EXTS = new Set(['mp3','ogg','m4a','aac','opus','flac','wav','webm']);
  const VIDEO_EXTS = new Set(['mp4','mov','mkv','m4v','avi','3gp']);
  const type = file.type.startsWith('image') ? 'image'
    : file.type.startsWith('video') ? 'video'
    : file.type.startsWith('audio') ? 'audio'
    : AUDIO_EXTS.has(ext) ? 'audio'
    : VIDEO_EXTS.has(ext) ? 'video'
    : 'file';
  // Route files > 50 MB through the streaming path so the renderer doesn't freeze
  const uploadFn = file.size > LARGE_FILE_THRESHOLD ? doUploadLarge : doUpload;
  const text = await uploadFn(`${NODE_BASE_URL}/api/node/chat/upload`, token, { type }, file);
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
  // Server may store audio/webm as video — fall back through all src fields
  const mediaUrl = upload.audio_src ?? upload.video_src ?? upload.file_src ?? '';
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

export async function joinGroup(token: string, groupId: number): Promise<void> {
  await nodePost('/api/node/group/join', token, { group_id: groupId });
}

export async function subscribeChannel(token: string, channelId: number): Promise<void> {
  await nodePost('/api/node/channel/subscribe', token, { channel_id: channelId });
}

export async function unsubscribeChannel(token: string, channelId: number): Promise<void> {
  await nodePost('/api/node/channel/unsubscribe', token, { channel_id: channelId });
}

export async function deleteChannel(token: string, channelId: number): Promise<void> {
  await nodePost('/api/node/channel/delete', token, { channel_id: channelId });
}

export type UserSearchResult = {
  id: number;
  username: string;
  first_name?: string;
  last_name?: string;
  avatar?: string;
};

export async function searchUsers(token: string, query: string): Promise<UserSearchResult[]> {
  const params = new URLSearchParams({ q: query, limit: '30', offset: '0' });
  const text = await doRequest(`${NODE_BASE_URL}/api/node/users/search?${params}`, {
    method: 'GET',
    headers: { 'access-token': token },
  });
  const payload = await parseJson<Record<string, unknown>>(text);
  const raw = (payload.users ?? payload.data ?? []) as Record<string, unknown>[];
  return Array.isArray(raw) ? raw.map(u => ({
    id:         Number(u.user_id ?? u.id ?? 0),
    username:   toStr(u.username, ''),
    first_name: toStr(u.first_name, ''),
    last_name:  toStr(u.last_name, ''),
    avatar:     toStr(u.avatar, ''),
  })) : [];
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
  const resp = await nodePost<Record<string, unknown>>('/api/node/channel/list', token, { type: 'search', query, limit: 50, offset: 0 });
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

function normalisePostMediaType(t: string): string {
  const s = t.toLowerCase();
  return s === 'photo' ? 'image' : s;
}

function normaliseChannelPost(raw: Record<string, unknown>): ChannelPost {
  // Server returns media as List<PostMedia> each with {url, type, filename}
  const rawMedia   = raw.media;
  const mediaList: Record<string, unknown>[] = Array.isArray(rawMedia) ? rawMedia : [];
  const mediaItems = mediaList.map(m => ({
    url:  toStr(m.url ?? m.file_url ?? m.filename, ''),
    type: normalisePostMediaType(toStr(m.type, 'file')),
  })).filter(m => m.url.length > 0);

  const first     = mediaItems[0];
  const mediaUrl  = first?.url;
  const mediaType = first ? normalisePostMediaType(first.type) : undefined;

  // Poll normalization
  let poll: ChannelPoll | undefined;
  if (raw.poll && typeof raw.poll === 'object') {
    const p = raw.poll as Record<string, unknown>;
    const opts = (p.options ?? []) as Record<string, unknown>[];
    poll = {
      id:                      Number(p.id ?? 0),
      question:                toStr(p.question, ''),
      poll_type:               toStr(p.poll_type, 'regular'),
      is_anonymous:            Boolean(p.is_anonymous ?? true),
      allows_multiple_answers: Boolean(p.allows_multiple_answers),
      is_closed:               Boolean(p.is_closed),
      total_votes:             Number(p.total_votes ?? 0),
      options: Array.isArray(opts) ? opts.map(o => ({
        id:         Number(o.id ?? 0),
        text:       toStr(o.text, ''),
        vote_count: Number(o.vote_count ?? 0),
        percent:    Number(o.percent ?? 0),
        is_voted:   Boolean(o.is_voted),
      } as PollOption)) : [],
    };
  }

  return {
    id:             Number(raw.id ?? 0),
    channel_id:     Number(raw.channel_id ?? 0),
    publisher_id:   Number(raw.author_id ?? raw.publisher_id ?? 0),
    text:           toStr(raw.text, ''),
    media:          mediaUrl && mediaUrl.length > 0 ? mediaUrl : undefined,
    media_type:     mediaType,
    media_items:    mediaItems.length > 0 ? mediaItems : undefined,
    poll,
    reactions:      (raw.reactions as MessageReaction[] | undefined),
    comments_count: Number(raw.comments_count ?? 0),
    views_count:    Number(raw.views_count ?? raw.view_count ?? 0),
    time:           raw.created_time ? new Date(Number(raw.created_time) * 1000).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : toStr(raw.time, undefined),
    time_unix:      raw.created_time ? Number(raw.created_time) : undefined,
    is_pinned:      Boolean(raw.is_pinned),
  };
}

export async function voteChannelPoll(token: string, pollId: number, optionIds: number[]): Promise<void> {
  await nodePost('/api/node/channel/poll/vote', token, {
    poll_id:    pollId,
    option_ids: optionIds.join(','),
  });
}

export async function loadChannelComments(token: string, postId: number, offset = 0): Promise<ChannelComment[]> {
  const resp = await nodePost<Record<string, unknown>>('/api/node/channel/comments', token, {
    post_id: postId, limit: 50, offset
  });
  const raw = (resp.comments ?? resp.data ?? []) as ChannelComment[];
  return Array.isArray(raw) ? raw : [];
}

export async function addChannelComment(token: string, postId: number, text: string, replyToId?: number): Promise<void> {
  const body: Record<string, unknown> = { post_id: postId, text, write_as: 'user' };
  if (replyToId) body.reply_to_id = replyToId;
  await nodePost('/api/node/channel/add-comment', token, body);
}

export async function deleteChannelComment(token: string, commentId: number): Promise<void> {
  await nodePost('/api/node/channel/delete-comment', token, { comment_id: commentId });
}

export async function reactToChannelComment(token: string, commentId: number, emoji: string): Promise<void> {
  await nodePost('/api/node/channel/comment-reaction', token, { comment_id: commentId, reaction: emoji });
}

export async function loadChannelPosts(token: string, channelId: number): Promise<ChannelPostsResponse> {
  const resp = await nodePost<Record<string, unknown>>('/api/node/channel/posts', token, {
    channel_id: channelId, limit: 30, offset: 0
  });
  const raw = (resp.posts ?? resp.data ?? []) as Record<string, unknown>[];
  return { api_status: String(resp.api_status ?? '200'), posts: Array.isArray(raw) ? raw.map(normaliseChannelPost) : [] };
}

export async function loadMoreChannelPosts(token: string, channelId: number, offset: number): Promise<ChannelPostsResponse> {
  const resp = await nodePost<Record<string, unknown>>('/api/node/channel/posts', token, {
    channel_id: channelId, limit: 30, offset
  });
  const raw = (resp.posts ?? resp.data ?? []) as Record<string, unknown>[];
  return { api_status: String(resp.api_status ?? '200'), posts: Array.isArray(raw) ? raw.map(normaliseChannelPost) : [] };
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

// Server stores story media as relative paths — prefix with site base
const SITE_BASE = NODE_BASE_URL.replace(':449', ''); // https://worldmates.club
function storyAbsUrl(path: string | undefined): string {
  if (!path) return '';
  if (path.startsWith('http')) return path;
  return `${SITE_BASE}/${path.replace(/^\//, '')}`;
}

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
    const rawFile = toStr(s.thumbnail ?? vidItem?.filename ?? imgItem?.filename, '');
    const file    = storyAbsUrl(rawFile);
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
      thumbnail:   storyAbsUrl(toStr(s.thumbnail, undefined)),
      file_type:   fileType,
      created_at:  s.posted ? new Date(Number(s.posted) * 1000).toLocaleDateString() : '',
      expire_time:   Number(s.expire ?? 0),
      is_seen:       Number(s.is_viewed ?? 0) === 1,
      views_count:   Number(s.view_count ?? 0),
      comment_count: Number(s.comment_count ?? 0),
      is_owner:      Boolean(s.is_owner),
      reaction: (() => {
        const r = (s.reaction ?? {}) as Record<string, unknown>;
        return {
          like:         Number(r.like  ?? 0),
          love:         Number(r.love  ?? 0),
          haha:         Number(r.haha  ?? 0),
          wow:          Number(r.wow   ?? 0),
          sad:          Number(r.sad   ?? 0),
          angry:        Number(r.angry ?? 0),
          is_reacted:   Boolean(r.is_reacted),
          reacted_type: toStr(r.type, undefined),
        };
      })(),
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

export async function deleteStory(token: string, storyId: number): Promise<void> {
  await nodePost('/api/node/stories/delete', token, { story_id: storyId }).catch(() => {});
}

export async function reactToStory(token: string, storyId: number, reaction: string): Promise<void> {
  await nodePost('/api/node/stories/react', token, { story_id: storyId, reaction }).catch(() => {});
}

export async function getStoryComments(token: string, storyId: number): Promise<import('./types').StoryComment[]> {
  try {
    const resp = await nodePost<Record<string, unknown>>('/api/node/stories/get-comments', token, { story_id: storyId, limit: 50 });
    const raw = (resp.comments ?? resp.data ?? []) as Record<string, unknown>[];
    return raw.map(c => {
      const ud = (c.user_data ?? {}) as Record<string, unknown>;
      return {
        id:          Number(c.id ?? 0),
        story_id:    Number(c.story_id ?? storyId),
        user_id:     Number(c.user_id ?? 0),
        text:        toStr(c.text, ''),
        time:        Number(c.time ?? 0),
        user_name:   toStr(ud.first_name ? `${ud.first_name} ${ud.last_name ?? ''}`.trim() : ud.username, undefined),
        user_avatar: toStr(ud.avatar, undefined),
      };
    });
  } catch { return []; }
}

export async function createStoryComment(token: string, storyId: number, text: string): Promise<import('./types').StoryComment | null> {
  try {
    const resp = await nodePost<Record<string, unknown>>('/api/node/stories/create-comment', token, { story_id: storyId, text });
    const c = (resp.comment as Record<string, unknown> | undefined) ?? resp;
    if (!c?.id) return null;
    return {
      id:       Number(c.id),
      story_id: Number(c.story_id ?? storyId),
      user_id:  Number(c.user_id ?? 0),
      text:     toStr(c.text, text),
      time:     Number(c.time ?? Math.floor(Date.now() / 1000)),
    };
  } catch { return null; }
}

export async function deleteStoryComment(token: string, commentId: number): Promise<void> {
  await nodePost('/api/node/stories/delete-comment', token, { comment_id: commentId }).catch(() => {});
}

// ─── Profile ──────────────────────────────────────────────────────────────────

export type UserProfile = {
  id:           number;
  username:     string;
  first_name?:  string;
  last_name?:   string;
  about?:       string;
  avatar?:      string;
  email?:       string;
  birthday?:    string;
  website?:     string;
  city?:        string;
  gender?:      string;
  working?:     string;
  school?:      string;
  phone?:       string;
  facebook?:    string;
  twitter?:     string;
  instagram?:   string;
  linkedin?:    string;
  youtube?:     string;
  status_emoji?: string;
  status_text?:  string;
};

export async function getMyProfile(token: string): Promise<UserProfile> {
  const resp = await nodeGet<Record<string, unknown>>('/api/node/users/me', token);
  const u = (resp.user_data ?? resp) as Record<string, unknown>;
  return {
    id:           Number(u.user_id ?? u.id ?? 0),
    username:     toStr(u.username, ''),
    first_name:   toStr(u.first_name, ''),
    last_name:    toStr(u.last_name, ''),
    about:        toStr(u.about, ''),
    avatar:       toStr(u.avatar, ''),
    email:        toStr(u.email, ''),
    birthday:     toStr(u.birthday, ''),
    website:      toStr(u.website, ''),
    city:         toStr(u.city, ''),
    gender:       toStr(u.gender, ''),
    working:      toStr(u.working, ''),
    school:       toStr(u.school, ''),
    phone:        toStr(u.phone_number ?? u.phone, ''),
    facebook:     toStr(u.facebook, ''),
    twitter:      toStr(u.twitter, ''),
    instagram:    toStr(u.instagram, ''),
    linkedin:     toStr(u.linkedin, ''),
    youtube:      toStr(u.youtube, ''),
    status_emoji: toStr(u.status_emoji, ''),
    status_text:  toStr(u.status_text, ''),
  };
}

export async function updateMyProfile(
  token: string,
  fields: {
    first_name?: string; last_name?: string; about?: string; username?: string;
    birthday?: string; website?: string; city?: string; gender?: string;
    working?: string; school?: string; phone_number?: string;
    facebook?: string; twitter?: string; instagram?: string;
    linkedin?: string; youtube?: string;
  }
): Promise<void> {
  await nodePut('/api/node/users/me', token, fields as Record<string, unknown>);
}

export async function setCustomStatus(token: string, emoji: string, text: string): Promise<void> {
  await nodePut('/api/node/users/me/status', token, {
    status_emoji: emoji || null,
    status_text:  text  || null,
  } as Record<string, unknown>).catch(() => {});
}

export interface NotificationSettings {
  email_notification:  number;
  e_liked:             number;
  e_commented:         number;
  e_followed:          number;
  e_mentioned:         number;
  e_joined_group:      number;
  e_accepted:          number;
  e_profile_wall_post: number;
  e_shared:            number;
  e_visited:           number;
}

export async function loadNotificationSettings(token: string): Promise<NotificationSettings> {
  try {
    const resp = await nodeGet<Record<string, unknown>>('/api/node/users/me', token);
    const u = (resp.user_data ?? resp) as Record<string, unknown>;
    const g = (n: unknown) => (Number(n ?? 1));
    return {
      email_notification:  g(u.email_notification),
      e_liked:             g(u.e_liked),
      e_commented:         g(u.e_commented),
      e_followed:          g(u.e_followed),
      e_mentioned:         g(u.e_mentioned),
      e_joined_group:      g(u.e_joined_group),
      e_accepted:          g(u.e_accepted),
      e_profile_wall_post: g(u.e_profile_wall_post),
      e_shared:            g(u.e_shared),
      e_visited:           g(u.e_visited),
    };
  } catch {
    return { email_notification:1, e_liked:1, e_commented:1, e_followed:1, e_mentioned:1, e_joined_group:1, e_accepted:1, e_profile_wall_post:1, e_shared:1, e_visited:1 };
  }
}

export async function updateNotificationSettings(token: string, s: Partial<NotificationSettings>): Promise<void> {
  await nodePut('/api/node/users/me/notifications', token, s as Record<string, unknown>).catch(() => {});
}

export async function uploadAvatar(token: string, file: File): Promise<string> {
  const text   = await doUpload(`${NODE_BASE_URL}/api/node/user/avatars/upload`, token, {}, file);
  const resp   = await parseJson<Record<string, unknown>>(text);
  const avatar = resp.avatar_url ?? resp.image_src ?? resp.url ?? '';
  return toStr(avatar, '');
}

// ─── Archived chats ───────────────────────────────────────────────────────────

export async function loadArchivedChats(token: string): Promise<ChatListResponse> {
  const resp = await nodePost<Record<string, unknown>>('/api/node/chat/chats', token, {
    limit: 80, offset: 0, show_archived: 'true'
  });
  const raw = (resp.data ?? []) as Record<string, unknown>[];
  return {
    api_status: '200',
    data: Array.isArray(raw) ? raw.map(normaliseChatItem) : []
  };
}

// ─── Block / unblock users ────────────────────────────────────────────────────

export async function blockUser(token: string, userId: number): Promise<void> {
  await nodePost(`/api/node/users/${userId}/block`, token, {});
}

export async function unblockUser(token: string, userId: number): Promise<void> {
  await nodeDelete(`/api/node/users/${userId}/block`, token);
}

export async function loadBlockedUsers(token: string): Promise<UserProfile[]> {
  const resp = await nodeGet<Record<string, unknown>>('/api/node/users/me/blocked', token);
  const raw  = (resp.data ?? resp.users ?? []) as Record<string, unknown>[];
  return Array.isArray(raw) ? raw.map(u => ({
    id:       Number(u.user_id ?? u.id ?? 0),
    username: toStr(u.username, ''),
    first_name: toStr(u.first_name, ''),
    last_name:  toStr(u.last_name, ''),
    avatar:     toStr(u.avatar, ''),
  })) : [];
}

// ─── Calls / ICE servers ──────────────────────────────────────────────────────

export async function getIceServers(token: string, _userId: number): Promise<RTCIceServer[]> {
  try {
    const text = await doRequest(`${NODE_BASE_URL}/api/ice-servers/`, {
      method: 'GET',
      headers: { 'access-token': token },
    });
    const payload = await parseJson<{ iceServers?: RTCIceServer[] } | RTCIceServer[]>(text);
    const servers = Array.isArray(payload) ? payload : (payload.iceServers ?? []);
    return servers.length > 0 ? servers : TURN_FALLBACK;
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

// ─── Call history (PHP v2 API) ────────────────────────────────────────────────

async function phpPost<T>(path: string, token: string, data: Record<string, string>): Promise<T> {
  const p = new URLSearchParams({ access_token: token, ...data });
  const text = await doRequest(`${API_BASE_URL.replace(/\/api\/v2\/$/, '')}${path}`, {
    method:  'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body:    p.toString()
  });
  return parseJson<T>(text);
}

export async function loadCallHistory(token: string, filter = 'all'): Promise<CallHistoryItem[]> {
  try {
    const resp = await phpPost<{ calls?: CallHistoryItem[] }>(
      '/api/v2/call_history.php', token,
      { type: 'get_history', filter, limit: '50', offset: '0' }
    );
    return resp.calls ?? [];
  } catch { return []; }
}

export async function deleteCallRecord(token: string, callId: number): Promise<void> {
  await phpPost('/api/v2/call_history.php', token, { type: 'delete_call', call_id: String(callId) });
}

export async function clearCallHistory(token: string): Promise<void> {
  await phpPost('/api/v2/call_history.php', token, { type: 'clear_history' });
}

// ─── Privacy settings ─────────────────────────────────────────────────────────

export async function loadPrivacySettings(token: string): Promise<PrivacySettings> {
  const resp = await nodeGet<Record<string, unknown>>('/api/node/users/me', token);
  const u = (resp.user_data ?? resp) as Record<string, unknown>;
  return {
    follow_privacy:          toStr(u.follow_privacy,          '0'),
    friend_privacy:          toStr(u.friend_privacy,          '0'),
    post_privacy:            toStr(u.post_privacy,            'everyone'),
    message_privacy:         toStr(u.message_privacy,         '0'),
    confirm_followers:       toStr(u.confirm_followers,       '0'),
    show_activities_privacy: toStr(u.show_activities_privacy, '1'),
    birth_privacy:           toStr(u.birth_privacy,           '0'),
    visit_privacy:           toStr(u.visit_privacy,           '0'),
    showlastseen:            toStr(u.showlastseen,            '1'),
  };
}

export async function updatePrivacySettings(token: string, settings: Partial<PrivacySettings>): Promise<void> {
  await phpPost('/api/v2/index.php', token, { type: 'update-privacy-settings', ...(settings as Record<string, string>) });
}

// ─── Stickers ─────────────────────────────────────────────────────────────────

export async function loadStickerPacks(token: string): Promise<StickerPack[]> {
  try {
    const resp = await nodeGet<{ packs?: StickerPack[] }>('/api/node/stickers', token);
    return resp.packs ?? [];
  } catch { return []; }
}

export async function sendStickerMessage(token: string, recipientId: number, url: string): Promise<void> {
  await nodePost('/api/node/chat/send', token, { recipient_id: recipientId, media: url, media_type: 'sticker', text: '' });
}

export async function sendGifMessage(token: string, recipientId: number, url: string): Promise<void> {
  await nodePost('/api/node/chat/send', token, { recipient_id: recipientId, media: url, media_type: 'gif', text: '' });
}

// ─── GIFs (GIPHY) ────────────────────────────────────────────────────────────

const GIPHY_KEY = '6jkmmXp7Pjkxl4uvXO5AUcL4pl22QrWA';

function normaliseGiphy(data: Record<string, unknown>[]): GifItem[] {
  return data.map(g => {
    const imgs = (g.images ?? {}) as Record<string, Record<string, string>>;
    return {
      id:         String(g.id ?? ''),
      title:      String(g.title ?? ''),
      url:        imgs.original?.url ?? '',
      previewUrl: imgs.fixed_width?.url ?? imgs.original?.url ?? '',
    };
  });
}

export async function loadTrendingGifs(limit = 20): Promise<GifItem[]> {
  try {
    const url  = `https://api.giphy.com/v1/gifs/trending?api_key=${GIPHY_KEY}&limit=${limit}&rating=g`;
    const res  = await fetch(url);
    const data = await res.json() as { data?: Record<string, unknown>[] };
    return normaliseGiphy(data.data ?? []);
  } catch { return []; }
}

export async function searchGifs(query: string, limit = 20): Promise<GifItem[]> {
  if (!query.trim()) return loadTrendingGifs(limit);
  try {
    const url  = `https://api.giphy.com/v1/gifs/search?api_key=${GIPHY_KEY}&q=${encodeURIComponent(query)}&limit=${limit}&rating=g`;
    const res  = await fetch(url);
    const data = await res.json() as { data?: Record<string, unknown>[] };
    return normaliseGiphy(data.data ?? []);
  } catch { return []; }
}

// ─── Bot search ───────────────────────────────────────────────────────────────

export async function searchBots(query: string, limit = 20): Promise<BotItem[]> {
  try {
    const text = await doRequest(`${NODE_BASE_URL}/api/node/bots/search?q=${encodeURIComponent(query)}&limit=${limit}&offset=0`, {
      method: 'GET', headers: {}
    });
    const resp = await parseJson<{ bots?: Record<string, unknown>[] }>(text);
    return (resp.bots ?? []).map(b => ({
      bot_id_str:   String(b.bot_id ?? ''),
      user_id:      Number(b.linked_user_id ?? 0),
      username:     String(b.username ?? ''),
      display_name: String(b.display_name ?? b.displayName ?? b.username ?? ''),
      avatar:       b.avatar ? String(b.avatar) : undefined,
      description:  b.description ? String(b.description) : undefined,
      web_app_url:  b.web_app_url ? String(b.web_app_url) : undefined,
    }));
  } catch { return []; }
}

export async function getBotLinkedUser(token: string, botIdStr: string): Promise<number> {
  try {
    const resp = await nodeGet<Record<string, unknown>>(`/api/node/bots/${encodeURIComponent(botIdStr)}`, token);
    // Server wraps bot data under resp.bot
    const bot = (resp.bot as Record<string, unknown> | undefined) ?? resp;
    return Number(bot.linked_user_id ?? bot.user_id ?? resp.linked_user_id ?? 0);
  } catch { return 0; }
}

// ─── Saved Messages ───────────────────────────────────────────────────────────

export interface SavedMessageItem {
  id?:           number;
  message_id:    number;
  chat_type:     'chat' | 'group' | 'channel';
  chat_id:       number;
  chat_name:     string;
  sender_name:   string;
  text:          string;
  media_url?:    string;
  media_type?:   string;
  saved_at:      number;
  original_time: number;
}

export async function listSaved(token: string): Promise<SavedMessageItem[]> {
  try {
    const resp = await nodeGet<Record<string, unknown>>('/api/node/saved/list', token);
    const raw = (resp.saved ?? resp.items ?? []) as Record<string, unknown>[];
    return raw.map(s => ({
      id:            Number(s.id ?? 0)  || undefined,
      message_id:    Number(s.message_id ?? s.messageId ?? 0),
      chat_type:     String(s.chat_type ?? s.chatType ?? 'chat') as SavedMessageItem['chat_type'],
      chat_id:       Number(s.chat_id ?? s.chatId ?? 0),
      chat_name:     String(s.chat_name ?? s.chatName ?? ''),
      sender_name:   String(s.sender_name ?? s.senderName ?? ''),
      text:          String(s.text ?? ''),
      media_url:     s.media_url ? String(s.media_url) : undefined,
      media_type:    s.media_type ? String(s.media_type) : undefined,
      saved_at:      Number(s.saved_at ?? s.savedAt ?? 0),
      original_time: Number(s.original_time ?? s.originalTime ?? 0),
    }));
  } catch { return []; }
}

export async function saveMessage(
  token:        string,
  item: Omit<SavedMessageItem, 'id' | 'saved_at'>
): Promise<boolean> {
  try {
    await nodePost('/api/node/saved/save', token, {
      message_id:    item.message_id,
      chat_type:     item.chat_type,
      chat_id:       item.chat_id,
      chat_name:     item.chat_name,
      sender_name:   item.sender_name,
      text:          item.text,
      media_url:     item.media_url,
      media_type:    item.media_type,
      original_time: item.original_time,
    });
    return true;
  } catch { return false; }
}

export async function unsaveMessage(token: string, messageId: number, chatType: string): Promise<boolean> {
  try {
    await nodePost('/api/node/saved/unsave', token, { message_id: messageId, chat_type: chatType });
    return true;
  } catch { return false; }
}

export async function clearSaved(token: string): Promise<void> {
  await nodePost('/api/node/saved/clear', token, {}).catch(() => {});
}

// ─── Notes ────────────────────────────────────────────────────────────────────

export interface NoteItem {
  id:          number;
  type:        'text' | 'image' | 'video' | 'audio' | 'file';
  text?:       string;
  file_name?:  string;
  file_size:   number;
  mime_type?:  string;
  created_at:  number;
}

export interface NotesStorageInfo {
  used_bytes:  number;
  quota_bytes: number;
}

export async function listNotes(token: string, limit = 50, offset = 0): Promise<NoteItem[]> {
  try {
    const resp = await nodeGet<Record<string, unknown>>(
      `/api/node/notes?limit=${limit}&offset=${offset}`, token
    );
    const raw = (resp.notes ?? resp.items ?? []) as Record<string, unknown>[];
    return raw.map(n => ({
      id:         Number(n.id ?? 0),
      type:       (String(n.type ?? 'text')) as NoteItem['type'],
      text:       n.text ? String(n.text) : undefined,
      file_name:  n.file_name ?? n.fileName ? String(n.file_name ?? n.fileName) : undefined,
      file_size:  Number(n.file_size ?? n.fileSize ?? 0),
      mime_type:  n.mime_type ?? n.mimeType ? String(n.mime_type ?? n.mimeType) : undefined,
      created_at: Number(n.created_at ?? n.createdAt ?? 0),
    }));
  } catch { return []; }
}

export async function createNote(token: string, text: string): Promise<NoteItem | null> {
  try {
    const resp = await nodePost<Record<string, unknown>>('/api/node/notes/create', token, { text });
    const n = (resp.note as Record<string, unknown> | undefined) ?? resp;
    if (!n || !n.id) return null;
    return {
      id:         Number(n.id),
      type:       (String(n.type ?? 'text')) as NoteItem['type'],
      text:       n.text ? String(n.text) : undefined,
      file_name:  undefined,
      file_size:  0,
      mime_type:  undefined,
      created_at: Number(n.created_at ?? n.createdAt ?? Date.now() / 1000),
    };
  } catch { return null; }
}

export async function deleteNote(token: string, id: number): Promise<void> {
  await nodePost(`/api/node/notes/${id}/delete`, token, {}).catch(() => {});
}

export async function getNotesStorage(token: string): Promise<NotesStorageInfo> {
  try {
    const resp = await nodeGet<Record<string, unknown>>('/api/node/notes/storage', token);
    return {
      used_bytes:  Number(resp.used_bytes  ?? resp.usedBytes  ?? 0),
      quota_bytes: Number(resp.quota_bytes ?? resp.quotaBytes ?? 0),
    };
  } catch { return { used_bytes: 0, quota_bytes: 0 }; }
}

// ─── Scheduled Messages ────────────────────────────────────────────────────────

export interface ScheduledMessage {
  id:          number;
  recipient_id: number;
  text:        string;
  media?:      string;
  media_type?: string;
  send_at:     number;   // unix timestamp
  created_at:  number;
}

export async function listScheduledMessages(token: string, recipientId: number): Promise<ScheduledMessage[]> {
  try {
    const resp = await nodeGet<Record<string, unknown>>(
      `/api/node/scheduled/list?recipient_id=${recipientId}`, token
    );
    const raw = (resp.messages ?? resp.data ?? []) as Record<string, unknown>[];
    return raw.map(m => ({
      id:           Number(m.id ?? 0),
      recipient_id: Number(m.recipient_id ?? recipientId),
      text:         String(m.text ?? ''),
      media:        m.media ? String(m.media) : undefined,
      media_type:   m.media_type ? String(m.media_type) : undefined,
      send_at:      Number(m.send_at ?? m.scheduled_at ?? 0),
      created_at:   Number(m.created_at ?? 0),
    }));
  } catch { return []; }
}

export async function createScheduledMessage(
  token: string,
  recipientId: number,
  text: string,
  sendAt: number
): Promise<ScheduledMessage | null> {
  try {
    const resp = await nodePost<Record<string, unknown>>('/api/node/scheduled/create', token, {
      recipient_id: recipientId,
      text,
      send_at: sendAt,
    });
    const m = (resp.message ?? resp.data ?? resp) as Record<string, unknown>;
    if (!m || !m.id) return null;
    return {
      id:           Number(m.id),
      recipient_id: recipientId,
      text:         String(m.text ?? text),
      send_at:      Number(m.send_at ?? sendAt),
      created_at:   Number(m.created_at ?? Date.now() / 1000),
    };
  } catch { return null; }
}

export async function deleteScheduledMessage(token: string, id: number): Promise<void> {
  await nodePost(`/api/node/scheduled/${id}/delete`, token, {}).catch(() => {});
}

export async function sendScheduledNow(token: string, id: number): Promise<void> {
  await nodePost(`/api/node/scheduled/${id}/send-now`, token, {}).catch(() => {});
}

// ─── Batch 5: Channel admin API ────────────────────────────────────────────────

import type {
  ChannelStatistics, ChannelAdmin, ChannelSettings,
  ChannelSubscriber, ChannelBannedMember, ActiveMember,
  GroupMemberFull, GroupSettings, GroupStatistics,
  GroupJoinRequest, AdminLogEntry,
} from './types';

export async function getChannelStatistics(token: string, channelId: number): Promise<ChannelStatistics | null> {
  try {
    const r = await nodePost<Record<string, unknown>>('/api/node/channel/statistics', token, { channel_id: channelId });
    const s = (r.statistics ?? r) as Record<string, unknown>;
    return {
      subscribers_count:      Number(s.subscribers_count ?? 0),
      new_subscribers_today:  Number(s.new_subscribers_today ?? 0),
      new_subscribers_week:   Number(s.new_subscribers_week ?? 0),
      left_subscribers_week:  Number(s.left_subscribers_week ?? 0),
      growth_rate:            Number(s.growth_rate ?? 0),
      active_subscribers_24h: Number(s.active_subscribers_24h ?? 0),
      subscribers_by_day:     Array.isArray(s.subscribers_by_day) ? (s.subscribers_by_day as number[]) : undefined,
      posts_count:            Number(s.posts_count ?? 0),
      posts_today:            Number(s.posts_today ?? 0),
      posts_last_week:        Number(s.posts_last_week ?? 0),
      posts_this_month:       Number(s.posts_this_month ?? 0),
      views_total:            Number(s.views_total ?? 0),
      views_last_week:        Number(s.views_last_week ?? 0),
      avg_views_per_post:     Number(s.avg_views_per_post ?? 0),
      views_by_day:           Array.isArray(s.views_by_day) ? (s.views_by_day as number[]) : undefined,
      reactions_total:        Number(s.reactions_total ?? 0),
      comments_total:         Number(s.comments_total ?? 0),
      engagement_rate:        Number(s.engagement_rate ?? 0),
      media_posts_count:      Number(s.media_posts_count ?? 0),
      text_posts_count:       Number(s.text_posts_count ?? 0),
      peak_hours:             Array.isArray(s.peak_hours) ? (s.peak_hours as number[]) : undefined,
      hourly_views:           Array.isArray(s.hourly_views) ? (s.hourly_views as number[]) : undefined,
      top_posts:              Array.isArray(s.top_posts) ? (s.top_posts as Record<string, unknown>[]).map(p => ({
        id: Number(p.id ?? 0), text: String(p.text ?? ''),
        views: Number(p.views ?? 0), reactions: Number(p.reactions ?? 0),
        comments: Number(p.comments ?? 0), published_time: Number(p.published_time ?? 0),
        has_media: Boolean(p.has_media),
      })) : undefined,
    };
  } catch { return null; }
}

export async function loadChannelAdmins(token: string, channelId: number): Promise<ChannelAdmin[]> {
  try {
    const r = await nodePost<Record<string, unknown>>('/api/node/channel/admins', token, { channel_id: channelId });
    const list = (r.admins ?? r.data ?? []) as Record<string, unknown>[];
    return list.map(a => ({
      user_id: Number(a.user_id ?? 0), username: String(a.username ?? ''),
      avatar: a.avatar ? String(a.avatar) : undefined,
      role: (['owner','admin','moderator'].includes(String(a.role)) ? a.role : 'admin') as ChannelAdmin['role'],
      added_time: Number(a.added_time ?? 0),
    }));
  } catch { return []; }
}

export async function addChannelAdmin(token: string, channelId: number, userId: number, role: string): Promise<void> {
  await nodePost('/api/node/channel/add-admin', token, { channel_id: channelId, user_id: userId, role });
}

export async function removeChannelAdmin(token: string, channelId: number, userId: number): Promise<void> {
  await nodePost('/api/node/channel/remove-admin', token, { channel_id: channelId, user_id: userId });
}

export async function loadChannelSubscribers(token: string, channelId: number, limit = 50, offset = 0): Promise<ChannelSubscriber[]> {
  try {
    const r = await nodePost<Record<string, unknown>>('/api/node/channel/subscribers', token, { channel_id: channelId, limit, offset });
    const list = (r.subscribers ?? r.data ?? []) as Record<string, unknown>[];
    return list.map(s => ({
      user_id: s.user_id ? Number(s.user_id) : undefined,
      username: s.username ? String(s.username) : undefined,
      name: s.name ? String(s.name) : undefined,
      avatar: s.avatar ? String(s.avatar) : undefined,
      subscribed_time: s.subscribed_time ? Number(s.subscribed_time) : undefined,
      is_muted: Boolean(s.is_muted), is_banned: Boolean(s.is_banned),
      role: s.role ? String(s.role) : undefined,
    }));
  } catch { return []; }
}

export async function loadChannelBanned(token: string, channelId: number, limit = 50, offset = 0): Promise<ChannelBannedMember[]> {
  try {
    const r = await nodePost<Record<string, unknown>>('/api/node/channel/banned', token, { channel_id: channelId, limit, offset });
    const list = (r.banned ?? r.data ?? []) as Record<string, unknown>[];
    return list.map(b => ({
      user_id: Number(b.user_id ?? 0), username: b.username ? String(b.username) : undefined,
      name: b.name ? String(b.name) : undefined, avatar: b.avatar ? String(b.avatar) : undefined,
      reason: b.reason ? String(b.reason) : undefined,
      banned_until: b.banned_until ? Number(b.banned_until) : undefined,
      banned_time: b.banned_time ? Number(b.banned_time) : undefined,
    }));
  } catch { return []; }
}

export async function banChannelMember(token: string, channelId: number, userId: number, reason?: string, durationSeconds?: number): Promise<void> {
  await nodePost('/api/node/channel/ban-member', token, {
    channel_id: channelId, user_id: userId,
    ...(reason ? { reason } : {}),
    ...(durationSeconds ? { duration_seconds: durationSeconds } : {}),
  });
}

export async function unbanChannelMember(token: string, channelId: number, userId: number): Promise<void> {
  await nodePost('/api/node/channel/unban-member', token, { channel_id: channelId, user_id: userId });
}

export async function kickChannelMember(token: string, channelId: number, userId: number): Promise<void> {
  await nodePost('/api/node/channel/kick-member', token, { channel_id: channelId, user_id: userId });
}

export async function updateChannelInfo(token: string, channelId: number, name: string, description: string, username?: string): Promise<void> {
  await nodePost('/api/node/channel/update', token, {
    channel_id: channelId, name, description,
    ...(username ? { username } : {}),
  });
}

export async function updateChannelSettings(token: string, channelId: number, settings: Partial<ChannelSettings>): Promise<void> {
  await nodePost('/api/node/channel/settings', token, { channel_id: channelId, settings_json: JSON.stringify(settings) });
}

export async function getChannelActiveMembers(token: string, channelId: number, periodDays = 30): Promise<ActiveMember[]> {
  try {
    const r = await nodePost<Record<string, unknown>>('/api/node/channel/active-members', token, { channel_id: channelId, period_days: periodDays });
    const list = (r.members ?? r.data ?? []) as Record<string, unknown>[];
    return list.map(m => ({
      user_id: Number(m.user_id ?? 0), username: m.username ? String(m.username) : undefined,
      name: m.name ? String(m.name) : undefined, avatar_url: m.avatar_url ? String(m.avatar_url) : undefined,
      comment_count: Number(m.comment_count ?? 0), reaction_count: Number(m.reaction_count ?? 0),
      score: Number(m.score ?? 0),
    }));
  } catch { return []; }
}

export async function createChannelFull(token: string, name: string, description: string, username?: string, isPrivate?: boolean): Promise<number | null> {
  try {
    const r = await nodePost<Record<string, unknown>>('/api/node/channel/create', token, {
      name, description,
      ...(username ? { username } : {}),
      ...(isPrivate !== undefined ? { is_private: isPrivate } : {}),
    });
    return r.channel_id ? Number(r.channel_id) : (r.id ? Number(r.id) : null);
  } catch { return null; }
}

// ─── Batch 5: Group admin API ──────────────────────────────────────────────────

export async function loadGroupMembers(token: string, groupId: number, limit = 100): Promise<GroupMemberFull[]> {
  try {
    const r = await nodePost<Record<string, unknown>>('/api/node/group/members', token, { group_id: groupId, limit, offset: 0 });
    const list = (r.members ?? r.data ?? []) as Record<string, unknown>[];
    return list.map(m => ({
      user_id: Number(m.user_id ?? 0), username: String(m.username ?? ''),
      avatar: m.avatar ? String(m.avatar) : undefined,
      role: (['owner','admin','moderator','member'].includes(String(m.role)) ? m.role : 'member') as GroupMemberFull['role'],
      joined_time: Number(m.joined_time ?? 0),
      is_muted: Boolean(m.is_muted), is_blocked: Boolean(m.is_blocked),
      permissions: Array.isArray(m.permissions) ? (m.permissions as string[]) : undefined,
    }));
  } catch { return []; }
}

export async function setGroupMemberRole(token: string, groupId: number, userId: number, role: string): Promise<void> {
  await nodePost('/api/node/group/set-role', token, { group_id: groupId, user_id: userId, role });
}

export async function removeGroupMember(token: string, groupId: number, userId: number): Promise<void> {
  await nodePost('/api/node/group/remove-member', token, { group_id: groupId, user_id: userId });
}

export async function banGroupMember(token: string, groupId: number, userId: number, reason?: string): Promise<void> {
  await nodePost('/api/node/group/ban-member', token, { group_id: groupId, user_id: userId, ...(reason ? { reason } : {}) });
}

export async function unbanGroupMember(token: string, groupId: number, userId: number): Promise<void> {
  await nodePost('/api/node/group/unban-member', token, { group_id: groupId, user_id: userId });
}

export async function loadGroupJoinRequests(token: string, groupId: number): Promise<GroupJoinRequest[]> {
  try {
    const r = await nodePost<Record<string, unknown>>('/api/node/group/join-requests', token, { group_id: groupId });
    const list = (r.requests ?? r.data ?? []) as Record<string, unknown>[];
    return list.map(req => ({
      id: Number(req.id ?? 0), group_id: groupId, user_id: Number(req.user_id ?? 0),
      username: String(req.username ?? ''), user_avatar: req.user_avatar ? String(req.user_avatar) : undefined,
      message: req.message ? String(req.message) : undefined,
      status: (['pending','approved','rejected'].includes(String(req.status)) ? req.status : 'pending') as GroupJoinRequest['status'],
      created_time: Number(req.created_time ?? 0),
    }));
  } catch { return []; }
}

export async function approveGroupJoinRequest(token: string, groupId: number, requestId: number): Promise<void> {
  await nodePost('/api/node/group/approve-join', token, { group_id: groupId, request_id: requestId });
}

export async function rejectGroupJoinRequest(token: string, groupId: number, requestId: number): Promise<void> {
  await nodePost('/api/node/group/reject-join', token, { group_id: groupId, request_id: requestId });
}

export async function updateGroupSettings(token: string, groupId: number, settings: Partial<GroupSettings>): Promise<void> {
  await nodePost('/api/node/group/settings', token, { group_id: groupId, ...settings });
}

export async function updateGroupInfo(token: string, groupId: number, name: string, description: string, isPrivate: boolean): Promise<void> {
  await nodePost('/api/node/group/update', token, { group_id: groupId, group_name: name, description, is_private: isPrivate });
}

export async function getGroupStatistics(token: string, groupId: number): Promise<GroupStatistics | null> {
  try {
    const r = await nodePost<Record<string, unknown>>('/api/node/group/statistics', token, { group_id: groupId });
    const s = (r.statistics ?? r.data ?? r) as Record<string, unknown>;
    return {
      group_id:            Number(s.group_id ?? groupId),
      members_count:       Number(s.members_count ?? 0),
      messages_count:      Number(s.messages_count ?? 0),
      messages_today:      Number(s.messages_today ?? 0),
      messages_this_week:  Number(s.messages_this_week ?? 0),
      messages_this_month: Number(s.messages_this_month ?? 0),
      active_members_24h:  Number(s.active_members_24h ?? 0),
      active_members_week: Number(s.active_members_week ?? 0),
      media_count:         Number(s.media_count ?? 0),
      links_count:         Number(s.links_count ?? 0),
      new_members_today:   Number(s.new_members_today ?? 0),
      new_members_week:    Number(s.new_members_week ?? 0),
      left_members_week:   Number(s.left_members_week ?? 0),
      top_contributors:    Array.isArray(s.top_contributors) ? (s.top_contributors as Record<string, unknown>[]).map(c => ({
        user_id: Number(c.user_id ?? 0), username: String(c.username ?? ''),
        name: c.name ? String(c.name) : undefined, avatar: c.avatar ? String(c.avatar) : undefined,
        messages_count: Number(c.messages_count ?? 0),
      })) : undefined,
      peak_hours: Array.isArray(s.peak_hours) ? (s.peak_hours as number[]) : undefined,
      growth_rate: Number(s.growth_rate ?? 0),
    };
  } catch { return null; }
}

export async function loadGroupAdminLogs(token: string, groupId: number, page = 1): Promise<{ logs: AdminLogEntry[]; total: number }> {
  try {
    const r = await nodePost<Record<string, unknown>>('/api/node/group/admin-logs', token, { group_id: groupId, page, limit: 50 });
    const list = (r.logs ?? r.data ?? []) as Record<string, unknown>[];
    return {
      total: Number(r.total ?? list.length),
      logs: list.map(l => ({
        id: l.id ? Number(l.id) : undefined,
        admin_id: l.admin_id ? Number(l.admin_id) : undefined,
        admin_name: String(l.admin_name ?? l.adminName ?? ''),
        admin_avatar: l.admin_avatar ? String(l.admin_avatar) : undefined,
        action: String(l.action ?? ''),
        target_user_name: l.target_user_name ? String(l.target_user_name) : undefined,
        details: l.details ? String(l.details) : undefined,
        created_at: String(l.created_at ?? l.createdAt ?? ''),
      })),
    };
  } catch { return { logs: [], total: 0 }; }
}

// ─── Media gallery ────────────────────────────────────────────────────────────

export type MediaGalleryItem = {
  url:     string;
  type:    'image' | 'video' | 'gif';
  thumb?:  string;
  caption?: string;
  date?:   string;
  sender?: string;
};

/** Load all media messages from a 1-on-1 chat (up to 3 pages × 40). */
export async function getChatMedia(token: string, recipientId: number): Promise<MediaGalleryItem[]> {
  const MEDIA_TYPES = new Set(['image', 'video', 'gif', 'photo']);
  const MEDIA_EXTS  = /\.(jpg|jpeg|png|gif|webp|mp4|webm|mov|mkv)$/i;
  const items: MediaGalleryItem[] = [];

  let beforeId = 0;
  for (let page = 0; page < 4; page++) {
    const resp = await nodePost<Record<string, unknown>>(
      page === 0 ? '/api/node/chat/get' : '/api/node/chat/loadmore',
      token,
      { recipient_id: recipientId, limit: 40, before_message_id: beforeId }
    );
    const msgs = normaliseMessages(resp).messages ?? [];
    if (!msgs.length) break;
    for (const m of msgs) {
      if (!m.media) continue;
      const mt = (m.media_type ?? '').toLowerCase();
      const isMedia = MEDIA_TYPES.has(mt) || MEDIA_EXTS.test(m.media);
      if (!isMedia) continue;
      const type: MediaGalleryItem['type'] = mt === 'video' || /\.(mp4|webm|mov|mkv)$/i.test(m.media)
        ? 'video' : mt === 'gif' ? 'gif' : 'image';
      items.push({
        url:     resolveUrl(m.media),
        type,
        caption: m.text || undefined,
        date:    m.time ? String(m.time) : undefined,
      });
    }
    beforeId = msgs[msgs.length - 1].id ?? 0;
  }
  return items;
}

/** Load all media messages from a group. */
export async function getGroupMedia(token: string, groupId: number): Promise<MediaGalleryItem[]> {
  const MEDIA_TYPES = new Set(['image', 'video', 'gif', 'photo']);
  const MEDIA_EXTS  = /\.(jpg|jpeg|png|gif|webp|mp4|webm|mov|mkv)$/i;
  const items: MediaGalleryItem[] = [];

  let beforeId = 0;
  for (let page = 0; page < 4; page++) {
    const resp = await nodePost<Record<string, unknown>>(
      page === 0 ? '/api/node/group/messages/get' : '/api/node/group/messages/loadmore',
      token,
      { group_id: groupId, limit: 40, before_message_id: beforeId }
    );
    const msgs = normaliseMessages(resp).messages ?? [];
    if (!msgs.length) break;
    for (const m of msgs) {
      if (!m.media) continue;
      const mt = (m.media_type ?? '').toLowerCase();
      const isMedia = MEDIA_TYPES.has(mt) || MEDIA_EXTS.test(m.media);
      if (!isMedia) continue;
      const type: MediaGalleryItem['type'] = mt === 'video' || /\.(mp4|webm|mov|mkv)$/i.test(m.media)
        ? 'video' : mt === 'gif' ? 'gif' : 'image';
      items.push({
        url:     resolveUrl(m.media),
        type,
        caption: m.text || undefined,
        date:    m.time ? String(m.time) : undefined,
      });
    }
    beforeId = msgs[msgs.length - 1].id ?? 0;
  }
  return items;
}

/** Load all media posts from a channel. */
export async function getChannelMedia(token: string, channelId: number): Promise<MediaGalleryItem[]> {
  const MEDIA_EXTS = /\.(jpg|jpeg|png|gif|webp|mp4|webm|mov|mkv)$/i;
  const items: MediaGalleryItem[] = [];

  let offset = 0;
  for (let page = 0; page < 4; page++) {
    const resp = await nodePost<Record<string, unknown>>('/api/node/channel/posts', token, {
      channel_id: channelId, limit: 40, offset
    });
    const raw = (resp.posts ?? resp.data ?? []) as Record<string, unknown>[];
    if (!raw.length) break;
    for (const p of raw) {
      const mediaUrl = String(p.media ?? p.image_src ?? p.video_src ?? '');
      if (!mediaUrl || !MEDIA_EXTS.test(mediaUrl)) continue;
      const isVideo = /\.(mp4|webm|mov|mkv)$/i.test(mediaUrl);
      items.push({
        url:     resolveUrl(mediaUrl),
        type:    isVideo ? 'video' : 'image',
        caption: p.text ? String(p.text).slice(0, 80) : undefined,
        date:    p.created_at ? String(p.created_at) : undefined,
      });
    }
    offset += raw.length;
    if (raw.length < 40) break;
  }
  return items;
}

function resolveUrl(path: string): string {
  if (!path) return '';
  if (path.startsWith('http')) return path;
  return `${(window as Window & { __API_BASE__?: string }).__API_BASE__ ?? ''}/uploads/${path.replace(/^\/?(uploads\/)?/, '')}`;
}

/**
 * WorldMates API client — dual-tier:
 *   Tier 1  PHP  REST API  (v2, legacy)     → https://worldmates.club/api/v2/
 *   Tier 2  Node.js REST API (primary)      → https://worldmates.club:449/
 *
 * All auth uses Bearer-style "access-token" header for the Node API, and
 * server_key + access_token query params for the PHP API.
 */

import type {
  AuthResponse,
  ChannelItem,
  ChatItem,
  ChatListResponse,
  GenericListResponse,
  GroupItem,
  MediaUploadResponse,
  MessageItem,
  MessagesResponse,
  NodeChatsResponse,
  NodeGroupsResponse,
  NodeMessagesResponse,
  NodeChannelsResponse,
  NodeStoriesResponse,
  SignalBundleResponse,
  StoryItem
} from './types';
import type { NodeApiShim, PreKeyBundle } from './signalService';

// ─── Config ───────────────────────────────────────────────────────────────────

export const API_BASE_URL       = 'https://worldmates.club/api/v2/';
export const NODE_BASE_URL      = 'https://worldmates.club:449';
export const SOCKET_URL         = 'https://worldmates.club:449/';
export const REGISTER_PATH      = 'https://worldmates.club/phone/register_user.php?type=user_registration';
export const WINDOWS_APP_BASE_URL = 'https://worldmates.club/api/windows_app/';

export const SITE_ENCRYPT_KEY   = '2ad9c757daccdfff436dc226779e20b719f6d6f8';
export const SERVER_KEY         = 'a8975daa76d7197ab87412b096696bb0e341eb4d-9bb411ab89d8a290362726fca6129e76-81746510';

const ENDPOINTS = {
  auth:               '?type=auth',
  chats:              '?type=get_chats',
  messages:           '?type=get_user_messages',
  sendMessage:        '?type=send-message',
  sendMessageLegacy:  '?type=send_message',
  uploadMedia:        '?type=upload_media',
  groups:             '/api/v2/group_chat_v2.php',
  channels:           '/api/v2/channels.php',
  stories:            '/api/v2/endpoints/get-stories.php',
  createStory:        '/api/v2/endpoints/create-story.php',
  iceServers:         '/api/ice-servers/',

  windowsLogin:       'login.php?type=user_login',
  windowsUsersList:   'get_users_list.php?type=get_users_list',
  windowsMessages:    'get_user_messages.php?type=get_user_messages',
  windowsInsertMsg:   'insert_new_message.php?type=insert_new_message'
};

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
  const res  = await fetch(url, options);
  const text = await res.text();
  if (res.status === 401) throw new AuthError();
  if (!res.ok) throw new Error(`HTTP ${res.status}: ${text.slice(0, 200)}`);
  return text;
}

async function parseJson<T>(text: string): Promise<T> {
  try { return JSON.parse(text) as T; }
  catch { throw new Error(`Non-JSON response: ${text.slice(0, 200)}`); }
}

// ─── PHP API helpers ──────────────────────────────────────────────────────────

function v2Url(endpoint: string, token?: string): string {
  const url = new URL(`${API_BASE_URL}${endpoint}`.replace('/api/v2//api/v2/', '/api/v2/'));
  url.searchParams.set('s', SITE_ENCRYPT_KEY);
  if (token) url.searchParams.set('access_token', token);
  return url.toString();
}

function absUrl(path: string, token?: string): string {
  const url = new URL(`https://worldmates.club${path}`);
  url.searchParams.set('s', SITE_ENCRYPT_KEY);
  if (token) url.searchParams.set('access_token', token);
  return url.toString();
}

function winUrl(endpoint: string): string {
  return `${WINDOWS_APP_BASE_URL}${endpoint}`;
}

function phpBody(payload: Record<string, unknown>): URLSearchParams {
  const body = new URLSearchParams();
  body.set('server_key', SERVER_KEY);
  for (const [k, v] of Object.entries(payload)) body.set(k, String(v ?? ''));
  return body;
}

async function phpPost<T>(url: string, data: Record<string, unknown>): Promise<T> {
  const text = await doRequest(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
    body: phpBody(data).toString()
  });
  return parseJson<T>(text);
}

// ─── Node.js API helpers ──────────────────────────────────────────────────────

function nodeHeaders(token: string): Record<string, string> {
  return { 'access-token': token, 'Content-Type': 'application/json' };
}

async function nodePost<T>(path: string, token: string, data: Record<string, unknown>): Promise<T> {
  const text = await doRequest(`${NODE_BASE_URL}${path}`, {
    method:  'POST',
    headers: nodeHeaders(token),
    body:    JSON.stringify(data)
  });
  return parseJson<T>(text);
}

async function nodeGet<T>(path: string, token: string): Promise<T> {
  const ipc = window.desktopApp?.request;
  if (ipc) {
    const result = await ipc({ url: `${NODE_BASE_URL}${path}`, method: 'GET', headers: nodeHeaders(token) });
    return parseJson<T>(result.text);
  }
  const res  = await fetch(`${NODE_BASE_URL}${path}`, { headers: nodeHeaders(token) });
  const text = await res.text();
  return parseJson<T>(text);
}

// ─── Normalisers ──────────────────────────────────────────────────────────────

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
    message:      toStr((p.message ?? p.messages ?? (p.errors as Record<string, unknown>)?.error_text) as unknown)
  };
}

function normaliseMessages(payload: Record<string, unknown>): MessagesResponse {
  const arr = (payload.messages ?? []) as Record<string, unknown>[];
  const messages: MessageItem[] = Array.isArray(arr) ? arr.map(m => ({
    id:             Number(m.id),
    from_id:        Number(m.from_id),
    to_id:          Number(m.to_id),
    text:           toStr(m.decrypted_text ?? m.text ?? m.or_text, ''),
    time_text:      toStr(m.time_text ?? m.time, ''),
    time:           m.time ? Number(m.time) : undefined,
    media:          m.media ? toStr(m.media) : undefined,
    media_type:     m.media_type as MessageItem['media_type'],
    reply_to:       m.reply as MessageItem['reply_to'],
    reactions:      m.reactions as MessageItem['reactions'],
    is_edited:      Boolean(m.is_edited),
    is_seen:        Boolean(m.seen),
    cipher_version: m.cipher_version ? Number(m.cipher_version) : undefined,
    text_encrypted: m.text_encrypted as string | undefined,
    iv:             m.iv as string | undefined,
    tag:            m.tag as string | undefined,
    signal_header:  m.signal_header as string | undefined
  })) : [];
  return { api_status: String(payload.api_status ?? '200'), messages };
}

// ─── Auth ─────────────────────────────────────────────────────────────────────

export async function login(username: string, password: string): Promise<AuthResponse> {
  // Try Node.js API first
  try {
    const resp = await nodePost<Record<string, unknown>>('/api/node/auth/login', '', {
      username, password, device_type: 'windows'
    });
    const norm = normaliseAuth(resp);
    if (norm.api_status === '200' && norm.access_token) return norm;
  } catch (e) { if (e instanceof AuthError) throw e; /* fallthrough */ }

  // PHP v2 API
  try {
    const resp = await phpPost<Record<string, unknown>>(
      v2Url(ENDPOINTS.auth),
      { username, password, device_type: 'windows' }
    );
    const norm = normaliseAuth(resp);
    if (norm.api_status === '200' && norm.access_token) return norm;
  } catch (e) { if (e instanceof AuthError) throw e; /* fallthrough */ }

  // Windows API fallback
  const resp = await phpPost<Record<string, unknown>>(winUrl(ENDPOINTS.windowsLogin), {
    username, password, timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'
  });
  return normaliseAuth(resp);
}

export async function loginByPhone(phone: string, password: string): Promise<AuthResponse> {
  return login(phone, password);
}

export async function registerAccount(input: {
  username: string; email?: string; phoneNumber?: string; password: string;
}): Promise<AuthResponse> {
  const resp = await phpPost<Record<string, unknown>>(
    (() => { const u = new URL(REGISTER_PATH); u.searchParams.set('s', SITE_ENCRYPT_KEY); return u.toString(); })(),
    {
      username:         input.username,
      email:            input.email       ?? '',
      phone_number:     input.phoneNumber ?? '',
      password:         input.password,
      confirm_password: input.password,
      s:                SITE_ENCRYPT_KEY,
      device_type:      'windows',
      gender:           'male'
    }
  );
  return normaliseAuth(resp);
}

// ─── Chats ────────────────────────────────────────────────────────────────────

export async function loadChats(token: string, userId?: number): Promise<ChatListResponse> {
  // Node.js API
  try {
    const resp = await nodePost<NodeChatsResponse>('/api/node/chat/chats', token, {
      user_limit: 80, data_type: 'all', SetOnline: 1, offset: 0
    });
    if ((resp.data ?? []).length > 0) return { api_status: '200', data: resp.data };
  } catch (e) { if (e instanceof AuthError) throw e; /* fallthrough */ }

  // PHP v2
  try {
    const resp = await phpPost<Record<string, unknown>>(
      v2Url(ENDPOINTS.chats, token),
      { user_limit: 80, data_type: 'all', SetOnline: 1, offset: 0 }
    );
    const arr  = (resp.data ?? resp.users ?? []) as Record<string, unknown>[];
    if (Array.isArray(arr) && arr.length > 0) {
      const data: ChatItem[] = arr.map(u => ({
        user_id:   Number(u.user_id ?? u.id),
        name:      toStr(u.name ?? `${u.first_name ?? ''} ${u.last_name ?? ''}`.trim() ?? u.username, 'User'),
        avatar:    u.avatar as string | undefined,
        last_message: toStr(u.last_message, ''),
        time:      toStr(u.lastseen ?? u.lastseen_unix_time, '')
      }));
      return { api_status: '200', data };
    }
  } catch (e) { if (e instanceof AuthError) throw e; /* fallthrough */ }

  if (!userId) return { api_status: '400', data: [] };

  // Windows API fallback
  const resp = await phpPost<Record<string, unknown>>(
    winUrl(ENDPOINTS.windowsUsersList),
    { user_id: userId, access_token: token }
  );
  const arr  = (resp.users ?? resp.data ?? []) as Record<string, unknown>[];
  const data: ChatItem[] = Array.isArray(arr) ? arr.map(u => ({
    user_id:   Number(u.user_id ?? u.id),
    name:      toStr(u.name ?? u.username, 'User'),
    avatar:    u.avatar as string | undefined,
    last_message: toStr(u.last_message, '')
  })) : [];
  return { api_status: '200', data };
}

// ─── Messages ─────────────────────────────────────────────────────────────────

export async function loadMessages(token: string, recipientId: number, userId?: number): Promise<MessagesResponse> {
  // Node.js API
  try {
    const resp = await nodePost<NodeMessagesResponse>('/api/node/chat/get', token, {
      user_id: recipientId, limit: 40, before_message_id: 0
    });
    if (resp.messages) return normaliseMessages(resp as unknown as Record<string, unknown>);
  } catch (e) { if (e instanceof AuthError) throw e; /* fallthrough */ }

  // PHP v2
  try {
    const resp = await phpPost<Record<string, unknown>>(
      v2Url(ENDPOINTS.messages, token),
      { recipient_id: recipientId, limit: 40, before_message_id: 0 }
    );
    return normaliseMessages(resp);
  } catch (e) { if (e instanceof AuthError) throw e; /* fallthrough */ }

  if (!userId) return { api_status: '400', messages: [] };

  const resp = await phpPost<Record<string, unknown>>(
    winUrl(ENDPOINTS.windowsMessages),
    { user_id: userId, recipient_id: recipientId, access_token: token, limit: 50 }
  );
  return normaliseMessages(resp);
}

export async function loadMoreMessages(
  token: string, recipientId: number, beforeId: number
): Promise<MessagesResponse> {
  try {
    const resp = await nodePost<NodeMessagesResponse>('/api/node/chat/loadmore', token, {
      user_id: recipientId, before_message_id: beforeId, limit: 40
    });
    return normaliseMessages(resp as unknown as Record<string, unknown>);
  } catch {
    return { api_status: '500', messages: [] };
  }
}

export async function sendMessage(
  token:       string,
  recipientId: number,
  text:        string,
  userId?:     number,
  signalPayload?: {
    ciphertext:   string;
    iv:           string;
    tag:          string;
    signalHeader: string;
  },
  replyToId?: number
): Promise<{ id?: number }> {
  const hashId = `${Date.now()}-${Math.random().toString(16).slice(2, 10)}`;

  const nodeBody: Record<string, unknown> = {
    user_id:          recipientId,
    recipient_id:     recipientId,
    text:             signalPayload ? '' : text,
    message_hash_id:  hashId,
    ...(replyToId ? { reply_id: replyToId } : {})
  };

  if (signalPayload) {
    nodeBody.cipher_version  = 3;
    nodeBody.text_encrypted  = signalPayload.ciphertext;
    nodeBody.iv              = signalPayload.iv;
    nodeBody.tag             = signalPayload.tag;
    nodeBody.signal_header   = signalPayload.signalHeader;
  }

  // Node.js primary
  try {
    const resp = await nodePost<Record<string, unknown>>('/api/node/chat/send', token, nodeBody);
    const msgData = resp.message_data as Record<string, unknown> | undefined;
    return { id: msgData ? Number(msgData.id) : undefined };
  } catch (e) { if (e instanceof AuthError) throw e; /* fallthrough */ }

  // PHP v2 fallback (plain text only)
  try {
    await phpPost(v2Url(ENDPOINTS.sendMessage, token), { user_id: recipientId, recipient_id: recipientId, text, message_hash_id: hashId });
    return {};
  } catch (e) { if (e instanceof AuthError) throw e; /* fallthrough */ }

  if (!userId) throw new Error('No user ID for Windows API fallback');
  await phpPost(winUrl(ENDPOINTS.windowsInsertMsg), {
    user_id: userId, recipient_id: recipientId, access_token: token,
    send_time: Math.floor(Date.now() / 1000), text, message_hash_id: hashId
  });
  return {};
}

export async function editMessage(token: string, messageId: number, text: string): Promise<void> {
  await nodePost('/api/node/chat/edit', token, { message_id: messageId, text });
}

export async function deleteMessage(token: string, messageId: number, type: 'for_me' | 'for_all' = 'for_all'): Promise<void> {
  await nodePost('/api/node/chat/delete', token, { message_id: messageId, delete_type: type });
}

export async function reactToMessage(token: string, messageId: number, emoji: string): Promise<void> {
  await nodePost('/api/node/chat/react', token, { message_id: messageId, emoji });
}

export async function pinMessage(token: string, messageId: number, pin: boolean): Promise<void> {
  await nodePost('/api/node/chat/pin', token, { message_id: messageId, pin: pin ? 1 : 0 });
}

export async function markSeen(token: string, recipientId: number, lastId: number): Promise<void> {
  try { await nodePost('/api/node/chat/seen', token, { user_id: recipientId, last_id: lastId }); }
  catch { /* non-critical */ }
}

export async function searchMessages(token: string, recipientId: number, query: string): Promise<MessagesResponse> {
  const resp = await nodePost<NodeMessagesResponse>('/api/node/chat/search', token, {
    user_id: recipientId, keyword: query
  });
  return normaliseMessages(resp as unknown as Record<string, unknown>);
}

// ─── Media ────────────────────────────────────────────────────────────────────

export async function uploadMedia(token: string, file: File): Promise<MediaUploadResponse> {
  const form = new FormData();
  form.append('server_key', SERVER_KEY);
  form.append('file', file);
  const text = await doRequest(`${NODE_BASE_URL}/api/node/media/upload`, {
    method: 'POST',
    headers: { 'access-token': token },
    body:   form as unknown as BodyInit
  });
  return parseJson<MediaUploadResponse>(text);
}

export async function sendMessageWithMedia(
  token:       string,
  recipientId: number,
  text:        string,
  file:        File,
  userId?:     number
): Promise<void> {
  const form = new FormData();
  form.append('server_key', SERVER_KEY);
  form.append('user_id',    String(recipientId));
  form.append('text',       text);
  form.append('message_hash_id', `${Date.now()}-${Math.random().toString(16).slice(2, 10)}`);
  form.append('file', file);

  try {
    await doRequest(v2Url(ENDPOINTS.sendMessage, token), { method: 'POST', body: form as unknown as BodyInit });
    return;
  } catch (e) { if (e instanceof AuthError) throw e; /* fallthrough */ }

  if (!userId) throw new Error('sendMessageWithMedia: no userId');
  form.append('user_id_sender', String(userId));
  form.append('access_token', token);
  await doRequest(winUrl(ENDPOINTS.windowsInsertMsg), { method: 'POST', body: form as unknown as BodyInit });
}

// ─── Chat management ──────────────────────────────────────────────────────────

export async function muteChat(token: string, userId: number, mute: boolean): Promise<void> {
  await nodePost('/api/node/chat/mute', token, { user_id: userId, mute: mute ? 1 : 0 });
}

export async function pinChat(token: string, userId: number, pin: boolean): Promise<void> {
  await nodePost('/api/node/chat/pin-chat', token, { user_id: userId, pin: pin ? 1 : 0 });
}

export async function archiveChat(token: string, userId: number, archive: boolean): Promise<void> {
  await nodePost('/api/node/chat/archive', token, { user_id: userId, archive: archive ? 1 : 0 });
}

export async function clearHistory(token: string, userId: number): Promise<void> {
  await nodePost('/api/node/chat/clear-history', token, { user_id: userId });
}

export async function deleteConversation(token: string, userId: number): Promise<void> {
  await nodePost('/api/node/chat/delete-conversation', token, { user_id: userId });
}

export async function setChatColor(token: string, userId: number, color: string): Promise<void> {
  await nodePost('/api/node/chat/color', token, { user_id: userId, color });
}

// ─── Groups ───────────────────────────────────────────────────────────────────

export async function loadGroups(token: string): Promise<GenericListResponse<GroupItem>> {
  try {
    const resp = await nodePost<NodeGroupsResponse>('/api/node/chat/groups/list', token, { limit: 50, offset: 0 });
    if ((resp.data ?? []).length > 0) return { api_status: '200', data: resp.data };
  } catch (e) { if (e instanceof AuthError) throw e; /* fallthrough */ }

  const resp = await phpPost<Record<string, unknown>>(
    absUrl(ENDPOINTS.groups, token),
    { type: 'get_list', limit: 50, offset: 0 }
  );
  const raw   = (resp.groups ?? resp.data ?? []) as Record<string, unknown>[];
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
  await phpPost(absUrl(ENDPOINTS.groups, token), { type: 'create', group_name: groupName, parts: '', group_type: 'group' });
}

export async function loadGroupMessages(token: string, groupId: number): Promise<MessagesResponse> {
  const resp = await nodePost<NodeMessagesResponse>('/api/node/chat/group/get', token, {
    group_id: groupId, limit: 40, before_message_id: 0
  });
  return normaliseMessages(resp as unknown as Record<string, unknown>);
}

export async function sendGroupMessage(token: string, groupId: number, text: string): Promise<void> {
  await nodePost('/api/node/chat/group/send', token, { group_id: groupId, text });
}

// ─── Channels ─────────────────────────────────────────────────────────────────

export async function loadChannels(token: string): Promise<GenericListResponse<ChannelItem>> {
  try {
    const resp = await nodePost<NodeChannelsResponse>('/api/node/chat/channels/list', token, { limit: 50, offset: 0 });
    if ((resp.data ?? []).length > 0) return { api_status: '200', data: resp.data };
  } catch (e) { if (e instanceof AuthError) throw e; /* fallthrough */ }

  const resp = await phpPost<Record<string, unknown>>(
    absUrl(ENDPOINTS.channels, token),
    { type: 'get_list', limit: 50, offset: 0 }
  );
  const raw    = (resp.channels ?? resp.data ?? []) as Record<string, unknown>[];
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
  await phpPost(absUrl(ENDPOINTS.channels, token), { action: 'create_channel', name, description });
}

// ─── Stories ──────────────────────────────────────────────────────────────────

export async function loadStories(token: string): Promise<GenericListResponse<StoryItem>> {
  const resp = await phpPost<Record<string, unknown>>(absUrl(ENDPOINTS.stories, token), { limit: 35 });
  const raw  = (resp.stories ?? resp.data ?? []) as StoryItem[];
  return { api_status: '200', data: raw };
}

export async function createStory(token: string, file: File, fileType: 'image' | 'video'): Promise<void> {
  const form = new FormData();
  form.append('server_key', SERVER_KEY);
  form.append('file', file);
  form.append('file_type', fileType);
  await doRequest(absUrl(ENDPOINTS.createStory, token), { method: 'POST', body: form as unknown as BodyInit });
}

// ─── Calls / ICE servers ──────────────────────────────────────────────────────

export async function getIceServers(userId: number): Promise<RTCIceServer[]> {
  try {
    const payload = await (async () => {
      const text = await doRequest(`https://worldmates.club/api/ice-servers/${userId}`, { method: 'GET' });
      return parseJson<{ iceServers?: RTCIceServer[] } | RTCIceServer[]>(text);
    })();
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
  await nodePost('/api/node/calls/ice-candidate', token, { user_id: userId, candidate });
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

// ─── NodeApiShim factory ──────────────────────────────────────────────────────
// Returns a NodeApiShim bound to the current access token for use with SignalService.

export function createNodeApiShim(token: string): import('./signalService').NodeApiShim {
  return {
    registerSignalKeys: (payload) => registerSignalKeys(token, payload),
    getSignalBundle:    (userId)  => getSignalBundle(token, userId),
    replenishSignalPreKeys: (prekeys) => replenishSignalPreKeys(token, prekeys)
  };
}

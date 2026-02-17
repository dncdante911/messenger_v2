import { API_BASE_URL, ENDPOINTS, REGISTER_PATH, SERVER_KEY, SITE_ENCRYPT_KEY, WINDOWS_APP_BASE_URL } from './config';
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
  StoryItem
} from './types';

function createFormBody(payload: Record<string, string | number>) {
  const body = new URLSearchParams();
  body.append('server_key', SERVER_KEY);
  Object.entries(payload).forEach(([key, value]) => body.append(key, String(value)));
  return body;
}

function createWindowsBody(payload: Record<string, string | number>) {
  const body = new URLSearchParams();
  Object.entries(payload).forEach(([key, value]) => body.append(key, String(value)));
  return body;
}

function withSecurityParams(url: string, accessToken?: string): string {
  const full = new URL(url);
  full.searchParams.set('s', SITE_ENCRYPT_KEY);
  if (accessToken) full.searchParams.set('access_token', accessToken);
  return full.toString();
}

function v2Url(endpoint: string, accessToken?: string): string {
  return withSecurityParams(`${API_BASE_URL}${endpoint}`, accessToken);
}

function absoluteUrl(path: string, accessToken?: string): string {
  return withSecurityParams(`https://worldmates.club${path}`, accessToken);
}

function windowsUrl(endpoint: string): string {
  return `${WINDOWS_APP_BASE_URL}${endpoint}`;
}

async function parseTextAsJson<T>(status: number, text: string, ok: boolean): Promise<T> {
  let payload: T;
  try {
    payload = JSON.parse(text) as T;
  } catch {
    throw new Error(`HTTP ${status}: ${text.slice(0, 300) || 'Non-JSON response'}`);
  }

  if (!ok) {
    throw new Error(`HTTP ${status}: ${text.slice(0, 300)}`);
  }

  return payload;
}

async function postForm<T>(url: string, body: URLSearchParams): Promise<T> {
  const desktopRequest = window.desktopApp?.request;
  if (desktopRequest) {
    const result = await desktopRequest({
      url,
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
      body: body.toString()
    });
    return parseTextAsJson<T>(result.status, result.text, result.ok);
  }

  const response = await fetch(url, { method: 'POST', body });
  const text = await response.text();
  return parseTextAsJson<T>(response.status, text, response.ok);
}

async function postMultipart<T>(url: string, body: FormData): Promise<T> {
  const response = await fetch(url, { method: 'POST', body });
  const text = await response.text();
  return parseTextAsJson<T>(response.status, text, response.ok);
}

async function getJson<T>(url: string): Promise<T> {
  const desktopRequest = window.desktopApp?.request;
  if (desktopRequest) {
    const result = await desktopRequest({ url, method: 'GET' });
    return parseTextAsJson<T>(result.status, result.text, result.ok);
  }

  const response = await fetch(url);
  const text = await response.text();
  return parseTextAsJson<T>(response.status, text, response.ok);
}


function toSafeText(value: unknown, fallback = ''): string {
  if (value === null || value === undefined) return fallback;
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  if (typeof value === 'object') {
    const candidate = (value as Record<string, unknown>).text;
    if (typeof candidate === 'string') return candidate;
    return fallback;
  }
  return fallback;
}

function normalizeAuth(payload: any): AuthResponse {
  return {
    api_status: String(payload.api_status ?? ''),
    access_token: payload.access_token,
    user_id: payload.user_id ? Number(payload.user_id) : undefined,
    message: payload.message ?? payload.messages ?? payload.errors?.error_text
  };
}

function normalizeChats(payload: any): ChatListResponse {
  if (Array.isArray(payload.data)) {
    return { api_status: String(payload.api_status ?? '200'), data: payload.data };
  }

  const usersRaw = payload.users ?? payload.data?.users ?? [];
  const data: ChatItem[] = Array.isArray(usersRaw)
    ? usersRaw.map((user: any) => ({
        user_id: Number(user.user_id ?? user.id),
        name: user.name ?? [user.first_name, user.last_name].filter(Boolean).join(' ') ?? user.username ?? 'Unknown',
        avatar: user.avatar,
        last_message: toSafeText(user.last_message, ''),
        time: user.lastseen ?? user.lastseen_unix_time ?? ''
      }))
    : [];

  return { api_status: String(payload.api_status ?? '200'), data };
}

function normalizeMessages(payload: any): MessagesResponse {
  if (Array.isArray(payload.messages)) {
    const messages: MessageItem[] = payload.messages.map((m: any) => ({
      id: Number(m.id),
      from_id: Number(m.from_id),
      to_id: Number(m.to_id),
      text: toSafeText(m.decrypted_text, toSafeText(m.text, toSafeText(m.or_text, ''))),
      time_text: m.time_text,
      media: m.media
    }));
    return { api_status: String(payload.api_status ?? '200'), messages };
  }

  return { api_status: String(payload.api_status ?? '200'), messages: [] };
}


function normalizeGroups(payload: any): GenericListResponse<GroupItem> {
  const groupsRaw = payload.groups ?? payload.data ?? [];
  const groups: GroupItem[] = Array.isArray(groupsRaw)
    ? groupsRaw.map((group: any) => ({
        id: Number(group.id ?? group.group_id),
        group_name: toSafeText(group.group_name, toSafeText(group.name, 'Group')),
        avatar: group.avatar,
        members_count: Number(group.members_count ?? 0)
      }))
    : [];

  return { api_status: String(payload.api_status ?? '200'), data: groups };
}

function normalizeChannels(payload: any): GenericListResponse<ChannelItem> {
  const channelsRaw = payload.channels ?? payload.data ?? [];
  const channels: ChannelItem[] = Array.isArray(channelsRaw)
    ? channelsRaw.map((channel: any) => ({
        id: Number(channel.id ?? channel.channel_id),
        name: toSafeText(channel.name, 'Channel'),
        username: channel.username,
        avatar_url: channel.avatar_url ?? channel.avatar,
        subscribers_count: Number(channel.subscribers_count ?? 0)
      }))
    : [];

  return { api_status: String(payload.api_status ?? '200'), data: channels };
}

async function loginViaWindowsApi(username: string, password: string): Promise<AuthResponse> {
  const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
  const response = await postForm<any>(
    windowsUrl(ENDPOINTS.windowsLogin),
    createWindowsBody({ username, password, timezone })
  );
  return normalizeAuth(response);
}

export async function login(username: string, password: string): Promise<AuthResponse> {
  try {
    const response = await postForm<any>(v2Url(ENDPOINTS.auth), createFormBody({ username, password, device_type: 'windows' }));
    const normalized = normalizeAuth(response);
    if (normalized.api_status === '200' && normalized.access_token) {
      return normalized;
    }
  } catch {
    // fallback below
  }

  return loginViaWindowsApi(username, password);
}

export async function loginByPhone(phone: string, password: string): Promise<AuthResponse> {
  // Windows API login accepts username/email/phone in one field.
  return login(phone, password);
}

export async function registerAccount(input: {
  username: string;
  email?: string;
  phoneNumber?: string;
  password: string;
}): Promise<AuthResponse> {
  const response = await postForm<any>(
    withSecurityParams(REGISTER_PATH),
    createFormBody({
      username: input.username,
      email: input.email ?? '',
      phone_number: input.phoneNumber ?? '',
      password: input.password,
      confirm_password: input.password,
      s: SITE_ENCRYPT_KEY,
      device_type: 'windows',
      gender: 'male'
    })
  );

  return normalizeAuth(response);
}

export async function loadChats(token: string, userId?: number): Promise<ChatListResponse> {
  try {
    const response = await postForm<any>(
      v2Url(ENDPOINTS.chats, token),
      createFormBody({ user_limit: 80, data_type: 'all', SetOnline: 1, offset: 0 })
    );
    const normalized = normalizeChats(response);
    if ((normalized.data ?? []).length > 0) {
      return normalized;
    }
  } catch {
    // fallback
  }

  if (!userId) {
    return { api_status: '400', data: [] };
  }

  const winPayload = await postForm<any>(
    windowsUrl(ENDPOINTS.windowsUsersList),
    createWindowsBody({ user_id: userId, access_token: token })
  );
  return normalizeChats(winPayload);
}

export async function loadMessages(token: string, recipientId: number, userId?: number): Promise<MessagesResponse> {
  try {
    const response = await postForm<any>(
      v2Url(ENDPOINTS.messages, token),
      createFormBody({ recipient_id: recipientId, limit: 40, before_message_id: 0 })
    );
    return normalizeMessages(response);
  } catch {
    // fallback
  }

  if (!userId) {
    return { api_status: '400', messages: [] };
  }

  const winPayload = await postForm<any>(
    windowsUrl(ENDPOINTS.windowsMessages),
    createWindowsBody({ user_id: userId, recipient_id: recipientId, access_token: token, limit: 50 })
  );
  return normalizeMessages(winPayload);
}

export async function sendMessage(token: string, recipientId: number, text: string, userId?: number): Promise<void> {
  const payload = {
    user_id: recipientId,
    recipient_id: recipientId,
    text,
    message_hash_id: `${Date.now()}-${Math.random().toString(16).slice(2, 10)}`
  };

  try {
    await postForm(v2Url(ENDPOINTS.sendMessage, token), createFormBody(payload));
    await postForm(v2Url(ENDPOINTS.sendMessageLegacy, token), createFormBody({ recipient_id: recipientId, text }));
    return;
  } catch {
    // fallback below
  }

  if (!userId) {
    throw new Error('Windows API fallback requires current user id.');
  }

  await postForm(
    windowsUrl(ENDPOINTS.windowsInsertMessage),
    createWindowsBody({
      user_id: userId,
      recipient_id: recipientId,
      access_token: token,
      send_time: Math.floor(Date.now() / 1000),
      text,
      message_hash_id: `${Date.now()}-${Math.random().toString(16).slice(2, 10)}`
    })
  );
}

export function uploadMedia(token: string, file: File): Promise<MediaUploadResponse> {
  const form = new FormData();
  form.append('server_key', SERVER_KEY);
  form.append('file', file);
  return postMultipart<MediaUploadResponse>(v2Url(ENDPOINTS.uploadMedia, token), form);
}

export async function sendMessageWithMedia(
  token: string,
  recipientId: number,
  text: string,
  file: File,
  userId?: number
): Promise<void> {
  const form = new FormData();
  form.append('server_key', SERVER_KEY);
  form.append('user_id', String(recipientId));
  form.append('text', text);
  form.append('message_hash_id', `${Date.now()}-${Math.random().toString(16).slice(2, 10)}`);
  form.append('file', file);

  try {
    await postMultipart(v2Url(ENDPOINTS.sendMessage, token), form);
    return;
  } catch {
    // fallback
  }

  if (!userId) {
    throw new Error('Windows API fallback requires current user id.');
  }

  const fallbackForm = new FormData();
  fallbackForm.append('user_id', String(userId));
  fallbackForm.append('recipient_id', String(recipientId));
  fallbackForm.append('access_token', token);
  fallbackForm.append('send_time', String(Math.floor(Date.now() / 1000)));
  fallbackForm.append('text', text);
  fallbackForm.append('file', file);

  await postMultipart(windowsUrl(ENDPOINTS.windowsInsertMessage), fallbackForm);
}

export async function loadGroups(token: string): Promise<GenericListResponse<GroupItem>> {
  const response = await postForm<any>(
    absoluteUrl(ENDPOINTS.groups, token),
    createFormBody({ type: 'get_list', limit: 50, offset: 0 })
  );
  return normalizeGroups(response);
}

export function createGroup(token: string, groupName: string): Promise<unknown> {
  return postForm(
    absoluteUrl(ENDPOINTS.groups, token),
    createFormBody({ type: 'create', group_name: groupName, parts: '', group_type: 'group' })
  );
}

export async function loadChannels(token: string): Promise<GenericListResponse<ChannelItem>> {
  const response = await postForm<any>(
    absoluteUrl(ENDPOINTS.channels, token),
    createFormBody({ type: 'get_list', limit: 50, offset: 0 })
  );
  return normalizeChannels(response);
}

export function createChannel(token: string, channelName: string, description: string): Promise<unknown> {
  return postForm(
    absoluteUrl(ENDPOINTS.channels, token),
    createFormBody({ action: 'create_channel', name: channelName, description })
  );
}

export function loadStories(token: string): Promise<GenericListResponse<StoryItem>> {
  return postForm<GenericListResponse<StoryItem>>(absoluteUrl(ENDPOINTS.stories, token), createFormBody({ limit: 35 }));
}

export async function createStory(token: string, file: File, fileType: 'image' | 'video'): Promise<void> {
  const form = new FormData();
  form.append('server_key', SERVER_KEY);
  form.append('file', file);
  form.append('file_type', fileType);

  await postMultipart(absoluteUrl(ENDPOINTS.createStory, token), form);
}

export async function getIceServers(userId: number): Promise<RTCIceServer[]> {
  try {
    const payload = await getJson<{ iceServers?: RTCIceServer[] } | RTCIceServer[]>(
      absoluteUrl(`${ENDPOINTS.iceServers}${userId}`)
    );
    return Array.isArray(payload) ? payload : (payload.iceServers ?? []);
  } catch {
    return [];
  }
}

// ─── Auth ─────────────────────────────────────────────────────────────────────

export type AuthResponse = {
  api_status: string;
  access_token?: string;
  user_id?: number;
  message?: string;
};

// ─── User & profile ───────────────────────────────────────────────────────────

export type UserInfo = {
  user_id: number;
  name: string;
  username?: string;
  avatar?: string;
  cover?: string;
  about?: string;
  is_online?: boolean;
  last_seen?: string;        // formatted text
  last_seen_unix?: number;
  is_following?: boolean;
  following_count?: number;
  followers_count?: number;
};

// ─── Chat list ────────────────────────────────────────────────────────────────

export type ChatItem = {
  user_id: number;
  name: string;
  avatar?: string;
  last_message?: string;
  time?: string;
  time_unix?: number;
  is_online?: boolean;
  unread_count?: number;
  is_muted?: boolean;
  is_pinned?: boolean;
  is_archived?: boolean;
  chat_color?: string;        // hex color for chat accent
};

export type ChatListResponse = {
  api_status: string;
  data?: ChatItem[];
};

// ─── Messages ─────────────────────────────────────────────────────────────────

export type MessageReaction = {
  emoji: string;
  count: number;
  user_ids: number[];
};

export type MessageItem = {
  id: number;
  from_id: number;
  to_id: number;
  text: string;
  time_text?: string;
  time?: number;              // unix timestamp
  media?: string;             // URL to media file
  media_type?: 'image' | 'video' | 'audio' | 'document' | 'voice' | 'sticker' | 'gif';
  media_filename?: string;
  media_duration?: number;    // seconds (audio/video)
  sender_name?: string;       // display name for group messages
  group_id?: number;          // populated for group messages
  reply_to?: {
    id: number;
    from_id: number;
    text: string;
    media?: string;
  };
  reactions?: MessageReaction[];
  is_edited?: boolean;
  is_pinned?: boolean;
  is_deleted?: boolean;
  is_seen?: boolean;
  cipher_version?: number;    // 0=plain, 2=AES-256, 3=Signal
  // Signal decryption fields
  text_encrypted?: string;
  iv?: string;
  tag?: string;
  signal_header?: string;
  // Client-side flags
  _decryptFailed?: boolean;
  _pending?: boolean;         // optimistic UI: not yet confirmed by server
};

export type MessagesResponse = {
  api_status: string;
  messages?: MessageItem[];
};

// ─── Groups ───────────────────────────────────────────────────────────────────

export type GroupMember = {
  user_id: number;
  name: string;
  avatar?: string;
  role: 'admin' | 'moderator' | 'member';
};

export type GroupItem = {
  id: number;
  group_name: string;
  avatar?: string;
  members_count?: number;
  description?: string;
  is_admin?: boolean;
  last_message?: string;
  time?: string;
  is_pinned?: boolean;
  is_muted?: boolean;
};

// ─── Channels ─────────────────────────────────────────────────────────────────

export type ChannelItem = {
  id: number;
  name: string;
  username?: string;
  avatar_url?: string;
  cover?: string;
  subscribers_count?: number;
  description?: string;
  is_subscribed?: boolean;
  is_owner?: boolean;
  last_post?: string;
  time?: string;
};

export type ChannelPost = {
  id: number;
  channel_id: number;
  publisher_id: number;
  text: string;
  media?: string;
  media_type?: string;
  reactions?: MessageReaction[];
  comments_count?: number;
  views_count?: number;
  time?: string;
  time_unix?: number;
  is_pinned?: boolean;
};

// ─── Stories ──────────────────────────────────────────────────────────────────

export type StoryItem = {
  id: number;
  user_id: number;
  user_name?: string;
  user_avatar?: string;
  file?: string;
  thumbnail?: string;
  file_type?: 'image' | 'video';
  created_at?: string;
  expire_time?: number;
  is_seen?: boolean;
  views_count?: number;
};

// ─── Calls ────────────────────────────────────────────────────────────────────

export type CallRecord = {
  id: number;
  from_id: number;
  to_id: number;
  call_type: 'audio' | 'video';
  duration?: number;
  status: 'answered' | 'missed' | 'declined' | 'busy';
  time?: string;
  time_unix?: number;
};

export type CallState =
  | { phase: 'idle' }
  | { phase: 'outgoing'; peer: ChatItem; type: 'audio' | 'video' }
  | { phase: 'incoming'; peer: ChatItem; type: 'audio' | 'video'; offerSdp: string }
  | { phase: 'connected'; peer: ChatItem; type: 'audio' | 'video'; duration: number };

// ─── Socket / real-time events ────────────────────────────────────────────────

export type TypingEvent = {
  sender_id: number;
  recipient_id: number;
};

export type UserActionEvent = {
  user_id: number;
  recipient_id: number;
  action: 'recording' | 'recording_video' | 'listening' | 'viewing' | 'choosing_sticker';
};

export type UserPresenceEvent = {
  user_id: number;
  is_online: boolean;
  last_seen?: string;
};

export type MessageSeenEvent = {
  sender_id: number;
  recipient_id: number;
  last_seen_id: number;
};

export type MessageReactionEvent = {
  msg_id: number;
  user_id: number;
  emoji: string;
};

export type MessagePinnedEvent = {
  msg_id: number;
  chat_id: number;
  pinned: boolean;
};

// ─── Channel posts response ───────────────────────────────────────────────────

export type ChannelPostsResponse = {
  api_status: string;
  posts?: ChannelPost[];
  data?: ChannelPost[];
};

// ─── Generic list response ────────────────────────────────────────────────────

export type GenericListResponse<T> = {
  api_status: string;
  data?: T[];
  stories?: T[];
  message?: string;
};

export type MediaUploadResponse = {
  api_status?: string;
  status?:     number;
  // Kotlin XhrUploadResponse fields
  image?: string;    image_src?: string;
  video?: string;    video_src?: string;
  audio?: string;    audio_src?: string;
  file?:  string;    file_src?:  string;
  // Generic fallbacks
  filename?: string;
  media?: string;
  url?: string;
  message?: string;
  error?: string;
};

// ─── Node.js API response wrappers ────────────────────────────────────────────

export type NodeApiStatus = { api_status: number; message?: string; error_message?: string };

export type NodeChatsResponse = NodeApiStatus & { data?: ChatItem[] };
export type NodeMessagesResponse = NodeApiStatus & { messages?: MessageItem[] };
export type NodeSendResponse = NodeApiStatus & { message_data?: MessageItem };
export type NodeUserResponse = NodeApiStatus & { data?: UserInfo };
export type NodeGroupsResponse = NodeApiStatus & { data?: GroupItem[] };
export type NodeChannelsResponse = NodeApiStatus & { data?: ChannelItem[] };
export type NodeStoriesResponse = NodeApiStatus & { stories?: StoryItem[]; data?: StoryItem[] };

export type SignalBundleResponse = NodeApiStatus & {
  identity_key?:       string;
  signed_prekey_id?:   number;
  signed_prekey?:      string;
  signed_prekey_sig?:  string;
  one_time_prekey_id?: number;
  one_time_prekey?:    string;
  remaining_prekeys?:  number;
};

// ─── Session ──────────────────────────────────────────────────────────────────

export type Session = {
  token:    string;
  userId:   number;
  username: string;
};

// ─── UI state ─────────────────────────────────────────────────────────────────

export type ActiveSection = 'chats' | 'groups' | 'channels' | 'stories' | 'calls' | 'settings' | 'archived';

export type ReplyTarget = {
  id:      number;
  from_id: number;
  text:    string;
  media?:  string;
};

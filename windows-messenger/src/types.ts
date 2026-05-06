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
  is_admin?: boolean;
  last_post?: string;
  time?: string;
};

export type PollOption = {
  id: number;
  text: string;
  vote_count: number;
  percent: number;
  is_voted: boolean;
};

export type ChannelPoll = {
  id: number;
  question: string;
  poll_type: string;
  is_anonymous: boolean;
  allows_multiple_answers: boolean;
  is_closed: boolean;
  total_votes: number;
  options: PollOption[];
};

export type ChannelPost = {
  id: number;
  channel_id: number;
  publisher_id: number;
  text: string;
  media?: string;
  media_type?: string;
  media_items?: Array<{ url: string; type: string }>;
  poll?: ChannelPoll;
  reactions?: MessageReaction[];
  comments_count?: number;
  views_count?: number;
  time?: string;
  time_unix?: number;
  is_pinned?: boolean;
};

export type ChannelComment = {
  id: number;
  user_id: number;
  username?: string;
  user_name?: string;
  user_avatar?: string;
  text: string;
  time: number;
  reply_to_comment_id?: number;
  reactions_count?: number;
};

// ─── Stories ──────────────────────────────────────────────────────────────────

export type StoryReaction = {
  like:       number;
  love:       number;
  haha:       number;
  wow:        number;
  sad:        number;
  angry:      number;
  is_reacted: boolean;
  reacted_type?: string;
};

export type StoryComment = {
  id:           number;
  story_id:     number;
  user_id:      number;
  text:         string;
  time:         number;
  user_name?:   string;
  user_avatar?: string;
};

export type StoryItem = {
  id:            number;
  user_id:       number;
  user_name?:    string;
  user_avatar?:  string;
  file?:         string;
  thumbnail?:    string;
  file_type?:    'image' | 'video';
  created_at?:   string;
  expire_time?:  number;
  is_seen?:      boolean;
  views_count?:  number;
  comment_count?: number;
  is_owner?:     boolean;
  reaction?:     StoryReaction;
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

export type CallHistoryItem = {
  id: number;
  call_category: 'personal' | 'group';
  call_type: 'audio' | 'video';
  status: string;
  direction: 'incoming' | 'outgoing';
  created_at: string;
  accepted_at?: string;
  ended_at?: string;
  duration: number;
  timestamp: number;
  other_user?: { user_id: number; username: string; name: string; avatar: string };
  group_data?: { group_id: number; group_name: string; avatar: string };
};

export type PrivacySettings = {
  follow_privacy: string;
  friend_privacy: string;
  post_privacy: string;
  message_privacy: string;
  confirm_followers: string;
  show_activities_privacy: string;
  birth_privacy: string;
  visit_privacy: string;
  showlastseen: string;
};

export type CallState =
  | { phase: 'idle' }
  | { phase: 'outgoing';       peer: ChatItem; type: 'audio' | 'video'; roomName: string }
  | { phase: 'incoming';       peer: ChatItem; type: 'audio' | 'video'; roomName: string; fromId: number; iceServers: RTCIceServer[]; sdpOffer: string }
  | { phase: 'connected';      peer: ChatItem; type: 'audio' | 'video'; roomName: string; duration: number }
  | { phase: 'group_incoming'; groupId: number; groupName: string; type: 'audio' | 'video'; roomName: string; fromName: string; iceServers: RTCIceServer[] }
  | { phase: 'group_connected';groupId: number; groupName: string; type: 'audio' | 'video'; roomName: string; duration: number };

export type GroupCallPeer = {
  userId:  number;
  name:    string;
  avatar?: string;
  pc:      RTCPeerConnection;
  stream?: MediaStream;
};

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

export type ActiveSection = 'chats' | 'groups' | 'channels' | 'stories' | 'calls' | 'settings' | 'archived' | 'bots';

export type ReplyTarget = {
  id:      number;
  from_id: number;
  text:    string;
  media?:  string;
};

export type Sticker = {
  id: number;
  pack_id: number;
  file_url: string;
  thumbnail_url?: string;
  emoji?: string;
  format?: string;
};

export type StickerPack = {
  id: number;
  name: string;
  icon_url?: string;
  author?: string;
  is_active: boolean;
  sticker_count?: number;
  stickers?: Sticker[];
};

export type GifItem = {
  id: string;
  title: string;
  url: string;
  previewUrl: string;
};

export type BotItem = {
  bot_id_str: string;    // server string id e.g. "wallybot_bot"
  user_id: number;       // linked_user_id (numeric) for opening chat, 0 until resolved
  username: string;
  display_name: string;
  avatar?: string;
  description?: string;
  web_app_url?: string;
};

// ─── Batch 5: Channel admin panel ─────────────────────────────────────────────

export type ChannelStatistics = {
  // Subscribers
  subscribers_count:     number;
  new_subscribers_today: number;
  new_subscribers_week:  number;
  left_subscribers_week: number;
  growth_rate:           number;   // % change this week
  active_subscribers_24h: number;
  subscribers_by_day?:   number[]; // 7 values

  // Posts
  posts_count:       number;
  posts_today:       number;
  posts_last_week:   number;
  posts_this_month:  number;

  // Views
  views_total:        number;
  views_last_week:    number;
  avg_views_per_post: number;
  views_by_day?:      number[]; // 7 values

  // Engagement
  reactions_total: number;
  comments_total:  number;
  engagement_rate: number; // %

  // Content breakdown
  media_posts_count: number;
  text_posts_count:  number;

  // Activity
  peak_hours?:    number[]; // active hour indices 0-23
  hourly_views?:  number[]; // views per hour [0..23]

  // Top content
  top_posts?: TopPostStat[];
};

export type TopPostStat = {
  id:             number;
  text:           string;
  views:          number;
  reactions:      number;
  comments:       number;
  published_time: number;
  has_media:      boolean;
};

export type ChannelAdmin = {
  user_id:    number;
  username:   string;
  avatar?:    string;
  role:       'owner' | 'admin' | 'moderator';
  added_time: number;
  permissions?: {
    can_post:            boolean;
    can_edit_posts:      boolean;
    can_delete_posts:    boolean;
    can_edit_info:       boolean;
    can_ban_users:       boolean;
    can_add_admins:      boolean;
    can_view_statistics: boolean;
    can_manage_comments: boolean;
  };
};

export type ChannelSettings = {
  allow_comments:               boolean;
  allow_reactions:              boolean;
  allow_shares:                 boolean;
  allow_forwarding:             boolean;
  show_statistics:              boolean;
  show_views_count:             boolean;
  notify_subscribers_new_post:  boolean;
  signature_enabled:            boolean;
  comments_moderation:          boolean;
  comment_identity:             'user' | 'channel' | 'user_with_signature';
  slow_mode_seconds?:           number;
  auto_delete_posts_days?:      number;
};

export type ChannelSubscriber = {
  user_id?:        number;
  username?:       string;
  name?:           string;
  avatar?:         string;
  subscribed_time?: number;
  is_muted:        boolean;
  is_banned:       boolean;
  role?:           string;
};

export type ChannelBannedMember = {
  user_id:       number;
  username?:     string;
  name?:         string;
  avatar?:       string;
  reason?:       string;
  banned_until?: number; // unix ts; null/0 = permanent
  banned_time?:  number;
};

export type ActiveMember = {
  user_id:        number;
  username?:      string;
  name?:          string;
  avatar_url?:    string;
  comment_count:  number;
  reaction_count: number;
  score:          number;
};

// ─── Batch 5: Group admin panel ───────────────────────────────────────────────

export type GroupMemberFull = {
  user_id:     number;
  username:    string;
  avatar?:     string;
  role:        'owner' | 'admin' | 'moderator' | 'member';
  joined_time: number;
  is_muted:    boolean;
  is_blocked:  boolean;
  permissions?: string[];
};

export type GroupSettings = {
  allow_members_invite:          boolean;
  allow_members_pin:             boolean;
  allow_members_delete_messages: boolean;
  allow_voice_calls:             boolean;
  allow_video_calls:             boolean;
  slow_mode_seconds:             number;
  history_visible_for_new_members: boolean;
  allow_members_send_media:      boolean;
  allow_members_send_stickers:   boolean;
  allow_members_send_gifs:       boolean;
  allow_members_send_links:      boolean;
  allow_members_send_polls:      boolean;
  anti_spam_enabled:             boolean;
  max_messages_per_minute:       number;
  auto_mute_spammers:            boolean;
  block_new_users_media:         boolean;
};

export type GroupStatistics = {
  group_id:            number;
  members_count:       number;
  messages_count:      number;
  messages_today:      number;
  messages_this_week:  number;
  messages_this_month: number;
  active_members_24h:  number;
  active_members_week: number;
  media_count:         number;
  links_count:         number;
  new_members_today:   number;
  new_members_week:    number;
  left_members_week:   number;
  top_contributors?:   TopContributor[];
  peak_hours?:         number[];
  growth_rate:         number;
};

export type TopContributor = {
  user_id:       number;
  username:      string;
  name?:         string;
  avatar?:       string;
  messages_count: number;
};

export type GroupJoinRequest = {
  id:           number;
  group_id:     number;
  user_id:      number;
  username:     string;
  user_avatar?: string;
  message?:     string;
  status:       'pending' | 'approved' | 'rejected';
  created_time: number;
};

export type AdminLogEntry = {
  id?:              number;
  admin_id?:        number;
  admin_name:       string;
  admin_avatar?:    string;
  action:           string;
  target_user_name?: string;
  details?:         string;
  created_at:       string;
};

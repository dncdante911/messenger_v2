// ============================================================
// WorldMates Messenger — API Constants
// Ported from Android Constants.kt
// ============================================================

// ==================== BASE URLs ====================
export const BASE_URL = 'https://worldmates.club/api/v2/';
export const MEDIA_BASE_URL = 'https://worldmates.club/';
export const SOCKET_URL = 'https://worldmates.club:449/';
export const NODE_BASE_URL = 'https://worldmates.club:449/';

// ==================== SECURITY ====================
export const SITE_ENCRYPT_KEY = '2ad9c757daccdfff436dc226779e20b719f6d6f8';
export const SERVER_KEY =
  'a8975daa76d7197ab87412b096696bb0e341eb4d-9bb411ab89d8a290362726fca6129e76-81746510';

// ==================== PHP API QUERY PARAMETERS ====================
export const AUTH_ENDPOINT = '?type=auth';
export const GET_CHATS_ENDPOINT = '?type=get_chats';
export const GET_MESSAGES_ENDPOINT = '/api/v2/index.php?type=get_user_messages';
export const SEND_MESSAGE_ENDPOINT = '/api/v2/index.php?type=send_message';

// ==================== NODE.JS REST — SUBSCRIPTION ====================
export const NODE_SUBSCRIPTION_STATUS = 'api/node/subscription/status';
export const NODE_SUBSCRIPTION_CREATE_PAYMENT = 'api/node/subscription/create-payment';

// ==================== NODE.JS REST — PRIVATE CHAT MESSAGES ====================
export const NODE_CHAT_GET = 'api/node/chat/get';
export const NODE_CHAT_SEND = 'api/node/chat/send';
export const NODE_CHAT_LOADMORE = 'api/node/chat/loadmore';
export const NODE_CHAT_EDIT = 'api/node/chat/edit';
export const NODE_CHAT_SEARCH = 'api/node/chat/search';
export const NODE_CHAT_SEEN = 'api/node/chat/seen';
export const NODE_CHAT_TYPING = 'api/node/chat/typing';
export const NODE_CHAT_USER_ACTION = 'api/node/chat/user-action';
export const NODE_CHAT_NOTIFY_MEDIA = 'api/node/chat/notify-media';
export const NODE_CHAT_SEND_MEDIA = 'api/node/chat/send-media';

// ==================== NODE.JS REST — USER PRESENCE ====================
export const NODE_USER_STATUS = 'api/node/user/status';
export const NODE_USER_STATUS_CUSTOM = 'api/node/users/me/status';

// ==================== NODE.JS REST — MULTI-AVATAR ====================
export const NODE_AVATAR_LIST = 'api/node/user/avatars/{userId}';
export const NODE_AVATAR_UPLOAD = 'api/node/user/avatars/upload';
export const NODE_AVATAR_SET_MAIN = 'api/node/user/avatars/{id}/set-main';
export const NODE_AVATAR_REORDER = 'api/node/user/avatars/reorder';
export const NODE_AVATAR_DELETE = 'api/node/user/avatars/{id}';

// ==================== NODE.JS REST — CHAT ACTIONS ====================
export const NODE_CHAT_DELETE = 'api/node/chat/delete';
export const NODE_CHAT_REACT = 'api/node/chat/react';
export const NODE_CHAT_PIN = 'api/node/chat/pin';
export const NODE_CHAT_PINNED = 'api/node/chat/pinned';
export const NODE_CHAT_FORWARD = 'api/node/chat/forward';

// ==================== NODE.JS REST — CHATS LIST & SETTINGS ====================
export const NODE_CHATS_LIST = 'api/node/chat/chats';
export const NODE_BUSINESS_CHATS_LIST = 'api/node/chat/business-chats';
export const NODE_BUSINESS_INBOX = 'api/node/chat/business-inbox';
export const NODE_CHAT_DEL_CONV = 'api/node/chat/delete-conversation';
export const NODE_CHAT_ARCHIVE = 'api/node/chat/archive';
export const NODE_CHAT_MUTE = 'api/node/chat/mute';
export const NODE_CHAT_PIN_CHAT = 'api/node/chat/pin-chat';
export const NODE_CHAT_COLOR = 'api/node/chat/color';
export const NODE_CHAT_READ = 'api/node/chat/read';
export const NODE_CHAT_CLEAR_HIST = 'api/node/chat/clear-history';
export const NODE_CHAT_MUTE_STATUS = 'api/node/chat/mute-status';
export const NODE_CHAT_MEDIA_AUTO_DELETE = 'api/node/chat/media-auto-delete-setting';
export const NODE_CHAT_HIDE = 'api/node/chat/hide';
export const NODE_CHAT_HIDDEN_COUNT = 'api/node/chat/hidden/count';

// ==================== NODE.JS REST — CRASH REPORTING ====================
export const NODE_CRASH_REPORT = 'api/node/crash-report';

// ==================== NODE.JS REST — FAVORITES ====================
export const NODE_CHAT_FAV = 'api/node/chat/fav';
export const NODE_CHAT_FAV_LIST = 'api/node/chat/fav-list';

// ==================== NODE.JS REST — SAVED MESSAGES ====================
export const NODE_SAVED_SAVE = 'api/node/chat/saved/save';
export const NODE_SAVED_UNSAVE = 'api/node/chat/saved/unsave';
export const NODE_SAVED_LIST = 'api/node/chat/saved/list';
export const NODE_SAVED_CLEAR = 'api/node/chat/saved/clear';

// ==================== NODE.JS REST — CHANNELS ====================
export const NODE_CHANNEL_LIST = 'api/node/channel/list';
export const NODE_CHANNEL_DETAILS = 'api/node/channel/details';
export const NODE_CHANNEL_CREATE = 'api/node/channel/create';
export const NODE_CHANNEL_UPDATE = 'api/node/channel/update';
export const NODE_CHANNEL_DELETE = 'api/node/channel/delete';
export const NODE_CHANNEL_SUBSCRIBE = 'api/node/channel/subscribe';
export const NODE_CHANNEL_UNSUBSCRIBE = 'api/node/channel/unsubscribe';
export const NODE_CHANNEL_ADD_MEMBER = 'api/node/channel/add-member';
export const NODE_CHANNEL_POSTS = 'api/node/channel/posts';
export const NODE_CHANNEL_CREATE_POST = 'api/node/channel/create-post';
export const NODE_CHANNEL_UPDATE_POST = 'api/node/channel/update-post';
export const NODE_CHANNEL_DELETE_POST = 'api/node/channel/delete-post';
export const NODE_CHANNEL_PIN_POST = 'api/node/channel/pin-post';
export const NODE_CHANNEL_UNPIN_POST = 'api/node/channel/unpin-post';
export const NODE_CHANNEL_COMMENTS = 'api/node/channel/comments';
export const NODE_CHANNEL_ADD_COMMENT = 'api/node/channel/add-comment';
export const NODE_CHANNEL_DEL_COMMENT = 'api/node/channel/delete-comment';
export const NODE_CHANNEL_POST_REACT = 'api/node/channel/post-reaction';
export const NODE_CHANNEL_POST_UNREACT = 'api/node/channel/post-unreaction';
export const NODE_CHANNEL_COMMENT_REACT = 'api/node/channel/comment-reaction';
export const NODE_CHANNEL_POST_VIEW = 'api/node/channel/post-view';
export const NODE_CHANNEL_ADD_ADMIN = 'api/node/channel/add-admin';
export const NODE_CHANNEL_REMOVE_ADMIN = 'api/node/channel/remove-admin';
export const NODE_CHANNEL_SETTINGS = 'api/node/channel/settings';
export const NODE_CHANNEL_STATISTICS = 'api/node/channel/statistics';
export const NODE_CHANNEL_ACTIVE_MEMBERS = 'api/node/channel/active-members';
export const NODE_CHANNEL_TOP_COMMENTS = 'api/node/channel/top-comments';
export const NODE_CHANNEL_GIVEAWAY = 'api/node/channel/giveaway';
export const NODE_CHANNEL_POST_ANALYTICS = 'api/node/channel/post/analytics';
export const NODE_CHANNEL_BACKUP_EXPORT = 'api/node/channel/backup/export';
export const NODE_CHANNEL_SUBSCRIBERS = 'api/node/channel/subscribers';
export const NODE_CHANNEL_MUTE = 'api/node/channel/mute';
export const NODE_CHANNEL_UNMUTE = 'api/node/channel/unmute';
export const NODE_CHANNEL_QR_GENERATE = 'api/node/channel/qr-generate';
export const NODE_CHANNEL_QR_SUBSCRIBE = 'api/node/channel/qr-subscribe';
export const NODE_CHANNEL_UPLOAD_AVATAR = 'api/node/channel/upload-avatar';
export const NODE_CHANNEL_BAN_MEMBER = 'api/node/channel/ban-member';
export const NODE_CHANNEL_UNBAN_MEMBER = 'api/node/channel/unban-member';
export const NODE_CHANNEL_KICK_MEMBER = 'api/node/channel/kick-member';
export const NODE_CHANNEL_BANNED = 'api/node/channel/banned-members';
// Channel polls
export const NODE_CHANNEL_POLL_CREATE = 'api/node/channel/poll/create';
export const NODE_CHANNEL_POLL_GET = 'api/node/channel/poll/get';
export const NODE_CHANNEL_POLL_VOTE = 'api/node/channel/poll/vote';
export const NODE_CHANNEL_POLL_CLOSE = 'api/node/channel/poll/close';
// Channel sub-groups (private channel linked groups)
export const NODE_CHANNEL_GROUPS_LIST = 'api/node/channel/groups/list';
export const NODE_CHANNEL_GROUPS_CREATE = 'api/node/channel/groups/create';
export const NODE_CHANNEL_GROUPS_ATTACH = 'api/node/channel/groups/attach';
export const NODE_CHANNEL_GROUPS_DETACH = 'api/node/channel/groups/detach';

// ==================== NODE.JS REST — MEDIA ====================
export const NODE_MEDIA_UPLOAD = 'api/node/media/upload';

// ==================== NODE.JS REST — GROUP CHATS ====================
export const NODE_GROUP_LIST = 'api/node/group/list';
export const NODE_GROUP_DETAILS = 'api/node/group/details';
export const NODE_GROUP_CREATE = 'api/node/group/create';
export const NODE_GROUP_UPDATE = 'api/node/group/update';
export const NODE_GROUP_DELETE = 'api/node/group/delete';
export const NODE_GROUP_LEAVE = 'api/node/group/leave';
export const NODE_GROUP_SEARCH = 'api/node/group/search';
// Members
export const NODE_GROUP_MEMBERS = 'api/node/group/members';
export const NODE_GROUP_ADD_MEMBER = 'api/node/group/add-member';
export const NODE_GROUP_REMOVE_MEMBER = 'api/node/group/remove-member';
export const NODE_GROUP_SET_ROLE = 'api/node/group/set-role';
export const NODE_GROUP_JOIN = 'api/node/group/join';
export const NODE_GROUP_REQUEST_JOIN = 'api/node/group/request-join';
export const NODE_GROUP_JOIN_REQUESTS = 'api/node/group/join-requests';
export const NODE_GROUP_APPROVE_JOIN = 'api/node/group/approve-join';
export const NODE_GROUP_REJECT_JOIN = 'api/node/group/reject-join';
export const NODE_GROUP_BAN_MEMBER = 'api/node/group/ban-member';
export const NODE_GROUP_UNBAN_MEMBER = 'api/node/group/unban-member';
export const NODE_GROUP_MUTE_MEMBER = 'api/node/group/mute-member';
export const NODE_GROUP_UNMUTE_MEMBER = 'api/node/group/unmute-member';
// Group messages
export const NODE_GROUP_MESSAGES_GET = 'api/node/group/messages/get';
export const NODE_GROUP_MESSAGES_SEND = 'api/node/group/messages/send';
export const NODE_GROUP_MESSAGES_LOADMORE = 'api/node/group/messages/loadmore';
export const NODE_GROUP_MESSAGES_EDIT = 'api/node/group/messages/edit';
export const NODE_GROUP_MESSAGES_DELETE = 'api/node/group/messages/delete';
export const NODE_GROUP_MESSAGES_PIN = 'api/node/group/messages/pin';
export const NODE_GROUP_MESSAGES_UNPIN = 'api/node/group/messages/unpin';
export const NODE_GROUP_MESSAGES_SEARCH = 'api/node/group/messages/search';
export const NODE_GROUP_MESSAGES_SEEN = 'api/node/group/messages/seen';
export const NODE_GROUP_MESSAGES_TYPING = 'api/node/group/messages/typing';
export const NODE_GROUP_MESSAGES_USER_ACTION = 'api/node/group/messages/user-action';
export const NODE_GROUP_MESSAGES_CLEAR_SELF = 'api/node/group/messages/clear-self';
export const NODE_GROUP_MESSAGES_CLEAR_ALL = 'api/node/group/messages/clear-all';
// Group admin
export const NODE_GROUP_UPLOAD_AVATAR = 'api/node/group/upload-avatar';
export const NODE_GROUP_SETTINGS = 'api/node/group/settings';
export const NODE_GROUP_MUTE = 'api/node/group/mute';
export const NODE_GROUP_UNMUTE = 'api/node/group/unmute';
export const NODE_GROUP_QR_GENERATE = 'api/node/group/qr-generate';
export const NODE_GROUP_QR_JOIN = 'api/node/group/qr-join';
export const NODE_GROUP_STATISTICS = 'api/node/group/statistics';
export const NODE_GROUP_ADD_ADMIN = 'api/node/group/add-admin';
export const NODE_GROUP_REMOVE_ADMIN = 'api/node/group/remove-admin';
// Group customization (theme)
export const NODE_GROUP_CUSTOMIZATION_GET = 'api/node/group/customization/get';
export const NODE_GROUP_CUSTOMIZATION_UPDATE = 'api/node/group/customization/update';
export const NODE_GROUP_CUSTOMIZATION_RESET = 'api/node/group/customization/reset';
// Group polls
export const NODE_GROUP_POLL_CREATE = 'api/node/group/poll/create';
export const NODE_GROUP_POLL_GET = 'api/node/group/poll/get';
export const NODE_GROUP_POLL_VOTE = 'api/node/group/poll/vote';
export const NODE_GROUP_POLL_CLOSE = 'api/node/group/poll/close';
// Group giveaway
export const NODE_GROUP_GIVEAWAY_RUN = 'api/node/group/giveaway/run';
// Group topics
export const NODE_GROUP_TOPICS_LIST = 'api/node/group/topics/list';
export const NODE_GROUP_TOPICS_CREATE = 'api/node/group/topics/create';
export const NODE_GROUP_TOPICS_UPDATE = 'api/node/group/topics/update';
export const NODE_GROUP_TOPICS_DELETE = 'api/node/group/topics/delete';
// Anonymous admin
export const NODE_GROUP_ANON_ADMIN_SET = 'api/node/group/admin/set-anonymous';
export const NODE_GROUP_ANON_ADMIN_GET = 'api/node/group/admin/get-anonymous';
// Admin logs
export const NODE_GROUP_ADMIN_LOGS = 'api/node/group/admin-logs';

// ==================== NODE.JS REST — EXPORT ====================
export const NODE_CHAT_EXPORT = 'api/node/chat/export';
export const NODE_GROUP_EXPORT = 'api/node/group/export';

// ==================== NODE.JS REST — BACKUP ====================
export const NODE_BACKUP_SETTINGS = 'api/node/backup/settings';
export const NODE_BACKUP_STATISTICS = 'api/node/backup/statistics';

// ==================== NODE.JS REST — INSTANT VIEW ====================
export const NODE_INSTANT_VIEW = 'api/node/instant-view';

// ==================== NODE.JS REST — SCHEDULED MESSAGES ====================
export const NODE_SCHEDULED_LIST = 'api/node/scheduled/list';
export const NODE_SCHEDULED_CREATE = 'api/node/scheduled/create';
export const NODE_SCHEDULED_UPDATE = 'api/node/scheduled/update/{id}';
export const NODE_SCHEDULED_DELETE = 'api/node/scheduled/{id}';
export const NODE_SCHEDULED_SEND_NOW = 'api/node/scheduled/{id}/send-now';

// ==================== NODE.JS REST — CHANNEL POST THREADS ====================
export const NODE_THREAD_MESSAGES = 'api/node/channel/post/{postId}/thread';
export const NODE_THREAD_SEND = 'api/node/channel/post/{postId}/thread/send';
export const NODE_THREAD_REPLY = 'api/node/channel/post/{postId}/thread/reply';
export const NODE_THREAD_DELETE = 'api/node/channel/post/{postId}/thread/{msgId}';
export const NODE_THREAD_COUNT = 'api/node/channel/post/{postId}/thread/count';
export const NODE_THREAD_BATCH_COUNTS = 'api/node/channel/threads/counts';

// ==================== NODE.JS REST — USER PROFILE ====================
export const NODE_PROFILE_ME = 'api/node/users/me';
export const NODE_PROFILE_ME_PRIVACY = 'api/node/users/me/privacy';
export const NODE_PROFILE_ME_PASSWORD = 'api/node/users/me/password';
export const NODE_PROFILE_ME_NOTIF = 'api/node/users/me/notifications';
export const NODE_PROFILE_ME_BLOCKED = 'api/node/users/me/blocked';
export const NODE_PROFILE_USER = 'api/node/users/{id}';
export const NODE_PROFILE_FOLLOWERS = 'api/node/users/{id}/followers';
export const NODE_PROFILE_FOLLOWING = 'api/node/users/{id}/following';
export const NODE_PROFILE_FOLLOW = 'api/node/users/{id}/follow';
export const NODE_PROFILE_BLOCK = 'api/node/users/{id}/block';
export const NODE_PROFILE_SEARCH = 'api/node/users/search';
export const NODE_PROFILE_RATING = 'api/node/users/{id}/rating';
export const NODE_PROFILE_RATE = 'api/node/users/{id}/rate';

// ==================== NODE.JS REST — CHAT FOLDERS ====================
export const NODE_FOLDER_LIST = 'api/node/folders';
export const NODE_FOLDER_CREATE = 'api/node/folders/create';
export const NODE_FOLDER_UPDATE = 'api/node/folders/{id}';
export const NODE_FOLDER_DELETE = 'api/node/folders/{id}';
export const NODE_FOLDER_ADD_CHAT = 'api/node/folders/{id}/add-chat';
export const NODE_FOLDER_REMOVE_CHAT = 'api/node/folders/{id}/remove-chat';
export const NODE_FOLDER_REORDER = 'api/node/folders/reorder';
export const NODE_FOLDER_SHARE = 'api/node/folders/{id}/share';
export const NODE_FOLDER_JOIN = 'api/node/folders/join/{code}';
export const NODE_FOLDER_LEAVE = 'api/node/folders/{id}/leave';

// ==================== NODE.JS REST — APP UPDATE ====================
export const NODE_UPDATE_CHECK = 'api/node/update/check';

// ==================== NODE.JS REST — GLOBAL SEARCH ====================
export const NODE_SEARCH_GLOBAL = 'api/node/search/global';
export const NODE_SEARCH_USERS = 'api/node/search/users';
export const NODE_SEARCH_SUGGESTIONS = 'api/node/search/suggestions';
export const NODE_SEARCH_SAVED = 'api/node/search/saved';
export const NODE_SEARCH_SAVED_DELETE = 'api/node/search/saved/{id}';
export const NODE_SEARCH_RECENT_SAVE = 'api/node/search/recent';
export const NODE_SEARCH_RECENT_CLEAR = 'api/node/search/recent';

// ==================== NODE.JS REST — NOTES ====================
export const NODE_NOTES_LIST = 'api/node/notes';
export const NODE_NOTES_CREATE = 'api/node/notes/create';
export const NODE_NOTES_DELETE = 'api/node/notes/{id}';
export const NODE_NOTES_STORAGE = 'api/node/notes/storage';

// ==================== SOCKET.IO EVENTS ====================
export const SOCKET_EVENT_AUTH = 'join';
export const SOCKET_EVENT_PRIVATE_MESSAGE = 'private_message';
export const SOCKET_EVENT_PRIVATE_MESSAGE_PAGE = 'private_message_page';
export const SOCKET_EVENT_PAGE_MESSAGE = 'page_message';
/** @deprecated use SOCKET_EVENT_PRIVATE_MESSAGE */
export const SOCKET_EVENT_NEW_MESSAGE = 'new_message';
export const SOCKET_EVENT_SEND_MESSAGE = 'private_message';
export const SOCKET_EVENT_TYPING = 'typing';
export const SOCKET_EVENT_TYPING_DONE = 'typing_done';
export const SOCKET_EVENT_RECORDING = 'recording';
/** server → client: recipient's online/last-seen timestamp */
export const SOCKET_EVENT_LAST_SEEN = 'ping_for_lastseen';
/** server → client: your message was read (bulk seen) */
export const SOCKET_EVENT_MESSAGE_SEEN = 'lastseen';
export const SOCKET_EVENT_GROUP_MESSAGE = 'group_message';
export const SOCKET_EVENT_GROUP_TYPING = 'group_typing';
export const SOCKET_EVENT_GROUP_TYPING_DONE = 'group_typing_done';
export const SOCKET_EVENT_USER_ACTION = 'user_action';
export const SOCKET_EVENT_GROUP_USER_ACTION = 'group_user_action';
export const SOCKET_EVENT_MESSAGE_REACTION = 'message_reaction';
export const SOCKET_EVENT_MESSAGE_PINNED = 'message_pinned';
export const SOCKET_EVENT_CHANNEL_MESSAGE = 'channel_message';
export const SOCKET_EVENT_USER_ONLINE = 'on_user_loggedin';
export const SOCKET_EVENT_USER_OFFLINE = 'on_user_loggedoff';
/** client → server: user opened a private chat */
export const SOCKET_EVENT_CHAT_OPEN = 'is_chat_on';
/** client → server: user closed a private chat */
export const SOCKET_EVENT_CHAT_CLOSE = 'close_chat';
// Live Location
export const SOCKET_EVENT_LIVE_LOCATION_START = 'live_location_start';
export const SOCKET_EVENT_LIVE_LOCATION_UPDATE = 'live_location_update';
export const SOCKET_EVENT_LIVE_LOCATION_STOP = 'live_location_stop';
// Message mutations
export const SOCKET_EVENT_MESSAGE_EDITED = 'message_edited';
export const SOCKET_EVENT_MESSAGE_DELETED = 'message_deleted';
export const SOCKET_EVENT_GROUP_MESSAGE_EDITED = 'group_message_edited';
export const SOCKET_EVENT_GROUP_MESSAGE_DELETED = 'group_message_deleted';
export const SOCKET_EVENT_GROUP_HISTORY_CLEARED = 'group_history_cleared';
export const SOCKET_EVENT_PRIVATE_HISTORY_CLEARED = 'private_history_cleared';
// E2EE
export const SOCKET_EVENT_SIGNAL_IDENTITY_CHANGED = 'signal:identity_changed';
export const SOCKET_EVENT_GROUP_MEMBER_JOINED = 'group:member_joined';
export const SOCKET_EVENT_GROUP_MEMBER_LEFT = 'group:member_left';
// WorldStars
export const SOCKET_EVENT_STARS_RECEIVED = 'stars_received';
// Bot
export const SOCKET_EVENT_BOT_CALLBACK_QUERY = 'bot_callback_query';
// Channel
export const SOCKET_EVENT_CHANNEL_SUBSCRIBE = 'channel:subscribe';
export const SOCKET_EVENT_CHANNEL_UNSUBSCRIBE = 'channel:unsubscribe';
export const SOCKET_EVENT_CHANNEL_POST_CREATED = 'channel:post_created';
export const SOCKET_EVENT_CHANNEL_POST_UPDATED = 'channel:post_updated';
export const SOCKET_EVENT_CHANNEL_POST_DELETED = 'channel:post_deleted';
export const SOCKET_EVENT_CHANNEL_COMMENT_ADDED = 'channel:comment_added';
export const SOCKET_EVENT_CHANNEL_TYPING = 'channel:typing';
export const SOCKET_EVENT_CHANNEL_STREAM_STARTED = 'channel:stream_started';
export const SOCKET_EVENT_CHANNEL_STREAM_ENDED = 'channel:stream_ended';
// Stories
export const SOCKET_EVENT_STORY_SUBSCRIBE = 'story:subscribe';
export const SOCKET_EVENT_STORY_UNSUBSCRIBE = 'story:unsubscribe';
export const SOCKET_EVENT_STORY_CREATED = 'story:created';
export const SOCKET_EVENT_STORY_DELETED = 'story:deleted';
export const SOCKET_EVENT_STORY_VIEW = 'story:view';
export const SOCKET_EVENT_STORY_COMMENT_ADDED = 'story:comment_added';
export const SOCKET_EVENT_STORY_TYPING = 'story:typing';
// WebRTC / ICE
export const SOCKET_EVENT_ICE_REQUEST = 'ice:request';

// ==================== USER ACTION TYPES ====================
/** Sent in the "action" field of user_action / group_user_action socket events */
export const USER_ACTION_RECORDING = 'recording';
export const USER_ACTION_RECORDING_VIDEO = 'recording_video';
export const USER_ACTION_LISTENING = 'listening';
export const USER_ACTION_VIEWING = 'viewing';
export const USER_ACTION_CHOOSING_STICKER = 'choosing_sticker';

// ==================== MESSAGE TYPES ====================
export const MESSAGE_TYPES = {
  TEXT: 'text',
  IMAGE: 'image',
  VIDEO: 'video',
  AUDIO: 'audio',
  VOICE: 'voice',
  FILE: 'file',
  CALL: 'call',
  LOCATION: 'location',
  SYSTEM: 'system',
} as const;

export type MessageType = (typeof MESSAGE_TYPES)[keyof typeof MESSAGE_TYPES];

// ==================== CHAT TYPES ====================
export const CHAT_TYPES = {
  USER: 'user',
  GROUP: 'group',
  CHANNEL: 'channel',
  PRIVATE_GROUP: 'private_group',
} as const;

export type ChatType = (typeof CHAT_TYPES)[keyof typeof CHAT_TYPES];

// ==================== USER ROLES ====================
export const USER_ROLES = {
  ADMIN: 'admin',
  MODERATOR: 'moderator',
  MEMBER: 'member',
  OWNER: 'owner',
} as const;

export type UserRole = (typeof USER_ROLES)[keyof typeof USER_ROLES];

// ==================== ERROR CODES ====================
export const ERROR_CODES = {
  UNAUTHORIZED: 401,
  FORBIDDEN: 403,
  NOT_FOUND: 404,
  TIMEOUT: 408,
  SERVER: 500,
  NO_CONNECTION: 0,
} as const;

// ==================== PAGINATION ====================
export const MESSAGES_PAGE_SIZE = 30;
export const CHATS_PAGE_SIZE = 50;
export const GROUPS_PAGE_SIZE = 50;
export const MEMBERS_PAGE_SIZE = 100;

// ==================== MEDIA LIMITS ====================
/** 25 MB */
export const MAX_IMAGE_SIZE = 25 * 1024 * 1024;
/** 1 GB */
export const MAX_VIDEO_SIZE = 1024 * 1024 * 1024;
/** 100 MB */
export const MAX_AUDIO_SIZE = 100 * 1024 * 1024;
/** 10 GB */
export const MAX_FILE_SIZE = 10 * 1024 * 1024 * 1024;
/** Files above this threshold are compressed before upload (50 MB) */
export const VIDEO_COMPRESSION_THRESHOLD = 50 * 1024 * 1024;
export const MAX_FILES_PER_MESSAGE = 10;
/** 10 minutes in seconds */
export const MEDIA_UPLOAD_TIMEOUT = 600;
/** 256 KB */
export const MEDIA_UPLOAD_CHUNK_SIZE = 256 * 1024;

// ==================== CACHE ====================
/** 24 hours in ms */
export const CACHE_EXPIRATION_TIME = 24 * 60 * 60 * 1000;
export const MAX_MESSAGES_IN_CACHE = 1000;
export const MAX_CHATS_IN_CACHE = 500;

// ==================== TIMEOUTS (seconds) ====================
export const CONNECT_TIMEOUT_SECONDS = 30;
export const READ_TIMEOUT_SECONDS = 30;
export const WRITE_TIMEOUT_SECONDS = 30;

// ==================== VOICE/VIDEO CALLS ====================
export const VOICE_CALL_TIMEOUT = 60;
export const VIDEO_CALL_TIMEOUT = 120;

// ==================== COMPRESSION ====================
export const IMAGE_COMPRESSION_QUALITY = 80;
export const VIDEO_COMPRESSION_BITRATE = 5_000_000;

// ==================== DEVICE INFO ====================
export const APP_VERSION = '2.0';
export const DEVICE_TYPE = 'ios';

package com.worldmates.messenger.data

object Constants {
    // ==================== API ENDPOINTS ====================
    const val BASE_URL = "https://worldmates.club/api/v2/"
    const val MEDIA_BASE_URL = "https://worldmates.club/" // For media files (images, videos, etc.)
    const val SOCKET_URL = "https://worldmates.club:449/"

    // ==================== SECURITY ====================
    const val SITE_ENCRYPT_KEY = "2ad9c757daccdfff436dc226779e20b719f6d6f8"
    const val SERVER_KEY = "a8975daa76d7197ab87412b096696bb0e341eb4d-9bb411ab89d8a290362726fca6129e76-81746510"

    // ==================== API QUERY PARAMETERS ====================
    const val AUTH_ENDPOINT = "?type=auth"
    const val GET_CHATS_ENDPOINT = "?type=get_chats"
    // Explicit index.php path — bypasses WoWonder api-v2.php router
    const val GET_MESSAGES_ENDPOINT = "/api/v2/index.php?type=get_user_messages"
    const val SEND_MESSAGE_ENDPOINT = "/api/v2/index.php?type=send_message"
    
    // ==================== NODE.JS REST API (приватні чати) ====================
    // Node.js сервер — прямі REST-ендпоінти для особистих чатів
    // Базовий URL збігається з SOCKET_URL (той самий Node.js сервер)
    const val NODE_BASE_URL = "https://worldmates.club:449/"

    // Subscription / PRO
    const val NODE_SUBSCRIPTION_STATUS         = "api/node/subscription/status"
    const val NODE_SUBSCRIPTION_CREATE_PAYMENT = "api/node/subscription/create-payment"

    // Messages
    const val NODE_CHAT_GET      = "api/node/chat/get"       // GET history
    const val NODE_CHAT_SEND     = "api/node/chat/send"      // send text
    const val NODE_CHAT_LOADMORE = "api/node/chat/loadmore"  // older messages
    const val NODE_CHAT_EDIT     = "api/node/chat/edit"      // edit message
    const val NODE_CHAT_SEARCH   = "api/node/chat/search"    // search in chat
    const val NODE_CHAT_SEEN         = "api/node/chat/seen"         // mark as seen
    const val NODE_CHAT_TYPING       = "api/node/chat/typing"       // typing indicator
    const val NODE_CHAT_USER_ACTION  = "api/node/chat/user-action"  // user action status (listening, viewing, etc.)
    const val NODE_CHAT_NOTIFY_MEDIA = "api/node/chat/notify-media" // notify after PHP media save
    const val NODE_CHAT_SEND_MEDIA   = "api/node/chat/send-media"   // send media message (replaces PHP send_message for media)

    // User presence
    const val NODE_USER_STATUS = "api/node/user/status" // online status + last_seen for any user

    // Multi-avatar
    const val NODE_AVATAR_LIST     = "api/node/user/avatars/{userId}" // GET
    const val NODE_AVATAR_UPLOAD   = "api/node/user/avatars/upload"   // POST multipart
    const val NODE_AVATAR_SET_MAIN = "api/node/user/avatars/{id}/set-main" // POST
    const val NODE_AVATAR_REORDER  = "api/node/user/avatars/reorder"  // POST
    const val NODE_AVATAR_DELETE   = "api/node/user/avatars/{id}"     // DELETE

    // Actions
    const val NODE_CHAT_DELETE  = "api/node/chat/delete"   // delete message
    const val NODE_CHAT_REACT   = "api/node/chat/react"    // react to message
    const val NODE_CHAT_PIN     = "api/node/chat/pin"      // pin message
    const val NODE_CHAT_PINNED  = "api/node/chat/pinned"   // get pinned messages
    const val NODE_CHAT_FORWARD = "api/node/chat/forward"  // forward message

    // Chats list & settings
    const val NODE_CHATS_LIST    = "api/node/chat/chats"               // conversations list
    const val NODE_CHAT_DEL_CONV = "api/node/chat/delete-conversation" // delete conversation
    const val NODE_CHAT_ARCHIVE  = "api/node/chat/archive"             // archive/unarchive
    const val NODE_CHAT_MUTE     = "api/node/chat/mute"                // mute/unmute
    const val NODE_CHAT_PIN_CHAT = "api/node/chat/pin-chat"            // pin/unpin chat
    const val NODE_CHAT_COLOR       = "api/node/chat/color"        // change color
    const val NODE_CHAT_READ        = "api/node/chat/read"         // mark all as read
    const val NODE_CHAT_CLEAR_HIST  = "api/node/chat/clear-history" // clear history (soft-delete)
    const val NODE_CHAT_MUTE_STATUS = "api/node/chat/mute-status"  // get mute/notify status
    const val NODE_CHAT_MEDIA_AUTO_DELETE = "api/node/chat/media-auto-delete-setting" // media auto-delete setting

    // Favorites
    const val NODE_CHAT_FAV      = "api/node/chat/fav"                 // fav/unfav message
    const val NODE_CHAT_FAV_LIST = "api/node/chat/fav-list"            // get favorites

    // Saved Messages (server-side bookmarks)
    const val NODE_SAVED_SAVE   = "api/node/chat/saved/save"   // bookmark a message
    const val NODE_SAVED_UNSAVE = "api/node/chat/saved/unsave" // remove bookmark
    const val NODE_SAVED_LIST   = "api/node/chat/saved/list"   // fetch all saved
    const val NODE_SAVED_CLEAR  = "api/node/chat/saved/clear"  // clear all

    // ==================== NODE.JS REST API (channels) ====================
    // Node.js сервер — REST-ендпоінти для каналів
    const val NODE_CHANNEL_LIST         = "api/node/channel/list"
    const val NODE_CHANNEL_DETAILS      = "api/node/channel/details"
    const val NODE_CHANNEL_CREATE       = "api/node/channel/create"
    const val NODE_CHANNEL_UPDATE       = "api/node/channel/update"
    const val NODE_CHANNEL_DELETE       = "api/node/channel/delete"
    const val NODE_CHANNEL_SUBSCRIBE    = "api/node/channel/subscribe"
    const val NODE_CHANNEL_UNSUBSCRIBE  = "api/node/channel/unsubscribe"
    const val NODE_CHANNEL_ADD_MEMBER   = "api/node/channel/add-member"
    const val NODE_CHANNEL_POSTS        = "api/node/channel/posts"
    const val NODE_CHANNEL_CREATE_POST  = "api/node/channel/create-post"
    const val NODE_CHANNEL_UPDATE_POST  = "api/node/channel/update-post"
    const val NODE_CHANNEL_DELETE_POST  = "api/node/channel/delete-post"
    const val NODE_CHANNEL_PIN_POST     = "api/node/channel/pin-post"
    const val NODE_CHANNEL_UNPIN_POST   = "api/node/channel/unpin-post"
    const val NODE_CHANNEL_COMMENTS     = "api/node/channel/comments"
    const val NODE_CHANNEL_ADD_COMMENT  = "api/node/channel/add-comment"
    const val NODE_CHANNEL_DEL_COMMENT  = "api/node/channel/delete-comment"
    const val NODE_CHANNEL_POST_REACT   = "api/node/channel/post-reaction"
    const val NODE_CHANNEL_POST_UNREACT = "api/node/channel/post-unreaction"
    const val NODE_CHANNEL_COMMENT_REACT = "api/node/channel/comment-reaction"
    const val NODE_CHANNEL_POST_VIEW    = "api/node/channel/post-view"
    const val NODE_CHANNEL_ADD_ADMIN    = "api/node/channel/add-admin"
    const val NODE_CHANNEL_REMOVE_ADMIN = "api/node/channel/remove-admin"
    const val NODE_CHANNEL_SETTINGS     = "api/node/channel/settings"
    const val NODE_CHANNEL_STATISTICS   = "api/node/channel/statistics"
    const val NODE_CHANNEL_SUBSCRIBERS  = "api/node/channel/subscribers"
    const val NODE_CHANNEL_MUTE         = "api/node/channel/mute"
    const val NODE_CHANNEL_UNMUTE       = "api/node/channel/unmute"
    const val NODE_CHANNEL_QR_GENERATE  = "api/node/channel/qr-generate"
    const val NODE_CHANNEL_QR_SUBSCRIBE = "api/node/channel/qr-subscribe"
    const val NODE_CHANNEL_UPLOAD_AVATAR = "api/node/channel/upload-avatar"
    const val NODE_MEDIA_UPLOAD          = "api/node/media/upload"
    const val NODE_CHANNEL_BAN_MEMBER    = "api/node/channel/ban-member"
    const val NODE_CHANNEL_UNBAN_MEMBER  = "api/node/channel/unban-member"
    const val NODE_CHANNEL_KICK_MEMBER   = "api/node/channel/kick-member"
    const val NODE_CHANNEL_BANNED        = "api/node/channel/banned-members"
    // Sub-groups (private channel linked groups)
    const val NODE_CHANNEL_GROUPS_LIST   = "api/node/channel/groups/list"
    const val NODE_CHANNEL_GROUPS_CREATE = "api/node/channel/groups/create"
    const val NODE_CHANNEL_GROUPS_ATTACH = "api/node/channel/groups/attach"
    const val NODE_CHANNEL_GROUPS_DETACH = "api/node/channel/groups/detach"

    // ==================== NODE.JS REST API (group chats) ====================
    // All group chat operations through Node.js (port 449)
    const val NODE_GROUP_LIST          = "api/node/group/list"
    const val NODE_GROUP_DETAILS       = "api/node/group/details"
    const val NODE_GROUP_CREATE        = "api/node/group/create"
    const val NODE_GROUP_UPDATE        = "api/node/group/update"
    const val NODE_GROUP_DELETE        = "api/node/group/delete"
    const val NODE_GROUP_LEAVE         = "api/node/group/leave"
    const val NODE_GROUP_SEARCH        = "api/node/group/search"
    // Members
    const val NODE_GROUP_MEMBERS       = "api/node/group/members"
    const val NODE_GROUP_ADD_MEMBER    = "api/node/group/add-member"
    const val NODE_GROUP_REMOVE_MEMBER = "api/node/group/remove-member"
    const val NODE_GROUP_SET_ROLE      = "api/node/group/set-role"
    const val NODE_GROUP_JOIN          = "api/node/group/join"
    const val NODE_GROUP_REQUEST_JOIN  = "api/node/group/request-join"
    const val NODE_GROUP_JOIN_REQUESTS = "api/node/group/join-requests"
    const val NODE_GROUP_APPROVE_JOIN  = "api/node/group/approve-join"
    const val NODE_GROUP_REJECT_JOIN   = "api/node/group/reject-join"
    const val NODE_GROUP_BAN_MEMBER    = "api/node/group/ban-member"
    const val NODE_GROUP_UNBAN_MEMBER  = "api/node/group/unban-member"
    const val NODE_GROUP_MUTE_MEMBER   = "api/node/group/mute-member"
    const val NODE_GROUP_UNMUTE_MEMBER = "api/node/group/unmute-member"
    // Messages
    const val NODE_GROUP_MESSAGES_GET      = "api/node/group/messages/get"
    const val NODE_GROUP_MESSAGES_SEND     = "api/node/group/messages/send"
    const val NODE_GROUP_MESSAGES_LOADMORE = "api/node/group/messages/loadmore"
    const val NODE_GROUP_MESSAGES_EDIT     = "api/node/group/messages/edit"
    const val NODE_GROUP_MESSAGES_DELETE   = "api/node/group/messages/delete"
    const val NODE_GROUP_MESSAGES_PIN      = "api/node/group/messages/pin"
    const val NODE_GROUP_MESSAGES_UNPIN    = "api/node/group/messages/unpin"
    const val NODE_GROUP_MESSAGES_SEARCH   = "api/node/group/messages/search"
    const val NODE_GROUP_MESSAGES_SEEN          = "api/node/group/messages/seen"
    const val NODE_GROUP_MESSAGES_TYPING        = "api/node/group/messages/typing"
    const val NODE_GROUP_MESSAGES_USER_ACTION   = "api/node/group/messages/user-action"
    const val NODE_GROUP_MESSAGES_CLEAR_SELF    = "api/node/group/messages/clear-self"
    const val NODE_GROUP_MESSAGES_CLEAR_ALL     = "api/node/group/messages/clear-all"
    // Admin
    const val NODE_GROUP_UPLOAD_AVATAR = "api/node/group/upload-avatar"
    const val NODE_GROUP_SETTINGS      = "api/node/group/settings"
    const val NODE_GROUP_MUTE          = "api/node/group/mute"
    const val NODE_GROUP_UNMUTE        = "api/node/group/unmute"
    const val NODE_GROUP_QR_GENERATE   = "api/node/group/qr-generate"
    const val NODE_GROUP_QR_JOIN       = "api/node/group/qr-join"
    const val NODE_GROUP_STATISTICS    = "api/node/group/statistics"
    const val NODE_GROUP_ADD_ADMIN     = "api/node/group/add-admin"
    const val NODE_GROUP_REMOVE_ADMIN  = "api/node/group/remove-admin"
    // Customization (theme)
    const val NODE_GROUP_CUSTOMIZATION_GET    = "api/node/group/customization/get"
    const val NODE_GROUP_CUSTOMIZATION_UPDATE = "api/node/group/customization/update"
    const val NODE_GROUP_CUSTOMIZATION_RESET  = "api/node/group/customization/reset"

    // ── Export ────────────────────────────────────────────────────────────────
    const val NODE_CHAT_EXPORT         = "api/node/chat/export"
    const val NODE_GROUP_EXPORT        = "api/node/group/export"

    // ── Topics ────────────────────────────────────────────────────────────────
    const val NODE_GROUP_TOPICS_LIST   = "api/node/group/topics/list"
    const val NODE_GROUP_TOPICS_CREATE = "api/node/group/topics/create"
    const val NODE_GROUP_TOPICS_UPDATE = "api/node/group/topics/update"
    const val NODE_GROUP_TOPICS_DELETE = "api/node/group/topics/delete"

    // ── Anonymous Admin ───────────────────────────────────────────────────────
    const val NODE_GROUP_ANON_ADMIN_SET = "api/node/group/admin/set-anonymous"
    const val NODE_GROUP_ANON_ADMIN_GET = "api/node/group/admin/get-anonymous"

    // ── Admin Logs ────────────────────────────────────────────────────────────
    const val NODE_GROUP_ADMIN_LOGS     = "api/node/group/admin-logs"

    // ── Group Polls ───────────────────────────────────────────────────────────
    const val NODE_GROUP_POLL_CREATE   = "api/node/group/poll/create"
    const val NODE_GROUP_POLL_GET      = "api/node/group/poll/get"
    const val NODE_GROUP_POLL_VOTE     = "api/node/group/poll/vote"
    const val NODE_GROUP_POLL_CLOSE    = "api/node/group/poll/close"

    // ── Channel Polls ─────────────────────────────────────────────────────────
    const val NODE_CHANNEL_POLL_CREATE = "api/node/channel/poll/create"
    const val NODE_CHANNEL_POLL_GET    = "api/node/channel/poll/get"
    const val NODE_CHANNEL_POLL_VOTE   = "api/node/channel/poll/vote"
    const val NODE_CHANNEL_POLL_CLOSE  = "api/node/channel/poll/close"

    // ── Backup / Cloud Settings ───────────────────────────────────────────────
    const val NODE_BACKUP_SETTINGS     = "api/node/backup/settings"
    const val NODE_BACKUP_STATISTICS   = "api/node/backup/statistics"

    // ── Instant View ──────────────────────────────────────────────────────────
    const val NODE_INSTANT_VIEW        = "api/node/instant-view"

    // ── Scheduled Messages ────────────────────────────────────────────────────
    const val NODE_SCHEDULED_LIST      = "api/node/scheduled/list"
    const val NODE_SCHEDULED_CREATE    = "api/node/scheduled/create"
    const val NODE_SCHEDULED_UPDATE    = "api/node/scheduled/update/{id}"
    const val NODE_SCHEDULED_DELETE    = "api/node/scheduled/{id}"
    const val NODE_SCHEDULED_SEND_NOW  = "api/node/scheduled/{id}/send-now"

    // ── Channel Post Threads ──────────────────────────────────────────────────
    const val NODE_THREAD_MESSAGES     = "api/node/channel/post/{postId}/thread"
    const val NODE_THREAD_SEND         = "api/node/channel/post/{postId}/thread/send"
    const val NODE_THREAD_REPLY        = "api/node/channel/post/{postId}/thread/reply"
    const val NODE_THREAD_DELETE       = "api/node/channel/post/{postId}/thread/{msgId}"
    const val NODE_THREAD_COUNT        = "api/node/channel/post/{postId}/thread/count"
    const val NODE_THREAD_BATCH_COUNTS = "api/node/channel/threads/counts"

    // ── User Profile (замінює PHP ?type=get-user-data / update-user-data) ────
    const val NODE_PROFILE_ME          = "api/node/users/me"
    const val NODE_PROFILE_ME_PRIVACY  = "api/node/users/me/privacy"
    const val NODE_PROFILE_ME_PASSWORD = "api/node/users/me/password"
    const val NODE_PROFILE_ME_NOTIF    = "api/node/users/me/notifications"
    const val NODE_PROFILE_ME_BLOCKED  = "api/node/users/me/blocked"
    const val NODE_PROFILE_USER        = "api/node/users/{id}"          // GET other user
    const val NODE_PROFILE_FOLLOWERS   = "api/node/users/{id}/followers"
    const val NODE_PROFILE_FOLLOWING   = "api/node/users/{id}/following"
    const val NODE_PROFILE_FOLLOW      = "api/node/users/{id}/follow"   // POST/DELETE
    const val NODE_PROFILE_BLOCK       = "api/node/users/{id}/block"    // POST/DELETE
    const val NODE_PROFILE_SEARCH      = "api/node/users/search"
    const val NODE_PROFILE_RATING      = "api/node/users/{id}/rating"   // GET rating
    const val NODE_PROFILE_RATE        = "api/node/users/{id}/rate"     // POST vote

    // ── Shared Chat Folders ───────────────────────────────────────────────────
    const val NODE_FOLDER_LIST         = "api/node/folders"
    const val NODE_FOLDER_CREATE       = "api/node/folders/create"
    const val NODE_FOLDER_UPDATE       = "api/node/folders/{id}"
    const val NODE_FOLDER_DELETE       = "api/node/folders/{id}"
    const val NODE_FOLDER_ADD_CHAT     = "api/node/folders/{id}/add-chat"
    const val NODE_FOLDER_REMOVE_CHAT  = "api/node/folders/{id}/remove-chat"
    const val NODE_FOLDER_REORDER      = "api/node/folders/reorder"
    const val NODE_FOLDER_SHARE        = "api/node/folders/{id}/share"
    const val NODE_FOLDER_JOIN         = "api/node/folders/join/{code}"
    const val NODE_FOLDER_LEAVE        = "api/node/folders/{id}/leave"

    // ── App update ────────────────────────────────────────────────────────────
    const val NODE_UPDATE_CHECK = "api/node/update/check"

    // ── Global Search ─────────────────────────────────────────────────────────
    const val NODE_SEARCH_GLOBAL = "api/node/search/global"
    const val NODE_SEARCH_USERS  = "api/node/search/users"

    // ── Notes (Telegram-style personal storage) ───────────────────────────────
    const val NODE_NOTES_LIST    = "api/node/notes"
    const val NODE_NOTES_CREATE  = "api/node/notes/create"
    const val NODE_NOTES_DELETE  = "api/node/notes/{id}"
    const val NODE_NOTES_STORAGE = "api/node/notes/storage"

    // ==================== SOCKET.IO EVENTS ====================
    const val SOCKET_EVENT_AUTH                = "join"
    const val SOCKET_EVENT_PRIVATE_MESSAGE     = "private_message"
    const val SOCKET_EVENT_PRIVATE_MESSAGE_PAGE= "private_message_page"
    const val SOCKET_EVENT_PAGE_MESSAGE        = "page_message"
    const val SOCKET_EVENT_NEW_MESSAGE         = "new_message"         // legacy compat
    const val SOCKET_EVENT_SEND_MESSAGE        = "private_message"
    const val SOCKET_EVENT_TYPING              = "typing"
    const val SOCKET_EVENT_TYPING_DONE         = "typing_done"
    const val SOCKET_EVENT_RECORDING           = "recording"
    // Server emits "lastseen" for BOTH last-seen timestamps AND message-read receipts
    const val SOCKET_EVENT_LAST_SEEN           = "ping_for_lastseen"   // server → client: recipient's online timestamp
    const val SOCKET_EVENT_MESSAGE_SEEN        = "lastseen"            // server → client: your message was read (was wrong: "seen_messages")
    const val SOCKET_EVENT_GROUP_MESSAGE       = "group_message"
    const val SOCKET_EVENT_GROUP_TYPING        = "group_typing"
    const val SOCKET_EVENT_GROUP_TYPING_DONE   = "group_typing_done"
    const val SOCKET_EVENT_USER_ACTION         = "user_action"
    const val SOCKET_EVENT_GROUP_USER_ACTION   = "group_user_action"
    const val SOCKET_EVENT_MESSAGE_REACTION    = "message_reaction"
    const val SOCKET_EVENT_MESSAGE_PINNED      = "message_pinned"
    const val SOCKET_EVENT_CHANNEL_MESSAGE     = "channel_message"
    const val SOCKET_EVENT_USER_ONLINE         = "on_user_loggedin"
    const val SOCKET_EVENT_USER_OFFLINE        = "on_user_loggedoff"
    const val SOCKET_EVENT_CHAT_OPEN           = "is_chat_on"        // client → server: user opened a private chat
    const val SOCKET_EVENT_CHAT_CLOSE          = "close_chat"        // client → server: user closed a private chat

    // ── Live Location ────────────────────────────────────────────────────────
    const val SOCKET_EVENT_LIVE_LOCATION_START  = "live_location_start"   // client → server → recipient
    const val SOCKET_EVENT_LIVE_LOCATION_UPDATE = "live_location_update"  // periodic GPS update
    const val SOCKET_EVENT_LIVE_LOCATION_STOP   = "live_location_stop"    // user stopped sharing

    // ==================== USER ACTION TYPES ====================
    // Values sent in the "action" field of user_action / group_user_action events
    const val USER_ACTION_RECORDING        = "recording"
    const val USER_ACTION_RECORDING_VIDEO  = "recording_video"
    const val USER_ACTION_LISTENING        = "listening"
    const val USER_ACTION_VIEWING          = "viewing"
    const val USER_ACTION_CHOOSING_STICKER = "choosing_sticker"
    
    // ==================== PUSH NOTIFICATIONS ====================
    // Firebase видалений — використовуємо Socket.IO ForegroundService + keep-alive
    
    // ==================== MEDIA UPLOAD ====================
    const val MAX_IMAGE_SIZE = 25 * 1024 * 1024L // 25MB (увеличено с 15MB)
    const val MAX_VIDEO_SIZE = 1024 * 1024 * 1024L // 1GB (с сжатием)
    const val MAX_AUDIO_SIZE = 100 * 1024 * 1024L // 100MB (со сжатием)
    const val MAX_FILE_SIZE = 250 * 1024 * 1024L // 250MB для документов (уменьшено с 500MB)
    const val MAX_FILES_PER_MESSAGE = 15 // Максимум 15 файлов за раз
    
    const val MEDIA_UPLOAD_TIMEOUT = 600 // 10 minutes in seconds
    const val MEDIA_UPLOAD_CHUNK_SIZE = 256 * 1024 // 256KB chunks
    
    // ==================== CACHE ====================
    const val CACHE_EXPIRATION_TIME = 24 * 60 * 60 * 1000L // 24 hours in ms
    const val MAX_MESSAGES_IN_CACHE = 1000
    const val MAX_CHATS_IN_CACHE = 500
    
    // ==================== MESSAGE PAGINATION ====================
    const val MESSAGES_PAGE_SIZE = 30
    const val CHATS_PAGE_SIZE = 50
    const val GROUPS_PAGE_SIZE = 50
    const val MEMBERS_PAGE_SIZE = 100
    
    // ==================== TIMEOUTS ====================
    const val CONNECT_TIMEOUT_SECONDS = 30L
    const val READ_TIMEOUT_SECONDS = 30L
    const val WRITE_TIMEOUT_SECONDS = 30L
    
    // ==================== VOICE CALLS ====================
    const val VOICE_CALL_TIMEOUT = 60 // seconds
    const val VIDEO_CALL_TIMEOUT = 120 // seconds
    
    // ==================== DEVICE INFO ====================
    const val APP_VERSION = "2.0-EDIT-FIX"
    const val DEVICE_TYPE = "android"
    
    // ==================== COMPRESSION ====================
    const val IMAGE_COMPRESSION_QUALITY = 80 // 0-100
    const val VIDEO_COMPRESSION_BITRATE = 5000000 // 5 Mbps
    
    // ==================== MESSAGE TYPES ====================
    const val MESSAGE_TYPE_TEXT = "text"
    const val MESSAGE_TYPE_IMAGE = "image"
    const val MESSAGE_TYPE_VIDEO = "video"
    const val MESSAGE_TYPE_AUDIO = "audio"
    const val MESSAGE_TYPE_VOICE = "voice"
    const val MESSAGE_TYPE_FILE = "file"
    const val MESSAGE_TYPE_CALL = "call"
    const val MESSAGE_TYPE_LOCATION = "location"
    const val MESSAGE_TYPE_SYSTEM = "system"
    
    // ==================== CHAT TYPES ====================
    const val CHAT_TYPE_USER = "user"
    const val CHAT_TYPE_GROUP = "group"
    const val CHAT_TYPE_CHANNEL = "channel"
    const val CHAT_TYPE_PRIVATE_GROUP = "private_group"
    
    // ==================== USER ROLES ====================
    const val ROLE_ADMIN = "admin"
    const val ROLE_MODERATOR = "moderator"
    const val ROLE_MEMBER = "member"
    
    // ==================== ERROR CODES ====================
    const val ERROR_UNAUTHORIZED = 401
    const val ERROR_FORBIDDEN = 403
    const val ERROR_NOT_FOUND = 404
    const val ERROR_SERVER = 500
    const val ERROR_TIMEOUT = 408
    const val ERROR_NO_CONNECTION = 0
}
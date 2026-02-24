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

    // Messages
    const val NODE_CHAT_GET      = "api/node/chat/get"       // GET history
    const val NODE_CHAT_SEND     = "api/node/chat/send"      // send text
    const val NODE_CHAT_LOADMORE = "api/node/chat/loadmore"  // older messages
    const val NODE_CHAT_EDIT     = "api/node/chat/edit"      // edit message
    const val NODE_CHAT_SEARCH   = "api/node/chat/search"    // search in chat
    const val NODE_CHAT_SEEN         = "api/node/chat/seen"         // mark as seen
    const val NODE_CHAT_TYPING       = "api/node/chat/typing"       // typing indicator
    const val NODE_CHAT_NOTIFY_MEDIA = "api/node/chat/notify-media" // notify after PHP media save
    const val NODE_CHAT_SEND_MEDIA   = "api/node/chat/send-media"   // send media message (replaces PHP send_message for media)

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
    const val NODE_CHAT_COLOR    = "api/node/chat/color"               // change color
    const val NODE_CHAT_READ     = "api/node/chat/read"                // mark all as read

    // Favorites
    const val NODE_CHAT_FAV      = "api/node/chat/fav"                 // fav/unfav message
    const val NODE_CHAT_FAV_LIST = "api/node/chat/fav-list"            // get favorites

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

    // ==================== SOCKET.IO EVENTS ====================
    const val SOCKET_EVENT_AUTH = "join"  // Изменено с register_socket на join
    const val SOCKET_EVENT_PRIVATE_MESSAGE = "private_message"  // Событие личного сообщения от сервера
    const val SOCKET_EVENT_NEW_MESSAGE = "new_message"  // Оставлено для обратной совместимости
    const val SOCKET_EVENT_SEND_MESSAGE = "private_message"  // Отправка личного сообщения
    const val SOCKET_EVENT_TYPING = "typing"  // Изменено с is_typing
    const val SOCKET_EVENT_TYPING_DONE = "typing_done"  // Окончание набора
    const val SOCKET_EVENT_LAST_SEEN = "ping_for_lastseen"  // Изменено
    const val SOCKET_EVENT_MESSAGE_SEEN = "seen_messages"  // Изменено с message_seen
    const val SOCKET_EVENT_GROUP_MESSAGE = "group_message"
    const val SOCKET_EVENT_CHANNEL_MESSAGE = "channel_message"  // Новое сообщение в канале
    const val SOCKET_EVENT_USER_ONLINE = "on_user_loggedin"  // Изменено с user_online
    const val SOCKET_EVENT_USER_OFFLINE = "on_user_loggedoff"  // ИСПРАВЛЕНО: было user_status_change
    
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
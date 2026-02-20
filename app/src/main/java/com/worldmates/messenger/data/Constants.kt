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
    const val SOCKET_EVENT_USER_ONLINE = "on_user_loggedin"  // Изменено с user_online
    const val SOCKET_EVENT_USER_OFFLINE = "on_user_loggedoff"  // ИСПРАВЛЕНО: было user_status_change
    
    // ==================== PUSH NOTIFICATIONS ====================
    const val FCM_TOPIC_MESSAGES = "worldmates_messages"
    const val FCM_TOPIC_CALLS = "worldmates_calls"
    
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
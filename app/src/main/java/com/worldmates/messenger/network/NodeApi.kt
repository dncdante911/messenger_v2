package com.worldmates.messenger.network

import com.google.gson.annotations.SerializedName
import com.worldmates.messenger.data.Constants
import com.worldmates.messenger.data.model.Chat
import com.worldmates.messenger.data.model.Message
import retrofit2.http.*

/**
 * Node.js REST API — Retrofit interface for private chats.
 *
 * Окремий інтерфейс (не розширює WorldMatesApi) щоб:
 *  - уникнути ApiKeyInterceptor (server_key / s — не потрібні на Node.js)
 *  - тримати файл WorldMatesApi.kt в розумних межах
 *  - мати чіткий кордон «PHP API» vs «Node.js API»
 *
 * Base URL: Constants.NODE_BASE_URL  ("https://worldmates.club:449/")
 * Auth:     access-token header, додається NodeAuthInterceptor
 */
interface NodeApi {

    // ═══════════════════════ MESSAGES ════════════════════════════════════════

    /**
     * GET message history between current user and recipient.
     * Replaces: PHP get_messages.php
     */
    @FormUrlEncoded
    @POST(Constants.NODE_CHAT_GET)
    suspend fun getMessages(
        @Field("recipient_id")    recipientId: Long,
        @Field("limit")           limit: Int = 30,
        @Field("before_message_id") beforeMessageId: Long = 0,
        @Field("after_message_id")  afterMessageId: Long  = 0,
        @Field("message_id")      messageId: Long = 0
    ): NodeMessageListResponse

    /**
     * SEND text message.
     * Replaces: Socket.IO private_message event + PHP send-message.php (text path)
     */
    @FormUrlEncoded
    @POST(Constants.NODE_CHAT_SEND)
    suspend fun sendMessage(
        @Field("recipient_id") recipientId: Long,
        @Field("text")         text: String,
        @Field("reply_id")     replyId: Long?   = null,
        @Field("story_id")     storyId: Long?   = null,
        @Field("stickers")     stickers: String? = null,
        @Field("lat")          lat: String?      = null,
        @Field("lng")          lng: String?      = null,
        @Field("contact")      contact: String?  = null
    ): NodeMessageResponse

    /**
     * SEND MEDIA MESSAGE — creates a message with the uploaded media URL.
     * Replaces the broken PHP send_message endpoint for media types.
     * Supports both private chats (recipient_id) and group chats (group_id).
     *
     * media_type: "voice", "audio", "video", "image", "file"
     */
    @FormUrlEncoded
    @POST(Constants.NODE_CHAT_SEND_MEDIA)
    suspend fun sendMediaMessage(
        @Field("recipient_id")    recipientId: Long = 0,
        @Field("group_id")        groupId: Long = 0,
        @Field("media_url")       mediaUrl: String,
        @Field("media_type")      mediaType: String,
        @Field("media_file_name") mediaFileName: String = "",
        @Field("message_hash_id") messageHashId: String = "",
        @Field("reply_id")        replyId: Long = 0,
        /** Optional caption shown under the media. Server encrypts with AES-256-GCM. */
        @Field("caption")         caption: String = ""
    ): NodeMessageResponse

    /**
     * NOTIFY-MEDIA — called after PHP saves a voice/video/audio message.
     * Node.js fetches the saved message and broadcasts it via Socket.IO to
     * both sender and recipient for real-time delivery.
     */
    @FormUrlEncoded
    @POST(Constants.NODE_CHAT_NOTIFY_MEDIA)
    suspend fun notifyMediaMessage(
        @Field("recipient_id") recipientId: Long,
        @Field("message_id")   messageId: Long = 0
    ): NodeSimpleResponse

    /**
     * LOADMORE — older messages (pagination).
     */
    @FormUrlEncoded
    @POST(Constants.NODE_CHAT_LOADMORE)
    suspend fun loadMore(
        @Field("recipient_id")      recipientId: Long,
        @Field("before_message_id") beforeMessageId: Long,
        @Field("limit")             limit: Int = 15
    ): NodeMessageListResponse

    /**
     * EDIT a sent message.
     */
    @FormUrlEncoded
    @POST(Constants.NODE_CHAT_EDIT)
    suspend fun editMessage(
        @Field("message_id") messageId: Long,
        @Field("text")       text: String
    ): NodeSimpleResponse

    /**
     * SEARCH messages in a conversation.
     */
    @FormUrlEncoded
    @POST(Constants.NODE_CHAT_SEARCH)
    suspend fun searchMessages(
        @Field("recipient_id") recipientId: Long,
        @Field("query")        query: String,
        @Field("limit")        limit: Int = 50,
        @Field("offset")       offset: Int = 0
    ): NodeMessageListResponse

    /**
     * MARK SEEN — notify server that messages were read.
     */
    @FormUrlEncoded
    @POST(Constants.NODE_CHAT_SEEN)
    suspend fun markSeen(
        @Field("recipient_id") recipientId: Long
    ): NodeSimpleResponse

    /**
     * TYPING indicator.
     */
    @FormUrlEncoded
    @POST(Constants.NODE_CHAT_TYPING)
    suspend fun sendTyping(
        @Field("recipient_id") recipientId: Long,
        @Field("typing")       typing: String  // "true" | "false"
    ): NodeSimpleResponse

    /**
     * USER ACTION status (listening, viewing, choosing_sticker, recording_video, etc.)
     */
    @FormUrlEncoded
    @POST(Constants.NODE_CHAT_USER_ACTION)
    suspend fun sendUserAction(
        @Field("recipient_id") recipientId: Long,
        @Field("action")       action: String
    ): NodeSimpleResponse

    // ═══════════════════════ ACTIONS ═════════════════════════════════════════

    /**
     * DELETE message.
     * delete_type: "just_me" | "everyone"
     */
    @FormUrlEncoded
    @POST(Constants.NODE_CHAT_DELETE)
    suspend fun deleteMessage(
        @Field("message_id")   messageId: Long,
        @Field("delete_type")  deleteType: String = "just_me"
    ): NodeSimpleResponse

    /**
     * REACT to message. Same emoji → toggle off; different → replace.
     */
    @FormUrlEncoded
    @POST(Constants.NODE_CHAT_REACT)
    suspend fun reactToMessage(
        @Field("message_id") messageId: Long,
        @Field("reaction")   reaction: String
    ): NodeReactResponse

    /**
     * PIN / UNPIN a message in conversation.
     * pin: "yes" | "no"
     */
    @FormUrlEncoded
    @POST(Constants.NODE_CHAT_PIN)
    suspend fun pinMessage(
        @Field("message_id") messageId: Long,
        @Field("chat_id")    chatId: Long,
        @Field("pin")        pin: String = "yes"
    ): NodeSimpleResponse

    /**
     * GET pinned messages for a conversation.
     */
    @FormUrlEncoded
    @POST(Constants.NODE_CHAT_PINNED)
    suspend fun getPinnedMessages(
        @Field("chat_id") chatId: Long
    ): NodeMessageListResponse

    /**
     * FORWARD message to one or multiple recipients.
     * Accepts comma-separated recipient_ids.
     */
    @FormUrlEncoded
    @POST(Constants.NODE_CHAT_FORWARD)
    suspend fun forwardMessage(
        @Field("message_id")    messageId: Long,
        @Field("recipient_ids") recipientIds: String   // "1,2,3"
    ): NodeForwardResponse

    // ═══════════════════════ CHATS LIST & SETTINGS ═══════════════════════════

    /**
     * GET conversations list.
     * Replaces: PHP get_chats.php (users section)
     */
    @FormUrlEncoded
    @POST(Constants.NODE_CHATS_LIST)
    suspend fun getChats(
        @Field("limit")          limit: Int = 30,
        @Field("offset")         offset: Int = 0,
        @Field("show_archived")  showArchived: String = "false"
    ): NodeChatListResponse

    /**
     * DELETE entire conversation.
     * delete_type: "me" | "all"
     */
    @FormUrlEncoded
    @POST(Constants.NODE_CHAT_DEL_CONV)
    suspend fun deleteConversation(
        @Field("user_id")      userId: Long,
        @Field("delete_type")  deleteType: String = "me"
    ): NodeSimpleResponse

    /**
     * ARCHIVE / UNARCHIVE chat.
     * archive: "yes" | "no"
     */
    @FormUrlEncoded
    @POST(Constants.NODE_CHAT_ARCHIVE)
    suspend fun archiveChat(
        @Field("chat_id")  chatId: Long,
        @Field("archive")  archive: String
    ): NodeSimpleResponse

    /**
     * MUTE / UNMUTE notifications.
     * notify: "yes" | "no"
     */
    @FormUrlEncoded
    @POST(Constants.NODE_CHAT_MUTE)
    suspend fun muteChat(
        @Field("chat_id")    chatId: Long,
        @Field("notify")     notify: String,
        @Field("call_chat")  callChat: String = "yes"
    ): NodeSimpleResponse

    /**
     * PIN / UNPIN entire chat in list.
     * pin: "yes" | "no"
     */
    @FormUrlEncoded
    @POST(Constants.NODE_CHAT_PIN_CHAT)
    suspend fun pinChat(
        @Field("chat_id") chatId: Long,
        @Field("pin")     pin: String
    ): NodeSimpleResponse

    /**
     * CHANGE chat accent color (hex without #).
     */
    @FormUrlEncoded
    @POST(Constants.NODE_CHAT_COLOR)
    suspend fun changeChatColor(
        @Field("user_id") userId: Long,
        @Field("color")   color: String   // hex string, e.g. "FF5733"
    ): NodeSimpleResponse

    /**
     * MARK all messages in a chat as read.
     */
    @FormUrlEncoded
    @POST(Constants.NODE_CHAT_READ)
    suspend fun readChat(
        @Field("recipient_id") recipientId: Long
    ): NodeSimpleResponse

    /**
     * CLEAR HISTORY — soft-deletes all messages for the caller only.
     * Messages are flagged deleted_one/deleted_two; conversation entry stays.
     */
    @FormUrlEncoded
    @POST(Constants.NODE_CHAT_CLEAR_HIST)
    suspend fun clearHistory(
        @Field("recipient_id") recipientId: Long
    ): NodeSimpleResponse

    /**
     * GET MUTE STATUS — returns current notify/call_chat/archive/pin flags for a chat.
     */
    @FormUrlEncoded
    @POST(Constants.NODE_CHAT_MUTE_STATUS)
    suspend fun getMuteStatus(
        @Field("chat_id") chatId: Long
    ): NodeMuteStatusResponse

    // ═══════════════════════ FAVORITES ═══════════════════════════════════════

    /**
     * FAV / UNFAV message.
     * fav: "yes" | "no"
     */
    @FormUrlEncoded
    @POST(Constants.NODE_CHAT_FAV)
    suspend fun favMessage(
        @Field("message_id") messageId: Long,
        @Field("chat_id")    chatId: Long,
        @Field("fav")        fav: String
    ): NodeSimpleResponse

    /**
     * GET favorite messages for a conversation.
     */
    @FormUrlEncoded
    @POST(Constants.NODE_CHAT_FAV_LIST)
    suspend fun getFavMessages(
        @Field("chat_id") chatId: Long,
        @Field("limit")   limit: Int = 50,
        @Field("offset")  offset: Int = 0
    ): NodeMessageListResponse

    // ═══════════════════════ AUTH (no access-token required) ═════════════════

    /** Send 6-digit OTP code for password reset. */
    @FormUrlEncoded
    @POST("api/node/auth/request-password-reset")
    suspend fun requestPasswordReset(
        @Field("email")        email: String? = null,
        @Field("phone_number") phoneNumber: String? = null
    ): PasswordResetRequestResponse

    /** Verify OTP + set new password. */
    @FormUrlEncoded
    @POST("api/node/auth/reset-password")
    suspend fun resetPassword(
        @Field("email")        email: String? = null,
        @Field("phone_number") phoneNumber: String? = null,
        @Field("code")         code: String,
        @Field("new_password") newPassword: String
    ): PasswordResetResponse

    /** Send 6-digit OTP for quick registration/login (no password). */
    @FormUrlEncoded
    @POST("api/node/auth/quick-register")
    suspend fun quickRegister(
        @Field("email")        email: String? = null,
        @Field("phone_number") phoneNumber: String? = null
    ): QuickRegisterResponse

    /** Verify OTP code and return session token. */
    @FormUrlEncoded
    @POST("api/node/auth/quick-verify")
    suspend fun quickVerify(
        @Field("email")        email: String? = null,
        @Field("phone_number") phoneNumber: String? = null,
        @Field("code")         code: String
    ): QuickVerifyResponse
}

// ═══════════════════════ RESPONSE MODELS ═════════════════════════════════════

/** Lightweight Node.js messages list response. */
data class NodeMessageListResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("messages")   val messages: List<Message>?,
    @SerializedName("count")      val count: Int? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

/** Lightweight Node.js single message response (send / edit). */
data class NodeMessageResponse(
    @SerializedName("api_status")   val apiStatus: Int,
    @SerializedName("message_data") val messageData: Message? = null,
    @SerializedName("message_id")   val messageId: Long?      = null,
    @SerializedName("text")         val text: String?         = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

/** Generic success/failure response. */
data class NodeSimpleResponse(
    @SerializedName("api_status")   val apiStatus: Int,
    @SerializedName("message")      val message: String? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

/** Response for reaction toggle. */
data class NodeReactResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("action")    val action: String?   = null,  // "added" | "updated" | "removed"
    @SerializedName("reaction")  val reaction: String? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

/** Response for forward (returns new message ids). */
data class NodeForwardResponse(
    @SerializedName("api_status")   val apiStatus: Int,
    @SerializedName("forwarded_ids") val forwardedIds: List<Long>? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

/** Chats list from Node.js. */
data class NodeChatListResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("data")       val data: List<Chat>? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

/** Mute/notify status for a private chat. */
data class NodeMuteStatusResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("notify")     val notify: String?   = null,  // "yes" | "no"
    @SerializedName("call_chat")  val callChat: String? = null,  // "yes" | "no"
    @SerializedName("archive")    val archive: String?  = null,  // "yes" | "no"
    @SerializedName("pin")        val pin: String?      = null,  // "yes" | "no"
    @SerializedName("error_message") val errorMessage: String? = null
)

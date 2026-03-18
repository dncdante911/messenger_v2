package com.worldmates.messenger.network

import com.google.gson.annotations.SerializedName
import com.worldmates.messenger.data.Constants
import com.worldmates.messenger.data.model.AuthResponse
import com.worldmates.messenger.data.model.BackupStatisticsResponse
import com.worldmates.messenger.data.model.Chat
import com.worldmates.messenger.data.model.CloudBackupSettingsResponse
import com.worldmates.messenger.data.model.EmojiPackDetailResponse
import com.worldmates.messenger.data.model.EmojiPacksResponse
import com.worldmates.messenger.data.model.ExportDataResponse
import com.worldmates.messenger.data.model.ImportDataResponse
import com.worldmates.messenger.data.model.ListBackupsResponse
import com.worldmates.messenger.data.model.Message
import com.worldmates.messenger.data.model.StickerPackDetailResponse
import com.worldmates.messenger.data.model.StickerPacksResponse
import com.worldmates.messenger.data.model.UpdateCloudBackupSettingsResponse
import com.worldmates.messenger.data.model.UserAvatar
import com.worldmates.messenger.data.model.UserAvatarListResponse
import com.worldmates.messenger.data.model.UserAvatarSimpleResponse
import com.worldmates.messenger.data.model.UserAvatarUploadResponse
import okhttp3.MultipartBody
import com.worldmates.messenger.utils.signal.PreKeyBundleResponse
import com.worldmates.messenger.utils.signal.SignalRegisterRequest
import com.worldmates.messenger.utils.signal.SignalReplenishRequest
import com.worldmates.messenger.utils.signal.SignalSimpleResponse
import com.worldmates.messenger.utils.signal.SignalGroupDistributeResponse
import com.worldmates.messenger.utils.signal.SignalGroupPendingResponse
import com.worldmates.messenger.utils.signal.SignalGroupConfirmResponse
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
     * SEND text message (supports AES-256-GCM and Signal Double Ratchet).
     *
     * For cipher_version=2 (default): server encrypts plaintext in [text].
     * For cipher_version=3 (Signal):  client pre-encrypts; [text] = Base64(ciphertext),
     *   [iv]/[tag] = crypto params, [signal_header] = JSON DR header.
     *   Server stores without re-encrypting (true E2EE).
     */
    @FormUrlEncoded
    @POST(Constants.NODE_CHAT_SEND)
    suspend fun sendMessage(
        @Field("recipient_id")   recipientId: Long,
        @Field("text")           text: String,
        @Field("reply_id")       replyId: Long?    = null,
        @Field("story_id")       storyId: Long?    = null,
        @Field("stickers")       stickers: String? = null,
        @Field("lat")            lat: String?      = null,
        @Field("lng")            lng: String?      = null,
        @Field("contact")        contact: String?  = null,
        // ── Signal Protocol fields (cipher_version=3 only) ──────────────────
        @Field("iv")             iv: String?           = null,
        @Field("tag")            tag: String?          = null,
        @Field("signal_header")  signalHeader: String? = null,
        @Field("cipher_version") cipherVersion: Int?   = null
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
     * UPLOAD CHAT MEDIA — replaces PHP /xhr/upload_*.php endpoints.
     * Single unified endpoint for image / video / audio / voice / file uploads.
     *
     * @param type  one of: "image", "video", "audio", "voice", "file"
     * @param file  multipart part with field name "file"
     *
     * Returns XhrUploadResponse-compatible JSON:
     *   { status: 200, image: "url", image_src: "path" }  for images
     *   { status: 200, video: "url", video_src: "path" }  for videos
     *   { status: 200, audio: "url", audio_src: "path" }  for audio/voice
     *   { status: 200, file:  "url", file_src:  "path" }  for documents
     */
    @Multipart
    @POST("api/node/chat/upload")
    suspend fun uploadChatMedia(
        @Part("type") type: okhttp3.RequestBody,
        @Part         file: MultipartBody.Part
    ): XhrUploadResponse

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

    // ═══════════════════════ USER PRESENCE ═══════════════════════════════════

    /**
     * GET online status + last_seen for any user.
     * Called when opening a chat to initialise the header status bar immediately,
     * without waiting for socket events (which only fire on connect/disconnect).
     */
    @FormUrlEncoded
    @POST(Constants.NODE_USER_STATUS)
    suspend fun getUserStatus(
        @Field("user_id") userId: Long
    ): NodeUserStatusResponse

    // ═══════════════════════ MULTI-AVATAR ════════════════════════════════════

    /** Get all avatars for a user (gallery). */
    @GET(Constants.NODE_AVATAR_LIST)
    suspend fun getUserAvatars(
        @Path("userId") userId: Long
    ): UserAvatarListResponse

    /** Upload a new avatar photo or animated GIF/WebP. */
    @Multipart
    @POST(Constants.NODE_AVATAR_UPLOAD)
    suspend fun uploadAvatar(
        @Part avatar: MultipartBody.Part,
        @Part("set_as_main") setAsMain: okhttp3.RequestBody = okhttp3.RequestBody.create(null, "true")
    ): UserAvatarUploadResponse

    /** Set an avatar as the main profile photo. */
    @POST(Constants.NODE_AVATAR_SET_MAIN)
    suspend fun setMainAvatar(
        @Path("id") avatarId: Long
    ): UserAvatarSimpleResponse

    /** Reorder avatars by providing an ordered list of IDs. */
    @FormUrlEncoded
    @POST(Constants.NODE_AVATAR_REORDER)
    suspend fun reorderAvatars(
        @Field("ids[]") ids: List<Long>
    ): UserAvatarSimpleResponse

    /** Delete an avatar by ID. */
    @DELETE(Constants.NODE_AVATAR_DELETE)
    suspend fun deleteAvatar(
        @Path("id") avatarId: Long
    ): UserAvatarSimpleResponse

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

    // ═══════════════════════ SAVED MESSAGES (server-side bookmarks) ══════════

    /** Bookmark a message. Idempotent — safe to call even if already saved. */
    @FormUrlEncoded
    @POST(Constants.NODE_SAVED_SAVE)
    suspend fun saveMessage(
        @Field("message_id")    messageId: Long,
        @Field("chat_type")     chatType: String,
        @Field("chat_id")       chatId: Long,
        @Field("chat_name")     chatName: String,
        @Field("sender_name")   senderName: String,
        @Field("text")          text: String? = null,
        @Field("media_url")     mediaUrl: String? = null,
        @Field("media_type")    mediaType: String? = null,
        @Field("original_time") originalTime: Long = 0
    ): NodeSavedMessageResponse

    /** Remove a bookmark. */
    @FormUrlEncoded
    @POST(Constants.NODE_SAVED_UNSAVE)
    suspend fun unsaveMessage(
        @Field("message_id") messageId: Long,
        @Field("chat_type")  chatType: String
    ): NodeSimpleResponse

    /** Fetch all saved messages for the current user. */
    @FormUrlEncoded
    @POST(Constants.NODE_SAVED_LIST)
    suspend fun listSaved(
        @Field("limit")  limit: Int = 200,
        @Field("offset") offset: Int = 0
    ): NodeSavedListResponse

    /** Remove all bookmarks for the current user. */
    @POST(Constants.NODE_SAVED_CLEAR)
    suspend fun clearSaved(): NodeSimpleResponse

    // ═══════════════════════ SIGNAL PROTOCOL ════════════════════════════════

    /**
     * Upload our identity key + signed pre-key + one-time pre-keys to the server.
     * Call once per app installation (or after key rotation).
     * [prekeys] = JSON array string, e.g. [{"id":1,"key":"base64..."},...]
     */
    @FormUrlEncoded
    @POST("api/node/signal/register")
    suspend fun registerSignalKeys(
        @Field("identity_key")      identityKey:     String,
        @Field("signed_prekey_id")  signedPreKeyId:  Int,
        @Field("signed_prekey")     signedPreKey:    String,
        @Field("signed_prekey_sig") signedPreKeySig: String,
        @Field("prekeys")           prekeys:         String   // JSON array of {id,key}
    ): SignalSimpleResponse

    /**
     * Fetch another user's pre-key bundle to initiate X3DH.
     * Server pops one one-time pre-key from the target user's pool.
     */
    @GET("api/node/signal/bundle/{userId}")
    suspend fun getSignalBundle(
        @Path("userId") userId: Long
    ): PreKeyBundleResponse

    /**
     * Upload a fresh batch of one-time pre-keys (called when local pool runs low).
     * [prekeys] = JSON array string.
     */
    @FormUrlEncoded
    @POST("api/node/signal/replenish")
    suspend fun replenishSignalPreKeys(
        @Field("prekeys") prekeys: String   // JSON array of {id,key}
    ): SignalSimpleResponse

    /**
     * Check how many one-time pre-keys remain on the server for current user.
     */
    @GET("api/node/signal/prekey-count")
    suspend fun getSignalPreKeyCount(): SignalSimpleResponse

    // ═══════════════════════ SIGNAL PROTOCOL — ГРУПОВІ ЧАТИ ══════════════════

    /**
     * Завантажити SenderKeyDistribution від поточного користувача до учасників групи.
     *
     * [distributions] = JSON масив: [{"recipient_id": 123, "distribution": "base64..."}, ...]
     * Кожен distribution зашифрований індивідуальним DR-сеансом з відповідним учасником.
     */
    @FormUrlEncoded
    @POST("api/node/signal/group/distribute")
    suspend fun distributeGroupSenderKey(
        @Field("group_id")      groupId:       Long,
        @Field("distributions") distributions: String   // JSON array
    ): SignalGroupDistributeResponse

    /**
     * Отримати всі невидані SenderKey distributions для поточного користувача.
     * [groupId] = 0 → всі групи; > 0 → тільки ця група.
     */
    @FormUrlEncoded
    @POST("api/node/signal/group/pending-distributions")
    suspend fun getGroupPendingDistributions(
        @Field("group_id") groupId: Long = 0
    ): SignalGroupPendingResponse

    /**
     * Підтвердити отримання distributions (після успішного розшифрування).
     * [distributionIds] = JSON масив ID: [1, 2, 3]
     */
    @FormUrlEncoded
    @POST("api/node/signal/group/confirm-delivery")
    suspend fun confirmGroupDistributionDelivery(
        @Field("distribution_ids") distributionIds: String   // JSON array of IDs
    ): SignalGroupConfirmResponse

    /**
     * Інвалідувати SenderKey учасника групи (при виході/видаленні).
     * Дозволено: сам senderId або адміністратор групи.
     */
    @FormUrlEncoded
    @POST("api/node/signal/group/invalidate-sender-key")
    suspend fun invalidateGroupSenderKey(
        @Field("group_id")  groupId:  Long,
        @Field("sender_id") senderId: Long
    ): SignalSimpleResponse

    // ═══════════════════════ SCHEDULED MESSAGES ══════════════════════════════

    @GET(Constants.NODE_SCHEDULED_LIST)
    suspend fun getScheduledMessages(
        @Query("chat_id")   chatId: Long,
        @Query("chat_type") chatType: String = "group",
        @Query("status")    status: String   = "pending"
    ): NodeScheduledListResponse

    @FormUrlEncoded
    @POST(Constants.NODE_SCHEDULED_CREATE)
    suspend fun createScheduledMessage(
        @Field("chat_id")        chatId: Long,
        @Field("chat_type")      chatType: String,
        @Field("text")           text: String = "",
        @Field("scheduled_at")   scheduledAt: Long,
        @Field("repeat_type")    repeatType: String = "none",
        @Field("is_pinned")      isPinned: Boolean = false,
        @Field("notify_members") notifyMembers: Boolean = true
    ): NodeScheduledItemResponse

    @FormUrlEncoded
    @POST(Constants.NODE_SCHEDULED_UPDATE)
    suspend fun updateScheduledMessage(
        @Path("id")              id: Long,
        @Field("text")           text: String? = null,
        @Field("scheduled_at")   scheduledAt: Long? = null,
        @Field("repeat_type")    repeatType: String? = null
    ): NodeScheduledItemResponse

    @DELETE(Constants.NODE_SCHEDULED_DELETE)
    suspend fun deleteScheduledMessage(
        @Path("id") id: Long
    ): NodeSimpleResponse

    @POST(Constants.NODE_SCHEDULED_SEND_NOW)
    suspend fun sendScheduledNow(
        @Path("id") id: Long
    ): NodeSimpleResponse

    // ═══════════════════════ CHANNEL THREADS ══════════════════════════════════

    @GET(Constants.NODE_THREAD_MESSAGES)
    suspend fun getThreadMessages(
        @Path("postId") postId: Long,
        @Query("limit")  limit: Int  = 50,
        @Query("offset") offset: Int = 0
    ): NodeThreadListResponse

    @FormUrlEncoded
    @POST(Constants.NODE_THREAD_SEND)
    suspend fun sendThreadMessage(
        @Path("postId")       postId: Long,
        @Field("text")        text: String,
        @Field("reply_to_id") replyToId: Long? = null
    ): NodeThreadMessageResponse

    @DELETE(Constants.NODE_THREAD_DELETE)
    suspend fun deleteThreadMessage(
        @Path("postId") postId: Long,
        @Path("msgId")  msgId: Long
    ): NodeSimpleResponse

    @GET(Constants.NODE_THREAD_COUNT)
    suspend fun getThreadCount(
        @Path("postId") postId: Long
    ): NodeThreadCountResponse

    @FormUrlEncoded
    @POST(Constants.NODE_THREAD_BATCH_COUNTS)
    suspend fun getThreadBatchCounts(
        @Field("post_ids[]") postIds: List<Long>
    ): NodeThreadBatchCountsResponse

    // ═══════════════════════ SHARED CHAT FOLDERS ══════════════════════════════

    @GET(Constants.NODE_FOLDER_LIST)
    suspend fun getFolders(): NodeFolderListResponse

    @FormUrlEncoded
    @POST(Constants.NODE_FOLDER_CREATE)
    suspend fun createFolder(
        @Field("name")  name: String,
        @Field("emoji") emoji: String = "📁",
        @Field("color") color: String = "#2196F3"
    ): NodeFolderItemResponse

    @FormUrlEncoded
    @PATCH(Constants.NODE_FOLDER_UPDATE)
    suspend fun updateFolder(
        @Path("id")     id: Long,
        @Field("name")  name: String? = null,
        @Field("emoji") emoji: String? = null,
        @Field("color") color: String? = null
    ): NodeFolderItemResponse

    @DELETE(Constants.NODE_FOLDER_DELETE)
    suspend fun deleteFolder(
        @Path("id") id: Long
    ): NodeSimpleResponse

    @FormUrlEncoded
    @POST(Constants.NODE_FOLDER_ADD_CHAT)
    suspend fun addChatToFolder(
        @Path("id")          id: Long,
        @Field("chat_type")  chatType: String,
        @Field("chat_id")    chatId: Long
    ): NodeSimpleResponse

    @FormUrlEncoded
    @POST(Constants.NODE_FOLDER_REMOVE_CHAT)
    suspend fun removeChatFromFolder(
        @Path("id")          id: Long,
        @Field("chat_type")  chatType: String,
        @Field("chat_id")    chatId: Long
    ): NodeSimpleResponse

    @FormUrlEncoded
    @POST(Constants.NODE_FOLDER_REORDER)
    suspend fun reorderFolders(
        @Field("ids[]") ids: List<Long>
    ): NodeSimpleResponse

    @POST(Constants.NODE_FOLDER_SHARE)
    suspend fun shareFolder(
        @Path("id") id: Long
    ): NodeFolderShareResponse

    @POST(Constants.NODE_FOLDER_JOIN)
    suspend fun joinFolder(
        @Path("code") code: String
    ): NodeFolderItemResponse

    @DELETE(Constants.NODE_FOLDER_LEAVE)
    suspend fun leaveFolder(
        @Path("id") id: Long
    ): NodeSimpleResponse

    // ═══════════════════════ AUTH (no access-token required) ═════════════════

    /** Username/email + password login. Returns session token. */
    @FormUrlEncoded
    @POST("api/node/auth/login")
    suspend fun login(
        @Field("username")     username:    String,
        @Field("password")     password:    String,
        @Field("device_type")  deviceType:  String = "phone"
    ): NodeLoginResponse

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

    /** Classic registration: username + email/phone + password. */
    @FormUrlEncoded
    @POST("api/node/auth/register")
    suspend fun register(
        @Field("username")         username:        String,
        @Field("email")            email:           String? = null,
        @Field("phone_number")     phoneNumber:     String? = null,
        @Field("password")         password:        String,
        @Field("confirm_password") confirmPassword: String,
        @Field("gender")           gender:          String  = "male",
        @Field("device_type")      deviceType:      String  = "phone"
    ): AuthResponse

    /** Send 6-digit OTP to an existing user for email/phone verification. */
    @FormUrlEncoded
    @POST("api/node/auth/send-code")
    suspend fun sendVerificationCode(
        @Field("verification_type") verificationType: String,
        @Field("contact_info")      contactInfo:      String,
        @Field("username")          username:         String? = null
    ): SendCodeResponse

    /** Verify OTP and activate the account; returns access_token. */
    @FormUrlEncoded
    @POST("api/node/auth/verify-code")
    suspend fun verifyCode(
        @Field("verification_type") verificationType: String,
        @Field("contact_info")      contactInfo:      String,
        @Field("code")              code:             String,
        @Field("username")          username:         String? = null
    ): VerifyCodeResponse

    /** Exchange a refresh token for a new access + refresh token pair. */
    @FormUrlEncoded
    @POST("api/node/auth/refresh")
    suspend fun refreshToken(
        @Field("refresh_token") refreshToken: String
    ): TokenRefreshResponse

    // ═══════════════════════ BACKUP / CLOUD SETTINGS ═════════════════════════

    /** Get cloud backup settings (replaces PHP get_cloud_backup_settings.php). */
    @GET(Constants.NODE_BACKUP_SETTINGS)
    suspend fun getBackupSettings(): CloudBackupSettingsResponse

    /** Update cloud backup settings (replaces PHP update_cloud_backup_settings.php). */
    @FormUrlEncoded
    @POST(Constants.NODE_BACKUP_SETTINGS)
    suspend fun updateBackupSettings(
        @Field("mobile_photos")                  mobilePhotos:               String? = null,
        @Field("mobile_videos")                  mobileVideos:               String? = null,
        @Field("mobile_files")                   mobileFiles:                String? = null,
        @Field("mobile_videos_limit")            mobileVideosLimit:          Int?    = null,
        @Field("mobile_files_limit")             mobileFilesLimit:           Int?    = null,
        @Field("wifi_photos")                    wifiPhotos:                 String? = null,
        @Field("wifi_videos")                    wifiVideos:                 String? = null,
        @Field("wifi_files")                     wifiFiles:                  String? = null,
        @Field("wifi_videos_limit")              wifiVideosLimit:            Int?    = null,
        @Field("wifi_files_limit")               wifiFilesLimit:             Int?    = null,
        @Field("roaming_photos")                 roamingPhotos:              String? = null,
        @Field("save_to_gallery_private_chats")  saveToGalleryPrivateChats:  String? = null,
        @Field("save_to_gallery_groups")         saveToGalleryGroups:        String? = null,
        @Field("save_to_gallery_channels")       saveToGalleryChannels:      String? = null,
        @Field("streaming_enabled")              streamingEnabled:           String? = null,
        @Field("cache_size_limit")               cacheSizeLimit:             Long?   = null,
        @Field("backup_enabled")                 backupEnabled:              String? = null,
        @Field("backup_provider")                backupProvider:             String? = null,
        @Field("backup_frequency")               backupFrequency:            String? = null,
        @Field("mark_backup_complete")           markBackupComplete:         String? = null,
        @Field("proxy_enabled")                  proxyEnabled:               String? = null,
        @Field("proxy_host")                     proxyHost:                  String? = null,
        @Field("proxy_port")                     proxyPort:                  Int?    = null
    ): UpdateCloudBackupSettingsResponse

    /** Get backup statistics (replaces PHP get-backup-statistics.php). */
    @POST(Constants.NODE_BACKUP_STATISTICS)
    suspend fun getBackupStatistics(): BackupStatisticsResponse

    /** Export all user data as a JSON backup. */
    @GET("api/node/backup/export")
    suspend fun exportUserData(): ExportDataResponse

    /** List server-side backup files for the user. */
    @GET("api/node/backup/list")
    suspend fun listBackups(): ListBackupsResponse

    /** Import a JSON backup and restore messages. */
    @FormUrlEncoded
    @POST("api/node/backup/import")
    suspend fun importUserData(
        @Field("backup_data") backupData: String
    ): ImportDataResponse

    // ═══════════════════════ STICKERS ════════════════════════════════════════

    /** List all sticker packs. */
    @GET("api/node/stickers")
    suspend fun getStickerPacks(): StickerPacksResponse

    /** Get a specific sticker pack with all stickers. */
    @GET("api/node/stickers/{packId}")
    suspend fun getStickerPack(
        @Path("packId") packId: Long
    ): StickerPackDetailResponse

    /** Activate a sticker pack for the current user. */
    @FormUrlEncoded
    @POST("api/node/stickers/{packId}/activate")
    suspend fun activateStickerPack(
        @Path("packId") packId: Long
    ): StickerPackDetailResponse

    /** Deactivate a sticker pack for the current user. */
    @FormUrlEncoded
    @POST("api/node/stickers/{packId}/deactivate")
    suspend fun deactivateStickerPack(
        @Path("packId") packId: Long
    ): StickerPackDetailResponse

    // ═══════════════════════ EMOJI PACKS ═════════════════════════════════════

    /** List all custom emoji packs. */
    @GET("api/node/emoji")
    suspend fun getEmojiPacks(): EmojiPacksResponse

    /** Get a specific emoji pack. */
    @GET("api/node/emoji/{packId}")
    suspend fun getEmojiPack(
        @Path("packId") packId: Long
    ): EmojiPackDetailResponse

    /** Activate an emoji pack. */
    @FormUrlEncoded
    @POST("api/node/emoji/{packId}/activate")
    suspend fun activateEmojiPack(
        @Path("packId") packId: Long
    ): EmojiPackDetailResponse

    /** Deactivate an emoji pack. */
    @FormUrlEncoded
    @POST("api/node/emoji/{packId}/deactivate")
    suspend fun deactivateEmojiPack(
        @Path("packId") packId: Long
    ): EmojiPackDetailResponse

    // ═══════════════════════ APP UPDATE ══════════════════════════════════════

    @GET(Constants.NODE_UPDATE_CHECK)
    suspend fun checkAppUpdate(): com.worldmates.messenger.data.model.AppUpdateResponse

    // ═══════════════════════ CHAT MESSAGE COUNT ═══════════════════════════════

    /** Count total messages in a private conversation (for backup progress bar). */
    @FormUrlEncoded
    @POST("api/node/chat/count")
    suspend fun getChatMessageCount(
        @Field("recipient_id") recipientId: Long
    ): com.worldmates.messenger.data.model.MessageCountResponse

    // ═══════════════════════ MEDIA SETTINGS ══════════════════════════════════

    /** Get user media auto-download settings. */
    @FormUrlEncoded
    @POST("api/node/media-settings/get")
    suspend fun getMediaSettings(): com.worldmates.messenger.data.model.MediaSettingsResponse

    /** Update user media auto-download settings. */
    @FormUrlEncoded
    @POST("api/node/media-settings/update")
    suspend fun updateMediaSettings(
        @Field("auto_download_photos")    autoDownloadPhotos: String? = null,
        @Field("auto_download_videos")    autoDownloadVideos: String? = null,
        @Field("auto_download_audio")     autoDownloadAudio: String? = null,
        @Field("auto_download_documents") autoDownloadDocuments: String? = null,
        @Field("compress_photos")         compressPhotos: String? = null,
        @Field("compress_videos")         compressVideos: String? = null,
        @Field("backup_enabled")          backupEnabled: String? = null,
        @Field("mark_backup_complete")    markBackupComplete: String? = null
    ): com.worldmates.messenger.data.model.UpdateMediaSettingsResponse

    // ═══════════════════════ MEDIA DOWNLOAD ══════════════════════════════════

    /**
     * Download any media file by its absolute URL.
     * Used by MediaLoadingManager for thumbnails and full media.
     */
    @GET
    suspend fun downloadMedia(@Url url: String): okhttp3.ResponseBody

    // ═══════════════════════ EXPORT CHAT HISTORY ═════════════════════════════

    @FormUrlEncoded
    @POST(Constants.NODE_CHAT_EXPORT)
    suspend fun exportPrivateChat(
        @Field("recipient_id") recipientId: Long,
        @Field("format")       format: String = "json",
        @Field("limit")        limit: Int     = 500
    ): okhttp3.ResponseBody

    // ═══════════════════════ INSTANT VIEW ════════════════════════════════════

    @FormUrlEncoded
    @POST(Constants.NODE_INSTANT_VIEW)
    suspend fun getInstantView(
        @Field("url") url: String
    ): NodeInstantViewResponse

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

/** Response from POST /api/node/auth/login */
data class NodeLoginResponse(
    @SerializedName("api_status")    val apiStatus: Int,
    @SerializedName("access_token")  val accessToken: String? = null,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("expires_at")    val expiresAt: Long? = null,
    @SerializedName("user_id")       val userId: Long? = null,
    @SerializedName("username")      val username: String? = null,
    @SerializedName("avatar")        val avatar: String? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

/** Response for POST /api/node/auth/refresh */
data class TokenRefreshResponse(
    @SerializedName("api_status")    val apiStatus: Int,
    @SerializedName("access_token")  val accessToken: String? = null,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("expires_at")    val expiresAt: Long? = null,
    @SerializedName("user_id")       val userId: Long? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

/** Response for GET /api/node/user/status */
data class NodeUserStatusResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("online")     val online: Boolean  = false,
    @SerializedName("last_seen")  val lastSeen: Long   = 0L,
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

// ─── Scheduled Messages ───────────────────────────────────────────────────────

data class ScheduledMessageItem(
    @SerializedName("id")             val id: Long,
    @SerializedName("chat_id")        val chatId: Long,
    @SerializedName("chat_type")      val chatType: String,
    @SerializedName("text")           val text: String?,
    @SerializedName("media_url")      val mediaUrl: String?,
    @SerializedName("media_type")     val mediaType: String?,
    @SerializedName("scheduled_at")   val scheduledAt: Long,
    @SerializedName("repeat_type")    val repeatType: String,
    @SerializedName("is_pinned")      val isPinned: Boolean,
    @SerializedName("notify_members") val notifyMembers: Boolean,
    @SerializedName("status")         val status: String,
    @SerializedName("created_at")     val createdAt: Long
)

data class NodeScheduledListResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("scheduled")  val scheduled: List<ScheduledMessageItem>? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

data class NodeScheduledItemResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("scheduled")  val scheduled: ScheduledMessageItem? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

// ─── Channel Threads ─────────────────────────────────────────────────────────

data class ThreadAuthor(
    @SerializedName("user_id")  val userId: Long,
    @SerializedName("name")     val name: String,
    @SerializedName("username") val username: String,
    @SerializedName("avatar")   val avatar: String
)

data class ThreadMessage(
    @SerializedName("id")          val id: Long,
    @SerializedName("post_id")     val postId: Long,
    @SerializedName("user_id")     val userId: Long,
    @SerializedName("text")        val text: String,
    @SerializedName("time")        val time: Long,
    @SerializedName("reply_to_id") val replyToId: Long?,
    @SerializedName("author")      val author: ThreadAuthor?
)

data class NodeThreadListResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("messages")   val messages: List<ThreadMessage>? = null,
    @SerializedName("total")      val total: Int = 0,
    @SerializedName("offset")     val offset: Int = 0,
    @SerializedName("limit")      val limit: Int = 50,
    @SerializedName("error_message") val errorMessage: String? = null
)

data class NodeThreadMessageResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("message")    val message: ThreadMessage? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

data class NodeThreadCountResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("count")      val count: Int = 0,
    @SerializedName("post_id")    val postId: Long = 0,
    @SerializedName("error_message") val errorMessage: String? = null
)

data class NodeThreadBatchCountsResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("counts")     val counts: Map<String, Int>? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

// ─── Shared Chat Folders ──────────────────────────────────────────────────────

data class FolderChatItem(
    @SerializedName("chat_type") val chatType: String,
    @SerializedName("chat_id")   val chatId: Long,
    @SerializedName("added_at")  val addedAt: Long
)

data class ServerFolder(
    @SerializedName("id")           val id: Long,
    @SerializedName("name")         val name: String,
    @SerializedName("emoji")        val emoji: String,
    @SerializedName("color")        val color: String,
    @SerializedName("position")     val position: Int,
    @SerializedName("is_shared")    val isShared: Boolean,
    @SerializedName("share_code")   val shareCode: String?,
    @SerializedName("member_count") val memberCount: Int,
    @SerializedName("chats")        val chats: List<FolderChatItem>?,
    @SerializedName("created_at")   val createdAt: Long
)

data class NodeFolderListResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("folders")    val folders: List<ServerFolder>? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

data class NodeFolderItemResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("folder")     val folder: ServerFolder? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

data class NodeFolderShareResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("share_code") val shareCode: String? = null,
    @SerializedName("share_url")  val shareUrl: String? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

data class NodeInstantViewResponse(
    @SerializedName("api_status")       val apiStatus: Int = 0,
    @SerializedName("url")              val url: String? = null,
    @SerializedName("title")            val title: String? = null,
    @SerializedName("description")      val description: String? = null,
    @SerializedName("site_name")        val siteName: String? = null,
    @SerializedName("image")            val image: String? = null,
    @SerializedName("content_html")     val contentHtml: String? = null,
    @SerializedName("reading_time_min") val readingTimeMin: Int = 1,
    @SerializedName("error_message")    val errorMessage: String? = null
)

// ─── Saved Messages ────────────────────────────────────────────────────────────

/** A single saved-message entry as returned by the server. */
data class ServerSavedItem(
    @SerializedName("id")            val id: Long,
    @SerializedName("message_id")    val messageId: Long,
    @SerializedName("chat_type")     val chatType: String,
    @SerializedName("chat_id")       val chatId: Long,
    @SerializedName("chat_name")     val chatName: String,
    @SerializedName("sender_name")   val senderName: String,
    @SerializedName("text")          val text: String?,
    @SerializedName("media_url")     val mediaUrl: String?,
    @SerializedName("media_type")    val mediaType: String?,
    @SerializedName("saved_at")      val savedAt: Long,
    @SerializedName("original_time") val originalTime: Long
)

data class NodeSavedMessageResponse(
    @SerializedName("api_status")    val apiStatus: Int,
    @SerializedName("saved")         val saved: ServerSavedItem? = null,
    @SerializedName("created")       val created: Boolean? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

data class NodeSavedListResponse(
    @SerializedName("api_status")    val apiStatus: Int,
    @SerializedName("saved")         val saved: List<ServerSavedItem>? = null,
    @SerializedName("total")         val total: Int = 0,
    @SerializedName("error_message") val errorMessage: String? = null
)

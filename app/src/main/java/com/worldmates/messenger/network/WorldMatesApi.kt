package com.worldmates.messenger.network

import com.google.gson.annotations.SerializedName
import com.worldmates.messenger.data.Constants
import com.worldmates.messenger.data.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*

interface WorldMatesApi {

    // ==================== AUTHENTICATION ====================

    @FormUrlEncoded
    @POST(Constants.AUTH_ENDPOINT)
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String,
        @Field("device_type") deviceType: String = "phone",
        @Field("android_m_device_id") deviceId: String? = null
    ): AuthResponse

    @FormUrlEncoded
    @POST("/api/phone/register_user.php?type=user_registration")
    suspend fun register(
        @Field("username") username: String,
        @Field("email") email: String? = null,
        @Field("phone_number") phoneNumber: String? = null,
        @Field("password") password: String,
        @Field("confirm_password") confirmPassword: String,
        @Field("s") sessionId: String, // КРИТИЧНО: обов'язковий параметр для WoWonder API
        @Field("device_type") deviceType: String = "phone",
        @Field("gender") gender: String = "male",
        @Field("android_m_device_id") deviceId: String? = null
    ): AuthResponse

    // ==================== VERIFICATION ====================

    @FormUrlEncoded
    @POST("/xhr/index.php?f=register_with_verification")
    suspend fun registerWithVerification(
        @Field("username") username: String,
        @Field("password") password: String,
        @Field("confirm_password") confirmPassword: String,
        @Field("verification_type") verificationType: String, // "email" or "phone"
        @Field("email") email: String? = null,
        @Field("phone_number") phoneNumber: String? = null,
        @Field("gender") gender: String = "male"
    ): RegisterVerificationResponse

    @FormUrlEncoded
    @POST("/api/v2/?type=send_verification_code")
    suspend fun sendVerificationCode(
        @Field("verification_type") verificationType: String,
        @Field("contact_info") contactInfo: String,
        @Field("username") username: String?
    ): SendCodeResponse

    @FormUrlEncoded
    @POST("/api/v2/?type=verify_code")
    suspend fun verifyCode(
        @Field("verification_type") verificationType: String,
        @Field("contact_info") contactInfo: String,
        @Field("code") code: String,
        @Field("username") username: String? = null,
        @Field("user_id") userId: Long? = null
    ): VerifyCodeResponse

    @FormUrlEncoded
    @POST("/api/v2/?type=send_verification_code")
    suspend fun resendVerificationCode(
        @Field("verification_type") verificationType: String,
        @Field("contact_info") contactInfo: String,
        @Field("username") username: String?
    ): SendCodeResponse

    @FormUrlEncoded
    @POST("/api/v2/sync_session.php")
    suspend fun syncSession(
        @Query("access_token") accessToken: String,
        @Field("user_id") userId: Long,
        @Field("platform") platform: String = "phone"
    ): SyncSessionResponse

    // ==================== CHATS ====================

    @FormUrlEncoded
    @POST(Constants.GET_CHATS_ENDPOINT)
    suspend fun getChats(
        @Query("access_token") accessToken: String,
        @Field("user_limit") limit: Int = 50,
        @Field("data_type") dataType: String = "all", // "all", "users", "groups", "channels"
        @Field("SetOnline") setOnline: Int = 1,
        @Field("offset") offset: Int = 0
    ): ChatListResponse

    @FormUrlEncoded
    @POST("?type=search")
    suspend fun searchUsers(
        @Query("access_token") accessToken: String,
        @Field("query") query: String,
        @Field("limit") limit: Int = 30,
        @Field("offset") offset: Int = 0
    ): UserSearchResponse

    @FormUrlEncoded
    @POST(Constants.GET_MESSAGES_ENDPOINT)
    suspend fun getMessages(
        @Query("access_token") accessToken: String,
        @Field("recipient_id") recipientId: Long,
        @Field("limit") limit: Int = 30,
        @Field("before_message_id") beforeMessageId: Long = 0
    ): MessageListResponse

    // 📦 CLOUD BACKUP: Получение сообщений с расширенными параметрами
    @FormUrlEncoded
    @POST(Constants.GET_MESSAGES_ENDPOINT)
    suspend fun getMessagesWithOptions(
        @Query("access_token") accessToken: String,
        @Field("recipient_id") recipientId: Long,
        @Field("limit") limit: Int = 30,
        @Field("before_message_id") beforeMessageId: Long = 0,
        @Field("full_history") fullHistory: String = "false", // "true" для загрузки всей истории
        @Field("count_only") countOnly: String = "false" // "true" для получения только количества
    ): MessageListResponse

    // 📦 CLOUD BACKUP: Подсчет количества сообщений
    @FormUrlEncoded
    @POST(Constants.GET_MESSAGES_ENDPOINT)
    suspend fun getMessageCount(
        @Query("access_token") accessToken: String,
        @Field("recipient_id") recipientId: Long,
        @Field("count_only") countOnly: String = "true"
    ): MessageCountResponse

    // 📡 ADAPTIVE TRANSPORT: Легкое получение сообщений (text-only)
    @FormUrlEncoded
    @POST(Constants.GET_MESSAGES_ENDPOINT)
    suspend fun getMessagesLightweight(
        @Query("access_token") accessToken: String,
        @Field("recipient_id") recipientId: Long,
        @Field("limit") limit: Int = 30,
        @Field("after_message_id") afterMessageId: Long = 0, // Получить сообщения ПОСЛЕ этого ID
        @Field("load_mode") loadMode: String = "text_only" // "text_only", "with_thumbnails", "full"
    ): MessageListResponse

    // 📡 ADAPTIVE TRANSPORT: Получить превью медиа (thumbnail)
    @GET
    suspend fun getMediaThumbnail(
        @Url thumbnailUrl: String
    ): okhttp3.ResponseBody

    // 📡 ADAPTIVE TRANSPORT: Получить полное медиа
    @GET
    suspend fun getFullMedia(
        @Url mediaUrl: String
    ): okhttp3.ResponseBody

    // 📦 CLOUD BACKUP: Получение настроек автозагрузки медиа
    @FormUrlEncoded
    @POST("/api/v2/endpoints/get_media_settings.php")
    suspend fun getMediaSettings(
        @Query("access_token") accessToken: String
    ): MediaSettingsResponse

    // 📦 CLOUD BACKUP: Обновление настроек автозагрузки медиа
    @FormUrlEncoded
    @POST("/api/v2/endpoints/update_media_settings.php")
    suspend fun updateMediaSettings(
        @Query("access_token") accessToken: String,
        @Field("auto_download_photos") autoDownloadPhotos: String? = null,
        @Field("auto_download_videos") autoDownloadVideos: String? = null,
        @Field("auto_download_audio") autoDownloadAudio: String? = null,
        @Field("auto_download_documents") autoDownloadDocuments: String? = null,
        @Field("compress_photos") compressPhotos: String? = null,
        @Field("compress_videos") compressVideos: String? = null,
        @Field("backup_enabled") backupEnabled: String? = null,
        @Field("mark_backup_complete") markBackupComplete: String? = null
    ): UpdateMediaSettingsResponse

    // 📦 CLOUD BACKUP v2: Получение расширенных настроек облачного бэкапа
    @GET("/api/v2/endpoints/get_cloud_backup_settings.php")
    suspend fun getCloudBackupSettings(
        @Query("access_token") accessToken: String
    ): CloudBackupSettingsResponse

    // 📦 CLOUD BACKUP v2: Обновление расширенных настроек облачного бэкапа
    @FormUrlEncoded
    @POST("/api/v2/endpoints/update_cloud_backup_settings.php")
    suspend fun updateCloudBackupSettings(
        @Field("access_token") accessToken: String,
        @Field("mobile_photos") mobilePhotos: String? = null,
        @Field("mobile_videos") mobileVideos: String? = null,
        @Field("mobile_files") mobileFiles: String? = null,
        @Field("mobile_videos_limit") mobileVideosLimit: Int? = null,
        @Field("mobile_files_limit") mobileFilesLimit: Int? = null,
        @Field("wifi_photos") wifiPhotos: String? = null,
        @Field("wifi_videos") wifiVideos: String? = null,
        @Field("wifi_files") wifiFiles: String? = null,
        @Field("wifi_videos_limit") wifiVideosLimit: Int? = null,
        @Field("wifi_files_limit") wifiFilesLimit: Int? = null,
        @Field("roaming_photos") roamingPhotos: String? = null,
        @Field("save_to_gallery_private_chats") saveToGalleryPrivateChats: String? = null,
        @Field("save_to_gallery_groups") saveToGalleryGroups: String? = null,
        @Field("save_to_gallery_channels") saveToGalleryChannels: String? = null,
        @Field("streaming_enabled") streamingEnabled: String? = null,
        @Field("cache_size_limit") cacheSizeLimit: Long? = null,
        @Field("backup_enabled") backupEnabled: String? = null,
        @Field("backup_provider") backupProvider: String? = null,
        @Field("backup_frequency") backupFrequency: String? = null,
        @Field("mark_backup_complete") markBackupComplete: String? = null,
        @Field("proxy_enabled") proxyEnabled: String? = null,
        @Field("proxy_host") proxyHost: String? = null,
        @Field("proxy_port") proxyPort: Int? = null
    ): UpdateCloudBackupSettingsResponse

    // 📊 CLOUD BACKUP: Получение статистики облачного хранилища
    @GET("/api/v2/endpoints/get-backup-statistics.php")
    suspend fun getBackupStatistics(
        @Query("access_token") accessToken: String
    ): BackupStatisticsResponse

    // 📤 CLOUD BACKUP: Експорт всіх даних користувача
    @GET("/api/v2/endpoints/export-user-data.php")
    suspend fun exportUserData(
        @Query("access_token") accessToken: String
    ): ExportDataResponse

    // 📥 CLOUD BACKUP: Імпорт даних користувача з бекапу
    @FormUrlEncoded
    @POST("/api/v2/endpoints/import-user-data.php")
    suspend fun importUserData(
        @Query("access_token") accessToken: String,
        @Field("backup_data") backupData: String
    ): ImportDataResponse

    // 📋 CLOUD BACKUP: Список бекапів на сервері
    @GET("/api/v2/endpoints/list-backups.php")
    suspend fun listBackups(
        @Query("access_token") accessToken: String
    ): ListBackupsResponse

    // ==================== GROUP CHATS (Messenger Groups) ====================
    // Uses /api/v2/group_chat_v2.php - Backend API endpoint with 'type' parameter

    @FormUrlEncoded
    @POST("/api/v2/group_chat_v2.php")
    suspend fun getGroups(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "get_list",
        @Field("limit") limit: Int = 50,
        @Field("offset") offset: Int = 0
    ): GroupListResponse

    @FormUrlEncoded
    @POST("/api/v2/group_chat_v2.php")
    suspend fun getGroupDetails(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "get_by_id",
        @Field("id") groupId: Long
    ): GroupDetailResponse

    @FormUrlEncoded
    @POST("/api/v2/group_chat_v2.php")
    suspend fun createGroup(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "create",
        @Field("group_name") name: String,
        @Field("parts") memberIds: String = "", // Comma-separated user IDs
        @Field("group_type") groupType: String = "group" // "group" or "channel"
    ): CreateGroupResponse?

    @FormUrlEncoded
    @POST("/api/v2/group_chat_v2.php")
    suspend fun updateGroup(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "edit",
        @Field("id") groupId: Long,
        @Field("group_name") name: String
    ): CreateGroupResponse

    @FormUrlEncoded
    @POST("/api/v2/group_chat_v2.php")
    suspend fun deleteGroup(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "delete",
        @Field("id") groupId: Long
    ): CreateGroupResponse

    @FormUrlEncoded
    @POST("/api/v2/group_chat_v2.php")
    suspend fun addGroupMember(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "add_user",
        @Field("id") groupId: Long,
        @Field("parts") userIds: String // Comma-separated user IDs
    ): CreateGroupResponse

    @FormUrlEncoded
    @POST("/api/v2/group_chat_v2.php")
    suspend fun removeGroupMember(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "remove_user",
        @Field("id") groupId: Long,
        @Field("parts") userIds: String // Comma-separated user IDs
    ): CreateGroupResponse

    @FormUrlEncoded
    @POST("/api/v2/group_chat_v2.php")
    suspend fun setGroupMemberRole(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "set_admin",
        @Field("id") groupId: Long,
        @Field("user_id") userId: Long,
        @Field("role") role: String = "admin" // admin, moderator, member
    ): GenericResponse

    @FormUrlEncoded
    @POST("/api/v2/group_chat_v2.php")
    suspend fun leaveGroup(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "leave",
        @Field("id") groupId: Long
    ): CreateGroupResponse

    @FormUrlEncoded
    @POST("/api/v2/group_chat_v2.php")
    suspend fun getGroupMembers(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "get_members",
        @Field("id") groupId: Long,
        @Field("limit") limit: Int = 100,
        @Field("offset") offset: Int = 0
    ): GroupDetailResponse

    @FormUrlEncoded
    @POST("/api/v2/group_chat_v2.php")
    suspend fun getGroupMessages(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "get_messages",
        @Field("id") groupId: Long,
        @Field("topic_id") topicId: Long = 0, // Topic/Subgroup filter
        @Field("limit") limit: Int = 50,
        @Field("before_message_id") beforeMessageId: Long = 0
    ): MessageListResponse

    @FormUrlEncoded
    @POST("/api/v2/group_chat_v2.php")
    suspend fun sendGroupMessage(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "send_message",
        @Field("id") groupId: Long,
        @Field("topic_id") topicId: Long = 0, // Topic/Subgroup for message
        @Field("text") text: String,
        @Field("reply_id") replyToId: Long? = null
    ): MessageResponse

    @Multipart
    @POST("/api/v2/group_chat_v2.php")
    suspend fun uploadGroupAvatar(
        @Query("access_token") accessToken: String,
        @Query("type") type: String = "upload_avatar",
        @Part("id") groupId: RequestBody,
        @Part avatar: MultipartBody.Part
    ): CreateGroupResponse

    // 📌 Pin/Unpin Group Messages (consolidated API)
    @FormUrlEncoded
    @POST("/api/v2/group_chat_v2.php")
    suspend fun pinGroupMessage(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "pin_message",
        @Field("id") groupId: Long,
        @Field("message_id") messageId: Long
    ): GenericResponse

    @FormUrlEncoded
    @POST("/api/v2/group_chat_v2.php")
    suspend fun unpinGroupMessage(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "unpin_message",
        @Field("id") groupId: Long
    ): GenericResponse

    // 🔕 Mute/Unmute Group Notifications (consolidated API)
    @FormUrlEncoded
    @POST("/api/v2/group_chat_v2.php")
    suspend fun muteGroup(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "mute",
        @Field("id") groupId: Long
    ): GenericResponse

    @FormUrlEncoded
    @POST("/api/v2/group_chat_v2.php")
    suspend fun unmuteGroup(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "unmute",
        @Field("id") groupId: Long
    ): GenericResponse

    // 🔍 Search Group Messages (consolidated API)
    @FormUrlEncoded
    @POST("/api/v2/group_chat_v2.php")
    suspend fun searchGroupMessages(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "search_messages",
        @Field("id") groupId: Long,
        @Field("query") query: String,
        @Field("limit") limit: Int = 50,
        @Field("offset") offset: Int = 0
    ): SearchMessagesResponse

    // 📸 Upload Group Avatar - Dedicated Endpoint (Recommended)
    @Multipart
    @POST("/api/v2/endpoints/upload_group_avatar.php")
    suspend fun uploadGroupAvatarDedicated(
        @Part("access_token") accessToken: RequestBody,
        @Part("group_id") groupId: RequestBody,
        @Part avatar: MultipartBody.Part
    ): UploadAvatarResponse

    // 📸 Upload Group Avatar - Legacy (via group_chat_v2)
    @Multipart
    @POST("/api/v2/endpoints/upload_group_avatar.php")
    suspend fun uploadGroupAvatar(
        @Part("access_token") accessToken: RequestBody,
        @Part("group_id") groupId: RequestBody,
        @Part avatar: MultipartBody.Part
    ): UploadAvatarResponse

    // 🔲 Generate Group QR Code (consolidated API)
    @FormUrlEncoded
    @POST("/api/v2/group_chat_v2.php")
    suspend fun generateGroupQr(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "generate_qr",
        @Field("id") groupId: Long
    ): GenerateQrResponse

    // 🔲 Join Group by QR Code (consolidated API)
    @FormUrlEncoded
    @POST("/api/v2/group_chat_v2.php")
    suspend fun joinGroupByQr(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "join_by_qr",
        @Field("qr_code") qrCode: String
    ): JoinGroupResponse

    // 🔓 Join Public Group (consolidated API)
    @FormUrlEncoded
    @POST("/api/v2/group_chat_v2.php")
    suspend fun joinGroup(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "join_group",
        @Field("id") groupId: Long
    ): JoinGroupResponse

    // ==================== GROUP MANAGEMENT (consolidated API) ====================

    // 📝 Get Join Requests
    @FormUrlEncoded
    @POST("/api/v2/group_chat_v2.php")
    suspend fun getGroupJoinRequests(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "get_join_requests",
        @Field("id") groupId: Long
    ): JoinRequestsResponse

    // ✅ Approve Join Request
    @FormUrlEncoded
    @POST("/api/v2/group_chat_v2.php")
    suspend fun approveJoinRequest(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "approve_join_request",
        @Field("request_id") requestId: Long
    ): GenericResponse

    // ❌ Reject Join Request
    @FormUrlEncoded
    @POST("/api/v2/group_chat_v2.php")
    suspend fun rejectJoinRequest(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "reject_join_request",
        @Field("request_id") requestId: Long
    ): GenericResponse

    // ⚙️ Update Group Settings (slow mode, privacy, permissions)
    @FormUrlEncoded
    @POST("/api/v2/group_chat_v2.php")
    suspend fun updateGroupSettings(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "update_settings",
        @Field("id") groupId: Long,
        @Field("is_private") isPrivate: Int? = null,
        @Field("slow_mode_seconds") slowModeSeconds: Int? = null,
        @Field("history_visible") historyVisible: Int? = null,
        @Field("anti_spam_enabled") antiSpamEnabled: Int? = null,
        @Field("max_messages_per_minute") maxMessagesPerMinute: Int? = null,
        @Field("allow_members_send_media") allowMedia: Int? = null,
        @Field("allow_members_send_links") allowLinks: Int? = null,
        @Field("allow_members_send_stickers") allowStickers: Int? = null,
        @Field("allow_members_invite") allowInvite: Int? = null
    ): GenericResponse

    // ⚙️ Get Group Settings
    @FormUrlEncoded
    @POST("/api/v2/group_chat_v2.php")
    suspend fun getGroupSettings(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "get_settings",
        @Field("id") groupId: Long
    ): GroupSettingsResponse

    // 🔒 Update Group Privacy
    @FormUrlEncoded
    @POST("/api/v2/group_chat_v2.php")
    suspend fun updateGroupPrivacy(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "update_privacy",
        @Field("id") groupId: Long,
        @Field("is_private") isPrivate: Int
    ): GenericResponse

    // 📊 Get Group Statistics
    @FormUrlEncoded
    @POST("/api/v2/group_chat_v2.php")
    suspend fun getGroupStatistics(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "get_statistics",
        @Field("id") groupId: Long
    ): GroupStatisticsResponse

    // ==================== GROUP CUSTOMIZATION (theme API) ====================

    // 🎨 Get Group Theme Customization
    @FormUrlEncoded
    @POST("/api/v2/endpoints/group_customization.php")
    suspend fun getGroupCustomization(
        @Field("access_token") accessToken: String,
        @Field("type") type: String = "get_customization",
        @Field("group_id") groupId: Long
    ): GroupCustomizationResponse

    // 🎨 Update Group Theme Customization
    @FormUrlEncoded
    @POST("/api/v2/endpoints/group_customization.php")
    suspend fun updateGroupCustomization(
        @Field("access_token") accessToken: String,
        @Field("type") type: String = "update_customization",
        @Field("group_id") groupId: Long,
        @Field("bubble_style") bubbleStyle: String? = null,
        @Field("preset_background") presetBackground: String? = null,
        @Field("accent_color") accentColor: String? = null,
        @Field("enabled_by_admin") enabledByAdmin: String? = null
    ): GroupCustomizationResponse

    // 🎨 Reset Group Theme to Defaults
    @FormUrlEncoded
    @POST("/api/v2/endpoints/group_customization.php")
    suspend fun resetGroupCustomization(
        @Field("access_token") accessToken: String,
        @Field("type") type: String = "reset_customization",
        @Field("group_id") groupId: Long
    ): GenericResponse

    // ==================== SCHEDULED POSTS (consolidated API) ====================

    // 📅 Get Scheduled Posts
    @FormUrlEncoded
    @POST("/api/v2/group_chat_v2.php")
    suspend fun getScheduledPosts(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "get_scheduled_posts",
        @Field("id") groupId: Long
    ): ScheduledPostsResponse

    // ➕ Create Scheduled Post
    @FormUrlEncoded
    @POST("/api/v2/group_chat_v2.php")
    suspend fun createScheduledPost(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "create_scheduled_post",
        @Field("id") groupId: Long,
        @Field("text") text: String,
        @Field("scheduled_time") scheduledTime: Long,
        @Field("media_url") mediaUrl: String? = null,
        @Field("repeat_type") repeatType: String = "none",
        @Field("is_pinned") isPinned: Int = 0,
        @Field("notify_members") notifyMembers: Int = 1
    ): ScheduledPostsResponse

    // 🗑️ Delete Scheduled Post
    @FormUrlEncoded
    @POST("/api/v2/group_chat_v2.php")
    suspend fun deleteScheduledPost(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "delete_scheduled_post",
        @Field("id") groupId: Long,
        @Field("post_id") postId: Long
    ): GenericResponse

    // 📤 Publish Scheduled Post Now
    @FormUrlEncoded
    @POST("/api/v2/group_chat_v2.php")
    suspend fun publishScheduledPost(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "publish_scheduled_post",
        @Field("id") groupId: Long,
        @Field("post_id") postId: Long
    ): GenericResponse

    // ==================== SUBGROUPS / TOPICS (consolidated API) ====================

    // 📱 Get Subgroups (Topics)
    @FormUrlEncoded
    @POST("/api/v2/group_chat_v2.php")
    suspend fun getSubgroups(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "get_subgroups",
        @Field("id") groupId: Long
    ): SubgroupsResponse

    // ➕ Create Subgroup (Topic)
    @FormUrlEncoded
    @POST("/api/v2/group_chat_v2.php")
    suspend fun createSubgroup(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "create_subgroup",
        @Field("id") groupId: Long,
        @Field("name") name: String,
        @Field("description") description: String? = null,
        @Field("color") color: String = "#2196F3",
        @Field("is_private") isPrivate: Int = 0
    ): SubgroupsResponse

    // 🗑️ Delete Subgroup (Topic)
    @FormUrlEncoded
    @POST("/api/v2/group_chat_v2.php")
    suspend fun deleteSubgroup(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "delete_subgroup",
        @Field("id") groupId: Long,
        @Field("subgroup_id") subgroupId: Long
    ): GenericResponse

    // 🔗 Generate Invite Link
    @FormUrlEncoded
    @POST("/api/v2/group_chat_v2.php")
    suspend fun generateGroupInviteLink(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "generate_invite_link",
        @Field("id") groupId: Long
    ): InviteLinkResponse

    // ==================== MESSAGES ====================

    @FormUrlEncoded
    @POST("?type=send-message")
    suspend fun sendMessage(
        @Query("access_token") accessToken: String,
        @Field("user_id") recipientId: Long,
        @Field("text") text: String,
        @Field("message_hash_id") messageHashId: String,
        @Field("reply_id") replyToId: Long? = null
    ): MessageResponse

    // Отправка сообщения с медиа-файлом
    @Multipart
    @POST("?type=send-message")
    suspend fun sendMessageWithMedia(
        @Query("access_token") accessToken: String,
        @Part("user_id") recipientId: RequestBody,
        @Part("text") text: RequestBody,
        @Part("message_hash_id") messageHashId: RequestBody,
        @Part file: MultipartBody.Part
    ): MessageResponse

    @FormUrlEncoded
    @POST("?type=delete_message")
    suspend fun deleteMessage(
        @Query("access_token") accessToken: String,
        @Field("message_id") messageId: Long
    ): MessageResponse

    @FormUrlEncoded
    @POST("?type=edit_message")
    suspend fun editMessage(
        @Query("access_token") accessToken: String,
        @Field("message_id") messageId: Long,
        @Field("text") newText: String
    ): MessageResponse

    // ==================== REACTIONS ====================

    @FormUrlEncoded
    @POST("?type=add_message_reaction")
    suspend fun addReaction(
        @Query("access_token") accessToken: String,
        @Field("message_id") messageId: Long,
        @Field("reaction") reaction: String
    ): ReactionResponse

    @FormUrlEncoded
    @POST("?type=remove_message_reaction")
    suspend fun removeReaction(
        @Query("access_token") accessToken: String,
        @Field("message_id") messageId: Long,
        @Field("reaction") reaction: String
    ): ReactionResponse

    @GET("?type=get_message_reactions")
    suspend fun getReactions(
        @Query("access_token") accessToken: String,
        @Query("message_id") messageId: Long
    ): ReactionsListResponse

    // ==================== CUSTOM EMOJIS ====================

    @GET("?type=get_emoji_packs")
    suspend fun getEmojiPacks(
        @Query("access_token") accessToken: String
    ): EmojiPacksResponse

    @GET("?type=get_emoji_pack")
    suspend fun getEmojiPack(
        @Query("access_token") accessToken: String,
        @Query("pack_id") packId: Long
    ): EmojiPackDetailResponse

    @FormUrlEncoded
    @POST("?type=activate_emoji_pack")
    suspend fun activateEmojiPack(
        @Query("access_token") accessToken: String,
        @Field("pack_id") packId: Long
    ): EmojiPackDetailResponse

    @FormUrlEncoded
    @POST("?type=deactivate_emoji_pack")
    suspend fun deactivateEmojiPack(
        @Query("access_token") accessToken: String,
        @Field("pack_id") packId: Long
    ): EmojiPackDetailResponse

    // ==================== STICKERS ====================

    @GET("?type=get_sticker_packs")
    suspend fun getStickerPacks(
        @Query("access_token") accessToken: String,
        @Query("server_key") serverKey: String
    ): StickerPacksResponse

    @GET("?type=get_sticker_pack")
    suspend fun getStickerPack(
        @Query("access_token") accessToken: String,
        @Query("server_key") serverKey: String,
        @Query("pack_id") packId: Long
    ): StickerPackDetailResponse

    @FormUrlEncoded
    @POST("?type=activate_sticker_pack")
    suspend fun activateStickerPack(
        @Query("access_token") accessToken: String,
        @Field("pack_id") packId: Long
    ): StickerPackDetailResponse

    @FormUrlEncoded
    @POST("?type=deactivate_sticker_pack")
    suspend fun deactivateStickerPack(
        @Query("access_token") accessToken: String,
        @Field("pack_id") packId: Long
    ): StickerPackDetailResponse

    @FormUrlEncoded
    @POST("?type=send_sticker")
    suspend fun sendSticker(
        @Query("access_token") accessToken: String,
        @Field("user_id") recipientId: Long? = null,
        @Field("group_id") groupId: Long? = null,
        @Field("sticker_id") stickerId: Long,
        @Field("message_hash_id") messageHashId: String
    ): MessageResponse

    // ==================== MEDIA UPLOAD ====================

    // XHR Upload endpoints (используются для загрузки файлов на сервер)
    // Используем абсолютный путь (с /) чтобы обойти api/v2/
    // Параметр f= указывает какой обработчик использовать
    @Multipart
    @POST("/xhr/upload_image.php")
    suspend fun uploadImage(
        @Query("access_token") accessToken: String,
        @Query("server_key") serverKey: String,
        @Query("f") f: String = "upload_image",
        @Part image: MultipartBody.Part
    ): XhrUploadResponse

    @Multipart
    @POST("/xhr/upload_video.php")
    suspend fun uploadVideo(
        @Query("access_token") accessToken: String,
        @Query("server_key") serverKey: String,
        @Query("f") f: String = "upload_video",
        @Part video: MultipartBody.Part
    ): XhrUploadResponse

    @Multipart
    @POST("/xhr/upload_audio.php")
    suspend fun uploadAudio(
        @Query("access_token") accessToken: String,
        @Query("server_key") serverKey: String,
        @Query("f") f: String = "upload_audio",
        @Part audio: MultipartBody.Part
    ): XhrUploadResponse

    @Multipart
    @POST("/xhr/upload_file.php")
    suspend fun uploadFile(
        @Query("access_token") accessToken: String,
        @Query("server_key") serverKey: String,
        @Query("f") f: String = "upload_file",
        @Part file: MultipartBody.Part
    ): XhrUploadResponse

    @Multipart
    @POST("?type=upload_media")
    suspend fun uploadMedia(
        @Query("access_token") accessToken: String,
        @Part("media_type") mediaType: RequestBody, // "image", "video", "audio", "voice", "file"
        @Part("recipient_id") recipientId: RequestBody? = null,
        @Part("group_id") groupId: RequestBody? = null,
        @Part file: MultipartBody.Part
    ): MediaUploadResponse

    // 📸 Upload Channel Avatar - direct path to avoid router redirection issues
    @Multipart
    @POST("/api/v2/endpoints/upload_channel_avatar.php")
    suspend fun uploadChannelAvatar(
        @Part("access_token") accessToken: RequestBody,
        @Part("channel_id") channelId: RequestBody,
        @Part avatar: MultipartBody.Part
    ): MediaUploadResponse

    // 📸 Upload User Avatar (DO NOT MODIFY - works correctly)
    @Multipart
    @POST("?type=upload_user_avatar")
    suspend fun uploadUserAvatar(
        @Query("access_token") accessToken: String,
        @Part file: MultipartBody.Part
    ): MediaUploadResponse

    // ==================== TYPING & PRESENCE ====================

    @FormUrlEncoded
    @POST("?type=set_typing")
    suspend fun setTyping(
        @Query("access_token") accessToken: String,
        @Field("recipient_id") recipientId: Long,
        @Field("is_typing") isTyping: Int = 1
    ): MessageResponse

    @FormUrlEncoded
    @POST("?type=set_last_seen")
    suspend fun setLastSeen(
        @Query("access_token") accessToken: String,
        @Field("recipient_id") recipientId: Long
    ): MessageResponse

    // ==================== VOICE/VIDEO CALLS ====================

    @GET("/api/ice-servers/{userId}")
    suspend fun getIceServers(
        @Path("userId") userId: Int
    ): IceServersResponse

    @FormUrlEncoded
    @POST("?type=initiate_call")
    suspend fun initiateCall(
        @Query("access_token") accessToken: String,
        @Field("recipient_id") recipientId: Long,
        @Field("call_type") callType: String, // "voice", "video"
        @Field("rtc_offer") rtcOffer: String? = null
    ): CallResponse

    @FormUrlEncoded
    @POST("?type=accept_call")
    suspend fun acceptCall(
        @Query("access_token") accessToken: String,
        @Field("call_id") callId: String,
        @Field("rtc_answer") rtcAnswer: String? = null
    ): CallResponse

    @FormUrlEncoded
    @POST("?type=reject_call")
    suspend fun rejectCall(
        @Query("access_token") accessToken: String,
        @Field("call_id") callId: String
    ): CallResponse

    @FormUrlEncoded
    @POST("?type=end_call")
    suspend fun endCall(
        @Query("access_token") accessToken: String,
        @Field("call_id") callId: String,
        @Field("duration") duration: Long
    ): CallResponse

    // ==================== USER PROFILE & SETTINGS ====================

    @FormUrlEncoded
    @POST("?type=get-user-data")
    suspend fun getUserData(
        @Query("access_token") accessToken: String,
        @Field("user_id") userId: Long? = null, // Если null, вернет данные текущего пользователя
        @Field("fetch") fetch: String = "user_data" // Возможные значения: user_data, followers, following, liked_pages, joined_groups, family (через запятую)
    ): GetUserDataResponse

    @FormUrlEncoded
    @POST("?type=update-user-data")
    suspend fun updateUserData(
        @Query("access_token") accessToken: String,
        @Field("first_name") firstName: String? = null,
        @Field("last_name") lastName: String? = null,
        @Field("about") about: String? = null,
        @Field("birthday") birthday: String? = null,
        @Field("gender") gender: String? = null,
        @Field("phone_number") phoneNumber: String? = null,
        @Field("website") website: String? = null,
        @Field("working") working: String? = null,
        @Field("address") address: String? = null,
        @Field("country_id") countryId: String? = null,
        @Field("city") city: String? = null,
        @Field("school") school: String? = null,
        @Field("language") language: String? = null
    ): UpdateUserDataResponse

    @FormUrlEncoded
    @POST("?type=update-privacy-settings")
    suspend fun updatePrivacySettings(
        @Query("access_token") accessToken: String,
        @Field("follow_privacy") followPrivacy: String? = null,
        @Field("friend_privacy") friendPrivacy: String? = null,
        @Field("post_privacy") postPrivacy: String? = null,
        @Field("message_privacy") messagePrivacy: String? = null,
        @Field("confirm_followers") confirmFollowers: String? = null,
        @Field("show_activities_privacy") showActivitiesPrivacy: String? = null,
        @Field("birth_privacy") birthPrivacy: String? = null,
        @Field("visit_privacy") visitPrivacy: String? = null
    ): UpdateUserDataResponse

    @FormUrlEncoded
    @POST("?type=update-notification-settings")
    suspend fun updateNotificationSettings(
        @Query("access_token") accessToken: String,
        @Field("email_notification") emailNotification: Int? = null,
        @Field("e_liked") eLiked: Int? = null,
        @Field("e_wondered") eWondered: Int? = null,
        @Field("e_shared") eShared: Int? = null,
        @Field("e_followed") eFollowed: Int? = null,
        @Field("e_commented") eCommented: Int? = null,
        @Field("e_visited") eVisited: Int? = null,
        @Field("e_liked_page") eLikedPage: Int? = null,
        @Field("e_mentioned") eMentioned: Int? = null,
        @Field("e_joined_group") eJoinedGroup: Int? = null,
        @Field("e_accepted") eAccepted: Int? = null,
        @Field("e_profile_wall_post") eProfileWallPost: Int? = null
    ): UpdateUserDataResponse

    @Multipart
    @POST("?type=update-profile-picture")
    suspend fun updateProfilePicture(
        @Query("access_token") accessToken: String,
        @Part avatar: MultipartBody.Part
    ): MediaUploadResponse

    @Multipart
    @POST("?type=update-cover-picture")
    suspend fun updateCoverPicture(
        @Query("access_token") accessToken: String,
        @Part cover: MultipartBody.Part
    ): MediaUploadResponse

    @FormUrlEncoded
    @POST("?type=get-my-groups")
    suspend fun getMyGroups(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "joined_groups", // Возможные значения: my_groups, joined_groups, category
        @Field("user_id") userId: Long? = null, // Требуется для joined_groups
        @Field("limit") limit: Int = 50,
        @Field("offset") offset: Int = 0
    ): GroupListResponse

    // ==================== CHAT HISTORY ====================

    /**
     * Очистити історію приватного чату
     */
    @FormUrlEncoded
    @POST("?type=delete_chat")
    suspend fun clearChatHistory(
        @Query("access_token") accessToken: String,
        @Field("user_id") userId: Long
    ): GenericResponse

    /**
     * Очистити історію групового чату
     */
    @FormUrlEncoded
    @POST("?type=delete_group_chat")
    suspend fun clearGroupChatHistory(
        @Query("access_token") accessToken: String,
        @Field("group_id") groupId: Long
    ): GenericResponse

    // ==================== USER BLOCKING ====================

    /**
     * Заблокировать пользователя
     */
    @FormUrlEncoded
    @POST("/api/v2/endpoints/block-user.php")
    suspend fun blockUser(
        @Query("access_token") accessToken: String,
        @Field("user_id") userId: Long,
        @Field("block_action") blockAction: String = "block"
    ): BlockActionResponse

    /**
     * Разблокировать пользователя
     */
    @FormUrlEncoded
    @POST("/api/v2/endpoints/unblock-user.php")
    suspend fun unblockUser(
        @Query("access_token") accessToken: String,
        @Field("user_id") userId: Long
    ): BlockActionResponse

    /**
     * Получить список заблокированных пользователей
     */
    @GET("/api/v2/endpoints/get-blocked-users.php")
    suspend fun getBlockedUsers(
        @Query("access_token") accessToken: String
    ): GetBlockedUsersResponse

    /**
     * Проверить статус блокировки с пользователем
     */
    @FormUrlEncoded
    @POST("/api/v2/endpoints/check-is-blocked.php")
    suspend fun checkIsBlocked(
        @Query("access_token") accessToken: String,
        @Field("user_id") userId: Long
    ): CheckBlockStatusResponse

    // ==================== CHANNELS ====================
    // Uses /api/v2/channels.php - Channel-specific API endpoint

    @FormUrlEncoded
    @POST("/api/v2/channels.php")
    suspend fun getChannels(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "get_list", // "get_list", "get_subscribed", "search"
        @Field("limit") limit: Int = 50,
        @Field("offset") offset: Int = 0,
        @Field("query") query: String? = null
    ): ChannelListResponse

    @FormUrlEncoded
    @POST("/api/v2/channels.php")
    suspend fun getChannelDetails(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "get_by_id",
        @Field("channel_id") channelId: Long
    ): ChannelDetailResponse

    @FormUrlEncoded
    @POST("/api/v2/channels.php")
    suspend fun createChannel(
        @Query("access_token") accessToken: String,
        @Field("action") action: String = "create_channel",
        @Field("name") name: String,
        @Field("username") username: String? = null,
        @Field("description") description: String? = null,
        @Field("avatar_url") avatarUrl: String? = null,
        @Field("is_private") isPrivate: Int = 0,
        @Field("category") category: String? = null
    ): CreateChannelResponse

    @FormUrlEncoded
    @POST("/api/v2/channels.php")
    suspend fun updateChannel(
        @Query("access_token") accessToken: String,
        @Field("action") action: String = "update_channel",
        @Field("channel_id") channelId: Long,
        @Field("name") name: String? = null,
        @Field("description") description: String? = null,
        @Field("username") username: String? = null,
        @Field("category") category: String? = null
    ): CreateChannelResponse

    @FormUrlEncoded
    @POST("/api/v2/channels.php")
    suspend fun deleteChannel(
        @Query("access_token") accessToken: String,
        @Field("action") action: String = "delete_channel",
        @Field("channel_id") channelId: Long
    ): CreateChannelResponse

    @FormUrlEncoded
    @POST("/api/v2/channels.php")
    suspend fun subscribeChannel(
        @Query("access_token") accessToken: String,
        @Field("action") action: String = "subscribe_channel",
        @Field("channel_id") channelId: Long
    ): CreateChannelResponse

    @FormUrlEncoded
    @POST("/api/v2/channels.php")
    suspend fun unsubscribeChannel(
        @Query("access_token") accessToken: String,
        @Field("action") action: String = "unsubscribe_channel",
        @Field("channel_id") channelId: Long
    ): CreateChannelResponse

    @FormUrlEncoded
    @POST("/api/v2/channels.php")
    suspend fun addChannelMember(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "add_channel_member",
        @Field("channel_id") channelId: Long,
        @Field("user_id") userId: Long
    ): CreateChannelResponse

    // Channel Posts
    @FormUrlEncoded
    @POST("/api/v2/channels.php")
    suspend fun getChannelPosts(
        @Query("access_token") accessToken: String,
        @Field("action") action: String = "get_channel_posts",
        @Field("channel_id") channelId: Long,
        @Field("limit") limit: Int = 20,
        @Field("before_post_id") beforePostId: Long? = null
    ): ChannelPostsResponse

    @FormUrlEncoded
    @POST("/api/v2/channels.php")
    suspend fun createChannelPost(
        @Query("access_token") accessToken: String,
        @Field("action") action: String = "create_post",
        @Field("channel_id") channelId: Long,
        @Field("text") text: String,
        @Field("media_urls") mediaUrls: String? = null, // JSON array of media
        @Field("disable_comments") disableComments: Int = 0,
        @Field("notify_subscribers") notifySubscribers: Int = 1
    ): CreatePostResponse

    @FormUrlEncoded
    @POST("/api/v2/channels.php")
    suspend fun updateChannelPost(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "update_post",
        @Field("post_id") postId: Long,
        @Field("text") text: String,
        @Field("media_urls") mediaUrls: String? = null
    ): CreatePostResponse

    @FormUrlEncoded
    @POST("/api/v2/channels.php")
    suspend fun deleteChannelPost(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "delete_post",
        @Field("post_id") postId: Long
    ): CreatePostResponse

    @FormUrlEncoded
    @POST("/api/v2/channels.php")
    suspend fun pinChannelPost(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "pin_post",
        @Field("post_id") postId: Long
    ): CreatePostResponse

    @FormUrlEncoded
    @POST("/api/v2/channels.php")
    suspend fun unpinChannelPost(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "unpin_post",
        @Field("post_id") postId: Long
    ): CreatePostResponse

    // Channel Comments
    @FormUrlEncoded
    @POST("/api/v2/channels.php")
    suspend fun getChannelComments(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "get_comments",
        @Field("post_id") postId: Long,
        @Field("limit") limit: Int = 50,
        @Field("offset") offset: Int = 0
    ): ChannelCommentsResponse

    @FormUrlEncoded
    @POST("/api/v2/channels.php")
    suspend fun addChannelComment(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "add_comment",
        @Field("post_id") postId: Long,
        @Field("text") text: String,
        @Field("reply_to_id") replyToId: Long? = null
    ): CreatePostResponse

    @FormUrlEncoded
    @POST("/api/v2/channels.php")
    suspend fun deleteChannelComment(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "delete_comment",
        @Field("comment_id") commentId: Long
    ): CreatePostResponse

    // Channel Reactions
    @FormUrlEncoded
    @POST("/api/v2/channels.php")
    suspend fun addPostReaction(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "add_post_reaction",
        @Field("post_id") postId: Long,
        @Field("reaction") emoji: String
    ): CreatePostResponse

    @FormUrlEncoded
    @POST("/api/v2/channels.php")
    suspend fun removePostReaction(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "remove_post_reaction",
        @Field("post_id") postId: Long,
        @Field("reaction") emoji: String
    ): CreatePostResponse

    @FormUrlEncoded
    @POST("/api/v2/channels.php")
    suspend fun addCommentReaction(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "add_comment_reaction",
        @Field("comment_id") commentId: Long,
        @Field("reaction") reaction: String
    ): CreatePostResponse

    // Register post view
    @FormUrlEncoded
    @POST("/api/v2/channels.php")
    suspend fun registerPostView(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "register_post_view",
        @Field("post_id") postId: Long
    ): CreatePostResponse

    // Channel Admin Management
    @FormUrlEncoded
    @POST("/api/v2/channels.php")
    suspend fun addChannelAdmin(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "add_channel_admin",
        @Field("channel_id") channelId: Long,
        @Field("user_id") userId: Long? = null,
        @Field("user_search") userSearch: String? = null,
        @Field("role") role: String = "admin" // "admin", "moderator"
    ): CreateChannelResponse

    @FormUrlEncoded
    @POST("/api/v2/channels.php")
    suspend fun removeChannelAdmin(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "remove_channel_admin",
        @Field("channel_id") channelId: Long,
        @Field("user_id") userId: Long
    ): CreateChannelResponse

    @FormUrlEncoded
    @POST("/api/v2/channels.php")
    suspend fun updateChannelSettings(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "update_settings",
        @Field("channel_id") channelId: Long,
        @Field("settings_json") settingsJson: String // JSON string of ChannelSettings
    ): CreateChannelResponse

    // Channel Statistics
    @FormUrlEncoded
    @POST("/api/v2/channels.php")
    suspend fun getChannelStatistics(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "get_channel_statistics",
        @Field("channel_id") channelId: Long
    ): ChannelStatisticsResponse

    // Channel Subscribers
    @FormUrlEncoded
    @POST("/api/v2/channels.php")
    suspend fun getChannelSubscribers(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "get_channel_subscribers",
        @Field("channel_id") channelId: Long,
        @Field("limit") limit: Int = 100,
        @Field("offset") offset: Int = 0
    ): ChannelSubscribersResponse

    // 🔲 Generate Channel QR Code
    @FormUrlEncoded
    @POST("/api/v2/endpoints/generate_channel_qr.php")
    suspend fun generateChannelQr(
        @Field("access_token") accessToken: String,
        @Field("channel_id") channelId: Long
    ): GenerateQrResponse

    // 🔲 Subscribe to Channel by QR Code
    @FormUrlEncoded
    @POST("/api/v2/endpoints/subscribe_channel_by_qr.php")
    suspend fun subscribeChannelByQr(
        @Field("access_token") accessToken: String,
        @Field("qr_code") qrCode: String
    ): SubscribeChannelResponse

    // 📡 Mute Channel Notifications
    @FormUrlEncoded
    @POST("/api/v2/endpoints/mute_channel.php")
    suspend fun muteChannel(
        @Field("access_token") accessToken: String,
        @Field("channel_id") channelId: Long
    ): GenericResponse

    // 📡 Unmute Channel Notifications
    @FormUrlEncoded
    @POST("/api/v2/endpoints/unmute_channel.php")
    suspend fun unmuteChannel(
        @Field("access_token") accessToken: String,
        @Field("channel_id") channelId: Long
    ): GenericResponse

    // ==================== APP UPDATES ====================

    @GET("/api/v2/endpoints/check_mobile_update.php")
    suspend fun checkMobileUpdate(
        @Query("platform") platform: String = "android",
        @Query("channel") channel: String = "stable"
    ): AppUpdateResponse

    // ⭐ Rate User (Like/Dislike)
    @FormUrlEncoded
    @POST("/api/v2/?type=rate_user")
    suspend fun rateUser(
        @Query("access_token") accessToken: String,
        @Field("user_id") userId: Long,
        @Field("rating_type") ratingType: String, // "like" or "dislike"
        @Field("comment") comment: String? = null
    ): com.worldmates.messenger.data.model.RateUserResponse

    // ⭐ Get User Rating
    @FormUrlEncoded
    @POST("/api/v2/?type=get_user_rating")
    suspend fun getUserRating(
        @Query("access_token") accessToken: String,
        @Field("user_id") userId: Long,
        @Field("include_details") includeDetails: String = "0" // "1" to include ratings list
    ): com.worldmates.messenger.data.model.GetUserRatingResponse
}

// ==================== RESPONSE MODELS ====================

data class MessageResponse(
    @SerializedName("api_status") private val _apiStatus: Any?, // Может быть String "200" или Int 200
    @SerializedName("api_text") val apiText: String?, // "success" или "failed"
    @SerializedName("api_version") val apiVersion: String?,
    @SerializedName("messages") val messages: List<Message>?,
    @SerializedName("message_data") val messageData: List<Message>?, // API иногда возвращает message_data
    @SerializedName("message") val message: Message?, // Одиночное сообщение (для группових чатів)
    @SerializedName("message_id") val messageId: Long?,
    @SerializedName("errors") val errors: ErrorDetails?,
    @SerializedName("error_code") val errorCode: Int?,
    @SerializedName("error_message") val errorMessage: String?
) {
    // Обработка api_status: old WoWonder API = String "200", v2 API = Int 200
    val apiStatus: Int
        get() = when (_apiStatus) {
            is Number -> _apiStatus.toInt()
            is String -> _apiStatus.toIntOrNull() ?: 400
            else -> 400
        }

    // Универсальный геттер для получения сообщений (из messages, message_data или message)
    val allMessages: List<Message>?
        get() = messages ?: messageData ?: message?.let { listOf(it) }
}

data class ErrorDetails(
    @SerializedName("error_id") val errorId: String?,
    @SerializedName("error_text") val errorText: String?
)

data class CallResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("call_id") val callId: String?,
    @SerializedName("rtc_signal") val rtcSignal: String?,
    @SerializedName("error_code") val errorCode: Int?,
    @SerializedName("error_message") val errorMessage: String?
)

/**
 * ICE Server configuration from backend
 */
data class IceServerConfig(
    @SerializedName("urls") val urls: Any?, // Can be String or List<String>
    @SerializedName("username") val username: String?,
    @SerializedName("credential") val credential: String?
)

data class IceServersResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("iceServers") val iceServers: List<IceServerConfig>?,
    @SerializedName("timestamp") val timestamp: Long?
)

// ==================== REACTION RESPONSES ====================

data class ReactionResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("api_text") val apiText: String?,
    @SerializedName("reaction") val reaction: MessageReaction?,
    @SerializedName("error_code") val errorCode: Int?,
    @SerializedName("error_message") val errorMessage: String?
)

data class ReactionsListResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("api_text") val apiText: String?,
    @SerializedName("reactions") val reactions: List<MessageReaction>?,
    @SerializedName("error_code") val errorCode: Int?,
    @SerializedName("error_message") val errorMessage: String?
)

data class SearchUser(
    @SerializedName("user_id") val userId: Long,
    @SerializedName("username") val username: String,
    @SerializedName("name") val name: String?,
    @SerializedName("avatar") val avatarUrl: String,
    @SerializedName("verified") val verified: Int = 0,
    @SerializedName("lastseen") val lastSeen: Long?,
    @SerializedName("lastseen_status") val lastSeenStatus: String?, // "online", "offline", "recently"
    @SerializedName("about") val about: String?
)

data class UserSearchResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("users") val users: List<SearchUser>?,
    @SerializedName("error_code") val errorCode: Int?,
    @SerializedName("error_message") val errorMessage: String?
)

/**
 * Response from XHR upload endpoints (upload_image.php, upload_video.php, etc.)
 */
data class XhrUploadResponse(
    @SerializedName("status") val status: Int,
    @SerializedName("image") val imageUrl: String?, // URL для изображений
    @SerializedName("image_src") val imageSrc: String?, // Путь к файлу
    @SerializedName("video") val videoUrl: String?, // URL для видео
    @SerializedName("video_src") val videoSrc: String?, // Путь к видео
    @SerializedName("audio") val audioUrl: String?, // URL для аудио
    @SerializedName("audio_src") val audioSrc: String?, // Путь к аудио
    @SerializedName("file") val fileUrl: String?, // URL для файлов
    @SerializedName("file_src") val fileSrc: String?, // Путь к файлу
    @SerializedName("error") val error: String?
)

/**
 * Response для завантаження медіа (аватарки, обкладинки тощо)
 */
data class MediaUploadResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("url") val url: String?,  // URL завантаженого файлу
    @SerializedName("media_id") val mediaId: String?,  // ID медіа (опціонально)
    @SerializedName("message") val message: String?,
    @SerializedName("errors") val errors: ErrorDetails?
) {
    val errorMessage: String?
        get() = errors?.errorText ?: message
}

// ==================== VERIFICATION RESPONSE MODELS ====================

/**
 * Response for registration with verification
 */
data class RegisterVerificationResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("message") val message: String?,
    @SerializedName("user_id") val userId: Long? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("verification_type") val verificationType: String? = null,
    @SerializedName("contact_info") val contactInfo: String? = null,
    @SerializedName("code_length") val codeLength: Int? = null,
    @SerializedName("expires_in") val expiresIn: Int? = null,
    @SerializedName("errors") val errors: String? = null
)

/**
 * Error object for API responses
 */
data class ApiErrorObject(
    @SerializedName("error_id") val errorId: Int? = null,
    @SerializedName("error_text") val errorText: String? = null
)

/**
 * Response for sending verification code
 */
data class SendCodeResponse(
    @SerializedName("status") val status: Int? = null,
    @SerializedName("api_status") val apiStatus: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("code_length") val codeLength: Int? = null,
    @SerializedName("expires_in") val expiresIn: Int? = null,
    @SerializedName("errors") val errorsObject: ApiErrorObject? = null
) {
    val actualStatus: Int
        get() = apiStatus ?: status ?: 400

    val errors: String?
        get() = errorsObject?.errorText
}

/**
 * Response for verifying code
 */
data class VerifyCodeResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("message") val message: String? = null,
    @SerializedName("user_id") val userId: Long? = null,
    @SerializedName("access_token") val accessToken: String? = null,
    @SerializedName("timezone") val timezone: String? = null,
    @SerializedName("errors") val errorsObject: ApiErrorObject? = null
) {
    val errors: String?
        get() = errorsObject?.errorText
}

/**
 * Response for resending verification code
 */
data class ResendCodeResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("message") val message: String?,
    @SerializedName("code_length") val codeLength: Int? = null,
    @SerializedName("expires_in") val expiresIn: Int? = null,
    @SerializedName("errors") val errors: String? = null
)

/**
 * Legacy response for verification (deprecated, use specific responses above)
 */
data class VerificationResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("message") val message: String?,
    @SerializedName("error_code") val errorCode: Int?,
    @SerializedName("error_message") val errorMessage: String?
)

/**
 * Generic response for simple operations (pin/unpin messages, etc.)
 */
data class GenericResponse(
    @SerializedName("api_status") private val _apiStatus: Any?,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error_message") val errorMessage: String? = null
) {
    val apiStatus: Int
        get() = when (_apiStatus) {
            is Number -> _apiStatus.toInt()
            is String -> _apiStatus.toIntOrNull() ?: 400
            else -> 400
        }
}

/**
 * Response for search group messages
 */
data class SearchMessagesResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("messages") val messages: List<Message>? = null,
    @SerializedName("total_count") val totalCount: Int = 0,
    @SerializedName("query") val query: String? = null,
    @SerializedName("message") val message: String?
)

/**
 * Response for upload group avatar
 */
data class UploadAvatarResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error_message") val errorMessage: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    @SerializedName("url") val url: String? = null // Alternative URL field from server
)

/**
 * 🔲 Response for QR code generation
 */
data class GenerateQrResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("message") val message: String?,
    @SerializedName("qr_code") val qrCode: String? = null,
    @SerializedName("join_url") val joinUrl: String? = null,
    @SerializedName("group_id") val groupId: Long? = null,
    @SerializedName("group_name") val groupName: String? = null
)

/**
 * 🔲 Response for joining group by QR
 */
data class JoinGroupResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("message") val message: String?,
    @SerializedName("group") val group: Group? = null
)

/**
 * 📡 Response for subscribing to channel by QR
 */
data class SubscribeChannelResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("message") val message: String?,
    @SerializedName("channel") val channel: com.worldmates.messenger.data.model.Channel? = null
)

/**
 * 📝 Response for group join requests
 */
data class JoinRequestsResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("join_requests") val joinRequests: List<JoinRequestData>? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

data class JoinRequestData(
    @SerializedName("id") val id: Long,
    @SerializedName("group_id") val groupId: Long,
    @SerializedName("user_id") val userId: Long,
    @SerializedName("username") val username: String,
    @SerializedName("user_name") val userName: String? = null,
    @SerializedName("user_avatar") val userAvatar: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("status") val status: String = "pending",
    @SerializedName("created_time") val createdTime: Long
)

/**
 * ⚙️ Response for group settings
 */
data class GroupSettingsResponse(
    @SerializedName("api_status") private val _apiStatus: Any?,
    @SerializedName("settings") val settings: GroupSettingsData? = null,
    @SerializedName("error_message") val errorMessage: String? = null
) {
    val apiStatus: Int
        get() = when (_apiStatus) {
            is Number -> _apiStatus.toInt()
            is String -> _apiStatus.toIntOrNull() ?: 400
            else -> 400
        }
}

data class GroupSettingsData(
    @SerializedName("group_id") val groupId: Long,
    @SerializedName("is_private") val isPrivate: Boolean = false,
    @SerializedName("slow_mode_seconds") val slowModeSeconds: Int = 0,
    @SerializedName("history_visible_for_new_members") val historyVisibleForNewMembers: Boolean = true,
    @SerializedName("anti_spam_enabled") val antiSpamEnabled: Boolean = false,
    @SerializedName("max_messages_per_minute") val maxMessagesPerMinute: Int = 20,
    @SerializedName("allow_members_send_media") val allowMembersSendMedia: Boolean = true,
    @SerializedName("allow_members_send_links") val allowMembersSendLinks: Boolean = true,
    @SerializedName("allow_members_send_stickers") val allowMembersSendStickers: Boolean = true,
    @SerializedName("allow_members_invite") val allowMembersInvite: Boolean = false
)

/**
 * 🎨 Response for group customization/theme
 */
data class GroupCustomizationResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("customization") val customization: GroupCustomizationData? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

data class GroupCustomizationData(
    @SerializedName("group_id") val groupId: Long,
    @SerializedName("bubble_style") val bubbleStyle: String = "STANDARD",
    @SerializedName("preset_background") val presetBackground: String = "ocean",
    @SerializedName("accent_color") val accentColor: String = "#2196F3",
    @SerializedName("enabled_by_admin") val enabledByAdmin: Boolean = true,
    @SerializedName("updated_at") val updatedAt: Long = 0,
    @SerializedName("updated_by") val updatedBy: Long = 0
)

/**
 * 📊 Response for group statistics
 */
data class GroupStatisticsResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("statistics") val statistics: GroupStatisticsData? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

data class GroupStatisticsData(
    @SerializedName("members_count") val membersCount: Int = 0,
    @SerializedName("messages_count") val messagesCount: Int = 0,
    @SerializedName("messages_today") val messagesToday: Int = 0,
    @SerializedName("new_members_week") val newMembersWeek: Int = 0,
    @SerializedName("admins_count") val adminsCount: Int = 0,
    @SerializedName("top_contributors") val topContributors: List<TopContributorData>? = null
)

data class TopContributorData(
    @SerializedName("user_id") val userId: Long,
    @SerializedName("username") val username: String,
    @SerializedName("name") val name: String? = null,
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("messages_count") val messagesCount: Int = 0
)

/**
 * 📅 Response for scheduled posts
 */
data class ScheduledPostsResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("scheduled_posts") val scheduledPosts: List<ScheduledPostData>? = null,
    @SerializedName("post") val post: ScheduledPostData? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

data class ScheduledPostData(
    @SerializedName("id") val id: Long,
    @SerializedName("group_id") val groupId: Long,
    @SerializedName("author_id") val authorId: Long,
    @SerializedName("text") val text: String,
    @SerializedName("media_url") val mediaUrl: String? = null,
    @SerializedName("scheduled_time") val scheduledTime: Long,
    @SerializedName("created_time") val createdTime: Long,
    @SerializedName("status") val status: String = "scheduled",
    @SerializedName("repeat_type") val repeatType: String? = null,
    @SerializedName("is_pinned") val isPinned: Boolean = false,
    @SerializedName("notify_members") val notifyMembers: Boolean = true
)

/**
 * 📱 Response for subgroups (topics)
 */
data class SubgroupsResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("subgroups") val subgroups: List<SubgroupData>? = null,
    @SerializedName("subgroup") val subgroup: SubgroupData? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

data class SubgroupData(
    @SerializedName("id") val id: Long,
    @SerializedName("parent_group_id") val parentGroupId: Long,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("color") val color: String = "#2196F3",
    @SerializedName("is_private") val isPrivate: Boolean = false,
    @SerializedName("members_count") val membersCount: Int = 0,
    @SerializedName("messages_count") val messagesCount: Int = 0,
    @SerializedName("created_by") val createdBy: Long = 0,
    @SerializedName("created_time") val createdTime: Long = 0
)

/**
 * 🔗 Response for invite link generation
 */
data class InviteLinkResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("invite_link") val inviteLink: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)


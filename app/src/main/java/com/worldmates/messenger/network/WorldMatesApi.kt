package com.worldmates.messenger.network

import com.google.gson.annotations.SerializedName
import com.worldmates.messenger.data.model.BackupFileInfo
import com.worldmates.messenger.data.model.Channel
import com.worldmates.messenger.data.model.ExportDataResponse
import com.worldmates.messenger.data.model.ImportDataResponse
import com.worldmates.messenger.data.model.ListBackupsResponse
import com.worldmates.messenger.data.model.EmojiPackDetailResponse
import com.worldmates.messenger.data.model.EmojiPacksResponse
import com.worldmates.messenger.data.model.StickerPackDetailResponse
import com.worldmates.messenger.data.model.StickerPacksResponse
import com.worldmates.messenger.data.model.SyncSessionResponse
import retrofit2.http.*

interface WorldMatesApi {

    // ==================== VERIFICATION ====================

    @FormUrlEncoded
    @POST("/xhr/index.php?f=register_with_verification")
    suspend fun registerWithVerification(
        @Field("username") username: String,
        @Field("password") password: String,
        @Field("confirm_password") confirmPassword: String,
        @Field("verification_type") verificationType: String,
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

    // ==================== QUICK REGISTRATION ====================

    @FormUrlEncoded
    @POST("?type=quick_register")
    suspend fun quickRegister(
        @Field("email") email: String? = null,
        @Field("phone_number") phoneNumber: String? = null
    ): QuickRegisterResponse

    @FormUrlEncoded
    @POST("?type=quick_verify")
    suspend fun quickVerify(
        @Field("email") email: String? = null,
        @Field("phone_number") phoneNumber: String? = null,
        @Field("code") code: String
    ): QuickVerifyResponse

    // ==================== PASSWORD RESET ====================

    @FormUrlEncoded
    @POST("?type=request_password_reset")
    suspend fun requestPasswordReset(
        @Field("email") email: String? = null,
        @Field("phone_number") phoneNumber: String? = null
    ): PasswordResetRequestResponse

    @FormUrlEncoded
    @POST("?type=reset_password")
    suspend fun resetPassword(
        @Field("email") email: String? = null,
        @Field("phone_number") phoneNumber: String? = null,
        @Field("code") code: String,
        @Field("new_password") newPassword: String
    ): PasswordResetResponse

    // ==================== SESSIONS ====================

    @FormUrlEncoded
    @POST("/api/v2/endpoints/sessions.php")
    suspend fun getSessions(
        @Field("access_token") accessToken: String,
        @Field("type") type: String = "get"
    ): SessionsResponse

    @FormUrlEncoded
    @POST("/api/v2/endpoints/sessions.php")
    suspend fun deleteSession(
        @Field("access_token") accessToken: String,
        @Field("type") type: String = "delete",
        @Field("id") sessionId: Long
    ): DeleteSessionResponse

    // ==================== TWO-FACTOR AUTH ====================

    @FormUrlEncoded
    @POST("/api/v2/endpoints/update_two_factor.php")
    suspend fun updateTwoFactor(
        @Field("access_token") accessToken: String,
        @Field("type") type: String? = null,
        @Field("code") code: String? = null,
        @Field("factor_method") factorMethod: String? = null
    ): TwoFactorUpdateResponse

    // ==================== REPORTS ====================

    @FormUrlEncoded
    @POST("/api/v2/endpoints/report_user.php")
    suspend fun reportUser(
        @Field("access_token") accessToken: String,
        @Field("user") userId: Long,
        @Field("text") text: String
    ): ReportResponse

    // ==================== DELETE ACCOUNT ====================

    @FormUrlEncoded
    @POST("/api/v2/endpoints/delete-user.php")
    suspend fun deleteAccount(
        @Field("access_token") accessToken: String,
        @Field("password") password: String
    ): DeleteAccountResponse

    // ==================== CLOUD BACKUP ====================

    @GET("/api/v2/endpoints/export-user-data.php")
    suspend fun exportUserData(
        @Query("access_token") accessToken: String
    ): ExportDataResponse

    @FormUrlEncoded
    @POST("/api/v2/endpoints/import-user-data.php")
    suspend fun importUserData(
        @Query("access_token") accessToken: String,
        @Field("backup_data") backupData: String
    ): ImportDataResponse

    @GET("/api/v2/endpoints/list-backups.php")
    suspend fun listBackups(
        @Query("access_token") accessToken: String
    ): ListBackupsResponse

    // ==================== EMOJI PACKS ====================

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

    // ==================== STICKER PACKS ====================

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
}

// ==================== VERIFICATION RESPONSES ====================

data class ApiErrorObject(
    @SerializedName("error_id") val errorId: Int? = null,
    @SerializedName("error_text") val errorText: String? = null
)

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

data class ResendCodeResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("message") val message: String?,
    @SerializedName("code_length") val codeLength: Int? = null,
    @SerializedName("expires_in") val expiresIn: Int? = null,
    @SerializedName("errors") val errors: String? = null
)

data class VerificationResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("message") val message: String?,
    @SerializedName("error_code") val errorCode: Int?,
    @SerializedName("error_message") val errorMessage: String?
)

// ==================== QUICK REGISTRATION ====================

data class QuickRegisterResponse(
    @SerializedName("api_status") val apiStatus: Int = 0,
    @SerializedName("message") val message: String? = null,
    @SerializedName("user_id") val userId: Long? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

data class QuickVerifyResponse(
    @SerializedName("api_status") val apiStatus: Int = 0,
    @SerializedName("message") val message: String? = null,
    @SerializedName("access_token") val accessToken: String? = null,
    @SerializedName("user_id") val userId: Long? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

// ==================== PASSWORD RESET ====================

data class PasswordResetRequestResponse(
    @SerializedName("api_status") val apiStatus: Int = 0,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

data class PasswordResetResponse(
    @SerializedName("api_status") val apiStatus: Int = 0,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

// ==================== SESSIONS ====================

data class SessionItem(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("session_id") val sessionId: String = "",
    @SerializedName("platform") val platform: String = "",
    @SerializedName("time") val time: Long = 0,
    @SerializedName("ip") val ip: String? = null,
    @SerializedName("device_name") val deviceName: String? = null
)

data class SessionsResponse(
    @SerializedName("api_status") val apiStatus: Int = 0,
    @SerializedName("data") val sessions: List<SessionItem> = emptyList(),
    @SerializedName("error_message") val errorMessage: String? = null
)

data class DeleteSessionResponse(
    @SerializedName("api_status") val apiStatus: Int = 0,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

// ==================== TWO-FACTOR AUTH ====================

data class TwoFactorUpdateResponse(
    @SerializedName("api_status") val apiStatus: Int = 0,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

// ==================== REPORTS ====================

data class ReportResponse(
    @SerializedName("api_status") val apiStatus: Int = 0,
    @SerializedName("code") val code: Int = 0,
    @SerializedName("error_message") val errorMessage: String? = null
)

// ==================== DELETE ACCOUNT ====================

data class DeleteAccountResponse(
    @SerializedName("api_status") val apiStatus: Int = 0,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

// ==================== UPLOAD ====================
// Used by MediaUploader, RetrofitClient, NodeApi

data class XhrUploadResponse(
    @SerializedName("status") val status: Int,
    @SerializedName("image") val imageUrl: String?,
    @SerializedName("image_src") val imageSrc: String?,
    @SerializedName("video") val videoUrl: String?,
    @SerializedName("video_src") val videoSrc: String?,
    @SerializedName("audio") val audioUrl: String?,
    @SerializedName("audio_src") val audioSrc: String?,
    @SerializedName("file") val fileUrl: String?,
    @SerializedName("file_src") val fileSrc: String?,
    @SerializedName("error") val error: String?,
    @SerializedName("length") val length: Int? = null
)

// ==================== CHANNEL SUBSCRIBE ====================
// Used by NodeChannelApi, ChannelsViewModel

data class SubscribeChannelResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("message") val message: String?,
    @SerializedName("channel") val channel: Channel? = null
)

// ==================== POLLS ====================
// Used by NodeChannelApi, ChannelDetailsViewModel

data class PollResponse(
    @SerializedName("api_status") val apiStatus: Int = 0,
    @SerializedName("poll") val poll: Poll? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

// ==================== GROUP CUSTOMIZATION ====================
// Used by NodeGroupApi, GroupThemeManager

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

// ==================== SHARED DATA MODELS ====================
// Used by GroupsViewModel, CreateGroupDialog, InviteMembersUI

data class SearchUser(
    @SerializedName("user_id") val userId: Long,
    @SerializedName("username") val username: String,
    @SerializedName("name") val name: String?,
    @SerializedName("avatar") val avatarUrl: String,
    @SerializedName("verified") val verified: Int = 0,
    @SerializedName("lastseen") val lastSeen: Long?,
    @SerializedName("lastseen_status") val lastSeenStatus: String?,
    @SerializedName("about") val about: String?
)

// Used by PollMessageComponent, MessageBubbleComponents

data class PollOption(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("text") val text: String = "",
    @SerializedName("vote_count") val voteCount: Int = 0,
    @SerializedName("percent") val percent: Int = 0,
    @SerializedName("is_voted") val isVoted: Boolean = false
)

data class Poll(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("question") val question: String = "",
    @SerializedName("poll_type") val pollType: String = "regular",
    @SerializedName("is_anonymous") val isAnonymous: Boolean = true,
    @SerializedName("allows_multiple_answers") val allowsMultipleAnswers: Boolean = false,
    @SerializedName("is_closed") val isClosed: Boolean = false,
    @SerializedName("total_votes") val totalVotes: Int = 0,
    @SerializedName("created_by") val createdBy: Long = 0,
    @SerializedName("options") val options: List<PollOption> = emptyList()
)

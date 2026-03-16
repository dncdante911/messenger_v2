package com.worldmates.messenger.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.*

interface WorldMatesApi {

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
}

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

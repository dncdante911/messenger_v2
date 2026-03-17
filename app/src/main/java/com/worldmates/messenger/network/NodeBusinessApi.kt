package com.worldmates.messenger.network

import com.worldmates.messenger.data.model.*
import retrofit2.http.*

/**
 * Business Mode API — бізнес-профіль, графік роботи, швидкі відповіді, посилання.
 *
 * Auth: автоматично через [NodeRetrofitClient] (заголовок access-token).
 */
interface NodeBusinessApi {

    // ── Profile ───────────────────────────────────────────────────────────────

    @GET("api/node/business/profile")
    suspend fun getMyProfile(): BusinessProfileResponse

    @PUT("api/node/business/profile")
    suspend fun updateProfile(
        @Body body: UpdateBusinessProfileRequest
    ): BusinessProfileResponse

    @DELETE("api/node/business/profile")
    suspend fun deleteProfile(): BusinessActionResponse

    // ── Working hours ─────────────────────────────────────────────────────────

    @GET("api/node/business/hours")
    suspend fun getHours(): BusinessHoursResponse

    @PUT("api/node/business/hours")
    suspend fun updateHours(
        @Body body: UpdateBusinessHoursRequest
    ): BusinessHoursResponse

    // ── Quick replies ─────────────────────────────────────────────────────────

    @GET("api/node/business/quick-replies")
    suspend fun getQuickReplies(): BusinessQuickRepliesResponse

    @POST("api/node/business/quick-replies")
    suspend fun createQuickReply(
        @Body body: CreateQuickReplyRequest
    ): BusinessQuickRepliesResponse

    @PUT("api/node/business/quick-replies/{id}")
    suspend fun updateQuickReply(
        @Path("id") id: Long,
        @Body body: CreateQuickReplyRequest
    ): BusinessQuickRepliesResponse

    @DELETE("api/node/business/quick-replies/{id}")
    suspend fun deleteQuickReply(
        @Path("id") id: Long
    ): BusinessActionResponse

    // ── Business links ────────────────────────────────────────────────────────

    @GET("api/node/business/links")
    suspend fun getLinks(): BusinessLinksResponse

    @POST("api/node/business/links")
    suspend fun createLink(
        @Body body: CreateBusinessLinkRequest
    ): BusinessLinksResponse

    @PUT("api/node/business/links/{id}")
    suspend fun updateLink(
        @Path("id") id: Long,
        @Body body: CreateBusinessLinkRequest
    ): BusinessLinksResponse

    @DELETE("api/node/business/links/{id}")
    suspend fun deleteLink(
        @Path("id") id: Long
    ): BusinessActionResponse

    // ── Public profile (for other users) ─────────────────────────────────────

    @GET("api/node/business/users/{userId}")
    suspend fun getUserBusinessProfile(
        @Path("userId") userId: Long
    ): BusinessProfileResponse
}

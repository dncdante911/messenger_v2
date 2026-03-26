package com.worldmates.messenger.network

import com.worldmates.messenger.data.model.*
import retrofit2.http.*

/**
 * Channel Scheduled Posts API
 *
 * Endpoints on port 449:
 *   GET    /api/node/channels/{id}/posts/scheduled
 *   POST   /api/node/channels/{id}/posts/scheduled
 *   DELETE /api/node/channels/{id}/posts/scheduled/{postId}
 */
interface NodeChannelScheduledApi {

    @GET("api/node/channels/{id}/posts/scheduled")
    suspend fun getScheduledPosts(
        @Path("id") channelId: Long,
    ): ChannelScheduledPostsResponse

    @POST("api/node/channels/{id}/posts/scheduled")
    suspend fun createScheduledPost(
        @Path("id") channelId: Long,
        @Body body: CreateScheduledPostRequest,
    ): ChannelScheduledPostResponse

    @DELETE("api/node/channels/{id}/posts/scheduled/{postId}")
    suspend fun cancelScheduledPost(
        @Path("id")     channelId: Long,
        @Path("postId") postId:    Long,
    ): BusinessActionResponse
}

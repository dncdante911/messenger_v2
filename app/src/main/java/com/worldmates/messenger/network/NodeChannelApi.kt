package com.worldmates.messenger.network

import com.worldmates.messenger.data.Constants
import com.worldmates.messenger.data.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*

/**
 * Node.js REST API — Retrofit interface for channels.
 *
 * Mirrors the PHP WorldMatesApi channel methods but routes through Node.js:
 *  - Base URL: Constants.NODE_BASE_URL (port 449)
 *  - Auth: access-token header (via NodeAuthInterceptor in NodeRetrofitClient)
 *  - No access_token query parameter needed
 */
interface NodeChannelApi {

    // ═══════════════════════ CHANNEL MANAGEMENT ═══════════════════════════════

    @FormUrlEncoded
    @POST(Constants.NODE_CHANNEL_LIST)
    suspend fun getChannels(
        @Field("type") type: String = "get_list",
        @Field("limit") limit: Int = 50,
        @Field("offset") offset: Int = 0,
        @Field("query") query: String? = null
    ): ChannelListResponse

    @FormUrlEncoded
    @POST(Constants.NODE_CHANNEL_DETAILS)
    suspend fun getChannelDetails(
        @Field("channel_id") channelId: Long
    ): ChannelDetailResponse

    @FormUrlEncoded
    @POST(Constants.NODE_CHANNEL_CREATE)
    suspend fun createChannel(
        @Field("name") name: String,
        @Field("username") username: String? = null,
        @Field("description") description: String? = null,
        @Field("avatar_url") avatarUrl: String? = null,
        @Field("is_private") isPrivate: Int = 0,
        @Field("category") category: String? = null
    ): CreateChannelResponse

    @FormUrlEncoded
    @POST(Constants.NODE_CHANNEL_UPDATE)
    suspend fun updateChannel(
        @Field("channel_id") channelId: Long,
        @Field("name") name: String? = null,
        @Field("description") description: String? = null,
        @Field("username") username: String? = null,
        @Field("category") category: String? = null
    ): CreateChannelResponse

    @FormUrlEncoded
    @POST(Constants.NODE_CHANNEL_DELETE)
    suspend fun deleteChannel(
        @Field("channel_id") channelId: Long
    ): CreateChannelResponse

    @FormUrlEncoded
    @POST(Constants.NODE_CHANNEL_SUBSCRIBE)
    suspend fun subscribeChannel(
        @Field("channel_id") channelId: Long
    ): CreateChannelResponse

    @FormUrlEncoded
    @POST(Constants.NODE_CHANNEL_UNSUBSCRIBE)
    suspend fun unsubscribeChannel(
        @Field("channel_id") channelId: Long
    ): CreateChannelResponse

    @FormUrlEncoded
    @POST(Constants.NODE_CHANNEL_ADD_MEMBER)
    suspend fun addChannelMember(
        @Field("channel_id") channelId: Long,
        @Field("user_id") userId: Long
    ): CreateChannelResponse

    // ═══════════════════════ POSTS ════════════════════════════════════════════

    @FormUrlEncoded
    @POST(Constants.NODE_CHANNEL_POSTS)
    suspend fun getChannelPosts(
        @Field("channel_id") channelId: Long,
        @Field("limit") limit: Int = 20,
        @Field("before_post_id") beforePostId: Long? = null
    ): ChannelPostsResponse

    @FormUrlEncoded
    @POST(Constants.NODE_CHANNEL_CREATE_POST)
    suspend fun createChannelPost(
        @Field("channel_id") channelId: Long,
        @Field("text") text: String,
        @Field("media_urls") mediaUrls: String? = null,
        @Field("disable_comments") disableComments: Int = 0,
        @Field("notify_subscribers") notifySubscribers: Int = 1
    ): CreatePostResponse

    @FormUrlEncoded
    @POST(Constants.NODE_CHANNEL_UPDATE_POST)
    suspend fun updateChannelPost(
        @Field("post_id") postId: Long,
        @Field("text") text: String,
        @Field("media_urls") mediaUrls: String? = null
    ): CreatePostResponse

    @FormUrlEncoded
    @POST(Constants.NODE_CHANNEL_DELETE_POST)
    suspend fun deleteChannelPost(
        @Field("post_id") postId: Long
    ): CreatePostResponse

    @FormUrlEncoded
    @POST(Constants.NODE_CHANNEL_PIN_POST)
    suspend fun pinChannelPost(
        @Field("post_id") postId: Long
    ): CreatePostResponse

    @FormUrlEncoded
    @POST(Constants.NODE_CHANNEL_UNPIN_POST)
    suspend fun unpinChannelPost(
        @Field("post_id") postId: Long
    ): CreatePostResponse

    // ═══════════════════════ COMMENTS ═════════════════════════════════════════

    @FormUrlEncoded
    @POST(Constants.NODE_CHANNEL_COMMENTS)
    suspend fun getChannelComments(
        @Field("post_id") postId: Long,
        @Field("limit") limit: Int = 50,
        @Field("offset") offset: Int = 0
    ): ChannelCommentsResponse

    @FormUrlEncoded
    @POST(Constants.NODE_CHANNEL_ADD_COMMENT)
    suspend fun addChannelComment(
        @Field("post_id") postId: Long,
        @Field("text") text: String,
        @Field("reply_to_id") replyToId: Long? = null
    ): CreatePostResponse

    @FormUrlEncoded
    @POST(Constants.NODE_CHANNEL_DEL_COMMENT)
    suspend fun deleteChannelComment(
        @Field("comment_id") commentId: Long
    ): CreatePostResponse

    // ═══════════════════════ REACTIONS ═════════════════════════════════════════

    @FormUrlEncoded
    @POST(Constants.NODE_CHANNEL_POST_REACT)
    suspend fun addPostReaction(
        @Field("post_id") postId: Long,
        @Field("reaction") emoji: String
    ): CreatePostResponse

    @FormUrlEncoded
    @POST(Constants.NODE_CHANNEL_POST_UNREACT)
    suspend fun removePostReaction(
        @Field("post_id") postId: Long,
        @Field("reaction") emoji: String
    ): CreatePostResponse

    @FormUrlEncoded
    @POST(Constants.NODE_CHANNEL_COMMENT_REACT)
    suspend fun addCommentReaction(
        @Field("comment_id") commentId: Long,
        @Field("reaction") reaction: String
    ): CreatePostResponse

    // ═══════════════════════ VIEW TRACKING ═════════════════════════════════════

    @FormUrlEncoded
    @POST(Constants.NODE_CHANNEL_POST_VIEW)
    suspend fun registerPostView(
        @Field("post_id") postId: Long
    ): CreatePostResponse

    // ═══════════════════════ ADMIN ═════════════════════════════════════════════

    @FormUrlEncoded
    @POST(Constants.NODE_CHANNEL_ADD_ADMIN)
    suspend fun addChannelAdmin(
        @Field("channel_id") channelId: Long,
        @Field("user_id") userId: Long? = null,
        @Field("user_search") userSearch: String? = null,
        @Field("role") role: String = "admin"
    ): CreateChannelResponse

    @FormUrlEncoded
    @POST(Constants.NODE_CHANNEL_REMOVE_ADMIN)
    suspend fun removeChannelAdmin(
        @Field("channel_id") channelId: Long,
        @Field("user_id") userId: Long
    ): CreateChannelResponse

    @FormUrlEncoded
    @POST(Constants.NODE_CHANNEL_SETTINGS)
    suspend fun updateChannelSettings(
        @Field("channel_id") channelId: Long,
        @Field("settings_json") settingsJson: String
    ): CreateChannelResponse

    // ═══════════════════════ STATISTICS & SUBSCRIBERS ═══════════════════════════

    @FormUrlEncoded
    @POST(Constants.NODE_CHANNEL_STATISTICS)
    suspend fun getChannelStatistics(
        @Field("channel_id") channelId: Long
    ): ChannelStatisticsResponse

    @FormUrlEncoded
    @POST(Constants.NODE_CHANNEL_SUBSCRIBERS)
    suspend fun getChannelSubscribers(
        @Field("channel_id") channelId: Long,
        @Field("limit") limit: Int = 50,
        @Field("offset") offset: Int = 0
    ): ChannelSubscribersResponse

    // ═══════════════════════ NOTIFICATIONS ══════════════════════════════════════

    @FormUrlEncoded
    @POST(Constants.NODE_CHANNEL_MUTE)
    suspend fun muteChannel(
        @Field("channel_id") channelId: Long
    ): CreateChannelResponse

    @FormUrlEncoded
    @POST(Constants.NODE_CHANNEL_UNMUTE)
    suspend fun unmuteChannel(
        @Field("channel_id") channelId: Long
    ): CreateChannelResponse

    // ═══════════════════════ QR CODES ═══════════════════════════════════════════

    @FormUrlEncoded
    @POST(Constants.NODE_CHANNEL_QR_GENERATE)
    suspend fun generateChannelQr(
        @Field("channel_id") channelId: Long
    ): QrCodeResponse

    @FormUrlEncoded
    @POST(Constants.NODE_CHANNEL_QR_SUBSCRIBE)
    suspend fun subscribeChannelByQr(
        @Field("qr_code") qrCode: String
    ): SubscribeChannelResponse

    // ═══════════════════════ AVATAR UPLOAD ══════════════════════════════════════

    @Multipart
    @POST(Constants.NODE_CHANNEL_UPLOAD_AVATAR)
    suspend fun uploadChannelAvatar(
        @Part("channel_id") channelId: RequestBody,
        @Part file: MultipartBody.Part
    ): MediaUploadResponse

    // ═══════════════════════ GENERAL MEDIA UPLOAD ═══════════════════════════════

    @Multipart
    @POST(Constants.NODE_MEDIA_UPLOAD)
    suspend fun uploadMedia(
        @Part("media_type") mediaType: RequestBody,
        @Part file: MultipartBody.Part
    ): MediaUploadResponse
}

/** QR code response. */
data class QrCodeResponse(
    @com.google.gson.annotations.SerializedName("api_status") val apiStatus: Int,
    @com.google.gson.annotations.SerializedName("qr_url") val qrUrl: String? = null,
    @com.google.gson.annotations.SerializedName("join_url") val joinUrl: String? = null,
    @com.google.gson.annotations.SerializedName("error_message") val errorMessage: String? = null
)

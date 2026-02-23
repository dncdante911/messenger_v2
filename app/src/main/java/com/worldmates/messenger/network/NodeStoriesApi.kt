package com.worldmates.messenger.network

import com.worldmates.messenger.data.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*

/**
 * Node.js REST API for Stories (replaces PHP endpoints).
 *
 * Auth is handled by NodeRetrofitClient's auth interceptor (access-token header).
 * Base URL: Constants.NODE_BASE_URL (https://worldmates.club:449/)
 */
interface NodeStoriesApi {

    /** Create a new story with file upload */
    @Multipart
    @POST("api/node/stories/create")
    suspend fun createStory(
        @Part file: MultipartBody.Part,
        @Part("file_type") fileType: RequestBody,
        @Part("story_title") storyTitle: RequestBody? = null,
        @Part("story_description") storyDescription: RequestBody? = null,
        @Part("video_duration") videoDuration: RequestBody? = null,
        @Part cover: MultipartBody.Part? = null
    ): CreateStoryResponse

    /** Get active stories list */
    @FormUrlEncoded
    @POST("api/node/stories/get")
    suspend fun getStories(
        @Field("limit") limit: Int = 35
    ): GetStoriesResponse

    /** Get stories for a specific user */
    @FormUrlEncoded
    @POST("api/node/stories/get-user-stories")
    suspend fun getUserStories(
        @Field("user_id") userId: Long,
        @Field("limit") limit: Int = 35
    ): GetStoriesResponse

    /** Mark story as viewed (inserts into Wo_Story_Seen) */
    @FormUrlEncoded
    @POST("api/node/stories/mark-viewed")
    suspend fun markStoryViewed(
        @Field("story_id") storyId: Long
    ): NodeSimpleResponse

    /** React to story (toggle: same reaction removes, different replaces) */
    @FormUrlEncoded
    @POST("api/node/stories/react")
    suspend fun reactToStory(
        @Field("story_id") storyId: Long,
        @Field("reaction") reaction: String
    ): ReactStoryResponse

    /** Get comments for a story */
    @FormUrlEncoded
    @POST("api/node/stories/get-comments")
    suspend fun getStoryComments(
        @Field("story_id") storyId: Long,
        @Field("limit") limit: Int = 20,
        @Field("offset") offset: Int = 0
    ): GetStoryCommentsResponse

    /** Create a comment on a story */
    @FormUrlEncoded
    @POST("api/node/stories/create-comment")
    suspend fun createStoryComment(
        @Field("story_id") storyId: Long,
        @Field("text") text: String
    ): CreateStoryCommentResponse
}

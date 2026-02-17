package com.worldmates.messenger.network

import com.worldmates.messenger.data.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*

/**
 * Окремий API сервіс для Stories
 * Створений як окремий інтерфейс для кращої модульності та підтримки
 */
interface StoriesApiService {

    // ==================== STORIES BASIC ====================

    /**
     * Створити нову story
     * @param file Медіа файл (фото або відео)
     * @param fileType Тип файлу: "image" або "video"
     * @param storyTitle Заголовок story (опціонально, макс 100 символів)
     * @param storyDescription Опис story (опціонально, макс 300 символів)
     * @param videoDuration Тривалість відео в секундах (обов'язково для відео)
     * @param cover Обкладинка для відео (опціонально)
     */
    @Multipart
    @POST("/api/v2/endpoints/create-story.php")
    suspend fun createStory(
        @Query("access_token") accessToken: String,
        @Part file: MultipartBody.Part,
        @Part("file_type") fileType: RequestBody,
        @Part("story_title") storyTitle: RequestBody? = null,
        @Part("story_description") storyDescription: RequestBody? = null,
        @Part("video_duration") videoDuration: RequestBody? = null,
        @Part cover: MultipartBody.Part? = null
    ): CreateStoryResponse

    /**
     * Отримати список активних stories
     * @param limit Кількість stories (за замовчуванням 35, макс 50)
     */
    @FormUrlEncoded
    @POST("/api/v2/endpoints/get-stories.php")
    suspend fun getStories(
        @Query("access_token") accessToken: String,
        @Field("limit") limit: Int = 35
    ): GetStoriesResponse

    /**
     * Отримати story за ID
     * @param storyId ID story
     */
    @FormUrlEncoded
    @POST("/api/v2/endpoints/get_story_by_id.php")
    suspend fun getStoryById(
        @Query("access_token") accessToken: String,
        @Field("id") storyId: Long
    ): GetStoryByIdResponse

    /**
     * Отримати stories конкретного користувача
     * @param userId ID користувача
     * @param limit Кількість stories
     */
    @FormUrlEncoded
    @POST("/api/v2/endpoints/get-user-stories.php")
    suspend fun getUserStories(
        @Query("access_token") accessToken: String,
        @Field("user_id") userId: Long,
        @Field("limit") limit: Int = 35
    ): GetStoriesResponse

    /**
     * Видалити story
     * @param storyId ID story для видалення
     */
    @FormUrlEncoded
    @POST("/api/v2/endpoints/delete-story.php")
    suspend fun deleteStory(
        @Query("access_token") accessToken: String,
        @Field("story_id") storyId: Long
    ): DeleteStoryResponse

    // ==================== STORY VIEWS ====================

    /**
     * Отримати список користувачів, які переглянули story
     * @param storyId ID story
     * @param limit Кількість результатів (за замовчуванням 20, макс 50)
     * @param offset Зміщення для пагінації
     */
    @FormUrlEncoded
    @POST("/api/v2/endpoints/get_story_views.php")
    suspend fun getStoryViews(
        @Query("access_token") accessToken: String,
        @Field("story_id") storyId: Long,
        @Field("limit") limit: Int = 20,
        @Field("offset") offset: Int = 0
    ): GetStoryViewsResponse

    // ==================== STORY REACTIONS ====================

    /**
     * Додати/видалити реакцію на story
     * @param storyId ID story
     * @param reaction Тип реакції: "like", "love", "haha", "wow", "sad", "angry"
     * Повторний виклик з тією ж реакцією видалить її
     */
    @FormUrlEncoded
    @POST("/api/v2/endpoints/react_story.php")
    suspend fun reactToStory(
        @Query("access_token") accessToken: String,
        @Field("id") storyId: Long,
        @Field("reaction") reaction: String
    ): ReactStoryResponse

    // ==================== STORY MUTE ====================

    /**
     * Приглушити stories певного користувача
     * @param userId ID користувача, чиї stories потрібно приглушити
     */
    @FormUrlEncoded
    @POST("/api/v2/endpoints/mute_story.php")
    suspend fun muteStory(
        @Query("access_token") accessToken: String,
        @Field("user_id") userId: Long
    ): MuteStoryResponse

    // ==================== STORY COMMENTS ====================

    /**
     * Створити коментар до story
     * @param storyId ID story
     * @param text Текст коментаря
     */
    @FormUrlEncoded
    @POST("/api/v2/endpoints/create_story_comment.php")
    suspend fun createStoryComment(
        @Query("access_token") accessToken: String,
        @Field("story_id") storyId: Long,
        @Field("text") text: String
    ): CreateStoryCommentResponse

    /**
     * Отримати коментарі до story
     * @param storyId ID story
     * @param limit Кількість коментарів (за замовчуванням 20, макс 50)
     * @param offset ID для пагінації
     */
    @FormUrlEncoded
    @POST("/api/v2/endpoints/get_story_comments.php")
    suspend fun getStoryComments(
        @Query("access_token") accessToken: String,
        @Field("story_id") storyId: Long,
        @Field("limit") limit: Int = 20,
        @Field("offset") offset: Int = 0
    ): GetStoryCommentsResponse

    /**
     * Видалити коментар до story
     * @param commentId ID коментаря
     * Примітка: Видалити може автор коментаря, власник story або адмін
     */
    @FormUrlEncoded
    @POST("/api/v2/endpoints/delete_story_comment.php")
    suspend fun deleteStoryComment(
        @Query("access_token") accessToken: String,
        @Field("comment_id") commentId: Long
    ): DeleteStoryCommentResponse

    // ==================== CHANNEL STORIES ====================

    /**
     * Створити story каналу (тільки для адмінів)
     */
    @Multipart
    @POST("/api/v2/channel_stories.php")
    suspend fun createChannelStory(
        @Query("access_token") accessToken: String,
        @Query("type") type: String = "create",
        @Part("channel_id") channelId: RequestBody,
        @Part file: MultipartBody.Part,
        @Part("file_type") fileType: RequestBody,
        @Part("story_title") storyTitle: RequestBody? = null,
        @Part("story_description") storyDescription: RequestBody? = null
    ): CreateStoryResponse

    /**
     * Отримати stories каналу
     */
    @FormUrlEncoded
    @POST("/api/v2/channel_stories.php")
    suspend fun getChannelStories(
        @Query("access_token") accessToken: String,
        @Query("type") type: String = "get_channel",
        @Field("channel_id") channelId: Long,
        @Field("limit") limit: Int = 20
    ): GetStoriesResponse

    /**
     * Отримати stories всіх підписаних каналів
     */
    @FormUrlEncoded
    @POST("/api/v2/channel_stories.php")
    suspend fun getSubscribedChannelStories(
        @Query("access_token") accessToken: String,
        @Query("type") type: String = "get_subscribed",
        @Field("limit") limit: Int = 30
    ): GetStoriesResponse

    /**
     * Видалити story каналу
     */
    @FormUrlEncoded
    @POST("/api/v2/channel_stories.php")
    suspend fun deleteChannelStory(
        @Query("access_token") accessToken: String,
        @Query("type") type: String = "delete",
        @Field("story_id") storyId: Long
    ): DeleteStoryResponse
}

/**
 * Enum для типів реакцій на stories
 */
enum class StoryReactionType(val value: String) {
    LIKE("like"),
    LOVE("love"),
    HAHA("haha"),
    WOW("wow"),
    SAD("sad"),
    ANGRY("angry");

    override fun toString(): String = value
}
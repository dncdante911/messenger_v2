package com.worldmates.messenger.network

import com.worldmates.messenger.data.model.*
import retrofit2.http.*

/**
 * WorldMates Bot API - Retrofit interface
 *
 * Окремий інтерфейс для Bot API endpoints, щоб не розширювати WorldMatesApi.kt.
 * Використовуй RetrofitClient.botApiService для викликів.
 *
 * Base URL: POST /api/v2/endpoints/bot_api.php
 */
interface BotApi {

    // ==================== BOT MANAGEMENT (User endpoints) ====================

    @FormUrlEncoded
    @POST("/api/v2/endpoints/bot_api.php")
    suspend fun createBot(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "create_bot",
        @Field("username") username: String,
        @Field("display_name") displayName: String,
        @Field("description") description: String? = null,
        @Field("about") about: String? = null,
        @Field("category") category: String? = "general",
        @Field("can_join_groups") canJoinGroups: Int = 1,
        @Field("is_public") isPublic: Int = 1
    ): CreateBotResponse

    @FormUrlEncoded
    @POST("/api/v2/endpoints/bot_api.php")
    suspend fun getMyBots(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "get_my_bots",
        @Field("limit") limit: Int = 20,
        @Field("offset") offset: Int = 0
    ): BotListResponse

    @FormUrlEncoded
    @POST("/api/v2/endpoints/bot_api.php")
    suspend fun updateBot(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "update_bot",
        @Field("bot_id") botId: String,
        @Field("display_name") displayName: String? = null,
        @Field("description") description: String? = null,
        @Field("about") about: String? = null,
        @Field("category") category: String? = null,
        @Field("is_public") isPublic: Int? = null,
        @Field("can_join_groups") canJoinGroups: Int? = null
    ): BotGenericResponse

    @FormUrlEncoded
    @POST("/api/v2/endpoints/bot_api.php")
    suspend fun deleteBot(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "delete_bot",
        @Field("bot_id") botId: String
    ): BotGenericResponse

    @FormUrlEncoded
    @POST("/api/v2/endpoints/bot_api.php")
    suspend fun regenerateBotToken(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "regenerate_token",
        @Field("bot_id") botId: String
    ): BotTokenResponse

    // ==================== PUBLIC BOT DISCOVERY ====================

    @FormUrlEncoded
    @POST("/api/v2/endpoints/bot_api.php")
    suspend fun getBotInfo(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "get_bot_info",
        @Field("bot_id") botId: String? = null,
        @Field("username") username: String? = null
    ): BotInfoResponse

    @FormUrlEncoded
    @POST("/api/v2/endpoints/bot_api.php")
    suspend fun searchBots(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "search_bots",
        @Field("query") query: String? = null,
        @Field("category") category: String? = null,
        @Field("limit") limit: Int = 20,
        @Field("offset") offset: Int = 0
    ): BotSearchResponse

    // ==================== COMMANDS ====================

    @FormUrlEncoded
    @POST("/api/v2/endpoints/bot_api.php")
    suspend fun getBotCommands(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "get_commands",
        @Field("bot_id") botId: String
    ): BotCommandsResponse

    // ==================== BOT MESSAGING (Bot token auth) ====================
    // These are for internal use when user interacts with bot in chat

    @FormUrlEncoded
    @POST("/api/v2/endpoints/bot_api.php")
    suspend fun sendBotCommand(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "send_message",
        @Field("bot_token") botToken: String,
        @Field("chat_id") chatId: String,
        @Field("text") text: String,
        @Field("parse_mode") parseMode: String = "markdown"
    ): BotSendMessageResponse

    // ==================== POLLS ====================

    @FormUrlEncoded
    @POST("/api/v2/endpoints/bot_api.php")
    suspend fun sendBotPoll(
        @Field("type") type: String = "send_poll",
        @Field("bot_token") botToken: String,
        @Field("chat_id") chatId: String,
        @Field("question") question: String,
        @Field("options") options: String, // JSON array
        @Field("poll_type") pollType: String = "regular",
        @Field("is_anonymous") isAnonymous: Int = 1,
        @Field("allows_multiple_answers") allowsMultiple: Int = 0
    ): BotPollResponse

    @FormUrlEncoded
    @POST("/api/v2/endpoints/bot_api.php")
    suspend fun stopBotPoll(
        @Field("type") type: String = "stop_poll",
        @Field("bot_token") botToken: String,
        @Field("poll_id") pollId: Int
    ): BotPollResponse

    // ==================== WEBHOOK INFO ====================

    @FormUrlEncoded
    @POST("/api/v2/endpoints/bot_api.php")
    suspend fun getWebhookInfo(
        @Field("type") type: String = "get_webhook_info",
        @Field("bot_token") botToken: String
    ): BotWebhookInfoResponse
}

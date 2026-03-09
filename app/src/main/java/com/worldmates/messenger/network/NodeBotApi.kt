package com.worldmates.messenger.network

import com.worldmates.messenger.data.model.*
import retrofit2.http.*

/**
 * Node.js Bot API — Retrofit interface.
 *
 * Всі запити йдуть через NodeRetrofitClient (access-token header).
 * Жодних викликів до bot_api.php (PHP).
 */
interface NodeBotApi {

    // ── Каталог / пошук ──────────────────────────────────────────────────────

    @FormUrlEncoded
    @POST("api/node/bot/catalog")
    suspend fun searchBots(
        @Field("query")    query: String? = null,
        @Field("category") category: String? = null,
        @Field("limit")    limit: Int = 30,
        @Field("offset")   offset: Int = 0
    ): BotSearchResponse

    // ── Інформація про бота ───────────────────────────────────────────────────

    @FormUrlEncoded
    @POST("api/node/bot/info")
    suspend fun getBotInfo(
        @Field("bot_id")   botId: String? = null,
        @Field("username") username: String? = null
    ): BotInfoResponse

    @FormUrlEncoded
    @POST("api/node/bot/commands")
    suspend fun getBotCommands(
        @Field("bot_id") botId: String
    ): BotCommandsResponse

    // ── Мої боти (CRUD) ───────────────────────────────────────────────────────

    @FormUrlEncoded
    @POST("api/node/bot/my")
    suspend fun getMyBots(
        @Field("limit")  limit: Int = 20,
        @Field("offset") offset: Int = 0
    ): BotListResponse

    @FormUrlEncoded
    @POST("api/node/bot/create")
    suspend fun createBot(
        @Field("username")       username: String,
        @Field("display_name")   displayName: String,
        @Field("description")    description: String? = null,
        @Field("about")          about: String? = null,
        @Field("category")       category: String? = "general",
        @Field("can_join_groups") canJoinGroups: Int = 1,
        @Field("is_public")      isPublic: Int = 1
    ): CreateBotResponse

    @FormUrlEncoded
    @POST("api/node/bot/update")
    suspend fun updateBot(
        @Field("bot_id")         botId: String,
        @Field("display_name")   displayName: String? = null,
        @Field("description")    description: String? = null,
        @Field("about")          about: String? = null,
        @Field("category")       category: String? = null,
        @Field("is_public")      isPublic: Int? = null,
        @Field("can_join_groups") canJoinGroups: Int? = null
    ): BotGenericResponse

    @FormUrlEncoded
    @POST("api/node/bot/delete")
    suspend fun deleteBot(
        @Field("bot_id") botId: String
    ): BotGenericResponse

    @FormUrlEncoded
    @POST("api/node/bot/regenerate-token")
    suspend fun regenerateBotToken(
        @Field("bot_id") botId: String
    ): BotTokenResponse

    // ── RSS ───────────────────────────────────────────────────────────────────

    @GET("api/node/bots/{bot_id}/rss")
    suspend fun getRssFeeds(
        @Path("bot_id") botId: String
    ): RssFeedListResponse

    @FormUrlEncoded
    @POST("api/node/bots/{bot_id}/rss")
    suspend fun addRssFeed(
        @Path("bot_id")                botId: String,
        @Field("feed_url")             feedUrl: String,
        @Field("chat_id")              chatId: String,
        @Field("feed_name")            feedName: String? = null,
        @Field("check_interval_minutes") intervalMinutes: Int = 30,
        @Field("max_items_per_check")  maxItems: Int = 5,
        @Field("include_image")        includeImage: Int = 1,
        @Field("include_description")  includeDesc: Int = 1
    ): RssFeedResponse

    @FormUrlEncoded
    @PUT("api/node/bots/{bot_id}/rss/{feed_id}")
    suspend fun updateRssFeed(
        @Path("bot_id")  botId: String,
        @Path("feed_id") feedId: Int,
        @Field("is_active")              isActive: Int? = null,
        @Field("check_interval_minutes") intervalMinutes: Int? = null,
        @Field("max_items_per_check")    maxItems: Int? = null
    ): RssFeedResponse

    @DELETE("api/node/bots/{bot_id}/rss/{feed_id}")
    suspend fun deleteRssFeed(
        @Path("bot_id")  botId: String,
        @Path("feed_id") feedId: Int
    ): BotGenericResponse
}

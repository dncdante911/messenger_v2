package com.worldmates.messenger.network

import com.worldmates.messenger.data.model.*
import retrofit2.http.*

/**
 * Node.js Bot API — Retrofit interface.
 *
 * Точно соответствует route-регистрации в api-server-files/nodejs/routes/bots/index.js:
 *
 *  GET    /api/node/bots/search?q=...   — публичный поиск ботов
 *  GET    /api/node/bots                — мои боты (uAuth)
 *  POST   /api/node/bots                — создать бота (uAuth)
 *  GET    /api/node/bots/:bot_id        — инфо о боте + commands (публичный)
 *  PUT    /api/node/bots/:bot_id        — обновить бота (uAuth)
 *  DELETE /api/node/bots/:bot_id        — удалить бота (uAuth)
 *  POST   /api/node/bots/:bot_id/regenerate-token  (uAuth)
 *  GET    /api/node/bots/:bot_id/rss                (uAuth)
 *  POST   /api/node/bots/:bot_id/rss                (uAuth)
 *  PUT    /api/node/bots/:bot_id/rss/:feed_id       (uAuth)
 *  DELETE /api/node/bots/:bot_id/rss/:feed_id       (uAuth)
 *
 * Auth: access-token header (добавляется NodeAuthInterceptor в NodeRetrofitClient).
 */
interface NodeBotApi {

    // ── Пошук ─────────────────────────────────────────────────────────────────
    // GET /api/node/bots/search?q=...&limit=...&offset=...
    // Обязательный параметр q; возвращает { api_status, bots, count }
    @GET("api/node/bots/search")
    suspend fun searchBots(
        @Query("q")      q: String,
        @Query("limit")  limit: Int = 30,
        @Query("offset") offset: Int = 0
    ): BotSearchResponse

    // ── Мої боти ──────────────────────────────────────────────────────────────
    // GET /api/node/bots?limit=...&offset=...
    @GET("api/node/bots")
    suspend fun getMyBots(
        @Query("limit")  limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): BotListResponse

    // ── Інформація (+ commands вбудовані) ─────────────────────────────────────
    // GET /api/node/bots/:bot_id
    @GET("api/node/bots/{bot_id}")
    suspend fun getBotInfo(
        @Path("bot_id") botId: String
    ): BotInfoResponse

    // ── Створення ─────────────────────────────────────────────────────────────
    // POST /api/node/bots
    @FormUrlEncoded
    @POST("api/node/bots")
    suspend fun createBot(
        @Field("username")        username: String,
        @Field("display_name")    displayName: String,
        @Field("description")     description: String? = null,
        @Field("about")           about: String? = null,
        @Field("category")        category: String? = "general",
        @Field("can_join_groups") canJoinGroups: Int = 1,
        @Field("is_public")       isPublic: Int = 1
    ): CreateBotResponse

    // ── Оновлення ─────────────────────────────────────────────────────────────
    // PUT /api/node/bots/:bot_id
    @FormUrlEncoded
    @PUT("api/node/bots/{bot_id}")
    suspend fun updateBot(
        @Path("bot_id")           botId: String,
        @Field("display_name")    displayName: String? = null,
        @Field("description")     description: String? = null,
        @Field("about")           about: String? = null,
        @Field("category")        category: String? = null,
        @Field("is_public")       isPublic: Int? = null,
        @Field("can_join_groups") canJoinGroups: Int? = null,
        // Mini App URL: set to https://... to configure, or pass clear_web_app=1 to remove
        @Field("web_app_url")     webAppUrl: String? = null,
        @Field("clear_web_app")   clearWebApp: Int? = null
    ): BotGenericResponse

    // ── Видалення ─────────────────────────────────────────────────────────────
    // DELETE /api/node/bots/:bot_id
    @DELETE("api/node/bots/{bot_id}")
    suspend fun deleteBot(
        @Path("bot_id") botId: String
    ): BotGenericResponse

    // ── Regenerate token ──────────────────────────────────────────────────────
    // POST /api/node/bots/:bot_id/regenerate-token
    @POST("api/node/bots/{bot_id}/regenerate-token")
    suspend fun regenerateBotToken(
        @Path("bot_id") botId: String
    ): BotTokenResponse

    // ── RSS Feeds ─────────────────────────────────────────────────────────────
    @GET("api/node/bots/{bot_id}/rss")
    suspend fun getRssFeeds(
        @Path("bot_id") botId: String
    ): RssFeedListResponse

    @FormUrlEncoded
    @POST("api/node/bots/{bot_id}/rss")
    suspend fun addRssFeed(
        @Path("bot_id")                  botId: String,
        @Field("feed_url")               feedUrl: String,
        @Field("chat_id")                chatId: String,
        @Field("feed_name")              feedName: String? = null,
        @Field("check_interval_minutes") intervalMinutes: Int = 30,
        @Field("max_items_per_check")    maxItems: Int = 5,
        @Field("include_image")          includeImage: Int = 1,
        @Field("include_description")    includeDesc: Int = 1
    ): RssFeedResponse

    @FormUrlEncoded
    @PUT("api/node/bots/{bot_id}/rss/{feed_id}")
    suspend fun updateRssFeed(
        @Path("bot_id")                  botId: String,
        @Path("feed_id")                 feedId: Int,
        @Field("is_active")              isActive: Int? = null,
        @Field("check_interval_minutes") intervalMinutes: Int? = null,
        @Field("max_items_per_check")    maxItems: Int? = null
    ): RssFeedResponse

    @DELETE("api/node/bots/{bot_id}/rss/{feed_id}")
    suspend fun deleteRssFeed(
        @Path("bot_id")  botId: String,
        @Path("feed_id") feedId: Int
    ): BotGenericResponse

    // ── Mini Apps ─────────────────────────────────────────────────────────────
    // POST /api/node/bot/createWebAppToken
    // uAuth — signs init_data for the Mini App WebView
    @FormUrlEncoded
    @POST("api/node/bot/createWebAppToken")
    suspend fun createWebAppToken(
        @Field("bot_id")   botId: String,
        @Field("chat_id")  chatId: String? = null
    ): WebAppTokenResponse

    // POST /api/node/bot/answerWebAppQuery
    // uAuth — delivers web_app_data back to bot via webhook / Socket.IO
    @FormUrlEncoded
    @POST("api/node/bot/answerWebAppQuery")
    suspend fun answerWebAppQuery(
        @Field("query_id") queryId: String,
        @Field("bot_id")   botId: String,
        @Field("data")     data: String
    ): WebAppQueryResponse
}

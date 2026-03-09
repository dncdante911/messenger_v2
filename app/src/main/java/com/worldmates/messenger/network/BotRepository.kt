package com.worldmates.messenger.network

import android.util.Log
import com.worldmates.messenger.data.model.*

/**
 * Repository для Bot API операцій.
 * Всі запити йдуть через NodeRetrofitClient (Node.js) — не через PHP.
 */
class BotRepository(
    private val botApi: NodeBotApi = NodeRetrofitClient.botApi
) {
    companion object {
        private const val TAG = "BotRepository"
    }

    // ── BOT MANAGEMENT ────────────────────────────────────────────────────────

    suspend fun createBot(
        accessToken: String,   // залишаємо для сумісності з BotViewModel, не використовується
        username: String,
        displayName: String,
        description: String? = null,
        about: String? = null,
        category: String = "general",
        canJoinGroups: Boolean = true,
        isPublic: Boolean = true
    ): Result<Bot> = try {
        val response = botApi.createBot(
            username      = username,
            displayName   = displayName,
            description   = description,
            about         = about,
            category      = category,
            canJoinGroups = if (canJoinGroups) 1 else 0,
            isPublic      = if (isPublic) 1 else 0
        )
        if (response.apiStatus == 200 && response.bot != null)
            Result.success(response.bot)
        else
            Result.failure(BotApiException(response.errorCode ?: 0, response.errorMessage ?: "Failed to create bot"))
    } catch (e: Exception) {
        Log.e(TAG, "createBot error", e); Result.failure(e)
    }

    suspend fun getMyBots(
        accessToken: String,
        limit: Int = 20,
        offset: Int = 0
    ): Result<List<Bot>> = try {
        val response = botApi.getMyBots(limit = limit, offset = offset)
        if (response.apiStatus == 200)
            Result.success(response.bots ?: emptyList())
        else
            Result.failure(BotApiException(response.errorCode ?: 0, response.errorMessage ?: "Failed to get bots"))
    } catch (e: Exception) {
        Log.e(TAG, "getMyBots error", e); Result.failure(e)
    }

    suspend fun updateBot(
        accessToken: String,
        botId: String,
        displayName: String? = null,
        description: String? = null,
        about: String? = null,
        category: String? = null,
        isPublic: Boolean? = null,
        canJoinGroups: Boolean? = null
    ): Result<Unit> = try {
        val response = botApi.updateBot(
            botId         = botId,
            displayName   = displayName,
            description   = description,
            about         = about,
            category      = category,
            isPublic      = isPublic?.let { if (it) 1 else 0 },
            canJoinGroups = canJoinGroups?.let { if (it) 1 else 0 }
        )
        if (response.apiStatus == 200) Result.success(Unit)
        else Result.failure(BotApiException(response.errorCode ?: 0, response.errorMessage ?: "Failed to update bot"))
    } catch (e: Exception) {
        Log.e(TAG, "updateBot error", e); Result.failure(e)
    }

    suspend fun deleteBot(accessToken: String, botId: String): Result<Unit> = try {
        val response = botApi.deleteBot(botId = botId)
        if (response.apiStatus == 200) Result.success(Unit)
        else Result.failure(BotApiException(response.errorCode ?: 0, response.errorMessage ?: "Failed to delete bot"))
    } catch (e: Exception) {
        Log.e(TAG, "deleteBot error", e); Result.failure(e)
    }

    suspend fun regenerateToken(accessToken: String, botId: String): Result<String> = try {
        val response = botApi.regenerateBotToken(botId = botId)
        if (response.apiStatus == 200 && response.botToken != null)
            Result.success(response.botToken)
        else
            Result.failure(BotApiException(response.errorCode ?: 0, response.errorMessage ?: "Failed to regenerate token"))
    } catch (e: Exception) {
        Log.e(TAG, "regenerateToken error", e); Result.failure(e)
    }

    // ── BOT DISCOVERY ─────────────────────────────────────────────────────────

    suspend fun getBotInfo(
        accessToken: String,
        botId: String? = null,
        username: String? = null
    ): Result<Bot> = try {
        val response = botApi.getBotInfo(botId = botId, username = username)
        if (response.apiStatus == 200 && response.bot != null)
            Result.success(response.bot)
        else
            Result.failure(BotApiException(response.errorCode ?: 0, response.errorMessage ?: "Bot not found"))
    } catch (e: Exception) {
        Log.e(TAG, "getBotInfo error", e); Result.failure(e)
    }

    suspend fun searchBots(
        accessToken: String,
        query: String? = null,
        category: String? = null,
        limit: Int = 30,
        offset: Int = 0
    ): Result<BotSearchResult> = try {
        val response = botApi.searchBots(
            query    = query,
            category = category,
            limit    = limit,
            offset   = offset
        )
        if (response.apiStatus == 200)
            Result.success(BotSearchResult(
                bots       = response.bots ?: emptyList(),
                categories = response.categories ?: emptyList(),
                total      = response.total
            ))
        else
            Result.failure(BotApiException(response.errorCode ?: 0, response.errorMessage ?: "Search failed"))
    } catch (e: Exception) {
        Log.e(TAG, "searchBots error", e); Result.failure(e)
    }

    suspend fun getBotCommands(accessToken: String, botId: String): Result<List<BotCommand>> = try {
        val response = botApi.getBotCommands(botId = botId)
        if (response.apiStatus == 200)
            Result.success(response.commands ?: emptyList())
        else
            Result.failure(BotApiException(response.errorCode ?: 0, response.errorMessage ?: "Failed to get commands"))
    } catch (e: Exception) {
        Log.e(TAG, "getBotCommands error", e); Result.failure(e)
    }

    // ── RSS FEEDS ─────────────────────────────────────────────────────────────

    suspend fun getRssFeeds(accessToken: String, botId: String): Result<List<RssFeed>> = try {
        val response = botApi.getRssFeeds(botId)
        if (response.apiStatus == 200) Result.success(response.feeds ?: emptyList())
        else Result.failure(BotApiException(0, response.errorMessage ?: "Failed"))
    } catch (e: Exception) {
        Log.e(TAG, "getRssFeeds", e); Result.failure(e)
    }

    suspend fun addRssFeed(
        accessToken: String, botId: String, feedUrl: String, chatId: String,
        feedName: String? = null, intervalMinutes: Int = 30,
        maxItems: Int = 5, includeImage: Boolean = true, includeDesc: Boolean = true
    ): Result<RssFeed> = try {
        val response = botApi.addRssFeed(
            botId, feedUrl, chatId, feedName,
            intervalMinutes, maxItems,
            if (includeImage) 1 else 0, if (includeDesc) 1 else 0
        )
        if (response.apiStatus == 200 && response.feed != null) Result.success(response.feed)
        else Result.failure(BotApiException(0, response.errorMessage ?: "Failed"))
    } catch (e: Exception) {
        Log.e(TAG, "addRssFeed", e); Result.failure(e)
    }

    suspend fun toggleRssFeed(accessToken: String, botId: String, feedId: Int, isActive: Boolean): Result<Unit> = try {
        val response = botApi.updateRssFeed(botId, feedId, isActive = if (isActive) 1 else 0)
        if (response.apiStatus == 200) Result.success(Unit)
        else Result.failure(BotApiException(0, response.errorMessage ?: "Failed"))
    } catch (e: Exception) {
        Log.e(TAG, "toggleRssFeed", e); Result.failure(e)
    }

    suspend fun deleteRssFeed(accessToken: String, botId: String, feedId: Int): Result<Unit> = try {
        val response = botApi.deleteRssFeed(botId, feedId)
        if (response.apiStatus == 200) Result.success(Unit)
        else Result.failure(BotApiException(0, response.errorMessage ?: "Failed"))
    } catch (e: Exception) {
        Log.e(TAG, "deleteRssFeed", e); Result.failure(e)
    }
}

data class BotSearchResult(
    val bots: List<Bot>,
    val categories: List<BotCategory>,
    val total: Int
)

class BotApiException(val errorCode: Int, message: String) : Exception(message)

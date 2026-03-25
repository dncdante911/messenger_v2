package com.worldmates.messenger.network

import android.util.Log
import com.worldmates.messenger.data.model.*

/**
 * Repository для Bot API.
 * Всі виклики → NodeRetrofitClient.botApi (Node.js).
 * Жодних звернень до PHP.
 */
class BotRepository(
    private val botApi: NodeBotApi = NodeRetrofitClient.botApi
) {
    companion object {
        private const val TAG = "BotRepository"
    }

    // ── Catalog / Search ──────────────────────────────────────────────────────

    /**
     * Шукає публічних ботів за query.
     * Якщо query порожній — повертає порожній список (Node.js вимагає q).
     */
    suspend fun searchBots(
        accessToken: String,   // зберігається для сумісності з BotViewModel, не використовується (йде в header)
        query: String? = null,
        category: String? = null,
        limit: Int = 30,
        offset: Int = 0
    ): Result<BotSearchResult> {
        val q = query?.trim()
        if (q.isNullOrBlank()) {
            return Result.success(BotSearchResult(emptyList(), emptyList(), 0))
        }
        return try {
            val resp = botApi.searchBots(q = q, limit = limit, offset = offset)
            if (resp.apiStatus == 200)
                Result.success(BotSearchResult(
                    bots       = resp.bots ?: emptyList(),
                    categories = resp.categories ?: emptyList(),
                    total      = resp.total
                ))
            else
                Result.failure(BotApiException(resp.errorCode ?: 0, resp.errorMessage ?: "Search failed"))
        } catch (e: Exception) {
            Log.e(TAG, "searchBots error", e); Result.failure(e)
        }
    }

    // ── Bot Info ──────────────────────────────────────────────────────────────

    /**
     * GET /api/node/bots/:bot_id — повертає бота + embedded commands.
     * username-lookup не підтримується, передайте botId.
     */
    suspend fun getBotInfo(
        accessToken: String,
        botId: String? = null,
        username: String? = null
    ): Result<Bot> {
        val id = botId ?: username ?: return Result.failure(BotApiException(400, "botId or username required"))
        return try {
            val resp = botApi.getBotInfo(id)
            if (resp.apiStatus == 200 && resp.bot != null) Result.success(resp.bot)
            else Result.failure(BotApiException(resp.errorCode ?: 0, resp.errorMessage ?: "Bot not found"))
        } catch (e: Exception) {
            Log.e(TAG, "getBotInfo error", e); Result.failure(e)
        }
    }

    /**
     * GET /api/node/bots/:bot_id — commands embedded in getBotInfo response.
     */
    suspend fun getBotCommands(accessToken: String, botId: String): Result<List<BotCommand>> {
        return try {
            val resp = botApi.getBotInfo(botId)
            if (resp.apiStatus == 200) Result.success(resp.commands ?: emptyList())
            else Result.failure(BotApiException(resp.errorCode ?: 0, resp.errorMessage ?: "Failed"))
        } catch (e: Exception) {
            Log.e(TAG, "getBotCommands error", e); Result.failure(e)
        }
    }

    // ── My Bots (CRUD) ────────────────────────────────────────────────────────

    suspend fun getMyBots(accessToken: String, limit: Int = 20, offset: Int = 0): Result<List<Bot>> {
        return try {
            val resp = botApi.getMyBots(limit = limit, offset = offset)
            if (resp.apiStatus == 200) Result.success(resp.bots ?: emptyList())
            else Result.failure(BotApiException(resp.errorCode ?: 0, resp.errorMessage ?: "Failed"))
        } catch (e: Exception) {
            Log.e(TAG, "getMyBots error", e); Result.failure(e)
        }
    }

    suspend fun createBot(
        accessToken: String,
        username: String,
        displayName: String,
        description: String? = null,
        about: String? = null,
        category: String = "general",
        canJoinGroups: Boolean = true,
        isPublic: Boolean = true
    ): Result<Bot> {
        return try {
            val resp = botApi.createBot(
                username      = username,
                displayName   = displayName,
                description   = description,
                about         = about,
                category      = category,
                canJoinGroups = if (canJoinGroups) 1 else 0,
                isPublic      = if (isPublic) 1 else 0
            )
            if (resp.apiStatus == 200 && resp.bot != null) Result.success(resp.bot)
            else Result.failure(BotApiException(resp.errorCode ?: 0, resp.errorMessage ?: "Failed to create bot"))
        } catch (e: Exception) {
            Log.e(TAG, "createBot error", e); Result.failure(e)
        }
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
    ): Result<Unit> {
        return try {
            val resp = botApi.updateBot(
                botId         = botId,
                displayName   = displayName,
                description   = description,
                about         = about,
                category      = category,
                isPublic      = isPublic?.let { if (it) 1 else 0 },
                canJoinGroups = canJoinGroups?.let { if (it) 1 else 0 }
            )
            if (resp.apiStatus == 200) Result.success(Unit)
            else Result.failure(BotApiException(resp.errorCode ?: 0, resp.errorMessage ?: "Failed to update bot"))
        } catch (e: Exception) {
            Log.e(TAG, "updateBot error", e); Result.failure(e)
        }
    }

    suspend fun setWebAppUrl(accessToken: String, botId: String, url: String): Result<Unit> {
        return try {
            val resp = botApi.updateBot(botId = botId, webAppUrl = url)
            if (resp.apiStatus == 200) Result.success(Unit)
            else Result.failure(BotApiException(resp.errorCode ?: 0, resp.errorMessage ?: "Failed to set Mini App URL"))
        } catch (e: Exception) {
            Log.e(TAG, "setWebAppUrl error", e); Result.failure(e)
        }
    }

    suspend fun clearWebAppUrl(accessToken: String, botId: String): Result<Unit> {
        return try {
            val resp = botApi.updateBot(botId = botId, clearWebApp = 1)
            if (resp.apiStatus == 200) Result.success(Unit)
            else Result.failure(BotApiException(resp.errorCode ?: 0, resp.errorMessage ?: "Failed to clear Mini App URL"))
        } catch (e: Exception) {
            Log.e(TAG, "clearWebAppUrl error", e); Result.failure(e)
        }
    }

    suspend fun deleteBot(accessToken: String, botId: String): Result<Unit> {
        return try {
            val resp = botApi.deleteBot(botId)
            if (resp.apiStatus == 200) Result.success(Unit)
            else Result.failure(BotApiException(resp.errorCode ?: 0, resp.errorMessage ?: "Failed to delete bot"))
        } catch (e: Exception) {
            Log.e(TAG, "deleteBot error", e); Result.failure(e)
        }
    }

    suspend fun regenerateToken(accessToken: String, botId: String): Result<String> {
        return try {
            val resp = botApi.regenerateBotToken(botId)
            if (resp.apiStatus == 200 && resp.botToken != null) Result.success(resp.botToken)
            else Result.failure(BotApiException(resp.errorCode ?: 0, resp.errorMessage ?: "Failed to regenerate token"))
        } catch (e: Exception) {
            Log.e(TAG, "regenerateToken error", e); Result.failure(e)
        }
    }

    // ── RSS Feeds ─────────────────────────────────────────────────────────────

    suspend fun getRssFeeds(accessToken: String, botId: String): Result<List<RssFeed>> {
        return try {
            val resp = botApi.getRssFeeds(botId)
            if (resp.apiStatus == 200) Result.success(resp.feeds ?: emptyList())
            else Result.failure(BotApiException(0, resp.errorMessage ?: "Failed"))
        } catch (e: Exception) {
            Log.e(TAG, "getRssFeeds", e); Result.failure(e)
        }
    }

    suspend fun addRssFeed(
        accessToken: String, botId: String, feedUrl: String, chatId: String,
        feedName: String? = null, intervalMinutes: Int = 30,
        maxItems: Int = 5, includeImage: Boolean = true, includeDesc: Boolean = true
    ): Result<RssFeed> {
        return try {
            val resp = botApi.addRssFeed(
                botId, feedUrl, chatId, feedName,
                intervalMinutes, maxItems,
                if (includeImage) 1 else 0, if (includeDesc) 1 else 0
            )
            if (resp.apiStatus == 200 && resp.feed != null) Result.success(resp.feed)
            else Result.failure(BotApiException(0, resp.errorMessage ?: "Failed"))
        } catch (e: Exception) {
            Log.e(TAG, "addRssFeed", e); Result.failure(e)
        }
    }

    suspend fun toggleRssFeed(accessToken: String, botId: String, feedId: Int, isActive: Boolean): Result<Unit> {
        return try {
            val resp = botApi.updateRssFeed(botId, feedId, isActive = if (isActive) 1 else 0)
            if (resp.apiStatus == 200) Result.success(Unit)
            else Result.failure(BotApiException(0, resp.errorMessage ?: "Failed"))
        } catch (e: Exception) {
            Log.e(TAG, "toggleRssFeed", e); Result.failure(e)
        }
    }

    suspend fun deleteRssFeed(accessToken: String, botId: String, feedId: Int): Result<Unit> {
        return try {
            val resp = botApi.deleteRssFeed(botId, feedId)
            if (resp.apiStatus == 200) Result.success(Unit)
            else Result.failure(BotApiException(0, resp.errorMessage ?: "Failed"))
        } catch (e: Exception) {
            Log.e(TAG, "deleteRssFeed", e); Result.failure(e)
        }
    }
}

data class BotSearchResult(
    val bots: List<Bot>,
    val categories: List<BotCategory>,
    val total: Int
)

class BotApiException(val errorCode: Int, message: String) : Exception(message)

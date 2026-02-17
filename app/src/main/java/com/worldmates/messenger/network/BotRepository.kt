package com.worldmates.messenger.network

import android.util.Log
import com.worldmates.messenger.data.model.*

/**
 * Repository для Bot API операцій.
 * Обгортка над BotApi з обробкою помилок та кешуванням.
 */
class BotRepository(
    private val botApi: BotApi = RetrofitClient.botApiService
) {
    companion object {
        private const val TAG = "BotRepository"
    }

    // ==================== BOT MANAGEMENT ====================

    /**
     * Створити нового бота
     */
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
            val response = botApi.createBot(
                accessToken = accessToken,
                username = username,
                displayName = displayName,
                description = description,
                about = about,
                category = category,
                canJoinGroups = if (canJoinGroups) 1 else 0,
                isPublic = if (isPublic) 1 else 0
            )
            if (response.apiStatus == 200 && response.bot != null) {
                Result.success(response.bot)
            } else {
                Result.failure(BotApiException(response.errorCode ?: 0, response.errorMessage ?: "Failed to create bot"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "createBot error", e)
            Result.failure(e)
        }
    }

    /**
     * Отримати список моїх ботів
     */
    suspend fun getMyBots(
        accessToken: String,
        limit: Int = 20,
        offset: Int = 0
    ): Result<List<Bot>> {
        return try {
            val response = botApi.getMyBots(accessToken, limit = limit, offset = offset)
            if (response.apiStatus == 200) {
                Result.success(response.bots ?: emptyList())
            } else {
                Result.failure(BotApiException(response.errorCode ?: 0, response.errorMessage ?: "Failed to get bots"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getMyBots error", e)
            Result.failure(e)
        }
    }

    /**
     * Оновити налаштування бота
     */
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
            val response = botApi.updateBot(
                accessToken = accessToken,
                botId = botId,
                displayName = displayName,
                description = description,
                about = about,
                category = category,
                isPublic = isPublic?.let { if (it) 1 else 0 },
                canJoinGroups = canJoinGroups?.let { if (it) 1 else 0 }
            )
            if (response.apiStatus == 200) {
                Result.success(Unit)
            } else {
                Result.failure(BotApiException(response.errorCode ?: 0, response.errorMessage ?: "Failed to update bot"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateBot error", e)
            Result.failure(e)
        }
    }

    /**
     * Видалити бота
     */
    suspend fun deleteBot(accessToken: String, botId: String): Result<Unit> {
        return try {
            val response = botApi.deleteBot(accessToken, botId = botId)
            if (response.apiStatus == 200) {
                Result.success(Unit)
            } else {
                Result.failure(BotApiException(response.errorCode ?: 0, response.errorMessage ?: "Failed to delete bot"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "deleteBot error", e)
            Result.failure(e)
        }
    }

    /**
     * Перегенерувати токен бота
     */
    suspend fun regenerateToken(accessToken: String, botId: String): Result<String> {
        return try {
            val response = botApi.regenerateBotToken(accessToken, botId = botId)
            if (response.apiStatus == 200 && response.botToken != null) {
                Result.success(response.botToken)
            } else {
                Result.failure(BotApiException(response.errorCode ?: 0, response.errorMessage ?: "Failed to regenerate token"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "regenerateToken error", e)
            Result.failure(e)
        }
    }

    // ==================== BOT DISCOVERY ====================

    /**
     * Отримати інформацію про бота за ID або username
     */
    suspend fun getBotInfo(
        accessToken: String,
        botId: String? = null,
        username: String? = null
    ): Result<Bot> {
        return try {
            val response = botApi.getBotInfo(accessToken, botId = botId, username = username)
            if (response.apiStatus == 200 && response.bot != null) {
                Result.success(response.bot)
            } else {
                Result.failure(BotApiException(response.errorCode ?: 0, response.errorMessage ?: "Bot not found"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getBotInfo error", e)
            Result.failure(e)
        }
    }

    /**
     * Пошук ботів у каталозі
     */
    suspend fun searchBots(
        accessToken: String,
        query: String? = null,
        category: String? = null,
        limit: Int = 20,
        offset: Int = 0
    ): Result<BotSearchResult> {
        return try {
            val response = botApi.searchBots(
                accessToken = accessToken,
                query = query,
                category = category,
                limit = limit,
                offset = offset
            )
            if (response.apiStatus == 200) {
                Result.success(BotSearchResult(
                    bots = response.bots ?: emptyList(),
                    categories = response.categories ?: emptyList(),
                    total = response.total
                ))
            } else {
                Result.failure(BotApiException(response.errorCode ?: 0, response.errorMessage ?: "Search failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "searchBots error", e)
            Result.failure(e)
        }
    }

    /**
     * Отримати команди бота
     */
    suspend fun getBotCommands(accessToken: String, botId: String): Result<List<BotCommand>> {
        return try {
            val response = botApi.getBotCommands(accessToken, botId = botId)
            if (response.apiStatus == 200) {
                Result.success(response.commands ?: emptyList())
            } else {
                Result.failure(BotApiException(response.errorCode ?: 0, response.errorMessage ?: "Failed to get commands"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getBotCommands error", e)
            Result.failure(e)
        }
    }
}

/**
 * Результат пошуку ботів
 */
data class BotSearchResult(
    val bots: List<Bot>,
    val categories: List<BotCategory>,
    val total: Int
)

/**
 * Виняток Bot API
 */
class BotApiException(val errorCode: Int, message: String) : Exception(message)

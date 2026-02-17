package com.worldmates.messenger.data.repository

import android.content.Context
import android.util.Log
import com.worldmates.messenger.data.local.AppDatabase
import com.worldmates.messenger.data.local.entity.Draft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * 📝 DraftRepository - управление черновиками сообщений
 *
 * Функции:
 * - Автоматическое сохранение черновиков
 * - Загрузка черновика при открытии чата
 * - Удаление черновика после отправки
 * - Очистка старых черновиков
 */
class DraftRepository private constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "DraftRepository"
        private const val DRAFT_EXPIRY_DAYS = 365L  // 1 год

        @Volatile
        private var instance: DraftRepository? = null

        fun getInstance(context: Context): DraftRepository {
            return instance ?: synchronized(this) {
                instance ?: DraftRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val database = AppDatabase.getInstance(context)
    private val draftDao = database.draftDao()

    /**
     * Получить черновик для чата
     */
    suspend fun getDraft(chatId: Long): Draft? = withContext(Dispatchers.IO) {
        try {
            draftDao.getDraft(chatId)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting draft for chat $chatId", e)
            null
        }
    }

    /**
     * Получить черновик как Flow (реактивно)
     */
    fun getDraftFlow(chatId: Long): Flow<Draft?> {
        return draftDao.getDraftFlow(chatId)
    }

    /**
     * Сохранить черновик
     */
    suspend fun saveDraft(
        chatId: Long,
        text: String,
        chatType: String,
        replyToMessageId: Long? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (text.isBlank()) {
                // Если текст пустой - удаляем черновик
                deleteDraft(chatId)
                return@withContext Result.success(Unit)
            }

            val draft = Draft(
                chatId = chatId,
                text = text,
                chatType = chatType,
                updatedAt = System.currentTimeMillis(),
                replyToMessageId = replyToMessageId
            )

            draftDao.insertOrUpdate(draft)
            Log.d(TAG, "✅ Draft saved for chat $chatId: ${text.take(50)}...")

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving draft for chat $chatId", e)
            Result.failure(e)
        }
    }

    /**
     * Удалить черновик для чата
     */
    suspend fun deleteDraft(chatId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            draftDao.delete(chatId)
            Log.d(TAG, "🗑️ Draft deleted for chat $chatId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error deleting draft for chat $chatId", e)
            Result.failure(e)
        }
    }

    /**
     * Получить все черновики
     */
    fun getAllDrafts(): Flow<List<Draft>> {
        return draftDao.getAllDrafts()
    }

    /**
     * Удалить все черновики
     */
    suspend fun deleteAllDrafts(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            draftDao.deleteAll()
            Log.d(TAG, "🗑️ All drafts deleted")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error deleting all drafts", e)
            Result.failure(e)
        }
    }

    /**
     * Очистить старые черновики (> 1 года)
     */
    suspend fun cleanupOldDrafts(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val expiryTimestamp = System.currentTimeMillis() -
                    TimeUnit.DAYS.toMillis(DRAFT_EXPIRY_DAYS)

            draftDao.deleteOlderThan(expiryTimestamp)
            Log.d(TAG, "🧹 Old drafts cleaned up (older than $DRAFT_EXPIRY_DAYS days)")

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cleaning up old drafts", e)
            Result.failure(e)
        }
    }

    /**
     * Проверить, есть ли черновик для чата
     */
    suspend fun hasDraft(chatId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            draftDao.getDraft(chatId) != null
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if draft exists for chat $chatId", e)
            false
        }
    }
}

package com.worldmates.messenger.data.repository

import android.content.Context
import android.util.Log
import com.worldmates.messenger.data.local.AppDatabase
import com.worldmates.messenger.data.local.entity.CachedMessage
import com.worldmates.messenger.data.model.Message
import com.worldmates.messenger.network.NodeRetrofitClient
import com.worldmates.messenger.utils.DecryptionUtility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 📦 CLOUD BACKUP: Repository для синхронизации сообщений
 *
 * Функции:
 * - Загрузка полной истории сообщений с сервера
 * - Кэширование сообщений локально
 * - Синхронизация изменений
 * - Офлайн доступ к сообщениям
 */
class BackupRepository(private val context: Context) {

    private val database = AppDatabase.getInstance(context)
    private val messageDao = database.messageDao()
    private val nodeApi = NodeRetrofitClient.api

    private val TAG = "BackupRepository"

    // ==================== СИНХРОНИЗАЦИЯ С ОБЛАКОМ ====================

    /**
     * Загрузить всю историю сообщений для конкретного чата
     * @param recipientId ID получателя (для личных чатов)
     * @param chatType "user" или "group"
     * @return Количество загруженных сообщений
     */
    suspend fun syncFullHistory(
        recipientId: Long,
        chatType: String = CachedMessage.CHAT_TYPE_USER
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📦 Starting full history sync for chat: $recipientId ($chatType)")

            // 1. Получаем количество сообщений для прогресс-бара
            val countResponse = nodeApi.getChatMessageCount(recipientId = recipientId)

            val totalMessages = countResponse.totalMessages
            Log.d(TAG, "📊 Total messages to sync: $totalMessages")

            // 2. Загружаем всю историю
            val response = nodeApi.getMessages(recipientId = recipientId, limit = 10000)

            if (response.apiStatus == 200) {
                val messages = response.messages ?: emptyList()
                Log.d(TAG, "✅ Received ${messages.size} messages from server")

                // 3. Конвертируем Message → CachedMessage
                val cachedMessages = messages.mapNotNull { message ->
                    convertToCachedMessage(message, recipientId, chatType)
                }

                // 4. Сохраняем в локальную БД
                messageDao.insertMessages(cachedMessages)
                Log.d(TAG, "💾 Saved ${cachedMessages.size} messages to local cache")

                Result.success(cachedMessages.size)
            } else {
                Result.failure(Exception("API error: ${response.apiStatus}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Sync failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Получить количество сообщений в чате (для прогресс-бара)
     */
    suspend fun getMessageCount(recipientId: Long): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val response = nodeApi.getChatMessageCount(recipientId = recipientId)

            if (response.apiStatus == 200) {
                Result.success(response.totalMessages)
            } else {
                Result.failure(Exception("API error: ${response.apiStatus}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== ЛОКАЛЬНЫЙ ДОСТУП К КЭШУ ====================

    /**
     * Получить сообщения из локального кэша (Flow для Compose)
     */
    fun getCachedMessages(chatId: Long, chatType: String): Flow<List<CachedMessage>> {
        return messageDao.getMessagesForChat(chatId, chatType)
    }

    /**
     * Получить последние N сообщений из кэша
     */
    suspend fun getRecentCachedMessages(
        chatId: Long,
        chatType: String,
        limit: Int = 50
    ): List<CachedMessage> = withContext(Dispatchers.IO) {
        messageDao.getRecentMessages(chatId, chatType, limit)
    }

    /**
     * Поиск сообщений в кэше
     */
    suspend fun searchCachedMessages(
        chatId: Long,
        chatType: String,
        query: String
    ): List<CachedMessage> = withContext(Dispatchers.IO) {
        messageDao.searchMessagesInChat(chatId, chatType, query)
    }

    /**
     * Глобальный поиск по всем кэшированным сообщениям
     */
    suspend fun searchAllCachedMessages(query: String): List<CachedMessage> =
        withContext(Dispatchers.IO) {
            messageDao.searchAllMessages(query)
        }

    // ==================== УПРАВЛЕНИЕ КЭШЕМ ====================

    /**
     * Очистить кэш для конкретного чата
     */
    suspend fun clearChatCache(chatId: Long, chatType: String) = withContext(Dispatchers.IO) {
        messageDao.clearChatCache(chatId, chatType)
        Log.d(TAG, "🗑️ Cleared cache for chat: $chatId")
    }

    /**
     * Очистить старые сообщения (старше N дней)
     * @param daysOld Количество дней (по умолчанию 30)
     */
    suspend fun clearOldMessages(daysOld: Int = 30) = withContext(Dispatchers.IO) {
        val olderThan = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L)
        messageDao.deleteOldMessages(olderThan)
        Log.d(TAG, "🗑️ Cleared messages older than $daysOld days")
    }

    /**
     * Получить размер кэша
     */
    suspend fun getCacheSize(): Int = withContext(Dispatchers.IO) {
        messageDao.getCacheSize()
    }

    /**
     * Получить количество непрочитанных сообщений
     */
    suspend fun getUnreadCount(chatId: Long, chatType: String): Int = withContext(Dispatchers.IO) {
        messageDao.getUnreadCount(chatId, chatType)
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

    /**
     * Конвертировать Message (из API) в CachedMessage (для локальной БД)
     */
    private fun convertToCachedMessage(
        message: Message,
        chatId: Long,
        chatType: String
    ): CachedMessage? {
        return try {
            // Расшифровываем текст сразу для быстрого доступа
            val decryptedText = if (message.encryptedText != null && message.iv != null && message.tag != null) {
                DecryptionUtility.decryptMessage(
                    encryptedText = message.encryptedText,
                    timestamp = message.timeStamp,
                    iv = message.iv,
                    tag = message.tag,
                    cipherVersion = message.cipherVersion ?: 2
                )
            } else {
                message.encryptedText // Если нет шифрования - используем как есть
            }

            CachedMessage(
                id = message.id,
                chatId = chatId,
                chatType = chatType,
                fromId = message.fromId,
                toId = message.toId,
                groupId = message.groupId,
                encryptedText = message.encryptedText,
                iv = message.iv,
                tag = message.tag,
                cipherVersion = message.cipherVersion,
                decryptedText = decryptedText,
                timestamp = message.timeStamp,
                mediaUrl = message.mediaUrl,
                mediaFileName = message.mediaFileName,
                type = message.type ?: "text",
                mediaType = message.mediaType,
                mediaDuration = message.mediaDuration,
                mediaSize = message.mediaSize,
                localMediaPath = null, // Заполним при скачивании медиа
                senderName = message.senderName,
                senderAvatar = message.senderAvatar,
                isEdited = message.isEdited,
                editedTime = message.editedTime,
                isDeleted = message.isDeleted,
                replyToId = message.replyToId,
                replyToText = message.replyToText,
                isRead = message.isRead,
                readAt = message.readAt,
                isSynced = true, // Пришло с сервера
                syncedAt = System.currentTimeMillis(),
                cachedAt = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to convert message ${message.id}: ${e.message}")
            null
        }
    }

    /**
     * Добавить локальное сообщение в кэш (перед отправкой на сервер)
     */
    suspend fun addLocalMessage(message: CachedMessage) = withContext(Dispatchers.IO) {
        messageDao.insertMessage(message)
    }

    /**
     * Обновить статус синхронизации сообщения
     */
    suspend fun updateSyncStatus(messageId: Long, isSynced: Boolean) = withContext(Dispatchers.IO) {
        messageDao.updateSyncStatus(messageId, isSynced)
    }

    /**
     * Пометить сообщения чата как прочитанные
     */
    suspend fun markChatAsRead(chatId: Long, chatType: String) = withContext(Dispatchers.IO) {
        messageDao.markChatAsRead(chatId, chatType)
    }
}
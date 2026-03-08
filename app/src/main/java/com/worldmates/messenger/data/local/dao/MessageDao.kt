package com.worldmates.messenger.data.local.dao

import androidx.room.*
import com.worldmates.messenger.data.local.entity.CachedMessage
import kotlinx.coroutines.flow.Flow

/**
 * 📦 MessageDao - DAO для работы с кэшированными сообщениями
 *
 * Предоставляет методы для:
 * - Сохранения сообщений из облака
 * - Получения сообщений для отображения
 * - Синхронизации с сервером
 * - Очистки старых сообщений
 */
@Dao
interface MessageDao {

    // ==================== ВСТАВКА ====================

    /**
     * Вставка одного сообщения
     * При конфликте (duplicate id) - заменяем
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: CachedMessage): Long

    /**
     * Вставка списка сообщений (для массовой загрузки истории)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<CachedMessage>)

    // ==================== ПОЛУЧЕНИЕ ====================

    /**
     * Получить все сообщения для конкретного чата
     * Сортировка по времени (новые внизу)
     * @param chatId ID чата (recipientId или groupId)
     * @param chatType "user" или "group"
     */
    @Query("""
        SELECT * FROM cached_messages
        WHERE chatId = :chatId AND chatType = :chatType AND isDeleted = 0
        ORDER BY timestamp ASC
    """)
    fun getMessagesForChat(chatId: Long, chatType: String): Flow<List<CachedMessage>>

    /**
     * Получить последние N сообщений для чата
     */
    @Query("""
        SELECT * FROM cached_messages
        WHERE chatId = :chatId AND chatType = :chatType AND isDeleted = 0
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    suspend fun getRecentMessages(chatId: Long, chatType: String, limit: Int): List<CachedMessage>

    /**
     * Получить сообщение по ID
     */
    @Query("SELECT * FROM cached_messages WHERE id = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: Long): CachedMessage?

    /**
     * Получить количество сообщений в чате
     */
    @Query("""
        SELECT COUNT(*) FROM cached_messages
        WHERE chatId = :chatId AND chatType = :chatType AND isDeleted = 0
    """)
    suspend fun getMessageCount(chatId: Long, chatType: String): Int

    /**
     * Получить количество несинхронизированных сообщений
     */
    @Query("SELECT COUNT(*) FROM cached_messages WHERE isSynced = 0")
    suspend fun getUnsyncedMessageCount(): Int

    /**
     * Получить несинхронизированные сообщения (для отправки на сервер)
     */
    @Query("""
        SELECT * FROM cached_messages
        WHERE isSynced = 0
        ORDER BY timestamp ASC
    """)
    suspend fun getUnsyncedMessages(): List<CachedMessage>

    /**
     * Поиск сообщений по тексту в конкретном чате
     */
    @Query("""
        SELECT * FROM cached_messages
        WHERE chatId = :chatId
        AND chatType = :chatType
        AND isDeleted = 0
        AND (decryptedText LIKE '%' || :query || '%' OR senderName LIKE '%' || :query || '%')
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    suspend fun searchMessagesInChat(
        chatId: Long,
        chatType: String,
        query: String,
        limit: Int = 100
    ): List<CachedMessage>

    /**
     * Глобальный поиск сообщений по всем чатам
     */
    @Query("""
        SELECT * FROM cached_messages
        WHERE isDeleted = 0
        AND (decryptedText LIKE '%' || :query || '%' OR senderName LIKE '%' || :query || '%')
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    suspend fun searchAllMessages(query: String, limit: Int = 100): List<CachedMessage>

    // ==================== ОБНОВЛЕНИЕ ====================

    /**
     * Обновить сообщение
     */
    @Update
    suspend fun updateMessage(message: CachedMessage)

    /**
     * Пометить сообщение как прочитанное
     */
    @Query("""
        UPDATE cached_messages
        SET isRead = 1, readAt = :readAt
        WHERE id = :messageId
    """)
    suspend fun markAsRead(messageId: Long, readAt: Long = System.currentTimeMillis())

    /**
     * Пометить все сообщения чата как прочитанные
     */
    @Query("""
        UPDATE cached_messages
        SET isRead = 1, readAt = :readAt
        WHERE chatId = :chatId AND chatType = :chatType AND isRead = 0
    """)
    suspend fun markChatAsRead(chatId: Long, chatType: String, readAt: Long = System.currentTimeMillis())

    /**
     * Обновить статус синхронизации
     */
    @Query("""
        UPDATE cached_messages
        SET isSynced = :isSynced, syncedAt = :syncedAt
        WHERE id = :messageId
    """)
    suspend fun updateSyncStatus(
        messageId: Long,
        isSynced: Boolean,
        syncedAt: Long = System.currentTimeMillis()
    )

    /**
     * Сохранить расшифрованный текст
     */
    @Query("UPDATE cached_messages SET decryptedText = :decryptedText WHERE id = :messageId")
    suspend fun updateDecryptedText(messageId: Long, decryptedText: String)

    /**
     * Сохранить путь к локально скачанному медиа
     */
    @Query("UPDATE cached_messages SET localMediaPath = :localPath WHERE id = :messageId")
    suspend fun updateLocalMediaPath(messageId: Long, localPath: String)

    /**
     * Сохранить путь к превью (thumbnail)
     */
    @Query("UPDATE cached_messages SET thumbnailPath = :thumbnailPath WHERE id = :messageId")
    suspend fun updateThumbnailPath(messageId: Long, thumbnailPath: String)

    /**
     * Обновить состояние загрузки медиа
     */
    @Query("UPDATE cached_messages SET mediaLoadingState = :state WHERE id = :messageId")
    suspend fun updateMediaLoadingState(messageId: Long, state: String)

    // ==================== УДАЛЕНИЕ ====================

    /**
     * Мягкое удаление сообщения (isDeleted = true)
     */
    @Query("UPDATE cached_messages SET isDeleted = 1 WHERE id = :messageId")
    suspend fun softDeleteMessage(messageId: Long)

    /**
     * Жесткое удаление сообщения из БД
     */
    @Query("DELETE FROM cached_messages WHERE id = :messageId")
    suspend fun hardDeleteMessage(messageId: Long)

    /**
     * Очистить весь кэш для конкретного чата
     */
    @Query("DELETE FROM cached_messages WHERE chatId = :chatId AND chatType = :chatType")
    suspend fun clearChatCache(chatId: Long, chatType: String)

    /**
     * Очистить старые сообщения (старше N дней)
     * Используется для экономии места
     */
    @Query("""
        DELETE FROM cached_messages
        WHERE cachedAt < :olderThan AND isSynced = 1
    """)
    suspend fun deleteOldMessages(olderThan: Long)

    /**
     * Очистить весь кэш сообщений
     */
    @Query("DELETE FROM cached_messages")
    suspend fun clearAllCache()

    // ==================== СЕКРЕТНІ ЧАТИ ====================

    /**
     * Видалити повідомлення з минулим таймером самознищення.
     * Викликається фоновим воркером кожні 15 хвилин.
     * @param nowMs поточний час у мілісекундах (System.currentTimeMillis())
     */
    @Query("""
        DELETE FROM cached_messages
        WHERE isSecret = 1 AND destroyAt IS NOT NULL AND destroyAt <= :nowMs
    """)
    suspend fun deleteExpiredSecretMessages(nowMs: Long)

    /**
     * Отримати всі секретні повідомлення для конкретного чату.
     */
    @Query("""
        SELECT * FROM cached_messages
        WHERE chatId = :chatId AND chatType = :chatType AND isSecret = 1 AND isDeleted = 0
        ORDER BY timestamp ASC
    """)
    fun getSecretMessagesForChat(chatId: Long, chatType: String): kotlinx.coroutines.flow.Flow<List<CachedMessage>>

    /**
     * Кількість прострочених секретних повідомлень (для діагностики).
     */
    @Query("""
        SELECT COUNT(*) FROM cached_messages
        WHERE isSecret = 1 AND destroyAt IS NOT NULL AND destroyAt <= :nowMs
    """)
    suspend fun countExpiredSecretMessages(nowMs: Long): Int

    // ==================== СТАТИСТИКА ====================

    /**
     * Получить размер кэша (количество сообщений)
     */
    @Query("SELECT COUNT(*) FROM cached_messages")
    suspend fun getCacheSize(): Int

    /**
     * Получить количество непрочитанных сообщений в чате
     */
    @Query("""
        SELECT COUNT(*) FROM cached_messages
        WHERE chatId = :chatId AND chatType = :chatType AND isRead = 0 AND isDeleted = 0
    """)
    suspend fun getUnreadCount(chatId: Long, chatType: String): Int

    /**
     * Получить общее количество непрочитанных сообщений
     */
    @Query("SELECT COUNT(*) FROM cached_messages WHERE isRead = 0 AND isDeleted = 0")
    suspend fun getTotalUnreadCount(): Int
}
package com.worldmates.messenger.data.local.dao

import androidx.room.*
import com.worldmates.messenger.data.local.entity.Draft
import kotlinx.coroutines.flow.Flow

/**
 * 📝 DraftDao - операции с черновиками
 */
@Dao
interface DraftDao {
    /**
     * Получить черновик для чата
     */
    @Query("SELECT * FROM drafts WHERE chatId = :chatId LIMIT 1")
    suspend fun getDraft(chatId: Long): Draft?

    /**
     * Получить черновик как Flow (реактивно)
     */
    @Query("SELECT * FROM drafts WHERE chatId = :chatId LIMIT 1")
    fun getDraftFlow(chatId: Long): Flow<Draft?>

    /**
     * Получить все черновики
     */
    @Query("SELECT * FROM drafts ORDER BY updatedAt DESC")
    fun getAllDrafts(): Flow<List<Draft>>

    /**
     * Сохранить или обновить черновик
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(draft: Draft)

    /**
     * Удалить черновик для чата
     */
    @Query("DELETE FROM drafts WHERE chatId = :chatId")
    suspend fun delete(chatId: Long)

    /**
     * Удалить все черновики
     */
    @Query("DELETE FROM drafts")
    suspend fun deleteAll()

    /**
     * Удалить старые черновики (> 30 дней)
     */
    @Query("DELETE FROM drafts WHERE updatedAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
}

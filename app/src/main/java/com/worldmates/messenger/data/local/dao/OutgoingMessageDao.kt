package com.worldmates.messenger.data.local.dao

import androidx.room.*
import com.worldmates.messenger.data.local.entity.OutgoingMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface OutgoingMessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(message: OutgoingMessage): Long

    /** Всі pending + failed (sorted chronologically) — для flush після reconnect */
    @Query("""
        SELECT * FROM outgoing_messages
        WHERE status IN ('pending', 'failed') AND retryCount < ${OutgoingMessage.MAX_RETRIES}
        ORDER BY createdAt ASC
    """)
    suspend fun getPendingMessages(): List<OutgoingMessage>

    /** Flow для UI — показувати "не відправлено" індикатор */
    @Query("SELECT COUNT(*) FROM outgoing_messages WHERE status != 'failed' OR retryCount < ${OutgoingMessage.MAX_RETRIES}")
    fun getPendingCountFlow(): Flow<Int>

    @Query("UPDATE outgoing_messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE outgoing_messages SET status = :status, retryCount = retryCount + 1 WHERE id = :id")
    suspend fun incrementRetry(id: Long, status: String = OutgoingMessage.STATUS_FAILED)

    @Query("DELETE FROM outgoing_messages WHERE id = :id")
    suspend fun delete(id: Long)

    /** Очистити всі назавжди failed (retryCount >= MAX_RETRIES) */
    @Query("DELETE FROM outgoing_messages WHERE retryCount >= ${OutgoingMessage.MAX_RETRIES}")
    suspend fun clearFailed()

    @Query("DELETE FROM outgoing_messages")
    suspend fun clearAll()
}

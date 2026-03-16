package com.worldmates.messenger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.worldmates.messenger.data.local.entity.SignalPlaintextCache

@Dao
interface SignalPlaintextCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: SignalPlaintextCache)

    @Query("SELECT plaintext FROM signal_plaintext_cache WHERE msgId = :msgId LIMIT 1")
    suspend fun get(msgId: Long): String?

    @Query("DELETE FROM signal_plaintext_cache WHERE msgId = :msgId")
    suspend fun delete(msgId: Long)

    @Query("DELETE FROM signal_plaintext_cache WHERE cachedAt < :olderThanMs")
    suspend fun evictOlderThan(olderThanMs: Long)

    @Query("DELETE FROM signal_plaintext_cache")
    suspend fun clearAll()
}

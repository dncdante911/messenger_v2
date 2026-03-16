package com.worldmates.messenger.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Plaintext cache for Signal (Double Ratchet + Sender Key) decrypted messages.
 *
 * After a message is decrypted using one-time ratchet keys, the plaintext is
 * stored here so it can be shown again without re-decrypting (which would fail,
 * since ratchet keys are consumed on first use).
 *
 * This table is included in Android Auto Backup → survives app reinstall on the
 * same Google account and device transfers.
 */
@Entity(
    tableName = "signal_plaintext_cache",
    indices = [Index(value = ["msgId"], unique = true)]
)
data class SignalPlaintextCache(
    @PrimaryKey
    val msgId: Long,
    val plaintext: String,
    val cachedAt: Long = System.currentTimeMillis()
)

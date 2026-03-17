package com.worldmates.messenger.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * OutgoingMessage — черга повідомлень для відправки в офлайн-режимі.
 *
 * Повідомлення потрапляє сюди якщо сокет недоступний у момент відправки.
 * Після відновлення з'єднання OutgoingMessageQueue читає цю таблицю
 * і відправляє всі pending записи в порядку [createdAt] ASC.
 */
@Entity(
    tableName = "outgoing_messages",
    indices = [Index(value = ["status", "createdAt"], name = "idx_status_created")]
)
data class OutgoingMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** "user" або "group" */
    val chatType: String,

    /** recipientId для особистих, groupId для групових */
    val chatId: Long,

    /** Відкритий текст (шифрування відбувається безпосередньо перед відправкою) */
    val plaintext: String,

    /** ID повідомлення-цитати (0 = немає) */
    val replyId: Long = 0,

    /** Стікери (порожньо якщо немає) */
    val stickers: String = "",

    /** Unix-час створення (мс) — визначає порядок відправки */
    val createdAt: Long = System.currentTimeMillis(),

    /** Кількість невдалих спроб відправки */
    val retryCount: Int = 0,

    /** "pending" | "sending" | "failed" */
    val status: String = STATUS_PENDING
) {
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_SENDING = "sending"
        const val STATUS_FAILED  = "failed"

        const val MAX_RETRIES = 5
        const val CHAT_TYPE_USER  = "user"
        const val CHAT_TYPE_GROUP = "group"
    }
}

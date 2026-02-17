package com.worldmates.messenger.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 📝 Draft - черновик сообщения
 *
 * Локальное хранилище несохраненных сообщений.
 * Автоматически сохраняется каждые 5 секунд при наборе текста.
 */
@Entity(tableName = "drafts")
data class Draft(
    /**
     * ID чата (recipientId для личных чатов, groupId для групп)
     * Используется как primary key
     */
    @PrimaryKey
    val chatId: Long,

    /**
     * Текст черновика
     */
    val text: String,

    /**
     * Тип чата: "user" или "group"
     */
    val chatType: String,

    /**
     * Timestamp последнего обновления
     */
    val updatedAt: Long = System.currentTimeMillis(),

    /**
     * ID сообщения, на которое отвечаем (reply)
     * Null если не reply
     */
    val replyToMessageId: Long? = null
) {
    companion object {
        const val CHAT_TYPE_USER = "user"
        const val CHAT_TYPE_GROUP = "group"
    }
}

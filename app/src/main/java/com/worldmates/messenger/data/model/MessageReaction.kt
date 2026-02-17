package com.worldmates.messenger.data.model

import com.google.gson.annotations.SerializedName

/**
 * Модель реакції на повідомлення
 */
data class MessageReaction(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("message_id") val messageId: Long,
    @SerializedName("user_id") val userId: Long,
    @SerializedName("reaction") val reaction: String,  // Емоджі: ❤️, 👍, 😂, 😮, 😢, 🙏
    @SerializedName("created_at") val createdAt: String? = null
)

/**
 * Група реакцій (згруповані по емоджі)
 */
data class ReactionGroup(
    val emoji: String,
    val count: Int,
    val userIds: List<Long>,
    val hasMyReaction: Boolean  // Чи поставив поточний користувач
)

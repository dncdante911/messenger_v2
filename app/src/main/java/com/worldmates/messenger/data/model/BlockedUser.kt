package com.worldmates.messenger.data.model

import com.google.gson.annotations.SerializedName

/**
 * Модель заблокированного пользователя
 */
data class BlockedUser(
    @SerializedName("user_id") val userId: Long,
    @SerializedName("username") val username: String,
    @SerializedName("first_name") val firstName: String?,
    @SerializedName("last_name") val lastName: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("avatar") val avatar: String?,
    @SerializedName("about") val about: String?,
    @SerializedName("verified") val verified: Int = 0,
    @SerializedName("is_verified") val isVerified: Boolean = false,
    @SerializedName("is_pro") val isPro: Boolean = false,
    @SerializedName("lastseen") val lastSeen: Long?,
    @SerializedName("lastseen_time_text") val lastSeenTimeText: String?,
    @SerializedName("gender") val gender: String?,
    @SerializedName("gender_text") val genderText: String?
)

/**
 * Response для операции блокировки/разблокировки
 */
data class BlockActionResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("block_status") val blockStatus: String,
    @SerializedName("message") val message: String?,
    @SerializedName("error_code") val errorCode: Int?,
    @SerializedName("error_message") val errorMessage: String?
)

/**
 * Response для получения списка заблокированных пользователей
 */
data class GetBlockedUsersResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("blocked_users") val blockedUsers: List<BlockedUser>,
    @SerializedName("total_blocked") val totalBlocked: Int,
    @SerializedName("error_code") val errorCode: Int?,
    @SerializedName("error_message") val errorMessage: String?
)

/**
 * Response для проверки статуса блокировки
 */
data class CheckBlockStatusResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("is_blocked") val isBlocked: Boolean,
    @SerializedName("blocked_by_me") val blockedByMe: Boolean,
    @SerializedName("blocked_me") val blockedMe: Boolean,
    @SerializedName("can_message") val canMessage: Boolean,
    @SerializedName("can_call") val canCall: Boolean,
    @SerializedName("error_code") val errorCode: Int?,
    @SerializedName("error_message") val errorMessage: String?
)

/**
 * Статусы блокировки
 */
enum class BlockStatus {
    BLOCKED,
    UNBLOCKED,
    ALREADY_BLOCKED,
    ALREADY_UNBLOCKED,
    INVALID,
    ERROR
}

/**
 * Расширение для преобразования строкового статуса в enum
 */
fun String.toBlockStatus(): BlockStatus {
    return when (this) {
        "blocked" -> BlockStatus.BLOCKED
        "unblocked", "un-blocked" -> BlockStatus.UNBLOCKED
        "already_blocked" -> BlockStatus.ALREADY_BLOCKED
        "already_unblocked" -> BlockStatus.ALREADY_UNBLOCKED
        "invalid" -> BlockStatus.INVALID
        else -> BlockStatus.ERROR
    }
}
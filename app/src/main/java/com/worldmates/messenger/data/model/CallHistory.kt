package com.worldmates.messenger.data.model

import com.google.gson.annotations.SerializedName

// ==================== CALL HISTORY MODELS ====================

/**
 * Запис історії дзвінка
 */
data class CallHistoryItem(
    @SerializedName("id") val id: Long,
    @SerializedName("call_category") val callCategory: String, // "personal" or "group"
    @SerializedName("call_type") val callType: String, // "audio" or "video"
    @SerializedName("status") val status: String, // "ringing", "connected", "ended", "missed", "rejected", "failed"
    @SerializedName("direction") val direction: String, // "incoming" or "outgoing"
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("accepted_at") val acceptedAt: String? = null,
    @SerializedName("ended_at") val endedAt: String? = null,
    @SerializedName("duration") val duration: Int = 0,
    @SerializedName("timestamp") val timestamp: Long = 0,
    @SerializedName("other_user") val otherUser: CallUser? = null,
    @SerializedName("group_data") val groupData: CallGroupData? = null
) {
    val isVideoCall: Boolean get() = callType == "video"
    val isGroupCall: Boolean get() = callCategory == "group"
    val isIncoming: Boolean get() = direction == "incoming"
    val isOutgoing: Boolean get() = direction == "outgoing"
    val isMissed: Boolean get() = status == "missed" || status == "rejected"
    val isConnected: Boolean get() = status == "connected" || status == "ended"

    /**
     * Назва для відображення в UI
     */
    val displayName: String
        get() = if (isGroupCall) {
            groupData?.groupName ?: "Груповий дзвінок"
        } else {
            otherUser?.displayName ?: "Невідомий"
        }

    /**
     * URL аватара
     */
    val avatarUrl: String?
        get() = if (isGroupCall) groupData?.avatar else otherUser?.avatar

    /**
     * Форматована тривалість
     */
    fun getFormattedDuration(): String {
        if (duration <= 0) return ""
        val minutes = duration / 60
        val seconds = duration % 60
        return if (minutes > 0) {
            "${minutes} хв ${seconds} с"
        } else {
            "${seconds} с"
        }
    }

    /**
     * Текст статусу для UI
     */
    fun getStatusText(): String {
        return when {
            isMissed && isIncoming -> "Пропущений"
            isMissed && isOutgoing -> "Скасований"
            status == "failed" -> "Не з'єднано"
            status == "ringing" -> "Виклик"
            isConnected && duration > 0 -> getFormattedDuration()
            isConnected -> "З'єднано"
            else -> status
        }
    }
}

/**
 * Дані іншого учасника дзвінка
 */
data class CallUser(
    @SerializedName("user_id") val userId: Long,
    @SerializedName("username") val username: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("verified") val verified: Int = 0
) {
    val displayName: String
        get() = name.ifBlank { username }

    val isVerified: Boolean
        get() = verified == 1
}

/**
 * Дані групи для групового дзвінка
 */
data class CallGroupData(
    @SerializedName("group_id") val groupId: Long,
    @SerializedName("group_name") val groupName: String = "",
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("max_participants") val maxParticipants: Int = 0
)

// ==================== API RESPONSE MODELS ====================

/**
 * Відповідь API - список дзвінків
 */
data class GetCallHistoryResponse(
    @SerializedName("api_status") val apiStatusRaw: Any? = null,
    @SerializedName("calls") val calls: List<CallHistoryItem>? = null,
    @SerializedName("total") val total: Int = 0,
    @SerializedName("offset") val offset: Int = 0,
    @SerializedName("limit") val limit: Int = 50,
    @SerializedName("error_message") val errorMessage: String? = null
) {
    val apiStatus: Int
        get() = when (apiStatusRaw) {
            is Number -> apiStatusRaw.toInt()
            is String -> apiStatusRaw.toIntOrNull() ?: 0
            else -> 0
        }

    val isSuccess: Boolean get() = apiStatus == 200
}

/**
 * Відповідь API - видалення/очистка
 */
data class CallHistoryActionResponse(
    @SerializedName("api_status") val apiStatusRaw: Any? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error_message") val errorMessage: String? = null
) {
    val apiStatus: Int
        get() = when (apiStatusRaw) {
            is Number -> apiStatusRaw.toInt()
            is String -> apiStatusRaw.toIntOrNull() ?: 0
            else -> 0
        }

    val isSuccess: Boolean get() = apiStatus == 200
}

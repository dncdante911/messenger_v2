package com.worldmates.messenger.data.model

data class AdminLog(
    val id: Long,
    val action: String,
    val adminId: Long,
    val adminName: String,
    val adminAvatar: String?,
    val targetUserId: Long?,
    val targetUserName: String?,
    val details: Map<String, String>?,
    val createdAt: String
) {
    fun getActionDescription(): String = when (action) {
        "add_member" -> "добавил участника"
        "remove_member" -> "удалил участника"
        "ban_user" -> "заблокировал пользователя"
        "unban_user" -> "разблокировал пользователя"
        "delete_message" -> "удалил сообщение"
        "pin_message" -> "закрепил сообщение"
        "unpin_message" -> "открепил сообщение"
        "change_title" -> "изменил название группы"
        "change_avatar" -> "изменил аватар группы"
        "change_description" -> "изменил описание группы"
        "change_role" -> "изменил роль участника"
        "change_settings" -> "изменил настройки группы"
        "enable_slow_mode" -> "включил медленный режим"
        "disable_slow_mode" -> "отключил медленный режим"
        else -> action.replace("_", " ")
    }
}

data class AdminLogsResponse(
    val status: Int,
    val logs: List<AdminLog>,
    val total: Int,
    val page: Int
)

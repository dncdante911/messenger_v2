package com.worldmates.messenger.data.model

import com.google.gson.annotations.SerializedName

/**
 * Options for the media auto-delete timer.
 *
 * Each option maps to the number of seconds after which the media file is
 * physically removed from the server. The message text is never deleted.
 *
 * @param seconds  Duration in seconds (0 = disabled).
 * @param label    Human-readable Russian/Ukrainian label shown in the dialog.
 */
enum class MediaAutoDeleteOption(val seconds: Long, val label: String) {
    NEVER(0,       "Никогда"),
    ONE_DAY(86400,       "1 день"),
    THREE_DAYS(259200,   "3 дня"),
    ONE_WEEK(604800,     "1 неделя"),
    TWO_WEEKS(1209600,   "2 недели"),
    ONE_MONTH(2592000,   "1 месяц");

    companion object {
        fun fromSeconds(seconds: Long): MediaAutoDeleteOption =
            values().find { it.seconds == seconds } ?: NEVER
    }
}

/**
 * Response from GET /api/node/chat/media-auto-delete-setting
 */
data class MediaAutoDeleteSettingResponse(
    @SerializedName("api_status")
    val apiStatus: Int,

    @SerializedName("seconds")
    val seconds: Long = 0L,

    @SerializedName("chat_id")
    val chatId: Long = 0L,

    @SerializedName("error_message")
    val errorMessage: String? = null
)

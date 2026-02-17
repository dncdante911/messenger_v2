package com.worldmates.messenger.data.model

import com.google.gson.annotations.SerializedName

/**
 * 📦 CLOUD BACKUP: Модель настроек автозагрузки медиа
 */
data class MediaSettings(
    @SerializedName("auto_download_photos")
    val autoDownloadPhotos: AutoDownloadMode = AutoDownloadMode.WIFI_ONLY,

    @SerializedName("auto_download_videos")
    val autoDownloadVideos: AutoDownloadMode = AutoDownloadMode.WIFI_ONLY,

    @SerializedName("auto_download_audio")
    val autoDownloadAudio: AutoDownloadMode = AutoDownloadMode.ALWAYS,

    @SerializedName("auto_download_documents")
    val autoDownloadDocuments: AutoDownloadMode = AutoDownloadMode.WIFI_ONLY,

    @SerializedName("compress_photos")
    val compressPhotos: Boolean = true,

    @SerializedName("compress_videos")
    val compressVideos: Boolean = true,

    @SerializedName("backup_enabled")
    val backupEnabled: Boolean = true,

    @SerializedName("last_backup_time")
    val lastBackupTime: Long? = null
) {
    /**
     * Режимы автозагрузки медиа
     */
    enum class AutoDownloadMode(val value: String) {
        @SerializedName("wifi_only")
        WIFI_ONLY("wifi_only"),

        @SerializedName("always")
        ALWAYS("always"),

        @SerializedName("never")
        NEVER("never");

        companion object {
            fun fromValue(value: String): AutoDownloadMode {
                return values().find { it.value == value } ?: WIFI_ONLY
            }
        }
    }
}

/**
 * 📦 Response для получения настроек медиа
 */
data class MediaSettingsResponse(
    @SerializedName("api_status")
    val apiStatus: Int,

    @SerializedName("settings")
    val settings: MediaSettings,

    @SerializedName("errors")
    val errors: Map<String, String>? = null
)

/**
 * 📦 Response для обновления настроек медиа
 */
data class UpdateMediaSettingsResponse(
    @SerializedName("api_status")
    val apiStatus: Int,

    @SerializedName("message")
    val message: String,

    @SerializedName("errors")
    val errors: Map<String, String>? = null
)

/**
 * 📦 Response для подсчета сообщений
 */
data class MessageCountResponse(
    @SerializedName("api_status")
    val apiStatus: Int,

    @SerializedName("total_messages")
    val totalMessages: Int,

    @SerializedName("errors")
    val errors: Map<String, String>? = null
)
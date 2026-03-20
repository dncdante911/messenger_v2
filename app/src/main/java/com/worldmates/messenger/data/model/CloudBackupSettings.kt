package com.worldmates.messenger.data.model

import com.google.gson.annotations.SerializedName

/**
 * 📦 CLOUD BACKUP v2: Расширенная модель настроек
 */
data class CloudBackupSettings(
    // ==================== АВТОЗАГРУЗКА МЕДИА ====================

    // Через мобильную сеть
    @SerializedName("mobile_photos")
    val mobilePhotos: Boolean = false,

    @SerializedName("mobile_videos")
    val mobileVideos: Boolean = false,

    @SerializedName("mobile_videos_limit")
    val mobileVideosLimit: Int = 10, // MB

    @SerializedName("mobile_files")
    val mobileFiles: Boolean = false,

    @SerializedName("mobile_files_limit")
    val mobileFilesLimit: Int = 1, // MB

    // Через Wi-Fi
    @SerializedName("wifi_photos")
    val wifiPhotos: Boolean = true,

    @SerializedName("wifi_videos")
    val wifiVideos: Boolean = true,

    @SerializedName("wifi_videos_limit")
    val wifiVideosLimit: Int = 15, // MB

    @SerializedName("wifi_files")
    val wifiFiles: Boolean = true,

    @SerializedName("wifi_files_limit")
    val wifiFilesLimit: Int = 3, // MB

    // В роуминге
    @SerializedName("roaming_photos")
    val roamingPhotos: Boolean = false,

    // ==================== СОХРАНЯТЬ В ГАЛЕРЕЕ ====================

    @SerializedName("save_to_gallery_private_chats")
    val saveToGalleryPrivateChats: Boolean = true,

    @SerializedName("save_to_gallery_groups")
    val saveToGalleryGroups: Boolean = false,

    @SerializedName("save_to_gallery_channels")
    val saveToGalleryChannels: Boolean = false,

    // ==================== СТРИМИНГ ====================

    @SerializedName("streaming_enabled")
    val streamingEnabled: Boolean = true,

    // ==================== УПРАВЛЕНИЕ КЭШЕМ ====================

    @SerializedName("cache_size_limit")
    val cacheSizeLimit: Long = 3 * 1024 * 1024 * 1024L, // 3GB по умолчанию

    @SerializedName("cache_time_limit")
    val cacheTimeLimit: Int = 30, // Хранить кэш N дней

    // ==================== CLOUD BACKUP ====================

    @SerializedName("backup_enabled")
    val backupEnabled: Boolean = true,

    @SerializedName("backup_provider")
    val backupProvider: BackupProvider = BackupProvider.LOCAL_SERVER,

    @SerializedName("auto_backup_on_login")
    val autoBackupOnLogin: Boolean = true,

    @SerializedName("backup_frequency")
    val backupFrequency: BackupFrequency = BackupFrequency.DAILY,

    @SerializedName("last_backup_time")
    val lastBackupTime: Long? = null,

    // ==================== СЖАТИЕ ====================

    @SerializedName("compress_photos")
    val compressPhotos: Boolean = true,

    @SerializedName("compress_videos")
    val compressVideos: Boolean = true
) {
    /**
     * Провайдер облачного хранилища
     */
    enum class BackupProvider(val displayName: String) {
        @SerializedName("local_server")
        LOCAL_SERVER("Personal Server"),

        @SerializedName("google_drive")
        GOOGLE_DRIVE("Google Drive"),

        @SerializedName("mega")
        MEGA("MEGA"),

        @SerializedName("dropbox")
        DROPBOX("Dropbox");

        companion object {
            fun fromValue(value: String): BackupProvider {
                return values().find { it.name.lowercase() == value.lowercase() } ?: LOCAL_SERVER
            }
        }
    }

    /**
     * Частота автоматического бэкапа
     */
    enum class BackupFrequency(val displayName: String, val hours: Int) {
        @SerializedName("never")
        NEVER("Never", 0),

        @SerializedName("daily")
        DAILY("Daily", 24),

        @SerializedName("weekly")
        WEEKLY("Weekly", 168),

        @SerializedName("monthly")
        MONTHLY("Monthly", 720);

        companion object {
            fun fromValue(value: String): BackupFrequency {
                return values().find { it.name.lowercase() == value.lowercase() } ?: DAILY
            }
        }
    }

    companion object {
        // Предустановленные размеры кэша
        val CACHE_SIZE_1GB = 1L * 1024 * 1024 * 1024
        val CACHE_SIZE_3GB = 3L * 1024 * 1024 * 1024
        val CACHE_SIZE_5GB = 5L * 1024 * 1024 * 1024
        val CACHE_SIZE_10GB = 10L * 1024 * 1024 * 1024
        val CACHE_SIZE_15GB = 15L * 1024 * 1024 * 1024
        val CACHE_SIZE_32GB = 32L * 1024 * 1024 * 1024
        val CACHE_SIZE_UNLIMITED = Long.MAX_VALUE

        fun cacheSizeToString(bytes: Long): String {
            return when (bytes) {
                CACHE_SIZE_UNLIMITED -> "∞"
                else -> {
                    val gb = bytes / (1024 * 1024 * 1024)
                    "$gb GB"
                }
            }
        }
    }
}

/**
 * Response для получения настроек
 */
data class CloudBackupSettingsResponse(
    @SerializedName("api_status")
    val apiStatus: Int,

    @SerializedName("settings")
    val settings: CloudBackupSettings,

    @SerializedName("errors")
    val errors: Map<String, String>? = null
)

/**
 * Response для обновления настроек
 */
data class UpdateCloudBackupSettingsResponse(
    @SerializedName("api_status")
    val apiStatus: Int,

    @SerializedName("message")
    val message: String,

    @SerializedName("errors")
    val errors: Map<String, String>? = null
)

/**
 * Состояние синхронизации
 */
data class SyncProgress(
    val isRunning: Boolean = false,
    val currentItem: Int = 0,
    val totalItems: Int = 0,
    val currentChatName: String? = null,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0
) {
    val progressPercent: Float
        get() = if (totalItems > 0) (currentItem.toFloat() / totalItems.toFloat()) * 100f else 0f
}

/**
 * Статистика облачного бекапу
 */
data class BackupStatistics(
    @SerializedName("total_messages")
    val totalMessages: Int,

    @SerializedName("messages_sent")
    val messagesSent: Int,

    @SerializedName("messages_received")
    val messagesReceived: Int,

    @SerializedName("media_files_count")
    val mediaFilesCount: Int,

    @SerializedName("media_size_bytes")
    val mediaSizeBytes: Long,

    @SerializedName("media_size_mb")
    val mediaSizeMb: Double,

    @SerializedName("groups_count")
    val groupsCount: Int,

    @SerializedName("channels_count")
    val channelsCount: Int,

    @SerializedName("total_storage_bytes")
    val totalStorageBytes: Long,

    @SerializedName("total_storage_mb")
    val totalStorageMb: Double,

    @SerializedName("total_storage_gb")
    val totalStorageGb: Double,

    @SerializedName("last_backup_time")
    val lastBackupTime: Long?,

    @SerializedName("backup_frequency")
    val backupFrequency: String,

    @SerializedName("server_name")
    val serverName: String,

    @SerializedName("backup_provider")
    val backupProvider: String
)

/**
 * Response для статистики
 */
data class BackupStatisticsResponse(
    @SerializedName("api_status")
    val apiStatus: Int,

    @SerializedName("statistics")
    val statistics: BackupStatistics,

    @SerializedName("errors")
    val errors: Map<String, String>? = null
)
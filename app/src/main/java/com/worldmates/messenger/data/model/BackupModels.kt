package com.worldmates.messenger.data.model

import com.google.gson.annotations.SerializedName

/**
 * 📦 Моделі для системи бекапу
 */

/**
 * Метадані бекапу
 */
data class BackupManifest(
    @SerializedName("version")
    val version: String,

    @SerializedName("created_at")
    val createdAt: Long,

    @SerializedName("user_id")
    val userId: Long,

    @SerializedName("app_version")
    val appVersion: String,

    @SerializedName("encryption")
    val encryption: String,

    @SerializedName("total_size")
    val totalSize: Long = 0,

    @SerializedName("total_messages")
    val totalMessages: Int = 0,

    @SerializedName("total_groups")
    val totalGroups: Int = 0
)

/**
 * Повний бекап користувача
 */
data class UserBackup(
    @SerializedName("manifest")
    val manifest: BackupManifest,

    @SerializedName("user")
    val user: Map<String, Any>?,

    @SerializedName("messages")
    val messages: List<Map<String, Any>>,

    @SerializedName("contacts")
    val contacts: List<Map<String, Any>>,

    @SerializedName("groups")
    val groups: List<Map<String, Any>>,

    @SerializedName("channels")
    val channels: List<Map<String, Any>>,

    @SerializedName("settings")
    val settings: Map<String, Any>?,

    @SerializedName("blocked_users")
    val blockedUsers: List<Long>
)

/**
 * Response для експорту даних
 */
data class ExportDataResponse(
    @SerializedName("api_status")
    val apiStatus: Int,

    @SerializedName("message")
    val message: String,

    @SerializedName("error_message")
    val errorMessage: String? = null,

    @SerializedName("backup_file")
    val backupFile: String,

    @SerializedName("backup_url")
    val backupUrl: String,

    @SerializedName("backup_size")
    val backupSize: Long,

    @SerializedName("export_data")
    val exportData: UserBackup
)

/**
 * Response для імпорту даних
 */
data class ImportDataResponse(
    @SerializedName("api_status")
    val apiStatus: Int,

    @SerializedName("message")
    val message: String,

    @SerializedName("imported")
    val imported: ImportStats
)

data class ImportStats(
    @SerializedName("messages")
    val messages: Int,

    @SerializedName("groups")
    val groups: Int,

    @SerializedName("channels")
    val channels: Int,

    @SerializedName("settings")
    val settings: Boolean
)

/**
 * Інформація про бекап файл
 */
data class BackupFileInfo(
    @SerializedName("filename")
    val filename: String?,

    @SerializedName("url")
    val url: String?,

    @SerializedName("size")
    val size: Long,

    @SerializedName("size_mb")
    val sizeMb: Double,

    @SerializedName("created_at")
    val createdAt: Long,

    @SerializedName("provider")
    val provider: String?
)

/**
 * Response для списку бекапів
 */
data class ListBackupsResponse(
    @SerializedName("api_status")
    val apiStatus: Int,

    @SerializedName("backups")
    val backups: List<BackupFileInfo>,

    @SerializedName("total_backups")
    val totalBackups: Int
)

/**
 * Стан процесу бекапу
 */
data class BackupProgress(
    val isRunning: Boolean = false,
    val progress: Int = 0, // 0-100
    val currentStep: String = "",
    val totalSteps: Int = 0,
    val currentStepNumber: Int = 0,
    val error: String? = null
) {
    val progressPercent: Float
        get() = if (totalSteps > 0) (currentStepNumber.toFloat() / totalSteps.toFloat()) * 100f else 0f
}
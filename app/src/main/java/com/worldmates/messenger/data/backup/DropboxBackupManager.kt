package com.worldmates.messenger.data.backup

import android.content.Context
import android.util.Log
import com.dropbox.core.DbxException
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.android.Auth
import com.dropbox.core.oauth.DbxCredential
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.*
import com.dropbox.core.v2.users.SpaceAllocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * 📦 DropboxBackupManager - Управління бекапами в Dropbox
 *
 * Функції:
 * - OAuth 2.0 авторизація
 * - Завантаження файлів на Dropbox
 * - Скачування файлів з Dropbox
 * - Список файлів бекапів
 * - Видалення файлів
 */
class DropboxBackupManager(private val context: Context) {

    companion object {
        private const val TAG = "DropboxBackupManager"
        private const val BACKUP_FOLDER_PATH = "/WorldMates Backups"
        private const val PREFS_NAME = "dropbox_prefs"
        private const val PREF_ACCESS_TOKEN = "access_token"
        private const val PREF_REFRESH_TOKEN = "refresh_token"
        private const val PREF_EXPIRES_AT = "expires_at"

        // TODO: Замініть на ваш App Key з Dropbox
        // Інструкція: https://www.dropbox.com/developers/apps
        // 1. Створіть Dropbox app на https://www.dropbox.com/developers/apps/create
        // 2. Виберіть "Scoped access"
        // 3. Виберіть "Full Dropbox" або "App folder"
        // 4. Скопіюйте App key і App secret
        // 5. Додайте redirect URI: worldmates://dropbox-auth-callback
        private const val DROPBOX_APP_KEY = "YOUR_DROPBOX_APP_KEY"
        private const val DROPBOX_APP_SECRET = "YOUR_DROPBOX_APP_SECRET"
    }

    private var dropboxClient: DbxClientV2? = null
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ==================== ІНІЦІАЛІЗАЦІЯ ====================

    /**
     * Ініціалізувати Dropbox клієнт з існуючого токену
     */
    fun initialize() {
        try {
            val accessToken = prefs.getString(PREF_ACCESS_TOKEN, null)
            if (!accessToken.isNullOrEmpty()) {
                val refreshToken = prefs.getString(PREF_REFRESH_TOKEN, null)
                val expiresAt = prefs.getLong(PREF_EXPIRES_AT, 0L)

                val credential = DbxCredential(
                    accessToken,
                    expiresAt,
                    refreshToken,
                    DROPBOX_APP_KEY,
                    DROPBOX_APP_SECRET
                )

                val config = DbxRequestConfig.newBuilder("WorldMates/2.0").build()
                dropboxClient = DbxClientV2(config, credential)

                Log.d(TAG, "✅ Dropbox client initialized from saved credentials")
            } else {
                Log.d(TAG, "ℹ️ No saved credentials found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize Dropbox client: ${e.message}", e)
        }
    }

    // ==================== АВТОРИЗАЦІЯ ====================

    /**
     * Розпочати процес авторизації
     * Викликайте цей метод, щоб відкрити Dropbox авторизацію
     */
    fun startOAuth() {
        try {
            Log.d(TAG, "🔐 Starting Dropbox OAuth")

            // Налаштування OAuth з PKCE
            Auth.startOAuth2PKCE(
                context,
                DROPBOX_APP_KEY,
                DbxRequestConfig.newBuilder("WorldMates/2.0").build(),
                listOf("files.content.write", "files.content.read", "files.metadata.read")
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start OAuth: ${e.message}", e)
        }
    }

    /**
     * Завершити процес авторизації
     * Викликайте цей метод в onResume() активності після авторизації
     */
    suspend fun finishOAuth(): Boolean = withContext(Dispatchers.IO) {
        try {
            val credential = Auth.getDbxCredential()
            if (credential == null) {
                Log.w(TAG, "⚠️ OAuth failed: no credential")
                return@withContext false
            }

            // Зберегти credentials
            prefs.edit()
                .putString(PREF_ACCESS_TOKEN, credential.accessToken)
                .putString(PREF_REFRESH_TOKEN, credential.refreshToken)
                .putLong(PREF_EXPIRES_AT, credential.expiresAt ?: 0L)
                .apply()

            // Ініціалізувати клієнт
            val config = DbxRequestConfig.newBuilder("WorldMates/2.0").build()
            dropboxClient = DbxClientV2(config, credential)

            Log.d(TAG, "✅ OAuth completed successfully")

            // Створити папку для бекапів
            createBackupFolderIfNeeded()

            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to finish OAuth: ${e.message}", e)
            false
        }
    }

    /**
     * Перевірити чи користувач авторизований
     */
    fun isAuthorized(): Boolean {
        return dropboxClient != null && prefs.contains(PREF_ACCESS_TOKEN)
    }

    /**
     * Вийти з Dropbox акаунту
     */
    fun logout() {
        try {
            // Видалити saved credentials
            prefs.edit().clear().apply()

            // Очистити клієнт
            dropboxClient = null

            Log.d(TAG, "✅ Logged out successfully")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Logout failed: ${e.message}", e)
        }
    }

    // ==================== УПРАВЛІННЯ ПАПКАМИ ====================

    /**
     * Створити папку для бекапів якщо не існує
     */
    private suspend fun createBackupFolderIfNeeded() = withContext(Dispatchers.IO) {
        try {
            if (dropboxClient == null) {
                return@withContext
            }

            // Перевірити чи папка існує
            try {
                dropboxClient!!.files().getMetadata(BACKUP_FOLDER_PATH)
                Log.d(TAG, "📁 Backup folder already exists")
            } catch (e: GetMetadataErrorException) {
                // Папка не існує - створити
                dropboxClient!!.files().createFolderV2(BACKUP_FOLDER_PATH)
                Log.d(TAG, "✅ Created backup folder")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to create backup folder: ${e.message}", e)
        }
    }

    // ==================== ЗАВАНТАЖЕННЯ ФАЙЛІВ ====================

    /**
     * Завантажити файл на Dropbox
     */
    suspend fun uploadFile(localFile: File, remotePath: String): Boolean = withContext(Dispatchers.IO) {
        if (dropboxClient == null) {
            Log.e(TAG, "❌ Not authorized")
            return@withContext false
        }

        try {
            Log.d(TAG, "📤 Uploading file: ${localFile.name} (${localFile.length()} bytes)")

            val fullPath = "$BACKUP_FOLDER_PATH$remotePath"

            FileInputStream(localFile).use { inputStream ->
                dropboxClient!!.files().uploadBuilder(fullPath)
                    .withMode(WriteMode.OVERWRITE)
                    .withAutorename(false)
                    .uploadAndFinish(inputStream)
            }

            Log.d(TAG, "✅ File uploaded successfully to: $fullPath")
            true

        } catch (e: DbxException) {
            Log.e(TAG, "❌ Upload failed: ${e.message}", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Upload failed: ${e.message}", e)
            false
        }
    }

    // ==================== СКАЧУВАННЯ ФАЙЛІВ ====================

    /**
     * Скачати файл з Dropbox
     */
    suspend fun downloadFile(remotePath: String, destinationPath: String): Boolean = withContext(Dispatchers.IO) {
        if (dropboxClient == null) {
            Log.e(TAG, "❌ Not authorized")
            return@withContext false
        }

        try {
            Log.d(TAG, "📥 Downloading file: $remotePath")

            val fullPath = "$BACKUP_FOLDER_PATH$remotePath"

            FileOutputStream(destinationPath).use { outputStream ->
                dropboxClient!!.files().download(fullPath).download(outputStream)
            }

            Log.d(TAG, "✅ File downloaded successfully to: $destinationPath")
            true

        } catch (e: DbxException) {
            Log.e(TAG, "❌ Download failed: ${e.message}", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Download failed: ${e.message}", e)
            false
        }
    }

    // ==================== СПИСОК ФАЙЛІВ ====================

    /**
     * Отримати список всіх бекапів на Dropbox
     */
    suspend fun listBackupFiles(): List<DropboxBackupFile> = withContext(Dispatchers.IO) {
        if (dropboxClient == null) {
            Log.w(TAG, "⚠️ Not authorized")
            return@withContext emptyList()
        }

        try {
            val result = dropboxClient!!.files().listFolder(BACKUP_FOLDER_PATH)

            val files = result.entries
                .filterIsInstance<FileMetadata>()
                .map { metadata ->
                    DropboxBackupFile(
                        path = metadata.pathLower ?: metadata.pathDisplay,
                        name = metadata.name,
                        size = metadata.size,
                        clientModified = metadata.clientModified?.time ?: 0L,
                        serverModified = metadata.serverModified?.time ?: 0L,
                        rev = metadata.rev
                    )
                }
                .sortedByDescending { it.serverModified }

            Log.d(TAG, "✅ Found ${files.size} backup files")
            files

        } catch (e: DbxException) {
            Log.e(TAG, "❌ Failed to list files: ${e.message}", e)
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to list files: ${e.message}", e)
            emptyList()
        }
    }

    // ==================== ВИДАЛЕННЯ ФАЙЛІВ ====================

    /**
     * Видалити файл з Dropbox
     */
    suspend fun deleteFile(remotePath: String): Boolean = withContext(Dispatchers.IO) {
        if (dropboxClient == null) {
            Log.e(TAG, "❌ Not authorized")
            return@withContext false
        }

        try {
            Log.d(TAG, "🗑️ Deleting file: $remotePath")

            val fullPath = "$BACKUP_FOLDER_PATH$remotePath"
            dropboxClient!!.files().deleteV2(fullPath)

            Log.d(TAG, "✅ File deleted successfully")
            true

        } catch (e: DbxException) {
            Log.e(TAG, "❌ Delete failed: ${e.message}", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Delete failed: ${e.message}", e)
            false
        }
    }

    // ==================== ОТРИМАТИ ІНФОРМАЦІЮ ====================

    /**
     * Отримати інформацію про акаунт
     */
    suspend fun getAccountInfo(): DropboxAccountInfo? = withContext(Dispatchers.IO) {
        if (dropboxClient == null) {
            return@withContext null
        }

        try {
            val account = dropboxClient!!.users().currentAccount

            DropboxAccountInfo(
                name = account.name.displayName,
                email = account.email,
                accountId = account.accountId
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to get account info: ${e.message}", e)
            null
        }
    }

    /**
     * Отримати storage quota
     */
    suspend fun getStorageQuota(): DropboxStorageQuota? = withContext(Dispatchers.IO) {
        if (dropboxClient == null) {
            return@withContext null
        }

        try {
            val spaceUsage = dropboxClient!!.users().spaceUsage

            val used = spaceUsage.used
            val allocated = try {
                when {
                    spaceUsage.allocation.isIndividual -> spaceUsage.allocation.individualValue.allocated
                    spaceUsage.allocation.isTeam -> spaceUsage.allocation.teamValue.allocated
                    else -> 0L
                }
            } catch (e: Exception) {
                0L
            }

            DropboxStorageQuota(
                totalBytes = allocated,
                usedBytes = used,
                availableBytes = allocated - used
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to get storage quota: ${e.message}", e)
            null
        }
    }
}

/**
 * Інформація про файл на Dropbox
 */
data class DropboxBackupFile(
    val path: String,
    val name: String,
    val size: Long,
    val clientModified: Long,
    val serverModified: Long,
    val rev: String
)

/**
 * Інформація про акаунт Dropbox
 */
data class DropboxAccountInfo(
    val name: String,
    val email: String,
    val accountId: String
)

/**
 * Інформація про storage quota в Dropbox
 */
data class DropboxStorageQuota(
    val totalBytes: Long,
    val usedBytes: Long,
    val availableBytes: Long
) {
    val usedPercent: Float
        get() = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes.toFloat()) * 100f else 0f

    val totalGb: Double
        get() = totalBytes / 1024.0 / 1024.0 / 1024.0

    val usedGb: Double
        get() = usedBytes / 1024.0 / 1024.0 / 1024.0

    val availableGb: Double
        get() = availableBytes / 1024.0 / 1024.0 / 1024.0
}
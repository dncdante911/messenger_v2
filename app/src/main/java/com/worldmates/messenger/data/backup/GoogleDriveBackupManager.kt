package com.worldmates.messenger.data.backup

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import kotlinx.coroutines.tasks.await
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream

/**
 * 📦 GoogleDriveBackupManager - Управління бекапами в Google Drive
 *
 * Функції:
 * - OAuth 2.0 авторизація
 * - Завантаження файлів на Google Drive
 * - Скачування файлів з Google Drive
 * - Список файлів бекапів
 * - Видалення файлів
 */
class GoogleDriveBackupManager(private val context: Context) {

    companion object {
        private const val TAG = "GoogleDriveBackupManager"
        private const val BACKUP_FOLDER_NAME = "WallyMates Backups"

        // TODO: Замініть на ваш Client ID з Google Cloud Console
        // Інструкція: https://console.cloud.google.com/apis/credentials
        // 1. Створіть проект
        // 2. Увімкніть Google Drive API
        // 3. Створіть OAuth 2.0 Client ID для Android
        // 4. Додайте SHA-1 fingerprint вашого keystore
        private const val GOOGLE_CLIENT_ID = "YOUR_GOOGLE_CLIENT_ID.apps.googleusercontent.com"
    }

    private var googleSignInClient: GoogleSignInClient? = null
    private var driveService: Drive? = null
    private var backupFolderId: String? = null

    // ==================== ІНІЦІАЛІЗАЦІЯ ====================

    /**
     * Ініціалізувати Google Sign In клієнт
     */
    fun initialize() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .requestServerAuthCode(GOOGLE_CLIENT_ID)
            .build()

        googleSignInClient = GoogleSignIn.getClient(context, gso)
        Log.d(TAG, "✅ Google Sign In client initialized")
    }

    /**
     * Отримати Intent для авторизації
     */
    fun getSignInIntent(): Intent? {
        return googleSignInClient?.signInIntent
    }

    /**
     * Обробити результат авторизації
     */
    suspend fun handleSignInResult(account: GoogleSignInAccount?): Boolean = withContext(Dispatchers.IO) {
        if (account == null) {
            Log.w(TAG, "⚠️ Sign in failed: account is null")
            return@withContext false
        }

        try {
            Log.d(TAG, "📝 Setting up Drive service for account: ${account.email}")

            val credential = GoogleAccountCredential.usingOAuth2(
                context,
                listOf(DriveScopes.DRIVE_FILE)
            )
            credential.selectedAccount = account.account

            driveService = Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            )
                .setApplicationName("WallyMates Messenger")
                .build()

            // Створити або знайти папку для бекапів
            backupFolderId = getOrCreateBackupFolder()

            Log.d(TAG, "✅ Drive service initialized successfully")
            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize Drive service: ${e.message}", e)
            false
        }
    }

    /**
     * Перевірити чи користувач авторизований
     */
    fun isSignedIn(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        val hasScopes = account?.grantedScopes?.contains(Scope(DriveScopes.DRIVE_FILE)) == true
        return account != null && hasScopes && driveService != null
    }

    /**
     * Вийти з акаунту
     */
    suspend fun signOut(): Boolean = withContext(Dispatchers.IO) {
        try {
            googleSignInClient?.signOut()?.await()
            driveService = null
            backupFolderId = null
            Log.d(TAG, "✅ Signed out successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Sign out failed: ${e.message}", e)
            false
        }
    }

    // ==================== УПРАВЛІННЯ ПАПКАМИ ====================

    /**
     * Отримати або створити папку для бекапів
     */
    private suspend fun getOrCreateBackupFolder(): String = withContext(Dispatchers.IO) {
        try {
            // Спробувати знайти існуючу папку
            val query = "mimeType='application/vnd.google-apps.folder' and name='$BACKUP_FOLDER_NAME' and trashed=false"
            val result = driveService?.files()?.list()
                ?.setQ(query)
                ?.setSpaces("drive")
                ?.setFields("files(id, name)")
                ?.execute()

            val existingFolder = result?.files?.firstOrNull()
            if (existingFolder != null) {
                Log.d(TAG, "📁 Found existing backup folder: ${existingFolder.id}")
                return@withContext existingFolder.id
            }

            // Створити нову папку
            val folderMetadata = File().apply {
                name = BACKUP_FOLDER_NAME
                mimeType = "application/vnd.google-apps.folder"
            }

            val folder = driveService?.files()?.create(folderMetadata)
                ?.setFields("id")
                ?.execute()

            Log.d(TAG, "✅ Created new backup folder: ${folder?.id}")
            folder?.id ?: throw Exception("Failed to create backup folder")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to get/create backup folder: ${e.message}", e)
            throw e
        }
    }

    // ==================== ЗАВАНТАЖЕННЯ ФАЙЛІВ ====================

    /**
     * Завантажити файл на Google Drive
     */
    suspend fun uploadFile(
        localFile: java.io.File,
        fileName: String,
        mimeType: String
    ): String = withContext(Dispatchers.IO) {
        try {
            if (!isSignedIn()) {
                throw Exception("Not signed in to Google Drive")
            }

            Log.d(TAG, "📤 Uploading file: $fileName (${localFile.length()} bytes)")

            val fileMetadata = File().apply {
                name = fileName
                parents = listOf(backupFolderId)
            }

            val mediaContent = com.google.api.client.http.FileContent(mimeType, localFile)

            val file = driveService?.files()?.create(fileMetadata, mediaContent)
                ?.setFields("id, name, size, createdTime")
                ?.execute()

            Log.d(TAG, "✅ File uploaded successfully: ${file?.id}")
            file?.id ?: throw Exception("Failed to upload file")

        } catch (e: Exception) {
            Log.e(TAG, "❌ File upload failed: ${e.message}", e)
            throw e
        }
    }

    // ==================== СКАЧУВАННЯ ФАЙЛІВ ====================

    /**
     * Скачати файл з Google Drive
     */
    suspend fun downloadFile(fileId: String, destinationPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!isSignedIn()) {
                throw Exception("Not signed in to Google Drive")
            }

            Log.d(TAG, "📥 Downloading file: $fileId")

            val outputStream = FileOutputStream(destinationPath)
            driveService?.files()?.get(fileId)?.executeMediaAndDownloadTo(outputStream)
            outputStream.close()

            Log.d(TAG, "✅ File downloaded successfully to: $destinationPath")
            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ File download failed: ${e.message}", e)
            false
        }
    }

    // ==================== СПИСОК ФАЙЛІВ ====================

    /**
     * Отримати список всіх бекапів на Google Drive
     */
    suspend fun listBackupFiles(): List<DriveBackupFile> = withContext(Dispatchers.IO) {
        try {
            if (!isSignedIn()) {
                Log.w(TAG, "⚠️ Not signed in")
                return@withContext emptyList()
            }

            val folderId = backupFolderId ?: getOrCreateBackupFolder()

            val query = "'$folderId' in parents and trashed=false"
            val result = driveService?.files()?.list()
                ?.setQ(query)
                ?.setSpaces("drive")
                ?.setFields("files(id, name, size, createdTime, modifiedTime)")
                ?.setOrderBy("createdTime desc")
                ?.execute()

            val files = result?.files?.map { file ->
                DriveBackupFile(
                    id = file.id,
                    name = file.name,
                    size = file.getSize() ?: 0L,
                    createdTime = file.createdTime?.value ?: 0L,
                    modifiedTime = file.modifiedTime?.value ?: 0L
                )
            } ?: emptyList()

            Log.d(TAG, "✅ Found ${files.size} backup files")
            files

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to list backup files: ${e.message}", e)
            emptyList()
        }
    }

    // ==================== ВИДАЛЕННЯ ФАЙЛІВ ====================

    /**
     * Видалити файл з Google Drive
     */
    suspend fun deleteFile(fileId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!isSignedIn()) {
                throw Exception("Not signed in to Google Drive")
            }

            Log.d(TAG, "🗑️ Deleting file: $fileId")

            driveService?.files()?.delete(fileId)?.execute()

            Log.d(TAG, "✅ File deleted successfully")
            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ File deletion failed: ${e.message}", e)
            false
        }
    }

    // ==================== ОТРИМАТИ ІНФОРМАЦІЮ ====================

    /**
     * Отримати інформацію про авторизованого користувача
     */
    fun getSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    /**
     * Отримати storage quota (використане/доступне місце)
     */
    suspend fun getStorageQuota(): StorageQuota? = withContext(Dispatchers.IO) {
        try {
            if (!isSignedIn()) {
                return@withContext null
            }

            val about = driveService?.about()?.get()
                ?.setFields("storageQuota")
                ?.execute()

            val quota = about?.storageQuota
            StorageQuota(
                limit = quota?.limit ?: 0L,
                usage = quota?.usage ?: 0L,
                usageInDrive = quota?.usageInDrive ?: 0L
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to get storage quota: ${e.message}", e)
            null
        }
    }
}

/**
 * Інформація про файл на Google Drive
 */
data class DriveBackupFile(
    val id: String,
    val name: String,
    val size: Long,
    val createdTime: Long,
    val modifiedTime: Long
)

/**
 * Інформація про storage quota
 */
data class StorageQuota(
    val limit: Long,
    val usage: Long,
    val usageInDrive: Long
) {
    val usedPercent: Float
        get() = if (limit > 0) (usage.toFloat() / limit.toFloat()) * 100f else 0f

    val availableBytes: Long
        get() = limit - usage
}
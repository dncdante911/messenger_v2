package com.worldmates.messenger.data.backup

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.worldmates.messenger.data.model.*
import com.worldmates.messenger.network.NodeRetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 📦 CloudBackupManager - Управління повними бекапами користувача
 *
 * Функції:
 * - Створення повних бекапів (всі дані користувача)
 * - Відновлення з бекапів
 * - Список бекапів
 * - Видалення бекапів
 * - Інтеграція з облачними сервісами
 */
class CloudBackupManager(private val context: Context) {

    companion object {
        private const val TAG = "CloudBackupManager"
        private const val BACKUP_FILE_PREFIX = "worldmates_backup_"
        private const val BACKUP_DIR_NAME = "backups"

        @Volatile
        private var instance: CloudBackupManager? = null

        fun getInstance(context: Context): CloudBackupManager {
            return instance ?: synchronized(this) {
                instance ?: CloudBackupManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val api = NodeRetrofitClient.api
    private val gson = Gson()

    // Менеджери облачних сервісів
    private var googleDriveManager: GoogleDriveBackupManager? = null
    private var megaManager: MegaBackupManager? = null
    private var dropboxManager: DropboxBackupManager? = null

    // Прогрес бекапу
    private val _backupProgress = MutableStateFlow(BackupProgress())
    val backupProgress: StateFlow<BackupProgress> = _backupProgress.asStateFlow()

    // Прогрес відновлення
    private val _restoreProgress = MutableStateFlow(BackupProgress())
    val restoreProgress: StateFlow<BackupProgress> = _restoreProgress.asStateFlow()

    // ==================== ІНІЦІАЛІЗАЦІЯ ====================

    /**
     * Ініціалізувати менеджери облачних сервісів
     */
    fun initializeCloudProviders() {
        googleDriveManager = GoogleDriveBackupManager(context)
        megaManager = MegaBackupManager(context)
        dropboxManager = DropboxBackupManager(context)
    }

    // ==================== СТВОРЕННЯ БЕКАПУ ====================

    /**
     * Створити повний бекап користувача
     * Зберігає на сервері + опціонально на обраному cloud provider
     */
    suspend fun createBackup(
        uploadToCloud: Boolean = false,
        cloudProvider: CloudBackupSettings.BackupProvider? = null
    ): Result<BackupFileInfo> = withContext(Dispatchers.IO) {
        try {
            val totalSteps = if (uploadToCloud) 3 else 2

            _backupProgress.value = BackupProgress(
                isRunning = true,
                currentStep = "Експорт даних з сервера...",
                totalSteps = totalSteps,
                currentStepNumber = 1
            )

            Log.d(TAG, "📤 Starting backup creation...")

            // Крок 1: Експорт даних з сервера
            val response = api.exportUserData()

            if (response.apiStatus != 200) {
                val errText = response.errorMessage ?: response.message.ifBlank { "api_status=${response.apiStatus}" }
                _backupProgress.value = BackupProgress(error = "Помилка експорту: $errText")
                return@withContext Result.failure(Exception(errText))
            }

            Log.d(TAG, "✅ Data exported: ${response.backupSize} bytes, ${response.exportData.manifest.totalMessages} messages")

            // Крок 2: Зберегти локально
            _backupProgress.value = _backupProgress.value.copy(
                currentStep = "Збереження локально...",
                currentStepNumber = 2,
                progress = 50
            )

            val localFile = saveBackupLocally(response.exportData)
            Log.d(TAG, "✅ Saved locally: ${localFile.name}")

            // Крок 3: Завантажити на облачний сервіс (опціонально)
            if (uploadToCloud && cloudProvider != null) {
                _backupProgress.value = _backupProgress.value.copy(
                    currentStep = "Завантаження на ${cloudProvider.displayName}...",
                    currentStepNumber = 3,
                    progress = 75
                )

                uploadToCloudProvider(localFile, cloudProvider)
            }

            _backupProgress.value = BackupProgress(
                isRunning = false,
                progress = 100
            )

            val backupInfo = BackupFileInfo(
                filename = response.backupFile,
                url = response.backupUrl,
                size = response.backupSize,
                sizeMb = response.backupSize / 1024.0 / 1024.0,
                createdAt = System.currentTimeMillis(),
                provider = "local_server"
            )

            Log.d(TAG, "🎉 Backup created successfully")
            Result.success(backupInfo)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Backup creation failed: ${e.message}", e)
            _backupProgress.value = BackupProgress(error = e.message)
            Result.failure(e)
        }
    }

    /**
     * Зберегти бекап локально на пристрої
     */
    private fun saveBackupLocally(backup: UserBackup): File {
        val backupDir = File(context.filesDir, BACKUP_DIR_NAME)
        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }

        val filename = "${BACKUP_FILE_PREFIX}${System.currentTimeMillis()}.json"
        val file = File(backupDir, filename)

        val json = gson.toJson(backup)
        file.writeText(json)

        Log.d(TAG, "💾 Saved locally: ${file.absolutePath} (${json.length} bytes)")

        return file
    }

    /**
     * Завантажити бекап на облачний сервіс
     */
    private suspend fun uploadToCloudProvider(
        file: File,
        provider: CloudBackupSettings.BackupProvider
    ) {
        when (provider) {
            CloudBackupSettings.BackupProvider.GOOGLE_DRIVE -> {
                googleDriveManager?.let {
                    if (it.isSignedIn()) {
                        val fileId = it.uploadFile(file, file.name, "application/json")
                        Log.d(TAG, "✅ Uploaded to Google Drive: $fileId")
                    } else {
                        Log.w(TAG, "⚠️ Google Drive not authorized")
                    }
                } ?: Log.w(TAG, "⚠️ Google Drive manager not initialized")
            }
            CloudBackupSettings.BackupProvider.MEGA -> {
                megaManager?.let {
                    if (it.isLoggedIn()) {
                        val success = it.uploadFile(file)
                        Log.d(TAG, "✅ Uploaded to MEGA: $success")
                    } else {
                        Log.w(TAG, "⚠️ MEGA not logged in")
                    }
                } ?: Log.w(TAG, "⚠️ MEGA manager not initialized")
            }
            CloudBackupSettings.BackupProvider.DROPBOX -> {
                dropboxManager?.let {
                    if (it.isAuthorized()) {
                        val success = it.uploadFile(file, "/${file.name}")
                        Log.d(TAG, "✅ Uploaded to Dropbox: $success")
                    } else {
                        Log.w(TAG, "⚠️ Dropbox not authorized")
                    }
                } ?: Log.w(TAG, "⚠️ Dropbox manager not initialized")
            }
            else -> {
                Log.d(TAG, "Local server - no cloud upload needed")
            }
        }
    }

    // ==================== СПИСОК БЕКАПІВ ====================

    /**
     * Отримати список всіх бекапів (сервер + локальні + облачні)
     */
    suspend fun listBackups(): Result<List<BackupFileInfo>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📋 Loading backups list...")

            // Бекапи з сервера
            val serverBackups = try {
                val response = api.listBackups()
                if (response.apiStatus == 200) response.backups else emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load server backups: ${e.message}")
                emptyList()
            }

            // Локальні бекапи
            val localBackups = getLocalBackups()

            // Об'єднати та відсортувати
            val allBackups = (serverBackups + localBackups)
                .sortedByDescending { it.createdAt }

            Log.d(TAG, "✅ Found ${allBackups.size} backups (${serverBackups.size} server, ${localBackups.size} local)")

            Result.success(allBackups)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to load backups: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Отримати локальні бекапи
     */
    private fun getLocalBackups(): List<BackupFileInfo> {
        val backupDir = File(context.filesDir, BACKUP_DIR_NAME)
        if (!backupDir.exists()) {
            return emptyList()
        }

        return backupDir.listFiles()
            ?.filter { it.extension == "json" && it.name.startsWith(BACKUP_FILE_PREFIX) }
            ?.map { file ->
                BackupFileInfo(
                    filename = file.name,
                    url = file.absolutePath,
                    size = file.length(),
                    sizeMb = file.length() / 1024.0 / 1024.0,
                    createdAt = file.lastModified(),
                    provider = "local_device"
                )
            }
            ?: emptyList()
    }

    // ==================== ВІДНОВЛЕННЯ З БЕКАПУ ====================

    /**
     * Відновити дані з бекапу
     */
    suspend fun restoreFromBackup(
        backupInfo: BackupFileInfo
    ): Result<ImportStats> = withContext(Dispatchers.IO) {
        try {
            _restoreProgress.value = BackupProgress(
                isRunning = true,
                currentStep = "Завантаження бекапу...",
                totalSteps = 3,
                currentStepNumber = 1
            )

            Log.d(TAG, "📥 Starting restore from ${backupInfo.filename}...")

            // Крок 1: Завантажити бекап
            val backupJson = when (backupInfo.provider) {
                "local_device" -> {
                    File(backupInfo.url).readText()
                }
                "local_server" -> {
                    // Зараз дані вже є в exportData, але можна скачати окремо
                    // TODO: Реалізувати скачування з сервера
                    throw NotImplementedError("Server download not yet implemented")
                }
                else -> {
                    throw NotImplementedError("Cloud download not yet implemented")
                }
            }

            // Крок 2: Валідація даних
            _restoreProgress.value = _restoreProgress.value.copy(
                currentStep = "Перевірка даних...",
                currentStepNumber = 2,
                progress = 33
            )

            val backup = gson.fromJson(backupJson, UserBackup::class.java)
            Log.d(TAG, "✅ Backup validated: ${backup.manifest.totalMessages} messages")

            // Крок 3: Імпорт на сервер
            _restoreProgress.value = _restoreProgress.value.copy(
                currentStep = "Відновлення даних...",
                currentStepNumber = 3,
                progress = 66
            )

            val response = api.importUserData(backupData = backupJson)

            if (response.apiStatus != 200) {
                _restoreProgress.value = BackupProgress(error = "Помилка імпорту: ${response.message}")
                return@withContext Result.failure(Exception(response.message))
            }

            _restoreProgress.value = BackupProgress(
                isRunning = false,
                progress = 100
            )

            Log.d(TAG, "🎉 Restore completed successfully")
            Log.d(TAG, "   - Messages: ${response.imported.messages}")
            Log.d(TAG, "   - Groups: ${response.imported.groups}")
            Log.d(TAG, "   - Settings: ${response.imported.settings}")

            Result.success(response.imported)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Restore failed: ${e.message}", e)
            _restoreProgress.value = BackupProgress(error = e.message)
            Result.failure(e)
        }
    }

    // ==================== ВИДАЛЕННЯ БЕКАПУ ====================

    /**
     * Видалити бекап
     */
    suspend fun deleteBackup(backupInfo: BackupFileInfo): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            when (backupInfo.provider) {
                "local_device" -> {
                    val file = File(backupInfo.url)
                    val deleted = file.delete()
                    Log.d(TAG, if (deleted) "✅ Local backup deleted" else "❌ Failed to delete local backup")
                    Result.success(deleted)
                }
                "local_server" -> {
                    Log.w(TAG, "⚠️ Server backup deletion not implemented")
                    Result.success(false)
                }
                else -> {
                    Log.w(TAG, "⚠️ Cloud backup deletion not implemented")
                    Result.success(false)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to delete backup: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ==================== ДОПОМІЖНІ МЕТОДИ ====================

    /**
     * Перевірити чи є бекапи
     */
    suspend fun hasBackups(): Boolean {
        return listBackups().getOrNull()?.isNotEmpty() == true
    }

    /**
     * Отримати останній бекап
     */
    suspend fun getLatestBackup(): BackupFileInfo? {
        return listBackups().getOrNull()?.firstOrNull()
    }

    /**
     * Очистити старі локальні бекапи (залишити тільки N останніх)
     */
    fun cleanOldBackups(keepCount: Int = 5) {
        val backupDir = File(context.filesDir, BACKUP_DIR_NAME)
        if (!backupDir.exists()) return

        val files = backupDir.listFiles()
            ?.filter { it.extension == "json" && it.name.startsWith(BACKUP_FILE_PREFIX) }
            ?.sortedByDescending { it.lastModified() }
            ?: return

        if (files.size > keepCount) {
            val toDelete = files.drop(keepCount)
            toDelete.forEach { file ->
                val deleted = file.delete()
                Log.d(TAG, "🗑️ Deleted old backup: ${file.name} ($deleted)")
            }
        }
    }
}
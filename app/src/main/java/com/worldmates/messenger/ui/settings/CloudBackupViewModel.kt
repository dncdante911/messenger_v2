package com.worldmates.messenger.ui.settings

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.data.local.AppDatabase
import com.worldmates.messenger.data.model.CloudBackupSettings
import com.worldmates.messenger.data.model.SyncProgress
import com.worldmates.messenger.data.model.BackupProgress
import com.worldmates.messenger.data.model.BackupFileInfo
import com.worldmates.messenger.data.backup.CloudBackupManager
import com.worldmates.messenger.data.repository.BackupRepository
import com.worldmates.messenger.data.repository.CloudBackupSettingsRepository
import com.worldmates.messenger.services.BackupWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 📦 CLOUD BACKUP v2: ViewModel для управления настройками
 */
class CloudBackupViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "CloudBackupViewModel"

    private val backupRepository = BackupRepository(application)
    private val settingsRepository = CloudBackupSettingsRepository(application)
    private val database = AppDatabase.getInstance(application)
    private val messageDao = database.messageDao()
    private val cloudBackupManager = CloudBackupManager(application)

    // ==================== STATE FLOWS ====================

    private val _settings = MutableStateFlow<CloudBackupSettings?>(null)
    val settings: StateFlow<CloudBackupSettings?> = _settings.asStateFlow()

    private val _syncProgress = MutableStateFlow(SyncProgress())
    val syncProgress: StateFlow<SyncProgress> = _syncProgress.asStateFlow()

    private val _cacheSize = MutableStateFlow(0L)
    val cacheSize: StateFlow<Long> = _cacheSize.asStateFlow()

    private val _backupStatistics = MutableStateFlow<com.worldmates.messenger.data.model.BackupStatistics?>(null)
    val backupStatistics: StateFlow<com.worldmates.messenger.data.model.BackupStatistics?> = _backupStatistics.asStateFlow()

    // 📦 NEW: Cloud Backup Manager States
    private val _backupProgress = MutableStateFlow(BackupProgress())
    val backupProgress: StateFlow<BackupProgress> = _backupProgress.asStateFlow()

    private val _backupList = MutableStateFlow<List<BackupFileInfo>>(emptyList())
    val backupList: StateFlow<List<BackupFileInfo>> = _backupList.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        loadSettings()
        loadBackupStatistics()
    }

    // ==================== ЗАГРУЗКА НАСТРОЕК ====================

    private fun loadSettings() {
        viewModelScope.launch {
            settingsRepository.loadSettings().onSuccess { settings ->
                _settings.value = settings
                Log.d(TAG, "✅ Settings loaded from server")
                // Восстановить расписание автобэкапа после перезапуска
                if (settings.backupEnabled) {
                    BackupWorker.schedule(getApplication(), settings.backupFrequency)
                }
            }.onFailure { error ->
                Log.e(TAG, "❌ Failed to load settings: ${error.message}")
                _settings.value = CloudBackupSettings()
            }
        }
    }

    /**
     * Загрузить реальную статистику облачного хранилища с сервера
     */
    private fun loadBackupStatistics() {
        viewModelScope.launch {
            try {
                val response = com.worldmates.messenger.network.NodeRetrofitClient.api.getBackupStatistics()

                if (response.apiStatus == 200) {
                    _backupStatistics.value = response.statistics
                    _cacheSize.value = response.statistics.totalStorageBytes
                    Log.d(TAG, "✅ Backup statistics loaded: ${response.statistics.totalStorageMb} MB")
                } else {
                    Log.e(TAG, "❌ Failed to load statistics: ${response.apiStatus}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to load backup statistics: ${e.message}", e)
            }
        }
    }

    /**
     * Оновити статистику (викликати коли щось змінилось)
     */
    fun refreshStatistics() {
        loadBackupStatistics()
    }

    // ==================== АВТОЗАГРУЗКА МЕДИА ====================

    fun updateMobileDataSettings(
        photos: Boolean,
        videos: Boolean,
        files: Boolean,
        videoLimit: Int,
        fileLimit: Int
    ) {
        viewModelScope.launch {
            _settings.value = _settings.value?.copy(
                mobilePhotos = photos,
                mobileVideos = videos,
                mobileFiles = files,
                mobileVideosLimit = videoLimit,
                mobileFilesLimit = fileLimit
            )
            saveSettings()
        }
    }

    fun updateWiFiSettings(
        photos: Boolean,
        videos: Boolean,
        files: Boolean,
        videoLimit: Int,
        fileLimit: Int
    ) {
        viewModelScope.launch {
            _settings.value = _settings.value?.copy(
                wifiPhotos = photos,
                wifiVideos = videos,
                wifiFiles = files,
                wifiVideosLimit = videoLimit,
                wifiFilesLimit = fileLimit
            )
            saveSettings()
        }
    }

    fun updateRoamingPhotos(enabled: Boolean) {
        viewModelScope.launch {
            _settings.value = _settings.value?.copy(roamingPhotos = enabled)
            saveSettings()
        }
    }

    fun resetMediaSettings() {
        viewModelScope.launch {
            _settings.value = _settings.value?.copy(
                mobilePhotos = false,
                mobileVideos = false,
                mobileFiles = false,
                wifiPhotos = true,
                wifiVideos = true,
                wifiFiles = true,
                roamingPhotos = false
            )
            saveSettings()
        }
    }

    // ==================== СОХРАНЯТЬ В ГАЛЕРЕЕ ====================

    fun updateSaveToGalleryPrivateChats(enabled: Boolean) {
        viewModelScope.launch {
            _settings.value = _settings.value?.copy(saveToGalleryPrivateChats = enabled)
            saveSettings()
        }
    }

    fun updateSaveToGalleryGroups(enabled: Boolean) {
        viewModelScope.launch {
            _settings.value = _settings.value?.copy(saveToGalleryGroups = enabled)
            saveSettings()
        }
    }

    fun updateSaveToGalleryChannels(enabled: Boolean) {
        viewModelScope.launch {
            _settings.value = _settings.value?.copy(saveToGalleryChannels = enabled)
            saveSettings()
        }
    }

    // ==================== СТРИМИНГ ====================

    fun updateStreaming(enabled: Boolean) {
        viewModelScope.launch {
            _settings.value = _settings.value?.copy(streamingEnabled = enabled)
            saveSettings()
        }
    }

    // ==================== УПРАВЛЕНИЕ КЭШЕМ ====================

    fun updateCacheSize(size: Long) {
        viewModelScope.launch {
            _settings.value = _settings.value?.copy(cacheSizeLimit = size)
            saveSettings()
            // Если новый лимит меньше текущего размера - очистим старые сообщения
            if (size < _cacheSize.value && size != CloudBackupSettings.CACHE_SIZE_UNLIMITED) {
                clearOldMessagesUntilLimit(size)
            }
        }
    }

    suspend fun clearCache() {
        withContext(Dispatchers.IO) {
            try {
                messageDao.clearAllCache()
                calculateCacheSize()
                Log.d(TAG, "✅ Cache cleared successfully")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to clear cache: ${e.message}", e)
            }
        }
    }

    private fun calculateCacheSize() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val messageCount = messageDao.getCacheSize()
                // Примерный подсчет: 1 сообщение ≈ 1KB (без медиа)
                // TODO: Добавить реальный подсчет с медиафайлами
                _cacheSize.value = messageCount * 1024L
                Log.d(TAG, "📊 Cache size: ${messageCount} messages, ${_cacheSize.value} bytes")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to calculate cache size: ${e.message}", e)
            }
        }
    }

    private suspend fun clearOldMessagesUntilLimit(limit: Long) {
        withContext(Dispatchers.IO) {
            try {
                // Удаляем сообщения старше 30 дней пока не достигнем лимита
                var daysOld = 30
                while (_cacheSize.value > limit && daysOld > 0) {
                    messageDao.deleteOldMessages(
                        System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L)
                    )
                    calculateCacheSize()
                    daysOld -= 7
                }
                Log.d(TAG, "✅ Cleared old messages until limit: $limit")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to clear old messages: ${e.message}", e)
            }
        }
    }

    // ==================== CLOUD BACKUP ====================

    fun updateBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            _settings.value = _settings.value?.copy(backupEnabled = enabled)
            saveSettings()
            val frequency = _settings.value?.backupFrequency ?: CloudBackupSettings.BackupFrequency.NEVER
            if (enabled) {
                BackupWorker.schedule(getApplication(), frequency)
            } else {
                BackupWorker.cancel(getApplication())
            }
        }
    }

    fun updateBackupFrequency(frequency: CloudBackupSettings.BackupFrequency) {
        viewModelScope.launch {
            _settings.value = _settings.value?.copy(backupFrequency = frequency)
            saveSettings()
            val enabled = _settings.value?.backupEnabled ?: false
            if (enabled) {
                BackupWorker.schedule(getApplication(), frequency)
            } else {
                BackupWorker.cancel(getApplication())
            }
        }
    }

    fun updateBackupProvider(provider: CloudBackupSettings.BackupProvider) {
        viewModelScope.launch {
            _settings.value = _settings.value?.copy(backupProvider = provider)
            saveSettings()
        }
    }

    suspend fun startSync() {
        withContext(Dispatchers.IO) {
            try {
                _syncProgress.value = SyncProgress(isRunning = true)

                val chatIds = messageDao.getDistinctUserChatIds()

                chatIds.forEachIndexed { index, chatId ->
                    _syncProgress.value = _syncProgress.value.copy(
                        currentItem = index + 1,
                        totalItems = chatIds.size,
                        currentChatName = "Чат #$chatId"
                    )

                    // Синхронизация чата
                    val result = backupRepository.syncFullHistory(
                        recipientId = chatId,
                        chatType = "user"
                    )

                    result.onSuccess { count ->
                        Log.d(TAG, "✅ Synced $count messages for chat $chatId")
                    }.onFailure { error ->
                        Log.e(TAG, "❌ Failed to sync chat $chatId: ${error.message}")
                    }
                }

                // Обновляем время последнего бэкапа
                _settings.value = _settings.value?.copy(
                    lastBackupTime = System.currentTimeMillis()
                )

                // Отметить завершение бэкапа на сервере
                settingsRepository.markBackupComplete().onSuccess {
                    Log.d(TAG, "✅ Backup completion marked on server")
                }

                _syncProgress.value = SyncProgress(isRunning = false)
                calculateCacheSize()

                Log.d(TAG, "✅ Sync completed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Sync failed: ${e.message}", e)
                _syncProgress.value = SyncProgress(isRunning = false)
            }
        }
    }

    // ==================== ЧЕРНОВИКИ ====================

    suspend fun deleteDrafts() {
        withContext(Dispatchers.IO) {
            try {
                database.draftDao().deleteAll()
                Log.d(TAG, "✅ Drafts deleted successfully")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to delete drafts: ${e.message}", e)
            }
        }
    }

    // ==================== СОХРАНЕНИЕ НАСТРОЕК ====================

    private fun saveSettings() {
        viewModelScope.launch {
            _settings.value?.let { settings ->
                settingsRepository.updateSettings(
                    mobilePhotos = settings.mobilePhotos,
                    mobileVideos = settings.mobileVideos,
                    mobileFiles = settings.mobileFiles,
                    mobileVideosLimit = settings.mobileVideosLimit,
                    mobileFilesLimit = settings.mobileFilesLimit,
                    wifiPhotos = settings.wifiPhotos,
                    wifiVideos = settings.wifiVideos,
                    wifiFiles = settings.wifiFiles,
                    wifiVideosLimit = settings.wifiVideosLimit,
                    wifiFilesLimit = settings.wifiFilesLimit,
                    roamingPhotos = settings.roamingPhotos,
                    saveToGalleryPrivateChats = settings.saveToGalleryPrivateChats,
                    saveToGalleryGroups = settings.saveToGalleryGroups,
                    saveToGalleryChannels = settings.saveToGalleryChannels,
                    streamingEnabled = settings.streamingEnabled,
                    cacheSizeLimit = settings.cacheSizeLimit,
                    backupEnabled = settings.backupEnabled,
                    backupProvider = settings.backupProvider.name.lowercase(),
                    backupFrequency = settings.backupFrequency.name.lowercase()
                ).onSuccess { message ->
                    Log.d(TAG, "💾 Settings saved: $message")
                }.onFailure { error ->
                    Log.e(TAG, "❌ Failed to save settings: ${error.message}")
                }
            }
        }
    }

    // ==================== 📦 CLOUD BACKUP MANAGER INTEGRATION ====================

    /**
     * Создать бэкап сейчас
     */
    fun createBackup(uploadToCloud: Boolean = false) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "📦 Starting backup creation...")
                _errorMessage.value = null

                // Подписываемся на прогресс
                launch {
                    cloudBackupManager.backupProgress.collect { progress ->
                        _backupProgress.value = progress
                    }
                }

                val provider = if (uploadToCloud) _settings.value?.backupProvider else null
                val result = cloudBackupManager.createBackup(
                    uploadToCloud = uploadToCloud,
                    cloudProvider = provider
                )

                result.onSuccess { backupInfo ->
                    Log.d(TAG, "✅ Backup created successfully: ${backupInfo.filename}")

                    // Обновить список бэкапов
                    loadBackupList()

                    // Обновить время последнего бэкапа
                    _settings.value = _settings.value?.copy(
                        lastBackupTime = System.currentTimeMillis()
                    )
                    saveSettings()

                }.onFailure { error ->
                    Log.e(TAG, "❌ Backup creation failed: ${error.message}", error)
                    _errorMessage.value = "Ошибка создания бэкапа: ${error.message}"
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Backup creation exception: ${e.message}", e)
                _errorMessage.value = "Ошибка: ${e.message}"
            }
        }
    }

    /**
     * Восстановить из бэкапа
     */
    fun restoreFromBackup(backupInfo: BackupFileInfo) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "📥 Starting backup restore...")
                _errorMessage.value = null

                // Подписываемся на прогресс
                launch {
                    cloudBackupManager.backupProgress.collect { progress ->
                        _backupProgress.value = progress
                    }
                }

                val result = cloudBackupManager.restoreFromBackup(backupInfo)

                result.onSuccess { stats ->
                    Log.d(TAG, "✅ Backup restored successfully: ${stats.messages} messages")

                    // Обновить статистику
                    refreshStatistics()
                    calculateCacheSize()

                }.onFailure { error ->
                    Log.e(TAG, "❌ Backup restore failed: ${error.message}", error)
                    _errorMessage.value = "Ошибка восстановления: ${error.message}"
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Backup restore exception: ${e.message}", e)
                _errorMessage.value = "Ошибка: ${e.message}"
            }
        }
    }

    /**
     * Загрузить список бэкапов
     */
    fun loadBackupList() {
        viewModelScope.launch {
            try {
                val result = cloudBackupManager.listBackups()

                result.onSuccess { backups ->
                    _backupList.value = backups
                    Log.d(TAG, "✅ Loaded ${backups.size} backups")
                }.onFailure { error ->
                    Log.e(TAG, "❌ Failed to load backups: ${error.message}", error)
                    _errorMessage.value = "Ошибка загрузки списка бэкапов"
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Load backups exception: ${e.message}", e)
            }
        }
    }

    /**
     * Удалить бэкап
     */
    fun deleteBackup(backupInfo: BackupFileInfo) {
        viewModelScope.launch {
            try {
                val result = cloudBackupManager.deleteBackup(backupInfo)

                result.onSuccess {
                    Log.d(TAG, "✅ Backup deleted: ${backupInfo.filename}")
                    loadBackupList()
                }.onFailure { error ->
                    Log.e(TAG, "❌ Failed to delete backup: ${error.message}", error)
                    _errorMessage.value = "Ошибка удаления бэкапа"
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Delete backup exception: ${e.message}", e)
            }
        }
    }

    /**
     * Очистить сообщение об ошибке
     */
    fun clearError() {
        _errorMessage.value = null
    }
}
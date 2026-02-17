package com.worldmates.messenger.data.repository

import android.content.Context
import android.util.Log
import com.worldmates.messenger.data.model.CloudBackupSettings
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * 📦 CLOUD BACKUP v2: Repository для расширенных настроек облачного бэкапа
 *
 * Функции:
 * - Получение настроек с сервера
 * - Обновление настроек
 * - Кэширование настроек локально (StateFlow)
 */
class CloudBackupSettingsRepository(private val context: Context) {

    private val apiService = RetrofitClient.apiService

    private val TAG = "CloudBackupSettingsRepo"

    // StateFlow для реактивного доступа к настройкам
    private val _settings = MutableStateFlow<CloudBackupSettings?>(null)
    val settings: StateFlow<CloudBackupSettings?> = _settings.asStateFlow()

    // ==================== ПОЛУЧЕНИЕ НАСТРОЕК ====================

    /**
     * Загрузить настройки с сервера
     */
    suspend fun loadSettings(): Result<CloudBackupSettings> = withContext(Dispatchers.IO) {
        try {
            val accessToken = UserSession.accessToken
                ?: return@withContext Result.failure(Exception("No access token"))

            val response = apiService.getCloudBackupSettings(accessToken = accessToken)

            if (response.apiStatus == 200) {
                _settings.value = response.settings
                Log.d(TAG, "✅ Settings loaded: ${response.settings}")
                Result.success(response.settings)
            } else {
                Result.failure(Exception("API error: ${response.apiStatus}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to load settings: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Получить текущие настройки (из кэша StateFlow)
     */
    fun getCurrentSettings(): CloudBackupSettings? {
        return _settings.value
    }

    // ==================== ОБНОВЛЕНИЕ НАСТРОЕК ====================

    /**
     * Обновить настройки на сервере
     */
    suspend fun updateSettings(
        // АВТОЗАГРУЗКА МЕДИА (Мобильный интернет)
        mobilePhotos: Boolean? = null,
        mobileVideos: Boolean? = null,
        mobileFiles: Boolean? = null,
        mobileVideosLimit: Int? = null,
        mobileFilesLimit: Int? = null,

        // АВТОЗАГРУЗКА МЕДИА (Wi-Fi)
        wifiPhotos: Boolean? = null,
        wifiVideos: Boolean? = null,
        wifiFiles: Boolean? = null,
        wifiVideosLimit: Int? = null,
        wifiFilesLimit: Int? = null,

        // АВТОЗАГРУЗКА МЕДИА (Роуминг)
        roamingPhotos: Boolean? = null,

        // СОХРАНЯТЬ В ГАЛЕРЕЕ
        saveToGalleryPrivateChats: Boolean? = null,
        saveToGalleryGroups: Boolean? = null,
        saveToGalleryChannels: Boolean? = null,

        // СТРИМИНГ
        streamingEnabled: Boolean? = null,

        // УПРАВЛЕНИЕ КЭШЕМ
        cacheSizeLimit: Long? = null,

        // ОБЛАЧНЫЙ БЭКАП
        backupEnabled: Boolean? = null,
        backupProvider: String? = null,
        backupFrequency: String? = null,
        markBackupComplete: Boolean = false,

        // ПРОКСИ
        proxyEnabled: Boolean? = null,
        proxyHost: String? = null,
        proxyPort: Int? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val accessToken = UserSession.accessToken
                ?: return@withContext Result.failure(Exception("No access token"))

            val response = apiService.updateCloudBackupSettings(
                accessToken = accessToken,
                mobilePhotos = mobilePhotos?.toString(),
                mobileVideos = mobileVideos?.toString(),
                mobileFiles = mobileFiles?.toString(),
                mobileVideosLimit = mobileVideosLimit,
                mobileFilesLimit = mobileFilesLimit,
                wifiPhotos = wifiPhotos?.toString(),
                wifiVideos = wifiVideos?.toString(),
                wifiFiles = wifiFiles?.toString(),
                wifiVideosLimit = wifiVideosLimit,
                wifiFilesLimit = wifiFilesLimit,
                roamingPhotos = roamingPhotos?.toString(),
                saveToGalleryPrivateChats = saveToGalleryPrivateChats?.toString(),
                saveToGalleryGroups = saveToGalleryGroups?.toString(),
                saveToGalleryChannels = saveToGalleryChannels?.toString(),
                streamingEnabled = streamingEnabled?.toString(),
                cacheSizeLimit = cacheSizeLimit,
                backupEnabled = backupEnabled?.toString(),
                backupProvider = backupProvider,
                backupFrequency = backupFrequency,
                markBackupComplete = if (markBackupComplete) "true" else null,
                proxyEnabled = proxyEnabled?.toString(),
                proxyHost = proxyHost,
                proxyPort = proxyPort
            )

            if (response.apiStatus == 200) {
                // Обновляем локальный кэш
                loadSettings()
                Log.d(TAG, "✅ Settings updated: ${response.message}")
                Result.success(response.message)
            } else {
                Result.failure(Exception("API error: ${response.apiStatus}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to update settings: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Отметить завершение бэкапа (обновляет last_backup_time на сервере)
     */
    suspend fun markBackupComplete(): Result<String> {
        return updateSettings(markBackupComplete = true)
    }
}
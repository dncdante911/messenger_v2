package com.worldmates.messenger.data.repository

import android.content.Context
import android.util.Log
import com.worldmates.messenger.data.model.MediaSettings
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * 📦 CLOUD BACKUP: Repository для настроек автозагрузки медиа
 *
 * Функции:
 * - Получение настроек с сервера
 * - Обновление настроек
 * - Кэширование настроек локально (StateFlow)
 */
class MediaSettingsRepository(private val context: Context) {

    private val apiService = RetrofitClient.apiService

    private val TAG = "MediaSettingsRepository"

    // StateFlow для реактивного доступа к настройкам
    private val _settings = MutableStateFlow<MediaSettings?>(null)
    val settings: StateFlow<MediaSettings?> = _settings.asStateFlow()

    // ==================== ПОЛУЧЕНИЕ НАСТРОЕК ====================

    /**
     * Загрузить настройки с сервера
     */
    suspend fun loadSettings(): Result<MediaSettings> = withContext(Dispatchers.IO) {
        try {
            val accessToken = UserSession.accessToken
                ?: return@withContext Result.failure(Exception("No access token"))

            val response = apiService.getMediaSettings(accessToken = accessToken)

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
    fun getCurrentSettings(): MediaSettings? {
        return _settings.value
    }

    // ==================== ОБНОВЛЕНИЕ НАСТРОЕК ====================

    /**
     * Обновить настройки на сервере
     */
    suspend fun updateSettings(
        autoDownloadPhotos: MediaSettings.AutoDownloadMode? = null,
        autoDownloadVideos: MediaSettings.AutoDownloadMode? = null,
        autoDownloadAudio: MediaSettings.AutoDownloadMode? = null,
        autoDownloadDocuments: MediaSettings.AutoDownloadMode? = null,
        compressPhotos: Boolean? = null,
        compressVideos: Boolean? = null,
        backupEnabled: Boolean? = null,
        markBackupComplete: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val accessToken = UserSession.accessToken
                ?: return@withContext Result.failure(Exception("No access token"))

            val response = apiService.updateMediaSettings(
                accessToken = accessToken,
                autoDownloadPhotos = autoDownloadPhotos?.value,
                autoDownloadVideos = autoDownloadVideos?.value,
                autoDownloadAudio = autoDownloadAudio?.value,
                autoDownloadDocuments = autoDownloadDocuments?.value,
                compressPhotos = compressPhotos?.toString(),
                compressVideos = compressVideos?.toString(),
                backupEnabled = backupEnabled?.toString(),
                markBackupComplete = if (markBackupComplete) "true" else null
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
     * Упрощенный метод для обновления одной настройки
     */
    suspend fun updateAutoDownloadPhotos(mode: MediaSettings.AutoDownloadMode): Result<String> {
        return updateSettings(autoDownloadPhotos = mode)
    }

    suspend fun updateAutoDownloadVideos(mode: MediaSettings.AutoDownloadMode): Result<String> {
        return updateSettings(autoDownloadVideos = mode)
    }

    suspend fun updateAutoDownloadAudio(mode: MediaSettings.AutoDownloadMode): Result<String> {
        return updateSettings(autoDownloadAudio = mode)
    }

    suspend fun updateAutoDownloadDocuments(mode: MediaSettings.AutoDownloadMode): Result<String> {
        return updateSettings(autoDownloadDocuments = mode)
    }

    suspend fun updateCompressPhotos(compress: Boolean): Result<String> {
        return updateSettings(compressPhotos = compress)
    }

    suspend fun updateCompressVideos(compress: Boolean): Result<String> {
        return updateSettings(compressVideos = compress)
    }

    suspend fun updateBackupEnabled(enabled: Boolean): Result<String> {
        return updateSettings(backupEnabled = enabled)
    }

    /**
     * Отметить завершение бэкапа (обновляет last_backup_time на сервере)
     */
    suspend fun markBackupComplete(): Result<String> {
        return updateSettings(markBackupComplete = true)
    }
}
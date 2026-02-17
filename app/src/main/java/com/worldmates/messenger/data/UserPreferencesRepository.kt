package com.worldmates.messenger.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val PREFS_NAME = "worldmates_prefs"

private val Context.dataStore by preferencesDataStore(name = PREFS_NAME)

/**
 * Менеджер для сохранения и загрузки пользовательских данных с использованием DataStore
 */
class UserPreferencesRepository(private val context: Context) {

    companion object {
        private val ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val USER_ID = longPreferencesKey("user_id")
        private val USERNAME = stringPreferencesKey("username")
        private val USER_AVATAR = stringPreferencesKey("user_avatar")
        private val USER_EMAIL = stringPreferencesKey("user_email")
        private val DEVICE_ID = stringPreferencesKey("device_id")
        private val FCM_TOKEN = stringPreferencesKey("fcm_token")
        private val LAST_LOGIN_TIME = longPreferencesKey("last_login_time")
        private val IS_PREMIUM = booleanPreferencesKey("is_premium")
        private val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        private val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        private val AUTO_DOWNLOAD_MEDIA = booleanPreferencesKey("auto_download_media")
        private val THEME_MODE = stringPreferencesKey("theme_mode") // "light", "dark", "system"
        private val LANGUAGE = stringPreferencesKey("language")
    }

    val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            accessToken = prefs[ACCESS_TOKEN] ?: "",
            userId = prefs[USER_ID] ?: 0L,
            username = prefs[USERNAME] ?: "",
            userAvatar = prefs[USER_AVATAR] ?: "",
            userEmail = prefs[USER_EMAIL] ?: "",
            deviceId = prefs[DEVICE_ID] ?: "",
            fcmToken = prefs[FCM_TOKEN] ?: "",
            lastLoginTime = prefs[LAST_LOGIN_TIME] ?: 0L,
            isPremium = prefs[IS_PREMIUM] ?: false,
            notificationsEnabled = prefs[NOTIFICATIONS_ENABLED] ?: true,
            soundEnabled = prefs[SOUND_ENABLED] ?: true,
            vibrationEnabled = prefs[VIBRATION_ENABLED] ?: true,
            autoDownloadMedia = prefs[AUTO_DOWNLOAD_MEDIA] ?: true,
            themeMode = prefs[THEME_MODE] ?: "system",
            language = prefs[LANGUAGE] ?: "uk"
        )
    }

    suspend fun saveUserSession(
        accessToken: String,
        userId: Long,
        username: String,
        userAvatar: String,
        userEmail: String,
        deviceId: String,
        isPremium: Boolean = false
    ) {
        context.dataStore.edit { prefs ->
            prefs[ACCESS_TOKEN] = accessToken
            prefs[USER_ID] = userId
            prefs[USERNAME] = username
            prefs[USER_AVATAR] = userAvatar
            prefs[USER_EMAIL] = userEmail
            prefs[DEVICE_ID] = deviceId
            prefs[LAST_LOGIN_TIME] = System.currentTimeMillis()
            prefs[IS_PREMIUM] = isPremium
        }
    }

    suspend fun saveFcmToken(fcmToken: String) {
        context.dataStore.edit { prefs ->
            prefs[FCM_TOKEN] = fcmToken
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[SOUND_ENABLED] = enabled
        }
    }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[VIBRATION_ENABLED] = enabled
        }
    }

    suspend fun setAutoDownloadMedia(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[AUTO_DOWNLOAD_MEDIA] = enabled
        }
    }

    suspend fun setThemeMode(themeMode: String) {
        context.dataStore.edit { prefs ->
            prefs[THEME_MODE] = themeMode
        }
    }

    suspend fun setLanguage(language: String) {
        context.dataStore.edit { prefs ->
            prefs[LANGUAGE] = language
        }
    }

    suspend fun clearUserSession() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}

/**
 * Data class для хранения пользовательских предпочтений
 */
data class UserPreferences(
    val accessToken: String = "",
    val userId: Long = 0L,
    val username: String = "",
    val userAvatar: String = "",
    val userEmail: String = "",
    val deviceId: String = "",
    val fcmToken: String = "",
    val lastLoginTime: Long = 0L,
    val isPremium: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val autoDownloadMedia: Boolean = true,
    val themeMode: String = "system",
    val language: String = "uk"
)

/**
 * Расширенный UserSession с поддержкой DataStore
 */
object UserSessionManager {
    private var _preferences: UserPreferences? = null

    val preferences: UserPreferences
        get() = _preferences ?: UserPreferences()

    fun setPreferences(prefs: UserPreferences) {
        _preferences = prefs
    }

    fun isLoggedIn(): Boolean = preferences.accessToken.isNotEmpty() && preferences.userId != 0L

    fun getUserId(): Long = preferences.userId

    fun getAccessToken(): String = preferences.accessToken

    fun getUsername(): String = preferences.username

    fun getUserAvatar(): String = preferences.userAvatar

    fun isPremium(): Boolean = preferences.isPremium

    fun clearSession() {
        _preferences = UserPreferences()
    }
}
package com.worldmates.messenger.data

import android.content.Context
import android.content.SharedPreferences
import com.worldmates.messenger.WMApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object UserSession {
    private const val PREFS_NAME = "worldmates_session"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USERNAME = "username"
    private const val KEY_AVATAR = "avatar"
    private const val KEY_IS_PRO = "is_pro"
    private const val KEY_PRO_TYPE = "pro_type"
    private const val KEY_PRO_EXPIRES_AT = "pro_expires_at"
    private const val KEY_REGISTERED_AT = "registered_at"
    private const val KEY_STATUS_EMOJI = "status_emoji"
    private const val KEY_STATUS_TEXT  = "status_text"

    private val prefs: SharedPreferences by lazy {
        WMApplication.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Reactive avatar state for UI updates
    private val _avatarFlow = MutableStateFlow<String?>(null)
    val avatarFlow: StateFlow<String?> = _avatarFlow.asStateFlow()

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply()

    var userId: Long
        get() = prefs.getLong(KEY_USER_ID, 0)
        set(value) = prefs.edit().putLong(KEY_USER_ID, value).apply()

    var username: String?
        get() = prefs.getString(KEY_USERNAME, null)
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    var avatar: String?
        get() = _avatarFlow.value ?: prefs.getString(KEY_AVATAR, null)
        set(value) {
            prefs.edit().putString(KEY_AVATAR, value).apply()
            _avatarFlow.value = value
        }

    var isPro: Int
        get() = prefs.getInt(KEY_IS_PRO, 0)
        set(value) = prefs.edit().putInt(KEY_IS_PRO, value).apply()

    /** Тип підписки: 0=free, 1=monthly, 2=yearly */
    var proType: Int
        get() = prefs.getInt(KEY_PRO_TYPE, 0)
        set(value) = prefs.edit().putInt(KEY_PRO_TYPE, value).apply()

    /** Unix-timestamp (мс) закінчення підписки, 0 = не встановлено */
    var proExpiresAt: Long
        get() = prefs.getLong(KEY_PRO_EXPIRES_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_PRO_EXPIRES_AT, value).apply()

    val isProActive: Boolean
        get() = isPro > 0 && (proExpiresAt == 0L || proExpiresAt > System.currentTimeMillis())

    /** Unix-timestamp (мс) першої реєстрації / першого входу */
    var registeredAt: Long
        get() = prefs.getLong(KEY_REGISTERED_AT, 0L)
        private set(value) = prefs.edit().putLong(KEY_REGISTERED_AT, value).apply()

    /** true якщо акаунт зареєстровано менше 5 діб тому */
    val isNewUser: Boolean
        get() {
            val ts = registeredAt
            if (ts == 0L) return false
            return System.currentTimeMillis() - ts < 5L * 24 * 60 * 60 * 1000
        }

    /**
     * Доступ до анімованих фонів чату:
     *  - активна Pro-підписка, АБО
     *  - перші 5 днів після реєстрації (trial)
     */
    val canUseAnimatedBackground: Boolean
        get() = isProActive || isNewUser

    val isLoggedIn: Boolean
        get() = !accessToken.isNullOrEmpty() && userId > 0

    // Custom status (Premium feature)
    private val _statusEmojiFlow = MutableStateFlow(prefs.getString(KEY_STATUS_EMOJI, null))
    val statusEmojiFlow: StateFlow<String?> = _statusEmojiFlow.asStateFlow()

    private val _statusTextFlow = MutableStateFlow(prefs.getString(KEY_STATUS_TEXT, null))
    val statusTextFlow: StateFlow<String?> = _statusTextFlow.asStateFlow()

    var statusEmoji: String?
        get() = _statusEmojiFlow.value
        set(value) {
            prefs.edit().putString(KEY_STATUS_EMOJI, value).apply()
            _statusEmojiFlow.value = value
        }

    var statusText: String?
        get() = _statusTextFlow.value
        set(value) {
            prefs.edit().putString(KEY_STATUS_TEXT, value).apply()
            _statusTextFlow.value = value
        }

    init {
        // Initialize avatar flow from SharedPreferences on startup
        _avatarFlow.value = prefs.getString(KEY_AVATAR, null)
    }

    fun saveSession(
        token: String,
        id: Long,
        username: String? = null,
        avatar: String? = null,
        isPro: Int = 0,
        proType: Int = 0,
        proExpiresAt: Long = 0L
    ) {
        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, token)
            putLong(KEY_USER_ID, id)
            putString(KEY_USERNAME, username)
            putString(KEY_AVATAR, avatar)
            putInt(KEY_IS_PRO, isPro)
            putInt(KEY_PRO_TYPE, proType)
            putLong(KEY_PRO_EXPIRES_AT, proExpiresAt)
            // Запам'ятовуємо дату першого входу один раз
            if (!prefs.contains(KEY_REGISTERED_AT)) {
                putLong(KEY_REGISTERED_AT, System.currentTimeMillis())
            }
            commit() // Синхронне збереження — токен гарантованно записаний
        }
        _avatarFlow.value = avatar
    }

    /** Оновити лише статус підписки (без перезапису сесії) */
    fun updateProStatus(isPro: Int, proType: Int = 0, proExpiresAt: Long = 0L) {
        prefs.edit().apply {
            putInt(KEY_IS_PRO, isPro)
            putInt(KEY_PRO_TYPE, proType)
            putLong(KEY_PRO_EXPIRES_AT, proExpiresAt)
            apply()
        }
    }

    fun clearSession() {
        prefs.edit().clear().apply()
        _avatarFlow.value = null
    }
}
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

    val isLoggedIn: Boolean
        get() = !accessToken.isNullOrEmpty() && userId > 0

    init {
        // Initialize avatar flow from SharedPreferences on startup
        _avatarFlow.value = prefs.getString(KEY_AVATAR, null)
    }

    fun saveSession(token: String, id: Long, username: String? = null, avatar: String? = null, isPro: Int = 0) {
        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, token)
            putLong(KEY_USER_ID, id)
            putString(KEY_USERNAME, username)
            putString(KEY_AVATAR, avatar)
            putInt(KEY_IS_PRO, isPro)
            commit() // Синхронное сохранение, чтобы токен гарантированно записался
        }
        // Update avatar flow
        _avatarFlow.value = avatar
    }

    fun clearSession() {
        prefs.edit().clear().apply()
        _avatarFlow.value = null
    }
}
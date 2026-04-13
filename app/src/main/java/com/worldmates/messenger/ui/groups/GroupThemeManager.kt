package com.worldmates.messenger.ui.groups

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.worldmates.messenger.network.NodeRetrofitClient
import com.worldmates.messenger.ui.preferences.BubbleStyle
import com.worldmates.messenger.ui.theme.PresetBackground
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Менеджер кастомних тем для групових чатів.
 *
 * Зберігає кастомізацію кожної групи локально в SharedPreferences
 * та синхронізує з сервером через Node.js API:
 *   POST /api/node/group/customization/get    { group_id }
 *   POST /api/node/group/customization/update { group_id, bubble_style, preset_background, accent_color }
 *   POST /api/node/group/customization/reset  { group_id }
 */
object GroupThemeManager {
    private const val TAG = "GroupThemeManager"
    private const val PREFS_NAME = "group_themes"
    private const val KEY_THEMES = "themes_map"

    private val gson = Gson()
    private var prefs: SharedPreferences? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _themes = MutableStateFlow<Map<Long, GroupTheme>>(emptyMap())
    val themes: StateFlow<Map<Long, GroupTheme>> = _themes.asStateFlow()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadThemes()
    }

    private fun loadThemes() {
        val json = prefs?.getString(KEY_THEMES, null)
        if (json != null) {
            val type = object : TypeToken<Map<Long, GroupTheme>>() {}.type
            _themes.value = gson.fromJson(json, type) ?: emptyMap()
        }
    }

    fun getGroupTheme(groupId: Long): GroupTheme? {
        return _themes.value[groupId]
    }

    fun setGroupTheme(groupId: Long, theme: GroupTheme) {
        val updated = _themes.value.toMutableMap()
        updated[groupId] = theme
        _themes.value = updated
        saveThemes()
        // Синхронізуємо з сервером
        syncThemeToServer(groupId, theme)
    }

    fun removeGroupTheme(groupId: Long) {
        val updated = _themes.value.toMutableMap()
        updated.remove(groupId)
        _themes.value = updated
        saveThemes()
        // Скидаємо на сервері
        resetThemeOnServer(groupId)
    }

    fun hasCustomTheme(groupId: Long): Boolean {
        return _themes.value.containsKey(groupId)
    }

    private fun saveThemes() {
        prefs?.edit()?.putString(KEY_THEMES, gson.toJson(_themes.value))?.apply()
    }

    // ==================== SERVER SYNC ====================

    /**
     * Завантажити тему групи з сервера та оновити локальний кеш
     */
    fun loadThemeFromServer(groupId: Long) {
        scope.launch {
            try {
                _syncState.value = SyncState.Loading

                val response = NodeRetrofitClient.groupApi.getGroupCustomization(groupId = groupId)

                if (response.apiStatus == 200 && response.customization != null) {
                    val serverTheme = GroupTheme(
                        bubbleStyle = response.customization.bubbleStyle,
                        presetBackgroundId = response.customization.presetBackground,
                        accentColor = response.customization.accentColor,
                        enabledByAdmin = response.customization.enabledByAdmin
                    )

                    // Оновлюємо локальний кеш тільки якщо сервер має новішу версію
                    val localTheme = _themes.value[groupId]
                    if (localTheme == null || serverTheme != localTheme) {
                        val updated = _themes.value.toMutableMap()
                        updated[groupId] = serverTheme
                        _themes.value = updated
                        saveThemes()
                        Log.d(TAG, "Theme loaded from server for group $groupId")
                    }

                    _syncState.value = SyncState.Success
                } else {
                    Log.w(TAG, "Failed to load theme from server: ${response.errorMessage}")
                    _syncState.value = SyncState.Error(response.errorMessage ?: "Unknown error")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading theme from server", e)
                _syncState.value = SyncState.Error(e.localizedMessage ?: "Network error")
            }
        }
    }

    /**
     * Зберегти тему групи на сервер
     */
    private fun syncThemeToServer(groupId: Long, theme: GroupTheme) {
        scope.launch {
            try {
                val response = NodeRetrofitClient.groupApi.updateGroupCustomization(
                    groupId = groupId,
                    bubbleStyle = theme.bubbleStyle,
                    presetBackground = theme.presetBackgroundId,
                    accentColor = theme.accentColor,
                    enabledByAdmin = if (theme.enabledByAdmin) "1" else "0"
                )

                if (response.apiStatus == 200) {
                    Log.d(TAG, "Theme synced to server for group $groupId")
                } else {
                    Log.w(TAG, "Failed to sync theme to server: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing theme to server for group $groupId", e)
            }
        }
    }

    /**
     * Скинути тему групи на сервері
     */
    private fun resetThemeOnServer(groupId: Long) {
        scope.launch {
            try {
                val response = NodeRetrofitClient.groupApi.resetGroupCustomization(groupId = groupId)

                if (response.apiStatus == 200) {
                    Log.d(TAG, "Theme reset on server for group $groupId")
                } else {
                    Log.w(TAG, "Failed to reset theme on server: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resetting theme on server for group $groupId", e)
            }
        }
    }
}

/**
 * Стан синхронізації з сервером
 */
sealed class SyncState {
    object Idle : SyncState()
    object Loading : SyncState()
    object Success : SyncState()
    data class Error(val message: String) : SyncState()
}

/**
 * Кастомна тема для групового чату.
 * Використовує тільки підключені до рендерингу налаштування.
 */
data class GroupTheme(
    val bubbleStyle: String = BubbleStyle.STANDARD.name,
    val presetBackgroundId: String = PresetBackground.MIDNIGHT.id,
    val accentColor: String = "#2196F3",
    val enabledByAdmin: Boolean = true
) {
    fun getBubbleStyle(): BubbleStyle {
        return try {
            BubbleStyle.valueOf(bubbleStyle)
        } catch (e: Exception) {
            BubbleStyle.STANDARD
        }
    }

    fun getPresetBackground(): PresetBackground {
        return PresetBackground.values().find { it.id == presetBackgroundId }
            ?: PresetBackground.MIDNIGHT
    }

    companion object {
        /** Готові шаблони для швидкого вибору */
        val PRESETS = listOf(
            GroupThemePreset(
                name = "Стандартна",
                emoji = "💬",
                theme = GroupTheme()
            ),
            GroupThemePreset(
                name = "Бізнес",
                emoji = "💼",
                theme = GroupTheme(
                    bubbleStyle = BubbleStyle.MINIMAL.name,
                    presetBackgroundId = PresetBackground.LAVENDER.id,
                    accentColor = "#607D8B"
                )
            ),
            GroupThemePreset(
                name = "Неон",
                emoji = "⚡",
                theme = GroupTheme(
                    bubbleStyle = BubbleStyle.NEON.name,
                    presetBackgroundId = PresetBackground.COSMIC.id,
                    accentColor = "#E040FB"
                )
            ),
            GroupThemePreset(
                name = "Природа",
                emoji = "🌿",
                theme = GroupTheme(
                    bubbleStyle = BubbleStyle.STANDARD.name,
                    presetBackgroundId = PresetBackground.SPRING.id,
                    accentColor = "#4CAF50"
                )
            ),
            GroupThemePreset(
                name = "Романтика",
                emoji = "🌹",
                theme = GroupTheme(
                    bubbleStyle = BubbleStyle.MODERN.name,
                    presetBackgroundId = PresetBackground.COTTON_CANDY.id,
                    accentColor = "#E91E63"
                )
            ),
            GroupThemePreset(
                name = "Ретро",
                emoji = "📼",
                theme = GroupTheme(
                    bubbleStyle = BubbleStyle.RETRO.name,
                    presetBackgroundId = PresetBackground.FIRE.id,
                    accentColor = "#FF5722"
                )
            ),
            GroupThemePreset(
                name = "Зима",
                emoji = "❄️",
                theme = GroupTheme(
                    bubbleStyle = BubbleStyle.NEUMORPHISM.name,
                    presetBackgroundId = PresetBackground.WINTER.id,
                    accentColor = "#00BCD4"
                )
            ),
            GroupThemePreset(
                name = "Космос",
                emoji = "🚀",
                theme = GroupTheme(
                    bubbleStyle = BubbleStyle.GLASS.name,
                    presetBackgroundId = PresetBackground.COSMIC.id,
                    accentColor = "#7C4DFF"
                )
            )
        )
    }
}

/**
 * Готовий шаблон теми для групи
 */
data class GroupThemePreset(
    val name: String,
    val emoji: String,
    val theme: GroupTheme
)

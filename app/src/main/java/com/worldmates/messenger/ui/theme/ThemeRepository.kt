package com.worldmates.messenger.ui.theme

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore для хранения настроек темы
 */
private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "theme_preferences"
)

private const val TAG = "ThemeRepository"

/**
 * Репозиторий для управления настройками темы приложения
 */
class ThemeRepository(private val context: Context) {

    companion object {
        private val THEME_VARIANT_KEY = intPreferencesKey("theme_variant")
        private val DARK_THEME_KEY = booleanPreferencesKey("dark_theme")
        private val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color")
        private val SYSTEM_THEME_KEY = booleanPreferencesKey("system_theme")
        private val BACKGROUND_IMAGE_URI_KEY = stringPreferencesKey("background_image_uri")
        private val PRESET_BACKGROUND_ID_KEY = stringPreferencesKey("preset_background_id")
    }

    /**
     * Получить текущий вариант темы
     */
    val themeVariant: Flow<ThemeVariant> = context.themeDataStore.data
        .map { preferences ->
            val ordinal = preferences[THEME_VARIANT_KEY] ?: ThemeVariant.CLASSIC.ordinal
            val variant = ThemeVariant.fromOrdinal(ordinal)
            Log.d(TAG, "Reading theme variant: ${variant.name}")
            variant
        }

    /**
     * Получить настройку темной темы
     */
    val darkTheme: Flow<Boolean> = context.themeDataStore.data
        .map { preferences ->
            val dark = preferences[DARK_THEME_KEY] ?: false
            Log.d(TAG, "Reading dark theme: $dark")
            dark
        }

    /**
     * Получить настройку динамических цветов (Material You)
     */
    val dynamicColor: Flow<Boolean> = context.themeDataStore.data
        .map { preferences ->
            val dynamic = preferences[DYNAMIC_COLOR_KEY] ?: false
            Log.d(TAG, "Reading dynamic color: $dynamic")
            dynamic
        }

    /**
     * Следовать ли системной теме
     */
    val systemTheme: Flow<Boolean> = context.themeDataStore.data
        .map { preferences ->
            val system = preferences[SYSTEM_THEME_KEY] ?: false  // Изменено с true на false
            Log.d(TAG, "Reading system theme: $system")
            system
        }

    /**
     * Получить URI кастомного фонового изображения
     */
    val backgroundImageUri: Flow<String?> = context.themeDataStore.data
        .map { preferences ->
            val uri = preferences[BACKGROUND_IMAGE_URI_KEY]
            Log.d(TAG, "Reading background image URI: $uri")
            uri
        }

    /**
     * Получить ID готового фонового градієнта
     */
    val presetBackgroundId: Flow<String?> = context.themeDataStore.data
        .map { preferences ->
            val id = preferences[PRESET_BACKGROUND_ID_KEY]
            Log.d(TAG, "Reading preset background ID: $id")
            id
        }

    /**
     * Сохранить вариант темы
     */
    suspend fun setThemeVariant(variant: ThemeVariant) {
        Log.d(TAG, "Saving theme variant: ${variant.name}")
        context.themeDataStore.edit { preferences ->
            preferences[THEME_VARIANT_KEY] = variant.ordinal
        }
    }

    /**
     * Сохранить настройку темной темы
     */
    suspend fun setDarkTheme(dark: Boolean) {
        Log.d(TAG, "Saving dark theme: $dark")
        context.themeDataStore.edit { preferences ->
            preferences[DARK_THEME_KEY] = dark
        }
    }

    /**
     * Сохранить настройку динамических цветов
     */
    suspend fun setDynamicColor(enabled: Boolean) {
        Log.d(TAG, "Saving dynamic color: $enabled")
        context.themeDataStore.edit { preferences ->
            preferences[DYNAMIC_COLOR_KEY] = enabled
        }
    }

    /**
     * Сохранить настройку следования системной теме
     */
    suspend fun setSystemTheme(enabled: Boolean) {
        Log.d(TAG, "Saving system theme: $enabled")
        context.themeDataStore.edit { preferences ->
            preferences[SYSTEM_THEME_KEY] = enabled
        }
    }

    /**
     * Сохранить URI кастомного фонового изображения
     */
    suspend fun setBackgroundImageUri(uri: String?) {
        Log.d(TAG, "Saving background image URI: $uri")
        context.themeDataStore.edit { preferences ->
            if (uri != null) {
                preferences[BACKGROUND_IMAGE_URI_KEY] = uri
                // При встановленні кастомного фону, видаляємо preset
                preferences.remove(PRESET_BACKGROUND_ID_KEY)
            } else {
                preferences.remove(BACKGROUND_IMAGE_URI_KEY)
            }
        }
    }

    /**
     * Зберегти ID готового фонового градієнта
     */
    suspend fun setPresetBackgroundId(id: String?) {
        Log.d(TAG, "Saving preset background ID: $id")
        context.themeDataStore.edit { preferences ->
            if (id != null) {
                preferences[PRESET_BACKGROUND_ID_KEY] = id
                // При встановленні preset, видаляємо кастомний фон
                preferences.remove(BACKGROUND_IMAGE_URI_KEY)
            } else {
                preferences.remove(PRESET_BACKGROUND_ID_KEY)
            }
        }
    }

    /**
     * Сбросить все настройки темы к значениям по умолчанию
     */
    suspend fun resetToDefaults() {
        context.themeDataStore.edit { preferences ->
            preferences.clear()
        }
    }
}

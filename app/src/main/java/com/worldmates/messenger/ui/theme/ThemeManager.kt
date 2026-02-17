package com.worldmates.messenger.ui.theme

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "ThemeManager"

/**
 * Состояние темы приложения
 */
data class ThemeState(
    val variant: ThemeVariant = ThemeVariant.CLASSIC,
    val isDark: Boolean = false,
    val useDynamicColor: Boolean = false,
    val useSystemTheme: Boolean = false,  // Изменено с true на false
    val backgroundImageUri: String? = null,  // URI кастомного фонового изображения
    val presetBackgroundId: String? = null  // ID готового фонового градієнта
)

/**
 * ViewModel для управления темой приложения
 */
class ThemeViewModel(private val repository: ThemeRepository) : ViewModel() {

    /**
     * Текущее состояние темы
     */
    val themeState: StateFlow<ThemeState> = combine(
        repository.themeVariant,
        repository.darkTheme,
        repository.dynamicColor,
        repository.systemTheme,
        combine(repository.backgroundImageUri, repository.presetBackgroundId) { bgUri, presetBgId ->
            Pair(bgUri, presetBgId)
        }
    ) { variant, dark, dynamic, system, backgrounds ->
        val (bgUri, presetBgId) = backgrounds
        Log.d(TAG, "ThemeState updated: variant=$variant, dark=$dark, dynamic=$dynamic, system=$system, bgUri=$bgUri, presetBgId=$presetBgId")
        ThemeState(
            variant = variant,
            isDark = dark,
            useDynamicColor = dynamic,
            useSystemTheme = system,
            backgroundImageUri = bgUri,
            presetBackgroundId = presetBgId
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = ThemeState()
    )

    /**
     * Установить вариант темы
     */
    fun setThemeVariant(variant: ThemeVariant) {
        Log.d(TAG, "Setting theme variant: ${variant.name}")
        viewModelScope.launch {
            repository.setThemeVariant(variant)
        }
    }

    /**
     * Переключить темную/светлую тему
     */
    fun toggleDarkTheme() {
        viewModelScope.launch {
            val currentDark = themeState.value.isDark
            repository.setDarkTheme(!currentDark)
            // При ручном переключении отключаем следование системной теме
            repository.setSystemTheme(false)
        }
    }

    /**
     * Установить темную тему
     */
    fun setDarkTheme(dark: Boolean) {
        viewModelScope.launch {
            repository.setDarkTheme(dark)
        }
    }

    /**
     * Переключить динамические цвета (Material You)
     */
    fun toggleDynamicColor() {
        viewModelScope.launch {
            val currentDynamic = themeState.value.useDynamicColor
            repository.setDynamicColor(!currentDynamic)
        }
    }

    /**
     * Установить использование динамических цветов
     */
    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            repository.setDynamicColor(enabled)
        }
    }

    /**
     * Переключить следование системной теме
     */
    fun toggleSystemTheme() {
        viewModelScope.launch {
            val currentSystem = themeState.value.useSystemTheme
            repository.setSystemTheme(!currentSystem)
        }
    }

    /**
     * Установить следование системной теме
     */
    fun setSystemTheme(enabled: Boolean) {
        viewModelScope.launch {
            repository.setSystemTheme(enabled)
        }
    }

    /**
     * Сбросить все настройки к значениям по умолчанию
     */
    fun resetToDefaults() {
        viewModelScope.launch {
            repository.resetToDefaults()
        }
    }

    /**
     * Установить URI кастомного фонового изображения
     */
    fun setBackgroundImageUri(uri: String?) {
        Log.d(TAG, "Setting background image URI: $uri")
        viewModelScope.launch {
            repository.setBackgroundImageUri(uri)
        }
    }

    /**
     * Встановити ID готового фонового градієнта
     */
    fun setPresetBackgroundId(id: String?) {
        Log.d(TAG, "Setting preset background ID: $id")
        viewModelScope.launch {
            repository.setPresetBackgroundId(id)
        }
    }

    /**
     * Проверить, доступны ли динамические цвета на этом устройстве
     */
    fun isDynamicColorAvailable(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }
}

/**
 * Синглтон для глобального доступа к ThemeManager
 */
object ThemeManager {
    private var viewModel: ThemeViewModel? = null

    /**
     * Инициализировать ThemeManager
     */
    fun initialize(context: Context) {
        if (viewModel == null) {
            val repository = ThemeRepository(context.applicationContext)
            viewModel = ThemeViewModel(repository)
        }
    }

    /**
     * Получить ViewModel темы
     */
    fun getViewModel(context: Context): ThemeViewModel {
        if (viewModel == null) {
            initialize(context)
        }
        return viewModel!!
    }
}

/**
 * Composable-функция для получения состояния темы
 */
@Composable
fun rememberThemeState(): ThemeState {
    val context = LocalContext.current
    val viewModel = remember {
        Log.d(TAG, "Creating/getting ThemeViewModel")
        ThemeManager.getViewModel(context)
    }
    val themeState by viewModel.themeState.collectAsState()
    val systemInDarkTheme = isSystemInDarkTheme()

    Log.d(TAG, "rememberThemeState: variant=${themeState.variant.name}, dark=${themeState.isDark}, system=${themeState.useSystemTheme}")

    // Если включено следование системной теме, используем системную настройку
    return if (themeState.useSystemTheme) {
        themeState.copy(isDark = systemInDarkTheme)
    } else {
        themeState
    }
}

/**
 * Composable-функция для получения ThemeViewModel
 */
@Composable
fun rememberThemeViewModel(): ThemeViewModel {
    val context = LocalContext.current
    return remember {
        Log.d(TAG, "Getting ThemeViewModel in rememberThemeViewModel")
        ThemeManager.getViewModel(context)
    }
}

/**
 * Wrapper для WorldMatesTheme с автоматическим управлением через ThemeManager
 */
@Composable
fun WorldMatesThemedApp(
    content: @Composable () -> Unit
) {
    val themeState = rememberThemeState()

    Log.d(TAG, "Applying theme: variant=${themeState.variant.name}, dark=${themeState.isDark}, dynamic=${themeState.useDynamicColor}")

    WorldMatesTheme(
        darkTheme = themeState.isDark,
        themeVariant = themeState.variant,
        dynamicColor = themeState.useDynamicColor,
        content = content
    )
}

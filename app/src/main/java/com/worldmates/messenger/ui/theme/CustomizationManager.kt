package com.worldmates.messenger.ui.theme

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val TAG = "CustomizationManager"

// DataStore extension
private val Context.customizationDataStore by preferencesDataStore(name = "customization_prefs")

/**
 * Стан кастомізації інтерфейсу
 */
data class CustomizationState(
    val bubbleStyle: MessageBubbleStyle = MessageBubbleStyle.MODERN,
    val animationStyle: MessageAnimationStyle = MessageAnimationStyle.FADE,
    val fontVariant: FontVariant = FontVariant.DEFAULT,
    val enableParticleEffects: Boolean = false,
    val enableMessageAnimations: Boolean = true,
    val enableSmoothScrolling: Boolean = true,
    val bubbleOpacity: Float = 1.0f,  // 0.5 - 1.0
    val cornerRadius: Int = 20  // 8 - 32 dp
)

/**
 * Repository для зберігання налаштувань кастомізації
 */
class CustomizationRepository(private val context: Context) {
    companion object {
        private val BUBBLE_STYLE_KEY = intPreferencesKey("bubble_style")
        private val ANIMATION_STYLE_KEY = intPreferencesKey("animation_style")
        private val FONT_VARIANT_KEY = intPreferencesKey("font_variant")
        private val PARTICLE_EFFECTS_KEY = stringPreferencesKey("particle_effects")
        private val MESSAGE_ANIMATIONS_KEY = stringPreferencesKey("message_animations")
        private val SMOOTH_SCROLLING_KEY = stringPreferencesKey("smooth_scrolling")
        private val BUBBLE_OPACITY_KEY = stringPreferencesKey("bubble_opacity")
        private val CORNER_RADIUS_KEY = intPreferencesKey("corner_radius")
    }

    val customizationState = context.customizationDataStore.data.map { prefs ->
        CustomizationState(
            bubbleStyle = MessageBubbleStyle.fromOrdinal(
                prefs[BUBBLE_STYLE_KEY] ?: MessageBubbleStyle.MODERN.ordinal
            ),
            animationStyle = MessageAnimationStyle.fromOrdinal(
                prefs[ANIMATION_STYLE_KEY] ?: MessageAnimationStyle.FADE.ordinal
            ),
            fontVariant = FontVariant.fromOrdinal(
                prefs[FONT_VARIANT_KEY] ?: FontVariant.DEFAULT.ordinal
            ),
            enableParticleEffects = prefs[PARTICLE_EFFECTS_KEY]?.toBoolean() ?: false,
            enableMessageAnimations = prefs[MESSAGE_ANIMATIONS_KEY]?.toBoolean() ?: true,
            enableSmoothScrolling = prefs[SMOOTH_SCROLLING_KEY]?.toBoolean() ?: true,
            bubbleOpacity = prefs[BUBBLE_OPACITY_KEY]?.toFloatOrNull() ?: 1.0f,
            cornerRadius = prefs[CORNER_RADIUS_KEY] ?: 20
        )
    }

    suspend fun setBubbleStyle(style: MessageBubbleStyle) {
        context.customizationDataStore.edit { prefs ->
            prefs[BUBBLE_STYLE_KEY] = style.ordinal
        }
    }

    suspend fun setAnimationStyle(style: MessageAnimationStyle) {
        context.customizationDataStore.edit { prefs ->
            prefs[ANIMATION_STYLE_KEY] = style.ordinal
        }
    }

    suspend fun setFontVariant(variant: FontVariant) {
        context.customizationDataStore.edit { prefs ->
            prefs[FONT_VARIANT_KEY] = variant.ordinal
        }
    }

    suspend fun setParticleEffects(enabled: Boolean) {
        context.customizationDataStore.edit { prefs ->
            prefs[PARTICLE_EFFECTS_KEY] = enabled.toString()
        }
    }

    suspend fun setMessageAnimations(enabled: Boolean) {
        context.customizationDataStore.edit { prefs ->
            prefs[MESSAGE_ANIMATIONS_KEY] = enabled.toString()
        }
    }

    suspend fun setSmoothScrolling(enabled: Boolean) {
        context.customizationDataStore.edit { prefs ->
            prefs[SMOOTH_SCROLLING_KEY] = enabled.toString()
        }
    }

    suspend fun setBubbleOpacity(opacity: Float) {
        context.customizationDataStore.edit { prefs ->
            prefs[BUBBLE_OPACITY_KEY] = opacity.coerceIn(0.5f, 1.0f).toString()
        }
    }

    suspend fun setCornerRadius(radius: Int) {
        context.customizationDataStore.edit { prefs ->
            prefs[CORNER_RADIUS_KEY] = radius.coerceIn(8, 32)
        }
    }

    suspend fun resetToDefaults() {
        context.customizationDataStore.edit { prefs ->
            prefs.clear()
        }
    }
}

/**
 * ViewModel для управління кастомізацією
 */
class CustomizationViewModel(private val repository: CustomizationRepository) : ViewModel() {

    private val _customizationState = MutableStateFlow(CustomizationState())
    val customizationState: StateFlow<CustomizationState> = _customizationState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.customizationState.collect { state ->
                _customizationState.value = state
                Log.d(TAG, "Customization state updated: $state")
            }
        }
    }

    fun setBubbleStyle(style: MessageBubbleStyle) {
        viewModelScope.launch {
            repository.setBubbleStyle(style)
            Log.d(TAG, "Bubble style set to: ${style.displayName}")
        }
    }

    fun setAnimationStyle(style: MessageAnimationStyle) {
        viewModelScope.launch {
            repository.setAnimationStyle(style)
            Log.d(TAG, "Animation style set to: ${style.displayName}")
        }
    }

    fun setFontVariant(variant: FontVariant) {
        viewModelScope.launch {
            repository.setFontVariant(variant)
            Log.d(TAG, "Font variant set to: ${variant.displayName}")
        }
    }

    fun toggleParticleEffects() {
        viewModelScope.launch {
            val newValue = !_customizationState.value.enableParticleEffects
            repository.setParticleEffects(newValue)
        }
    }

    fun toggleMessageAnimations() {
        viewModelScope.launch {
            val newValue = !_customizationState.value.enableMessageAnimations
            repository.setMessageAnimations(newValue)
        }
    }

    fun toggleSmoothScrolling() {
        viewModelScope.launch {
            val newValue = !_customizationState.value.enableSmoothScrolling
            repository.setSmoothScrolling(newValue)
        }
    }

    fun setBubbleOpacity(opacity: Float) {
        viewModelScope.launch {
            repository.setBubbleOpacity(opacity)
        }
    }

    fun setCornerRadius(radius: Int) {
        viewModelScope.launch {
            repository.setCornerRadius(radius)
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            repository.resetToDefaults()
            Log.d(TAG, "Customization reset to defaults")
        }
    }
}

/**
 * Singleton для глобального доступу до CustomizationManager
 */
object CustomizationManager {
    private var viewModel: CustomizationViewModel? = null

    fun initialize(context: Context) {
        if (viewModel == null) {
            val repository = CustomizationRepository(context.applicationContext)
            viewModel = CustomizationViewModel(repository)
        }
    }

    fun getViewModel(context: Context): CustomizationViewModel {
        if (viewModel == null) {
            initialize(context)
        }
        return viewModel!!
    }
}
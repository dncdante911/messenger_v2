package com.worldmates.messenger.ui.calls

import android.content.Context

enum class CallBackground(val isPremium: Boolean) {
    DEFAULT(false),
    SUNSET(false),
    OCEAN(false),
    AURORA(true),
    STARFIELD(true),
    NEON_CITY(true),
    GRADIENT_SHIFT(true),
    CRYSTAL(true)
}

object CallBackgroundManager {
    private const val PREFS_NAME = "call_background_prefs"
    private const val KEY_SELECTED = "selected_background"

    fun save(context: Context, bg: CallBackground) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SELECTED, bg.name).apply()
    }

    fun load(context: Context): CallBackground {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_SELECTED, CallBackground.DEFAULT.name)
        return try {
            CallBackground.valueOf(name ?: CallBackground.DEFAULT.name)
        } catch (e: Exception) {
            CallBackground.DEFAULT
        }
    }

    fun reset(context: Context) = save(context, CallBackground.DEFAULT)
}

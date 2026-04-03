package com.worldmates.messenger.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import java.util.Locale

/**
 * LanguageManager — керування мовою застосунку (Ukrainian/Russian).
 * Зберігає вибір у SharedPreferences та застосовує locale до контексту.
 */
object LanguageManager {

    const val LANG_UK = "uk"
    const val LANG_RU = "ru"

    private const val PREFS_NAME = "wm_language_prefs"
    private const val KEY_LANGUAGE = "selected_language"
    private const val KEY_FIRST_LAUNCH = "first_launch_done"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** Поточна мова (uk або ru) */
    val currentLanguage: String
        get() = if (::prefs.isInitialized) prefs.getString(KEY_LANGUAGE, LANG_UK) ?: LANG_UK
                else LANG_UK

    /** Чи вже вибрано мову при першому запуску */
    val isLanguageSelected: Boolean
        get() = if (::prefs.isInitialized) prefs.getBoolean(KEY_FIRST_LAUNCH, false) else false

    /** Зберегти вибрану мову та позначити перший запуск як виконаний */
    fun setLanguage(language: String) {
        if (::prefs.isInitialized) {
            prefs.edit()
                .putString(KEY_LANGUAGE, language)
                .putBoolean(KEY_FIRST_LAUNCH, true)
                .apply()
        }
    }

    /**
     * Застосовує збережену мову до контексту (AndroidX AppCompat).
     * Викликається в attachBaseContext() кожної Activity.
     */
    fun applyLanguage(context: Context): Context {
        val lang = currentLanguage
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    /**
     * Оновлює locale для контексту без перестворення (для Application).
     */
    fun applyToConfiguration(context: Context) {
        val lang = currentLanguage
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = context.resources.configuration
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }

    fun getDisplayName(lang: String): String = when (lang) {
        LANG_UK -> "Українська"
        LANG_RU -> "Русский"
        else -> "Українська"
    }
}

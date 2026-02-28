package com.worldmates.messenger.ui.theme

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.worldmates.messenger.utils.LanguageManager

/**
 * Activity для налаштувань теми - доступна з меню чату
 */
class ThemeSettingsActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

        override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ініціалізуємо ThemeManager
        ThemeManager.initialize(this)

        setContent {
            WorldMatesThemedApp {
                ThemeSettingsScreen(
                    onBackClick = { finish() }
                )
            }
        }
    }
}

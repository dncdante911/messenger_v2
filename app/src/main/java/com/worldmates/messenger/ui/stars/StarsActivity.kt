package com.worldmates.messenger.ui.stars

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.worldmates.messenger.ui.theme.ThemeManager
import com.worldmates.messenger.ui.theme.WorldMatesThemedApp
import com.worldmates.messenger.utils.LanguageManager

class StarsActivity : AppCompatActivity() {

    private lateinit var viewModel: StarsViewModel

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        ThemeManager.initialize(this)
        viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        ).get(StarsViewModel::class.java)

        setContent {
            WorldMatesThemedApp {
                StarsScreen(
                    viewModel = viewModel,
                    onBack    = { finish() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Синхронізуємо баланс після повернення з браузера оплати
        viewModel.syncAfterPayment()
    }
}

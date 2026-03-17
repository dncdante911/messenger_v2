package com.worldmates.messenger.ui.business

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.worldmates.messenger.ui.theme.WorldMatesThemedApp
import com.worldmates.messenger.utils.LanguageManager

class BusinessActivity : AppCompatActivity() {

    private lateinit var viewModel: BusinessViewModel

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        ).get(BusinessViewModel::class.java)

        setContent {
            WorldMatesThemedApp {
                BusinessModeScreen(
                    viewModel = viewModel,
                    onBack    = { finish() }
                )
            }
        }
    }
}

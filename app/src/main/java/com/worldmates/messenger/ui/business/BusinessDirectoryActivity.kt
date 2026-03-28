package com.worldmates.messenger.ui.business

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.worldmates.messenger.ui.messages.MessagesActivity
import com.worldmates.messenger.ui.business.BusinessProfileViewActivity
import com.worldmates.messenger.ui.theme.WorldMatesThemedApp
import com.worldmates.messenger.utils.LanguageManager

class BusinessDirectoryActivity : AppCompatActivity() {

    private lateinit var viewModel: BusinessDirectoryViewModel

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        ).get(BusinessDirectoryViewModel::class.java)

        setContent {
            WorldMatesThemedApp {
                BusinessDirectoryScreen(
                    viewModel       = viewModel,
                    onBack          = { finish() },
                    onChatClick     = { userId, name ->
                        val intent = Intent(this, MessagesActivity::class.java)
                        intent.putExtra("recipient_id", userId)
                        intent.putExtra("recipient_name", name)
                        startActivity(intent)
                    },
                    onProfileClick  = { userId ->
                        val intent = Intent(this, BusinessProfileViewActivity::class.java)
                        intent.putExtra("user_id", userId)
                        startActivity(intent)
                    }
                )
            }
        }
    }
}

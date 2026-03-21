package com.worldmates.messenger.ui.groups

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.worldmates.messenger.ui.theme.WorldMatesThemedApp
import com.worldmates.messenger.utils.LanguageManager

/**
 * Activity для журналу адміністративних дій групи
 */
class GroupAdminLogsActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val groupId = intent.getLongExtra("group_id", 0L)
        if (groupId == 0L) {
            finish()
            return
        }

        setContent {
            WorldMatesThemedApp {
                GroupAdminLogsScreen(
                    groupId = groupId,
                    onBack = { finish() }
                )
            }
        }
    }
}

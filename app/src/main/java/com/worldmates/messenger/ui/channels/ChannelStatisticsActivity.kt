package com.worldmates.messenger.ui.channels

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import com.worldmates.messenger.ui.channels.components.ChannelStatisticsScreen
import com.worldmates.messenger.ui.theme.ThemeManager
import com.worldmates.messenger.ui.theme.WorldMatesThemedApp
import com.worldmates.messenger.utils.LanguageManager

/**
 * Повноекранна сторінка детальної статистики каналу (Telegram-style)
 */
class ChannelStatisticsActivity : AppCompatActivity() {

    private lateinit var detailsViewModel: ChannelDetailsViewModel
    private var channelId: Long = 0

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

        override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        channelId = intent.getLongExtra("channel_id", 0)
        val channelName = intent.getStringExtra("channel_name") ?: ""
        if (channelId == 0L) {
            finish()
            return
        }

        ThemeManager.initialize(this)
        detailsViewModel = ViewModelProvider(this)[ChannelDetailsViewModel::class.java]
        detailsViewModel.loadStatistics(channelId)

        setContent {
            WorldMatesThemedApp {
                val statistics by detailsViewModel.statistics.collectAsState()
                val isLoading  by detailsViewModel.isLoading.collectAsState()

                ChannelStatisticsScreen(
                    statistics  = statistics,
                    channelName = channelName,
                    isLoading   = isLoading,
                    onBackClick = { finish() },
                    onRefresh   = { detailsViewModel.loadStatistics(channelId) }
                )
            }
        }
    }
}

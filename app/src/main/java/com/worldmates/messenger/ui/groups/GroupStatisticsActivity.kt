package com.worldmates.messenger.ui.groups

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import com.worldmates.messenger.ui.groups.components.GroupStatisticsScreen
import com.worldmates.messenger.ui.theme.WorldMatesThemedApp
import com.worldmates.messenger.utils.LanguageManager

/**
 * Повноекранна сторінка детальної статистики групи
 */
class GroupStatisticsActivity : AppCompatActivity() {

    private lateinit var viewModel: GroupsViewModel
    private var groupId: Long = 0

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

        override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        groupId = intent.getLongExtra("group_id", 0)
        val groupName = intent.getStringExtra("group_name") ?: ""
        if (groupId == 0L) {
            finish()
            return
        }

        viewModel = ViewModelProvider(this)[GroupsViewModel::class.java]
        viewModel.loadGroupStatistics(groupId)

        setContent {
            WorldMatesThemedApp {
                val statistics by viewModel.groupStatistics.collectAsState()
                val isLoading by viewModel.isLoading.collectAsState()

                GroupStatisticsScreen(
                    statistics = statistics,
                    groupName = groupName,
                    isLoading = isLoading,
                    onBackClick = { finish() },
                    onRefresh = { viewModel.loadGroupStatistics(groupId) }
                )
            }
        }
    }
}

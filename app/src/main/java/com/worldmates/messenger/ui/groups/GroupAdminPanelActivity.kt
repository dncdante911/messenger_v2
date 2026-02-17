package com.worldmates.messenger.ui.groups

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.ui.groups.components.GroupAdminPanelScreen
import com.worldmates.messenger.ui.theme.WorldMatesThemedApp

/**
 * Activity для повної адмін-панелі групи
 */
class GroupAdminPanelActivity : AppCompatActivity() {

    private lateinit var viewModel: GroupsViewModel
    private var groupId: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        groupId = intent.getLongExtra("group_id", 0)
        if (groupId == 0L) {
            finish()
            return
        }

        viewModel = ViewModelProvider(this)[GroupsViewModel::class.java]

        // Завантажуємо дані групи
        viewModel.fetchGroupDetails(groupId)
        viewModel.fetchGroupMembers(groupId)
        viewModel.loadJoinRequests(groupId)
        viewModel.loadScheduledPosts(groupId)
        viewModel.loadGroupStatistics(groupId)

        setContent {
            WorldMatesThemedApp {
                val groups by viewModel.groupList.collectAsState()
                val members by viewModel.groupMembers.collectAsState()
                val joinRequests by viewModel.joinRequests.collectAsState()
                val scheduledPosts by viewModel.scheduledPosts.collectAsState()
                val statistics by viewModel.groupStatistics.collectAsState()
                val isLoading by viewModel.isLoading.collectAsState()

                val group = groups.find { it.id == groupId }

                if (group != null) {
                    GroupAdminPanelScreen(
                        group = group,
                        statistics = statistics,
                        joinRequests = joinRequests,
                        scheduledPosts = scheduledPosts,
                        members = members,
                        currentUserId = UserSession.userId ?: 0L,
                        isLoading = isLoading,
                        onBackClick = { finish() },
                        onSettingsChange = { settings ->
                            viewModel.updateGroupSettings(groupId, settings)
                        },
                        onPrivacyChange = { isPrivate ->
                            viewModel.updateGroupPrivacy(groupId, isPrivate)
                        },
                        onApproveJoinRequest = { request ->
                            viewModel.approveJoinRequest(request)
                        },
                        onRejectJoinRequest = { request ->
                            viewModel.rejectJoinRequest(request)
                        },
                        onRoleChange = { userId, newRole ->
                            viewModel.updateMemberRole(groupId, userId, newRole)
                        },
                        onCreateScheduledPost = { text, scheduledTime, mediaUrl, repeatType, notifyMembers, isPinned ->
                            viewModel.createScheduledPost(
                                groupId = groupId,
                                text = text,
                                scheduledTime = scheduledTime,
                                mediaUrl = mediaUrl,
                                repeatType = repeatType,
                                isPinned = isPinned,
                                notifyMembers = notifyMembers
                            )
                        },
                        onDeleteScheduledPost = { post ->
                            viewModel.deleteScheduledPost(post)
                        },
                        onPublishScheduledPost = { post ->
                            viewModel.publishScheduledPost(post)
                        },
                        onOpenStatistics = {
                            // Вже відкриті в адмін-панелі
                        },
                        onRefresh = {
                            viewModel.fetchGroupDetails(groupId)
                            viewModel.fetchGroupMembers(groupId)
                            viewModel.loadJoinRequests(groupId)
                            viewModel.loadScheduledPosts(groupId)
                            viewModel.loadGroupStatistics(groupId)
                        }
                    )
                }
            }
        }
    }
}

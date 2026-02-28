package com.worldmates.messenger.ui.groups.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.worldmates.messenger.R
import com.worldmates.messenger.data.model.*

/**
 * Повна адмін-панель групи з усіма налаштуваннями
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupAdminPanelScreen(
    group: Group,
    statistics: GroupStatistics?,
    joinRequests: List<GroupJoinRequest>,
    scheduledPosts: List<ScheduledPost>,
    members: List<GroupMember>,
    currentUserId: Long,
    isLoading: Boolean,
    onBackClick: () -> Unit,
    onSettingsChange: (GroupSettings) -> Unit,
    onPrivacyChange: (Boolean) -> Unit,
    onApproveJoinRequest: (GroupJoinRequest) -> Unit,
    onRejectJoinRequest: (GroupJoinRequest) -> Unit,
    onRoleChange: (Long, String) -> Unit,
    onCreateScheduledPost: (String, Long, String?, String, Boolean, Boolean) -> Unit,
    onDeleteScheduledPost: (ScheduledPost) -> Unit,
    onPublishScheduledPost: (ScheduledPost) -> Unit,
    onOpenStatistics: () -> Unit,
    onRefresh: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.general_tab),
        stringResource(R.string.permissions_tab),
        stringResource(R.string.requests_tab),
        stringResource(R.string.posts_tab),
        stringResource(R.string.statistics_tab)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.admin_panel)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(title)
                                // Show badge for join requests
                                if (index == 2 && joinRequests.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Badge(containerColor = MaterialTheme.colorScheme.error) {
                                        Text(joinRequests.size.toString())
                                    }
                                }
                            }
                        }
                    )
                }
            }

            // Content
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "tab_content"
            ) { tab ->
                when (tab) {
                    0 -> GeneralSettingsTab(
                        group = group,
                        settings = group.settings ?: GroupSettings(),
                        onSettingsChange = onSettingsChange,
                        onPrivacyChange = onPrivacyChange
                    )
                    1 -> PermissionsTab(
                        settings = group.settings ?: GroupSettings(),
                        members = members,
                        currentUserId = currentUserId,
                        isOwner = group.isOwner,
                        onSettingsChange = onSettingsChange,
                        onRoleChange = onRoleChange
                    )
                    2 -> JoinRequestsTab(
                        requests = joinRequests,
                        isLoading = isLoading,
                        onApprove = onApproveJoinRequest,
                        onReject = onRejectJoinRequest
                    )
                    3 -> ScheduledPostsTab(
                        posts = scheduledPosts,
                        groupId = group.id,
                        isLoading = isLoading,
                        onCreate = onCreateScheduledPost,
                        onDelete = onDeleteScheduledPost,
                        onPublish = onPublishScheduledPost
                    )
                    4 -> StatisticsTab(
                        statistics = statistics,
                        isLoading = isLoading,
                        onOpenFull = onOpenStatistics
                    )
                }
            }
        }
    }
}

// ==================== GENERAL SETTINGS TAB ====================

@Composable
private fun GeneralSettingsTab(
    group: Group,
    settings: GroupSettings,
    onSettingsChange: (GroupSettings) -> Unit,
    onPrivacyChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Privacy settings
        GroupPrivacySettingsPanel(
            isPrivate = group.isPrivate,
            onPrivacyChange = onPrivacyChange,
            settings = settings,
            onSettingsChange = onSettingsChange,
            isAdmin = true
        )

        // Slow mode
        SlowModeSettingsPanel(
            currentSeconds = settings.slowModeSeconds,
            onSecondsChange = { onSettingsChange(settings.copy(slowModeSeconds = it)) },
            isAdmin = true
        )

        // Anti-spam
        AntiSpamSettingsPanel(
            settings = settings,
            onSettingsChange = onSettingsChange,
            isAdmin = true
        )
    }
}

// ==================== PERMISSIONS TAB ====================

@Composable
private fun PermissionsTab(
    settings: GroupSettings,
    members: List<GroupMember>,
    currentUserId: Long,
    isOwner: Boolean,
    onSettingsChange: (GroupSettings) -> Unit,
    onRoleChange: (Long, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Member permissions
        MemberPermissionsPanel(
            settings = settings,
            onSettingsChange = onSettingsChange,
            isAdmin = true
        )

        // Roles management
        RolesManagementPanel(
            members = members,
            onRoleChange = onRoleChange,
            currentUserId = currentUserId,
            isOwner = isOwner
        )
    }
}

// ==================== JOIN REQUESTS TAB ====================

@Composable
private fun JoinRequestsTab(
    requests: List<GroupJoinRequest>,
    isLoading: Boolean,
    onApprove: (GroupJoinRequest) -> Unit,
    onReject: (GroupJoinRequest) -> Unit
) {
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (requests.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Немає запитів на вступ",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Коли хтось захоче приєднатися до приватної групи,\nзапит з'явиться тут",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                JoinRequestsPanel(
                    requests = requests,
                    onApprove = onApprove,
                    onReject = onReject
                )
            }
        }
    }
}

// ==================== SCHEDULED POSTS TAB ====================

@Composable
private fun ScheduledPostsTab(
    posts: List<ScheduledPost>,
    groupId: Long,
    isLoading: Boolean,
    onCreate: (String, Long, String?, String, Boolean, Boolean) -> Unit,
    onDelete: (ScheduledPost) -> Unit,
    onPublish: (ScheduledPost) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        ScheduledPostsPanel(
            scheduledPosts = posts,
            onCreateClick = { showCreateDialog = true },
            onEditClick = { /* TODO */ },
            onDeleteClick = onDelete,
            onPublishNowClick = onPublish,
            isAdmin = true,
            modifier = Modifier.padding(16.dp)
        )
    }

    if (showCreateDialog) {
        CreateScheduledPostDialog(
            groupId = groupId,
            onDismiss = { showCreateDialog = false },
            onSave = { text, time, media, repeat, pinned, notify ->
                onCreate(text, time, media, repeat, pinned, notify)
                showCreateDialog = false
            }
        )
    }
}

// ==================== STATISTICS TAB ====================

@Composable
private fun StatisticsTab(
    statistics: GroupStatistics?,
    isLoading: Boolean,
    onOpenFull: () -> Unit
) {
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (statistics == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.BarChart,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Статистика недоступна",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatisticsOverviewCards(statistics)
            ActivityChartCard(statistics)
            MembersGrowthCard(statistics)

            // Open full statistics button
            OutlinedButton(
                onClick = onOpenFull,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Детальна статистика")
            }
        }
    }
}

// ==================== QUICK ADMIN CONTROLS CARD ====================

/**
 * Компактна картка адмін-контролів для вбудовування в GroupDetailsActivity
 */
@Composable
fun QuickAdminControlsCard(
    group: Group,
    joinRequestsCount: Int,
    scheduledPostsCount: Int,
    onOpenAdminPanel: () -> Unit,
    onEditClick: () -> Unit,
    onMembersClick: () -> Unit,
    onQrCodeClick: () -> Unit,
    onFormattingClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.AdminPanelSettings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Управління групою",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onOpenAdminPanel) {
                    Text("Адмін-панель")
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick actions grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                QuickActionButton(
                    icon = Icons.Default.Edit,
                    label = "Редагувати",
                    onClick = onEditClick
                )
                QuickActionButton(
                    icon = Icons.Default.Group,
                    label = "Учасники",
                    onClick = onMembersClick
                )
                QuickActionButton(
                    icon = Icons.Default.QrCode,
                    label = "QR-код",
                    onClick = onQrCodeClick
                )
                QuickActionButton(
                    icon = Icons.Default.TextFormat,
                    label = "Формат",
                    onClick = onFormattingClick
                )
            }

            // Badges for pending items
            if (joinRequestsCount > 0 || scheduledPostsCount > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (joinRequestsCount > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp),
                            onClick = onOpenAdminPanel
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    Icons.Default.PersonAdd,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "$joinRequestsCount запитів",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    if (scheduledPostsCount > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp),
                            onClick = onOpenAdminPanel
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "$scheduledPostsCount постів",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            shape = CircleShape
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(12.dp)
                    .size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ==================== GROUP TYPE INDICATOR ====================

/**
 * Індикатор типу групи (публічна/приватна)
 */
@Composable
fun GroupTypeIndicator(
    isPrivate: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isPrivate)
            MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
        else
            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = if (isPrivate) Icons.Default.Lock else Icons.Default.Public,
                contentDescription = null,
                tint = if (isPrivate)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isPrivate) "Приватна" else "Публічна",
                style = MaterialTheme.typography.labelMedium,
                color = if (isPrivate)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ==================== MEMBER ROLE BADGE ====================

/**
 * Бейдж ролі учасника
 */
@Composable
fun MemberRoleBadge(
    role: String,
    modifier: Modifier = Modifier
) {
    val (color, label) = when (role) {
        "owner" -> MaterialTheme.colorScheme.primary to "Власник"
        "admin" -> Color(0xFF2196F3) to "Адмін"
        "moderator" -> Color(0xFF4CAF50) to "Модератор"
        else -> return // No badge for regular members
    }

    Surface(
        color = color.copy(alpha = 0.2f),
        shape = RoundedCornerShape(4.dp),
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

package com.worldmates.messenger.ui.groups

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.worldmates.messenger.R
import com.worldmates.messenger.data.model.AdminLogDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupAdminLogsScreen(
    groupId: Long,
    onBack: () -> Unit,
    viewModel: GroupAdminLogsViewModel = viewModel()
) {
    val logs by viewModel.logs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val total by viewModel.total.collectAsState()
    val listState = rememberLazyListState()

    var currentPage by remember { mutableStateOf(1) }

    LaunchedEffect(groupId) {
        viewModel.loadLogs(groupId, page = 1)
    }

    // Load more when reaching end of list
    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem != null &&
                lastVisibleItem.index >= listState.layoutInfo.totalItemsCount - 3 &&
                !isLoading &&
                logs.size < total
        }
    }
    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            currentPage++
            viewModel.loadLogs(groupId, page = currentPage)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.admin_logs_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        currentPage = 1
                        viewModel.loadLogs(groupId, page = 1)
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                }
            )
        }
    ) { padding ->
        when {
            isLoading && logs.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            logs.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.admin_logs_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(logs) { log ->
                        AdminLogItem(log = log)
                        Divider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    if (isLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminLogItem(log: AdminLogDto) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Admin avatar
        if (log.adminAvatar != null) {
            AsyncImage(
                model = log.adminAvatar,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            )
        } else {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Action description
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = actionIcon(log.action),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = buildActionText(log),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Normal
                )
            }

            // Timestamp
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = formatTimestamp(log.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun buildActionText(log: AdminLogDto): String {
    val action = when (log.action) {
        "add_member" -> "добавил участника"
        "remove_member" -> "удалил участника"
        "ban_user" -> "заблокировал пользователя"
        "unban_user" -> "разблокировал пользователя"
        "delete_message" -> "удалил сообщение"
        "pin_message" -> "закрепил сообщение"
        "unpin_message" -> "открепил сообщение"
        "change_title" -> "изменил название группы"
        "change_avatar" -> "изменил аватар группы"
        "change_description" -> "изменил описание группы"
        "change_role" -> "изменил роль участника"
        "change_settings" -> "изменил настройки группы"
        "enable_slow_mode" -> "включил медленный режим"
        "disable_slow_mode" -> "отключил медленный режим"
        else -> log.action.replace("_", " ")
    }
    return if (log.targetUserName != null) {
        "${log.adminName} $action ${log.targetUserName}"
    } else {
        "${log.adminName} $action"
    }
}

private fun actionIcon(action: String): ImageVector = when (action) {
    "add_member" -> Icons.Default.PersonAdd
    "remove_member" -> Icons.Default.PersonRemove
    "ban_user" -> Icons.Default.Block
    "unban_user" -> Icons.Default.CheckCircle
    "delete_message" -> Icons.Default.Delete
    "pin_message", "unpin_message" -> Icons.Default.PushPin
    "change_title", "change_description" -> Icons.Default.Edit
    "change_avatar" -> Icons.Default.Image
    "change_role" -> Icons.Default.ManageAccounts
    "change_settings", "enable_slow_mode", "disable_slow_mode" -> Icons.Default.Settings
    else -> Icons.Default.Info
}

private fun formatTimestamp(createdAt: String): String {
    if (createdAt.isBlank()) return ""
    return try {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.getDefault())
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val date = sdf.parse(createdAt) ?: return createdAt
        val now = System.currentTimeMillis()
        val diff = now - date.time
        val minutes = diff / 60_000
        val hours = diff / 3_600_000
        val days = diff / 86_400_000
        when {
            minutes < 1 -> "только что"
            minutes < 60 -> "$minutes мин. назад"
            hours < 24 -> "$hours ч. назад"
            days == 1L -> "вчера"
            days < 7 -> "$days дн. назад"
            else -> {
                val outFmt = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
                outFmt.format(date)
            }
        }
    } catch (e: Exception) {
        createdAt
    }
}

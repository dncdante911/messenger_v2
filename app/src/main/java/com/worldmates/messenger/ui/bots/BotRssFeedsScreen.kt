package com.worldmates.messenger.ui.bots

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.worldmates.messenger.R
import com.worldmates.messenger.data.model.RssFeed

/**
 * Screen for managing RSS feeds of a bot.
 * Allows owner to add, toggle, delete RSS feed subscriptions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotRssFeedsScreen(
    state: RssFeedsState,
    onAddFeed: (url: String, chatId: String, name: String?) -> Unit,
    onToggleFeed: (feedId: Int, isActive: Boolean) -> Unit,
    onDeleteFeed: (feedId: Int) -> Unit,
    onBack: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var feedToDelete by remember { mutableStateOf<RssFeed?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.bot_rss_title), fontWeight = FontWeight.SemiBold)
                        if (state.botId != null) {
                            Text(
                                text = state.botId,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back_cd))
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.bot_rss_add))
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
            state.feeds.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            Icons.Default.RssFeed,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.bot_rss_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.bot_rss_empty_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.bot_rss_add))
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (state.error != null) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                                Text(
                                    text = state.error,
                                    modifier = Modifier.padding(12.dp),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    items(state.feeds, key = { it.id }) { feed ->
                        RssFeedCard(
                            feed = feed,
                            onToggle = { onToggleFeed(feed.id, !feed.isEnabled) },
                            onDelete = { feedToDelete = feed }
                        )
                    }
                }
            }
        }
    }

    // Add feed dialog
    if (showAddDialog) {
        AddRssFeedDialog(
            onConfirm = { url, chatId, name ->
                onAddFeed(url, chatId, name)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    // Delete confirmation dialog
    feedToDelete?.let { feed ->
        AlertDialog(
            onDismissRequest = { feedToDelete = null },
            title = { Text(stringResource(R.string.bot_rss_delete_title)) },
            text = { Text(stringResource(R.string.bot_rss_delete_message, feed.displayName)) },
            confirmButton = {
                TextButton(
                    onClick = { onDeleteFeed(feed.id); feedToDelete = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { feedToDelete = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun RssFeedCard(feed: RssFeed, onToggle: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.RssFeed,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (feed.isEnabled) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = feed.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = feed.feedUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = feed.isEnabled,
                    onCheckedChange = { onToggle() }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RssStat(stringResource(R.string.bot_rss_stat_chat, feed.chatId))
                RssStat(stringResource(R.string.bot_rss_stat_interval, feed.checkIntervalMinutes))
                RssStat(stringResource(R.string.bot_rss_stat_posted, feed.itemsPosted))
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.delete))
                }
            }
        }
    }
}

@Composable
private fun RssStat(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRssFeedDialog(
    onConfirm: (url: String, chatId: String, name: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var feedUrl by remember { mutableStateOf("") }
    var chatId by remember { mutableStateOf("") }
    var feedName by remember { mutableStateOf("") }
    var urlError by remember { mutableStateOf(false) }
    var chatError by remember { mutableStateOf(false) }

    val errorUrlText = stringResource(R.string.bot_rss_error_url)
    val errorChatText = stringResource(R.string.bot_rss_error_chat_id)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bot_rss_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = feedUrl,
                    onValueChange = { feedUrl = it; urlError = false },
                    label = { Text(stringResource(R.string.bot_rss_field_url)) },
                    placeholder = { Text("https://example.com/rss") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = urlError,
                    supportingText = if (urlError) ({ Text(errorUrlText) }) else null
                )
                OutlinedTextField(
                    value = chatId,
                    onValueChange = { chatId = it; chatError = false },
                    label = { Text(stringResource(R.string.bot_rss_field_chat_id)) },
                    placeholder = { Text(stringResource(R.string.bot_rss_field_chat_id_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = chatError,
                    supportingText = if (chatError) ({ Text(errorChatText) }) else null
                )
                OutlinedTextField(
                    value = feedName,
                    onValueChange = { feedName = it },
                    label = { Text(stringResource(R.string.bot_rss_field_name)) },
                    placeholder = { Text(stringResource(R.string.bot_rss_field_name_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                urlError = feedUrl.isBlank() || (!feedUrl.startsWith("http://") && !feedUrl.startsWith("https://"))
                chatError = chatId.isBlank()
                if (!urlError && !chatError) {
                    onConfirm(feedUrl.trim(), chatId.trim(), feedName.takeIf { it.isNotBlank() })
                }
            }) { Text(stringResource(R.string.add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
                        Text("RSS Feeds", fontWeight = FontWeight.SemiBold)
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
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add RSS feed")
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
                        Text("No RSS feeds", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Add an RSS feed and this bot will automatically post new items to the specified chat.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add RSS feed")
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
            title = { Text("Delete RSS feed?") },
            text = { Text("\"${feed.displayName}\" will be removed. Posted items history will also be cleared.") },
            confirmButton = {
                TextButton(
                    onClick = { onDeleteFeed(feed.id); feedToDelete = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { feedToDelete = null }) { Text("Cancel") }
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
                RssStat("Chat: ${feed.chatId}")
                RssStat("Every ${feed.checkIntervalMinutes}m")
                RssStat("Posted: ${feed.itemsPosted}")
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Remove")
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add RSS feed") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = feedUrl,
                    onValueChange = { feedUrl = it; urlError = false },
                    label = { Text("Feed URL *") },
                    placeholder = { Text("https://example.com/rss") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = urlError,
                    supportingText = if (urlError) ({ Text("Enter a valid URL") }) else null
                )
                OutlinedTextField(
                    value = chatId,
                    onValueChange = { chatId = it; chatError = false },
                    label = { Text("Target chat ID *") },
                    placeholder = { Text("Channel or group ID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = chatError,
                    supportingText = if (chatError) ({ Text("Chat ID required") }) else null
                )
                OutlinedTextField(
                    value = feedName,
                    onValueChange = { feedName = it },
                    label = { Text("Feed name (optional)") },
                    placeholder = { Text("My news feed") },
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
            }) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

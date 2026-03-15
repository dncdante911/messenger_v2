package com.worldmates.messenger.ui.saved

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.R
import com.worldmates.messenger.data.SavedMessageItem
import com.worldmates.messenger.data.SavedMessagesManager
import com.worldmates.messenger.ui.theme.WorldMatesMessengerTheme
import java.text.SimpleDateFormat
import java.util.*

/**
 * Saved Messages screen — shows all locally-bookmarked messages.
 * Users arrive here from the sidebar drawer.
 */
class SavedMessagesActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WorldMatesMessengerTheme {
                SavedMessagesScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavedMessagesScreen(onBack: () -> Unit) {
    val savedMessages by SavedMessagesManager.savedMessages.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.saved_messages),
                            fontWeight = FontWeight.Bold
                        )
                        if (savedMessages.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.saved_messages_count, savedMessages.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (savedMessages.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                Icons.Default.BookmarkRemove,
                                contentDescription = stringResource(R.string.saved_clear_all)
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (savedMessages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Bookmark,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Text(
                        text = stringResource(R.string.saved_messages_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.saved_messages_empty_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(savedMessages, key = { "${it.chatType}_${it.messageId}" }) { item ->
                    SavedMessageCard(
                        item = item,
                        onRemove = {
                            SavedMessagesManager.removeSaved(item.messageId, item.chatType)
                        }
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.saved_clear_all)) },
            text = { Text(stringResource(R.string.saved_clear_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        SavedMessagesManager.clearAll()
                        showClearDialog = false
                    }
                ) {
                    Text(stringResource(R.string.delete_action), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun SavedMessageCard(
    item: SavedMessageItem,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: chat name + time + remove button
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Bookmark,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.chatName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.senderName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = formatDate(item.savedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.BookmarkRemove,
                        contentDescription = stringResource(R.string.saved_remove),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(8.dp))

            // Message content
            if (item.text.isNotBlank()) {
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (item.mediaType != null) {
                Spacer(Modifier.height(6.dp))
                MediaTypeChip(mediaType = item.mediaType)
            }
        }
    }
}

@Composable
private fun MediaTypeChip(mediaType: String) {
    val (icon, label) = when (mediaType) {
        "image", "photo" -> Icons.Default.Image to stringResource(R.string.media_type_photo)
        "video" -> Icons.Default.Movie to stringResource(R.string.media_type_video)
        "voice" -> Icons.Default.Mic to stringResource(R.string.media_type_voice)
        "audio" -> Icons.Default.Mic to stringResource(R.string.media_type_audio)
        else -> Icons.Default.AttachFile to stringResource(R.string.media_type_file)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

private fun formatDate(unixSec: Long): String {
    val ms = if (unixSec > 9_999_999_999L) unixSec else unixSec * 1000L
    val now = System.currentTimeMillis()
    val diff = now - ms
    return when {
        diff < 86_400_000L -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
        diff < 7 * 86_400_000L -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(ms))
        else -> SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(Date(ms))
    }
}

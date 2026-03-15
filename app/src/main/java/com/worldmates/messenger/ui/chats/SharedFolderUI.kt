package com.worldmates.messenger.ui.chats

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worldmates.messenger.R
import com.worldmates.messenger.network.ServerFolder

/**
 * Bottom sheet — list of server-synced shared folders.
 * Shown from the chat list when the user taps a "Shared Folders" button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedFoldersSheet(
    onDismiss: () -> Unit,
    viewModel: ServerFolderViewModel = viewModel()
) {
    val folders    by viewModel.folders.collectAsState()
    val isLoading  by viewModel.isLoading.collectAsState()
    val shareUrl   by viewModel.shareUrl.collectAsState()
    val context    = LocalContext.current

    var showCreateDialog by remember { mutableStateOf(false) }
    var showShareSheet   by remember { mutableStateOf<ServerFolder?>(null) }
    var showJoinDialog   by remember { mutableStateOf(false) }
    var editFolder       by remember { mutableStateOf<ServerFolder?>(null) }
    var deleteFolderTarget by remember { mutableStateOf<ServerFolder?>(null) }

    LaunchedEffect(Unit) { viewModel.loadFolders() }

    // Copy share URL to clipboard when it becomes available
    LaunchedEffect(shareUrl) {
        shareUrl?.let { url ->
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("folder_link", url))
            Toast.makeText(context, context.getString(R.string.folder_link_copied), Toast.LENGTH_SHORT).show()
            showShareSheet = folders.find { it.shareCode != null && url.contains(it.shareCode!!) }
            viewModel.clearShareUrl()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.folder_share),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showJoinDialog = true }) {
                    Icon(Icons.Default.PersonAdd, contentDescription = stringResource(R.string.folder_join))
                }
                IconButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.folder_create))
                }
            }

            HorizontalDivider()

            if (isLoading) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.folder_syncing))
                    }
                }
            } else if (folders.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.FolderShared,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.folder_share_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        FilledTonalButton(onClick = { showCreateDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.folder_create))
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 420.dp)
                ) {
                    items(folders, key = { it.id }) { folder ->
                        ServerFolderCard(
                            folder = folder,
                            onShare  = { viewModel.shareFolder(folder.id) },
                            onEdit   = { editFolder = folder },
                            onDelete = { deleteFolderTarget = folder },
                            onLeave  = { viewModel.leaveFolder(folder.id) }
                        )
                    }
                }
            }

            Spacer(Modifier.navigationBarsPadding())
        }
    }

    // Create folder dialog
    if (showCreateDialog) {
        CreateFolderDialog(
            onDismiss = { showCreateDialog = false },
            onCreate  = { name, emoji, color ->
                viewModel.createFolder(name, emoji, color)
                showCreateDialog = false
            }
        )
    }

    // Edit folder dialog
    editFolder?.let { folder ->
        CreateFolderDialog(
            existing  = folder,
            onDismiss = { editFolder = null },
            onCreate  = { name, emoji, color ->
                viewModel.updateFolder(folder.id, name, emoji, color)
                editFolder = null
            }
        )
    }

    // Join folder dialog
    if (showJoinDialog) {
        JoinFolderDialog(
            onDismiss = { showJoinDialog = false },
            onJoin    = { code ->
                viewModel.joinFolder(code,
                    onJoined = { folder ->
                        showJoinDialog = false
                        Toast.makeText(
                            context,
                            context.getString(R.string.folder_joined, folder.name),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onError = { err ->
                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        )
    }

    // Share sheet (shows invite link after sharing)
    showShareSheet?.let { folder ->
        FolderShareResultSheet(
            folder   = folder,
            onDismiss = { showShareSheet = null }
        )
    }

    // Delete confirm dialog
    deleteFolderTarget?.let { folder ->
        AlertDialog(
            onDismissRequest = { deleteFolderTarget = null },
            title = { Text(stringResource(R.string.folder_delete_confirm, folder.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteFolder(folder.id)
                        deleteFolderTarget = null
                    }
                ) { Text(stringResource(R.string.delete_action), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteFolderTarget = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

// ─── Folder card ──────────────────────────────────────────────────────────────

@Composable
private fun ServerFolderCard(
    folder: ServerFolder,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLeave: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(parseColor(folder.color).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = folder.emoji, style = MaterialTheme.typography.titleLarge)
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = folder.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (folder.isShared) {
                        Spacer(Modifier.width(6.dp))
                        SharedBadge()
                    }
                }
                val chatCount = folder.chats?.size ?: 0
                val memberCount = folder.memberCount
                Text(
                    text = buildString {
                        append("$chatCount чатів")
                        if (memberCount > 0) append(" · ${stringForMembers(memberCount)}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = null)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.folder_share)) },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = { showMenu = false; onShare() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.folder_edit)) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = { showMenu = false; onEdit() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.folder_leave)) },
                        leadingIcon = { Icon(Icons.Default.ExitToApp, contentDescription = null) },
                        onClick = { showMenu = false; onLeave() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete_action), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { showMenu = false; onDelete() }
                    )
                }
            }
        }
    }
}

// ─── Shared badge ─────────────────────────────────────────────────────────────

@Composable
fun SharedBadge() {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                Icons.Default.People,
                contentDescription = null,
                modifier = Modifier.size(10.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.folder_shared_badge),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ─── Create / Edit folder dialog ─────────────────────────────────────────────

private val EMOJI_PRESETS = listOf("📁","💬","📢","👥","⭐","🔖","💼","🎮","✈️","🏠","❤️","🔥","📌","🎵")
private val COLOR_PRESETS  = listOf("#2196F3","#E91E63","#4CAF50","#FF9800","#9C27B0","#607D8B","#F44336","#00BCD4")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateFolderDialog(
    existing: ServerFolder? = null,
    onDismiss: () -> Unit,
    onCreate: (name: String, emoji: String, color: String) -> Unit
) {
    var name  by remember { mutableStateOf(existing?.name  ?: "") }
    var emoji by remember { mutableStateOf(existing?.emoji ?: "📁") }
    var color by remember { mutableStateOf(existing?.color ?: "#2196F3") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (existing == null) stringResource(R.string.folder_create) else stringResource(R.string.folder_edit))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.folder_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Emoji picker row
                Text(stringResource(R.string.folder_emoji_hint), style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    EMOJI_PRESETS.take(7).forEach { e ->
                        Surface(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable { emoji = e },
                            color = if (emoji == e)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(e, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                // Color picker row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    COLOR_PRESETS.forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(parseColor(c))
                                .clickable { color = c }
                                .then(
                                    if (color == c) Modifier.padding(2.dp) else Modifier
                                )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name.trim(), emoji, color) },
                enabled = name.isNotBlank()
            ) { Text(stringResource(R.string.save_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

// ─── Join folder dialog ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JoinFolderDialog(
    onDismiss: () -> Unit,
    onJoin: (code: String) -> Unit
) {
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folder_join_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.folder_join_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.trim() },
                    label = { Text(stringResource(R.string.folder_invite_link)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://…/folder/join/…") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Extract code from URL if user pasted a full URL
                    val extractedCode = extractJoinCode(code)
                    if (extractedCode.isNotBlank()) onJoin(extractedCode)
                },
                enabled = code.isNotBlank()
            ) { Text(stringResource(R.string.folder_join)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

// ─── Share result sheet ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderShareResultSheet(
    folder: ServerFolder,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.FolderShared,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.folder_share_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Share link card
            val shareUrl = "worldmates.club/folder/join/${folder.shareCode}"
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = shareUrl,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("folder_link", "https://$shareUrl"))
                            Toast.makeText(context, context.getString(R.string.folder_link_copied), Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.folder_copy_link))
                    }
                }
            }

            if (folder.memberCount > 0) {
                Text(
                    text = stringResource(R.string.folder_members, folder.memberCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private fun parseColor(hex: String): androidx.compose.ui.graphics.Color {
    return try {
        val c = android.graphics.Color.parseColor(hex)
        androidx.compose.ui.graphics.Color(c)
    } catch (e: Exception) {
        androidx.compose.ui.graphics.Color(0xFF2196F3)
    }
}

private fun extractJoinCode(input: String): String {
    // Handle full URLs like https://worldmates.club/folder/join/ABCD1234
    val regex = Regex("""/folder/join/([a-zA-Z0-9]+)""")
    val match = regex.find(input)
    return match?.groupValues?.get(1) ?: input.trim()
}

@Composable
private fun stringForMembers(count: Int): String =
    stringResource(R.string.folder_members, count)

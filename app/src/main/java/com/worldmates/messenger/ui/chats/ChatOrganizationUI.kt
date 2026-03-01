package com.worldmates.messenger.ui.chats

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.R
import com.worldmates.messenger.data.model.Chat

/**
 * Горизонтальна смуга з табами-папками (Telegram-style).
 * Замінює окремий TabRow + старі папки.
 * Включає системні папки (Усі, Особисті, Канали, Групи, Непрочитані) + кастомні.
 */
@Composable
fun ChatFolderTabs(
    selectedFolderId: String,
    onFolderSelected: (String) -> Unit,
    onAddFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val folders by ChatOrganizationManager.folders.collectAsState()
    val archivedCount by ChatOrganizationManager.archivedChatIds.collectAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        folders.forEach { folder ->
            FolderTabChip(
                folder = folder,
                isSelected = folder.id == selectedFolderId,
                onClick = { onFolderSelected(folder.id) }
            )
        }

        // Archived tab
        if (archivedCount.isNotEmpty()) {
            FolderTabChip(
                folder = ChatFolder("archived", stringResource(R.string.archive), "📦", 99),
                isSelected = selectedFolderId == "archived",
                badge = archivedCount.size,
                onClick = { onFolderSelected("archived") }
            )
        }

        // Add folder button
        IconButton(
            onClick = onAddFolder,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.add),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun FolderTabChip(
    folder: ChatFolder,
    isSelected: Boolean,
    badge: Int = 0,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    )

    Card(
        modifier = Modifier
            .height(32.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(folder.emoji, fontSize = 13.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = folder.name,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (badge > 0) {
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$badge",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Фільтрує чати за обраною папкою
 */
fun filterChatsByFolder(
    chats: List<Chat>,
    folderId: String,
    archivedIds: Set<Long>,
    folderMapping: Map<Long, String>
): List<Chat> {
    // Спочатку виключаємо архівовані (крім папки "archived")
    val nonArchived = if (folderId == "archived") {
        chats.filter { archivedIds.contains(it.userId) }
    } else {
        chats.filter { !archivedIds.contains(it.userId) }
    }

    return when (folderId) {
        "all" -> nonArchived
        "personal" -> nonArchived.filter { !it.isGroup }
        "groups" -> nonArchived.filter { it.isGroup }
        "unread" -> nonArchived.filter { it.unreadCount > 0 }
        "archived" -> nonArchived
        else -> {
            // Custom folder - filter by mapping
            nonArchived.filter { folderMapping[it.userId] == folderId }
        }
    }
}

/**
 * Теги чату (відображення під назвою чату)
 */
@Composable
fun ChatTagsRow(
    chatId: Long,
    modifier: Modifier = Modifier
) {
    val allTags by ChatOrganizationManager.chatTags.collectAsState()
    val tags = allTags[chatId] ?: return

    if (tags.isEmpty()) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tags.take(3).forEach { tag ->
            val tagColor = try {
                Color(android.graphics.Color.parseColor(tag.color))
            } catch (e: Exception) {
                MaterialTheme.colorScheme.primary
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(tagColor.copy(alpha = 0.15f))
                    .border(0.5.dp, tagColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(
                    text = tag.name,
                    fontSize = 9.sp,
                    color = tagColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Діалог створення нової папки з інформацією про ліміти
 */
@Composable
fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, emoji: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("📁") }

    val emojiOptions = listOf("📁", "💼", "🏠", "🎮", "📚", "🛒", "✈️", "🎵", "⭐", "🔒", "💰", "🎯")
    val customCount = ChatOrganizationManager.getCustomFolderCount()
    val maxCount = ChatOrganizationManager.getMaxCustomFolders()
    val canCreate = ChatOrganizationManager.canCreateFolder()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_folder_dialog_title)) },
        text = {
            Column {
                // Лімітер папок
                Text(
                    text = stringResource(R.string.folder_count_format, customCount, maxCount),
                    fontSize = 12.sp,
                    color = if (canCreate)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium
                )
                if (!canCreate) {
                    Text(
                        text = stringResource(R.string.folder_limit_reached),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.folder_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canCreate
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.choose_icon_label), fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    emojiOptions.forEach { e ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (e == emoji) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { emoji = e },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(e, fontSize = 20.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank() && canCreate) onConfirm(name.trim(), emoji) },
                enabled = name.isNotBlank() && canCreate
            ) { Text(stringResource(R.string.create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/**
 * Діалог управління тегами чату
 */
@Composable
fun ManageTagsDialog(
    chatId: Long,
    chatName: String,
    onDismiss: () -> Unit
) {
    val currentTags by ChatOrganizationManager.chatTags.collectAsState()
    val chatCurrentTags = currentTags[chatId] ?: emptyList()
    var customTagName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tags) + ": $chatName") },
        text = {
            Column {
                Text(
                    stringResource(R.string.select_or_create_tags),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Preset tags
                ChatTag.PRESET_TAGS.chunked(2).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        row.forEach { tag ->
                            val isSelected = chatCurrentTags.any { it.name == tag.name }
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (isSelected) {
                                        ChatOrganizationManager.removeTagFromChat(chatId, tag.name)
                                    } else {
                                        ChatOrganizationManager.addTagToChat(chatId, tag)
                                    }
                                },
                                label = { Text(tag.name, fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (row.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Custom tag input
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = customTagName,
                        onValueChange = { customTagName = it },
                        label = { Text(stringResource(R.string.custom_tag)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (customTagName.isNotBlank()) {
                                ChatOrganizationManager.addTagToChat(
                                    chatId,
                                    ChatTag(customTagName.trim())
                                )
                                customTagName = ""
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
        }
    )
}

/**
 * Контекстне меню чату (архів, папки, теги)
 */
@Composable
fun ChatContextMenu(
    chat: Chat,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onManageTags: () -> Unit,
    onMoveToFolder: () -> Unit
) {
    val isArchived = ChatOrganizationManager.isArchived(chat.userId)

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (isArchived) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.unarchive)) },
                onClick = { onUnarchive(); onDismiss() },
                leadingIcon = { Icon(Icons.Default.Unarchive, contentDescription = null) }
            )
        } else {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.archive_chat)) },
                onClick = { onArchive(); onDismiss() },
                leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) }
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.tags)) },
            onClick = { onManageTags(); onDismiss() },
            leadingIcon = { Icon(Icons.Default.Label, contentDescription = null) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.move_to_folder)) },
            onClick = { onMoveToFolder(); onDismiss() },
            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) }
        )
    }
}

/**
 * Діалог вибору папки для переміщення чату
 */
@Composable
fun MoveToChatFolderDialog(
    chatId: Long,
    chatName: String,
    onDismiss: () -> Unit
) {
    val folders by ChatOrganizationManager.folders.collectAsState()
    val customFolders = folders.filter { it.isCustom }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_chat_title_format, chatName)) },
        text = {
            Column {
                if (customFolders.isEmpty()) {
                    Text(
                        stringResource(R.string.no_custom_folders_message),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    customFolders.forEach { folder ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    ChatOrganizationManager.moveChatToFolder(chatId, folder.id)
                                    onDismiss()
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(folder.emoji, fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(folder.name, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                // Option to remove from folder
                val currentFolder = ChatOrganizationManager.getChatFolder(chatId)
                if (currentFolder != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            ChatOrganizationManager.removeChatFromFolder(chatId)
                            onDismiss()
                        }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.remove_from_folder))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}

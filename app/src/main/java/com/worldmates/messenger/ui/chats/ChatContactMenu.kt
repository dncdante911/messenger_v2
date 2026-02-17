package com.worldmates.messenger.ui.chats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.worldmates.messenger.data.ContactNicknameRepository
import com.worldmates.messenger.data.model.Chat
import kotlinx.coroutines.launch

/**
 * Контекстне меню для контактів (Профіль, Перейменувати, Видалити)
 * + опціональні дії: Архівувати, Теги, Папка
 *
 * Витягнуто з ChatsActivity.kt та розширено для підтримки
 * організації чатів (папки, архів, теги).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactContextMenu(
    chat: Chat,
    onDismiss: () -> Unit,
    onRename: (Chat) -> Unit,
    onDelete: (Chat) -> Unit,
    nicknameRepository: ContactNicknameRepository,
    onViewProfile: ((Chat) -> Unit)? = null,
    // Опціональні дії організації чатів
    onArchive: ((Chat) -> Unit)? = null,
    onUnarchive: ((Chat) -> Unit)? = null,
    onManageTags: ((Chat) -> Unit)? = null,
    onMoveToFolder: ((Chat) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState()
    val colorScheme = MaterialTheme.colorScheme
    val existingNickname by nicknameRepository.getNickname(chat.userId).collectAsState(initial = null)
    var showRenameDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            // Заголовок
            Text(
                text = "Дії з контактом",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                color = colorScheme.onSurface
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // View Profile
            val context = LocalContext.current
            ContactMenuRow(
                icon = Icons.Default.Person,
                title = "Переглянути профіль",
                onClick = {
                    onDismiss()
                    if (onViewProfile != null) {
                        onViewProfile(chat)
                    } else {
                        val intent = android.content.Intent(
                            context,
                            com.worldmates.messenger.ui.profile.UserProfileActivity::class.java
                        ).apply {
                            putExtra("user_id", chat.userId)
                        }
                        context.startActivity(intent)
                    }
                }
            )

            Divider(modifier = Modifier.padding(vertical = 4.dp, horizontal = 24.dp))

            // Rename
            ContactMenuRow(
                icon = Icons.Default.Edit,
                title = if (existingNickname != null) "Змінити псевдонім" else "Додати псевдонім",
                onClick = { showRenameDialog = true }
            )

            // Archive / Unarchive (тільки якщо передані callbacks)
            if (onArchive != null || onUnarchive != null) {
                Divider(modifier = Modifier.padding(vertical = 4.dp, horizontal = 24.dp))
                val isArchived = ChatOrganizationManager.isArchived(chat.userId)
                if (isArchived && onUnarchive != null) {
                    ContactMenuRow(
                        icon = Icons.Default.Unarchive,
                        title = "Розархівувати",
                        onClick = {
                            onUnarchive(chat)
                            onDismiss()
                        }
                    )
                } else if (!isArchived && onArchive != null) {
                    ContactMenuRow(
                        icon = Icons.Default.Archive,
                        title = "Архівувати",
                        onClick = {
                            onArchive(chat)
                            onDismiss()
                        }
                    )
                }
            }

            // Tags (тільки якщо передано callback)
            if (onManageTags != null) {
                Divider(modifier = Modifier.padding(vertical = 4.dp, horizontal = 24.dp))
                ContactMenuRow(
                    icon = Icons.Default.Label,
                    title = "Теги",
                    onClick = {
                        onManageTags(chat)
                        onDismiss()
                    }
                )
            }

            // Move to folder (тільки якщо передано callback)
            if (onMoveToFolder != null) {
                Divider(modifier = Modifier.padding(vertical = 4.dp, horizontal = 24.dp))
                ContactMenuRow(
                    icon = Icons.Default.Folder,
                    title = "Перемістити в папку",
                    onClick = {
                        onMoveToFolder(chat)
                        onDismiss()
                    }
                )
            }

            // Delete / Hide
            Divider(modifier = Modifier.padding(vertical = 4.dp, horizontal = 24.dp))
            ContactMenuRow(
                icon = Icons.Default.Delete,
                title = "Приховати чат",
                tint = Color(0xFFD32F2F),
                onClick = { onDelete(chat) }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Inline Rename Dialog
    if (showRenameDialog) {
        RenameContactDialog(
            chat = chat,
            currentNickname = existingNickname,
            onDismiss = {
                showRenameDialog = false
                onDismiss()
            },
            onSave = { nickname: String? ->
                scope.launch {
                    nicknameRepository.setNickname(chat.userId, nickname)
                    showRenameDialog = false
                    onDismiss()
                }
            },
            nicknameRepository = nicknameRepository
        )
    }
}

/**
 * Рядок дії у контекстному меню
 */
@Composable
private fun ContactMenuRow(
    icon: ImageVector,
    title: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = tint
        )
    }
}

/**
 * Діалог для перейменування контакту (встановлення локального псевдоніма)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenameContactDialog(
    chat: Chat,
    currentNickname: String?,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit,
    nicknameRepository: ContactNicknameRepository
) {
    val existingNickname by nicknameRepository.getNickname(chat.userId).collectAsState(initial = null)
    var nickname by remember { mutableStateOf(existingNickname ?: "") }

    // Оновлюємо nickname, коли existingNickname змінюється
    LaunchedEffect(existingNickname) {
        existingNickname?.let {
            nickname = it
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Перейменувати контакт")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Встановіть зручне ім'я для контакту ${chat.username}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("Псевдонім") },
                    placeholder = { Text(chat.username ?: "Введіть псевдонім") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )

                if (existingNickname != null) {
                    TextButton(
                        onClick = {
                            nickname = ""
                            onSave(null)
                        },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Скинути до оригінального імені")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(if (nickname.isBlank()) null else nickname.trim())
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Зберегти")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Скасувати")
            }
        }
    )
}

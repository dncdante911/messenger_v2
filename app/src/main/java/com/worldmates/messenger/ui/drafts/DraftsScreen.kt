package com.worldmates.messenger.ui.drafts

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.data.local.entity.Draft
import com.worldmates.messenger.data.repository.DraftRepository
import com.worldmates.messenger.ui.messages.MessagesActivity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Экран со списком всех черновиков
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val draftRepository = remember { DraftRepository.getInstance(context) }

    // Список черновиков
    val drafts by draftRepository.getAllDrafts().collectAsState(initial = emptyList())

    var showDeleteAllDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Черновики",
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (drafts.isNotEmpty()) {
                            Text(
                                text = "${drafts.size} ${getPluralForm(drafts.size)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (drafts.isNotEmpty()) {
                        IconButton(onClick = { showDeleteAllDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить все")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (drafts.isEmpty()) {
                // Пустое состояние
                EmptyDraftsView()
            } else {
                // Список черновиков
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(drafts, key = { it.chatId }) { draft ->
                        DraftItem(
                            draft = draft,
                            onClick = {
                                // Открываем чат с черновиком
                                openChatWithDraft(context, draft)
                            },
                            onDelete = {
                                scope.launch {
                                    draftRepository.deleteDraft(draft.chatId)
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }

        // Диалог удаления всех черновиков
        if (showDeleteAllDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteAllDialog = false },
                icon = {
                    Icon(Icons.Default.Delete, contentDescription = null)
                },
                title = {
                    Text("Удалить все черновики?")
                },
                text = {
                    Text("Это действие нельзя отменить. Все черновики (${drafts.size}) будут удалены.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                draftRepository.deleteAllDrafts()
                                showDeleteAllDialog = false
                            }
                        }
                    ) {
                        Text("Удалить")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteAllDialog = false }) {
                        Text("Отмена")
                    }
                }
            )
        }
    }
}

/**
 * Элемент черновика в списке
 */
@Composable
private fun DraftItem(
    draft: Draft,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Иконка
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Chat,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Текст черновика
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = draft.text,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDate(draft.updatedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
                Text(
                    text = if (draft.chatType == Draft.CHAT_TYPE_GROUP) "Группа" else "Чат",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        // Кнопка удаления
        IconButton(onClick = { showDeleteDialog = true }) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Удалить",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }

    // Диалог подтверждения удаления
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = {
                Icon(Icons.Default.Delete, contentDescription = null)
            },
            title = {
                Text("Удалить черновик?")
            },
            text = {
                Text("Вы уверены, что хотите удалить этот черновик?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

/**
 * Пустое состояние (нет черновиков)
 */
@Composable
private fun EmptyDraftsView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Chat,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Нет черновиков",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Черновики сообщений появятся здесь",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

/**
 * Форматирование даты для отображения
 */
private fun formatDate(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "Только что"
        diff < 3600_000 -> "${diff / 60_000} мин назад"
        diff < 86400_000 -> "${diff / 3600_000} ч назад"
        diff < 604800_000 -> "${diff / 86400_000} д назад"
        else -> {
            val format = SimpleDateFormat("dd MMM yyyy", Locale("ru"))
            format.format(Date(timestamp))
        }
    }
}

/**
 * Получить правильную форму слова "черновик"
 */
private fun getPluralForm(count: Int): String {
    val lastDigit = count % 10
    val lastTwoDigits = count % 100

    return when {
        lastTwoDigits in 11..14 -> "черновиков"
        lastDigit == 1 -> "черновик"
        lastDigit in 2..4 -> "черновика"
        else -> "черновиков"
    }
}

/**
 * Открыть чат с черновиком
 */
private fun openChatWithDraft(context: android.content.Context, draft: Draft) {
    val intent = Intent(context, MessagesActivity::class.java).apply {
        if (draft.chatType == Draft.CHAT_TYPE_GROUP) {
            putExtra("group_id", draft.chatId)
            putExtra("is_group", true)
        } else {
            putExtra("recipient_id", draft.chatId)
        }
    }
    context.startActivity(intent)
}

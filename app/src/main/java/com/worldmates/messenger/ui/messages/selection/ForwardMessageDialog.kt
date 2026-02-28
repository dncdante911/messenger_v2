package com.worldmates.messenger.ui.messages.selection

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.worldmates.messenger.R

/**
 * 📤 Діалог для вибору отримувачів при пересиланні повідомлень
 *
 * Показує список контактів та груп для вибору.
 * - Можливість вибору декількох отримувачів
 * - Пошук по контактах
 * - Групи та окремі користувачі
 *
 * @param visible Чи показувати діалог
 * @param contacts Список контактів для вибору
 * @param groups Список груп для вибору
 * @param selectedCount Кількість вибраних повідомлень для пересилання
 * @param onForward Callback при натисканні "Переслати" (передає список ID отримувачів)
 * @param onDismiss Callback для закриття діалогу
 */
@Composable
fun ForwardMessageDialog(
    visible: Boolean,
    contacts: List<ForwardRecipient> = emptyList(),
    groups: List<ForwardRecipient> = emptyList(),
    selectedCount: Int = 1,
    onForward: (List<Long>) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedRecipients by remember { mutableStateOf(setOf<Long>()) }
    var searchQuery by remember { mutableStateOf("") }

    // Об'єднуємо контакти та групи
    val allRecipients = remember(contacts, groups) {
        contacts + groups
    }

    // Фільтруємо за пошуковим запитом
    val filteredRecipients = remember(allRecipients, searchQuery) {
        if (searchQuery.isBlank()) {
            allRecipients
        } else {
            allRecipients.filter { recipient ->
                recipient.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f)
                    .clickable(enabled = false, onClick = {}), // Блокуємо клік на діалозі
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Заголовок з кнопкою закриття
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.forward_message),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.forward_selected_count, selectedCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.close),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Поле пошуку
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.search_contacts_or_groups)) },
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Список отримувачів
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredRecipients) { recipient ->
                            RecipientItem(
                                recipient = recipient,
                                isSelected = selectedRecipients.contains(recipient.id),
                                onClick = {
                                    selectedRecipients = if (selectedRecipients.contains(recipient.id)) {
                                        selectedRecipients - recipient.id
                                    } else {
                                        selectedRecipients + recipient.id
                                    }
                                }
                            )
                        }

                        // Повідомлення якщо нічого не знайдено
                        if (filteredRecipients.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.nothing_found),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Кнопка "Переслати"
                    Button(
                        onClick = {
                            if (selectedRecipients.isNotEmpty()) {
                                onForward(selectedRecipients.toList())
                                selectedRecipients = emptySet()
                                onDismiss()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedRecipients.isNotEmpty(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = stringResource(R.string.forward_message),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.forward_count, selectedRecipients.size),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * 👤 Елемент списку отримувача (контакт або група)
 */
@Composable
fun RecipientItem(
    recipient: ForwardRecipient,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val groupLabel = stringResource(R.string.group_label)
    val contactLabel = stringResource(R.string.contact_label)
    val selectedLabel = stringResource(R.string.selected)
    val notSelectedLabel = stringResource(R.string.not_selected)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Аватар
            if (recipient.avatarUrl.isNotEmpty()) {
                AsyncImage(
                    model = recipient.avatarUrl,
                    contentDescription = recipient.name,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                )
            } else {
                // Placeholder якщо немає аватара
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = recipient.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Ім'я та тип (контакт/група)
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = recipient.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (recipient.isGroup) groupLabel else contactLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Індикатор вибору
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.Circle,
                contentDescription = if (isSelected) selectedLabel else notSelectedLabel,
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                },
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * 📋 Модель отримувача для пересилання
 */
data class ForwardRecipient(
    val id: Long,
    val name: String,
    val avatarUrl: String = "",
    val isGroup: Boolean = false
)

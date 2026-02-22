package com.worldmates.messenger.ui.chats

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.worldmates.messenger.data.model.Chat
import com.worldmates.messenger.data.model.Group
import java.text.SimpleDateFormat
import java.util.*

/**
 * Telegram-style компонент для чату
 * Мінімалістичний дизайн без градієнтів та анімацій
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TelegramChatItem(
    chat: Chat,
    nickname: String? = null,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Аватарка (маленька, як в Telegram)
        AsyncImage(
            model = chat.avatarUrl,
            contentDescription = chat.username,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Контент (ім'я, останнє повідомлення)
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // Ім'я користувача (псевдонім якщо є, інакше оригінальне)
            Text(
                text = nickname ?: chat.username ?: "Unknown",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (chat.unreadCount > 0) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 16.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Якщо є псевдонім, показуємо оригінальне ім'я нижче маленьким текстом
            if (nickname != null && chat.username != null) {
                Text(
                    text = "@${chat.username}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Останнє повідомлення
            if (chat.lastMessage != null) {
                val previewText = com.worldmates.messenger.ui.messages.getLastMessagePreview(chat.lastMessage)
                Text(
                    text = previewText.ifEmpty { "" },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        fontWeight = if (chat.unreadCount > 0) FontWeight.Medium else FontWeight.Normal
                    ),
                    color = if (chat.unreadCount > 0)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Права колонка (час, лічильник непрочитаних)
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Час останнього повідомлення
            if (chat.lastMessage != null) {
                Text(
                    text = formatTime(chat.lastMessage.timeStamp),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = if (chat.unreadCount > 0)
                        Color(0xFF3390EC) // Telegram blue
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            // Лічильник непрочитаних
            if (chat.unreadCount > 0) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF3390EC), // Telegram blue
                    modifier = Modifier.size(22.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString(),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }

    // Divider між елементами
    Divider(
        modifier = Modifier.padding(start = 76.dp), // Зміщення для вирівнювання з текстом
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        thickness = 0.5.dp
    )
}

/**
 * Telegram-style компонент для групи
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TelegramGroupItem(
    group: Group,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Аватарка групи
        AsyncImage(
            model = group.avatarUrl,
            contentDescription = group.name,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Контент
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // Назва групи
            Text(
                text = group.name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(3.dp))

            // Кількість учасників
            Text(
                text = "${group.membersCount} учасників",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Індикатор типу групи
        if (group.isPrivate) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = "Приватна група",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }

    // Divider
    Divider(
        modifier = Modifier.padding(start = 76.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        thickness = 0.5.dp
    )
}

/**
 * Форматування часу для Telegram-style
 */
private fun formatTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "щойно" // Менше хвилини
        diff < 3600_000 -> "${diff / 60_000} хв" // Менше години
        diff < 86400_000 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp)) // Сьогодні
        diff < 172800_000 -> "вчора" // Вчора
        diff < 604800_000 -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(timestamp)) // Цього тижня
        else -> SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(Date(timestamp)) // Давно
    }
}

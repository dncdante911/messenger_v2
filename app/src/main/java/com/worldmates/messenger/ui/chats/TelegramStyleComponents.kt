package com.worldmates.messenger.ui.chats

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.worldmates.messenger.R
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
    isOnline: Boolean = false,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar with online indicator
        Box {
            AsyncImage(
                model = chat.avatarUrl,
                contentDescription = chat.username,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            if (isOnline) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Color(0xFF4CAF50))
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                )
            }
        }

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
                val context = LocalContext.current
                Text(
                    text = formatTime(chat.lastMessage.timeStamp, context),
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
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
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
            val membersText = getMembersCountText(group.membersCount, LocalContext.current)
            Text(
                text = "${group.membersCount} $membersText",
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
                contentDescription = LocalContext.current.getString(R.string.private_group_cd),
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
private fun formatTime(timestamp: Long, context: Context): String {
    // timestamp from server is Unix seconds; convert to ms for Date/diff
    val tsMs = if (timestamp > 1_000_000_000_000L) timestamp else timestamp * 1000L
    val now = System.currentTimeMillis()
    val diff = now - tsMs

    return when {
        diff < 60_000 -> context.getString(R.string.time_just_now)
        diff < 3600_000 -> context.getString(R.string.time_min_short, diff / 60_000)
        diff < 86400_000 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(tsMs))
        diff < 172800_000 -> context.getString(R.string.time_yesterday)
        diff < 604800_000 -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(tsMs))
        else -> SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(Date(tsMs))
    }
}

/**
 * Plural forms for members count
 */
private fun getMembersCountText(count: Int, context: Context): String {
    return when {
        count % 10 == 1 && count % 100 != 11 -> context.getString(R.string.member_one)
        count % 10 in 2..4 && (count % 100 < 10 || count % 100 >= 20) -> context.getString(R.string.member_few)
        else -> context.getString(R.string.members_many)
    }
}

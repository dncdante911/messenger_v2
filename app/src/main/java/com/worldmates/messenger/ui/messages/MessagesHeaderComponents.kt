package com.worldmates.messenger.ui.messages

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.worldmates.messenger.data.model.UserPresenceStatus
import com.worldmates.messenger.network.NetworkQualityMonitor
import com.worldmates.messenger.ui.messages.selection.SelectionTopBarActions
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesHeaderBar(
    recipientName: String,
    recipientAvatar: String,
    presenceStatus: UserPresenceStatus = UserPresenceStatus.Offline,
    onBackPressed: () -> Unit,
    onUserProfileClick: () -> Unit = {},
    onCallClick: () -> Unit = {},
    onVideoCallClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onMuteClick: () -> Unit = {},
    onClearHistoryClick: () -> Unit = {},
    onChangeWallpaperClick: () -> Unit = {},
    onBlockClick: () -> Unit = {},
    isUserBlocked: Boolean = false,
    isMuted: Boolean = false,
    // 🔥 Group-specific parameters
    isGroup: Boolean = false,
    isGroupAdmin: Boolean = false,
    onCreateSubgroupClick: () -> Unit = {},
    onAddMembersClick: () -> Unit = {},
    onGroupSettingsClick: () -> Unit = {},
    // 🔥 Параметри для режиму вибору
    isSelectionMode: Boolean = false,
    selectedCount: Int = 0,
    totalCount: Int = 0,
    canEdit: Boolean = false,
    canPin: Boolean = false,
    onEditSelected: () -> Unit = {},
    onPinSelected: () -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    onSelectAll: () -> Unit = {},
    onCloseSelectionMode: () -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    var showUserMenu by remember { mutableStateOf(false) }

    // Telegram-style AppBar - четкий и читаемый
    TopAppBar(
        title = {
            // 🔥 В режимі вибору показуємо кількість вибраних
            if (isSelectionMode) {
                Text(
                    text = "$selectedCount вибрано",
                    color = colorScheme.onPrimary,
                    fontSize = 20.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxHeight()
                        .clickable { onUserProfileClick() }
                ) {
                    // Avatar with online status dot
                    if (recipientAvatar.isNotEmpty()) {
                        Box {
                            AsyncImage(
                                model = recipientAvatar,
                                contentDescription = recipientName,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            val dotColor = when (presenceStatus) {
                                is UserPresenceStatus.Online,
                                is UserPresenceStatus.Typing,
                                is UserPresenceStatus.GroupTyping,
                                is UserPresenceStatus.RecordingVoice,
                                is UserPresenceStatus.RecordingVideo,
                                is UserPresenceStatus.ListeningAudio,
                                is UserPresenceStatus.ViewingMedia,
                                is UserPresenceStatus.ChoosingSticker -> Color(0xFF4CAF50)
                                else -> Color.Gray
                            }
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .align(Alignment.BottomEnd)
                                    .clip(CircleShape)
                                    .background(dotColor)
                                    .border(2.dp, Color.White, CircleShape)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    // Name and animated presence status
                    Column {
                        Text(recipientName, color = colorScheme.onPrimary)
                        PresenceStatusText(
                            status = presenceStatus,
                            textColor = colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBackPressed) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Назад",
                    tint = colorScheme.onPrimary
                )
            }
        },
        actions = {
            // 🔥 Режим вибору - показуємо кнопки дій
            if (isSelectionMode) {
                SelectionTopBarActions(
                    selectedCount = selectedCount,
                    totalCount = totalCount,
                    canEdit = canEdit,
                    canPin = canPin,
                    onEdit = onEditSelected,
                    onPin = onPinSelected,
                    onDelete = onDeleteSelected,
                    onSelectAll = onSelectAll,
                    onClose = onCloseSelectionMode
                )
            } else {
                // Звичайні кнопки
                // Кнопка пошуку
                IconButton(onClick = onSearchClick) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Пошук",
                        tint = colorScheme.onPrimary
                    )
                }

                // Кнопка дзвінка
                IconButton(onClick = onCallClick) {
                    Icon(
                        Icons.Default.Call,
                        contentDescription = "Дзвінок",
                        tint = colorScheme.onPrimary
                    )
                }

                // Кнопка меню (3 крапки)
                Box {
                    IconButton(onClick = { showUserMenu = !showUserMenu }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Більше",
                            tint = colorScheme.onPrimary
                        )
                    }

                    // Випадаюче меню
                    DropdownMenu(
                        expanded = showUserMenu,
                        onDismissRequest = { showUserMenu = false }
                    ) {
                        // ✅ Common options for both groups and users
                        DropdownMenuItem(
                            text = { Text(if (isGroup) "Деталі групи" else "Переглянути профіль") },
                            onClick = {
                                showUserMenu = false
                                onUserProfileClick()
                            },
                            leadingIcon = {
                                Icon(if (isGroup) Icons.Default.Group else Icons.Default.Person, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Відеодзвінок") },
                            onClick = {
                                showUserMenu = false
                                onVideoCallClick()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.VideoCall, contentDescription = null)
                            }
                        )

                        // ✅ GROUP-SPECIFIC OPTIONS
                        if (isGroup) {
                            Divider()
                            // Add members option
                            DropdownMenuItem(
                                text = { Text("Додати учасників") },
                                onClick = {
                                    showUserMenu = false
                                    onAddMembersClick()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color(0xFF0084FF))
                                }
                            )
                            // Create subgroup/folder option (for admins)
                            if (isGroupAdmin) {
                                DropdownMenuItem(
                                    text = { Text("Створити підгрупу/папку") },
                                    onClick = {
                                        showUserMenu = false
                                        onCreateSubgroupClick()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = Color(0xFF4CAF50))
                                    }
                                )
                            }
                            // Group settings (for admins)
                            if (isGroupAdmin) {
                                DropdownMenuItem(
                                    text = { Text("Налаштування групи") },
                                    onClick = {
                                        showUserMenu = false
                                        onGroupSettingsClick()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Settings, contentDescription = null)
                                    }
                                )
                            }
                        }

                        Divider()
                        DropdownMenuItem(
                            text = {
                                Text(if (isMuted) "Увімкнути сповіщення" else "Вимкнути сповіщення")
                            },
                            onClick = {
                                showUserMenu = false
                                onMuteClick()
                            },
                            leadingIcon = {
                                Icon(
                                    if (isMuted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = if (isMuted) Color(0xFFF44336) else LocalContentColor.current
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Змінити обої") },
                            onClick = {
                                showUserMenu = false
                                onChangeWallpaperClick()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Image, contentDescription = null)
                            }
                        )
                        Divider()
                        DropdownMenuItem(
                            text = { Text("Очистити історію") },
                            onClick = {
                                showUserMenu = false
                                onClearHistoryClick()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, contentDescription = null)
                            }
                        )

                        // ✅ User-only option: block user
                        if (!isGroup) {
                            Divider()
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (isUserBlocked) "Розблокувати користувача" else "Заблокувати користувача",
                                        color = if (isUserBlocked) Color(0xFF4CAF50) else Color(0xFFF44336)
                                    )
                                },
                                onClick = {
                                    showUserMenu = false
                                    onBlockClick()
                                },
                                leadingIcon = {
                                    Icon(
                                        if (isUserBlocked) Icons.Default.LockOpen else Icons.Default.Block,
                                        contentDescription = null,
                                        tint = if (isUserBlocked) Color(0xFF4CAF50) else Color(0xFFF44336)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colorScheme.primary,  // Цвет темы
            titleContentColor = colorScheme.onPrimary,
            navigationIconContentColor = colorScheme.onPrimary,
            actionIconContentColor = colorScheme.onPrimary
        )
    )  // Конец TopAppBar
}

/** Animated three-dot typing indicator (●  ●  ●) */
@Composable
private fun TypingDots(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes { durationMillis = 900; 1f at 300; 0.3f at 600 },
            repeatMode = RepeatMode.Restart
        ), label = "dot1"
    )
    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes { durationMillis = 900; 0.3f at 150; 1f at 450; 0.3f at 750 },
            repeatMode = RepeatMode.Restart
        ), label = "dot2"
    )
    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes { durationMillis = 900; 0.3f at 300; 1f at 600 },
            repeatMode = RepeatMode.Restart
        ), label = "dot3"
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        listOf(dot1Alpha, dot2Alpha, dot3Alpha).forEach { alpha ->
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = alpha))
            )
            Spacer(Modifier.width(2.dp))
        }
    }
}

/** Formats a Unix-seconds timestamp for "last seen" display. */
private fun formatLastSeen(ts: Long): String {
    if (ts <= 0L) return "офлайн"
    val now = System.currentTimeMillis()
    val diff = now - ts * 1000L
    val cal = Calendar.getInstance().apply { timeInMillis = ts * 1000L }
    val today = Calendar.getInstance()

    return when {
        diff < 60_000L -> "був(ла) нещодавно"
        diff < 3_600_000L -> {
            val mins = (diff / 60_000L).toInt()
            "був(ла) $mins хв тому"
        }
        cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> {
            val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            "був(ла) сьогодні о ${fmt.format(Date(ts * 1000L))}"
        }
        diff < 172_800_000L -> {
            val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            "був(ла) вчора о ${fmt.format(Date(ts * 1000L))}"
        }
        else -> {
            val fmt = SimpleDateFormat("d MMM", Locale.getDefault())
            "був(ла) ${fmt.format(Date(ts * 1000L))}"
        }
    }
}

/**
 * Shows the current presence/activity status text under the recipient name.
 * Typing and group-typing show animated dots.
 */
@Composable
fun PresenceStatusText(status: UserPresenceStatus, textColor: Color) {
    val fontSize = 12.sp
    when (status) {
        is UserPresenceStatus.Typing -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("друкує", fontSize = fontSize, color = textColor)
                Spacer(Modifier.width(3.dp))
                TypingDots(color = textColor)
            }
        }
        is UserPresenceStatus.GroupTyping -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${status.userName} друкує", fontSize = fontSize, color = textColor)
                Spacer(Modifier.width(3.dp))
                TypingDots(color = textColor)
            }
        }
        is UserPresenceStatus.RecordingVoice -> {
            Text("🎤 пише голосове...", fontSize = fontSize, color = textColor)
        }
        is UserPresenceStatus.RecordingVideo -> {
            Text("🎥 знімає відео...", fontSize = fontSize, color = textColor)
        }
        is UserPresenceStatus.ListeningAudio -> {
            Text("🎧 слухає аудіо...", fontSize = fontSize, color = textColor)
        }
        is UserPresenceStatus.ViewingMedia -> {
            Text("👁 переглядає...", fontSize = fontSize, color = textColor)
        }
        is UserPresenceStatus.ChoosingSticker -> {
            Text("😊 вибирає стикер...", fontSize = fontSize, color = textColor)
        }
        is UserPresenceStatus.Online -> {
            Text("онлайн", fontSize = fontSize, color = textColor)
        }
        is UserPresenceStatus.LastSeen -> {
            Text(formatLastSeen(status.timestamp), fontSize = fontSize, color = textColor)
        }
        is UserPresenceStatus.Offline -> {
            // Show nothing for plain offline with no lastSeen data
        }
    }
}

@Composable
fun ConnectionQualityBanner(quality: NetworkQualityMonitor.ConnectionQuality) {
    // Показуємо banner тільки якщо з'єднання не EXCELLENT
    if (quality == NetworkQualityMonitor.ConnectionQuality.EXCELLENT) {
        return
    }

    val (text, color, icon) = when (quality) {
        NetworkQualityMonitor.ConnectionQuality.GOOD ->
            Triple(
                "🟡 Добре з'єднання. Медіа завантажуються як превью.",
                Color(0xFFFFA500),
                Icons.Default.SignalCellularAlt
            )
        NetworkQualityMonitor.ConnectionQuality.POOR ->
            Triple(
                "🟠 Погане з'єднання. Завантажується тільки текст.",
                Color(0xFFFF6B6B),
                Icons.Default.SignalCellularAlt
            )
        NetworkQualityMonitor.ConnectionQuality.OFFLINE ->
            Triple(
                "🔴 Немає з'єднання. Показуються кешовані повідомлення.",
                Color(0xFFE74C3C),
                Icons.Default.WifiOff
            )
        else -> return // Не показуємо для EXCELLENT
    }

    AnimatedVisibility(
        visible = true,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            color = color.copy(alpha = 0.15f),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = text,
                    fontSize = 14.sp,
                    color = color,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

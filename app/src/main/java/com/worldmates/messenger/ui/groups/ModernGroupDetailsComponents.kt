package com.worldmates.messenger.ui.groups

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.worldmates.messenger.data.model.Group
import com.worldmates.messenger.data.model.GroupMember
import com.worldmates.messenger.data.model.isOwner
import com.worldmates.messenger.data.model.isAdmin
import com.worldmates.messenger.data.model.isModerator
import com.worldmates.messenger.data.model.isOnline
import com.worldmates.messenger.data.model.avatar

/**
 * Великий header групи з аватаром та інформацією
 */
@Composable
fun GroupDetailsHeader(
    group: Group,
    onAvatarClick: () -> Unit,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF667EEA),
                        Color(0xFF764BA2)
                    )
                )
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Великий аватар
        Box(
            modifier = Modifier.clickable(onClick = onAvatarClick)
        ) {
            AsyncImage(
                model = group.avatarUrl,
                contentDescription = group.name,
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )

            // Кнопка редагування (якщо є права)
            if (group.isAdmin || group.isOwner == true) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Редагувати",
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .padding(6.dp)
                        .clickable(onClick = onEditClick),
                    tint = Color(0xFF667EEA)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Назва групи
        Text(
            text = group.name,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        // Опис групи
        if (!group.description.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = group.description,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Статистика
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth()
        ) {
            StatCard(
                icon = Icons.Default.Group,
                value = "${group.membersCount}",
                label = "Учасників"
            )
            StatCard(
                icon = if (group.isPrivate) Icons.Default.Lock else Icons.Default.Public,
                value = if (group.isPrivate) "Приватна" else "Публічна",
                label = "Тип"
            )
            StatCard(
                icon = Icons.Default.Shield,
                value = if (group.isAdmin || group.isOwner == true) "Адмін" else "Учасник",
                label = "Роль"
            )
        }
    }
}

/**
 * Карткастатистики
 */
@Composable
fun StatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(8.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

/**
 * Вкладки для деталей групи
 */
@Composable
fun GroupDetailsTabs(
    selectedTab: GroupTab,
    onTabSelected: (GroupTab) -> Unit
) {
    TabRow(
        selectedTabIndex = selectedTab.ordinal,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = Color(0xFF667EEA)
    ) {
        GroupTab.values().forEach { tab ->
            Tab(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            tab.icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(tab.title)
                    }
                }
            )
        }
    }
}

/**
 * Список учасників групи з ролями
 */
@Composable
fun MembersList(
    members: List<GroupMember>,
    currentUserId: Long,
    isAdmin: Boolean,
    onMemberClick: (GroupMember) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        items(members) { member ->
            ModernMemberCard(
                member = member,
                isCurrentUser = member.userId == currentUserId,
                onClick = { onMemberClick(member) }
            )
        }
    }
}

/**
 * Картка учасника групи (для списку учасників)
 */
@Composable
fun ModernMemberCard(
    member: GroupMember,
    isCurrentUser: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentUser)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Аватар
            AsyncImage(
                model = member.avatar,
                contentDescription = member.username,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Інфо
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = member.username,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (isCurrentUser) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "(Ви)",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Роль
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val role = when {
                        member.isOwner -> "Власник"
                        member.isAdmin -> "Адміністратор"
                        member.isModerator -> "Модератор"
                        else -> "Учасник"
                    }

                    val roleIcon = when {
                        member.isOwner -> Icons.Default.Star
                        member.isAdmin -> Icons.Default.Shield
                        member.isModerator -> Icons.Default.VerifiedUser
                        else -> Icons.Default.Person
                    }

                    Icon(
                        roleIcon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = when {
                            member.isOwner -> Color(0xFFFFD700)
                            member.isAdmin -> Color(0xFFFF6B6B)
                            member.isModerator -> Color(0xFF4ECDC4)
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = role,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            // Online індикатор
            if (member.isOnline) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4CAF50))
                )
            }
        }
    }
}

/**
 * Налаштування групи
 */
@Composable
fun GroupSettings(
    group: Group,
    isAdmin: Boolean,
    notificationsEnabled: Boolean = true,
    isMuted: Boolean = false,
    joinRequestsEnabled: Boolean = false,
    onSettingChange: (String, Boolean) -> Unit,
    onLeaveGroup: () -> Unit = {},
    onDeleteGroup: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Секція: Повідомлення
        SettingsSection(title = "Повідомлення") {
            SettingItem(
                icon = Icons.Default.Notifications,
                title = "Сповіщення",
                description = "Отримувати повідомлення з групи",
                checked = notificationsEnabled,
                onCheckedChange = { onSettingChange("notifications", it) }
            )

            SettingItem(
                icon = Icons.Default.VolumeOff,
                title = "Без звуку",
                description = "Вимкнути звук повідомлень",
                checked = isMuted,
                onCheckedChange = { onSettingChange("mute", it) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Секція: Конфіденційність (тільки для адмінів)
        if (isAdmin) {
            SettingsSection(title = "Конфіденційність") {
                SettingItem(
                    icon = Icons.Default.Lock,
                    title = "Приватна група",
                    description = "Тільки запрошені можуть приєднатися",
                    checked = group.isPrivate,
                    onCheckedChange = { onSettingChange("private", it) }
                )

                SettingItem(
                    icon = Icons.Default.CheckCircle,
                    title = "Підтвердження нових учасників",
                    description = "Адмін повинен схвалити запити",
                    checked = joinRequestsEnabled,
                    onCheckedChange = { onSettingChange("approve_members", it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Секція: Дії
        SettingsSection(title = "Дії") {
            DangerButton(
                icon = Icons.Default.ExitToApp,
                title = "Вийти з групи",
                onClick = onLeaveGroup
            )

            if (isAdmin) {
                Spacer(modifier = Modifier.height(8.dp))
                DangerButton(
                    icon = Icons.Default.Delete,
                    title = "Видалити групу",
                    onClick = onDeleteGroup,
                    color = Color(0xFFEF5350)
                )
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF667EEA),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(8.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun SettingItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF667EEA)
            )
        )
    }
}

@Composable
fun DangerButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
    color: Color = Color(0xFFFF6B6B)
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = color.copy(alpha = 0.1f),
            contentColor = color
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(icon, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(title)
    }
}

/**
 * Enum для вкладок
 */
enum class GroupTab(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    MEMBERS("Учасники", Icons.Default.Group),
    MEDIA("Медіа", Icons.Default.Image),
    SETTINGS("Налаштування", Icons.Default.Settings)
}

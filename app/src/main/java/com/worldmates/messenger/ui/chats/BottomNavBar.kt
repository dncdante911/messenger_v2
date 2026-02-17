package com.worldmates.messenger.ui.chats

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.worldmates.messenger.data.UserSession

/**
 * Вкладки нижньої навігації
 */
enum class BottomNavTab {
    CHATS,
    CONTACTS,
    SETTINGS,
    PROFILE
}

/**
 * Нижня панель навігації в стилі VK/Telegram
 * Чати | Контакти | Налаштування | Профіль
 * Враховує системні навігаційні кнопки (WindowInsets)
 */
@Composable
fun AppBottomNavBar(
    selectedTab: BottomNavTab,
    onTabSelected: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val avatarUrl by UserSession.avatarFlow.collectAsState()

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                thickness = 0.5.dp
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavItem(
                    icon = Icons.Outlined.Chat,
                    selectedIcon = Icons.Filled.Chat,
                    label = "Чати",
                    isSelected = selectedTab == BottomNavTab.CHATS,
                    onClick = { onTabSelected(BottomNavTab.CHATS) },
                    modifier = Modifier.weight(1f)
                )

                BottomNavItem(
                    icon = Icons.Outlined.Contacts,
                    selectedIcon = Icons.Filled.Contacts,
                    label = "Контакти",
                    isSelected = selectedTab == BottomNavTab.CONTACTS,
                    onClick = { onTabSelected(BottomNavTab.CONTACTS) },
                    modifier = Modifier.weight(1f)
                )

                BottomNavItem(
                    icon = Icons.Outlined.Settings,
                    selectedIcon = Icons.Filled.Settings,
                    label = "Настройки",
                    isSelected = selectedTab == BottomNavTab.SETTINGS,
                    onClick = { onTabSelected(BottomNavTab.SETTINGS) },
                    modifier = Modifier.weight(1f)
                )

                // Профіль з аватаром замість іконки
                ProfileNavItem(
                    avatarUrl = avatarUrl,
                    label = "Профіль",
                    isSelected = selectedTab == BottomNavTab.PROFILE,
                    onClick = { onTabSelected(BottomNavTab.PROFILE) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Елемент нижньої навігації з іконкою
 */
@Composable
private fun BottomNavItem(
    icon: ImageVector,
    selectedIcon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = if (isSelected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .selectable(
                selected = isSelected,
                onClick = onClick
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (isSelected) selectedIcon else icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(26.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

/**
 * Елемент навігації "Профіль" з аватаром
 */
@Composable
private fun ProfileNavItem(
    avatarUrl: String?,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = if (isSelected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .selectable(
                selected = isSelected,
                onClick = onClick
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(26.dp)
        ) {
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = label,
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .then(
                            if (isSelected) Modifier.border(
                                2.dp,
                                MaterialTheme.colorScheme.primary,
                                CircleShape
                            ) else Modifier
                        ),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = if (isSelected) Icons.Filled.Person else Icons.Outlined.Person,
                    contentDescription = label,
                    tint = color,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

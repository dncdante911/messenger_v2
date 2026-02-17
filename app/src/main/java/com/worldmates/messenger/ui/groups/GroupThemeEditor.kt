package com.worldmates.messenger.ui.groups

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.ui.preferences.BubbleStyle
import com.worldmates.messenger.ui.theme.PresetBackground

/**
 * Діалог редактора теми для групового чату.
 * Дозволяє адміну вибрати:
 * - Стиль бульбашок
 * - Фон чату
 * - Акцентний колір
 * Або вибрати готовий шаблон.
 */
@Composable
fun GroupThemeEditorDialog(
    groupId: Long,
    groupName: String,
    onDismiss: () -> Unit
) {
    val themes by GroupThemeManager.themes.collectAsState()
    val currentTheme = themes[groupId] ?: GroupTheme()

    var selectedBubbleStyle by remember { mutableStateOf(currentTheme.getBubbleStyle()) }
    var selectedBackground by remember { mutableStateOf(currentTheme.presetBackgroundId) }
    var selectedAccentColor by remember { mutableStateOf(currentTheme.accentColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Тема: $groupName")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Готові шаблони
                Text(
                    text = "Готові шаблони",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GroupTheme.PRESETS.forEach { preset ->
                        ThemePresetChip(
                            preset = preset,
                            isSelected = selectedBubbleStyle.name == preset.theme.bubbleStyle &&
                                    selectedBackground == preset.theme.presetBackgroundId,
                            onClick = {
                                selectedBubbleStyle = preset.theme.getBubbleStyle()
                                selectedBackground = preset.theme.presetBackgroundId
                                selectedAccentColor = preset.theme.accentColor
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Стиль бульбашок
                Text(
                    text = "Стиль бульбашок",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BubbleStyle.values().forEach { style ->
                        BubbleStyleChip(
                            style = style,
                            isSelected = style == selectedBubbleStyle,
                            onClick = { selectedBubbleStyle = style }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Фон чату
                Text(
                    text = "Фон чату",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PresetBackground.values().forEach { bg ->
                        MiniPresetBackgroundCard(
                            preset = bg,
                            isSelected = bg.id == selectedBackground,
                            onClick = { selectedBackground = bg.id }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Акцентний колір
                Text(
                    text = "Акцентний колір",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val accentColors = listOf(
                    "#2196F3", "#E91E63", "#4CAF50", "#FF9800",
                    "#9C27B0", "#00BCD4", "#FF5722", "#607D8B",
                    "#E040FB", "#7C4DFF", "#F44336", "#009688"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    accentColors.forEach { color ->
                        val parsed = try {
                            Color(android.graphics.Color.parseColor(color))
                        } catch (e: Exception) {
                            Color.Blue
                        }

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(parsed)
                                .then(
                                    if (color == selectedAccentColor)
                                        Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                )
                                .clickable { selectedAccentColor = color },
                            contentAlignment = Alignment.Center
                        ) {
                            if (color == selectedAccentColor) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Кнопка скинути до глобальної теми
                if (GroupThemeManager.hasCustomTheme(groupId)) {
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        onClick = {
                            GroupThemeManager.removeGroupTheme(groupId)
                            onDismiss()
                        }
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Скинути до глобальної теми",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    GroupThemeManager.setGroupTheme(
                        groupId,
                        GroupTheme(
                            bubbleStyle = selectedBubbleStyle.name,
                            presetBackgroundId = selectedBackground,
                            accentColor = selectedAccentColor
                        )
                    )
                    onDismiss()
                }
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

/**
 * Чіп готового шаблону теми
 */
@Composable
private fun ThemePresetChip(
    preset: GroupThemePreset,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceVariant
    )

    Card(
        modifier = Modifier
            .width(90.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 1.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(preset.emoji, fontSize = 24.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = preset.name,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1,
                color = if (isSelected)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Чіп для вибору стилю бульбашок
 */
@Composable
private fun BubbleStyleChip(
    style: BubbleStyle,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceVariant
    )

    Card(
        modifier = Modifier
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(style.icon, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = style.displayName,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Мініатюрна карточка фону для вибору в діалозі
 */
@Composable
private fun MiniPresetBackgroundCard(
    preset: PresetBackground,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )

    Box(
        modifier = Modifier
            .size(56.dp)
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(
                brush = Brush.linearGradient(colors = preset.gradientColors),
                shape = RoundedCornerShape(10.dp)
            )
            .then(
                if (isSelected) Modifier.border(
                    2.dp,
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(10.dp)
                )
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(20.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        CircleShape
                    )
                    .padding(2.dp)
            )
        }
        Text(
            text = preset.displayName.take(4),
            fontSize = 8.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 2.dp)
        )
    }
}

/**
 * Кнопка відкриття редактора теми групи (для вставки в GroupDetailsActivity)
 */
@Composable
fun GroupThemeButton(
    groupId: Long,
    groupName: String,
    modifier: Modifier = Modifier
) {
    var showEditor by remember { mutableStateOf(false) }
    val hasTheme = GroupThemeManager.hasCustomTheme(groupId)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showEditor = true },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hasTheme)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.ColorLens,
                contentDescription = null,
                tint = if (hasTheme)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Тема групи",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = if (hasTheme) "Кастомна тема встановлена" else "Використовується глобальна тема",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showEditor) {
        GroupThemeEditorDialog(
            groupId = groupId,
            groupName = groupName,
            onDismiss = { showEditor = false }
        )
    }
}

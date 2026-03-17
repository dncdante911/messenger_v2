package com.worldmates.messenger.ui.fonts

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.ui.premium.PremiumActivity

/**
 * Bottom sheet for picking a Unicode font style.
 * Shows a live preview of user's text in each style.
 * Premium-only — redirects to PremiumActivity if user is not subscribed.
 *
 * Usage:
 *   FontPickerSheet(
 *       previewText = firstName,
 *       currentStyle = selectedFontStyle,
 *       onStyleSelected = { style, styledText -> firstName = styledText; selectedFontStyle = style },
 *       onDismiss = { showFontPicker = false }
 *   )
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontPickerSheet(
    previewText: String,
    currentStyle: FontStyle = FontStyle.NORMAL,
    onStyleSelected: (style: FontStyle, styledText: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isPremium = UserSession.isProActive

    var localPreview by remember { mutableStateOf(previewText.ifBlank { "Привіт Світ" }) }
    var selected by remember { mutableStateOf(currentStyle) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.TextFields,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Стиль шрифту",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                if (!isPremium) {
                    PremiumBadge()
                }
            }

            Spacer(Modifier.height(12.dp))

            if (!isPremium) {
                // Premium lock banner
                PremiumLockBanner(
                    onUnlockClick = {
                        context.startActivity(Intent(context, PremiumActivity::class.java))
                        onDismiss()
                    }
                )
                Spacer(Modifier.height(16.dp))
            }

            // Live preview input
            Text(
                text = "Попередній перегляд",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (isPremium) {
                    BasicTextField(
                        value = localPreview,
                        onValueChange = { localPreview = it },
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                } else {
                    Text(
                        text = FontStyleConverter.convert(localPreview, selected),
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Live styled preview
            if (isPremium && selected != FontStyle.NORMAL) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = FontStyleConverter.convert(localPreview, selected),
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            Text(
                text = "Оберіть стиль (${styleCount(FontStyle.entries.size - 1)})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            // Font style grid — 3 columns to fit 50 styles
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
            ) {
                items(FontStyle.entries) { style ->
                    FontStyleCard(
                        style = style,
                        previewText = localPreview,
                        isSelected = selected == style,
                        isLocked = !isPremium && style != FontStyle.NORMAL,
                        onClick = {
                            if (!isPremium && style != FontStyle.NORMAL) {
                                context.startActivity(Intent(context, PremiumActivity::class.java))
                                onDismiss()
                                return@FontStyleCard
                            }
                            selected = style
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Apply button
            Button(
                onClick = {
                    val styledText = FontStyleConverter.convert(localPreview, selected)
                    onStyleSelected(selected, styledText)
                    onDismiss()
                },
                enabled = isPremium || selected == FontStyle.NORMAL,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Застосувати")
            }
        }
    }
}

// ==================== CHILD COMPOSABLES ====================

@Composable
private fun FontStyleCard(
    style: FontStyle,
    previewText: String,
    isSelected: Boolean,
    isLocked: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val borderColor = if (isSelected) colorScheme.primary else colorScheme.outlineVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .background(if (isSelected) colorScheme.primaryContainer.copy(alpha = 0.3f) else colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = style.emoji,
                    fontSize = 14.sp
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = style.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isLocked) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Тільки для Premium",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(12.dp)
                    )
                } else if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Вибрано",
                        tint = colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (isLocked) style.sampleText
                       else FontStyleConverter.convert(previewText.ifBlank { "Привіт" }, style),
                fontSize = 14.sp,
                color = if (isLocked) colorScheme.onSurface.copy(alpha = 0.4f) else colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start
            )
        }
    }
}

@Composable
private fun PremiumBadge() {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFFFD700).copy(alpha = 0.2f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = Color(0xFFB8860B),
                modifier = Modifier.size(12.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "Premium",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFB8860B)
            )
        }
    }
}

/** Ukrainian plural declension for "варіант". */
private fun styleCount(n: Int): String {
    val last2 = n % 100
    val last1 = n % 10
    val form = when {
        last2 in 11..19 -> "варіантів"
        last1 == 1      -> "варіант"
        last1 in 2..4   -> "варіанти"
        else            -> "варіантів"
    }
    return "$n $form"
}

@Composable
private fun PremiumLockBanner(onUnlockClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFFFD700).copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "✨", fontSize = 20.sp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Стилі шрифтів — Premium",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Оформлюй нікнейм та повідомлення по-особливому",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onUnlockClick) {
                Text("Отримати", fontWeight = FontWeight.Bold, color = Color(0xFFB8860B))
            }
        }
    }
}

package com.worldmates.messenger.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.R

/** Набор эмодзи для быстрого выбора статуса (6 колонок × 4 строки). */
private val PRESET_EMOJIS = listOf(
    "🔥", "✨", "🎯", "💡", "🚀", "🎮",
    "🎵", "❤️", "😎", "🤔", "💪", "🌙",
    "🌈", "🍀", "⚡", "🎉", "🏆", "🌸",
    "🦋", "🐉", "📚", "🎨", "☕", "🌍",
)

/**
 * Шторка для установки/очистки кастомного эмодзи-статуса (PRO-фича).
 *
 * @param currentEmoji  текущий эмодзи пользователя (или null)
 * @param currentText   текущий текст статуса (или null)
 * @param isPro         разблокировать поля только для PRO
 * @param onDismiss     закрыть шторку без изменений
 * @param onSave        сохранить: (emoji, text) — null/null = очистить статус
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiStatusSheet(
    currentEmoji: String?,
    currentText:  String?,
    isPro:        Boolean,
    onDismiss:    () -> Unit,
    onSave:       (emoji: String?, text: String?) -> Unit,
) {
    var selectedEmoji by remember { mutableStateOf(currentEmoji ?: "") }
    var statusText    by remember { mutableStateOf(currentText  ?: "") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle       = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
        ) {
            // ── Заголовок ────────────────────────────────────────────────────
            Text(
                text       = stringResource(R.string.custom_status_title),
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text  = stringResource(R.string.custom_status_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ── PRO-баннер (если не PRO) ─────────────────────────────────────
            if (!isPro) {
                Spacer(Modifier.height(14.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF667EEA), Color(0xFF764BA2))
                                    )
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("✦", color = Color.White, fontSize = 16.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text  = stringResource(R.string.custom_status_premium_title),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text  = stringResource(R.string.custom_status_premium_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }

            // ── Предпросмотр + поле текста ───────────────────────────────────
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Emoji preview box
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .then(
                            if (selectedEmoji.isNotBlank())
                                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                            else
                                Modifier
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selectedEmoji.isNotBlank()) {
                        Text(text = selectedEmoji, fontSize = 28.sp, textAlign = TextAlign.Center)
                    } else {
                        Icon(
                            Icons.Default.EmojiEmotions,
                            contentDescription = null,
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value          = statusText,
                    onValueChange  = { if (it.length <= 100) statusText = it },
                    label          = { Text(stringResource(R.string.custom_status_label)) },
                    placeholder    = { Text(stringResource(R.string.custom_status_placeholder)) },
                    enabled        = isPro,
                    modifier       = Modifier.weight(1f),
                    singleLine     = true,
                    supportingText = if (isPro) {
                        { Text("${statusText.length}/100", style = MaterialTheme.typography.labelSmall) }
                    } else null,
                )
            }

            // ── Сетка быстрого выбора ────────────────────────────────────────
            Spacer(Modifier.height(16.dp))
            Text(
                text  = stringResource(R.string.custom_status_presets),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(
                columns               = GridCells.Fixed(6),
                modifier              = Modifier.heightIn(max = 220.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement   = Arrangement.spacedBy(6.dp),
                contentPadding        = PaddingValues(0.dp),
                userScrollEnabled     = false,
            ) {
                items(PRESET_EMOJIS) { emoji ->
                    val isSelected = selectedEmoji == emoji
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .then(
                                if (isSelected) Modifier.border(
                                    1.5.dp,
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(10.dp)
                                ) else Modifier
                            )
                            .clickable(enabled = isPro) { selectedEmoji = emoji },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = emoji, fontSize = 22.sp, textAlign = TextAlign.Center)
                    }
                }
            }

            // ── Кнопки действий ──────────────────────────────────────────────
            Spacer(Modifier.height(20.dp))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick  = { onSave(null, null) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.custom_status_clear))
                }
                Button(
                    onClick  = {
                        onSave(
                            selectedEmoji.ifBlank { null },
                            statusText.trim().ifBlank { null },
                        )
                    },
                    enabled  = isPro,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}

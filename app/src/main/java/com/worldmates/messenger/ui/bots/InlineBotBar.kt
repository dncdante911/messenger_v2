package com.worldmates.messenger.ui.bots

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
 * Панель inline-бота.
 *
 * Активується коли користувач вводить у поле вводу текст виду "@botusername запит".
 * Показує список результатів отриманих від бота.
 * При виборі результату — вставляє вміст у поле або надсилає повідомлення.
 *
 * Використання в MessagesScreen / поле вводу:
 *   // Визначаємо, чи поточний ввід є inline-запитом
 *   val inlineMatch = InlineBotBar.parseInlineQuery(inputText)
 *   if (inlineMatch != null) {
 *       InlineBotBar(
 *           botUsername = inlineMatch.botUsername,
 *           query       = inlineMatch.query,
 *           results     = inlineBotResults,
 *           isLoading   = inlineBotLoading,
 *           onResultChosen = { result -> onSendInlineResult(result) },
 *           onDismiss   = { clearInlineMode() }
 *       )
 *   }
 */

// ─── Data models ──────────────────────────────────────────────────────────────

data class InlineQueryMatch(
    val botUsername: String,
    val query: String
)

/**
 * Результат inline-запиту від бота.
 * Відповідає структурі InlineQueryResult з Bot API.
 */
data class InlineQueryResult(
    val id: String,
    val type: String,           // "article", "photo", "gif", "sticker", "document", ...
    val title: String = "",
    val description: String = "",
    val thumbUrl: String? = null,
    val inputMessageContent: String? = null,  // текст для відправки
    val url: String? = null,
    val hideUrl: Boolean = false,
    val switchPmText: String? = null,
    val switchPmParameter: String? = null
)

// ─── Composable ───────────────────────────────────────────────────────────────

@Composable
fun InlineBotBar(
    botUsername: String,
    query: String,
    results: List<InlineQueryResult>,
    isLoading: Boolean,
    switchPmText: String? = null,
    onResultChosen: (InlineQueryResult) -> Unit,
    onSwitchPm: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit  = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "@$botUsername",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Switch PM button (якщо є)
            if (switchPmText != null && onSwitchPm != null) {
                TextButton(
                    onClick = onSwitchPm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    Text(switchPmText, fontSize = 13.sp)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            if (results.isEmpty() && !isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (query.isEmpty()) "" else stringResource(R.string.inline_bot_no_results),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LazyColumn {
                items(results, key = { it.id }) { result ->
                    InlineResultItem(
                        result = result,
                        onClick = { onResultChosen(result) }
                    )
                }
            }
        }
    }
}

@Composable
private fun InlineResultItem(
    result: InlineQueryResult,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Thumbnail
        if (result.thumbUrl != null) {
            AsyncImage(
                model = result.thumbUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = result.type.take(1).uppercase(),
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            if (result.title.isNotEmpty()) {
                Text(
                    text = result.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (result.description.isNotEmpty()) {
                Text(
                    text = result.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = 72.dp)
    )
}

// ─── Helper: parse "@botname query" from input text ───────────────────────────

object InlineBotBar {
    private val INLINE_REGEX = Regex("""^@([a-zA-Z][a-zA-Z0-9_]{2,31}[bB][oO][tT])\s*(.*)$""")

    /**
     * Повертає [InlineQueryMatch] якщо текст відповідає патерну "@botname query",
     * або null якщо це не inline-запит.
     */
    fun parseInlineQuery(text: String): InlineQueryMatch? {
        val trimmed = text.trim()
        val match = INLINE_REGEX.matchEntire(trimmed) ?: return null
        return InlineQueryMatch(
            botUsername = match.groupValues[1],
            query       = match.groupValues[2].trim()
        )
    }
}

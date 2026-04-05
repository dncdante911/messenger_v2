package com.worldmates.messenger.ui.stories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.R
import com.worldmates.messenger.data.model.StoryAnalytics
import java.text.SimpleDateFormat
import java.util.*

/**
 * Аналитика сторис — ModalBottomSheet для владельца сторис.
 *
 * Показывает:
 *  - Уникальные просмотры (охват)
 *  - Реакции по типам
 *  - Комментарии
 *  - Вовлечённость (engagement rate, %)
 *  - Время публикации и срок действия
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryAnalyticsSheet(
    analytics: StoryAnalytics?,
    isLoading: Boolean,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Заголовок
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.story_analytics_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = null)
                }
            }

            Spacer(Modifier.height(16.dp))

            if (isLoading || analytics == null) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                // ── Основные метрики ─────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AnalyticStatCard(
                        modifier    = Modifier.weight(1f),
                        icon        = Icons.Default.Visibility,
                        value       = analytics.uniqueViews.toString(),
                        label       = stringResource(R.string.story_analytics_views),
                        accentColor = MaterialTheme.colorScheme.primary
                    )
                    AnalyticStatCard(
                        modifier    = Modifier.weight(1f),
                        icon        = Icons.Default.ThumbUp,
                        value       = analytics.totalReactions.toString(),
                        label       = stringResource(R.string.story_analytics_reactions),
                        accentColor = Color(0xFFE91E8C)
                    )
                    AnalyticStatCard(
                        modifier    = Modifier.weight(1f),
                        icon        = Icons.Default.ChatBubble,
                        value       = analytics.totalComments.toString(),
                        label       = stringResource(R.string.story_analytics_comments),
                        accentColor = Color(0xFF00BCD4)
                    )
                }

                Spacer(Modifier.height(12.dp))

                // ── Вовлечённость ─────────────────────────────────────────────
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.TrendingUp, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.story_analytics_engagement),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "${analytics.engagementRate}%",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                // ── Реакции по типам ──────────────────────────────────────────
                Text(
                    text = stringResource(R.string.story_analytics_reactions_breakdown),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(10.dp))

                val reactionItems = listOf(
                    "👍" to analytics.reactions.like,
                    "❤️" to analytics.reactions.love,
                    "😂" to analytics.reactions.haha,
                    "😮" to analytics.reactions.wow,
                    "😢" to analytics.reactions.sad,
                    "😡" to analytics.reactions.angry
                )
                val totalReactions = analytics.reactions.getTotalReactions().coerceAtLeast(1)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    reactionItems.forEach { (emoji, count) ->
                        ReactionProgressRow(emoji = emoji, count = count, total = totalReactions)
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                // ── Даты ─────────────────────────────────────────────────────
                val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                Text(
                    text = stringResource(
                        R.string.story_analytics_posted,
                        dateFormat.format(Date(analytics.postedAt * 1000))
                    ),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.story_analytics_expires,
                        dateFormat.format(Date(analytics.expiresAt * 1000))
                    ),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

// ─── Вспомогательные composable ───────────────────────────────────────────────

@Composable
private fun AnalyticStatCard(
    modifier:    Modifier,
    icon:        ImageVector,
    value:       String,
    label:       String,
    accentColor: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = accentColor.copy(alpha = 0.10f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = accentColor)
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun ReactionProgressRow(emoji: String, count: Int, total: Int) {
    val fraction = count.toFloat() / total.toFloat()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(emoji, fontSize = 18.sp, modifier = Modifier.width(28.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.weight(1f).height(8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Text(
            text = count.toString(),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(30.dp)
        )
    }
}

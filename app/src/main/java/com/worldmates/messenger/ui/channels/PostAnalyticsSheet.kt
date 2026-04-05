package com.worldmates.messenger.ui.channels

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.R
import com.worldmates.messenger.network.PostAnalyticsResponse

/**
 * Bottom sheet showing per-post analytics: views, reactions, comments, reach, engagement.
 * Shown to channel admins via the post options menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostAnalyticsSheet(
    analytics: PostAnalyticsResponse?,
    isLoading: Boolean,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surface,
        shape            = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text       = stringResource(R.string.post_analytics_title),
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(16.dp))

            when {
                isLoading -> {
                    Box(
                        modifier          = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment  = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                analytics == null -> {
                    Text(
                        text  = stringResource(R.string.stat_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    // Grid: 2 × 2 metric cards
                    Row(
                        modifier            = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AnalyticCard(
                            icon  = Icons.Default.Visibility,
                            label = stringResource(R.string.post_views_label),
                            value = formatAnalyticsNum(analytics.views),
                            color = Color(0xFF2196F3),
                            modifier = Modifier.weight(1f)
                        )
                        AnalyticCard(
                            icon  = Icons.Default.Favorite,
                            label = stringResource(R.string.post_reactions_label),
                            value = formatAnalyticsNum(analytics.reactionsCount),
                            color = Color(0xFFE91E63),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AnalyticCard(
                            icon  = Icons.Default.ChatBubble,
                            label = stringResource(R.string.post_comments_label),
                            value = formatAnalyticsNum(analytics.commentsCount),
                            color = Color(0xFF4CAF50),
                            modifier = Modifier.weight(1f)
                        )
                        AnalyticCard(
                            icon  = Icons.Default.People,
                            label = stringResource(R.string.post_reach_label),
                            value = "${analytics.reachPct}%",
                            color = Color(0xFFFF9800),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    HorizontalDivider()

                    Spacer(Modifier.height(12.dp))

                    // Engagement rate row
                    Surface(
                        shape  = RoundedCornerShape(12.dp),
                        color  = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text  = stringResource(R.string.post_engagement_label),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text  = "${analytics.engagementRate}%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticCard(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape    = RoundedCornerShape(14.dp),
        color    = color.copy(alpha = 0.08f),
        modifier = modifier
    ) {
        Column(
            modifier          = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = color,
                modifier           = Modifier.size(24.dp)
            )
            Text(
                text       = value,
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color      = color,
                textAlign  = TextAlign.Center
            )
            Text(
                text      = label,
                style     = MaterialTheme.typography.labelSmall,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                fontSize  = 11.sp
            )
        }
    }
}

private fun formatAnalyticsNum(n: Int): String = when {
    n >= 1_000_000 -> "${n / 1_000_000}M"
    n >= 1_000     -> "${n / 1_000}K"
    else           -> n.toString()
}

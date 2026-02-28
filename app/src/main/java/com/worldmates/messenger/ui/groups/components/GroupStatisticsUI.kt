package com.worldmates.messenger.ui.groups.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.worldmates.messenger.R
import com.worldmates.messenger.data.model.GroupStatistics
import com.worldmates.messenger.data.model.TopContributor

/**
 * Повна сторінка статистики групи
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupStatisticsScreen(
    statistics: GroupStatistics?,
    groupName: String,
    isLoading: Boolean,
    onBackClick: () -> Unit,
    onRefresh: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.statistics_tab)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (statistics == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.BarChart,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.statistics_unavailable),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Overview Cards
                StatisticsOverviewCards(statistics)

                // Activity Chart
                ActivityChartCard(statistics)

                // Members Growth
                MembersGrowthCard(statistics)

                // Top Contributors
                TopContributorsCard(statistics.topContributors ?: emptyList())

                // Peak Hours
                PeakHoursCard(statistics.peakHours ?: emptyList())
            }
        }
    }
}

/**
 * Картки з основними показниками
 */
@Composable
fun StatisticsOverviewCards(statistics: GroupStatistics) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                icon = Icons.Default.Group,
                title = stringResource(R.string.members_count_label),
                value = statistics.membersCount.toString(),
                trend = if (statistics.growthRate > 0) "+${statistics.growthRate.toInt()}%" else null,
                trendPositive = statistics.growthRate > 0,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                icon = Icons.Default.Message,
                title = stringResource(R.string.messages_count_label),
                value = formatNumber(statistics.messagesCount),
                subtitle = stringResource(R.string.all_time),
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                icon = Icons.Default.Today,
                title = stringResource(R.string.today_label),
                value = statistics.messagesToday.toString(),
                subtitle = stringResource(R.string.messages_count_label),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                icon = Icons.Default.DateRange,
                title = stringResource(R.string.week_label),
                value = statistics.messagesThisWeek.toString(),
                subtitle = stringResource(R.string.messages_count_label),
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                icon = Icons.Default.Visibility,
                title = "Активних (24г)",
                value = statistics.activeMembers24h.toString(),
                subtitle = "учасників",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                icon = Icons.Default.TrendingUp,
                title = "Активних (тижд.)",
                value = statistics.activeMembersWeek.toString(),
                subtitle = "учасників",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    title: String,
    value: String,
    subtitle: String? = null,
    trend: String? = null,
    trendPositive: Boolean = true,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (trend != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = if (trendPositive)
                            Color(0xFF4CAF50).copy(alpha = 0.2f)
                        else
                            MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = trend,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (trendPositive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Графік активності
 */
@Composable
fun ActivityChartCard(statistics: GroupStatistics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Активність за тиждень",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Simple bar chart
            val weekData = listOf(
                "Пн" to (statistics.messagesThisWeek / 7 * 0.8f).toInt(),
                "Вт" to (statistics.messagesThisWeek / 7 * 1.2f).toInt(),
                "Ср" to (statistics.messagesThisWeek / 7 * 1.0f).toInt(),
                "Чт" to (statistics.messagesThisWeek / 7 * 0.9f).toInt(),
                "Пт" to (statistics.messagesThisWeek / 7 * 1.3f).toInt(),
                "Сб" to (statistics.messagesThisWeek / 7 * 0.7f).toInt(),
                "Нд" to (statistics.messagesThisWeek / 7 * 0.6f).toInt()
            )

            val maxValue = weekData.maxOfOrNull { it.second } ?: 1

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                weekData.forEach { (day, value) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = value.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .height((80.dp * (value.toFloat() / maxValue)).coerceAtLeast(8.dp))
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = day,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Картка росту учасників
 */
@Composable
fun MembersGrowthCard(statistics: GroupStatistics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Динаміка учасників",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // New members today
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        color = Color(0xFF4CAF50).copy(alpha = 0.2f),
                        shape = CircleShape
                    ) {
                        Icon(
                            Icons.Default.PersonAdd,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "+${statistics.newMembersToday}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                    Text(
                        text = "Сьогодні",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // New members this week
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        shape = CircleShape
                    ) {
                        Icon(
                            Icons.Default.GroupAdd,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "+${statistics.newMembersWeek}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "За тиждень",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Left members
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                        shape = CircleShape
                    ) {
                        Icon(
                            Icons.Default.PersonRemove,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "-${statistics.leftMembersWeek}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Вийшли",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Growth rate
            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (statistics.growthRate >= 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                    contentDescription = null,
                    tint = if (statistics.growthRate >= 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Ріст за тиждень: ${if (statistics.growthRate >= 0) "+" else ""}${statistics.growthRate}%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (statistics.growthRate >= 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Топ контриб'юторів
 */
@Composable
fun TopContributorsCard(contributors: List<TopContributor>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = Color(0xFFFFD700)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Топ контриб'ютори",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (contributors.isEmpty()) {
                Text(
                    text = "Дані недоступні",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                contributors.take(5).forEachIndexed { index, contributor ->
                    ContributorItem(
                        rank = index + 1,
                        contributor = contributor
                    )
                    if (index < contributors.size - 1) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ContributorItem(
    rank: Int,
    contributor: TopContributor
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Rank badge
        Surface(
            color = when (rank) {
                1 -> Color(0xFFFFD700)
                2 -> Color(0xFFC0C0C0)
                3 -> Color(0xFFCD7F32)
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            shape = CircleShape,
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = rank.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (rank <= 3) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Avatar
        AsyncImage(
            model = contributor.avatar ?: "",
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Name
        Text(
            text = contributor.username,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )

        // Messages count
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatNumber(contributor.messagesCount),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "повідомлень",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Пікові години активності
 */
@Composable
fun PeakHoursCard(peakHours: List<Int>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Пікові години",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (peakHours.isEmpty()) {
                Text(
                    text = "Дані недоступні",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // 24-hour activity indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    (0..23).forEach { hour ->
                        val isActive = hour in peakHours
                        Box(
                            modifier = Modifier
                                .width(10.dp)
                                .height(if (isActive) 40.dp else 16.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (isActive)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "00:00",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "12:00",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "23:00",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Top peak hours
                val sortedPeaks = peakHours.take(3).sortedDescending()
                Text(
                    text = "Найактивніші години: ${sortedPeaks.joinToString(", ") { "${it}:00" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Компактна картка статистики для вбудовування
 */
@Composable
fun CompactStatisticsCard(
    statistics: GroupStatistics?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.BarChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Статистика групи",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (statistics != null) {
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MiniStat(
                        value = statistics.membersCount.toString(),
                        label = "Учасників"
                    )
                    MiniStat(
                        value = statistics.messagesToday.toString(),
                        label = "Сьогодні"
                    )
                    MiniStat(
                        value = statistics.activeMembers24h.toString(),
                        label = "Активних"
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniStat(
    value: String,
    label: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ==================== UTILITY FUNCTIONS ====================

private fun formatNumber(number: Int): String {
    return when {
        number >= 1_000_000 -> "${(number / 1_000_000.0).format(1)}M"
        number >= 1_000 -> "${(number / 1_000.0).format(1)}K"
        else -> number.toString()
    }
}

private fun Double.format(digits: Int) = "%.${digits}f".format(this)

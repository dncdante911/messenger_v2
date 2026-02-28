package com.worldmates.messenger.ui.channels.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.data.model.ChannelStatistics
import com.worldmates.messenger.data.model.TopPostStatistic
import java.text.SimpleDateFormat
import java.util.*

// ══════════════════════════════════════════════════════════════════════════════
//  FULL SCREEN
// ══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelStatisticsScreen(
    statistics: ChannelStatistics?,
    channelName: String,
    isLoading: Boolean,
    onBackClick: () -> Unit,
    onRefresh: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Статистика каналу", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (channelName.isNotEmpty()) {
                            Text(channelName, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Оновити")
                    }
                }
            )
        }
    ) { padding ->
        when {
            isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            statistics == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.BarChart, contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    Spacer(Modifier.height(16.dp))
                    Text("Статистика недоступна", style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onRefresh) { Text("Спробувати знову") }
                }
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ChannelOverviewSection(statistics)
                SubscriberDynamicsCard(statistics)
                ViewsTrendCard(statistics)
                PostActivityCard(statistics)
                EngagementCard(statistics)
                TopPostsCard(statistics.topPosts ?: emptyList())
                PeakHoursHeatmapCard(statistics)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  COMPACT PREVIEW (for admin panel tab)
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun ChannelStatisticsCompactCard(
    statistics: ChannelStatistics?,
    onOpenFullClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.BarChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Статистика", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onOpenFullClick) {
                    Text("Детально")
                    Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }

            if (statistics != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MiniStatItem(value = formatNumber(statistics.subscribersCount), label = "Підписники",
                        trend = if (statistics.growthRate != 0f) "${if (statistics.growthRate > 0) "+" else ""}${statistics.growthRate}%" else null,
                        trendPositive = statistics.growthRate >= 0)
                    MiniStatItem(value = formatNumber(statistics.viewsTotal.toInt()), label = "Перегляди")
                    MiniStatItem(value = formatNumber(statistics.avgViewsPerPost), label = "Сер./пост")
                    MiniStatItem(value = "${statistics.engagementRate}%", label = "ER")
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  OVERVIEW — 6-metric grid
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun ChannelOverviewSection(statistics: ChannelStatistics) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChStatCard(
                icon = Icons.Default.People,
                title = "Підписники",
                value = formatNumber(statistics.subscribersCount),
                trend = if (statistics.growthRate != 0f) "${if (statistics.growthRate > 0) "+" else ""}${statistics.growthRate}%" else null,
                trendPositive = statistics.growthRate >= 0,
                modifier = Modifier.weight(1f)
            )
            ChStatCard(
                icon = Icons.Default.Visibility,
                title = "Перегляди всього",
                value = formatNumber(statistics.viewsTotal.toInt()),
                subtitle = "${formatNumber(statistics.viewsLastWeek.toInt())} за тижд.",
                modifier = Modifier.weight(1f)
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChStatCard(
                icon = Icons.Default.RemoveRedEye,
                title = "Сер. переглядів",
                value = formatNumber(statistics.avgViewsPerPost),
                subtitle = "на пост",
                modifier = Modifier.weight(1f)
            )
            ChStatCard(
                icon = Icons.Default.Percent,
                title = "Залученість (ER)",
                value = "${statistics.engagementRate}%",
                subtitle = engagementLabel(statistics.engagementRate),
                trendPositive = statistics.engagementRate >= 1f,
                modifier = Modifier.weight(1f)
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChStatCard(
                icon = Icons.Default.Article,
                title = "Постів",
                value = formatNumber(statistics.postsCount),
                subtitle = "+${statistics.postsLastWeek} за тижд.",
                modifier = Modifier.weight(1f)
            )
            ChStatCard(
                icon = Icons.Default.OnlinePrediction,
                title = "Активні (24г)",
                value = formatNumber(statistics.activeSubscribers24h),
                subtitle = "підписників",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  SUBSCRIBER DYNAMICS
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun SubscriberDynamicsCard(statistics: ChannelStatistics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(icon = Icons.Default.PeopleAlt, title = "Динаміка підписників")
            Spacer(Modifier.height(16.dp))

            // KPI row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                DeltaCell(
                    icon = Icons.Default.PersonAdd,
                    value = "+${statistics.newSubscribersToday}",
                    label = "Сьогодні",
                    color = Color(0xFF4CAF50)
                )
                DeltaCell(
                    icon = Icons.Default.GroupAdd,
                    value = "+${statistics.newSubscribersWeek}",
                    label = "За тиждень",
                    color = MaterialTheme.colorScheme.primary
                )
                DeltaCell(
                    icon = Icons.Default.PersonRemove,
                    value = "-${statistics.leftSubscribersWeek}",
                    label = "Відписок",
                    color = MaterialTheme.colorScheme.error
                )
            }

            // 7-day bar chart (if data available)
            val byDay = statistics.subscribersByDay
            if (!byDay.isNullOrEmpty() && byDay.any { it > 0 }) {
                Spacer(Modifier.height(20.dp))
                Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))
                Spacer(Modifier.height(16.dp))
                Text("Нові підписники за 7 днів",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                WeekBarChart(data = byDay, barColor = MaterialTheme.colorScheme.primary)
            }

            // Growth rate footer
            Spacer(Modifier.height(14.dp))
            Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = if (statistics.growthRate >= 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                    contentDescription = null,
                    tint = if (statistics.growthRate >= 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Ріст за тиждень: ${if (statistics.growthRate >= 0) "+" else ""}${statistics.growthRate}%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (statistics.growthRate >= 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  VIEWS TREND
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun ViewsTrendCard(statistics: ChannelStatistics) {
    val viewsByDay = statistics.viewsByDay ?: List(7) { 0 }
    val primary = MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(icon = Icons.Default.ShowChart, title = "Тренд переглядів")
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("${formatNumber(statistics.viewsLastWeek.toInt())} за тиждень",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Середнє: ${formatNumber(if (statistics.postsLastWeek > 0) (statistics.viewsLastWeek / statistics.postsLastWeek).toInt() else 0)}/пост",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))

            if (viewsByDay.any { it > 0 }) {
                // Line chart
                val maxVal = viewsByDay.maxOrNull()?.toFloat()?.coerceAtLeast(1f) ?: 1f
                Box(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val step = w / (viewsByDay.size - 1).coerceAtLeast(1)
                        val pts = viewsByDay.mapIndexed { i, v ->
                            Offset(i * step, h - (v / maxVal) * h * 0.9f)
                        }
                        // Fill gradient under line
                        val fillPath = Path().apply {
                            moveTo(pts.first().x, h)
                            pts.forEach { lineTo(it.x, it.y) }
                            lineTo(pts.last().x, h)
                            close()
                        }
                        drawPath(fillPath, brush = Brush.verticalGradient(
                            colors = listOf(primary.copy(alpha = 0.3f), Color.Transparent),
                            startY = 0f, endY = h
                        ))
                        // Line
                        val linePath = Path().apply {
                            moveTo(pts.first().x, pts.first().y)
                            pts.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(linePath, color = primary, style = Stroke(width = 3f, cap = StrokeCap.Round))
                        // Dots
                        pts.forEach { drawCircle(color = primary, radius = 5f, center = it) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                // Day labels
                val days = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Нд")
                val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
                val labels = (0..6).map { i ->
                    val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, i - 6) }
                    days[(cal.get(Calendar.DAY_OF_WEEK) + 5) % 7]
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    labels.forEach { label ->
                        Text(label, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    Text("Немає даних за тиждень", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  POST ACTIVITY
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun PostActivityCard(statistics: ChannelStatistics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(icon = Icons.Default.Article, title = "Активність публікацій")
            Spacer(Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                PostPeriodItem(value = statistics.postsToday.toString(), label = "Сьогодні")
                PostPeriodItem(value = statistics.postsLastWeek.toString(), label = "За тиждень")
                PostPeriodItem(value = statistics.postsThisMonth.toString(), label = "За місяць")
                PostPeriodItem(value = statistics.postsCount.toString(), label = "Всього")
            }

            Spacer(Modifier.height(16.dp))
            Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))
            Spacer(Modifier.height(12.dp))

            // Media vs text breakdown
            val total = statistics.postsCount.coerceAtLeast(1)
            val mediaFraction = statistics.mediaPostsCount.toFloat() / total
            Text("Тип контенту", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            // Stacked bar
            val primary = MaterialTheme.colorScheme.primary
            val secondary = MaterialTheme.colorScheme.secondary
            Box(modifier = Modifier.fillMaxWidth().height(20.dp).clip(RoundedCornerShape(10.dp))) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    drawRoundRect(color = secondary.copy(alpha = 0.3f), size = size, cornerRadius = CornerRadius(10f))
                    drawRoundRect(color = primary, size = Size(w * mediaFraction, h), cornerRadius = CornerRadius(10f))
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(primary))
                    Spacer(Modifier.width(4.dp))
                    Text("Медіа: ${statistics.mediaPostsCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(secondary.copy(alpha = 0.5f)))
                    Spacer(Modifier.width(4.dp))
                    Text("Текст: ${statistics.textPostsCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  ENGAGEMENT
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun EngagementCard(statistics: ChannelStatistics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(icon = Icons.Default.Favorite, title = "Залученість")
            Spacer(Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Reactions card
                Surface(
                    modifier = Modifier.weight(1f),
                    color = Color(0xFFF44336).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.FavoriteBorder, contentDescription = null,
                            tint = Color(0xFFF44336), modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(formatNumber(statistics.reactionsTotal),
                            fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color(0xFFF44336))
                        Text("Реакції", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // Comments card
                Surface(
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF2196F3).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ChatBubbleOutline, contentDescription = null,
                            tint = Color(0xFF2196F3), modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(formatNumber(statistics.commentsTotal),
                            fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color(0xFF2196F3))
                        Text("Коментарі", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // ER card
                val erColor = when {
                    statistics.engagementRate >= 5f  -> Color(0xFF4CAF50)
                    statistics.engagementRate >= 1f  -> Color(0xFFFF9800)
                    else                              -> MaterialTheme.colorScheme.error
                }
                Surface(
                    modifier = Modifier.weight(1f),
                    color = erColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Percent, contentDescription = null,
                            tint = erColor, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(6.dp))
                        Text("${statistics.engagementRate}%",
                            fontWeight = FontWeight.Bold, fontSize = 22.sp, color = erColor)
                        Text("ER", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            // ER scale explanation
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when {
                            statistics.engagementRate >= 5f  -> "Відмінна залученість! Канал активно взаємодіє з аудиторією."
                            statistics.engagementRate >= 1f  -> "Середня залученість. Нормально для великих каналів."
                            statistics.engagementRate > 0f   -> "Низька залученість. Спробуйте інтерактивний контент."
                            else                              -> "Даних про взаємодію поки немає."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  TOP POSTS (Telegram-style leaderboard)
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun TopPostsCard(topPosts: List<TopPostStatistic>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(icon = Icons.Default.EmojiEvents, title = "Топ публікації")
            Spacer(Modifier.height(4.dp))
            Text("За кількістю переглядів", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))

            if (topPosts.isEmpty()) {
                Text("Немає даних", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            } else {
                topPosts.take(10).forEachIndexed { index, post ->
                    TopPostItem(rank = index + 1, post = post)
                    if (index < topPosts.size - 1) {
                        Divider(modifier = Modifier.padding(vertical = 10.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun TopPostItem(rank: Int, post: TopPostStatistic) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        // Rank medal
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = when (rank) {
                    1    -> Color(0xFFFFD700)
                    2    -> Color(0xFFC0C0C0)
                    3    -> Color(0xFFCD7F32)
                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                },
                shape = CircleShape,
                modifier = Modifier.size(26.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(rank.toString(), style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (rank <= 3) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Post text preview
            val preview = post.text.trim().ifEmpty { if (post.hasMedia) "[Медіа-пост]" else "[Пост без тексту]" }
            Text(preview, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))

            // Metrics row
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                // Views
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(Icons.Default.Visibility, contentDescription = null,
                        modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(formatNumber(post.views), style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                }
                // Reactions
                if (post.reactions > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(Icons.Default.FavoriteBorder, contentDescription = null,
                            modifier = Modifier.size(13.dp), tint = Color(0xFFF44336))
                        Text(formatNumber(post.reactions), style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFF44336))
                    }
                }
                // Comments
                if (post.comments > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(Icons.Default.ChatBubbleOutline, contentDescription = null,
                            modifier = Modifier.size(13.dp), tint = Color(0xFF2196F3))
                        Text(formatNumber(post.comments), style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2196F3))
                    }
                }
                // Media badge
                if (post.hasMedia) {
                    Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp)) {
                        Text("📷", style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                    }
                }
                // Date
                if (post.publishedTime > 0) {
                    Text(formatDate(post.publishedTime), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  PEAK HOURS HEATMAP
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun PeakHoursHeatmapCard(statistics: ChannelStatistics) {
    val hourlyViews = statistics.hourlyViews ?: List(24) { 0 }
    val peakHours   = statistics.peakHours   ?: emptyList()
    val primary     = MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(icon = Icons.Default.Schedule, title = "Пікові години публікацій")
            Spacer(Modifier.height(4.dp))
            Text("Найкращий час для публікацій", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))

            if (hourlyViews.any { it > 0 }) {
                val maxVal = hourlyViews.maxOrNull()?.toFloat()?.coerceAtLeast(1f) ?: 1f
                // Heatmap bars
                Row(
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    hourlyViews.forEachIndexed { hour, count ->
                        val fraction = count / maxVal
                        val isPeak = hour in peakHours
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .height((60.dp * fraction).coerceAtLeast(4.dp))
                                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                    .background(
                                        if (isPeak) primary
                                        else primary.copy(alpha = 0.25f + 0.5f * fraction)
                                    )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                // Hour labels — only show every 4 hours
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    (0..23).forEach { h ->
                        Text(
                            text = if (h % 4 == 0) "$h" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontSize = 9.sp
                        )
                    }
                }

                if (peakHours.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))
                    Spacer(Modifier.height(10.dp))
                    // Peak hours chips
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bolt, contentDescription = null,
                            tint = Color(0xFFFFD700), modifier = Modifier.size(18.dp))
                        Text("Найкращий час: ", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        peakHours.take(5).forEach { h ->
                            Surface(
                                color = primary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("${h}:00", style = MaterialTheme.typography.labelMedium,
                                    color = primary, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    Text("Немає даних про активність", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  SHARED SUB-COMPOSABLES
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ChStatCard(
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
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (trend != null) {
                    Surface(
                        color = if (trendPositive) Color(0xFF4CAF50).copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(trend, style = MaterialTheme.typography.labelSmall,
                            color = if (trendPositive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                    }
                }
            }
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DeltaCell(icon: ImageVector, value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(color = color.copy(alpha = 0.15f), shape = CircleShape) {
            Icon(icon, contentDescription = null, tint = color,
                modifier = Modifier.padding(10.dp).size(24.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MiniStatItem(value: String, label: String, trend: String? = null, trendPositive: Boolean = true) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        if (trend != null) {
            Text(trend, style = MaterialTheme.typography.labelSmall,
                color = if (trendPositive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PostPeriodItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WeekBarChart(data: List<Int>, barColor: Color) {
    val maxVal = data.maxOrNull()?.toFloat()?.coerceAtLeast(1f) ?: 1f
    val days = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Нд")
    val today = Calendar.getInstance()
    val labels = (0..6).map { i ->
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, i - 6) }
        days[(cal.get(Calendar.DAY_OF_WEEK) + 5) % 7]
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(80.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        data.forEachIndexed { idx, v ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.weight(1f)
            ) {
                if (v > 0) Text(v.toString(), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height((56.dp * (v / maxVal)).coerceAtLeast(4.dp))
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(barColor)
                )
                Spacer(Modifier.height(3.dp))
                Text(labels.getOrElse(idx) { "" }, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  UTILITY
// ══════════════════════════════════════════════════════════════════════════════

private fun formatNumber(n: Int): String = when {
    n >= 1_000_000 -> "${"%.1f".format(n / 1_000_000.0)}M"
    n >= 1_000     -> "${"%.1f".format(n / 1_000.0)}K"
    else           -> n.toString()
}

private fun formatDate(unixSeconds: Long): String =
    SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(Date(unixSeconds * 1000))

private fun engagementLabel(er: Float) = when {
    er >= 10f -> "Вірусний 🔥"
    er >= 5f  -> "Відмінний"
    er >= 1f  -> "Середній"
    er > 0f   -> "Низький"
    else      -> "Немає даних"
}

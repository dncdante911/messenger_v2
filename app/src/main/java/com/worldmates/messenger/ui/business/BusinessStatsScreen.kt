package com.worldmates.messenger.ui.business

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.R
import com.worldmates.messenger.data.model.BusinessStatDay

private val BizDeep   = Color(0xFF0D1B2A)
private val BizAccent = Color(0xFF1E90FF)
private val BizGold   = Color(0xFFFFD166)
private val BizCard   = Color(0xFF233044)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessStatsScreen(
    state: BusinessUiState,
    onBack: () -> Unit,
    onLoadStats: (Int) -> Unit,
) {
    var selectedDays by remember { mutableIntStateOf(30) }

    LaunchedEffect(selectedDays) { onLoadStats(selectedDays) }

    Box(modifier = Modifier.fillMaxSize().background(BizDeep)) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.biz_stats_title), color = Color.White, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A2942)),
                )
            },
        ) { padding ->
            LazyColumn(
                modifier            = Modifier.fillMaxSize().padding(padding),
                contentPadding      = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Period selector
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(7, 30, 90).forEach { days ->
                            FilterChip(
                                selected = selectedDays == days,
                                onClick  = { selectedDays = days },
                                label    = { Text(stringResource(R.string.biz_stats_days, days)) },
                                colors   = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BizAccent,
                                    selectedLabelColor     = Color.White,
                                    labelColor             = Color.White.copy(0.7f),
                                ),
                            )
                        }
                    }
                }

                if (state.isStatsLoading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = BizAccent)
                        }
                    }
                } else {
                    val totals = state.stats?.totals
                    item {
                        // Summary cards
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            StatCard(
                                modifier = Modifier.weight(1f),
                                icon     = Icons.Default.Visibility,
                                value    = totals?.profileViews ?: 0,
                                label    = stringResource(R.string.biz_stats_profile_views),
                                color    = BizAccent,
                            )
                            StatCard(
                                modifier = Modifier.weight(1f),
                                icon     = Icons.Default.Message,
                                value    = totals?.messagesReceived ?: 0,
                                label    = stringResource(R.string.biz_stats_messages),
                                color    = Color(0xFF2ECC71),
                            )
                            StatCard(
                                modifier = Modifier.weight(1f),
                                icon     = Icons.Default.Link,
                                value    = totals?.linkClicks ?: 0,
                                label    = stringResource(R.string.biz_stats_link_clicks),
                                color    = BizGold,
                            )
                        }
                    }

                    val daily = state.stats?.daily ?: emptyList()
                    if (daily.isNotEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.biz_stats_by_day),
                                color      = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize   = 15.sp,
                            )
                        }
                        items(daily.size) { i ->
                            DayRow(daily[i])
                        }
                    } else {
                        item {
                            Box(
                                Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    stringResource(R.string.biz_stats_empty),
                                    color = Color.White.copy(0.5f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier,
    icon:     ImageVector,
    value:    Int,
    label:    String,
    color:    Color,
) {
    Card(
        modifier = modifier,
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = BizCard),
    ) {
        Column(
            modifier              = Modifier.padding(12.dp),
            horizontalAlignment   = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(4.dp))
            Text(text = "$value", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = Color.White)
            Text(text = label, fontSize = 10.sp, color = Color.White.copy(0.6f))
        }
    }
}

@Composable
private fun DayRow(day: BusinessStatDay) {
    Card(
        shape  = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = BizCard.copy(alpha = 0.7f)),
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 14.dp, vertical = 10.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text     = day.date,
                color    = Color.White.copy(0.8f),
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            MiniStat("👁 ${day.profileViews}")
            Spacer(Modifier.width(10.dp))
            MiniStat("💬 ${day.messagesReceived}")
            Spacer(Modifier.width(10.dp))
            MiniStat("🔗 ${day.linkClicks}")
        }
    }
}

@Composable
private fun MiniStat(text: String) {
    Text(text = text, color = Color.White.copy(0.7f), fontSize = 12.sp)
}

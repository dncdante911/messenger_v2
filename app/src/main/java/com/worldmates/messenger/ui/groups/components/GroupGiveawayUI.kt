package com.worldmates.messenger.ui.groups.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.R
import com.worldmates.messenger.network.GroupGiveawayResponse
import com.worldmates.messenger.network.GroupGiveawayWinner

/**
 * Giveaway section embedded in the Statistics tab of the Group Admin Panel.
 *
 * Parameters:
 *   winners_count  – 1..20
 *   min_messages   – 0 = no filter
 *   period_days    – 7 / 30 / 90 days
 */
@Composable
fun GroupGiveawaySection(
    result: GroupGiveawayResponse?,
    isRunning: Boolean,
    onRunGiveaway: (winnersCount: Int, minMessages: Int, periodDays: Int) -> Unit
) {
    var winnersCount by remember { mutableIntStateOf(1) }
    var minMessages  by remember { mutableIntStateOf(0) }
    var selectedPeriod by remember { mutableIntStateOf(30) }
    val periods = listOf(7, 30, 90)

    Card(
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CardGiftcard,
                    contentDescription = null,
                    tint = Color(0xFFFF6B35),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = stringResource(R.string.group_giveaway_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.group_giveaway_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            // Period chips
            Text(
                text = stringResource(R.string.giveaway_period_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                periods.forEach { days ->
                    val label = when (days) {
                        7    -> stringResource(R.string.period_7d)
                        30   -> stringResource(R.string.period_30d)
                        else -> stringResource(R.string.period_90d)
                    }
                    FilterChip(
                        selected = selectedPeriod == days,
                        onClick  = { selectedPeriod = days },
                        label    = { Text(label, fontSize = 12.sp) }
                    )
                }
            }

            // Winners count stepper
            GiveawayStepperRow(
                label   = stringResource(R.string.giveaway_winners_count, winnersCount),
                value   = winnersCount,
                onMinus = { if (winnersCount > 1)  winnersCount-- },
                onPlus  = { if (winnersCount < 20) winnersCount++ }
            )

            // Min messages stepper
            Column {
                GiveawayStepperRow(
                    label   = stringResource(R.string.group_giveaway_min_messages, minMessages),
                    value   = minMessages,
                    onMinus = { if (minMessages > 0) minMessages-- },
                    onPlus  = { minMessages++ }
                )
                Text(
                    text = stringResource(R.string.giveaway_min_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Run button
            Button(
                onClick  = { onRunGiveaway(winnersCount, minMessages, selectedPeriod) },
                enabled  = !isRunning,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B35))
            ) {
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(18.dp),
                        color       = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.giveaway_running))
                } else {
                    Text("🎲 ${stringResource(R.string.giveaway_run_btn)}", fontWeight = FontWeight.Bold)
                }
            }

            // Results
            if (result != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Text(
                    text = stringResource(R.string.giveaway_results_title, result.totalParticipants),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                if (result.winners.isNullOrEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.giveaway_no_eligible),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    result.winners.forEach { winner -> GroupGiveawayWinnerRow(winner) }
                }
            }
        }
    }
}

// ─── Reusable stepper row ─────────────────────────────────────────────────────

@Composable
private fun GiveawayStepperRow(
    label: String,
    value: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilledIconButton(
                onClick = onMinus,
                modifier = Modifier.size(30.dp),
                colors   = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            Text(
                text = value.toString(),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.widthIn(min = 28.dp),
                textAlign = TextAlign.Center
            )
            FilledIconButton(
                onClick = onPlus,
                modifier = Modifier.size(30.dp),
                colors   = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ─── Winner row ───────────────────────────────────────────────────────────────

@Composable
private fun GroupGiveawayWinnerRow(winner: GroupGiveawayWinner) {
    Surface(
        color = when (winner.place) {
            1    -> Color(0xFFFFD700).copy(alpha = 0.12f)
            2    -> Color(0xFFC0C0C0).copy(alpha = 0.12f)
            3    -> Color(0xFFCD7F32).copy(alpha = 0.12f)
            else -> MaterialTheme.colorScheme.surface
        },
        shape    = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = when (winner.place) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "#${winner.place}" },
                fontSize = 20.sp
            )
            if (!winner.avatarUrl.isNullOrBlank()) {
                coil.compose.AsyncImage(
                    model              = winner.avatarUrl,
                    contentDescription = null,
                    modifier           = Modifier.size(36.dp).clip(CircleShape),
                    contentScale       = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (winner.name?.firstOrNull() ?: winner.username?.firstOrNull() ?: 'U')
                            .uppercaseChar().toString(),
                        fontWeight = FontWeight.Bold,
                        fontSize   = 14.sp,
                        color      = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Column {
                Text(
                    text = winner.name ?: winner.username ?: "User #${winner.userId}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                winner.username?.let { un ->
                    Text(
                        text  = "@$un",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

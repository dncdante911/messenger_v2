package com.worldmates.messenger.ui.channels.premium.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.worldmates.messenger.ui.channels.premium.design.PremiumBrushes
import com.worldmates.messenger.ui.channels.premium.design.PremiumDesign
import com.worldmates.messenger.ui.channels.premium.design.current

/**
 * Gold-accented card that invites a channel owner to start the free
 * trial. Sits above the plan list when the channel has never had a
 * premium subscription before.
 *
 * [trialDays] is injected from the server so legal / country-specific
 * trial lengths can change without a client release.
 */
@Composable
fun PremiumTrialBanner(
    trialDays: Int,
    onStartTrial: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    headline: String = "Try premium free for $trialDays days",
    subtitle: String = "All features unlocked. No card required. Cancel anytime.",
    ctaText: String = "Start $trialDays-day trial",
) {
    val design = PremiumDesign.current
    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(design.colors.backgroundElevated, shape)
            .border(width = 0.8.dp, brush = PremiumBrushes.goldHairline(), shape = shape)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(PremiumBrushes.matteGoldLinear()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Bolt,
                    contentDescription = null,
                    tint = design.colors.onAccent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = headline,
                    style = design.typography.bodyStrong.copy(color = design.colors.onPrimary),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = design.typography.caption.copy(color = design.colors.onSecondary),
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        PremiumPrimaryButton(
            text = ctaText,
            onClick = onStartTrial,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
        )
    }
}

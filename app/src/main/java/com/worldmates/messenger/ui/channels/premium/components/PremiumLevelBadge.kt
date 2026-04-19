package com.worldmates.messenger.ui.channels.premium.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.worldmates.messenger.ui.channels.premium.design.PremiumBrushes
import com.worldmates.messenger.ui.channels.premium.design.PremiumDesign
import com.worldmates.messenger.ui.channels.premium.design.PremiumMotion
import com.worldmates.messenger.ui.channels.premium.design.current

/**
 * Five tier bands a channel moves through as it levels up. Each tier
 * covers a range of levels and ships a short display label used on the
 * pill.
 */
enum class PremiumChannelTier(val minLevel: Int, val displayName: String) {
    Spark(1, "Spark"),
    Rising(5, "Rising"),
    Established(10, "Established"),
    Elite(20, "Elite"),
    Legend(40, "Legend");

    companion object {
        fun forLevel(level: Int): PremiumChannelTier =
            values().lastOrNull { level >= it.minLevel } ?: Spark
    }
}

/**
 * Horizontal gold pill that communicates the channel's current level and
 * tier at a glance. Unlike [PremiumLevelIndicator] (circular arc), this
 * is a badge meant to sit next to the channel name or inside a status
 * row.
 *
 * When [animated] is true a subtle shine travels across the pill on the
 * same cadence as [PremiumBadge].
 */
@Composable
fun PremiumLevelBadge(
    level: Int,
    modifier: Modifier = Modifier,
    tier: PremiumChannelTier = PremiumChannelTier.forLevel(level),
    animated: Boolean = true,
    compact: Boolean = false,
) {
    val design = PremiumDesign.current
    val shape = RoundedCornerShape(if (compact) 8.dp else 10.dp)
    val hPad: Dp = if (compact) 8.dp else 10.dp
    val vPad: Dp = if (compact) 4.dp else 6.dp

    val shineX = if (animated) {
        val transition = rememberInfiniteTransition(label = "levelBadgeShine")
        val v by transition.animateFloat(
            initialValue = -1f,
            targetValue = 2f,
            animationSpec = infiniteRepeatable(
                animation = tween(PremiumMotion.shimmerDurationMs, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "levelBadgeShineX",
        )
        v
    } else -1f

    Row(
        modifier = modifier
            .clip(shape)
            .background(PremiumBrushes.matteGoldLinear(), shape)
            .border(width = 0.5.dp, color = design.colors.accentDeep, shape = shape)
            .drawWithContent {
                drawContent()
                if (animated) {
                    val w = size.width
                    val x = w * shineX
                    val shine = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.25f),
                            Color.Transparent,
                        ),
                        start = Offset(x - w * 0.25f, 0f),
                        end = Offset(x + w * 0.25f, size.height),
                    )
                    drawRect(brush = shine, blendMode = BlendMode.Plus)
                }
            }
            .padding(horizontal = hPad, vertical = vPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "LV.$level",
            style = design.typography.metric.copy(color = design.colors.onAccent),
        )
        Box(
            Modifier
                .width(1.dp)
                .height(if (compact) 10.dp else 12.dp)
                .background(design.colors.onAccent.copy(alpha = 0.35f)),
        )
        Text(
            text = tier.displayName.uppercase(),
            style = design.typography.overline.copy(color = design.colors.onAccent),
        )
    }
}

/**
 * Compact tier-only variant — used on list cards where the numeric level
 * would crowd the row. Still shows a one-letter glyph so readers can
 * rank tiers at a glance.
 */
@Composable
fun PremiumTierChip(
    tier: PremiumChannelTier,
    modifier: Modifier = Modifier,
) {
    val design = PremiumDesign.current
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(design.colors.glassFill, shape)
            .border(width = 0.5.dp, color = design.colors.glassStroke, shape = shape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = tier.displayName.uppercase(),
            style = design.typography.overline.copy(color = design.colors.accent),
        )
    }
}

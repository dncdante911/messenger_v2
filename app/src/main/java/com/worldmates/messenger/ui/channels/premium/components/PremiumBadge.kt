package com.worldmates.messenger.ui.channels.premium.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.worldmates.messenger.ui.channels.premium.design.PremiumDesign
import com.worldmates.messenger.ui.channels.premium.design.PremiumMotion
import com.worldmates.messenger.ui.channels.premium.design.PremiumTokens
import com.worldmates.messenger.ui.channels.premium.design.current
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Semantic sizes for the premium badge. */
enum class PremiumBadgeSize(internal val dp: Dp) {
    Micro(14.dp),
    Small(20.dp),
    Medium(28.dp),
    Hero(56.dp),
}

/**
 * Animated five-point gold star. Used next to channel names, on list
 * cards (Small) and as the hero mark on the purchase screen (Hero).
 *
 * The star is filled with a rotating gold gradient, traced with a
 * darker gold stroke, and — when `animated = true` — swept with a
 * highlight that travels around its bounding box.
 */
@Composable
fun PremiumBadge(
    modifier: Modifier = Modifier,
    size: PremiumBadgeSize = PremiumBadgeSize.Small,
    animated: Boolean = true,
) {
    val colors = PremiumDesign.current.colors

    val sweep = if (animated) {
        val transition = rememberInfiniteTransition(label = "badgeSweep")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(PremiumMotion.shimmerDurationMs, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "badgeSweepAngle",
        ).value
    } else 0f

    Canvas(modifier.size(size.dp)) {
        val s = min(this.size.width, this.size.height)
        val center = Offset(s / 2f, s / 2f)
        val outerR = s * 0.48f
        val innerR = s * 0.21f
        val path = starPath(center, outerR, innerR, points = 5)

        // Gold fill with a directional gradient.
        val angleRad = sweep * PI.toFloat() / 180f
        val fillBrush = Brush.linearGradient(
            colors = listOf(
                colors.accentSoft,
                colors.accent,
                colors.accentDeep,
            ),
            start = Offset(center.x + cos(angleRad) * outerR, center.y + sin(angleRad) * outerR),
            end = Offset(center.x - cos(angleRad) * outerR, center.y - sin(angleRad) * outerR),
        )
        drawPath(path, fillBrush)

        // Darker hairline edge.
        drawPath(
            path,
            color = PremiumTokens.GoldInk,
            style = Stroke(width = s * 0.03f),
        )

        // Central highlight — subtle white-gold dot for "engraved" feel.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
                center = Offset(center.x - outerR * 0.12f, center.y - outerR * 0.15f),
                radius = innerR,
            ),
            radius = innerR,
            center = Offset(center.x - outerR * 0.12f, center.y - outerR * 0.15f),
        )
    }
}

/** Star polygon path centred at [center] with [outerR] / [innerR] radii. */
private fun starPath(
    center: Offset,
    outerR: Float,
    innerR: Float,
    points: Int,
): Path {
    val path = Path()
    val step = PI / points
    val rotationOffset = -PI / 2f
    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) outerR else innerR
        val a = rotationOffset + step * i
        val x = center.x + cos(a).toFloat() * r
        val y = center.y + sin(a).toFloat() * r
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/** Verified checkmark — gold circle + hairline check, matches Telegram scale. */
@Composable
fun PremiumVerifiedMark(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
) {
    val colors = PremiumDesign.current.colors
    Canvas(modifier.size(size)) {
        val s = min(this.size.width, this.size.height)
        val r = s / 2f
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(colors.accentSoft, colors.accent, colors.accentDeep),
                start = Offset(0f, 0f),
                end = Offset(s, s),
            ),
            radius = r,
            center = Offset(r, r),
        )
        val tick = Path().apply {
            moveTo(s * 0.28f, s * 0.52f)
            lineTo(s * 0.46f, s * 0.70f)
            lineTo(s * 0.74f, s * 0.34f)
        }
        drawPath(
            tick,
            color = PremiumTokens.ObsidianBlack,
            style = Stroke(width = s * 0.12f),
        )
    }
}

/** Tiny lock glyph for private premium channels. */
@Composable
fun PremiumLockMark(
    modifier: Modifier = Modifier,
    size: Dp = 14.dp,
    tint: Color = PremiumDesign.current.colors.accent,
) {
    Canvas(modifier.size(size)) {
        val s = min(this.size.width, this.size.height)
        drawRoundRect(
            color = tint,
            topLeft = Offset(s * 0.2f, s * 0.45f),
            size = Size(s * 0.6f, s * 0.45f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.1f, s * 0.1f),
        )
        drawArc(
            color = tint,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(s * 0.28f, s * 0.20f),
            size = Size(s * 0.44f, s * 0.44f),
            style = Stroke(width = s * 0.10f),
        )
    }
}

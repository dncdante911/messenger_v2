package com.worldmates.messenger.ui.channels.premium.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.ui.channels.premium.design.PremiumBrushes
import com.worldmates.messenger.ui.channels.premium.design.PremiumDesign
import com.worldmates.messenger.ui.channels.premium.design.current

/**
 * Circular level indicator. The track is a thin obsidian ring; the
 * progress arc is a gold sweep. The level number sits centred in
 * Exo 2 Bold — Roman numerals for 1-5, Arabic for 6+.
 *
 * Animates the arc when [progress] changes.
 */
@Composable
fun PremiumLevelIndicator(
    level: Int,
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    showNumber: Boolean = true,
) {
    val design = PremiumDesign.current
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "levelProgress",
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val strokeW = this.size.minDimension * 0.12f
            val inset = strokeW / 2f
            val arcSize = Size(
                this.size.width - inset * 2f,
                this.size.height - inset * 2f,
            )
            val topLeft = Offset(inset, inset)

            // Track.
            drawArc(
                color = design.colors.outline,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeW),
            )
            // Progress.
            drawArc(
                brush = PremiumBrushes.matteGoldLinear(),
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeW),
            )
        }

        if (showNumber) {
            Text(
                text = level.toDisplayLevel(),
                color = design.colors.accent,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.36f).sp,
                style = design.typography.title.copy(
                    color = design.colors.accent,
                ),
            )
        }
    }
}

/** Micro variant (24 dp) for list cards. Stripped of the number for density. */
@Composable
fun PremiumLevelIndicatorMicro(
    level: Int,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    PremiumLevelIndicator(
        level = level,
        progress = progress,
        modifier = modifier,
        size = 24.dp,
        showNumber = false,
    )
}

private fun Int.toDisplayLevel(): String = when (this) {
    0 -> "–"
    1 -> "I"
    2 -> "II"
    3 -> "III"
    4 -> "IV"
    5 -> "V"
    else -> this.toString()
}

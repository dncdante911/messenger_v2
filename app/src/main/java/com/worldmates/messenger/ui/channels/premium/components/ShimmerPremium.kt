package com.worldmates.messenger.ui.channels.premium.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.worldmates.messenger.ui.channels.premium.design.PremiumDesign
import com.worldmates.messenger.ui.channels.premium.design.PremiumMotion
import com.worldmates.messenger.ui.channels.premium.design.current

/**
 * Gold shimmer sweep — traveling gradient band used on buttons, badges
 * and plan-card "popular" markers. The sweep animates from left to
 * right and wraps every PremiumMotion.shimmerDurationMs.
 */
@Composable
fun rememberGoldShimmerBrush(
    baseColor: Color = PremiumDesign.current.colors.accent,
    highlight: Color = PremiumDesign.current.colors.accentSoft,
    widthPx: Float = 800f,
): Brush {
    val transition = rememberInfiniteTransition(label = "goldShimmer")
    val shift by transition.animateFloat(
        initialValue = -widthPx,
        targetValue = widthPx,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = PremiumMotion.shimmerDurationMs,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "goldShimmerShift",
    )
    return remember(shift, baseColor, highlight) {
        Brush.linearGradient(
            colors = listOf(baseColor, highlight, baseColor),
            start = Offset(shift - widthPx / 2f, 0f),
            end = Offset(shift + widthPx / 2f, 0f),
        )
    }
}

/** Single-line skeleton placeholder in the premium palette. */
@Composable
fun PremiumShimmerLine(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 6.dp,
) {
    val brush = rememberGoldShimmerBrush(
        baseColor = PremiumDesign.current.colors.surface,
        highlight = PremiumDesign.current.colors.surfaceHigh,
    )
    Box(
        modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(PremiumDesign.current.colors.surface)
    ) {
        Box(Modifier.fillMaxSize().background(brush))
    }
}

package com.worldmates.messenger.ui.channels.premium.design

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Animation specs shared across premium components. Spring-first so the
 * UI feels physical, tween reserved for shimmer/fade timings.
 */
object PremiumMotion {

    // ── Springs ──────────────────────────────────────────────────────────────
    val springMedium = spring<Float>(
        dampingRatio = 0.72f,
        stiffness = Spring.StiffnessMediumLow,
    )

    val springSnappy = spring<Float>(
        dampingRatio = 0.6f,
        stiffness = Spring.StiffnessMedium,
    )

    val springBouncy = spring<Float>(
        dampingRatio = 0.45f,
        stiffness = Spring.StiffnessMediumLow,
    )

    // ── Fade / enter ─────────────────────────────────────────────────────────
    val fadeInOutMs = 220
    val fadeInOut = tween<Float>(durationMillis = fadeInOutMs)

    // ── Shimmer / shine loops ────────────────────────────────────────────────
    /** Gold shimmer sweep across a badge / button edge. */
    val shimmerDurationMs = 2400
    /** Emoji-status gentle pulse. */
    val pulseDurationMs = 3000
    /** Aurora glow that fades in on press and out on release. */
    val auroraPulseDurationMs = 640
}

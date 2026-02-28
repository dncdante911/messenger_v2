package com.worldmates.messenger.ui.messages

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

// ─── Dust / ash particle ─────────────────────────────────────────────────────

private data class DustParticle(
    val startX: Float,   // 0..1 normalised
    val startY: Float,   // 0..1 normalised
    val vx: Float,       // horizontal velocity coefficient (rightward)
    val vy: Float,       // vertical velocity coefficient (upward, negative = up)
    val size: Float,     // dp
    val color: Color,
    val delay: Float     // 0..0.45 – stagger per particle
)

// Colour palette: grey ash + occasional warm embers
private val dustColors = listOf(
    Color(0xFFBDBDBD),  // light grey
    Color(0xFF9E9E9E),  // grey
    Color(0xFF757575),  // dark grey
    Color(0xFFB0BEC5),  // blue-grey
    Color(0xFFCFD8DC),  // lightest blue-grey
    Color(0xFFD4A853),  // golden ember
    Color(0xFFA1887F),  // warm brown
    Color(0xFFFFCC80),  // orange ember spark
)

private fun buildParticles(seed: Long): List<DustParticle> {
    val rng = java.util.Random(seed)
    return List(130) {
        // Stagger by X position so the disintegration wave moves left→right
        val sx = rng.nextFloat()
        DustParticle(
            startX = sx,
            startY = rng.nextFloat(),
            // Most particles drift right; a few scatter sideways/up
            vx = 0.25f + rng.nextFloat() * 0.75f,
            vy = -(rng.nextFloat() * 0.55f),   // 0 .. -0.55 (upward)
            size = 1.8f + rng.nextFloat() * 3.5f,
            color = dustColors[rng.nextInt(dustColors.size)],
            // Stagger: left-side particles start earlier → wave moves left→right
            delay = sx * 0.35f + rng.nextFloat() * 0.1f
        )
    }
}

// ─── Composable ──────────────────────────────────────────────────────────────

/**
 * 🌪️ Thanos Snap Disintegration Effect
 *
 * Wraps [content] with a particle-based disintegration animation.
 *
 * When [isDisintegrating] becomes `true` the content fades out while
 * 130 ash/ember particles spread rightward and upward, exactly like the
 * MCU Infinity Gauntlet snap. The host ViewModel is responsible for
 * removing the item from the list after ~750 ms.
 *
 * @param isDisintegrating Whether the animation should play.
 * @param seed  Unique seed per message so every bubble has distinct particles.
 */
@Composable
fun ThanosDisintegrationEffect(
    isDisintegrating: Boolean,
    seed: Long = 0L,
    content: @Composable () -> Unit
) {
    // Build particles once per message (stable across recompositions)
    val particles = remember(seed) { buildParticles(seed) }

    // 0 → 1 over 750 ms; drives both content fade and particle movement
    val progress by animateFloatAsState(
        targetValue = if (isDisintegrating) 1f else 0f,
        animationSpec = tween(durationMillis = 750),
        label = "thanos_progress"
    )

    Box {
        // ── Original content — fades out in the first ~40 % of the animation
        Box(
            modifier = Modifier.graphicsLayer {
                alpha = if (!isDisintegrating && progress == 0f) 1f
                        else (1f - progress * 2.4f).coerceIn(0f, 1f)
                // Subtle shrink as it disintegrates
                val scale = 1f - progress * 0.06f
                scaleX = scale
                scaleY = scale
            }
        ) {
            content()
        }

        // ── Particle canvas (only rendered while animating)
        if (progress > 0f) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height

                particles.forEach { p ->
                    // Each particle has its own staggered start
                    val pProgress = ((progress - p.delay) / (1f - p.delay))
                        .coerceIn(0f, 1f)
                    if (pProgress <= 0f) return@forEach

                    val cx = p.startX * w + p.vx * pProgress * w * 0.7f
                    val cy = p.startY * h + p.vy * pProgress * h * 0.7f
                    val alpha = (1f - pProgress * 1.25f).coerceIn(0f, 1f)
                    val radius = (p.size * (1f - pProgress * 0.55f))
                        .coerceAtLeast(0.3f).dp.toPx()

                    if (alpha > 0f) {
                        drawCircle(
                            color = p.color.copy(alpha = alpha),
                            radius = radius,
                            center = Offset(cx, cy),
                            blendMode = BlendMode.SrcOver
                        )
                    }
                }
            }
        }
    }
}

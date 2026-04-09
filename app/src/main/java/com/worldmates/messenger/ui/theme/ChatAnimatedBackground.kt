package com.worldmates.messenger.ui.theme

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Variants for the animated chat background.
 */
enum class AnimatedBgVariant {
    /** No animation (disabled) */
    NONE,

    /** Flowing aurora borealis northern lights (green/purple/teal gradients shifting) */
    AURORA,

    /** Deep ocean waves (blue/teal, wave-like oscillation) */
    OCEAN_WAVES,

    /** Cosmic nebula (deep purple/blue/pink, slow swirling) */
    COSMIC,

    /** Warm sunset gradient flow (orange/pink/red flowing right to left) */
    SUNSET_FLOW,

    /** Neon city pulse (dark background with neon blue/pink animated lines/particles) */
    NEON_PULSE,

    /** Forest morning mist (green/teal/white particles drifting up) */
    FOREST_MIST,

    /** Fire with floating ember particles (dark red/orange, particles floating up) */
    FIRE_EMBERS,

    /** Tiny glowing star particles slowly drifting — peaceful night sky */
    STARDUST,

    /** Soft blurred bokeh circles of light floating gently */
    BOKEH_LIGHTS,

    /** Smooth slow looping gradient color cycle */
    GRADIENT_CYCLE,

    /** Gentle falling rain streaks — subtle and calm */
    GENTLE_RAIN
}

// ---------------------------------------------------------------------------
// Main dispatcher
// ---------------------------------------------------------------------------

/**
 * Renders an animated background for the given [variant].
 * [AnimatedBgVariant.NONE] renders nothing.
 */
@Composable
fun ChatAnimatedBackground(
    variant: AnimatedBgVariant,
    modifier: Modifier = Modifier
) {
    when (variant) {
        AnimatedBgVariant.NONE -> Unit
        AnimatedBgVariant.AURORA -> AuroraBackground(modifier)
        AnimatedBgVariant.OCEAN_WAVES -> OceanWavesBackground(modifier)
        AnimatedBgVariant.COSMIC -> CosmicBackground(modifier)
        AnimatedBgVariant.SUNSET_FLOW -> SunsetFlowBackground(modifier)
        AnimatedBgVariant.NEON_PULSE -> NeonPulseBackground(modifier)
        AnimatedBgVariant.FOREST_MIST -> ForestMistBackground(modifier)
        AnimatedBgVariant.FIRE_EMBERS -> FireEmbersBackground(modifier)
        AnimatedBgVariant.STARDUST      -> StardustBackground(modifier = modifier)
        AnimatedBgVariant.BOKEH_LIGHTS  -> BokehLightsBackground(modifier = modifier)
        AnimatedBgVariant.GRADIENT_CYCLE -> GradientCycleBackground(modifier = modifier)
        AnimatedBgVariant.GENTLE_RAIN   -> GentleRainBackground(modifier = modifier)
    }
}

// ---------------------------------------------------------------------------
// AURORA
// ---------------------------------------------------------------------------

@Composable
private fun AuroraBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "aurora")

    val offset1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aurora_offset1"
    )
    val offset2 by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aurora_offset2"
    )
    val alpha by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aurora_alpha"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Base dark background
        drawRect(color = Color(0xFF0A1628))

        // Layer 1 – green/teal aurora band
        val startY1 = h * (0.2f + 0.3f * offset1)
        val endY1 = h * (0.5f + 0.3f * offset1)
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0x00057A5A),
                    Color(0xCC00C9A7).copy(alpha = alpha * 0.7f),
                    Color(0x0057A5AA)
                ),
                start = Offset(0f, startY1),
                end = Offset(w, endY1)
            )
        )

        // Layer 2 – purple aurora band
        val startY2 = h * (0.1f + 0.4f * offset2)
        val endY2 = h * (0.45f + 0.35f * offset2)
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0x008B00FF),
                    Color(0xAA7B2FFF).copy(alpha = alpha * 0.5f),
                    Color(0x0043006B)
                ),
                start = Offset(w * 0.2f, startY2),
                end = Offset(w * 0.8f, endY2)
            )
        )

        // Layer 3 – teal shimmer
        val startX3 = w * offset1
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0x0000FFDD),
                    Color(0x9900E5CC).copy(alpha = alpha * 0.4f),
                    Color(0x0000AAAA)
                ),
                start = Offset(startX3, 0f),
                end = Offset(startX3 + w * 0.6f, h * 0.6f)
            )
        )
    }
}

// ---------------------------------------------------------------------------
// OCEAN WAVES
// ---------------------------------------------------------------------------

@Composable
private fun OceanWavesBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "ocean")

    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )
    val alpha by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave_alpha"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Deep ocean background
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF001B3A), Color(0xFF003D6B), Color(0xFF005C8A))
            )
        )

        val waveColors = listOf(
            Color(0xFF0077B6).copy(alpha = alpha * 0.6f),
            Color(0xFF0096C7).copy(alpha = alpha * 0.5f),
            Color(0xFF00B4D8).copy(alpha = alpha * 0.4f),
            Color(0xFF48CAE4).copy(alpha = alpha * 0.3f)
        )

        waveColors.forEachIndexed { index, color ->
            val waveHeight = h * (0.08f + index * 0.04f)
            val verticalOffset = h * (0.4f + index * 0.12f)
            val phaseShift = phase + index * (PI / 2).toFloat()
            val frequency = 1.5f + index * 0.5f

            val path = Path()
            path.moveTo(0f, verticalOffset)

            var x = 0f
            val step = w / 100f
            while (x <= w) {
                val y = verticalOffset + waveHeight * sin((x / w * frequency * 2 * PI + phaseShift).toFloat())
                if (x == 0f) path.moveTo(x, y.toFloat())
                else path.lineTo(x, y.toFloat())
                x += step
            }
            path.lineTo(w, h)
            path.lineTo(0f, h)
            path.close()

            drawPath(path = path, color = color)
        }
    }
}

// ---------------------------------------------------------------------------
// COSMIC
// ---------------------------------------------------------------------------

private data class Star(val x: Float, val y: Float, val radius: Float, val baseAlpha: Float)

@Composable
private fun CosmicBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "cosmic")

    val swirl by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cosmic_swirl"
    )
    val nebulaAlpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "nebula_alpha"
    )
    val starPulse by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "star_pulse"
    )

    // Generate stable star positions
    val stars = remember {
        val rng = java.util.Random(42L)
        List(80) {
            Star(
                x = rng.nextFloat(),
                y = rng.nextFloat(),
                radius = rng.nextFloat() * 3f + 1f,
                baseAlpha = rng.nextFloat() * 0.6f + 0.2f
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Deep space background
        drawRect(color = Color(0xFF06030F))

        // Nebula layers
        val cx = w * 0.5f + w * 0.15f * cos(swirl)
        val cy = h * 0.4f + h * 0.1f * sin(swirl)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF6A0DAD).copy(alpha = nebulaAlpha * 0.5f),
                    Color(0x003D0070)
                ),
                center = Offset(cx, cy),
                radius = w * 0.55f
            ),
            radius = w * 0.55f,
            center = Offset(cx, cy)
        )

        val cx2 = w * 0.3f + w * 0.1f * sin(swirl + 1f)
        val cy2 = h * 0.6f + h * 0.1f * cos(swirl + 1f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF1A0A5E).copy(alpha = nebulaAlpha * 0.6f),
                    Color(0x0010003A)
                ),
                center = Offset(cx2, cy2),
                radius = w * 0.45f
            ),
            radius = w * 0.45f,
            center = Offset(cx2, cy2)
        )

        val cx3 = w * 0.75f + w * 0.08f * cos(swirl + 2f)
        val cy3 = h * 0.25f + h * 0.08f * sin(swirl + 2f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFB0004E).copy(alpha = nebulaAlpha * 0.35f),
                    Color(0x00600025)
                ),
                center = Offset(cx3, cy3),
                radius = w * 0.35f
            ),
            radius = w * 0.35f,
            center = Offset(cx3, cy3)
        )

        // Stars
        stars.forEach { star ->
            val pulse = if (star.baseAlpha > 0.6f) starPulse else 1f
            drawCircle(
                color = Color.White.copy(alpha = star.baseAlpha * pulse),
                radius = star.radius,
                center = Offset(star.x * w, star.y * h)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// SUNSET FLOW
// ---------------------------------------------------------------------------

@Composable
private fun SunsetFlowBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "sunset")

    val flowOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sunset_flow"
    )
    val scale by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sunset_scale"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Flowing right-to-left sunset gradient
        val shiftX = w * (1f - flowOffset)

        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFF4500),
                    Color(0xFFFF6B35),
                    Color(0xFFFF8C42),
                    Color(0xFFFFB347),
                    Color(0xFFFF4081),
                    Color(0xFFE91E8C),
                    Color(0xFFFF1744),
                    Color(0xFFFF4500)
                ),
                start = Offset(shiftX - w * 0.5f, 0f),
                end = Offset(shiftX + w * 1.5f, h)
            )
        )

        // Warm glow overlay that pulses
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFD700).copy(alpha = 0.15f * scale),
                    Color(0x00FF8C00)
                ),
                center = Offset(w * 0.5f, h * 0.3f),
                radius = w * 0.7f * scale
            )
        )
    }
}

// ---------------------------------------------------------------------------
// NEON PULSE
// ---------------------------------------------------------------------------

@Composable
private fun NeonPulseBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "neon")

    val pulseAlpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "neon_alpha"
    )
    val lineOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "neon_line_offset"
    )
    val particlePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "neon_particle"
    )

    // Stable neon particle positions
    val particles = remember {
        val rng = java.util.Random(7L)
        List(30) {
            Triple(rng.nextFloat(), rng.nextFloat(), rng.nextFloat() * (2 * PI).toFloat())
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Dark city background
        drawRect(color = Color(0xFF050510))

        // Neon blue horizontal lines
        val lineCount = 8
        repeat(lineCount) { i ->
            val y = h * (i.toFloat() / lineCount) + h * 0.05f * sin(particlePhase + i)
            val lineAlpha = pulseAlpha * (0.4f + 0.6f * ((i % 3) / 2f))
            drawLine(
                color = Color(0xFF00BFFF).copy(alpha = lineAlpha.coerceIn(0f, 1f)),
                start = Offset(0f, y.toFloat()),
                end = Offset(w * (0.4f + 0.6f * lineOffset), y.toFloat()),
                strokeWidth = if (i % 2 == 0) 2f else 1f
            )
        }

        // Neon pink vertical lines
        val vLineCount = 6
        repeat(vLineCount) { i ->
            val x = w * (i.toFloat() / vLineCount) + w * 0.03f * cos(particlePhase + i * 0.7f)
            val vLineAlpha = pulseAlpha * (0.3f + 0.5f * ((i % 2).toFloat()))
            drawLine(
                color = Color(0xFFFF007F).copy(alpha = vLineAlpha.coerceIn(0f, 1f)),
                start = Offset(x.toFloat(), 0f),
                end = Offset(x.toFloat(), h * (0.3f + 0.5f * lineOffset)),
                strokeWidth = if (i % 3 == 0) 2f else 1f
            )
        }

        // Glowing neon particles
        particles.forEach { (px, py, phaseOffset) ->
            val animAlpha = (0.5f + 0.5f * sin(particlePhase + phaseOffset)) * pulseAlpha
            val isBlue = phaseOffset < PI.toFloat()
            val color = if (isBlue) Color(0xFF00FFFF) else Color(0xFFFF00FF)
            drawCircle(
                color = color.copy(alpha = animAlpha.coerceIn(0f, 1f)),
                radius = 4f,
                center = Offset(px * w, py * h)
            )
            // Glow halo
            drawCircle(
                color = color.copy(alpha = (animAlpha * 0.3f).coerceIn(0f, 1f)),
                radius = 10f,
                center = Offset(px * w, py * h)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// FOREST MIST
// ---------------------------------------------------------------------------

private data class MistParticle(val x: Float, val baseY: Float, val radius: Float, val speed: Float, val phase: Float)

@Composable
private fun ForestMistBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "forest")

    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "forest_drift"
    )
    val mistAlpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mist_alpha"
    )

    val particles = remember {
        val rng = java.util.Random(13L)
        List(50) {
            MistParticle(
                x = rng.nextFloat(),
                baseY = rng.nextFloat(),
                radius = rng.nextFloat() * 12f + 4f,
                speed = rng.nextFloat() * 0.4f + 0.1f,
                phase = rng.nextFloat()
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Forest background
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF0D2818), Color(0xFF1A4731), Color(0xFF0F3422))
            )
        )

        // Mist base layer
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0x00AAFFDD),
                    Color(0x33AAFFDD).copy(alpha = mistAlpha * 0.3f),
                    Color(0x0088CCAA)
                ),
                startY = h * 0.5f,
                endY = h
            )
        )

        // Drifting mist particles
        particles.forEach { p ->
            val currentY = ((p.baseY - drift * p.speed + p.phase) % 1f + 1f) % 1f
            val normalizedY = currentY  // 0 = top, 1 = bottom
            val risingY = 1f - normalizedY  // invert so particles rise

            // Fade out as they reach the top
            val alphaFade = (normalizedY * 2f).coerceIn(0f, 1f)
            val particleAlpha = mistAlpha * alphaFade * 0.6f

            val color = when {
                p.phase < 0.33f -> Color(0xFF90EE90) // light green
                p.phase < 0.66f -> Color(0xFF40E0D0) // teal
                else -> Color(0xFFFFFFFF)              // white mist
            }

            drawCircle(
                color = color.copy(alpha = particleAlpha.coerceIn(0f, 1f)),
                radius = p.radius,
                center = Offset(p.x * w, risingY * h)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// FIRE EMBERS
// ---------------------------------------------------------------------------

private data class Ember(val x: Float, val baseY: Float, val radius: Float, val speed: Float, val phase: Float)

@Composable
private fun FireEmbersBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "fire")

    val rise by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ember_rise"
    )
    val flicker by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fire_flicker"
    )

    val embers = remember {
        val rng = java.util.Random(99L)
        List(60) {
            Ember(
                x = rng.nextFloat(),
                baseY = rng.nextFloat(),
                radius = rng.nextFloat() * 5f + 2f,
                speed = rng.nextFloat() * 0.5f + 0.2f,
                phase = rng.nextFloat()
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Dark fire background
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF0A0000), Color(0xFF1A0500), Color(0xFF2D0800))
            )
        )

        // Fire glow at bottom
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0x00FF4500),
                    Color(0x66FF4500).copy(alpha = flicker * 0.5f),
                    Color(0xAAFF6B00).copy(alpha = flicker * 0.8f)
                ),
                startY = h * 0.6f,
                endY = h
            )
        )

        // Additional orange glow layer
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFF8C00).copy(alpha = flicker * 0.4f),
                    Color(0x00FF4500)
                ),
                center = Offset(w * 0.5f, h),
                radius = w * 0.8f
            )
        )

        // Rising ember particles
        embers.forEach { ember ->
            val riseProgress = ((ember.phase + rise * ember.speed) % 1f)
            // Embers start near the bottom and rise to the top
            val currentY = h * (1f - riseProgress)
            // Alpha decreases as ember rises (fades out at the top)
            val alphaFade = (1f - riseProgress).coerceIn(0f, 1f)
            val emberAlpha = alphaFade * flicker * 0.9f

            // Horizontal drift using sine for organic movement
            val drift = ember.radius * 3f * sin((riseProgress * 4 * PI + ember.phase * 2 * PI).toFloat())
            val currentX = (ember.x * w + drift).coerceIn(0f, w)

            val emberColor = when {
                ember.phase < 0.4f -> Color(0xFFFF4500) // red-orange
                ember.phase < 0.7f -> Color(0xFFFF8C00) // dark orange
                else -> Color(0xFFFFD700)                // golden ember
            }

            // Glow halo
            drawCircle(
                color = emberColor.copy(alpha = (emberAlpha * 0.3f).coerceIn(0f, 1f)),
                radius = ember.radius * 3f,
                center = Offset(currentX, currentY)
            )
            // Core ember
            drawCircle(
                color = emberColor.copy(alpha = emberAlpha.coerceIn(0f, 1f)),
                radius = ember.radius,
                center = Offset(currentX, currentY)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// STARDUST
// ---------------------------------------------------------------------------

@Composable
fun StardustBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "stardust")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
        label = "stardust_time"
    )

    val stars = remember {
        List(60) {
            Triple(
                (0..100).random() / 100f,  // x position
                (0..100).random() / 100f,  // y position
                (3..8).random() / 10f       // speed factor
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color(0xFF0A0A1F), Color(0xFF0F0F35), Color(0xFF151530))
            )
        )
        stars.forEach { (x, y, speed) ->
            val animY = (y - time * speed * 0.3f + 1f) % 1f
            val alpha = (0.3f + sin(time * speed * PI.toFloat() * 2f).coerceIn(-1f, 1f) * 0.35f + 0.35f).coerceIn(0.1f, 0.9f)
            val radius = (1.5f + speed * 2f)
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = radius,
                center = Offset(x * size.width, animY * size.height)
            )
            // Subtle glow
            if (speed > 0.6f) {
                drawCircle(
                    color = Color(0xFFADD8FF).copy(alpha = alpha * 0.3f),
                    radius = radius * 2.5f,
                    center = Offset(x * size.width, animY * size.height)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// BOKEH LIGHTS
// ---------------------------------------------------------------------------

@Composable
fun BokehLightsBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "bokeh")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing)),
        label = "bokeh_time"
    )

    val circleData: List<Triple<Float, Float, Float>> = remember {
        List(18) {
            Triple(
                (5..95).random() / 100f,
                (5..95).random() / 100f,
                (3..10).random() / 10f
            )
        }
    }
    val circleRadii = remember { List(18) { (20..60).random().toFloat() } }
    val circleColorIndices = remember { List(18) { (0..5).random() } }
    val bokehColors = listOf(
        Color(0xFFFF6B9D), Color(0xFF6B9DFF), Color(0xFF9DFF6B),
        Color(0xFFFFD96B), Color(0xFF6BFFD9), Color(0xFFD96BFF)
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(color = Color(0xFF0D0D1A))
        circleData.forEachIndexed { i, c ->
            val offsetX = cos(time * c.third) * 30f
            val offsetY = sin(time * c.third * 0.7f) * 20f
            val alpha = (0.12f + sin(time * c.third + c.first * PI.toFloat()).coerceIn(-1f, 1f) * 0.06f + 0.06f).coerceIn(0.05f, 0.25f)
            drawCircle(
                color = bokehColors[circleColorIndices[i]].copy(alpha = alpha),
                radius = circleRadii[i] * (size.width / 400f),
                center = Offset(c.first * size.width + offsetX, c.second * size.height + offsetY)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// GRADIENT CYCLE
// ---------------------------------------------------------------------------

@Composable
fun GradientCycleBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "gradcycle")
    val hueShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing)),
        label = "hue_shift"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val h1 = (hueShift % 360f) / 360f
        val h2 = ((hueShift + 120f) % 360f) / 360f
        val h3 = ((hueShift + 240f) % 360f) / 360f

        fun hslToColor(h: Float, s: Float, l: Float): Color {
            val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
            val x = c * (1f - kotlin.math.abs((h * 6f) % 2f - 1f))
            val m = l - c / 2f
            val (r, g, b) = when {
                h < 1f/6f -> Triple(c, x, 0f)
                h < 2f/6f -> Triple(x, c, 0f)
                h < 3f/6f -> Triple(0f, c, x)
                h < 4f/6f -> Triple(0f, x, c)
                h < 5f/6f -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }
            return Color(r + m, g + m, b + m)
        }

        drawRect(
            brush = Brush.verticalGradient(
                listOf(
                    hslToColor(h1, 0.6f, 0.28f),
                    hslToColor(h2, 0.55f, 0.22f),
                    hslToColor(h3, 0.5f, 0.18f)
                )
            )
        )
    }
}

// ---------------------------------------------------------------------------
// GENTLE RAIN
// ---------------------------------------------------------------------------

@Composable
fun GentleRainBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "rain")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "rain_time"
    )

    val drops = remember {
        List(80) {
            Triple(
                (0..100).random() / 100f,  // x
                (0..100).random() / 100f,  // initial y offset
                (4..12).random() / 10f      // speed
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color(0xFF1A2A3A), Color(0xFF0F1F2F), Color(0xFF0A1520))
            )
        )
        drops.forEach { (x, yOff, speed) ->
            val y = (yOff + time * speed) % 1f
            val alpha = (0.15f + speed * 0.2f).coerceIn(0.1f, 0.4f)
            val length = 8f + speed * 12f
            drawLine(
                color = Color(0xFF9EC8E8).copy(alpha = alpha),
                start = Offset(x * size.width, y * size.height),
                end = Offset(x * size.width - length * 0.15f, y * size.height + length),
                strokeWidth = 1f,
                cap = StrokeCap.Round
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Preferences
// ---------------------------------------------------------------------------

/**
 * Persists the selected [AnimatedBgVariant] in SharedPreferences.
 */
object AnimatedBgPrefs {
    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_VARIANT = "animated_bg_variant"

    private val _variantFlow = MutableStateFlow(AnimatedBgVariant.NONE)
    val variantFlow: StateFlow<AnimatedBgVariant> = _variantFlow.asStateFlow()

    fun getVariant(context: Context): AnimatedBgVariant {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_VARIANT, AnimatedBgVariant.NONE.name)
        return try {
            AnimatedBgVariant.valueOf(name ?: AnimatedBgVariant.NONE.name)
        } catch (e: IllegalArgumentException) {
            AnimatedBgVariant.NONE
        }
    }

    fun setVariant(context: Context, variant: AnimatedBgVariant) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_VARIANT, variant.name)
            .apply()
        _variantFlow.value = variant
    }

    /** Call once when the chat screen enters composition to sync the flow from SharedPreferences. */
    fun syncFromPrefs(context: Context) {
        _variantFlow.value = getVariant(context)
    }
}

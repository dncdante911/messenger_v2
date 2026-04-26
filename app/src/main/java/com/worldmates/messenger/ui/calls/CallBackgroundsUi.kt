package com.worldmates.messenger.ui.calls

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun CallBgLayer(bg: CallBackground, modifier: Modifier = Modifier) {
    when (bg) {
        CallBackground.DEFAULT -> DefaultCallBg(modifier)
        CallBackground.SUNSET -> SunsetCallBg(modifier)
        CallBackground.OCEAN -> OceanCallBg(modifier)
        CallBackground.AURORA -> AuroraCallBg(modifier)
        CallBackground.STARFIELD -> StarfieldCallBg(modifier)
        CallBackground.NEON_CITY -> NeonCityCallBg(modifier)
        CallBackground.GRADIENT_SHIFT -> GradientShiftCallBg(modifier)
        CallBackground.CRYSTAL -> CrystalCallBg(modifier)
    }
}

@Composable
fun DefaultCallBg(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF1A1A2E),
                    Color(0xFF16213E),
                    Color(0xFF0F2040)
                )
            )
        )
    )
}

@Composable
fun SunsetCallBg(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF1A0622),
                    Color(0xFF5C1A40),
                    Color(0xFFB84525)
                )
            )
        )
    )
}

@Composable
fun OceanCallBg(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF001524),
                    Color(0xFF003355),
                    Color(0xFF015F8A)
                )
            )
        )
    )
}

@Composable
fun AuroraCallBg(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "aurora")

    val greenOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "greenOffset"
    )

    val greenAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "greenAlpha"
    )

    val purpleOffset by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(5500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "purpleOffset"
    )

    val purpleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(5500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "purpleAlpha"
    )

    Box(
        modifier = modifier.background(Color(0xFF020E1A))
    ) {
        // Green aurora band
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.4f)
                .offset(y = (greenOffset * 200f - 50f).dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x0000E676),
                            Color(0x00E676).copy(alpha = greenAlpha),
                            Color(0x0000E676)
                        )
                    )
                )
        )
        // Purple aurora band
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.35f)
                .offset(y = (purpleOffset * 250f).dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x007B1FA2),
                            Color(0xFF7B1FA2).copy(alpha = purpleAlpha),
                            Color(0x007B1FA2)
                        )
                    )
                )
        )
    }
}

@Composable
fun StarfieldCallBg(modifier: Modifier = Modifier) {
    val stars = remember {
        listOf(
            Triple(0.07f, 0.08f, 2.5f),
            Triple(0.18f, 0.03f, 1.5f),
            Triple(0.32f, 0.11f, 3f),
            Triple(0.45f, 0.06f, 2f),
            Triple(0.58f, 0.09f, 1.5f),
            Triple(0.72f, 0.04f, 2.5f),
            Triple(0.88f, 0.07f, 2f),
            Triple(0.12f, 0.18f, 1.5f),
            Triple(0.28f, 0.22f, 2f),
            Triple(0.52f, 0.15f, 1.5f),
            Triple(0.65f, 0.25f, 3f),
            Triple(0.80f, 0.18f, 2f),
            Triple(0.93f, 0.22f, 1.5f),
            Triple(0.05f, 0.35f, 2.5f),
            Triple(0.22f, 0.40f, 1.5f),
            Triple(0.38f, 0.30f, 2f),
            Triple(0.55f, 0.45f, 1.5f),
            Triple(0.70f, 0.38f, 2.5f),
            Triple(0.85f, 0.42f, 2f),
            Triple(0.15f, 0.55f, 1.5f),
            Triple(0.30f, 0.60f, 2.5f),
            Triple(0.48f, 0.52f, 2f),
            Triple(0.62f, 0.58f, 1.5f),
            Triple(0.78f, 0.50f, 3f),
            Triple(0.95f, 0.55f, 2f),
            Triple(0.10f, 0.68f, 2.5f),
            Triple(0.25f, 0.72f, 1.5f),
            Triple(0.42f, 0.65f, 2f)
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "starfield")

    val twinkle0 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, delayMillis = 0, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "twinkle0"
    )

    val twinkle1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, delayMillis = 300, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "twinkle1"
    )

    val twinkle2 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, delayMillis = 150, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "twinkle2"
    )

    val twinkleGroups = listOf(twinkle0, twinkle1, twinkle2)

    Canvas(modifier = modifier.background(Color(0xFF03040D))) {
        stars.forEachIndexed { index, (xFrac, yFrac, radius) ->
            val alpha = twinkleGroups[index % 3]
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = radius,
                center = Offset(xFrac * size.width, yFrac * size.height)
            )
        }
    }
}

@Composable
fun NeonCityCallBg(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "neonCity")

    val magentaAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "magentaAlpha"
    )

    val cyanAlpha by infiniteTransition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, delayMillis = 400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cyanAlpha"
    )

    val accentMagentaAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "accentMagentaAlpha"
    )

    val accentCyanAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, delayMillis = 400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "accentCyanAlpha"
    )

    Box(modifier = modifier.background(Color(0xFF050510))) {
        // Magenta glow band at bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .align(androidx.compose.ui.Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFFFF0090).copy(alpha = magentaAlpha)
                        )
                    )
                )
        )
        // Cyan glow band at bottom (overlapping)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .align(androidx.compose.ui.Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF00EEFF).copy(alpha = cyanAlpha)
                        )
                    )
                )
        )
        // Magenta vertical accent line on the left
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .offset(x = 60.dp)
                .background(Color(0xFFFF0090).copy(alpha = accentMagentaAlpha))
        )
        // Cyan vertical accent line on the right
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .align(androidx.compose.ui.Alignment.TopEnd)
                .offset(x = (-80).dp)
                .background(Color(0xFF00EEFF).copy(alpha = accentCyanAlpha))
        )
    }
}

@Composable
fun GradientShiftCallBg(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "gradientShift")

    val stop1 by infiniteTransition.animateColor(
        initialValue = Color(0xFF1A0066),
        targetValue = Color(0xFF330000),
        animationSpec = infiniteRepeatable(
            animation = tween(6000, delayMillis = 0, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "stop1"
    )

    val stop2 by infiniteTransition.animateColor(
        initialValue = Color(0xFF003399),
        targetValue = Color(0xFF660033),
        animationSpec = infiniteRepeatable(
            animation = tween(6000, delayMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "stop2"
    )

    val stop3 by infiniteTransition.animateColor(
        initialValue = Color(0xFF003333),
        targetValue = Color(0xFF220033),
        animationSpec = infiniteRepeatable(
            animation = tween(6000, delayMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "stop3"
    )

    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                colors = listOf(stop1, stop2, stop3)
            )
        )
    )
}

@Composable
fun CrystalCallBg(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "crystal")

    val shimmer by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer"
    )

    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF0A1628),
                    Color(0xFF1565C0).copy(alpha = 0.4f + shimmer * 0.5f),
                    Color(0xFF42A5F5).copy(alpha = 0.3f + shimmer * 0.6f),
                    Color(0xFF1565C0).copy(alpha = 0.4f + shimmer * 0.4f),
                    Color(0xFF0A1628)
                ),
                start = Offset(shimmer * 1000f, 0f),
                end = Offset(shimmer * 1000f + 800f, 1200f)
            )
        )
    )
}

package com.worldmates.messenger.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Цвета бренда
private val BrandTeal  = Color(0xFF4ECDC4)
private val BrandMint  = Color(0xFF95E1A3)
private val BrandBlue  = Color(0xFF0084FF)

/**
 * Основной лоадер приложения — три точки в фирменных цветах.
 *
 * Использование:
 *   WMLoadingIndicator()                    // стандартный
 *   WMLoadingIndicator(label = "Загрузка…") // с текстом
 *   WMLoadingIndicator(size = Large)        // большой
 */
@Composable
fun WMLoadingIndicator(
    modifier: Modifier = Modifier,
    label: String? = null,
    size: WMLoadingSize = WMLoadingSize.Medium,
    color: Color = BrandTeal
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        WMBouncingDots(dotSize = size.dotSize, color = color)

        if (label != null) {
            Text(
                text = label,
                fontSize = 14.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Полноэкранный лоадер — используй когда загружается весь экран.
 */
@Composable
fun WMFullScreenLoading(
    message: String = "Загружаем…"
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Маленькая иконка маскота над точками
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        Brush.linearGradient(listOf(BrandTeal, BrandMint)),
                        shape = RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("🐶", fontSize = 36.sp)
            }

            WMBouncingDots(dotSize = 12.dp)

            Text(
                text = message,
                fontSize = 15.sp,
                color = Color(0xFF757575),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Инлайн лоадер — вместо стандартного CircularProgressIndicator.
 * Использование: заменяй CircularProgressIndicator() → WMInlineLoader()
 */
@Composable
fun WMInlineLoader(
    modifier: Modifier = Modifier,
    color: Color = BrandTeal
) {
    WMBouncingDots(
        modifier = modifier,
        dotSize = 8.dp,
        spacing = 5.dp,
        color = color
    )
}

/**
 * Базовые три прыгающие точки — можно использовать где угодно.
 */
@Composable
fun WMBouncingDots(
    modifier: Modifier = Modifier,
    dotSize: Dp = 10.dp,
    spacing: Dp = 7.dp,
    color: Color = BrandTeal
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wm_dots")

    // Каждая точка стартует с задержкой 150ms
    val dot1Y = infiniteTransition.animateBounce(delayMs = 0)
    val dot2Y = infiniteTransition.animateBounce(delayMs = 150)
    val dot3Y = infiniteTransition.animateBounce(delayMs = 300)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(dot1Y, dot2Y, dot3Y).forEachIndexed { index, offset ->
            Box(
                modifier = Modifier
                    .offset(y = offset.value.dp)
                    .size(dotSize)
                    .background(
                        color = when (index) {
                            0 -> BrandTeal
                            1 -> color
                            else -> BrandMint
                        },
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
private fun InfiniteTransition.animateBounce(delayMs: Int): State<Float> {
    return animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 900
                0f   at delayMs      with EaseInOut
                -12f at delayMs + 180 with EaseInOut
                0f   at delayMs + 360
                0f   at 900
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "bounce_$delayMs"
    )
}

enum class WMLoadingSize(val dotSize: Dp) {
    Small(7.dp),
    Medium(10.dp),
    Large(14.dp)
}

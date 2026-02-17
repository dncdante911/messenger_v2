package com.worldmates.messenger.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Динамический анимированный фон для мессенджера
 * Добавляет жизнь и движение в интерфейс
 */
@Composable
fun AnimatedGradientBackground(
    brush: Brush,
    modifier: Modifier = Modifier,
    animated: Boolean = true
) {
    // Плавная пульсация фона для эффекта "дыхания"
    val infiniteTransition = rememberInfiniteTransition(label = "background_pulse")

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha_animation"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(brush)
            .alpha(if (animated) alpha else 1f)
    )
}

/**
 * Overlay эффект для добавления глубины
 * Создает эффект "стеклянных слоев"
 */
@Composable
fun GradientOverlay(
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(
        Color.White.copy(alpha = 0.05f),
        Color.Transparent,
        Color.Black.copy(alpha = 0.1f)
    )
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(colors)
            )
    )
}

/**
 * Эффект движущихся точек/частиц для футуристичности
 * Можно добавить позже для еще более живого интерфейса
 */
@Composable
fun ParticleEffect(
    modifier: Modifier = Modifier,
    particleColor: Color = Color.White.copy(alpha = 0.3f)
) {
    // TODO: Реализовать Canvas с анимированными частицами
    // Пока оставляем заглушку для будущих улучшений
    Box(modifier = modifier.fillMaxSize())
}

/**
 * Полный композит фона с всеми эффектами
 */
@Composable
fun DynamicThemedBackground(
    themePalette: ThemePalette,
    modifier: Modifier = Modifier,
    enableAnimations: Boolean = true,
    enableOverlay: Boolean = true
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Базовый градиентный фон
        AnimatedGradientBackground(
            brush = themePalette.backgroundGradient,
            animated = enableAnimations
        )

        // Overlay для глубины
        if (enableOverlay) {
            GradientOverlay()
        }
    }
}

/**
 * Glassmorphism эффект для карточек поверх фона
 * Создает эффект матового стекла
 */
fun Modifier.glassmorphism(
    blur: Float = 20f,
    backgroundColor: Color = Color.White.copy(alpha = 0.1f),
    borderColor: Color = Color.White.copy(alpha = 0.2f)
): Modifier {
    return this
        .background(backgroundColor)
        // TODO: Добавить blur через renderEffect когда доступно
        // .blur(radiusX = blur.dp, radiusY = blur.dp)
}

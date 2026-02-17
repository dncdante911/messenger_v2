package com.worldmates.messenger.ui.messages.selection

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ⚡ Швидка реакція при подвійному тапі
 *
 * Показує анімоване сердечко або інший емодзі при подвійному тапі на повідомленні.
 * - Плавна анімація масштабування та прозорості
 * - Налаштовуваний емодзі (за замовчуванням ❤️)
 * - Автоматичне зникнення після завершення анімації
 *
 * @param visible Чи показувати анімацію
 * @param emoji Емодзі для відображення (за замовчуванням "❤️")
 * @param onAnimationEnd Callback викликається після завершення анімації
 */
@Composable
fun QuickReactionAnimation(
    visible: Boolean,
    emoji: String = "❤️",
    onAnimationEnd: () -> Unit
) {
    // Анімація масштабування
    val scale by animateFloatAsState(
        targetValue = if (visible) 1.5f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        finishedListener = { if (!visible) onAnimationEnd() },
        label = "reaction_scale"
    )

    // Анімація прозорості
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "reaction_alpha"
    )

    // Відображаємо тільки якщо visible або анімація ще йде
    if (visible || scale > 0f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emoji,
                fontSize = 80.sp,
                modifier = Modifier.shadow(8.dp)
            )
        }
    }
}

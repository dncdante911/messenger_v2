package com.worldmates.messenger.ui.channels.premium.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.ui.channels.premium.design.PremiumMotion

/**
 * Small emoji sitting next to a channel name — the "emoji status" slot.
 * Gently pulses every PremiumMotion.pulseDurationMs when [animated].
 *
 * Tap target defaults to size+8 dp so the owner can easily change it
 * from the header.
 */
@Composable
fun PremiumEmojiStatus(
    emoji: String?,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    animated: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    if (emoji.isNullOrBlank()) return

    val scale = if (animated) {
        val transition = rememberInfiniteTransition(label = "emojiPulse")
        val v by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = PremiumMotion.pulseDurationMs,
                    easing = LinearEasing,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "emojiPulseValue",
        )
        v
    } else 1f

    val clickable = if (onClick != null) {
        Modifier.clip(CircleShape).clickable(onClick = onClick)
    } else Modifier

    Box(
        modifier = modifier
            .size(size + 8.dp)
            .then(clickable),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = emoji,
            fontSize = size.value.sp,
            modifier = Modifier.scale(scale),
        )
    }
}

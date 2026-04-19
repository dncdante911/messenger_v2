package com.worldmates.messenger.ui.channels.premium.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.worldmates.messenger.ui.channels.premium.design.PremiumBrushes
import com.worldmates.messenger.ui.channels.premium.design.PremiumDesign
import com.worldmates.messenger.ui.channels.premium.design.PremiumMotion
import com.worldmates.messenger.ui.channels.premium.design.current
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Full-screen celebration shown the first time a channel reaches a new
 * level. Renders:
 *  - dimmed obsidian scrim
 *  - centred glass card with the new [PremiumLevelBadge] + tier name +
 *    short description of what unlocked
 *  - procedural gold confetti floating behind the card
 *
 * The dialog dismisses on tap outside or via the CTA button.
 */
@Composable
fun ChannelLevelUpDialog(
    visible: Boolean,
    newLevel: Int,
    unlockedSummary: String,
    onDismiss: () -> Unit,
    onExplore: () -> Unit,
    channelName: String? = null,
) {
    if (!visible) return
    val design = PremiumDesign.current
    val tier = PremiumChannelTier.forLevel(newLevel)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true,
            dismissOnBackPress = true,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(design.colors.scrim),
            contentAlignment = Alignment.Center,
        ) {
            GoldConfettiField()

            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(PremiumMotion.fadeInOutMs)) + scaleIn(initialScale = 0.86f),
                exit = fadeOut() + scaleOut(targetScale = 0.9f),
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(design.colors.backgroundElevated)
                        .border(width = 0.6.dp, brush = PremiumBrushes.goldHairline(), shape = RoundedCornerShape(20.dp))
                        .padding(horizontal = 22.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PremiumBadge(size = PremiumBadgeSize.Hero, animated = true)

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = "LEVEL UP!",
                        style = design.typography.overline.copy(color = design.colors.accent),
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = channelName?.takeIf { it.isNotBlank() }?.let { "$it is now" } ?: "Your channel is now",
                        style = design.typography.caption.copy(color = design.colors.onMuted),
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(10.dp))

                    PremiumLevelBadge(level = newLevel, tier = tier)

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = unlockedSummary,
                        style = design.typography.body.copy(color = design.colors.onSecondary),
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(22.dp))

                    PremiumPrimaryButton(
                        text = "See what's new",
                        onClick = onExplore,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "Tap outside to close",
                        style = design.typography.caption.copy(color = design.colors.onMuted),
                    )
                }
            }
        }
    }
}

/**
 * Lightweight animated confetti. Generates a small fixed set of gold /
 * rose-gold rectangles and slowly rotates them around the card. Intentionally
 * avoids per-frame physics so it stays cheap on low-end devices.
 */
@Composable
private fun GoldConfettiField() {
    val design = PremiumDesign.current
    val pieces = remember {
        List(24) { ConfettiPiece.random(seed = it) }
    }
    val transition = rememberInfiniteTransition(label = "levelUpConfetti")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "levelUpConfettiPhase",
    )

    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        pieces.forEach { p ->
            val t = (phase + p.offset) % 1f
            val radius = size.minDimension * (0.2f + 0.35f * p.ring)
            val angle = (p.angle + t * 360f) * (PI.toFloat() / 180f)
            val x = center.x + cos(angle) * radius
            val y = center.y + sin(angle) * radius
            val color = if (p.rose) design.colors.secondary else design.colors.accent
            rotate(degrees = t * 720f + p.angle, pivot = Offset(x, y)) {
                drawRect(
                    color = color.copy(alpha = 0.55f + 0.3f * p.ring),
                    topLeft = Offset(x - p.size / 2f, y - p.size / 6f),
                    size = androidx.compose.ui.geometry.Size(p.size, p.size / 3f),
                )
            }
        }
    }
}

private data class ConfettiPiece(
    val angle: Float,
    val ring: Float,
    val offset: Float,
    val size: Float,
    val rose: Boolean,
) {
    companion object {
        fun random(seed: Int): ConfettiPiece {
            val r = Random(seed * 9173)
            return ConfettiPiece(
                angle = r.nextFloat() * 360f,
                ring = r.nextFloat(),
                offset = r.nextFloat(),
                size = 8f + r.nextFloat() * 10f,
                rose = r.nextFloat() < 0.25f,
            )
        }
    }
}

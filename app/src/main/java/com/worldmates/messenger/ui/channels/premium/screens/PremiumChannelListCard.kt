package com.worldmates.messenger.ui.channels.premium.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.worldmates.messenger.data.model.Channel
import com.worldmates.messenger.ui.channels.premium.components.AvatarFrameStyle
import com.worldmates.messenger.ui.channels.premium.components.PremiumBadge
import com.worldmates.messenger.ui.channels.premium.components.PremiumBadgeSize
import com.worldmates.messenger.ui.channels.premium.components.PremiumChannelAvatar
import com.worldmates.messenger.ui.channels.premium.components.PremiumEmojiStatus
import com.worldmates.messenger.ui.channels.premium.components.PremiumLevelIndicatorMicro
import com.worldmates.messenger.ui.channels.premium.components.PremiumLockMark
import com.worldmates.messenger.ui.channels.premium.components.PremiumVerifiedMark
import com.worldmates.messenger.ui.channels.premium.design.PremiumBrushes
import com.worldmates.messenger.ui.channels.premium.design.PremiumDesign
import com.worldmates.messenger.ui.channels.premium.design.PremiumTheme
import com.worldmates.messenger.ui.channels.premium.design.current

/**
 * Obsidian list card. Intentionally does not use the Material [Card] —
 * we want a direct obsidian surface with a gold hairline, no Material
 * elevation shadow (which clashes with the matte palette).
 *
 * On press the hairline thickens and a subtle aurora overlay appears —
 * that is the only place the aurora triad shows up in the list.
 */
@Composable
fun PremiumChannelListCard(
    channel: Channel,
    channelLevel: Int,
    channelLevelProgress: Float,
    emojiStatus: String?,
    animatedAvatarUrl: String?,
    avatarFrame: AvatarFrameStyle,
    onClick: () -> Unit,
    onSubscribeToggle: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    PremiumTheme {
        val design = PremiumDesign.current
        val shape = design.shapes.large
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()

        val containerBrush: Brush = if (pressed) {
            Brush.linearGradient(
                colors = listOf(
                    design.colors.surface,
                    design.colors.surfaceHigh,
                ),
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    design.colors.backgroundElevated,
                    design.colors.surface,
                ),
            )
        }
        val borderBrush = if (pressed) PremiumBrushes.auroraHighlight() else PremiumBrushes.goldHairline()

        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(shape)
                .background(containerBrush, shape)
                .border(width = if (pressed) 1.2.dp else 0.6.dp, brush = borderBrush, shape = shape)
                .clickable(
                    interactionSource = interaction,
                    indication = ripple(bounded = true, color = design.colors.accent),
                    onClick = onClick,
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar + tiny badge overlay.
            Box(modifier = Modifier.size(56.dp)) {
                PremiumChannelAvatar(
                    size = 56.dp,
                    imageUrl = channel.avatarUrl,
                    animatedUrl = animatedAvatarUrl,
                    initials = channel.name,
                    frame = avatarFrame,
                    modifier = Modifier.size(56.dp),
                )
                PremiumBadge(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(16.dp),
                    size = PremiumBadgeSize.Micro,
                    animated = !pressed,
                )
            }

            Spacer(Modifier.width(12.dp))

            // Text column.
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = channel.name.ifBlank { "—" },
                        style = design.typography.titleSmall.copy(color = design.colors.onPrimary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (channel.isVerified) PremiumVerifiedMark(size = 12.dp)
                    if (channel.isPrivate) PremiumLockMark(size = 12.dp)
                    PremiumEmojiStatus(
                        emoji = emojiStatus,
                        size = 14.dp,
                        animated = true,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!channel.username.isNullOrBlank()) {
                        Text(
                            text = "@${channel.username}",
                            style = design.typography.caption.copy(color = design.colors.onMuted),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(
                            text = "·",
                            style = design.typography.caption.copy(color = design.colors.onMuted),
                        )
                    }
                    Text(
                        text = "${formatMetric(channel.subscribersCount)} subs",
                        style = design.typography.metric.copy(color = design.colors.onSecondary),
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Level micro + optional subscribe.
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PremiumLevelIndicatorMicro(level = channelLevel, progress = channelLevelProgress)
                if (onSubscribeToggle != null && !channel.isSubscribed && !channel.isAdmin) {
                    SubscribePill(onClick = onSubscribeToggle)
                }
                if (channel.isAdmin) {
                    AdminPill()
                }
            }
        }
    }
}

@Composable
private fun SubscribePill(onClick: () -> Unit) {
    val design = PremiumDesign.current
    Box(
        modifier = Modifier
            .clip(design.shapes.pill)
            .background(PremiumBrushes.matteGoldLinear(), design.shapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = "JOIN",
            style = design.typography.overline.copy(color = design.colors.onAccent),
        )
    }
}

@Composable
private fun AdminPill() {
    val design = PremiumDesign.current
    Box(
        modifier = Modifier
            .clip(design.shapes.pill)
            .background(design.colors.glassFill, design.shapes.pill)
            .border(width = 0.6.dp, color = design.colors.glassStroke, shape = design.shapes.pill)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = "ADMIN",
            style = design.typography.overline.copy(color = design.colors.accent),
        )
    }
}

private fun formatMetric(n: Int): String = when {
    n < 1_000 -> n.toString()
    n < 10_000 -> "%.1fK".format(n / 1000f).replace(".0K", "K")
    n < 1_000_000 -> "${n / 1000}K"
    else -> "%.1fM".format(n / 1_000_000f).replace(".0M", "M")
}

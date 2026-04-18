package com.worldmates.messenger.ui.channels.premium.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.ui.channels.premium.design.PremiumDesign
import com.worldmates.messenger.ui.channels.premium.design.current

/**
 * Reaction on a premium post. Emits a small bounce on press and uses
 * gold fill when the user has reacted themselves.
 */
data class PremiumReaction(
    val emoji: String,
    val count: Int,
    val userReacted: Boolean,
    val isPremiumOnly: Boolean = false,
)

@Composable
fun PremiumReactionChip(
    reaction: PremiumReaction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val design = PremiumDesign.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.45f),
        label = "reactionScale",
    )
    val shape = CircleShape
    val fill = if (reaction.userReacted) design.colors.reactionSelectedFill else design.colors.reactionIdleFill
    val border = if (reaction.userReacted) design.colors.accent else design.colors.outline

    Row(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(fill, shape)
            .border(width = if (reaction.userReacted) 0.8.dp else 0.5.dp, color = border, shape = shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = reaction.emoji, fontSize = 14.sp)
        if (reaction.count > 0) {
            Text(
                text = formatCount(reaction.count),
                style = design.typography.metric.copy(
                    color = if (reaction.userReacted) design.colors.accent else design.colors.onSecondary,
                ),
            )
        }
    }
}

/** Horizontal bar of reactions + "add" button. Caller handles overflow. */
@Composable
fun PremiumReactionBar(
    reactions: List<PremiumReaction>,
    onReactionClick: (PremiumReaction) -> Unit,
    onAddReaction: () -> Unit,
    modifier: Modifier = Modifier,
    maxVisible: Int = 6,
) {
    if (reactions.isEmpty()) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PremiumAddReactionButton(onClick = onAddReaction)
        }
        return
    }
    val visible = reactions.take(maxVisible)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        visible.forEach { r ->
            PremiumReactionChip(reaction = r, onClick = { onReactionClick(r) })
        }
        Spacer(Modifier.width(2.dp))
        PremiumAddReactionButton(onClick = onAddReaction)
    }
}

@Composable
private fun PremiumAddReactionButton(onClick: () -> Unit) {
    val design = PremiumDesign.current
    val shape = CircleShape
    Row(
        modifier = Modifier
            .clip(shape)
            .background(design.colors.glassFill, shape)
            .border(width = 0.5.dp, color = design.colors.glassStroke, shape = shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "+",
            style = design.typography.button.copy(color = design.colors.accent),
            fontSize = 14.sp,
        )
    }
}

private fun formatCount(n: Int): String = when {
    n < 1_000 -> n.toString()
    n < 10_000 -> "%.1fK".format(n / 1000f).removeSuffix(".0K").let { if (it.endsWith("K")) it else "${it}K" }
    n < 1_000_000 -> "${n / 1000}K"
    else -> "%.1fM".format(n / 1_000_000f).removeSuffix(".0M").let { if (it.endsWith("M")) it else "${it}M" }
}

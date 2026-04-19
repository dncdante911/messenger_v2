package com.worldmates.messenger.ui.channels.premium.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.ui.channels.premium.design.ChannelAppearance
import com.worldmates.messenger.ui.channels.premium.design.PremiumDesign
import com.worldmates.messenger.ui.channels.premium.design.current

/**
 * Bottom-sheet content showing the channel's emoji-pack reactions in a
 * dense grid. Selecting an emoji fires [onReactionPick] and the caller
 * dismisses the sheet.
 *
 * Reads the active [ChannelAppearance] so each channel's reactions pick
 * comes from its chosen pack (classic / celebration / nature / finance /
 * cosmos). Unknown / absent pack falls back to Classic.
 */
@Composable
fun PremiumReactionsPicker(
    onReactionPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    currentReaction: String? = null,
    columns: Int = 7,
) {
    val design = PremiumDesign.current
    val pack = ChannelAppearance.current.emojiPack

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(design.colors.backgroundElevated, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        // Handle.
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 36.dp, height = 3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(design.colors.glassStroke),
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = title ?: pack.displayName,
            style = design.typography.titleSmall.copy(color = design.colors.onPrimary),
        )

        Spacer(Modifier.height(12.dp))

        // Non-lazy chunked grid — emoji sets are tiny (≤ ~30) so laziness is
        // unnecessary, and a LazyVerticalGrid inside a non-sized parent (like
        // a ModalBottomSheet content or another Column) throws "measured with
        // infinity maximum height".
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            pack.reactions.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { emoji ->
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            PremiumReactionCell(
                                emoji = emoji,
                                selected = emoji == currentReaction,
                                onClick = { onReactionPick(emoji) },
                            )
                        }
                    }
                    // Pad the last row so cells in a short row stay the same size as full rows.
                    repeat(columns - row.size) {
                        Box(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PremiumReactionCell(
    emoji: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val design = PremiumDesign.current
    val shape = CircleShape
    val fill = if (selected) design.colors.reactionSelectedFill else design.colors.reactionIdleFill
    val border = if (selected) design.colors.accent else design.colors.glassStroke
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(shape)
            .background(fill, shape)
            .border(width = if (selected) 0.8.dp else 0.5.dp, color = border, shape = shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = emoji, fontSize = 20.sp)
    }
}

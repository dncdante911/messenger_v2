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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
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
 * Owner-only sheet for picking the channel's "emoji status" — a small
 * emoji that sits next to the channel name. Pulls suggestions from the
 * active emoji pack's statusChoices plus a Classic fallback row.
 *
 * A trailing X cell clears the status.
 */
@Composable
fun PremiumEmojiStatusPicker(
    current: String?,
    onPick: (String?) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Emoji status",
) {
    val design = PremiumDesign.current
    val appearance = ChannelAppearance.current
    val packChoices = appearance.emojiPack.statusChoices
    val classicChoices = listOf("⭐\uFE0F", "\uD83D\uDC8E", "\uD83D\uDC51", "\uD83C\uDFC6", "\uD83D\uDD25")
    val merged = (packChoices + classicChoices).distinct()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(design.colors.backgroundElevated, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 36.dp, height = 3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(design.colors.glassStroke),
        )
        Spacer(Modifier.height(12.dp))

        Text(
            text = title,
            style = design.typography.titleSmall.copy(color = design.colors.onPrimary),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = appearance.emojiPack.displayName,
            style = design.typography.caption.copy(color = design.colors.onMuted),
        )

        Spacer(Modifier.height(12.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(merged) { emoji ->
                EmojiStatusCell(
                    emoji = emoji,
                    selected = emoji == current,
                    onClick = { onPick(emoji) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(design.colors.glassFill)
                .border(width = 0.5.dp, color = design.colors.glassStroke, shape = RoundedCornerShape(12.dp))
                .clickable { onPick(null) }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = null,
                tint = design.colors.onSecondary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = "Clear status",
                style = design.typography.button.copy(color = design.colors.onPrimary),
            )
        }
    }
}

@Composable
private fun EmojiStatusCell(
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
            .size(48.dp)
            .clip(shape)
            .background(fill, shape)
            .border(width = if (selected) 0.8.dp else 0.5.dp, color = border, shape = shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = emoji, fontSize = 22.sp)
    }
}

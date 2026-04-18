package com.worldmates.messenger.ui.channels.premium.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.worldmates.messenger.data.model.Channel
import com.worldmates.messenger.ui.channels.premium.components.AvatarFrameStyle
import com.worldmates.messenger.ui.channels.premium.components.PremiumBadge
import com.worldmates.messenger.ui.channels.premium.components.PremiumBadgeSize
import com.worldmates.messenger.ui.channels.premium.components.PremiumChannelAvatar
import com.worldmates.messenger.ui.channels.premium.components.PremiumDivider
import com.worldmates.messenger.ui.channels.premium.components.PremiumEmojiStatus
import com.worldmates.messenger.ui.channels.premium.components.PremiumGlassIconButton
import com.worldmates.messenger.ui.channels.premium.components.PremiumLevelIndicatorMicro
import com.worldmates.messenger.ui.channels.premium.components.PremiumLockMark
import com.worldmates.messenger.ui.channels.premium.components.PremiumVerifiedMark
import com.worldmates.messenger.ui.channels.premium.design.PremiumDesign
import com.worldmates.messenger.ui.channels.premium.design.PremiumTheme
import com.worldmates.messenger.ui.channels.premium.design.current

/**
 * Obsidian Gold hero header for premium channels.
 *
 * Replaces the previous 3-gradient + 3 blur-sphere + scrim layout with
 * a single obsidian background, gold hairline accent strip, a 92-dp
 * framed avatar and a compact stats row. The aurora highlight is
 * reserved for press states on the action buttons only.
 *
 * Layout (top-to-bottom):
 *   ┌────────────────────────────────────────────────────────┐
 *   │  ← back          [settings] [add]    ← glass chips    │
 *   │                                                        │
 *   │          [Avatar 92dp]   Name ★ ✓ 🔒 🎨               │
 *   │                          @username                    │
 *   │                                                        │
 *   │  ─────── gold hairline ────────                        │
 *   │  Short description (2 lines)                           │
 *   │  ○ Lvl  12.4K subscribers  ·  324 posts  ·  CATEGORY  │
 *   └────────────────────────────────────────────────────────┘
 */
@Composable
fun PremiumChannelHeroHeader(
    channel: Channel,
    isOwner: Boolean,
    channelLevel: Int,
    channelLevelProgress: Float,
    emojiStatus: String?,
    animatedAvatarUrl: String?,
    avatarFrame: AvatarFrameStyle,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onAddMembers: () -> Unit,
    onAvatarClick: () -> Unit,
    onEmojiStatusClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    PremiumTheme {
        val design = PremiumDesign.current
        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(design.colors.backgroundBrush())
                .padding(horizontal = 18.dp)
                .padding(top = 14.dp, bottom = 16.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {

                // ── Top bar ───────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PremiumGlassIconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowBackIosNew,
                            contentDescription = "Back",
                            tint = design.colors.onPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (isOwner) {
                        PremiumGlassIconButton(onClick = onAddMembers, modifier = Modifier.size(40.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.PersonAddAlt,
                                contentDescription = "Add members",
                                tint = design.colors.accent,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        PremiumGlassIconButton(onClick = onSettings, modifier = Modifier.size(40.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = "Settings",
                                tint = design.colors.accent,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        PremiumGlassIconButton(onClick = onSettings, modifier = Modifier.size(40.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = "Info",
                                tint = design.colors.onSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(22.dp))

                // ── Identity row ──────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .offset(y = 2.dp)
                            .clickable(onClick = onAvatarClick),
                    ) {
                        PremiumChannelAvatar(
                            size = 92.dp,
                            imageUrl = channel.avatarUrl,
                            animatedUrl = animatedAvatarUrl,
                            initials = channel.name,
                            frame = avatarFrame,
                            modifier = Modifier.size(92.dp),
                        )
                        PremiumBadge(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = (-2).dp, y = (-2).dp),
                            size = PremiumBadgeSize.Small,
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = channel.name.ifBlank { "—" },
                                style = design.typography.title.copy(color = design.colors.onPrimary),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (channel.isVerified) {
                                PremiumVerifiedMark(size = 16.dp)
                            }
                            if (channel.isPrivate) {
                                PremiumLockMark(size = 14.dp)
                            }
                            PremiumEmojiStatus(
                                emoji = emojiStatus,
                                size = 16.dp,
                                animated = true,
                                onClick = onEmojiStatusClick,
                            )
                        }
                        if (!channel.username.isNullOrBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "@${channel.username}",
                                style = design.typography.caption.copy(color = design.colors.onMuted),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                PremiumDivider()
                Spacer(Modifier.height(12.dp))

                // ── Description ──────────────────────────────────────────
                channel.description?.takeIf { it.isNotBlank() }?.let { desc ->
                    Text(
                        text = desc,
                        style = design.typography.body.copy(color = design.colors.onSecondary),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(12.dp))
                }

                // ── Stats row ────────────────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PremiumLevelIndicatorMicro(level = channelLevel, progress = channelLevelProgress)
                    MetricPill(label = "SUBSCRIBERS", value = formatMetric(channel.subscribersCount))
                    MetricPill(label = "POSTS", value = formatMetric(channel.postsCount))
                    if (!channel.category.isNullOrBlank()) {
                        CategoryChip(channel.category)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricPill(label: String, value: String) {
    val design = PremiumDesign.current
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = value,
            style = design.typography.metric.copy(color = design.colors.onPrimary),
        )
        Text(
            text = label,
            style = design.typography.overline.copy(color = design.colors.onMuted),
        )
    }
}

@Composable
private fun CategoryChip(category: String) {
    val design = PremiumDesign.current
    Box(
        modifier = Modifier
            .background(design.colors.glassFill, design.shapes.pill)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = category.uppercase(),
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

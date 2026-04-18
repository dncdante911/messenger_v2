package com.worldmates.messenger.ui.channels.premium.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Forward
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.worldmates.messenger.R
import com.worldmates.messenger.data.model.ChannelPost
import com.worldmates.messenger.data.model.InlinePostButton
import com.worldmates.messenger.data.model.PostMedia
import com.worldmates.messenger.data.model.PostReaction
import com.worldmates.messenger.ui.channels.formatPostTime
import com.worldmates.messenger.ui.channels.premium.components.AvatarFrameStyle
import com.worldmates.messenger.ui.channels.premium.components.PremiumChannelAvatar
import com.worldmates.messenger.ui.channels.premium.components.PremiumReaction
import com.worldmates.messenger.ui.channels.premium.components.PremiumReactionBar
import com.worldmates.messenger.ui.channels.premium.design.PremiumBrushes
import com.worldmates.messenger.ui.channels.premium.design.PremiumDesign
import com.worldmates.messenger.ui.channels.premium.design.PremiumTheme
import com.worldmates.messenger.ui.channels.premium.design.current
import com.worldmates.messenger.ui.components.formatting.FormattedText
import com.worldmates.messenger.ui.components.formatting.FormattingSettings
import com.worldmates.messenger.ui.messages.components.PollMessageComponent
import com.worldmates.messenger.util.toFullMediaUrl

/**
 * Obsidian Gold post card (Phase 4 redesign).
 *
 * Layout:
 *   ┌──────────────────────────────────────────┐
 *   │ [pinned gold strip]                      │
 *   │  [avatar]  Name ★     12m · [⋯]          │
 *   │            @username / forwarded         │
 *   │  [Media, full-width, no rounded inside]  │
 *   │  Post body — FormattedText               │
 *   │  [reactions bar]                         │
 *   │  ─── gold hairline ───                   │
 *   │  [reply] [comment] [views] [share]       │
 *   │  [inline buttons, gold-hairline]         │
 *   └──────────────────────────────────────────┘
 */
@Composable
fun PremiumPostCardHero(
    post: ChannelPost,
    channelName: String,
    channelAvatarUrl: String?,
    onPostClick: () -> Unit,
    onReactionClick: (String) -> Unit,
    onCommentsClick: () -> Unit,
    onShareClick: () -> Unit,
    onMoreClick: () -> Unit,
    onMediaClick: ((Int) -> Unit)? = null,
    onPollVote: (pollId: Long, optionId: Long) -> Unit = { _, _ -> },
    canEdit: Boolean = false,
    onInlineButtonClick: ((InlinePostButton) -> Unit)? = null,
    avatarFrame: AvatarFrameStyle = AvatarFrameStyle.GoldRing,
    modifier: Modifier = Modifier,
) {
    PremiumTheme {
        val design = PremiumDesign.current
        val hasMedia = !post.media.isNullOrEmpty()
        val hasReactions = post.reactions?.isNotEmpty() == true
        val hasInlineButtons = !post.inlineButtons.isNullOrEmpty()
        val shape = RoundedCornerShape(18.dp)
        val context = LocalContext.current

        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(shape)
                .background(design.colors.surface, shape)
                .border(width = 0.6.dp, brush = PremiumBrushes.goldHairline(), shape = shape),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, color = design.colors.accent),
                        onClick = onPostClick,
                    ),
            ) {
                if (post.isPinned) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PremiumBrushes.matteGoldLinear())
                            .padding(horizontal = 14.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Filled.PushPin,
                            contentDescription = null,
                            tint = design.colors.onAccent,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = stringResource(R.string.channel_detail_pinned),
                            style = design.typography.overline.copy(color = design.colors.onAccent),
                        )
                    }
                }

                // ── Author row ───────────────────────────────────────────────
                val displayAvatar = if (post.isAd) post.authorAvatar else channelAvatarUrl
                val displayName = if (post.isAd) {
                    post.authorName ?: post.authorUsername ?: channelName
                } else {
                    channelName.ifEmpty { post.authorName ?: post.authorUsername ?: "" }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 14.dp,
                            end = 14.dp,
                            top = if (post.isPinned) 10.dp else 14.dp,
                            bottom = if (hasMedia) 10.dp else 8.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PremiumChannelAvatar(
                        size = 36.dp,
                        imageUrl = displayAvatar,
                        initials = displayName.ifEmpty { "?" },
                        frame = avatarFrame,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayName,
                            style = design.typography.bodyStrong.copy(color = design.colors.onPrimary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (post.isAd) {
                            Text(
                                text = stringResource(R.string.post_ad_badge),
                                style = design.typography.overline.copy(color = design.colors.warning),
                            )
                        } else if (post.forwardedFromName != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Forward,
                                    contentDescription = null,
                                    tint = design.colors.accent,
                                    modifier = Modifier.size(11.dp),
                                )
                                Text(
                                    text = stringResource(R.string.post_forwarded_from_fmt, post.forwardedFromName),
                                    style = design.typography.caption.copy(color = design.colors.onMuted),
                                )
                            }
                        } else if (!post.authorUsername.isNullOrBlank() && !post.isAd) {
                            Text(
                                text = "@${post.authorUsername}",
                                style = design.typography.caption.copy(color = design.colors.onMuted),
                            )
                        }
                    }
                    Text(
                        text = formatPostTime(post.createdTime, context),
                        style = design.typography.caption.copy(color = design.colors.onMuted),
                    )
                    if (canEdit) {
                        Spacer(Modifier.width(2.dp))
                        Icon(
                            Icons.Outlined.MoreHoriz,
                            contentDescription = null,
                            tint = design.colors.onMuted,
                            modifier = Modifier
                                .size(22.dp)
                                .clickable(onClick = onMoreClick),
                        )
                    }
                }

                // ── Media ────────────────────────────────────────────────────
                if (hasMedia) {
                    PremiumMediaGalleryV2(
                        media = post.media!!,
                        onMediaClick = onMediaClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // ── Body / poll ──────────────────────────────────────────────
                if (post.poll != null) {
                    Box(
                        modifier = Modifier.padding(
                            start = 14.dp, end = 14.dp,
                            top = if (hasMedia) 12.dp else 2.dp,
                            bottom = 6.dp,
                        ),
                    ) {
                        PollMessageComponent(
                            poll = post.poll,
                            isMine = false,
                            onVote = { optionId -> onPollVote(post.poll.id, optionId) },
                        )
                    }
                } else if (post.text.isNotBlank()) {
                    Box(
                        modifier = Modifier.padding(
                            start = 14.dp, end = 14.dp,
                            top = if (hasMedia) 12.dp else 2.dp,
                            bottom = 6.dp,
                        ),
                    ) {
                        FormattedText(
                            text = post.text,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            color = design.colors.onPrimary,
                            quoteBarColor = design.colors.accent,
                            quoteBackgroundColor = design.colors.glassFill,
                            quoteBarWidth = 3.dp,
                            quoteCornerRadius = 6.dp,
                            settings = FormattingSettings(allowQuotes = true),
                        )
                    }
                }

                if (post.isEdited) {
                    Text(
                        text = stringResource(R.string.post_edited),
                        style = design.typography.caption.copy(color = design.colors.onMuted),
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(start = 14.dp, top = 2.dp),
                    )
                }

                // ── Reactions ────────────────────────────────────────────────
                if (hasReactions) {
                    PremiumReactionBar(
                        reactions = (post.reactions ?: emptyList()).map { it.toPremium() },
                        onReactionClick = { onReactionClick(it.emoji) },
                        onAddReaction = { onReactionClick("+") },
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 6.dp),
                    )
                }

                // ── Action bar ───────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .height(0.6.dp)
                        .background(PremiumBrushes.goldHairline()),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PremiumActionChip(
                        icon = Icons.Outlined.ChatBubbleOutline,
                        label = if (post.commentsCount > 0) formatCount(post.commentsCount) else null,
                        tint = design.colors.onSecondary,
                        onClick = onCommentsClick,
                    )
                    Spacer(Modifier.weight(1f))
                    if (post.viewsCount > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(end = 6.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Visibility,
                                contentDescription = null,
                                tint = design.colors.onMuted,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = formatCount(post.viewsCount),
                                style = design.typography.caption.copy(color = design.colors.onMuted),
                            )
                        }
                    }
                    PremiumActionChip(
                        icon = Icons.Outlined.Share,
                        label = null,
                        tint = design.colors.onSecondary,
                        onClick = onShareClick,
                    )
                }

                if (hasInlineButtons) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        post.inlineButtons!!.forEach { buttonRow ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                buttonRow.forEach { btn ->
                                    PremiumInlineButton(
                                        button = btn,
                                        onClick = { onInlineButtonClick?.invoke(btn) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Action chip ──────────────────────────────────────────────────────────────
@Composable
private fun PremiumActionChip(
    icon: ImageVector,
    label: String?,
    tint: Color,
    onClick: () -> Unit,
) {
    val design = PremiumDesign.current
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = design.colors.accent),
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
        if (label != null) {
            Text(
                text = label,
                style = design.typography.caption.copy(color = tint),
            )
        }
    }
}

// ── Inline button ────────────────────────────────────────────────────────────
@Composable
private fun PremiumInlineButton(
    button: InlinePostButton,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val design = PremiumDesign.current
    val shape = RoundedCornerShape(10.dp)
    val hasUrl = !button.url.isNullOrBlank()
    Box(
        modifier = modifier
            .clip(shape)
            .background(design.colors.glassFill, shape)
            .border(width = 0.6.dp, brush = PremiumBrushes.goldHairline(), shape = shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = button.text,
                style = design.typography.button.copy(color = design.colors.accent),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (hasUrl) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Outlined.OpenInNew,
                    contentDescription = null,
                    tint = design.colors.accent,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

// ── Media gallery V2 ─────────────────────────────────────────────────────────
@Composable
private fun PremiumMediaGalleryV2(
    media: List<PostMedia>,
    onMediaClick: ((Int) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    when (media.size) {
        0 -> Unit
        1 -> PremiumMediaItem(
            media = media[0],
            onClick = { onMediaClick?.invoke(0) },
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp),
        )
        2 -> Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            media.forEachIndexed { i, item ->
                PremiumMediaItem(
                    media = item,
                    onClick = { onMediaClick?.invoke(i) },
                    modifier = Modifier
                        .weight(1f)
                        .height(180.dp),
                )
            }
        }
        else -> Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            PremiumMediaItem(
                media = media[0],
                onClick = { onMediaClick?.invoke(0) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                media.drop(1).take(3).forEachIndexed { i, item ->
                    Box(modifier = Modifier.weight(1f)) {
                        PremiumMediaItem(
                            media = item,
                            onClick = { onMediaClick?.invoke(i + 1) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                        )
                        if (i == 2 && media.size > 4) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .clickable { onMediaClick?.invoke(i + 1) },
                                contentAlignment = Alignment.Center,
                            ) {
                                val d = PremiumDesign.current
                                Text(
                                    text = "+${media.size - 4}",
                                    style = d.typography.title.copy(color = d.colors.onPrimary),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumMediaItem(
    media: PostMedia,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isVideo = media.type == "video"
    Box(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        ),
    ) {
        AsyncImage(
            model = media.url.toFullMediaUrl(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        if (isVideo) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f)),
                contentAlignment = Alignment.Center,
            ) {
                val design = PremiumDesign.current
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(design.colors.accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = design.colors.onAccent,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
}

private fun PostReaction.toPremium() = PremiumReaction(
    emoji = emoji,
    count = count,
    userReacted = userReacted,
)

private fun formatCount(n: Int): String = when {
    n < 1_000 -> n.toString()
    n < 10_000 -> "%.1fK".format(n / 1000f).replace(".0K", "K")
    n < 1_000_000 -> "${n / 1000}K"
    else -> "%.1fM".format(n / 1_000_000f).replace(".0M", "M")
}

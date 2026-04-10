package com.worldmates.messenger.ui.channels

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import com.worldmates.messenger.ui.components.formatting.FormattedText
import com.worldmates.messenger.ui.components.formatting.FormattingSettings
import androidx.compose.foundation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.worldmates.messenger.data.model.Channel
import com.worldmates.messenger.data.model.ChannelPost
import com.worldmates.messenger.data.model.InlinePostButton
import com.worldmates.messenger.data.model.PostMedia
import com.worldmates.messenger.util.toFullMediaUrl
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.res.stringResource
import com.worldmates.messenger.R
import com.worldmates.messenger.ui.messages.components.PollMessageComponent
import com.worldmates.messenger.ui.fonts.AppFonts

/**
 * Premium Channel UI — radically different design.
 * Magazine/editorial style: clean typography, generous whitespace,
 * accent lines instead of cards, full-bleed media, inline actions.
 * All colors from MaterialTheme — follows app palette.
 */

// ==================== PREMIUM CHANNEL HEADER ====================
// Full cover-banner style: large gradient banner, avatar overlapping bottom,
// channel name in Exo2, stats row with Orbitron numbers — nothing like a chat header.

@Composable
fun PremiumChannelHeader(
    channel: Channel,
    onBackClick: () -> Unit,
    onSettingsClick: (() -> Unit)? = null,
    onAvatarClick: (() -> Unit)? = null,
    onSubscribersClick: (() -> Unit)? = null,
    onAddMembersClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val bannerHeight = 190.dp
    val avatarSize = 80.dp
    val avatarOverlap = avatarSize / 2

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        // ── BANNER ─────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(bannerHeight)
        ) {
            // Primary gradient background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                colorScheme.primary.copy(alpha = 0.95f),
                                colorScheme.tertiary.copy(alpha = 0.85f),
                                colorScheme.secondary.copy(alpha = 0.80f)
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                        )
                    )
            )

            // Decorative blurred circles for depth
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .offset(x = (-40).dp, y = (-40).dp)
                    .blur(60.dp)
                    .background(
                        color = colorScheme.onPrimary.copy(alpha = 0.08f),
                        shape = CircleShape
                    )
            )
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 30.dp, y = (-20).dp)
                    .blur(50.dp)
                    .background(
                        color = colorScheme.tertiary.copy(alpha = 0.25f),
                        shape = CircleShape
                    )
            )
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 10.dp, y = 20.dp)
                    .blur(40.dp)
                    .background(
                        color = colorScheme.primary.copy(alpha = 0.3f),
                        shape = CircleShape
                    )
            )

            // Bottom scrim so text is readable
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .align(Alignment.BottomStart)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.55f)
                            )
                        )
                    )
            )

            // ── Floating back button (top-left) ──
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(8.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.28f))
                    .clickable(onClick = onBackClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // ── Floating action buttons (top-right) ──
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = 8.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (onAddMembersClick != null && channel.isAdmin) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.28f))
                            .clickable(onClick = onAddMembersClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PersonAdd,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (onSettingsClick != null && channel.isAdmin) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.28f))
                            .clickable(onClick = onSettingsClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // ── Channel name + username at bottom of banner ──
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = avatarSize + 24.dp, end = 16.dp, bottom = 14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = channel.name,
                        fontFamily = AppFonts.Exo2,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (channel.isVerified) {
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (channel.isPrivate) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                if (channel.username != null) {
                    Text(
                        text = "@${channel.username}",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.75f),
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }

        // ── AVATAR overlapping banner ──────────────────────────────────
        Box(
            modifier = Modifier
                .offset(y = bannerHeight - avatarOverlap)
                .padding(start = 16.dp)
        ) {
            // White ring behind avatar
            Box(
                modifier = Modifier
                    .size(avatarSize + 4.dp)
                    .clip(CircleShape)
                    .background(colorScheme.surface)
            ) {
                val avatarModifier = Modifier
                    .size(avatarSize)
                    .align(Alignment.Center)
                    .clip(CircleShape)

                if (channel.avatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = channel.avatarUrl.toFullMediaUrl(),
                        contentDescription = channel.name,
                        modifier = avatarModifier,
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = avatarModifier
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        colorScheme.primary,
                                        colorScheme.tertiary
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = channel.name.take(2).uppercase(),
                            fontFamily = AppFonts.RussoOne,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // Camera overlay for admin
            if (onAvatarClick != null && channel.isAdmin) {
                Surface(
                    onClick = onAvatarClick,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 2.dp, y = 2.dp)
                        .size(28.dp),
                    shape = CircleShape,
                    color = colorScheme.primary,
                    shadowElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // ── INFO + STATS below banner ──────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = bannerHeight + avatarOverlap - 4.dp,
                    start = 16.dp,
                    end = 16.dp
                )
        ) {
            // Description
            if (!channel.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = channel.description!!,
                    fontSize = 13.sp,
                    color = colorScheme.onSurface.copy(alpha = 0.62f),
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Stats row — numbers in Orbitron, labels in default font
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PremiumBannerStat(
                    value = formatCountPremium(channel.subscribersCount),
                    label = stringResource(R.string.channel_detail_subscribers),
                    onClick = onSubscribersClick
                )
                PremiumBannerStat(
                    value = formatCountPremium(channel.postsCount),
                    label = stringResource(R.string.channel_detail_posts)
                )
                if (!channel.category.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colorScheme.primaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = channel.category!!,
                            fontFamily = AppFonts.Righteous,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                colorScheme.primary.copy(alpha = 0.3f),
                                colorScheme.tertiary.copy(alpha = 0.3f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    }
}

// ── Banner stat block ─────────────────────────────────────────────────────────
@Composable
private fun PremiumBannerStat(
    value: String,
    label: String,
    onClick: (() -> Unit)? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val mod = if (onClick != null) Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = ripple(bounded = false, radius = 40.dp),
        onClick = onClick
    ) else Modifier

    Column(
        modifier = mod,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontFamily = AppFonts.Orbitron,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = colorScheme.primary
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = colorScheme.onSurface.copy(alpha = 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ==================== PREMIUM POST CARD (Editorial magazine style) ====================
// ── PREMIUM POST CARD ──────────────────────────────────────────────────────────
// Each post is an elevated card with consistent author header at the top,
// full-width media clipped by the card, and a clean action bar.
// No flat backgrounds, no left accent bars — proper card-based premium feel.

@Composable
fun PremiumPostCard(
    post: ChannelPost,
    onPostClick: () -> Unit,
    onReactionClick: (String) -> Unit,
    onCommentsClick: () -> Unit,
    onShareClick: () -> Unit,
    onMoreClick: () -> Unit,
    onMediaClick: ((Int) -> Unit)? = null,
    onPollVote: (pollId: Long, optionId: Long) -> Unit = { _, _ -> },
    canEdit: Boolean = false,
    onInlineButtonClick: ((InlinePostButton) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val hasMedia = !post.media.isNullOrEmpty()
    val hasReactions = post.reactions?.isNotEmpty() == true
    val reactionsCount = post.reactions?.sumOf { it.count } ?: 0
    val hasInlineButtons = !post.inlineButtons.isNullOrEmpty()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surface.copy(alpha = 0.93f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true),
                    onClick = onPostClick
                )
        ) {

            // ── PINNED badge ───────────────────────────────────────────────────
            if (post.isPinned) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFFFFAB00).copy(alpha = 0.16f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = null,
                        tint = Color(0xFFFFAB00),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = stringResource(R.string.channel_detail_pinned),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFAB00),
                        letterSpacing = 0.8.sp
                    )
                }
            }

            // ── AUTHOR HEADER (always at top) ──────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 14.dp,
                        end = 14.dp,
                        top = if (post.isPinned) 8.dp else 12.dp,
                        bottom = if (hasMedia) 8.dp else 6.dp
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                PremiumSmallAvatar(
                    avatarUrl = post.authorAvatar ?: "",
                    name = post.authorName ?: post.authorUsername ?: "?",
                    size = 34.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = post.authorName ?: post.authorUsername ?: "User #${post.authorId}",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = AppFonts.Exo2,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = formatPostTime(post.createdTime),
                    fontSize = 11.sp,
                    color = colorScheme.onSurface.copy(alpha = 0.45f)
                )
                if (canEdit) {
                    Icon(
                        Icons.Default.MoreHoriz,
                        contentDescription = null,
                        tint = colorScheme.onSurface.copy(alpha = 0.35f),
                        modifier = Modifier
                            .size(20.dp)
                            .clickable(onClick = onMoreClick)
                    )
                }
            }

            // ── MEDIA (full-width, rounded by card) ────────────────────────────
            if (hasMedia) {
                PremiumMediaGallery(
                    media = post.media!!,
                    onMediaClick = onMediaClick,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── TEXT / POLL ────────────────────────────────────────────────────
            if (post.poll != null) {
                Box(
                    modifier = Modifier.padding(
                        start = 14.dp, end = 14.dp,
                        top = if (hasMedia) 10.dp else 2.dp,
                        bottom = 4.dp
                    )
                ) {
                    PollMessageComponent(
                        poll = post.poll,
                        isMine = false,
                        onVote = { optionId -> onPollVote(post.poll.id, optionId) }
                    )
                }
            } else if (post.text.isNotBlank()) {
                Box(
                    modifier = Modifier.padding(
                        start = 14.dp, end = 14.dp,
                        top = if (hasMedia) 10.dp else 2.dp,
                        bottom = 4.dp
                    )
                ) {
                    FormattedText(
                        text = post.text,
                        fontSize = 15.sp,
                        lineHeight = 23.sp,
                        color = colorScheme.onSurface,
                        quoteBarColor = colorScheme.primary,
                        quoteBackgroundColor = colorScheme.primary.copy(alpha = 0.09f),
                        quoteBarWidth = 4.dp,
                        quoteCornerRadius = 8.dp,
                        settings = FormattingSettings(allowQuotes = true),
                    )
                }
            }

            // ── EDITED label ───────────────────────────────────────────────────
            if (post.isEdited) {
                Text(
                    text = stringResource(R.string.post_edited),
                    fontSize = 11.sp,
                    color = colorScheme.onSurface.copy(alpha = 0.35f),
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(start = 14.dp, top = 2.dp)
                )
            }

            // ── REACTIONS ──────────────────────────────────────────────────────
            if (hasReactions) {
                LazyReactionsRow(
                    reactions = post.reactions ?: emptyList(),
                    onReactionClick = onReactionClick,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 2.dp)
                )
            }

            // ── ACTION BAR ─────────────────────────────────────────────────────
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                thickness = 0.5.dp,
                color = colorScheme.onSurface.copy(alpha = 0.07f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 6.dp, top = 2.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EditorialActionChip(
                    icon = Icons.Outlined.EmojiEmotions,
                    label = if (reactionsCount > 0) formatCountPremium(reactionsCount) else null,
                    isActive = hasReactions,
                    activeColor = colorScheme.primary,
                    onClick = { onReactionClick("👍") }
                )
                EditorialActionChip(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    label = if (post.commentsCount > 0) formatCountPremium(post.commentsCount) else null,
                    isActive = false,
                    activeColor = colorScheme.tertiary,
                    onClick = onCommentsClick
                )
                Spacer(modifier = Modifier.weight(1f))
                if (post.viewsCount > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Visibility,
                            contentDescription = null,
                            tint = colorScheme.onSurface.copy(alpha = 0.32f),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = formatCountPremium(post.viewsCount),
                            fontSize = 11.sp,
                            color = colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                }
                EditorialActionChip(
                    icon = Icons.Outlined.Share,
                    label = null,
                    isActive = false,
                    activeColor = colorScheme.secondary,
                    onClick = onShareClick
                )
            }

            // ── INLINE BUTTONS ─────────────────────────────────────────────────
            if (hasInlineButtons) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    post.inlineButtons!!.forEach { buttonRow ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            buttonRow.forEach { button ->
                                TgInlineButton(
                                    button = button,
                                    onClick = { onInlineButtonClick?.invoke(button) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Editorial action chip (replaces TgActionButton for premium posts) ─────────
@Composable
private fun EditorialActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String?,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    val color = if (isActive) activeColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, radius = 20.dp),
                onClick = onClick
            )
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(17.dp))
        if (label != null) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = color
            )
        }
    }
}

// ==================== TG INLINE BUTTON ====================

@Composable
private fun TgInlineButton(
    button: InlinePostButton,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val hasUrl = !button.url.isNullOrBlank()

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = BorderStroke(
            width = 0.5.dp,
            color = colorScheme.outline.copy(alpha = 0.25f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = button.text,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = AppFonts.Exo2,
                color = colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (hasUrl) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.Default.OpenInNew,
                    contentDescription = null,
                    tint = colorScheme.primary.copy(alpha = 0.6f),
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

// ==================== TG ACTION BUTTON ====================

@Composable
private fun TgActionButton(
    icon: ImageVector,
    count: Int?,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val color = if (isActive) activeColor else colorScheme.onSurface.copy(alpha = 0.4f)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isActive) activeColor.copy(alpha = 0.1f) else Color.Transparent,
        modifier = Modifier.height(34.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
            if (count != null) {
                Text(
                    text = formatCountPremium(count),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = color
                )
            }
        }
    }
}

// ==================== REACTIONS ROW ====================

@Composable
private fun LazyReactionsRow(
    reactions: List<com.worldmates.messenger.data.model.PostReaction>,
    onReactionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        reactions.take(6).forEach { reaction ->
            Surface(
                onClick = { onReactionClick(reaction.emoji) },
                shape = RoundedCornerShape(12.dp),
                color = if (reaction.userReacted)
                    colorScheme.primary.copy(alpha = 0.12f)
                else
                    colorScheme.surfaceVariant.copy(alpha = 0.6f),
                border = if (reaction.userReacted)
                    BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.35f))
                else null
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = reaction.emoji,
                        fontSize = 14.sp
                    )
                    if (reaction.count > 1) {
                        Text(
                            text = reaction.count.toString(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (reaction.userReacted)
                                colorScheme.primary
                            else
                                colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                    }
                }
            }
        }
    }
}

// ==================== PREMIUM SMALL AVATAR ====================

@Composable
fun PremiumSmallAvatar(
    avatarUrl: String,
    name: String,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    if (avatarUrl.isNotBlank()) {
        AsyncImage(
            model = avatarUrl.toFullMediaUrl(),
            contentDescription = name,
            modifier = modifier
                .size(size)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            colorScheme.primary,
                            colorScheme.tertiary
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.take(1).uppercase(),
                fontSize = (size.value / 2.5f).sp,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onPrimary
            )
        }
    }
}

// ==================== PREMIUM SUBSCRIBE BUTTON ====================
// Pill-shaped, animated, uses theme colors

@Composable
fun PremiumSubscribeButton(
    isSubscribed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colorScheme = MaterialTheme.colorScheme
    val bgColor by animateColorAsState(
        targetValue = if (isSubscribed) colorScheme.surfaceVariant
        else colorScheme.primary,
        animationSpec = tween(300),
        label = "subscribe_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSubscribed) colorScheme.onSurfaceVariant
        else colorScheme.onPrimary,
        animationSpec = tween(300),
        label = "subscribe_content"
    )

    Button(
        onClick = onToggle,
        enabled = enabled,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = bgColor,
            contentColor = contentColor
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (isSubscribed) 0.dp else 3.dp
        )
    ) {
        AnimatedContent(
            targetState = isSubscribed,
            transitionSpec = {
                slideInVertically { -it } + fadeIn() togetherWith
                        slideOutVertically { it } + fadeOut()
            },
            label = "subscribe_content"
        ) { subscribed ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    if (subscribed) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (subscribed) stringResource(R.string.channel_detail_subscribed)
                    else stringResource(R.string.channel_detail_subscribe),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ==================== PREMIUM FAB ====================
// Clean Material3 FAB, no pulsing glow — elegant

@Composable
fun PremiumFAB(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        containerColor = colorScheme.primary,
        contentColor = colorScheme.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp
        )
    ) {
        Icon(
            Icons.Default.Edit,
            contentDescription = stringResource(R.string.channel_detail_create_post),
            modifier = Modifier.size(24.dp)
        )
    }
}

// ==================== PREMIUM SECTION HEADER ====================

@Composable
fun PremiumSectionHeader(
    title: String,
    count: Int,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Accent bar
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(18.dp)
                .background(
                    colorScheme.primary,
                    RoundedCornerShape(2.dp)
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = count.toString(),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurface.copy(alpha = 0.4f)
        )
    }
}

// ==================== PREMIUM EMPTY STATE ====================

@Composable
fun PremiumEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = colorScheme.onSurface.copy(alpha = 0.25f),
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )

        if (subtitle != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = colorScheme.onSurface.copy(alpha = 0.4f),
                textAlign = TextAlign.Center
            )
        }

        if (action != null) {
            Spacer(modifier = Modifier.height(16.dp))
            action()
        }
    }
}

// ==================== PREMIUM MENU ITEM ====================

@Composable
fun PremiumMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = iconTint.copy(alpha = 0.1f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ==================== PREMIUM MEDIA GALLERY ====================

@Composable
fun PremiumMediaGallery(
    media: List<PostMedia>,
    onMediaClick: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    when (media.size) {
        1 -> {
            PremiumMediaItem(
                media = media[0],
                onClick = { onMediaClick?.invoke(0) },
                modifier = modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
            )
        }
        2 -> {
            Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                media.forEachIndexed { index, item ->
                    PremiumMediaItem(
                        media = item,
                        onClick = { onMediaClick?.invoke(index) },
                        modifier = Modifier
                            .weight(1f)
                            .height(180.dp)
                    )
                }
            }
        }
        else -> {
            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                PremiumMediaItem(
                    media = media[0],
                    onClick = { onMediaClick?.invoke(0) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    media.drop(1).take(3).forEachIndexed { index, item ->
                        Box(modifier = Modifier.weight(1f)) {
                            PremiumMediaItem(
                                media = item,
                                onClick = { onMediaClick?.invoke(index + 1) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp)
                            )
                            if (index == 2 && media.size > 4) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.5f))
                                        .clickable { onMediaClick?.invoke(index + 1) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "+${media.size - 4}",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
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

@Composable
private fun PremiumMediaItem(
    media: PostMedia,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isVideo = media.type == "video"

    Box(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        )
    ) {
        AsyncImage(
            model = media.url.toFullMediaUrl(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        if (isVideo) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==================== PREMIUM VERIFIED BADGE ====================

@Composable
fun PremiumVerifiedBadge(
    size: Dp = 20.dp,
    modifier: Modifier = Modifier
) {
    Icon(
        Icons.Default.Verified,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier.size(size)
    )
}

// ==================== PREMIUM CHANNEL AVATAR ====================
// Kept for backward compatibility with other screens

@Composable
fun PremiumChannelAvatar(
    avatarUrl: String,
    channelName: String,
    size: Dp = 80.dp,
    isVerified: Boolean = false,
    isAdmin: Boolean = false,
    onAvatarClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size + 6.dp)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            colorScheme.primary,
                            colorScheme.tertiary,
                            colorScheme.secondary,
                            colorScheme.primary
                        )
                    ),
                    shape = CircleShape
                )
                .padding(3.dp)
        ) {
            if (avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = avatarUrl.toFullMediaUrl(),
                    contentDescription = channelName,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    colorScheme.primary,
                                    colorScheme.tertiary
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = channelName.take(2).uppercase(),
                        fontSize = (size.value / 2.5f).sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onPrimary,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        if (isAdmin && onAvatarClick != null) {
            Surface(
                onClick = onAvatarClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(28.dp),
                shape = CircleShape,
                color = colorScheme.primary,
                shadowElevation = 3.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

// ==================== PREMIUM STAT CARD ====================
// Kept for backward compatibility with other screens that may use it

@Composable
fun PremiumStatCard(
    icon: ImageVector,
    value: String,
    label: String,
    color: Color,
    isDarkTheme: Boolean,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.08f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                color = colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

// ==================== UTILITY FUNCTIONS ====================

fun formatCountPremium(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 10_000 -> String.format("%.0fK", count / 1_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}

fun formatPostTime(timestamp: String?): String {
    if (timestamp.isNullOrBlank()) return ""
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val date = inputFormat.parse(timestamp.substringBefore(".")) ?: return timestamp

        val now = Date()
        val diff = now.time - date.time
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        when {
            seconds < 60 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days < 7 -> "${days}d ago"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
        }
    } catch (e: Exception) {
        timestamp.take(10)
    }
}

// ==================== LEGACY COLOR PALETTE ====================
// Kept for backward compatibility with CreateChannelActivity and other screens.
// New code should use MaterialTheme.colorScheme instead.

object PremiumColors {
    val TelegramBlue = Color(0xFF0088CC)
    val TelegramBlueDark = Color(0xFF006699)
    val TelegramBlueLight = Color(0xFF54A9EB)

    val GradientStart = Color(0xFF667eea)
    val GradientMiddle = Color(0xFF764ba2)
    val GradientEnd = Color(0xFFf093fb)

    val PremiumGold = Color(0xFFFFD700)
    val PremiumOrange = Color(0xFFFF8C00)

    val SuccessGreen = Color(0xFF00C853)
    val WarningOrange = Color(0xFFFF9800)
    val ErrorRed = Color(0xFFFF5252)

    val SurfaceLight = Color(0xFFF8F9FA)
    val SurfaceDark = Color(0xFF1E1E1E)
    val CardLight = Color(0xFFFFFFFF)
    val CardDark = Color(0xFF2D2D2D)

    val TextPrimaryLight = Color(0xFF1A1A1A)
    val TextSecondaryLight = Color(0xFF6B7280)
    val TextPrimaryDark = Color(0xFFFFFFFF)
    val TextSecondaryDark = Color(0xFF9CA3AF)
}

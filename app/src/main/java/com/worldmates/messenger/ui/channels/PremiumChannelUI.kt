package com.worldmates.messenger.ui.channels

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.worldmates.messenger.data.model.Channel
import com.worldmates.messenger.data.model.ChannelPost
import com.worldmates.messenger.data.model.PostMedia
import com.worldmates.messenger.util.toFullMediaUrl
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.res.stringResource
import com.worldmates.messenger.R

/**
 * Premium Channel UI — radically different design.
 * Magazine/editorial style: clean typography, generous whitespace,
 * accent lines instead of cards, full-bleed media, inline actions.
 * All colors from MaterialTheme — follows app palette.
 */

// ==================== PREMIUM CHANNEL HEADER ====================
// Toolbar-style header: avatar + info inline, stats as small text, no big cards

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

    Column(modifier = modifier.fillMaxWidth()) {
        // Top bar — slim, functional
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = colorScheme.onSurface
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onAddMembersClick != null && channel.isAdmin) {
                    IconButton(onClick = onAddMembersClick) {
                        Icon(
                            Icons.Default.PersonAdd,
                            contentDescription = null,
                            tint = colorScheme.primary
                        )
                    }
                }
                if (onSettingsClick != null && channel.isAdmin) {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            tint = colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        // Compact horizontal channel info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar — clean, subtle border
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
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
                        .padding(2.dp)
                ) {
                    if (channel.avatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = channel.avatarUrl.toFullMediaUrl(),
                            contentDescription = channel.name,
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
                                text = channel.name.take(2).uppercase(),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onPrimary
                            )
                        }
                    }
                }

                // Camera button for admin
                if (onAvatarClick != null && channel.isAdmin) {
                    Surface(
                        onClick = onAvatarClick,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(24.dp),
                        shape = CircleShape,
                        color = colorScheme.primary,
                        shadowElevation = 2.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = colorScheme.onPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Channel info — name, username, description, stats
            Column(modifier = Modifier.weight(1f)) {
                // Name + verified
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (channel.isVerified) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Username
                if (channel.username != null) {
                    Text(
                        text = "@${channel.username}",
                        fontSize = 13.sp,
                        color = colorScheme.primary.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    )
                }

                // Description
                if (!channel.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = channel.description!!,
                        fontSize = 12.sp,
                        color = colorScheme.onSurface.copy(alpha = 0.55f),
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Inline stats
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PremiumInlineStat(
                        value = formatCountPremium(channel.subscribersCount),
                        label = stringResource(R.string.channel_detail_subscribers),
                        onClick = onSubscribersClick
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 10.dp)
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(colorScheme.onSurface.copy(alpha = 0.2f))
                    )
                    PremiumInlineStat(
                        value = formatCountPremium(channel.postsCount),
                        label = stringResource(R.string.channel_detail_posts)
                    )
                    if (!channel.category.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 10.dp)
                                .size(3.dp)
                                .clip(CircleShape)
                                .background(colorScheme.onSurface.copy(alpha = 0.2f))
                        )
                        Text(
                            text = channel.category!!,
                            fontSize = 11.sp,
                            color = colorScheme.onSurface.copy(alpha = 0.45f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Accent line — thin gradient divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .padding(horizontal = 20.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            colorScheme.primary.copy(alpha = 0.4f),
                            colorScheme.tertiary.copy(alpha = 0.4f),
                            Color.Transparent
                        )
                    ),
                    shape = RoundedCornerShape(1.dp)
                )
        )
    }
}

// ==================== INLINE STAT ====================

@Composable
private fun PremiumInlineStat(
    value: String,
    label: String,
    onClick: (() -> Unit)? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val mod = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(bounded = false, radius = 40.dp),
            onClick = onClick
        )
    } else Modifier

    Row(
        modifier = mod,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onSurface
        )
        Text(
            text = label,
            fontSize = 13.sp,
            color = colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

// ==================== PREMIUM POST CARD ====================
// Full-bleed editorial style: no card borders, accent left bar, clean typography

@Composable
fun PremiumPostCard(
    post: ChannelPost,
    onPostClick: () -> Unit,
    onReactionClick: (String) -> Unit,
    onCommentsClick: () -> Unit,
    onShareClick: () -> Unit,
    onMoreClick: () -> Unit,
    onMediaClick: ((Int) -> Unit)? = null,
    canEdit: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val hasReactions = post.reactions?.isNotEmpty() == true
    val reactionsCount = post.reactions?.sumOf { it.count } ?: 0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = colorScheme.primary.copy(alpha = 0.1f)),
                onClick = onPostClick
            )
    ) {
        // Pinned indicator — subtle top bar
        if (post.isPinned) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colorScheme.primaryContainer.copy(alpha = 0.3f))
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Default.PushPin,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = stringResource(R.string.channel_detail_pinned),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.primary
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Left accent line — thin, uses theme primary
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .padding(vertical = 8.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = if (post.isPinned) {
                                listOf(colorScheme.tertiary, colorScheme.primary)
                            } else {
                                listOf(
                                    colorScheme.primary.copy(alpha = 0.6f),
                                    colorScheme.primary.copy(alpha = 0.1f)
                                )
                            }
                        ),
                        shape = RoundedCornerShape(2.dp)
                    )
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, end = 20.dp, top = 12.dp, bottom = 8.dp)
            ) {
                // Author row — compact
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Small avatar
                    PremiumSmallAvatar(
                        avatarUrl = post.authorAvatar ?: "",
                        name = post.authorName ?: post.authorUsername ?: "User",
                        size = 36.dp
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = post.authorName ?: post.authorUsername ?: "User #${post.authorId}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = formatPostTime(post.createdTime),
                            fontSize = 12.sp,
                            color = colorScheme.onSurface.copy(alpha = 0.45f)
                        )
                    }

                    if (canEdit) {
                        IconButton(
                            onClick = onMoreClick,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreHoriz,
                                contentDescription = null,
                                tint = colorScheme.onSurface.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Media first — full-bleed with rounded corners
                if (!post.media.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    PremiumMediaGallery(
                        media = post.media!!,
                        onMediaClick = onMediaClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                    )
                }

                // Post text below media
                if (post.text.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = post.text,
                        fontSize = 15.sp,
                        color = colorScheme.onSurface,
                        lineHeight = 22.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom action bar — icon-centric, compact
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: views
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Visibility,
                            contentDescription = null,
                            tint = colorScheme.onSurface.copy(alpha = 0.35f),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = formatCountPremium(post.viewsCount),
                            fontSize = 12.sp,
                            color = colorScheme.onSurface.copy(alpha = 0.35f)
                        )
                    }

                    // Right: action buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Reaction
                        PremiumCompactAction(
                            icon = if (hasReactions) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                            count = if (reactionsCount > 0) reactionsCount else null,
                            isActive = hasReactions,
                            activeColor = colorScheme.primary,
                            onClick = { onReactionClick("\uD83D\uDC4D") }
                        )

                        // Comments
                        PremiumCompactAction(
                            icon = Icons.Outlined.ChatBubbleOutline,
                            count = if (post.commentsCount > 0) post.commentsCount else null,
                            isActive = post.commentsCount > 0,
                            activeColor = colorScheme.tertiary,
                            onClick = onCommentsClick
                        )

                        // Share
                        PremiumCompactAction(
                            icon = Icons.Outlined.Share,
                            count = null,
                            isActive = false,
                            activeColor = colorScheme.secondary,
                            onClick = onShareClick
                        )
                    }
                }
            }
        }

        // Subtle separator
        HorizontalDivider(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp),
            color = colorScheme.onSurface.copy(alpha = 0.06f)
        )
    }
}

// ==================== COMPACT ACTION BUTTON ====================

@Composable
private fun PremiumCompactAction(
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
        color = if (isActive) activeColor.copy(alpha = 0.08f) else Color.Transparent,
        modifier = Modifier.height(32.dp)
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

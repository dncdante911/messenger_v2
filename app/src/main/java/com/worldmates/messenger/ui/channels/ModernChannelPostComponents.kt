package com.worldmates.messenger.ui.channels

import com.worldmates.messenger.util.toFullMediaUrl
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.worldmates.messenger.data.model.*
import com.worldmates.messenger.ui.fonts.AppFonts
import com.worldmates.messenger.ui.messages.components.PollMessageComponent
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.res.stringResource
import com.worldmates.messenger.R
import kotlinx.coroutines.launch
import androidx.compose.ui.window.Dialog

// ==================== CHANNEL POST CARD ====================

@Composable
fun ChannelPostCard(
    post: ChannelPost,
    onPostClick: () -> Unit = {},
    onReactionClick: (String) -> Unit = {},
    onCommentsClick: () -> Unit = {},
    onShareClick: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    onMediaClick: ((Int) -> Unit)? = null,
    onPollVote: (pollId: Long, optionId: Long) -> Unit = { _, _ -> },
    canEdit: Boolean = false,
    userKarmaScore: Float? = null,
    userTrustLevel: String? = null,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "post_scale"
    )
    val elevation by animateDpAsState(
        targetValue = if (isPressed) 2.dp else 6.dp,
        animationSpec = tween(200),
        label = "post_elevation"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onPostClick
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = elevation,
            pressedElevation = 2.dp,
            hoveredElevation = 8.dp
        )
    ) {
        // Gradient accent line at top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary,
                            MaterialTheme.colorScheme.secondary
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Post Header - Современный стильный дизайн
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Аватар автора с красивой тенью и border
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                    ) {
                        if (!post.authorAvatar.isNullOrEmpty()) {
                            AsyncImage(
                                model = post.authorAvatar.toFullMediaUrl(),
                                contentDescription = "Author Avatar",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .border(
                                        width = 2.dp,
                                        brush = Brush.linearGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.tertiary
                                            )
                                        ),
                                        shape = CircleShape
                                    ),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            // Placeholder з першою літерою імені автора
                            val authorInitial = (post.authorName?.firstOrNull()
                                ?: post.authorUsername?.firstOrNull()
                                ?: 'U').uppercaseChar().toString()

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .border(
                                        width = 2.dp,
                                        brush = Brush.linearGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.tertiary
                                            )
                                        ),
                                        shape = CircleShape
                                    )
                                    .background(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primaryContainer,
                                                MaterialTheme.colorScheme.primary
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = authorInitial,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = post.authorName ?: post.authorUsername ?: "User #${post.authorId}",
                                fontFamily = AppFonts.Exo2,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                letterSpacing = 0.15.sp
                            )
                            // User karma/trust badge
                            if (userKarmaScore != null && userKarmaScore > 0) {
                                KarmaMiniBadge(score = userKarmaScore)
                            }
                            if (userTrustLevel != null) {
                                TrustMiniBadge(trustLevel = userTrustLevel)
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = formatPostTime(post.createdTime),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Medium
                            )
                            if (post.isEdited) {
                                Text(
                                    text = "•",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                                Text(
                                    text = "edited",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            }
                        }
                    }
                }

                // More options - стильная кнопка
                if (canEdit) {
                    Surface(
                        onClick = onMoreClick,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "More",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Pinned indicator - стильный badge
            if (post.isPinned) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.PushPin,
                                    contentDescription = "Pinned",
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Закріплений пост",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.2.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Poll or regular text content
            if (post.poll != null) {
                PollMessageComponent(
                    poll = post.poll,
                    isMine = false,
                    onVote = { optionId -> onPollVote(post.poll.id, optionId) }
                )
            } else if (post.text.isNotEmpty()) {
                Text(
                    text = post.text,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 24.sp,
                    letterSpacing = 0.15.sp,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            // Media Gallery
            if (!post.media.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                PostMediaGallery(
                    media = post.media!!,
                    onMediaClick = onMediaClick
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Post Stats - стильный badge
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Visibility,
                        contentDescription = "Views",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formatCount(post.viewsCount),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "переглядів",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Display existing reactions - стильные pill badges
            if (!post.reactions.isNullOrEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    post.reactions.forEach { reaction ->
                        Surface(
                            onClick = { onReactionClick(reaction.emoji) },
                            shape = RoundedCornerShape(20.dp),
                            color = if (reaction.userReacted)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(
                                width = if (reaction.userReacted) 2.dp else 1.dp,
                                color = if (reaction.userReacted)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            ),
                            shadowElevation = if (reaction.userReacted) 2.dp else 0.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = reaction.emoji,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = "${reaction.count}",
                                    fontSize = 14.sp,
                                    fontWeight = if (reaction.userReacted) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (reaction.userReacted)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                // Show avatars of first 2 users who reacted
                                if (!reaction.recentUsers.isNullOrEmpty()) {
                                    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                                        reaction.recentUsers.take(2).forEach { user ->
                                            if (!user.avatar.isNullOrEmpty()) {
                                                AsyncImage(
                                                    model = user.avatar,
                                                    contentDescription = user.username,
                                                    modifier = Modifier
                                                        .size(18.dp)
                                                        .clip(CircleShape)
                                                        .border(
                                                            1.5.dp,
                                                            MaterialTheme.colorScheme.surface,
                                                            CircleShape
                                                        ),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            Divider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                thickness = 1.dp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Reactions - стильные кнопки
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf("👍", "❤️", "🔥", "😂", "😮", "😢").forEach { emoji ->
                    Surface(
                        onClick = { onReactionClick(emoji) },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = emoji,
                                fontSize = 22.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Divider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                thickness = 1.dp
            )

            // Action Buttons - стильные современные кнопки
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Comments button
                Surface(
                    onClick = onCommentsClick,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Comment,
                            contentDescription = "Comments",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        if (post.commentsCount > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = formatCount(post.commentsCount),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        } else {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Коментарі",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                // Share button
                Surface(
                    onClick = onShareClick,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Share",
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Поділитись",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }
    }
}

// ==================== POST MEDIA GALLERY ====================

@Composable
fun PostMediaGallery(
    media: List<PostMedia>,
    onMediaClick: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    when (media.size) {
        1 -> {
            // Single media
            PostMediaItem(
                media = media[0],
                onClick = { onMediaClick?.invoke(0) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        }
        2 -> {
            // Two media side by side
            Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PostMediaItem(
                    media = media[0],
                    onClick = { onMediaClick?.invoke(0) },
                    modifier = Modifier
                        .weight(1f)
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                PostMediaItem(
                    media = media[1],
                    onClick = { onMediaClick?.invoke(1) },
                    modifier = Modifier
                        .weight(1f)
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            }
        }
        else -> {
            // Grid for 3+ media
            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PostMediaItem(
                        media = media[0],
                        onClick = { onMediaClick?.invoke(0) },
                        modifier = Modifier
                            .weight(1f)
                            .height(150.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    PostMediaItem(
                        media = media[1],
                        onClick = { onMediaClick?.invoke(1) },
                        modifier = Modifier
                            .weight(1f)
                            .height(150.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
                if (media.size > 2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        PostMediaItem(
                            media = media[2],
                            onClick = { onMediaClick?.invoke(2) },
                            modifier = Modifier
                                .weight(1f)
                                .height(150.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        if (media.size > 3) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(150.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            ) {
                                PostMediaItem(
                                    media = media[3],
                                    onClick = { onMediaClick?.invoke(3) },
                                    modifier = Modifier.fillMaxSize()
                                )
                                if (media.size > 4) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.6f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "+${media.size - 4}",
                                            fontSize = 24.sp,
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
}

@Composable
fun PostMediaItem(
    media: PostMedia,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        )
    ) {
        when (media.type) {
            "image" -> {
                AsyncImage(
                    model = media.url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            "video" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1A1A2E)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.White.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PlayCircle,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }
            else -> {
                // Placeholder for other media types
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFF5F5F5)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

// ==================== POST REACTIONS BAR ====================

@Composable
fun PostReactionsBar(
    reactions: List<PostReaction>,
    onReactionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        reactions.forEach { reaction ->
            ReactionChip(
                emoji = reaction.emoji,
                count = reaction.count,
                isSelected = reaction.userReacted,
                onClick = { onReactionClick(reaction.emoji) }
            )
        }
    }
}

@Composable
fun ReactionChip(
    emoji: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceVariant,
        border = if (isSelected)
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        else
            null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = emoji,
                fontSize = 16.sp
            )
            Text(
                text = count.toString(),
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ==================== COMMENT CARD ====================

@Composable
fun CommentCard(
    comment: ChannelComment,
    onReactionClick: (String) -> Unit = {},
    onReplyClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    canDelete: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.Top
            ) {
                // Author Avatar
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "U",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Author name and time
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "User",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = formatPostTime(comment.time),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    // Comment text
                    Text(
                        text = comment.text,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    // Actions
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        TextButton(
                            onClick = { onReactionClick("") },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                "Реакція",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        TextButton(
                            onClick = onReplyClick,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                "Відповісти",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            if (canDelete) {
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SmallReactionChip(
    emoji: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(text = emoji, fontSize = 12.sp)
            Text(
                text = count.toString(),
                fontSize = 11.sp,
                color = if (isSelected)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ==================== ACTION BUTTON ====================

@Composable
fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

// ==================== UTILITY FUNCTIONS ====================

fun formatPostTime(timestamp: Long): String {
    // Конвертуємо timestamp з секунд в мілісекунди, якщо потрібно
    val timestampMs = if (timestamp < 10000000000L) timestamp * 1000 else timestamp

    val now = System.currentTimeMillis()
    val diff = now - timestampMs

    return when {
        diff < 60_000 -> "Щойно"
        diff < 3_600_000 -> "${diff / 60_000} хв"
        diff < 86_400_000 -> "${diff / 3_600_000} год"
        diff < 604_800_000 -> "${diff / 86_400_000} д"
        else -> {
            val sdf = SimpleDateFormat("dd MMM", Locale("uk"))
            sdf.format(Date(timestampMs))
        }
    }
}

fun formatDuration(seconds: Long): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return String.format("%d:%02d", minutes, secs)
}

// ==================== COMMENTS COMPONENTS ====================

/**
 * Bottom sheet для відображення коментарів
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsBottomSheet(
    post: ChannelPost?,
    comments: List<ChannelComment>,
    isLoading: Boolean,
    currentUserId: Long,
    isAdmin: Boolean,
    onDismiss: () -> Unit,
    onAddComment: (String) -> Unit,
    onAddCommentWithReply: ((String, Long?) -> Unit)? = null,
    onDeleteComment: (Long) -> Unit,
    onCommentReaction: (commentId: Long, emoji: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var commentText by remember { mutableStateOf("") }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showGifPicker by remember { mutableStateOf(false) }
    var showStrapiPicker by remember { mutableStateOf(false) }
    var activePickerTab by remember { mutableStateOf<String?>(null) }
    var replyingToComment by remember { mutableStateOf<ChannelComment?>(null) }
    var userActionsComment by remember { mutableStateOf<ChannelComment?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            // Compact drag handle
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 4.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Compact header with gradient accent
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Comments",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (comments.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "${comments.size}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Comments list
            if (isLoading) {
                // Shimmer loading
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 150.dp)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    repeat(3) {
                        Row {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant
                                            .copy(alpha = 0.6f)
                                    )
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Box(
                                    modifier = Modifier
                                        .width(100.dp)
                                        .height(12.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant
                                                .copy(alpha = 0.6f)
                                        )
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.8f)
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant
                                                .copy(alpha = 0.4f)
                                        )
                                )
                            }
                        }
                    }
                }
            } else if (comments.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.ChatBubbleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No comments yet",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Be the first to share your thoughts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(comments) { comment ->
                        PremiumCommentItem(
                            comment = comment,
                            canDelete = isAdmin || comment.userId == currentUserId,
                            onDeleteClick = { onDeleteComment(comment.id) },
                            onReactionClick = { emoji -> onCommentReaction(comment.id, emoji) },
                            onReply = { replyingToComment = it },
                            onUserMenu = { userActionsComment = it },
                            replyToComment = comment.replyToCommentId?.let { rid ->
                                comments.find { it.id == rid }
                            }
                        )
                    }
                }
            }

            // Reply banner
            androidx.compose.animation.AnimatedVisibility(
                visible = replyingToComment != null,
                enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
            ) {
                replyingToComment?.let { replying ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Reply,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = replying.userName ?: replying.username ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = replying.text,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { replyingToComment = null },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Input area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )

                // Quick picker tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CommentPickerChip(
                        icon = Icons.Outlined.EmojiEmotions,
                        label = "Stickers",
                        isActive = activePickerTab == "strapi",
                        onClick = {
                            activePickerTab = if (activePickerTab == "strapi") null else "strapi"
                            showStrapiPicker = activePickerTab == "strapi"
                            showEmojiPicker = false
                            showGifPicker = false
                        }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    CommentPickerChip(
                        icon = Icons.Outlined.Mood,
                        label = "Emoji",
                        isActive = activePickerTab == "emoji",
                        onClick = {
                            activePickerTab = if (activePickerTab == "emoji") null else "emoji"
                            showEmojiPicker = activePickerTab == "emoji"
                            showStrapiPicker = false
                            showGifPicker = false
                        }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    CommentPickerChip(
                        icon = Icons.Outlined.Gif,
                        label = "GIF",
                        isActive = activePickerTab == "gif",
                        onClick = {
                            activePickerTab = if (activePickerTab == "gif") null else "gif"
                            showGifPicker = activePickerTab == "gif"
                            showEmojiPicker = false
                            showStrapiPicker = false
                        }
                    )
                }

                // Main input row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp, max = 120.dp),
                        placeholder = {
                            Text(
                                "Write a comment...",
                                fontSize = 14.sp
                            )
                        },
                        maxLines = 4,
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Send button
                    val sendEnabled = commentText.isNotBlank()
                    Surface(
                        onClick = {
                            if (sendEnabled) {
                                if (onAddCommentWithReply != null) {
                                    onAddCommentWithReply(commentText, replyingToComment?.id)
                                } else {
                                    onAddComment(commentText)
                                }
                                commentText = ""
                                replyingToComment = null
                            }
                        },
                        enabled = sendEnabled,
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        color = if (sendEnabled)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send",
                                tint = if (sendEnabled)
                                    Color.White
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Pickers
                AnimatedVisibility(visible = showEmojiPicker) {
                    com.worldmates.messenger.ui.components.EmojiPicker(
                        onEmojiSelected = { emoji -> commentText += emoji },
                        onDismiss = {
                            showEmojiPicker = false
                            activePickerTab = null
                        }
                    )
                }

                AnimatedVisibility(visible = showGifPicker) {
                    com.worldmates.messenger.ui.components.GifPicker(
                        onGifSelected = { gifUrl ->
                            commentText += "\n[GIF]($gifUrl)"
                            showGifPicker = false
                            activePickerTab = null
                        },
                        onDismiss = {
                            showGifPicker = false
                            activePickerTab = null
                        }
                    )
                }

                AnimatedVisibility(visible = showStrapiPicker) {
                    com.worldmates.messenger.ui.strapi.StrapiContentPicker(
                        onItemSelected = { contentUrl ->
                            commentText += "\n[Sticker]($contentUrl)"
                            showStrapiPicker = false
                            activePickerTab = null
                        },
                        onDismiss = {
                            showStrapiPicker = false
                            activePickerTab = null
                        }
                    )
                }
            }
        }
    }

    // User actions sheet for comment author
    userActionsComment?.let { target ->
        com.worldmates.messenger.ui.components.CommentUserActionsSheet(
            userId = target.userId,
            username = target.userName ?: target.username ?: "User",
            avatar = target.userAvatar,
            isOwnComment = target.userId == currentUserId,
            isAdmin = isAdmin,
            context = "channel",
            commentText = target.text,
            onDismiss = { userActionsComment = null },
            onAction = { action ->
                when (action) {
                    is com.worldmates.messenger.ui.components.CommentUserAction.Reply ->
                        replyingToComment = target
                    is com.worldmates.messenger.ui.components.CommentUserAction.Mention ->
                        commentText = "@${target.username ?: ""} $commentText"
                    else -> { /* caller handles ViewProfile, Follow, Block, Ban etc. */ }
                }
            }
        )
    }
}

@Composable
private fun CommentPickerChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isActive)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else
            Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Компонент для відображення контенту коментаря (текст, стікери, GIF)
 */
@Composable
fun CommentContent(text: String) {
    // Парсимо markdown формат [Текст](URL)
    val markdownRegex = """\[(.*?)\]\((.*?)\)""".toRegex()
    val matches = markdownRegex.findAll(text).toList()

    if (matches.isEmpty()) {
        // Звичайний текст без markdown
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            var lastIndex = 0
            matches.forEach { match ->
                // Текст перед markdown
                if (match.range.first > lastIndex) {
                    val beforeText = text.substring(lastIndex, match.range.first)
                    if (beforeText.isNotBlank()) {
                        Text(
                            text = beforeText,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Відображаємо стікер/GIF/Lottie анімацію
                val label = match.groupValues[1]
                val url = match.groupValues[2]

                when {
                    // Telegram stickers (.tgs - Lottie animations)
                    url.matches(""".*\.tgs$""".toRegex(RegexOption.IGNORE_CASE)) -> {
                        com.airbnb.lottie.compose.LottieAnimation(
                            composition = com.airbnb.lottie.compose.rememberLottieComposition(
                                com.airbnb.lottie.compose.LottieCompositionSpec.Url(url)
                            ).value,
                            iterations = com.airbnb.lottie.compose.LottieConstants.IterateForever,
                            modifier = Modifier
                                .size(150.dp)
                        )
                    }
                    // Звичайні зображення (GIF, PNG, JPG, WEBP, SVG)
                    url.matches(""".*\.(gif|jpg|jpeg|png|webp|svg)$""".toRegex(RegexOption.IGNORE_CASE)) -> {
                        AsyncImage(
                            model = url,
                            contentDescription = label,
                            modifier = Modifier
                                .heightIn(max = 200.dp)
                                .widthIn(max = 200.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                    // WebM відео — відтворюємо через ExoPlayer (підтримує WebM/VP8/VP9)
                    url.matches(""".*\.webm$""".toRegex(RegexOption.IGNORE_CASE)) -> {
                        com.worldmates.messenger.ui.media.InlineVideoPlayer(
                            videoUrl = url,
                            modifier = Modifier
                                .heightIn(max = 200.dp)
                                .widthIn(max = 300.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                    else -> {
                        // Якщо не медіа, показуємо як текст-посилання
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline
                        )
                    }
                }

                lastIndex = match.range.last + 1
            }

            // Текст після останнього markdown
            if (lastIndex < text.length) {
                val afterText = text.substring(lastIndex)
                if (afterText.isNotBlank()) {
                    Text(
                        text = afterText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

/**
 * Premium comment item with bubble style and compact reactions
 */
@Composable
fun PremiumCommentItem(
    comment: ChannelComment,
    canDelete: Boolean,
    onDeleteClick: () -> Unit,
    onReactionClick: (String) -> Unit = {},
    onReply: ((ChannelComment) -> Unit)? = null,
    onUserMenu: ((ChannelComment) -> Unit)? = null,
    replyToComment: ChannelComment? = null,
    modifier: Modifier = Modifier
) {
    var showActions by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { showActions = !showActions }
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        // Compact avatar
        if (!comment.userAvatar.isNullOrEmpty()) {
            AsyncImage(
                model = comment.userAvatar.toFullMediaUrl(),
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (comment.userName ?: comment.username ?: "U")
                        .take(1).uppercase(),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Name + time row
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = comment.userName ?: comment.username ?: "User #${comment.userId}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = formatPostTime(comment.time),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Reply quote (if this is a reply)
            if (replyToComment != null) {
                com.worldmates.messenger.ui.components.CommentReplyQuote(
                    replyToUsername = replyToComment.userName
                        ?: replyToComment.username
                        ?: "User",
                    replyToText = replyToComment.text,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            // Comment text
            CommentContent(text = comment.text)

            // Compact reactions row
            if (comment.reactionsCount > 0 || showActions) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("👍", "❤️", "😂").forEach { emoji ->
                        Surface(
                            onClick = { onReactionClick(emoji) },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Text(text = emoji, fontSize = 13.sp)
                            }
                        }
                    }
                    if (comment.reactionsCount > 0) {
                        Text(
                            text = "${comment.reactionsCount}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Actions: 3-dot menu + delete (only when showActions)
        if (showActions) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (canDelete) {
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                IconButton(
                    onClick = { onUserMenu?.invoke(comment) },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// Keep backward compatibility alias
@Composable
fun CommentItem(
    comment: ChannelComment,
    canDelete: Boolean,
    onDeleteClick: () -> Unit,
    onReactionClick: (String) -> Unit = {},
    onReply: ((ChannelComment) -> Unit)? = null,
    onUserMenu: ((ChannelComment) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    PremiumCommentItem(
        comment = comment,
        canDelete = canDelete,
        onDeleteClick = onDeleteClick,
        onReactionClick = onReactionClick,
        onReply = onReply,
        onUserMenu = onUserMenu,
        modifier = modifier
    )
}

// ==================== POST OPTIONS COMPONENTS ====================

/**
 * Bottom sheet з опціями для поста (Pin, Edit, Delete)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostOptionsBottomSheet(
    post: ChannelPost,
    onDismiss: () -> Unit,
    onPinClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            // Handle bar is already provided by ModalBottomSheet

            // Header with post preview
            Text(
                text = "Post Options",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            if (post.text.isNotBlank()) {
                Text(
                    text = post.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Pin/Unpin
            PostOptionItem(
                icon = Icons.Default.PushPin,
                text = if (post.isPinned) "Unpin Post" else "Pin Post",
                subtitle = if (post.isPinned) "Remove from top" else "Show at the top",
                iconTint = MaterialTheme.colorScheme.primary,
                onClick = {
                    onPinClick()
                    onDismiss()
                }
            )

            // Edit
            PostOptionItem(
                icon = Icons.Default.Edit,
                text = "Edit Post",
                subtitle = "Change text or media",
                iconTint = Color(0xFFFF9800),
                onClick = {
                    onEditClick()
                    onDismiss()
                }
            )

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            Spacer(modifier = Modifier.height(4.dp))

            // Delete
            PostOptionItem(
                icon = Icons.Default.Delete,
                text = "Delete Post",
                subtitle = "This action cannot be undone",
                iconTint = MaterialTheme.colorScheme.error,
                textColor = MaterialTheme.colorScheme.error,
                onClick = {
                    onDeleteClick()
                    onDismiss()
                }
            )
        }
    }
}

/**
 * Premium post option item with icon background and subtitle
 */
@Composable
fun PostOptionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    subtitle: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    textColor: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = iconTint.copy(alpha = 0.12f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = text,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (textColor != Color.Unspecified) textColor
                        else MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

/**
 * Діалог для редагування поста
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPostDialog(
    post: ChannelPost,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var editedText by remember { mutableStateOf(post.text) }
    val charCount = editedText.length
    val hasChanges = editedText.isNotBlank() && editedText != post.text

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Edit Post",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = editedText,
                    onValueChange = { editedText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 150.dp, max = 300.dp),
                    placeholder = { Text("Post text...") },
                    maxLines = 10,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$charCount characters",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                    if (hasChanges) {
                        Text(
                            text = "Modified",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (editedText.isNotBlank()) {
                        onSave(editedText)
                    }
                },
                enabled = hasChanges,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ==================== STATISTICS DIALOG ====================

/**
 * Діалог статистики каналу
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsDialog(
    statistics: ChannelStatistics?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.BarChart,
                    contentDescription = null,
                    tint = Color(0xFF00C853),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Channel Statistics", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            if (statistics == null) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Stats grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatCard(
                            value = "${statistics.subscribersCount}",
                            label = "Subscribers",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            value = "${statistics.postsCount}",
                            label = "Posts",
                            color = Color(0xFF00C853),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatCard(
                            value = "${statistics.postsLastWeek}",
                            label = "This week",
                            color = Color(0xFFFF9800),
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            value = "${statistics.activeSubscribers24h}",
                            label = "Active 24h",
                            color = Color(0xFF9C27B0),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (!statistics.topPosts.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Top Posts",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        statistics.topPosts.take(3).forEachIndexed { index, topPost ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = topPost.text.take(40) + if (topPost.text.length > 40) "..." else "",
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Outlined.Visibility,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "${topPost.views}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun StatCard(
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

// ==================== ADMIN MANAGEMENT ====================

/**
 * Діалог управління адмінами
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageAdminsDialog(
    admins: List<ChannelAdmin>,
    onDismiss: () -> Unit,
    onAddAdmin: (String, String) -> Unit,
    onRemoveAdmin: (Long) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AdminPanelSettings,
                    contentDescription = null,
                    tint = Color(0xFFFF9800),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Administrators", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "${admins.size}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                items(admins) { admin ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Role icon
                        val roleColor = when (admin.role) {
                            "owner" -> Color(0xFFFFD700)
                            "admin" -> MaterialTheme.colorScheme.primary
                            else -> Color(0xFF00C853)
                        }
                        Surface(
                            shape = CircleShape,
                            color = roleColor.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    when (admin.role) {
                                        "owner" -> Icons.Default.Star
                                        "admin" -> Icons.Default.Shield
                                        else -> Icons.Default.Person
                                    },
                                    contentDescription = null,
                                    tint = roleColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = admin.username.takeIf { !it.isNullOrEmpty() } ?: "User #${admin.userId}",
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                            Text(
                                when(admin.role) {
                                    "owner" -> "Owner"
                                    "admin" -> "Administrator"
                                    "moderator" -> "Moderator"
                                    else -> admin.role
                                },
                                fontSize = 12.sp,
                                color = roleColor
                            )
                        }
                        if (admin.role != "owner") {
                            IconButton(onClick = { onRemoveAdmin(admin.userId) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Admin")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )

    if (showAddDialog) {
        AddAdminDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { searchText, role ->
                onAddAdmin(searchText, role)
                showAddDialog = false
            }
        )
    }
}

/**
 * Діалог додавання адміна
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAdminDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    var searchText by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("admin") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.PersonAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Add Administrator", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = { Text("User") },
                    placeholder = { Text("Enter ID, @username or name...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )

                Text(
                    "Role",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedRole == "admin",
                        onClick = { selectedRole = "admin" },
                        label = { Text("Admin") },
                        leadingIcon = if (selectedRole == "admin") {
                            { Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    FilterChip(
                        selected = selectedRole == "moderator",
                        onClick = { selectedRole = "moderator" },
                        label = { Text("Moderator") },
                        leadingIcon = if (selectedRole == "moderator") {
                            { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF00C853).copy(alpha = 0.15f),
                            selectedLabelColor = Color(0xFF00C853)
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (searchText.isNotBlank()) {
                        onAdd(searchText.trim(), selectedRole)
                    }
                },
                enabled = searchText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ==================== EDIT CHANNEL INFO DIALOG ====================

/**
 * Діалог редагування інформації про канал
 */
@Composable
fun EditChannelInfoDialog(
    channel: Channel,
    onDismiss: () -> Unit,
    onSave: (name: String, description: String, username: String) -> Unit
) {
    var channelName by remember { mutableStateOf(channel.name) }
    var channelDescription by remember { mutableStateOf(channel.description ?: "") }
    var channelUsername by remember { mutableStateOf(channel.username ?: "") }
    var usernameError by remember { mutableStateOf<String?>(null) }

    val hasChanges = channelName != channel.name ||
        channelDescription != (channel.description ?: "") ||
        channelUsername != (channel.username ?: "")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Edit Channel",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Channel name
                OutlinedTextField(
                    value = channelName,
                    onValueChange = { channelName = it },
                    label = { Text("Channel Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    leadingIcon = {
                        Icon(Icons.Default.Title, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )

                // Username
                OutlinedTextField(
                    value = channelUsername,
                    onValueChange = {
                        val cleaned = it.trim().lowercase()
                        channelUsername = cleaned
                        usernameError = when {
                            cleaned.isEmpty() -> null
                            !cleaned.matches(Regex("^[a-z0-9_]+$")) -> "Only letters, numbers and _"
                            cleaned.length < 5 -> "Minimum 5 characters"
                            else -> null
                        }
                    },
                    label = { Text("Username") },
                    placeholder = { Text("channel_name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = usernameError != null,
                    shape = RoundedCornerShape(14.dp),
                    supportingText = {
                        if (usernameError != null) {
                            Text(usernameError!!, color = MaterialTheme.colorScheme.error)
                        }
                    },
                    leadingIcon = {
                        Icon(Icons.Default.AlternateEmail, contentDescription = null, tint = Color(0xFFFF9800))
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )

                // Description
                OutlinedTextField(
                    value = channelDescription,
                    onValueChange = { channelDescription = it },
                    label = { Text("Description") },
                    placeholder = { Text("Tell about your channel...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp),
                    maxLines = 4,
                    shape = RoundedCornerShape(14.dp),
                    leadingIcon = {
                        Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFF00C853))
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (channelName.isNotBlank() && usernameError == null) {
                        onSave(
                            channelName.trim(),
                            channelDescription.trim(),
                            channelUsername.trim()
                        )
                    }
                },
                enabled = channelName.isNotBlank() && usernameError == null && hasChanges,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ==================== CHANNEL SETTINGS DIALOG ====================

/**
 * Діалог налаштувань каналу
 */
@Composable
fun ChannelSettingsDialog(
    currentSettings: ChannelSettings?,
    onDismiss: () -> Unit,
    onSave: (ChannelSettings) -> Unit
) {
    // Якщо налаштування ще не завантажені, використовуємо дефолтні
    val defaultSettings = currentSettings ?: ChannelSettings()

    var allowComments by remember { mutableStateOf(defaultSettings.allowComments) }
    var allowReactions by remember { mutableStateOf(defaultSettings.allowReactions) }
    var allowShares by remember { mutableStateOf(defaultSettings.allowShares) }
    var showStatistics by remember { mutableStateOf(defaultSettings.showStatistics) }
    var notifySubscribers by remember { mutableStateOf(defaultSettings.notifySubscribersNewPost) }
    var signatureEnabled by remember { mutableStateOf(defaultSettings.signatureEnabled) }
    var commentsModeration by remember { mutableStateOf(defaultSettings.commentsModeration) }
    var slowModeSeconds by remember { mutableStateOf(defaultSettings.slowModeSeconds?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Channel Settings",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "POSTS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                // Підпис автора
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Author Signature", fontSize = 14.sp)
                        Text(
                            "Show author name on posts",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = signatureEnabled,
                        onCheckedChange = { signatureEnabled = it }
                    )
                }

                Divider()

                Text(
                    text = "INTERACTIVITY",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                // Коментарі
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Allow Comments", fontSize = 14.sp)
                        Text(
                            "Subscribers can comment on posts",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = allowComments,
                        onCheckedChange = { allowComments = it }
                    )
                }

                // Реакції
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Allow Reactions", fontSize = 14.sp)
                        Text(
                            "Subscribers can add reactions",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = allowReactions,
                        onCheckedChange = { allowReactions = it }
                    )
                }

                // Поширення
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Allow Sharing", fontSize = 14.sp)
                        Text(
                            "Allow reposting channel posts",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = allowShares,
                        onCheckedChange = { allowShares = it }
                    )
                }

                Divider()

                Text(
                    text = "MODERATION",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                // Модерація коментарів
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Comment Moderation", fontSize = 14.sp)
                        Text(
                            "Comments require approval",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = commentsModeration,
                        onCheckedChange = { commentsModeration = it }
                    )
                }

                // Повільний режим
                OutlinedTextField(
                    value = slowModeSeconds,
                    onValueChange = {
                        if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                            slowModeSeconds = it
                        }
                    },
                    label = { Text("Slow Mode (seconds)") },
                    placeholder = { Text("0 = disabled") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text(
                            "Delay between comments (0-300 sec)",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                )

                Divider()

                Text(
                    text = "NOTIFICATIONS & ANALYTICS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                // Сповіщення про нові пости
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Post Notifications", fontSize = 14.sp)
                        Text(
                            "Send push notifications to subscribers",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = notifySubscribers,
                        onCheckedChange = { notifySubscribers = it }
                    )
                }

                // Показувати статистику
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Show Statistics", fontSize = 14.sp)
                        Text(
                            "Subscribers can see channel stats",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = showStatistics,
                        onCheckedChange = { showStatistics = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val slowMode = slowModeSeconds.toIntOrNull()
                    val validSlowMode = when {
                        slowMode == null || slowMode < 0 -> null
                        slowMode > 300 -> 300
                        else -> slowMode
                    }

                    val updatedSettings = ChannelSettings(
                        allowComments = allowComments,
                        allowReactions = allowReactions,
                        allowShares = allowShares,
                        showStatistics = showStatistics,
                        showViewsCount = defaultSettings.showViewsCount,
                        notifySubscribersNewPost = notifySubscribers,
                        autoDeletePostsDays = defaultSettings.autoDeletePostsDays,
                        signatureEnabled = signatureEnabled,
                        commentsModeration = commentsModeration,
                        allowForwarding = defaultSettings.allowForwarding,
                        slowModeSeconds = validSlowMode
                    )
                    onSave(updatedSettings)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Діалог детального перегляду поста — повноекранний, з медіа-переглядачами та Telegram-style коментарями
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailDialog(
    post: ChannelPost,
    comments: List<ChannelComment>,
    isLoadingComments: Boolean,
    currentUserId: Long,
    isAdmin: Boolean,
    onDismiss: () -> Unit,
    onReactionClick: (String) -> Unit,
    onAddComment: (String) -> Unit,
    onAddCommentWithReply: ((String, Long?) -> Unit)? = null,
    onDeleteComment: (Long) -> Unit,
    onCommentReaction: (commentId: Long, emoji: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var commentText by remember { mutableStateOf("") }

    // Picker states
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showGifPicker by remember { mutableStateOf(false) }
    var showStrapiPicker by remember { mutableStateOf(false) }
    var activePickerTab by remember { mutableStateOf<String?>(null) }

    // Media viewer states
    var showMediaViewer by remember { mutableStateOf(false) }
    var mediaViewerPage by remember { mutableStateOf(0) }
    val imageMediaUrls = remember(post.media) {
        post.media
            ?.filter { it.type == "image" || it.type.isNullOrBlank() }
            ?.mapNotNull { it.url.toFullMediaUrl() }
            ?: emptyList()
    }

    // Video player state
    var showVideoPlayer by remember { mutableStateOf(false) }
    var videoPlayerUrl by remember { mutableStateOf("") }
    var replyingToCommentDetail by remember { mutableStateOf<ChannelComment?>(null) }
    var userActionsCommentDetail by remember { mutableStateOf<ChannelComment?>(null) }

    // Voice recording states
    val voiceRecorder = remember { com.worldmates.messenger.utils.VoiceRecorder(context) }
    val recordingState by voiceRecorder.recordingState.collectAsState()
    val recordingDuration by voiceRecorder.recordingDuration.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val isRecording = recordingState is com.worldmates.messenger.utils.VoiceRecorder.RecordingState.Recording

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ── Top bar ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.post_detail_close),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = stringResource(R.string.post_detail_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // ── Scrollable content ──
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    // Author info
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!post.authorAvatar.isNullOrEmpty()) {
                                AsyncImage(
                                    model = post.authorAvatar.toFullMediaUrl(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(
                                                    MaterialTheme.colorScheme.primary,
                                                    MaterialTheme.colorScheme.tertiary
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = post.authorName ?: post.authorUsername
                                        ?: String.format(stringResource(R.string.post_detail_user_fallback), post.authorId),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = formatPostTime(post.createdTime),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Media (images, video, audio)
                    if (!post.media.isNullOrEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                var imageIndex = 0
                                post.media?.forEach { media ->
                                    when (media.type) {
                                        "image", null, "" -> {
                                            val currentImageIdx = imageIndex
                                            AsyncImage(
                                                model = media.url.toFullMediaUrl().orEmpty(),
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(max = 280.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .clickable {
                                                        mediaViewerPage = currentImageIdx.coerceIn(0, (imageMediaUrls.size - 1).coerceAtLeast(0))
                                                        showMediaViewer = true
                                                    },
                                                contentScale = ContentScale.Crop
                                            )
                                            imageIndex++
                                        }
                                        "video" -> {
                                            com.worldmates.messenger.ui.media.InlineVideoPlayer(
                                                videoUrl = media.url.toFullMediaUrl().orEmpty(),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(220.dp)
                                                    .clip(RoundedCornerShape(12.dp)),
                                                onFullscreenClick = {
                                                    videoPlayerUrl = media.url.toFullMediaUrl().orEmpty()
                                                    showVideoPlayer = true
                                                }
                                            )
                                        }
                                        "audio" -> {
                                            var showAudioPlayer by remember { mutableStateOf(false) }
                                            Surface(
                                                onClick = { showAudioPlayer = true },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(48.dp),
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.PlayCircle,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(28.dp)
                                                    )
                                                    Text(
                                                        text = "Audio",
                                                        fontSize = 14.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                            if (showAudioPlayer) {
                                                com.worldmates.messenger.ui.media.SimpleAudioPlayer(
                                                    audioUrl = media.url.toFullMediaUrl().orEmpty(),
                                                    onDismiss = { showAudioPlayer = false }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Post text
                    item {
                        if (post.text.isNotBlank()) {
                            Text(
                                text = post.text,
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                    }

                    // Inline stats + reactions row
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Stats chips
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = "${post.viewsCount}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.FavoriteBorder,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = "${post.reactionsCount}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.ChatBubbleOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = "${post.commentsCount}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

                    // Reaction buttons
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ReactionEmojis.DEFAULT_REACTIONS.forEach { emoji ->
                                Surface(
                                    onClick = { onReactionClick(emoji) },
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    ) {
                                        Text(text = emoji, fontSize = 18.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Divider + comments header
                    item {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = String.format(stringResource(R.string.post_detail_comments_title), comments.size),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Comments list
                    if (isLoadingComments) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    } else if (comments.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Outlined.ChatBubbleOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.post_detail_no_comments),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(R.string.post_detail_be_first),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    } else {
                        items(comments) { comment ->
                            PremiumCommentItem(
                                comment = comment,
                                canDelete = isAdmin || comment.userId == currentUserId,
                                onDeleteClick = { onDeleteComment(comment.id) },
                                onReactionClick = { emoji -> onCommentReaction(comment.id, emoji) },
                                onReply = { replyingToCommentDetail = it },
                                onUserMenu = { userActionsCommentDetail = it },
                                replyToComment = comment.replyToCommentId?.let { rid ->
                                    comments.find { it.id == rid }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }
                }

                // ── Bottom input area ──
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )

                    // Voice recording bar
                    if (isRecording) {
                        val voiceLabel = stringResource(R.string.post_detail_voice)
                        val recordingLabel = stringResource(R.string.post_detail_recording)
                        val cancelDesc = stringResource(R.string.cancel)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "$recordingLabel ${voiceRecorder.formatDuration(recordingDuration)}",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    coroutineScope.launch { voiceRecorder.cancelRecording() }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = cancelDesc,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Surface(
                                onClick = {
                                    coroutineScope.launch {
                                        voiceRecorder.stopRecording()
                                        val state = voiceRecorder.recordingState.value
                                        if (state is com.worldmates.messenger.utils.VoiceRecorder.RecordingState.Completed) {
                                            onAddComment("[$voiceLabel](file://${state.filePath})")
                                            voiceRecorder.cancelRecording()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.height(32.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.post_detail_send),
                                        fontSize = 13.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    } else {
                        // Picker tabs
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CommentPickerChip(
                                icon = Icons.Outlined.EmojiEmotions,
                                label = stringResource(R.string.post_detail_stickers),
                                isActive = activePickerTab == "strapi",
                                onClick = {
                                    activePickerTab = if (activePickerTab == "strapi") null else "strapi"
                                    showStrapiPicker = activePickerTab == "strapi"
                                    showEmojiPicker = false
                                    showGifPicker = false
                                }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            CommentPickerChip(
                                icon = Icons.Outlined.Mood,
                                label = stringResource(R.string.post_detail_emoji),
                                isActive = activePickerTab == "emoji",
                                onClick = {
                                    activePickerTab = if (activePickerTab == "emoji") null else "emoji"
                                    showEmojiPicker = activePickerTab == "emoji"
                                    showStrapiPicker = false
                                    showGifPicker = false
                                }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            CommentPickerChip(
                                icon = Icons.Outlined.Gif,
                                label = stringResource(R.string.post_detail_gif),
                                isActive = activePickerTab == "gif",
                                onClick = {
                                    activePickerTab = if (activePickerTab == "gif") null else "gif"
                                    showGifPicker = activePickerTab == "gif"
                                    showEmojiPicker = false
                                    showStrapiPicker = false
                                }
                            )
                        }

                        // Input row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 8.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            // Voice recording button
                            IconButton(
                                onClick = {
                                    coroutineScope.launch { voiceRecorder.startRecording() }
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = stringResource(R.string.post_detail_voice),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            OutlinedTextField(
                                value = commentText,
                                onValueChange = { commentText = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 44.dp, max = 120.dp),
                                placeholder = {
                                    Text(
                                        stringResource(R.string.post_detail_add_comment),
                                        fontSize = 14.sp
                                    )
                                },
                                maxLines = 4,
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                )
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            // Send button
                            val sendEnabled = commentText.isNotBlank()
                            Surface(
                                onClick = {
                                    if (sendEnabled) {
                                        if (onAddCommentWithReply != null) {
                                            onAddCommentWithReply(commentText, replyingToCommentDetail?.id)
                                        } else {
                                            onAddComment(commentText)
                                        }
                                        commentText = ""
                                        replyingToCommentDetail = null
                                    }
                                },
                                enabled = sendEnabled,
                                modifier = Modifier.size(40.dp),
                                shape = CircleShape,
                                color = if (sendEnabled)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Send,
                                        contentDescription = stringResource(R.string.post_detail_send),
                                        tint = if (sendEnabled)
                                            Color.White
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Pickers
                    AnimatedVisibility(visible = showEmojiPicker) {
                        com.worldmates.messenger.ui.components.EmojiPicker(
                            onEmojiSelected = { emoji -> commentText += emoji },
                            onDismiss = {
                                showEmojiPicker = false
                                activePickerTab = null
                            }
                        )
                    }

                    AnimatedVisibility(visible = showGifPicker) {
                        com.worldmates.messenger.ui.components.GifPicker(
                            onGifSelected = { gifUrl ->
                                commentText += "\n[GIF]($gifUrl)"
                                showGifPicker = false
                                activePickerTab = null
                            },
                            onDismiss = {
                                showGifPicker = false
                                activePickerTab = null
                            }
                        )
                    }

                    AnimatedVisibility(visible = showStrapiPicker) {
                        com.worldmates.messenger.ui.strapi.StrapiContentPicker(
                            onItemSelected = { contentUrl ->
                                commentText += "\n[Sticker]($contentUrl)"
                                showStrapiPicker = false
                                activePickerTab = null
                            },
                            onDismiss = {
                                showStrapiPicker = false
                                activePickerTab = null
                            }
                        )
                    }
                }
            }
        }
    }

    // Media viewers (outside Dialog to avoid nesting issues)
    if (showMediaViewer && imageMediaUrls.isNotEmpty()) {
        com.worldmates.messenger.ui.media.ImageGalleryViewer(
            imageUrls = imageMediaUrls,
            initialPage = mediaViewerPage,
            onDismiss = { showMediaViewer = false }
        )
    }

    if (showVideoPlayer && videoPlayerUrl.isNotEmpty()) {
        com.worldmates.messenger.ui.media.FullscreenVideoPlayer(
            videoUrl = videoPlayerUrl,
            onDismiss = { showVideoPlayer = false }
        )
    }

    // User actions sheet (detail view)
    userActionsCommentDetail?.let { target ->
        com.worldmates.messenger.ui.components.CommentUserActionsSheet(
            userId = target.userId,
            username = target.userName ?: target.username ?: "User",
            avatar = target.userAvatar,
            isOwnComment = target.userId == currentUserId,
            isAdmin = isAdmin,
            context = "channel",
            commentText = target.text,
            onDismiss = { userActionsCommentDetail = null },
            onAction = { action ->
                when (action) {
                    is com.worldmates.messenger.ui.components.CommentUserAction.Reply ->
                        replyingToCommentDetail = target
                    is com.worldmates.messenger.ui.components.CommentUserAction.Mention ->
                        commentText = "@${target.username ?: ""} $commentText"
                    else -> {}
                }
            }
        )
    }
}

// ==================== KARMA & TRUST BADGES ====================

/**
 * Мини-badge для отображения кармы пользователя в постах
 */
@Composable
fun KarmaMiniBadge(
    score: Float,
    modifier: Modifier = Modifier
) {
    val scoreColor = when {
        score >= 4.0f -> Color(0xFF00C851)
        score >= 3.0f -> Color(0xFF00BCD4)
        score >= 2.0f -> Color(0xFFFFBB33)
        else -> Color(0xFFFF4444)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = scoreColor.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                tint = scoreColor,
                modifier = Modifier.size(10.dp)
            )
            Text(
                text = String.format("%.1f", score),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = scoreColor
            )
        }
    }
}

/**
 * Мини-badge для отображения уровня доверия
 */
@Composable
fun TrustMiniBadge(
    trustLevel: String,
    modifier: Modifier = Modifier
) {
    val (color, icon) = when (trustLevel.lowercase()) {
        "verified" -> Pair(Color(0xFF00C851), Icons.Default.Verified)
        "trusted" -> Pair(Color(0xFF0A84FF), Icons.Default.ThumbUp)
        "neutral" -> Pair(Color(0xFF8E8E93), Icons.Default.Person)
        "untrusted" -> Pair(Color(0xFFFF4444), Icons.Default.Warning)
        else -> Pair(Color(0xFF8E8E93), Icons.Default.Person)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Icon(
            icon,
            contentDescription = trustLevel,
            tint = color,
            modifier = Modifier
                .padding(3.dp)
                .size(12.dp)
        )
    }
}

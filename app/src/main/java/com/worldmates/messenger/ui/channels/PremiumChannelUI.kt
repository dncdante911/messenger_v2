package com.worldmates.messenger.ui.channels

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
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

// ==================== PREMIUM COLOR PALETTE ====================

object PremiumColors {
    // Primary accent colors
    val TelegramBlue = Color(0xFF0088CC)
    val TelegramBlueDark = Color(0xFF006699)
    val TelegramBlueLight = Color(0xFF54A9EB)

    // Gradient colors
    val GradientStart = Color(0xFF667eea)
    val GradientMiddle = Color(0xFF764ba2)
    val GradientEnd = Color(0xFFf093fb)

    // Premium gradients
    val PremiumGold = Color(0xFFFFD700)
    val PremiumOrange = Color(0xFFFF8C00)

    // Status colors
    val SuccessGreen = Color(0xFF00C853)
    val WarningOrange = Color(0xFFFF9800)
    val ErrorRed = Color(0xFFFF5252)

    // Neutral colors
    val SurfaceLight = Color(0xFFF8F9FA)
    val SurfaceDark = Color(0xFF1E1E1E)
    val CardLight = Color(0xFFFFFFFF)
    val CardDark = Color(0xFF2D2D2D)

    // Text colors
    val TextPrimaryLight = Color(0xFF1A1A1A)
    val TextSecondaryLight = Color(0xFF6B7280)
    val TextPrimaryDark = Color(0xFFFFFFFF)
    val TextSecondaryDark = Color(0xFF9CA3AF)
}

// ==================== PREMIUM CHANNEL HEADER ====================

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
    val isDarkTheme = isSystemInDarkTheme()
    val cardColor = if (isDarkTheme) PremiumColors.CardDark else PremiumColors.CardLight
    val textPrimary = if (isDarkTheme) PremiumColors.TextPrimaryDark else PremiumColors.TextPrimaryLight
    val textSecondary = if (isDarkTheme) PremiumColors.TextSecondaryDark else PremiumColors.TextSecondaryLight

    // Animated gradient offset
    val infiniteTransition = rememberInfiniteTransition(label = "header_gradient")
    val gradientOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradient_offset"
    )

    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        // Animated gradient background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
                .drawBehind {
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                PremiumColors.GradientStart.copy(alpha = 0.4f),
                                PremiumColors.GradientMiddle.copy(alpha = 0.3f),
                                PremiumColors.TelegramBlue.copy(alpha = 0.2f),
                                if (isDarkTheme) PremiumColors.SurfaceDark else PremiumColors.SurfaceLight
                            ),
                            start = Offset(gradientOffset, 0f),
                            end = Offset(gradientOffset + 500f, size.height)
                        )
                    )
                }
        )

        // Decorative blur circles
        Box(
            modifier = Modifier
                .offset(x = (-60).dp, y = (-40).dp)
                .size(200.dp)
                .blur(80.dp)
                .background(
                    color = PremiumColors.TelegramBlue.copy(alpha = 0.3f),
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 80.dp, y = 60.dp)
                .size(160.dp)
                .blur(70.dp)
                .background(
                    color = PremiumColors.GradientMiddle.copy(alpha = 0.25f),
                    shape = CircleShape
                )
        )

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Top app bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button
                PremiumIconButton(
                    onClick = onBackClick,
                    icon = Icons.Default.ArrowBack,
                    isDarkTheme = isDarkTheme
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onAddMembersClick != null && channel.isAdmin) {
                        PremiumIconButton(
                            onClick = onAddMembersClick,
                            icon = Icons.Default.PersonAdd,
                            isDarkTheme = isDarkTheme,
                            tint = PremiumColors.TelegramBlue
                        )
                    }
                    if (onSettingsClick != null && channel.isAdmin) {
                        PremiumIconButton(
                            onClick = onSettingsClick,
                            icon = Icons.Default.Settings,
                            isDarkTheme = isDarkTheme
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Channel info section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Premium avatar with animated ring
                PremiumChannelAvatar(
                    avatarUrl = channel.avatarUrl,
                    channelName = channel.name,
                    isVerified = channel.isVerified,
                    isAdmin = channel.isAdmin,
                    onAvatarClick = onAvatarClick,
                    size = 110.dp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Channel name with verified badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = channel.name,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        letterSpacing = (-0.5).sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (channel.isVerified) {
                        Spacer(modifier = Modifier.width(8.dp))
                        PremiumVerifiedBadge(size = 24.dp)
                    }
                }

                // Username tag
                if (channel.username != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = PremiumColors.TelegramBlue.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "@${channel.username}",
                            fontSize = 15.sp,
                            color = PremiumColors.TelegramBlue,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                // Category badge
                if (!channel.category.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isDarkTheme) Color.White.copy(alpha = 0.08f)
                            else Color.Black.copy(alpha = 0.04f)
                    ) {
                        Text(
                            text = channel.category!!,
                            fontSize = 12.sp,
                            color = textSecondary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }

                // Description
                if (!channel.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = channel.description!!,
                        fontSize = 15.sp,
                        color = textSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Stats cards row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    PremiumStatCard(
                        icon = Icons.Default.People,
                        value = formatCountPremium(channel.subscribersCount),
                        label = "Subscribers",
                        color = PremiumColors.TelegramBlue,
                        onClick = onSubscribersClick,
                        isDarkTheme = isDarkTheme,
                        modifier = Modifier.weight(1f)
                    )
                    PremiumStatCard(
                        icon = Icons.Default.Article,
                        value = formatCountPremium(channel.postsCount),
                        label = "Posts",
                        color = PremiumColors.GradientMiddle,
                        isDarkTheme = isDarkTheme,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Divider with gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                PremiumColors.TelegramBlue.copy(alpha = 0.3f),
                                PremiumColors.TelegramBlue.copy(alpha = 0.3f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    }
}

// ==================== PREMIUM ICON BUTTON ====================

@Composable
private fun PremiumIconButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isDarkTheme: Boolean,
    tint: Color? = null
) {
    val bgColor = if (isDarkTheme) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f)
    val iconColor = tint ?: if (isDarkTheme) Color.White else Color.Black.copy(alpha = 0.8f)

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = bgColor,
        modifier = Modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ==================== PREMIUM CHANNEL AVATAR ====================

@Composable
fun PremiumChannelAvatar(
    avatarUrl: String,
    channelName: String,
    size: Dp = 100.dp,
    isVerified: Boolean = false,
    isAdmin: Boolean = false,
    onAvatarClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Animated ring rotation
    val infiniteTransition = rememberInfiniteTransition(label = "avatar_ring")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_rotation"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Glow effect
        Box(
            modifier = Modifier
                .size(size + 16.dp)
                .blur(25.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            PremiumColors.TelegramBlue.copy(alpha = 0.5f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Animated gradient ring
        Box(
            modifier = Modifier
                .size(size + 8.dp)
                .clip(CircleShape)
                .drawBehind {
                    drawCircle(
                        brush = Brush.sweepGradient(
                            0f to PremiumColors.TelegramBlue,
                            0.25f to PremiumColors.GradientMiddle,
                            0.5f to PremiumColors.GradientEnd,
                            0.75f to PremiumColors.TelegramBlueLight,
                            1f to PremiumColors.TelegramBlue
                        )
                    )
                }
                .graphicsLayer { rotationZ = rotation }
                .padding(4.dp)
        ) {
            // White inner circle
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(3.dp)
            ) {
                // Avatar image or placeholder
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
                    // Gradient placeholder
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        PremiumColors.TelegramBlue,
                                        PremiumColors.GradientMiddle
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = channelName.take(2).uppercase(),
                            fontSize = (size.value / 2.5f).sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }

        // Edit avatar button for admin
        if (isAdmin && onAvatarClick != null) {
            Surface(
                onClick = onAvatarClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 4.dp, y = 4.dp)
                    .size(36.dp),
                shape = CircleShape,
                color = PremiumColors.TelegramBlue,
                shadowElevation = 6.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "Change avatar",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ==================== PREMIUM VERIFIED BADGE ====================

@Composable
fun PremiumVerifiedBadge(
    size: Dp = 22.dp,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = PremiumColors.TelegramBlue,
        shadowElevation = 3.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Verified",
                tint = Color.White,
                modifier = Modifier.size(size * 0.6f)
            )
        }
    }
}

// ==================== PREMIUM STAT CARD ====================

@Composable
fun PremiumStatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    color: Color,
    isDarkTheme: Boolean,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val cardBg = if (isDarkTheme) PremiumColors.CardDark else PremiumColors.CardLight
    val textPrimary = if (isDarkTheme) PremiumColors.TextPrimaryDark else PremiumColors.TextPrimaryLight
    val textSecondary = if (isDarkTheme) PremiumColors.TextSecondaryDark else PremiumColors.TextSecondaryLight

    Card(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier.shadow(
            elevation = 8.dp,
            shape = RoundedCornerShape(20.dp),
            ambientColor = color.copy(alpha = 0.15f),
            spotColor = color.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon with colored background
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = value,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = textSecondary
            )
        }
    }
}

// ==================== PREMIUM POST CARD ====================

@Composable
fun PremiumPostCard(
    post: ChannelPost,
    onPostClick: () -> Unit,
    onReactionClick: (String) -> Unit,
    onCommentsClick: () -> Unit,
    onShareClick: () -> Unit,
    onMoreClick: () -> Unit,
    canEdit: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = isSystemInDarkTheme()
    val cardBg = if (isDarkTheme) PremiumColors.CardDark else PremiumColors.CardLight
    val textPrimary = if (isDarkTheme) PremiumColors.TextPrimaryDark else PremiumColors.TextPrimaryLight
    val textSecondary = if (isDarkTheme) PremiumColors.TextSecondaryDark else PremiumColors.TextSecondaryLight

    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "post_scale"
    )

    Card(
        onClick = onPostClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Post header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Author avatar
                PremiumSmallAvatar(
                    avatarUrl = post.authorAvatar ?: "",
                    name = post.authorName ?: post.authorUsername ?: "User",
                    size = 44.dp
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Author info
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = post.authorName ?: post.authorUsername ?: "User #${post.authorId}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (post.isPinned) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = "Pinned",
                                tint = PremiumColors.TelegramBlue,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Text(
                        text = formatPostTime(post.createdTime),
                        fontSize = 13.sp,
                        color = textSecondary
                    )
                }

                // More options button
                if (canEdit) {
                    IconButton(
                        onClick = onMoreClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "More",
                            tint = textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Post content
            if (post.text.isNotBlank()) {
                Text(
                    text = post.text,
                    fontSize = 15.sp,
                    color = textPrimary,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Media content
            if (!post.media.isNullOrEmpty()) {
                PremiumMediaGallery(
                    media = post.media!!,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Views counter
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Visibility,
                    contentDescription = null,
                    tint = textSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${formatCountPremium(post.viewsCount)} views",
                    fontSize = 13.sp,
                    color = textSecondary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Divider
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = if (isDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
            )

            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Reactions
                PremiumActionButton(
                    icon = if (post.reactions?.isNotEmpty() ?:false ) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                    label = if (post.reactions?.isNotEmpty() ?:false ) "${post.reactions.sumOf { it.count }}" else "Like",
                    onClick = { onReactionClick("\uD83D\uDC4D") },
                    color = if (post.reactions?.isNotEmpty() ?:false ) PremiumColors.TelegramBlue else textSecondary,
                    isDarkTheme = isDarkTheme
                )

                // Comments
                PremiumActionButton(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    label = if (post.commentsCount > 0) "${post.commentsCount}" else "Comment",
                    onClick = onCommentsClick,
                    color = textSecondary,
                    isDarkTheme = isDarkTheme
                )

                // Share
                PremiumActionButton(
                    icon = Icons.Outlined.Share,
                    label = "Share",
                    onClick = onShareClick,
                    color = textSecondary,
                    isDarkTheme = isDarkTheme
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
                            PremiumColors.TelegramBlue,
                            PremiumColors.GradientMiddle
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.take(1).uppercase(),
                fontSize = (size.value / 2.5f).sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

// ==================== PREMIUM ACTION BUTTON ====================

@Composable
private fun PremiumActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    color: Color,
    isDarkTheme: Boolean
) {
    TextButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.textButtonColors(
            contentColor = color
        )
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ==================== PREMIUM MEDIA GALLERY ====================

@Composable
fun PremiumMediaGallery(
    media: List<PostMedia>,
    modifier: Modifier = Modifier
) {
    when (media.size) {
        1 -> {
            // Single media - full width
            PremiumMediaItem(
                media = media[0],
                modifier = modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .clip(RoundedCornerShape(0.dp))
            )
        }
        2 -> {
            // Two media - side by side
            Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                media.forEach { item ->
                    PremiumMediaItem(
                        media = item,
                        modifier = Modifier
                            .weight(1f)
                            .height(200.dp)
                    )
                }
            }
        }
        else -> {
            // 3+ media - grid layout
            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // First row - first image full width
                PremiumMediaItem(
                    media = media[0],
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )

                // Second row - remaining images
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    media.drop(1).take(3).forEachIndexed { index, item ->
                        Box(modifier = Modifier.weight(1f)) {
                            PremiumMediaItem(
                                media = item,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                            )
                            // Show +N overlay for remaining
                            if (index == 2 && media.size > 4) {
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

@Composable
private fun PremiumMediaItem(
    media: PostMedia,
    modifier: Modifier = Modifier
) {
    val isVideo = media.type == "video"

    Box(modifier = modifier) {
        AsyncImage(
            model = media.url.toFullMediaUrl(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Video play button overlay
        if (isVideo) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Play video",
                            tint = Color.Black,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==================== PREMIUM FAB ====================

@Composable
fun PremiumFAB(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "fab_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fab_scale"
    )

    Box(modifier = modifier) {
        // Glow effect
        Box(
            modifier = Modifier
                .size(64.dp)
                .blur(20.dp)
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                }
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            PremiumColors.TelegramBlue.copy(alpha = 0.6f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        FloatingActionButton(
            onClick = onClick,
            shape = CircleShape,
            containerColor = PremiumColors.TelegramBlue,
            contentColor = Color.White,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 8.dp,
                pressedElevation = 12.dp
            )
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Create post",
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

// ==================== PREMIUM SUBSCRIBE BUTTON ====================

@Composable
fun PremiumSubscribeButton(
    isSubscribed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSubscribed)
            Color(0xFF2D2D2D)
        else
            PremiumColors.TelegramBlue,
        animationSpec = tween(300),
        label = "subscribe_bg"
    )

    Button(
        onClick = onToggle,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = bgColor,
            contentColor = Color.White
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (isSubscribed) 0.dp else 6.dp
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
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (subscribed) "Subscribed" else "Subscribe",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ==================== PREMIUM SECTION HEADER ====================

@Composable
fun PremiumSectionHeader(
    title: String,
    count: Int,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = isSystemInDarkTheme()
    val cardBg = if (isDarkTheme) PremiumColors.CardDark else PremiumColors.CardLight
    val textPrimary = if (isDarkTheme) PremiumColors.TextPrimaryDark else PremiumColors.TextPrimaryLight
    val textSecondary = if (isDarkTheme) PremiumColors.TextSecondaryDark else PremiumColors.TextSecondaryLight

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = cardBg
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = PremiumColors.TelegramBlue.copy(alpha = 0.15f)
            ) {
                Text(
                    text = count.toString(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PremiumColors.TelegramBlue,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

// ==================== PREMIUM EMPTY STATE ====================

@Composable
fun PremiumEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = isSystemInDarkTheme()
    val textPrimary = if (isDarkTheme) PremiumColors.TextPrimaryDark else PremiumColors.TextPrimaryLight
    val textSecondary = if (isDarkTheme) PremiumColors.TextSecondaryDark else PremiumColors.TextSecondaryLight

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(PremiumColors.TelegramBlue.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = PremiumColors.TelegramBlue,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = textPrimary,
            textAlign = TextAlign.Center
        )

        if (subtitle != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = textSecondary,
                textAlign = TextAlign.Center
            )
        }

        if (action != null) {
            Spacer(modifier = Modifier.height(20.dp))
            action()
        }
    }
}

// ==================== PREMIUM MENU ITEM ====================

@Composable
fun PremiumMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    iconTint: Color = PremiumColors.TelegramBlue,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = isSystemInDarkTheme()

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon with tinted background
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = iconTint.copy(alpha = 0.12f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isDarkTheme) PremiumColors.TextPrimaryDark
                        else PremiumColors.TextPrimaryLight
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = if (isDarkTheme) PremiumColors.TextSecondaryDark
                            else PremiumColors.TextSecondaryLight
                    )
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if (isDarkTheme) PremiumColors.TextSecondaryDark
                    else PremiumColors.TextSecondaryLight,
                modifier = Modifier.size(20.dp)
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

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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.worldmates.messenger.data.model.Channel
import com.worldmates.messenger.util.toFullMediaUrl

// ==================== IMPROVED CLASSIC CHANNEL LIST ITEM ====================

/**
 * Покращений класичний елемент списку каналів
 * - Збільшений аватар з градієнтною рамкою для верифікованих
 * - Третій рядок з категорією/описом
 * - Індикатор онлайн-активності
 * - Плавні анімації при натисканні
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TelegramChannelItem(
    channel: Channel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(
                        bounded = true,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    ),
                    onClick = onClick
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with gradient ring for verified channels
            Box(modifier = Modifier.size(52.dp)) {
                val avatarShape = RoundedCornerShape(16.dp)

                // Gradient ring for verified
                if (channel.isVerified) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                brush = Brush.sweepGradient(
                                    colors = listOf(
                                        Color(0xFF0A84FF),
                                        Color(0xFF5E5CE6),
                                        Color(0xFFBF5AF2),
                                        Color(0xFF0A84FF)
                                    )
                                )
                            )
                            .padding(2.dp)
                    ) {
                        if (channel.avatarUrl.isNotBlank()) {
                            AsyncImage(
                                model = channel.avatarUrl.toFullMediaUrl(),
                                contentDescription = channel.name,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(avatarShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(avatarShape)
                                    .background(
                                        brush = Brush.linearGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.tertiary
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = channel.name.take(1).uppercase(),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                } else {
                    if (channel.avatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = channel.avatarUrl.toFullMediaUrl(),
                            contentDescription = channel.name,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(avatarShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(avatarShape)
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.tertiary
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = channel.name.take(1).uppercase(),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Verified mini badge
                if (channel.isVerified) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(1.5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(Color(0xFF0A84FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Verified",
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Channel info - three-line layout
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Line 1: Name + badges
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = channel.name,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 20.sp,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (channel.isPrivate) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Private",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Line 2: Category or description snippet
                if (channel.category != null || channel.description != null) {
                    Text(
                        text = channel.category ?: channel.description?.take(60) ?: "",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (channel.category != null) FontWeight.Medium else FontWeight.Normal
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                }

                // Line 3: Stats with icons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Outlined.PeopleAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = formatCount(channel.subscribersCount),
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        maxLines = 1
                    )
                    if (channel.postsCount > 0) {
                        Text(
                            text = " · ",
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                        Icon(
                            Icons.Outlined.Article,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = formatCount(channel.postsCount),
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right-side: Admin badge or subscription indicator
            if (channel.isAdmin) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFF6B6B).copy(alpha = 0.12f),
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = "Admin",
                            tint = Color(0xFFFF6B6B),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            } else if (channel.isSubscribed) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                ) {
                    Icon(
                        Icons.Default.NotificationsActive,
                        contentDescription = "Subscribed",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier
                            .padding(5.dp)
                            .size(14.dp)
                    )
                }
            } else {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Subtle divider
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 82.dp)
                .height(0.5.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
        )
    }
}

// ==================== MODERN CHANNEL CARD ====================

@Composable
fun ChannelCard(
    channel: Channel,
    onClick: () -> Unit,
    onSubscribeToggle: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "card_scale"
    )
    val elevation by animateDpAsState(
        targetValue = if (isPressed) 2.dp else 6.dp,
        animationSpec = tween(200),
        label = "card_elevation"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = elevation,
            pressedElevation = 2.dp
        )
    ) {
        // Gradient overlay at top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with glow effect
            Box(
                modifier = Modifier
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(18.dp),
                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
            ) {
                ModernChannelAvatar(
                    avatarUrl = channel.avatarUrl,
                    channelName = channel.name,
                    size = 60.dp,
                    isVerified = channel.isVerified
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Channel Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = channel.name,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        letterSpacing = 0.2.sp
                    )
                    if (channel.isVerified) {
                        VerifiedBadge(size = 18.dp)
                    }
                }

                if (channel.description != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = channel.description!!,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Modern stats chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatChip(
                        icon = Icons.Outlined.PeopleAlt,
                        value = formatCount(channel.subscribersCount),
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (channel.postsCount > 0) {
                        StatChip(
                            icon = Icons.Outlined.Article,
                            value = formatCount(channel.postsCount),
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    if (channel.isPrivate) {
                        StatChip(
                            icon = Icons.Default.Lock,
                            value = "Private",
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Subscribe button or admin badge
            if (channel.isAdmin) {
                ModernAdminBadge()
            } else if (onSubscribeToggle != null) {
                ModernSubscribeButton(
                    isSubscribed = channel.isSubscribed,
                    onToggle = { onSubscribeToggle(channel.isSubscribed) }
                )
            }
        }
    }
}

// ==================== MODERN CHANNEL AVATAR ====================

@Composable
fun ModernChannelAvatar(
    avatarUrl: String,
    channelName: String,
    size: Dp = 56.dp,
    isVerified: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        if (avatarUrl.isNotBlank()) {
            AsyncImage(
                model = avatarUrl.toFullMediaUrl(),
                contentDescription = channelName,
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(size / 3.5f)),
                contentScale = ContentScale.Crop
            )
        } else {
            // Modern gradient placeholder
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(size / 3.5f))
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(size.value, size.value)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = channelName.take(2).uppercase(),
                    fontSize = (size.value / 2.8f).sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 1.sp
                )
            }
        }

        // Verified badge
        if (isVerified) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 4.dp, y = 4.dp)
            ) {
                VerifiedBadge(size = size / 3.5f)
            }
        }
    }
}

// ==================== VERIFIED BADGE ====================

@Composable
fun VerifiedBadge(
    size: Dp = 20.dp,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = Color(0xFF0A84FF),
        shadowElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Verified",
                tint = Color.White,
                modifier = Modifier.size(size * 0.65f)
            )
        }
    }
}

// ==================== STAT CHIP ====================

@Composable
private fun StatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = value,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

// ==================== MODERN SUBSCRIBE BUTTON ====================

@Composable
fun ModernSubscribeButton(
    isSubscribed: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean = true
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSubscribed)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.primary,
        animationSpec = tween(300),
        label = "subscribe_bg"
    )

    Surface(
        onClick = onToggle,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        shadowElevation = if (isSubscribed) 0.dp else 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                if (isSubscribed) Icons.Default.NotificationsActive else Icons.Default.Add,
                contentDescription = null,
                tint = if (isSubscribed)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp)
            )
            AnimatedContent(
                targetState = isSubscribed,
                transitionSpec = {
                    slideInVertically { -it } + fadeIn() togetherWith
                            slideOutVertically { it } + fadeOut()
                },
                label = "subscribe_text"
            ) { subscribed ->
                Text(
                    text = if (subscribed) "Joined" else "Join",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (subscribed)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

// ==================== MODERN ADMIN BADGE ====================

@Composable
fun ModernAdminBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFFF6B6B).copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                Icons.Default.Shield,
                contentDescription = "Admin",
                tint = Color(0xFFFF6B6B),
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "Admin",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF6B6B),
                letterSpacing = 0.5.sp
            )
        }
    }
}

// ==================== COMPACT CHANNEL HEADER ====================

@Composable
fun ChannelHeader(
    channel: Channel,
    onBackClick: () -> Unit,
    onSettingsClick: (() -> Unit)? = null,
    onAvatarClick: (() -> Unit)? = null,
    onSubscribersClick: (() -> Unit)? = null,
    onAddMembersClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        // Subtle gradient background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                            MaterialTheme.colorScheme.surface
                        ),
                        startY = 0f,
                        endY = 600f
                    )
                )
        )

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassButton(
                    onClick = onBackClick,
                    icon = Icons.Default.ArrowBack
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onAddMembersClick != null && channel.isAdmin) {
                        GlassButton(
                            onClick = onAddMembersClick,
                            icon = Icons.Default.PersonAdd,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (onSettingsClick != null && channel.isAdmin) {
                        GlassButton(
                            onClick = onSettingsClick,
                            icon = Icons.Default.Settings
                        )
                    }
                }
            }

            // Channel info — compact horizontal layout
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Compact avatar
                Box {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.sweepGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary,
                                        MaterialTheme.colorScheme.secondary,
                                        MaterialTheme.colorScheme.primary
                                    )
                                )
                            )
                            .padding(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(2.dp)
                        ) {
                            ModernChannelAvatar(
                                avatarUrl = channel.avatarUrl,
                                channelName = channel.name,
                                size = 64.dp,
                                isVerified = false
                            )
                        }
                    }

                    if (onAvatarClick != null && channel.isAdmin) {
                        Surface(
                            onClick = onAvatarClick,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(26.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            shadowElevation = 2.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.CameraAlt,
                                    contentDescription = "Change avatar",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    if (channel.isVerified) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 2.dp, y = (-2).dp)
                        ) {
                            VerifiedBadge(size = 20.dp)
                        }
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Channel name + username + description
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channel.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (channel.username != null) {
                        Text(
                            text = "@${channel.username}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (!channel.description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = channel.description!!,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            lineHeight = 17.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Compact inline stats
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CompactStatChip(
                    icon = Icons.Default.People,
                    value = formatCount(channel.subscribersCount),
                    label = "Subscribers",
                    onClick = onSubscribersClick,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                CompactStatChip(
                    icon = Icons.Default.Article,
                    value = formatCount(channel.postsCount),
                    label = "Posts",
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Divider
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            )
        }
    }
}

// ==================== GLASS BUTTON ====================

@Composable
private fun GlassButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        shadowElevation = 4.dp,
        modifier = Modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

// ==================== COMPACT STAT CHIP ====================

@Composable
private fun CompactStatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    color: Color,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
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
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

// ==================== CHANNEL SEARCH BAR ====================

@Composable
fun ChannelSearchBar(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Box(modifier = Modifier.weight(1f)) {
                if (searchQuery.isEmpty()) {
                    Text(
                        text = "Search channels...",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontSize = 15.sp
                    )
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (searchQuery.isNotEmpty()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ==================== CHANNEL INFO CARD ====================

@Composable
fun ChannelInfoCard(
    channel: Channel,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "About",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (channel.description != null) {
                InfoItem(
                    icon = Icons.Outlined.Info,
                    label = "Description",
                    value = channel.description!!
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (channel.category != null) {
                InfoItem(
                    icon = Icons.Outlined.Category,
                    label = "Category",
                    value = channel.category!!
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            InfoItem(
                icon = if (channel.isPrivate) Icons.Outlined.Lock else Icons.Outlined.Public,
                label = "Type",
                value = if (channel.isPrivate) "Private Channel" else "Public Channel"
            )
        }
    }
}

@Composable
private fun InfoItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                lineHeight = 20.sp
            )
        }
    }
}

// ==================== UTILITY FUNCTIONS ====================

fun formatCount(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}

// Legacy compatibility
@Composable
fun ChannelAvatar(
    avatarUrl: String,
    channelName: String,
    size: Dp = 56.dp,
    isVerified: Boolean = false,
    modifier: Modifier = Modifier
) = ModernChannelAvatar(avatarUrl, channelName, size, isVerified, modifier)

@Composable
fun SubscribeButton(
    isSubscribed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    ModernSubscribeButton(isSubscribed = isSubscribed, onToggle = onToggle, enabled = enabled)
}

@Composable
fun SubscribeButtonCompact(
    isSubscribed: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean = true
) = ModernSubscribeButton(isSubscribed, onToggle, enabled)

@Composable
fun AdminBadge(modifier: Modifier = Modifier) = ModernAdminBadge(modifier)

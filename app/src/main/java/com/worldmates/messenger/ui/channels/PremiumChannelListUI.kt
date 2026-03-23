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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.draw.blur
import com.worldmates.messenger.data.model.Channel
import com.worldmates.messenger.ui.fonts.AppFonts
import com.worldmates.messenger.util.toFullMediaUrl

// ==================== PREMIUM CHANNEL LIST ITEM (Telegram-style tile) ====================

/**
 * Telegram-style channel tile: circular avatar, channel name with Exo2 font,
 * subscriber stats, category badge, and animated subscribe/action button.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PremiumChannelListItem(
    channel: Channel,
    onClick: () -> Unit,
    onSubscribeToggle: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.982f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "tile_scale"
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // ── Category color stripe (left accent) ──────────────────
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(76.dp)
                    .align(Alignment.CenterVertically)
                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                colorScheme.primary.copy(alpha = 0.85f),
                                colorScheme.tertiary.copy(alpha = 0.7f)
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular avatar with gradient ring for verified
                TgChannelAvatar(
                    avatarUrl = channel.avatarUrl,
                    channelName = channel.name,
                    isVerified = channel.isVerified,
                    size = 56.dp
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Info column
                Column(modifier = Modifier.weight(1f)) {
                    // Name row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = channel.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = AppFonts.Exo2,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (channel.isPrivate) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = colorScheme.onSurface.copy(alpha = 0.28f),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        if (channel.isVerified) {
                            Icon(
                                Icons.Default.Verified,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    // Subscribers row — Orbitron for numbers
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                Icons.Outlined.PeopleAlt,
                                contentDescription = null,
                                tint = colorScheme.primary.copy(alpha = 0.7f),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = formatCount(channel.subscribersCount),
                                fontFamily = AppFonts.Orbitron,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.primary.copy(alpha = 0.9f)
                            )
                        }
                        if (channel.postsCount > 0) {
                            PremiumMicroStat(
                                icon = Icons.Outlined.Article,
                                value = formatCount(channel.postsCount),
                                color = colorScheme.tertiary
                            )
                        }
                    }

                    // Category badge or description
                    if (channel.category != null || channel.description != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        if (channel.category != null) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = colorScheme.primaryContainer.copy(alpha = 0.55f),
                                border = BorderStroke(
                                    0.5.dp,
                                    colorScheme.primary.copy(alpha = 0.15f)
                                )
                            ) {
                                Text(
                                    text = channel.category!!,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = AppFonts.Righteous,
                                    color = colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    maxLines = 1
                                )
                            }
                        } else {
                            Text(
                                text = channel.description!!,
                                fontSize = 12.sp,
                                color = colorScheme.onSurface.copy(alpha = 0.48f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Action button
                PremiumChannelAction(
                    channel = channel,
                    onSubscribeToggle = onSubscribeToggle
                )
            }
        }
    }
}

// ==================== TG CHANNEL AVATAR ====================

@Composable
private fun TgChannelAvatar(
    avatarUrl: String,
    channelName: String,
    isVerified: Boolean,
    size: Dp = 56.dp
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(modifier = Modifier.size(size + if (isVerified) 4.dp else 0.dp)) {
        if (isVerified) {
            // Gradient ring for verified
            Box(
                modifier = Modifier
                    .size(size + 4.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                colorScheme.primary,
                                colorScheme.tertiary,
                                colorScheme.primary
                            )
                        )
                    )
                    .padding(2.dp)
            )
        }
        if (avatarUrl.isNotBlank()) {
            AsyncImage(
                model = avatarUrl.toFullMediaUrl(),
                contentDescription = channelName,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .align(Alignment.Center),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                colorScheme.primary.copy(alpha = 0.85f),
                                colorScheme.tertiary.copy(alpha = 0.9f)
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(size.value, size.value)
                        )
                    )
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = channelName.take(2).uppercase(),
                    fontSize = (size.value / 2.6f).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = AppFonts.RussoOne,
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
                    .offset(x = 2.dp, y = 2.dp)
            ) {
                PremiumVerifiedIcon(size = (size.value / 3.2f).dp)
            }
        }
    }
}

// ==================== PREMIUM LIST AVATAR ====================

@Composable
private fun PremiumListAvatar(
    avatarUrl: String,
    channelName: String,
    isVerified: Boolean,
    size: Dp = 54.dp
) {
    Box(modifier = Modifier.size(size)) {
        // Glass-фон під аватар
        Box(
            modifier = Modifier
                .size(size)
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(size / 3.2f),
                    ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                )
        ) {
            if (avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = avatarUrl.toFullMediaUrl(),
                    contentDescription = channelName,
                    modifier = Modifier
                        .size(size)
                        .clip(RoundedCornerShape(size / 3.2f)),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Градієнтний плейсхолдер
                Box(
                    modifier = Modifier
                        .size(size)
                        .clip(RoundedCornerShape(size / 3.2f))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f)
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
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        // Бейджик верифікації
        if (isVerified) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
            ) {
                PremiumVerifiedIcon(size = size / 3.5f)
            }
        }
    }
}

// ==================== PREMIUM VERIFIED ICON ====================

@Composable
fun PremiumVerifiedIcon(
    size: Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = Color.Transparent,
        shadowElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF0A84FF),
                            Color(0xFF5E5CE6)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Verified",
                tint = Color.White,
                modifier = Modifier.size(size * 0.6f)
            )
        }
    }
}

// ==================== PREMIUM MICRO STAT ====================

@Composable
private fun PremiumMicroStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = color.copy(alpha = 0.6f),
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        )
    }
}

// ==================== PREMIUM CHANNEL ACTION ====================

@Composable
private fun PremiumChannelAction(
    channel: Channel,
    onSubscribeToggle: ((Boolean) -> Unit)? = null
) {
    when {
        channel.isAdmin -> {
            // Адмін-бейджик — компактний та помітний
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFFF6B6B).copy(alpha = 0.1f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = "Admin",
                        tint = Color(0xFFFF6B6B),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Admin",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF6B6B)
                    )
                }
            }
        }
        onSubscribeToggle != null -> {
            // Інлайн кнопка підписки
            val isSubscribed = channel.isSubscribed
            val bgColor by animateColorAsState(
                targetValue = if (isSubscribed)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                else
                    MaterialTheme.colorScheme.primary,
                animationSpec = tween(250),
                label = "subscribe_bg"
            )

            Surface(
                onClick = { onSubscribeToggle(channel.isSubscribed) },
                shape = RoundedCornerShape(10.dp),
                color = bgColor,
                shadowElevation = if (isSubscribed) 0.dp else 2.dp
            ) {
                AnimatedContent(
                    targetState = isSubscribed,
                    transitionSpec = {
                        (fadeIn(tween(150)) + scaleIn(
                            initialScale = 0.9f,
                            animationSpec = tween(150)
                        )) togetherWith (fadeOut(tween(100)) + scaleOut(
                            targetScale = 0.9f,
                            animationSpec = tween(100)
                        ))
                    },
                    label = "subscribe_content"
                ) { subscribed ->
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            if (subscribed) Icons.Default.Check else Icons.Default.Add,
                            contentDescription = null,
                            tint = if (subscribed)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (subscribed) "Joined" else "Join",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (subscribed)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
        channel.isSubscribed -> {
            // Просто індикатор підписки
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Subscribed",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier
                        .padding(6.dp)
                        .size(14.dp)
                )
            }
        }
        else -> {
            // Стрілка навігації
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ==================== PREMIUM CHANNEL CARD (EXPANDED) ====================

/**
 * Розширена преміальна картка каналу для explore/discover секцій
 */
@Composable
fun PremiumChannelCardExpanded(
    channel: Channel,
    onClick: () -> Unit,
    onSubscribeToggle: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "expanded_scale"
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
            defaultElevation = 3.dp,
            pressedElevation = 1.dp
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Фоновий градієнт у верхній частині
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Декоративні елементи
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 30.dp, y = (-10).dp)
                    .size(60.dp)
                    .blur(25.dp)
                    .background(
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f),
                        shape = CircleShape
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    // Великий аватар
                    PremiumListAvatar(
                        avatarUrl = channel.avatarUrl,
                        channelName = channel.name,
                        isVerified = channel.isVerified,
                        size = 64.dp
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        // Назва
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = channel.name,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                letterSpacing = 0.15.sp,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (channel.isVerified) {
                                PremiumVerifiedIcon(size = 18.dp)
                            }
                        }

                        // Username
                        if (channel.username != null) {
                            Text(
                                text = "@${channel.username}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Опис
                        if (channel.description != null) {
                            Text(
                                text = channel.description!!,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Нижня секція: стати + кнопка
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Статистика чіпси
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PremiumStatPill(
                            icon = Icons.Outlined.PeopleAlt,
                            value = formatCount(channel.subscribersCount),
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (channel.postsCount > 0) {
                            PremiumStatPill(
                                icon = Icons.Outlined.Article,
                                value = formatCount(channel.postsCount),
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        if (channel.category != null) {
                            PremiumStatPill(
                                icon = Icons.Outlined.Category,
                                value = channel.category!!,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    // Кнопка
                    PremiumChannelAction(
                        channel = channel,
                        onSubscribeToggle = onSubscribeToggle
                    )
                }
            }
        }
    }
}

// ==================== PREMIUM STAT PILL ====================

@Composable
private fun PremiumStatPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.08f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color.copy(alpha = 0.8f),
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = value,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = color.copy(alpha = 0.9f),
                maxLines = 1
            )
        }
    }
}

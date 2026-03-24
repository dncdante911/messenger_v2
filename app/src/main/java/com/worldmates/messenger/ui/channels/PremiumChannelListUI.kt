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

// ==================== PREMIUM CHANNEL LIST ITEM (Cover Card) ====================

/**
 * Magazine-style "cover card":
 *  – 64dp gradient banner at top (primary→tertiary) with subtle wave overlay
 *  – Square-rounded avatar (not circular) overlapping the banner bottom-left
 *  – Channel name in Exo2 + verified badge, username in muted color
 *  – Stats row with Orbitron numbers for subs/posts
 *  – Subscribe/action on the right of the card body
 * Looks nothing like the standard chat list item.
 */
@Composable
fun PremiumChannelListItem(
    channel: Channel,
    onClick: () -> Unit,
    onSubscribeToggle: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val avatarSize = 52.dp
    val bannerHeight = 52.dp

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp, pressedElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // ── Gradient banner with avatar embedded at bottom-left ───────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bannerHeight)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                colorScheme.primary.copy(alpha = 0.88f),
                                colorScheme.tertiary.copy(alpha = 0.80f)
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                        )
                    )
            ) {
                // Decorative blurred orb for depth
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .align(Alignment.CenterEnd)
                        .offset(x = 20.dp)
                        .blur(30.dp)
                        .background(
                            color = colorScheme.onPrimary.copy(alpha = 0.12f),
                            shape = CircleShape
                        )
                )
                // Action button anchored top-right inside the banner
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 10.dp)
                ) {
                    PremiumChannelAction(channel = channel, onSubscribeToggle = onSubscribeToggle)
                }
            }

            // ── Card body: avatar + name ──────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Square-rounded avatar
                TgChannelAvatar(
                    avatarUrl = channel.avatarUrl,
                    channelName = channel.name,
                    isVerified = channel.isVerified,
                    size = avatarSize,
                    squareRadius = 13.dp
                )

                Spacer(modifier = Modifier.width(10.dp))

                // Name + username / category
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = channel.name,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = AppFonts.Exo2,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (channel.isVerified) {
                            Icon(
                                Icons.Default.Verified,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        if (channel.isPrivate) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = colorScheme.onSurface.copy(alpha = 0.25f),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                    if (channel.username != null) {
                        Text(
                            text = "@${channel.username}",
                            fontSize = 11.5.sp,
                            color = colorScheme.primary.copy(alpha = 0.65f),
                            fontWeight = FontWeight.Medium
                        )
                    } else if (channel.category != null) {
                        Text(
                            text = channel.category!!,
                            fontSize = 11.sp,
                            color = colorScheme.tertiary.copy(alpha = 0.75f),
                            fontFamily = AppFonts.Righteous,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Stats row ─────────────────────────────────────────────────────
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        Icons.Outlined.PeopleAlt,
                        contentDescription = null,
                        tint = colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = formatCount(channel.subscribersCount),
                        fontFamily = AppFonts.Orbitron,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.primary.copy(alpha = 0.85f)
                    )
                    Text(
                        text = "subs",
                        fontSize = 10.sp,
                        color = colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
                if (channel.postsCount > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Article,
                            contentDescription = null,
                            tint = colorScheme.tertiary.copy(alpha = 0.6f),
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = formatCount(channel.postsCount),
                            fontFamily = AppFonts.Orbitron,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.tertiary.copy(alpha = 0.85f)
                        )
                        Text(
                            text = "posts",
                            fontSize = 10.sp,
                            color = colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
                if (channel.description != null && channel.username == null) {
                    Text(
                        text = channel.description!!,
                        fontSize = 11.sp,
                        color = colorScheme.onSurface.copy(alpha = 0.45f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
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
    size: Dp = 56.dp,
    squareRadius: Dp? = null   // if non-null, use rounded square instead of circle
) {
    val colorScheme = MaterialTheme.colorScheme
    val shape = if (squareRadius != null) RoundedCornerShape(squareRadius) else CircleShape
    Box(modifier = Modifier.size(size + if (isVerified) 4.dp else 0.dp)) {
        if (isVerified) {
            // Gradient ring for verified
            Box(
                modifier = Modifier
                    .size(size + 4.dp)
                    .clip(shape)
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
                    .clip(shape)
                    .align(Alignment.Center),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(shape)
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

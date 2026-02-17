package com.worldmates.messenger.ui.channels

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.data.model.UserRating

// ==================== SHIMMER EFFECTS ====================

/**
 * Shimmer brush для создания эффекта загрузки
 */
@Composable
fun shimmerBrush(
    targetValue: Float = 1000f,
    colors: List<Color> = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    )
): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnimation = transition.animateFloat(
        initialValue = 0f,
        targetValue = targetValue,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1200,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    return Brush.linearGradient(
        colors = colors,
        start = Offset(translateAnimation.value - 200f, 0f),
        end = Offset(translateAnimation.value, 0f)
    )
}

/**
 * Shimmer placeholder для текста
 */
@Composable
fun ShimmerText(
    width: Dp,
    height: Dp = 14.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .background(shimmerBrush())
    )
}

/**
 * Shimmer placeholder для аватара
 */
@Composable
fun ShimmerAvatar(
    size: Dp = 48.dp,
    shape: androidx.compose.ui.graphics.Shape = CircleShape,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(shimmerBrush())
    )
}

/**
 * Shimmer карточка поста
 */
@Composable
fun ShimmerPostCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShimmerAvatar(size = 48.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    ShimmerText(width = 120.dp, height = 16.dp)
                    Spacer(modifier = Modifier.height(6.dp))
                    ShimmerText(width = 80.dp, height = 12.dp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Content lines
            ShimmerText(width = 280.dp, height = 14.dp)
            Spacer(modifier = Modifier.height(8.dp))
            ShimmerText(width = 240.dp, height = 14.dp)
            Spacer(modifier = Modifier.height(8.dp))
            ShimmerText(width = 200.dp, height = 14.dp)

            Spacer(modifier = Modifier.height(16.dp))

            // Media placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(shimmerBrush())
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Actions row
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                repeat(4) {
                    ShimmerText(width = 60.dp, height = 32.dp)
                }
            }
        }
    }
}

/**
 * Shimmer для списка каналов
 */
@Composable
fun ShimmerChannelItem(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShimmerAvatar(size = 52.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            ShimmerText(width = 140.dp, height = 16.dp)
            Spacer(modifier = Modifier.height(6.dp))
            ShimmerText(width = 100.dp, height = 12.dp)
        }
    }
}

/**
 * Shimmer для header канала
 */
@Composable
fun ShimmerChannelHeader(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ShimmerAvatar(size = 96.dp)
        Spacer(modifier = Modifier.height(16.dp))
        ShimmerText(width = 160.dp, height = 24.dp)
        Spacer(modifier = Modifier.height(8.dp))
        ShimmerText(width = 100.dp, height = 14.dp)
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(2) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(80.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(shimmerBrush())
                )
            }
        }
    }
}

// ==================== ANIMATION UTILITIES ====================

/**
 * Анимированное появление элементов списка
 */
@Composable
fun AnimatedListItem(
    index: Int,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index * 50L)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = 300,
                easing = FastOutSlowInEasing
            )
        ) + slideInVertically(
            initialOffsetY = { 50 },
            animationSpec = tween(
                durationMillis = 300,
                easing = FastOutSlowInEasing
            )
        )
    ) {
        content()
    }
}

/**
 * Scale animation при нажатии
 */
@Composable
fun PressableScale(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "press_scale"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        content()
    }
}

// ==================== USER KARMA/RATING COMPONENTS ====================

/**
 * Trust Level Badge - показывает уровень доверия пользователя
 */
@Composable
fun TrustLevelBadge(
    trustLevel: String,
    trustLevelEmoji: String? = null,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor, icon) = when (trustLevel.lowercase()) {
        "verified" -> Triple(
            Color(0xFF00C851).copy(alpha = 0.15f),
            Color(0xFF00C851),
            Icons.Default.Verified
        )
        "trusted" -> Triple(
            Color(0xFF0A84FF).copy(alpha = 0.15f),
            Color(0xFF0A84FF),
            Icons.Default.ThumbUp
        )
        "neutral" -> Triple(
            Color(0xFF8E8E93).copy(alpha = 0.15f),
            Color(0xFF8E8E93),
            Icons.Default.Person
        )
        "untrusted" -> Triple(
            Color(0xFFFF4444).copy(alpha = 0.15f),
            Color(0xFFFF4444),
            Icons.Default.Warning
        )
        else -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Default.Person
        )
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (trustLevelEmoji != null) {
                Text(
                    text = trustLevelEmoji,
                    fontSize = 12.sp
                )
            } else {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(12.dp)
                )
            }
            Text(
                text = trustLevel.replaceFirstChar { it.uppercase() },
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
        }
    }
}

/**
 * Karma Score Display - показывает рейтинг пользователя
 */
@Composable
fun KarmaScoreDisplay(
    score: Float,
    likes: Int = 0,
    dislikes: Int = 0,
    compact: Boolean = true,
    modifier: Modifier = Modifier
) {
    val scoreColor = when {
        score >= 4.0f -> Color(0xFF00C851)  // Отлично
        score >= 3.0f -> Color(0xFF00BCD4)  // Хорошо
        score >= 2.0f -> Color(0xFFFFBB33)  // Нормально
        else -> Color(0xFFFF4444)           // Плохо
    }

    if (compact) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = scoreColor.copy(alpha = 0.12f),
            modifier = modifier
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = scoreColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = String.format("%.1f", score),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = scoreColor
                )
            }
        }
    } else {
        // Full display with likes/dislikes
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = modifier
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Score
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = scoreColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = String.format("%.1f", score),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = scoreColor
                    )
                }

                // Divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                )

                // Likes
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        Icons.Default.ThumbUp,
                        contentDescription = null,
                        tint = Color(0xFF00C851),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = formatCompactNumber(likes),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF00C851)
                    )
                }

                // Dislikes
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        Icons.Default.ThumbDown,
                        contentDescription = null,
                        tint = Color(0xFFFF4444),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = formatCompactNumber(dislikes),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFFF4444)
                    )
                }
            }
        }
    }
}

/**
 * User Reputation Card - карточка репутации в профиле
 */
@Composable
fun UserReputationCard(
    rating: UserRating,
    onRateClick: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Репутація",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TrustLevelBadge(
                    trustLevel = rating.trustLevel,
                    trustLevelEmoji = rating.trustLevelEmoji
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Score display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Score circle
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    getScoreColor(rating.score).copy(alpha = 0.2f),
                                    getScoreColor(rating.score).copy(alpha = 0.05f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = String.format("%.1f", rating.score),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = getScoreColor(rating.score)
                        )
                        Text(
                            text = "/ 5.0",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }

                // Stats
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatRow(
                        icon = Icons.Default.ThumbUp,
                        iconTint = Color(0xFF00C851),
                        label = "Лайків",
                        value = formatCompactNumber(rating.likes),
                        percentage = rating.likePercentage?.let { "${it.toInt()}%" }
                    )
                    StatRow(
                        icon = Icons.Default.ThumbDown,
                        iconTint = Color(0xFFFF4444),
                        label = "Дизлайків",
                        value = formatCompactNumber(rating.dislikes),
                        percentage = rating.dislikePercentage?.let { "${it.toInt()}%" }
                    )
                    StatRow(
                        icon = Icons.Default.People,
                        iconTint = MaterialTheme.colorScheme.primary,
                        label = "Всього",
                        value = formatCompactNumber(rating.totalRatings)
                    )
                }
            }

            // Rate buttons
            if (onRateClick != null) {
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Like button
                    Button(
                        onClick = { onRateClick(true) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (rating.myRating?.type == "like")
                                Color(0xFF00C851)
                            else
                                Color(0xFF00C851).copy(alpha = 0.15f),
                            contentColor = if (rating.myRating?.type == "like")
                                Color.White
                            else
                                Color(0xFF00C851)
                        )
                    ) {
                        Icon(
                            Icons.Default.ThumbUp,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Подобається", fontWeight = FontWeight.SemiBold)
                    }

                    // Dislike button
                    Button(
                        onClick = { onRateClick(false) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (rating.myRating?.type == "dislike")
                                Color(0xFFFF4444)
                            else
                                Color(0xFFFF4444).copy(alpha = 0.15f),
                            contentColor = if (rating.myRating?.type == "dislike")
                                Color.White
                            else
                                Color(0xFFFF4444)
                        )
                    ) {
                        Icon(
                            Icons.Default.ThumbDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Не подобається", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    label: String,
    value: String,
    percentage: String? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (percentage != null) {
            Text(
                text = "($percentage)",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

// ==================== GLASSMORPHISM EFFECTS ====================

/**
 * Glassmorphism Surface - полупрозрачная поверхность с blur эффектом
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    alpha: Float = 0.7f,
    blurRadius: Dp = 10.dp,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .blur(blurRadius),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = alpha),
        tonalElevation = 0.dp
    ) {
        content()
    }
}

/**
 * Gradient Border Box
 */
@Composable
fun GradientBorderBox(
    modifier: Modifier = Modifier,
    gradientColors: List<Color> = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary
    ),
    borderWidth: Dp = 2.dp,
    cornerRadius: Dp = 16.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                brush = Brush.linearGradient(colors = gradientColors)
            )
            .padding(borderWidth)
    ) {
        Surface(
            shape = RoundedCornerShape(cornerRadius - 1.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxSize()
        ) {
            content()
        }
    }
}

// ==================== UTILITY FUNCTIONS ====================

private fun formatCompactNumber(number: Int): String {
    return when {
        number >= 1_000_000 -> String.format("%.1fM", number / 1_000_000.0)
        number >= 1_000 -> String.format("%.1fK", number / 1_000.0)
        else -> number.toString()
    }
}

private fun getScoreColor(score: Float): Color {
    return when {
        score >= 4.0f -> Color(0xFF00C851)
        score >= 3.0f -> Color(0xFF00BCD4)
        score >= 2.0f -> Color(0xFFFFBB33)
        else -> Color(0xFFFF4444)
    }
}

// ==================== LOADING STATES ====================

/**
 * Пульсирующий индикатор загрузки
 */
@Composable
fun PulsingLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .alpha(alpha)
            .size(48.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            color = color,
            strokeWidth = 3.dp
        )
    }
}

/**
 * Skeleton loading для контента
 */
@Composable
fun ContentSkeleton(
    lines: Int = 3,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(lines) { index ->
            val width = when (index) {
                lines - 1 -> 0.6f
                else -> 1f - (index * 0.1f)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth(width.coerceIn(0.5f, 1f))
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerBrush())
            )
        }
    }
}

// ==================== FLOATING ACTION BUTTON VARIANTS ====================

/**
 * Gradient FAB - красивая кнопка с градиентом
 */
@Composable
fun GradientFab(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    gradientColors: List<Color> = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary
    )
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "fab_scale"
    )

    Box(
        modifier = modifier
            .size(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(
                brush = Brush.linearGradient(colors = gradientColors)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

package com.worldmates.messenger.ui.groups

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.worldmates.messenger.data.model.Group
import com.worldmates.messenger.data.model.lastActivity
import java.text.SimpleDateFormat
import java.util.*

/**
 * Сучасна картка групи з градієнтом, анімаціями та Material 3 дизайном
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModernGroupCard(
    group: Group,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    isPinned: Boolean = false,
    unreadCount: Int = 0,
    modifier: Modifier = Modifier
) {
    // Анімація масштабу при натисканні
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    // Градієнти для різних типів груп
    val gradientColors = when {
        isPinned -> listOf(
            Color(0xFF667EEA),
            Color(0xFF764BA2)
        )
        group.isPrivate -> listOf(
            Color(0xFFf093fb),
            Color(0xFFf5576c)
        )
        else -> listOf(
            Color(0xFF4facfe),
            Color(0xFF00f2fe)
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .scale(scale)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isPinned) 8.dp else 4.dp
        )
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.horizontalGradient(gradientColors)
                )
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Аватар з рамкою
                Box {
                    AsyncImage(
                        model = group.avatarUrl,
                        contentDescription = group.name,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .border(
                                width = 3.dp,
                                color = Color.White.copy(alpha = 0.5f),
                                shape = CircleShape
                            ),
                        contentScale = ContentScale.Crop
                    )

                    // Online індикатор (якщо є активність)
                    if (unreadCount > 0) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .align(Alignment.TopEnd)
                                .background(
                                    color = Color(0xFF4CAF50),
                                    shape = CircleShape
                                )
                                .border(2.dp, Color.White, CircleShape)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Інфо групи
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = group.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (isPinned) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = "Закріплено",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (group.isPrivate) Icons.Default.Lock else Icons.Default.Group,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${group.membersCount} ${getMemberText(group.membersCount)}",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )

                        if (!group.description.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "•",
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = group.description!!,
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Остання активність
                    group.lastActivity?.let { lastActivity ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatLastActivity(lastActivity),
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }

                // Badge з непрочитаними
                if (unreadCount > 0) {
                    Spacer(modifier = Modifier.width(12.dp))
                    UnreadBadge(count = unreadCount)
                }

                // Стрілка
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }

    LaunchedEffect(isPressed) {
        if (!isPressed) {
            kotlinx.coroutines.delay(100)
            isPressed = false
        }
    }
}

/**
 * Badge для відображення кількості непрочитаних повідомлень
 */
@Composable
fun UnreadBadge(count: Int) {
    val displayText = if (count > 99) "99+" else count.toString()

    Box(
        modifier = Modifier
            .background(
                color = Color(0xFFFF3B30),
                shape = CircleShape
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayText,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Пустий стан з красивою анімацією
 */
@Composable
fun EmptyGroupsPlaceholder(
    onCreateClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Анімована іконка
        val infiniteTransition = rememberInfiniteTransition()
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        )

        Icon(
            Icons.Default.Group,
            contentDescription = null,
            modifier = Modifier
                .size(120.dp)
                .scale(scale),
            tint = Color(0xFF667EEA).copy(alpha = 0.3f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Ще немає груп",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Створіть свою першу групу для\nспілкування з друзями",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onCreateClick,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF667EEA)
            ),
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(56.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Створити групу",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Пошукова панель для груп
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsSearchBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    TextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = {
            Text(
                "Пошук груп...",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = "Пошук",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchQueryChange("") }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Очистити",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        },
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        singleLine = true
    )
}

/**
 * Чіп для фільтрації груп
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable (() -> Unit)? = null
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = icon,
        shape = RoundedCornerShape(12.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Color(0xFF667EEA),
            selectedLabelColor = Color.White
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = if (selected) Color.Transparent else Color(0xFF667EEA).copy(alpha = 0.3f)
        )
    )
}

/**
 * Хелпер для форматування кількості членів
 */
private fun getMemberText(count: Int): String {
    return when {
        count % 10 == 1 && count % 100 != 11 -> "учасник"
        count % 10 in 2..4 && (count % 100 < 10 || count % 100 >= 20) -> "учасники"
        else -> "учасників"
    }
}

/**
 * Хелпер для форматування останньої активності
 */
private fun formatLastActivity(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60000 -> "щойно"
        diff < 3600000 -> "${diff / 60000} хв тому"
        diff < 86400000 -> "${diff / 3600000} год тому"
        diff < 604800000 -> "${diff / 86400000} дн тому"
        else -> SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}

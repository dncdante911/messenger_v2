
package com.worldmates.messenger.ui.stories

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.data.model.Story

/**
 * Горизонтальний список особистих stories (для головного екрану)
 * Показується вгорі списку чатів, як в Instagram/Telegram
 */
@Composable
fun PersonalStoriesRow(
    stories: List<Story>,
    modifier: Modifier = Modifier,
    onCreateStoryClick: () -> Unit = {}
) {
    val context = LocalContext.current

    // Перевіряємо, чи є у користувача власні Stories
    val currentUserId = UserSession.userId
    val ownStories = stories.filter { it.userId == currentUserId }
    val hasOwnStories = ownStories.isNotEmpty()

    // Список stories згрупованих по користувачам (виключаємо власні stories)
    val groupedStories = remember(stories, currentUserId) {
        stories.filter { it.userId != currentUserId }.groupBy { it.userId }
    }

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 12.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Кнопка створення нової story або перегляду власних stories (завжди перша)
        item {
            CreateStoryButton(
                hasOwnStories = hasOwnStories,
                onCreateClick = onCreateStoryClick,
                onViewClick = {
                    // Відкриваємо перегляд власних stories
                    context.startActivity(Intent(context, StoryViewerActivity::class.java).apply {
                        putExtra("user_id", currentUserId)
                        putExtra("is_channel_story", false)
                    })
                }
            )
        }

        items(
            items = groupedStories.entries.toList(),
            key = { (userId, _) -> userId }
        ) { (userId, userStories) ->
            val firstStory = userStories.first()
            val hasUnviewed = userStories.any { !it.seen }

            StoryItem(
                story = firstStory,
                hasUnviewed = hasUnviewed,
                onClick = {
                    // Відкриваємо StoryViewerActivity
                    context.startActivity(Intent(context, StoryViewerActivity::class.java).apply {
                        putExtra("user_id", userId)
                        putExtra("is_channel_story", false)
                    })
                }
            )
        }
    }
}

/**
 * Кнопка створення нової story або перегляду власних stories
 */
@Composable
fun CreateStoryButton(
    hasOwnStories: Boolean,
    onCreateClick: () -> Unit,
    onViewClick: () -> Unit
) {
    // Observe avatar changes
    val currentAvatar by UserSession.avatarFlow.collectAsState()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = if (hasOwnStories) onViewClick else onCreateClick)
    ) {
        Box(
            contentAlignment = Alignment.BottomEnd,
            modifier = Modifier.size(64.dp)
        ) {
            // Аватар користувача
            AsyncImage(
                model = currentAvatar,
                contentDescription = "My avatar",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .border(
                        width = if (hasOwnStories) 3.dp else 2.dp,
                        color = if (hasOwnStories) {
                            // Градієнт для активних stories
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        shape = CircleShape
                    ),
                contentScale = ContentScale.Crop
            )

            // Іконка "+" тільки якщо немає stories
            if (!hasOwnStories) {
                Surface(
                    modifier = Modifier.size(24.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add story",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Text(
            text = if (hasOwnStories) "Ваша story" else "Створити",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = if (hasOwnStories) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/**
 * Елемент story користувача
 */
@Composable
fun StoryItem(
    story: Story,
    hasUnviewed: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.size(64.dp),
            contentAlignment = Alignment.Center
        ) {
            // Анімований градієнт для непереглянутих stories
            if (hasUnviewed) {
                val infiniteTransition = rememberInfiniteTransition(label = "story_gradient")
                val angle by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(3000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "angle"
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color(0xFFFF6B9D),
                                    Color(0xFFC239E8),
                                    Color(0xFF6C63FF),
                                    Color(0xFF00D4FF),
                                    Color(0xFFFF6B9D)
                                )
                            ),
                            shape = CircleShape
                        )
                        .padding(3.dp)
                )
            } else {
                // Сірий бордер для переглянутих
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(2.dp, Color.Gray.copy(alpha = 0.3f), CircleShape)
                )
            }

            // Аватар
            AsyncImage(
                model = story.userData?.avatar,
                contentDescription = story.userData?.name,
                modifier = Modifier
                    .size(if (hasUnviewed) 58.dp else 60.dp)
                    .clip(CircleShape)
                    .border(
                        3.dp,
                        MaterialTheme.colorScheme.surface,
                        CircleShape
                    ),
                contentScale = ContentScale.Crop
            )
        }

        // Ім'я користувача
        Text(
            text = story.userData?.name ?: "Unknown",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (hasUnviewed) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/**
 * Компактна версія stories row (без кнопки створення)
 * Для використання в інших місцях
 */
@Composable
fun CompactStoriesRow(
    stories: List<Story>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val groupedStories = remember(stories) {
        stories.groupBy { it.userId }
    }

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = groupedStories.entries.toList(),
            key = { (userId, _) -> userId }
        ) { (userId, userStories) ->
            val firstStory = userStories.first()
            val hasUnviewed = userStories.any { !it.seen }

            StoryItem(
                story = firstStory,
                hasUnviewed = hasUnviewed,
                onClick = {
                    context.startActivity(Intent(context, StoryViewerActivity::class.java).apply {
                        putExtra("user_id", userId)
                        putExtra("is_channel_story", false)
                    })
                }
            )
        }
    }
}
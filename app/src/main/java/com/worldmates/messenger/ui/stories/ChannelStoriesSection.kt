package com.worldmates.messenger.ui.stories

import android.content.Intent
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
import com.worldmates.messenger.data.model.Story

/**
 * Горизонтальний рядок stories каналів — чистий стиль як PersonalStoriesRow
 * Групує stories по page_id (каналу) і показує кружечки з аватарами каналів
 */
@Composable
fun ChannelStoriesRow(
    stories: List<Story>,
    adminChannelIds: List<Long> = emptyList(),
    onCreateClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Групуємо stories по каналу (page_id)
    val groupedByChannel = stories
        .filter { it.isChannelStory }
        .groupBy { it.pageId!! }

    // Показуємо тільки якщо є stories або є канали для створення
    if (groupedByChannel.isEmpty() && adminChannelIds.isEmpty()) return

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 12.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Кнопка "Створити story каналу" (якщо користувач — адмін каналу)
        if (adminChannelIds.isNotEmpty()) {
            item {
                CreateChannelStoryButton(onClick = onCreateClick)
            }
        }

        // Stories каналів
        items(groupedByChannel.entries.toList()) { (pageId, channelStories) ->
            val hasUnviewed = channelStories.any { it.isViewed == 0 }
            val firstStory = channelStories.first()
            val channelName = firstStory.channelData?.groupName ?: "Канал"
            val channelAvatar = firstStory.channelData?.avatar

            ChannelStoryCircle(
                channelName = channelName,
                avatarUrl = channelAvatar,
                hasUnviewed = hasUnviewed,
                storiesCount = channelStories.size,
                onClick = {
                    context.startActivity(Intent(context, StoryViewerActivity::class.java).apply {
                        putExtra("user_id", firstStory.userId)
                        putExtra("is_channel_story", true)
                        putExtra("page_id", pageId)
                    })
                }
            )
        }
    }
}

/**
 * Кнопка створення story каналу
 */
@Composable
private fun CreateChannelStoryButton(
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
            // Сіра обводка
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(2.dp, Color.Gray.copy(alpha = 0.3f), CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Створити story каналу",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Text(
            text = "Story каналу",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/**
 * Кружечок story каналу — аналог StoryItem з PersonalStoriesRow
 */
@Composable
fun ChannelStoryCircle(
    channelName: String,
    avatarUrl: String?,
    hasUnviewed: Boolean,
    storiesCount: Int,
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
            if (hasUnviewed) {
                // Анімований градієнтний бордер (як у особистих stories)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color(0xFF00BCD4), // Teal
                                    Color(0xFF2196F3), // Blue
                                    Color(0xFF673AB7), // Purple
                                    Color(0xFF00BCD4)  // Teal
                                )
                            ),
                            shape = CircleShape
                        )
                        .padding(3.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(2.dp, Color.Gray.copy(alpha = 0.3f), CircleShape)
                )
            }

            AsyncImage(
                model = avatarUrl,
                contentDescription = channelName,
                modifier = Modifier
                    .size(if (hasUnviewed) 58.dp else 60.dp)
                    .clip(CircleShape)
                    .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape),
                contentScale = ContentScale.Crop
            )
        }

        Text(
            text = channelName,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

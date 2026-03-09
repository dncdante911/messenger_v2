package com.worldmates.messenger.ui.stories

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.R
import com.worldmates.messenger.data.model.StoryReactions

/**
 * Панель реакцій на сторіс (emoji picker + лічильники).
 *
 * Використання у StoryViewerActivity / StoryViewerScreen:
 *   StoryReactionBar(
 *       currentReaction = story.reaction.type,
 *       reactionCounts  = story.reaction,
 *       onReact         = { reaction -> viewModel.reactToStory(storyId, reaction) }
 *   )
 */

private data class ReactionEntry(val emoji: String, val key: String, val labelRes: Int)

private val REACTIONS = listOf(
    ReactionEntry("👍", "like",  R.string.story_reaction_like),
    ReactionEntry("❤️", "love",  R.string.story_reaction_love),
    ReactionEntry("😂", "haha",  R.string.story_reaction_haha),
    ReactionEntry("😮", "wow",   R.string.story_reaction_wow),
    ReactionEntry("😢", "sad",   R.string.story_reaction_sad),
    ReactionEntry("😡", "angry", R.string.story_reaction_angry),
)

@Composable
fun StoryReactionBar(
    currentReaction: String?,
    reactionCounts: StoryReactions?,
    onReact: (String) -> Unit,
    modifier: Modifier = Modifier,
    showCounts: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // Picker рядок (розгортається при натисканні на основну кнопку)
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit  = fadeOut() + shrinkVertically()
        ) {
            ReactionPickerRow(
                currentReaction = currentReaction,
                onReact = { key ->
                    onReact(key)
                    expanded = false
                }
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Основна кнопка реакції
            ReactionToggleButton(
                currentReaction = currentReaction,
                onClick = { expanded = !expanded }
            )

            // Лічильник реакцій
            if (showCounts && reactionCounts != null) {
                val total = reactionCounts.total
                if (total > 0) {
                    Text(
                        text = stringResource(R.string.story_reactions_count, total),
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun ReactionToggleButton(
    currentReaction: String?,
    onClick: () -> Unit
) {
    val emoji = REACTIONS.find { it.key == currentReaction }?.emoji ?: "👍"
    val scale by animateFloatAsState(
        targetValue = if (currentReaction != null) 1.2f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "reactionScale"
    )

    Box(
        modifier = Modifier
            .size(40.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(
                if (currentReaction != null)
                    Color(0x88FFFFFF)
                else
                    Color(0x44FFFFFF)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji, fontSize = 20.sp)
    }
}

@Composable
private fun ReactionPickerRow(
    currentReaction: String?,
    onReact: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xCC1A1A1A))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        REACTIONS.forEach { entry ->
            val isSelected = currentReaction == entry.key
            val scale by animateFloatAsState(
                targetValue = if (isSelected) 1.3f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "emojiScale_${entry.key}"
            )

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(if (isSelected) Color(0x66FFFFFF) else Color.Transparent)
                    .clickable { onReact(entry.key) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = entry.emoji,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ─── Анонімний перегляд тогл ──────────────────────────────────────────────────

/**
 * Компонент перемикача анонімного перегляду.
 * Показується в налаштуваннях сторіс або як окремий елемент у переглядачі.
 *
 * @param isAnonymous  поточний стан
 * @param onToggle     callback при зміні
 */
@Composable
fun AnonymousViewToggle(
    isAnonymous: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x88000000))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.story_anonymous_view),
            fontSize = 13.sp,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = isAnonymous,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF2196F3)
            )
        )
    }
}

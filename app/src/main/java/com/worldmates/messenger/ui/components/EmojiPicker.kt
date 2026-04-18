package com.worldmates.messenger.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.worldmates.messenger.R
import com.worldmates.messenger.data.model.CustomEmoji
import com.worldmates.messenger.data.repository.EmojiRepository
import kotlinx.coroutines.launch

@Composable
fun EmojiPicker(
    onEmojiSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val emojiRepository = remember { EmojiRepository.getInstance(context) }

    val customEmojis by emojiRepository.customEmojis.collectAsState()
    val recentEmojis by emojiRepository.recentEmojis.collectAsState()

    LaunchedEffect(Unit) {
        scope.launch { emojiRepository.fetchEmojiPacks() }
    }

    // All categories: RECENT first (only when non-empty), then standard, then CUSTOM
    val categories = remember(recentEmojis) {
        buildList {
            if (recentEmojis.isNotEmpty()) add(EmojiCategory.RECENT)
            addAll(listOf(
                EmojiCategory.SMILEYS, EmojiCategory.GESTURES, EmojiCategory.ANIMALS,
                EmojiCategory.FOOD, EmojiCategory.ACTIVITIES, EmojiCategory.TRAVEL,
                EmojiCategory.OBJECTS, EmojiCategory.SYMBOLS, EmojiCategory.CUSTOM
            ))
        }
    }

    val pagerState = rememberPagerState(initialPage = 0) { categories.size }

    fun onEmoji(emoji: String) {
        emojiRepository.trackEmojiUsage(emoji)
        onEmojiSelected(emoji)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(320.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Column {
            // Category tabs (scrollable icon row)
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                edgePadding = 4.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = {}
            ) {
                categories.forEachIndexed { index, category ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = category.icon,
                            contentDescription = stringResource(category.titleRes),
                            modifier = Modifier.size(22.dp),
                            tint = if (pagerState.currentPage == index)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // Swipeable emoji grid
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { pageIndex ->
                val category = categories[pageIndex]
                val emojis = when (category) {
                    EmojiCategory.RECENT -> recentEmojis
                    EmojiCategory.CUSTOM -> customEmojis.map { it.code }
                    else -> category.emojis
                }

                if (category == EmojiCategory.CUSTOM) {
                    // Custom emojis with image rendering
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(8),
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        items(customEmojis) { customEmoji ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clickable { onEmoji(customEmoji.code) },
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = customEmoji.url,
                                    contentDescription = customEmoji.name ?: customEmoji.code,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                    }
                } else if (emojis.isEmpty() && category == EmojiCategory.RECENT) {
                    // Empty recent state
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🙂", fontSize = 32.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.emoji_no_recent),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(8),
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        items(emojis) { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clickable { onEmoji(emoji) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emoji, fontSize = 26.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}

enum class EmojiCategory(
    @StringRes val titleRes: Int,
    val icon: ImageVector,
    val emojis: List<String> = emptyList()
) {
    RECENT(
        titleRes = R.string.emoji_recent,
        icon = Icons.Default.History
    ),
    SMILEYS(
        titleRes = R.string.emoji_smileys,
        icon = Icons.Default.Face,
        emojis = listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂",
            "🙂", "🙃", "😉", "😊", "😇", "🥰", "😍", "🤩",
            "😘", "😗", "😚", "😙", "🥲", "😋", "😛", "😜",
            "🤪", "😝", "🤑", "🤗", "🤭", "🤫", "🤔", "🤐",
            "🤨", "😐", "😑", "😶", "😏", "😒", "🙄", "😬",
            "🤥", "😌", "😔", "😪", "🤤", "😴", "😷", "🤒",
            "🤕", "🤢", "🤮", "🤧", "🥵", "🥶", "🥴", "😵",
            "🤯", "🤠", "🥳", "🥸", "😎", "🤓", "🧐", "😕"
        )
    ),
    GESTURES(
        titleRes = R.string.emoji_gestures,
        icon = Icons.Default.ThumbUp,
        emojis = listOf(
            "👍", "👎", "👊", "✊", "🤛", "🤜", "🤞", "✌️",
            "🤟", "🤘", "👌", "🤌", "🤏", "👈", "👉", "👆",
            "👇", "☝️", "✋", "🤚", "🖐", "🖖", "👋", "🤙",
            "💪", "🦾", "🖕", "✍️", "🙏", "🦶", "🦵", "🦿",
            "👂", "🦻", "👃", "🧠", "🦷", "🦴", "👀", "👁",
            "👅", "👄", "💋", "🩸", "👶", "👧", "🧒", "👦"
        )
    ),
    ANIMALS(
        titleRes = R.string.emoji_animals,
        icon = Icons.Default.Pets,
        emojis = listOf(
            "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼",
            "🐨", "🐯", "🦁", "🐮", "🐷", "🐽", "🐸", "🐵",
            "🙈", "🙉", "🙊", "🐒", "🐔", "🐧", "🐦", "🐤",
            "🐣", "🐥", "🦆", "🦅", "🦉", "🦇", "🐺", "🐗",
            "🐴", "🦄", "🐝", "🪱", "🐛", "🦋", "🐌", "🐞",
            "🐜", "🪰", "🪲", "🪳", "🦟", "🦗", "🕷", "🕸"
        )
    ),
    FOOD(
        titleRes = R.string.emoji_food,
        icon = Icons.Default.Restaurant,
        emojis = listOf(
            "🍏", "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇",
            "🍓", "🫐", "🍈", "🍒", "🍑", "🥭", "🍍", "🥥",
            "🥝", "🍅", "🍆", "🥑", "🥦", "🥬", "🥒", "🌶",
            "🫑", "🌽", "🥕", "🫒", "🧄", "🧅", "🥔", "🍠",
            "🥐", "🥯", "🍞", "🥖", "🥨", "🧀", "🥚", "🍳",
            "🧈", "🥞", "🧇", "🥓", "🥩", "🍗", "🍖", "🦴"
        )
    ),
    ACTIVITIES(
        titleRes = R.string.emoji_activities,
        icon = Icons.Default.SportsBasketball,
        emojis = listOf(
            "⚽️", "🏀", "🏈", "⚾️", "🥎", "🎾", "🏐", "🏉",
            "🥏", "🎱", "🪀", "🏓", "🏸", "🏒", "🏑", "🥍",
            "🏏", "🪃", "🥅", "⛳️", "🪁", "🏹", "🎣", "🤿",
            "🥊", "🥋", "🎽", "🛹", "🛼", "🛷", "⛸", "🥌",
            "🎿", "⛷", "🏂", "🪂", "🏋️", "🤼", "🤸", "🤺",
            "⛹️", "🤾", "🏌️", "🏇", "🧘", "🏊", "🤽", "🚣"
        )
    ),
    TRAVEL(
        titleRes = R.string.emoji_travel,
        icon = Icons.Default.Flight,
        emojis = listOf(
            "🚗", "🚕", "🚙", "🚌", "🚎", "🏎", "🚓", "🚑",
            "🚒", "🚐", "🛻", "🚚", "🚛", "🚜", "🦯", "🦽",
            "🦼", "🛴", "🚲", "🛵", "🏍", "🛺", "🚨", "🚔",
            "🚍", "🚘", "🚖", "🚡", "🚠", "🚟", "🚃", "🚋",
            "🚞", "🚝", "🚄", "🚅", "🚈", "🚂", "🚆", "🚇",
            "🚊", "🚉", "✈️", "🛫", "🛬", "🛩", "💺", "🛰"
        )
    ),
    OBJECTS(
        titleRes = R.string.emoji_objects,
        icon = Icons.Default.Build,
        emojis = listOf(
            "⌚️", "📱", "📲", "💻", "⌨️", "🖥", "🖨", "🖱",
            "🖲", "🕹", "🗜", "💽", "💾", "💿", "📀", "📼",
            "📷", "📸", "📹", "🎥", "📽", "🎞", "📞", "☎️",
            "📟", "📠", "📺", "📻", "🎙", "🎚", "🎛", "🧭",
            "⏱", "⏲", "⏰", "🕰", "⌛️", "⏳", "📡", "🔋",
            "🔌", "💡", "🔦", "🕯", "🪔", "🧯", "🛢", "💸"
        )
    ),
    SYMBOLS(
        titleRes = R.string.emoji_symbols,
        icon = Icons.Default.Star,
        emojis = listOf(
            "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍",
            "🤎", "💔", "❣️", "💕", "💞", "💓", "💗", "💖",
            "💘", "💝", "💟", "☮️", "✝️", "☪️", "🕉", "☸️",
            "✡️", "🔯", "🕎", "☯️", "☦️", "🛐", "⛎", "♈️",
            "♉️", "♊️", "♋️", "♌️", "♍️", "♎️", "♏️", "♐️",
            "♑️", "♒️", "♓️", "🆔", "⚛️", "🉑", "☢️", "☣️"
        )
    ),
    CUSTOM(
        titleRes = R.string.emoji_custom,
        icon = Icons.Default.AddCircle
    )
}

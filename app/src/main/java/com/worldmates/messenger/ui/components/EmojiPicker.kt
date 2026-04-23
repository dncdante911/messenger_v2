package com.worldmates.messenger.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.worldmates.messenger.R
import com.worldmates.messenger.data.repository.EmojiRepository
import kotlinx.coroutines.launch

/**
 * Modern emoji picker with category tabs at the bottom (Telegram / Viber style).
 *
 * @param onEmojiSelected Called when the user taps an emoji.
 * @param onDismiss       Called when the picker should be closed.
 * @param onBackspace     Optional: called when user taps the backspace button.
 *                        If null, the backspace button is hidden.
 */
@Composable
fun EmojiPicker(
    onEmojiSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    onBackspace: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val emojiRepository = remember { EmojiRepository.getInstance(context) }

    val customEmojis by emojiRepository.customEmojis.collectAsState()
    val recentEmojis by emojiRepository.recentEmojis.collectAsState()

    LaunchedEffect(Unit) { scope.launch { emojiRepository.fetchEmojiPacks() } }

    val categories = remember(recentEmojis) {
        buildList {
            if (recentEmojis.isNotEmpty()) add(EmojiCategory.RECENT)
            addAll(listOf(
                EmojiCategory.SMILEYS, EmojiCategory.PEOPLE, EmojiCategory.GESTURES,
                EmojiCategory.ANIMALS, EmojiCategory.FOOD, EmojiCategory.ACTIVITIES,
                EmojiCategory.TRAVEL, EmojiCategory.OBJECTS, EmojiCategory.SYMBOLS,
                EmojiCategory.CUSTOM
            ))
        }
    }

    val pagerState = rememberPagerState(initialPage = 0) { categories.size }
    val cs = MaterialTheme.colorScheme

    fun onEmoji(emoji: String) {
        emojiRepository.trackEmojiUsage(emoji)
        onEmojiSelected(emoji)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp),
        color = cs.surface,
        shadowElevation = 4.dp
    ) {
        Column {
            // ── Swipeable emoji grid ──────────────────────────────────────────
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { pageIndex ->
                val category = categories[pageIndex]

                when {
                    category == EmojiCategory.CUSTOM && customEmojis.isNotEmpty() -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(8),
                            modifier = Modifier.fillMaxSize().padding(4.dp),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            items(customEmojis) { ce ->
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onEmoji(ce.code) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = ce.url,
                                        contentDescription = ce.name ?: ce.code,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                        }
                    }

                    category == EmojiCategory.RECENT && recentEmojis.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🙂", fontSize = 36.sp)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.emoji_no_recent),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurface.copy(alpha = 0.45f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    else -> {
                        val emojis = when (category) {
                            EmojiCategory.RECENT -> recentEmojis
                            else -> category.emojis
                        }
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(8),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(emojis) { emoji ->
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onEmoji(emoji) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(emoji, fontSize = 26.sp, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
            }

            // ── Bottom category tab bar (Telegram-style) ──────────────────────
            HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.2f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cs.surfaceVariant.copy(alpha = 0.4f))
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(modifier = Modifier.weight(1f)) {
                    itemsIndexed(categories) { index, category ->
                        val selected = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) cs.primary.copy(alpha = 0.15f)
                                    else Color.Transparent
                                )
                                .clickable { scope.launch { pagerState.animateScrollToPage(index) } },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = category.tabIcon,
                                fontSize = if (selected) 22.sp else 20.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                if (onBackspace != null) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onBackspace),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Backspace,
                            contentDescription = null,
                            tint = cs.onSurfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

enum class EmojiCategory(
    @StringRes val titleRes: Int,
    val tabIcon: String,
    val emojis: List<String> = emptyList()
) {
    RECENT(
        titleRes = R.string.emoji_recent,
        tabIcon = "🕐"
    ),
    SMILEYS(
        titleRes = R.string.emoji_smileys,
        tabIcon = "😀",
        emojis = listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂",
            "🙂", "🙃", "😉", "😊", "😇", "🥰", "😍", "🤩",
            "😘", "😗", "😚", "😙", "🥲", "😋", "😛", "😜",
            "🤪", "😝", "🤑", "🤗", "🤭", "🤫", "🤔", "🤐",
            "🤨", "😐", "😑", "😶", "😏", "😒", "🙄", "😬",
            "🤥", "😌", "😔", "😪", "🤤", "😴", "😷", "🤒",
            "🤕", "🤢", "🤮", "🤧", "🥵", "🥶", "🥴", "😵",
            "🤯", "🤠", "🥳", "🥸", "😎", "🤓", "🧐", "😕",
            "😟", "🙁", "☹️", "😮", "😯", "😲", "😳", "🥺",
            "😦", "😧", "😨", "😰", "😥", "😢", "😭", "😱",
            "😖", "😣", "😞", "😓", "😩", "😫", "🥱", "😤",
            "😡", "😠", "🤬", "😈", "👿", "💀", "☠️", "💩",
            "🤡", "👹", "👺", "👻", "👽", "👾", "🤖", "😺",
            "😸", "😹", "😻", "😼", "😽", "🙀", "😿", "😾"
        )
    ),
    PEOPLE(
        titleRes = R.string.emoji_people,
        tabIcon = "👤",
        emojis = listOf(
            "👶", "🧒", "👦", "👧", "🧑", "👱", "👨", "🧔",
            "👩", "🧓", "👴", "👵", "🙍", "🙎", "🙅", "🙆",
            "💁", "🙋", "🧏", "🙇", "🤦", "🤷", "👮", "🕵️",
            "💂", "🥷", "👷", "🫅", "🤴", "👸", "👳", "👲",
            "🧕", "🤵", "👰", "🤰", "🤱", "👼", "🎅", "🤶",
            "🦸", "🦹", "🧙", "🧝", "🧛", "🧟", "🧞", "🧜",
            "🧚", "🧑‍⚕️", "👨‍⚕️", "👩‍⚕️", "🧑‍🎓", "👨‍🎓", "👩‍🎓", "🧑‍🏫",
            "👨‍🍳", "👩‍🍳", "🧑‍🌾", "👨‍🔧", "👩‍🔧", "🧑‍💻", "👨‍💻", "👩‍💻",
            "🧑‍🎤", "👨‍🎨", "👩‍🎨", "🧑‍✈️", "👨‍🚀", "👩‍🚀", "🧑‍🚒", "👨‍⚖️",
            "👫", "👬", "👭", "💏", "💑", "👪", "👨‍👩‍👦", "👨‍👩‍👧"
        )
    ),
    GESTURES(
        titleRes = R.string.emoji_gestures,
        tabIcon = "👍",
        emojis = listOf(
            "👋", "🤚", "🖐️", "✋", "🖖", "🤙", "🫱", "🫲",
            "🫳", "🫴", "👌", "🤌", "🤏", "✌️", "🤞", "🫰",
            "🤟", "🤘", "👈", "👉", "👆", "🖕", "👇", "☝️",
            "🫵", "👍", "👎", "✊", "👊", "🤛", "🤜", "🤝",
            "🙏", "🫶", "👏", "🙌", "🫙", "🤲", "💪", "🦾",
            "🦿", "🦵", "🦶", "👂", "🦻", "👃", "🧠", "🫀",
            "🫁", "🦷", "🦴", "👀", "👁️", "👅", "👄", "🫦"
        )
    ),
    ANIMALS(
        titleRes = R.string.emoji_animals,
        tabIcon = "🐶",
        emojis = listOf(
            "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼",
            "🐨", "🐯", "🦁", "🐮", "🐷", "🐽", "🐸", "🐵",
            "🙈", "🙉", "🙊", "🐒", "🐔", "🐧", "🐦", "🐤",
            "🐣", "🐥", "🦆", "🦅", "🦉", "🦇", "🐺", "🐗",
            "🐴", "🦄", "🐝", "🪱", "🐛", "🦋", "🐌", "🐞",
            "🐜", "🪰", "🪲", "🪳", "🦟", "🦗", "🕷️", "🕸️",
            "🦂", "🐢", "🐍", "🦎", "🦖", "🦕", "🐙", "🦑",
            "🦐", "🦞", "🦀", "🐡", "🐠", "🐟", "🐬", "🐳"
        )
    ),
    FOOD(
        titleRes = R.string.emoji_food,
        tabIcon = "🍎",
        emojis = listOf(
            "🍏", "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇",
            "🍓", "🫐", "🍈", "🍒", "🍑", "🥭", "🍍", "🥥",
            "🥝", "🍅", "🍆", "🥑", "🥦", "🥬", "🥒", "🌶️",
            "🫑", "🌽", "🥕", "🫒", "🧄", "🧅", "🥔", "🍠",
            "🥐", "🥯", "🍞", "🥖", "🥨", "🧀", "🥚", "🍳",
            "🧈", "🥞", "🧇", "🥓", "🥩", "🍗", "🍖", "🌭",
            "🍔", "🍟", "🍕", "🫓", "🥪", "🥙", "🧆", "🌮",
            "🌯", "🫔", "🥗", "🥘", "🫕", "🍜", "🍝", "🍲"
        )
    ),
    ACTIVITIES(
        titleRes = R.string.emoji_activities,
        tabIcon = "⚽",
        emojis = listOf(
            "⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉",
            "🥏", "🎱", "🪀", "🏓", "🏸", "🏒", "🏑", "🥍",
            "🏏", "🪃", "🥅", "⛳", "🪁", "🏹", "🎣", "🤿",
            "🥊", "🥋", "🎽", "🛹", "🛼", "🛷", "⛸️", "🥌",
            "🎿", "⛷️", "🏂", "🪂", "🏋️", "🤼", "🤸", "🤺",
            "⛹️", "🤾", "🏌️", "🏇", "🧘", "🏊", "🤽", "🚣",
            "🧗", "🚵", "🚴", "🏆", "🥇", "🥈", "🥉", "🏅",
            "🎖️", "🎗️", "🎟️", "🎫", "🎪", "🎭", "🎨", "🎬"
        )
    ),
    TRAVEL(
        titleRes = R.string.emoji_travel,
        tabIcon = "✈️",
        emojis = listOf(
            "🚗", "🚕", "🚙", "🚌", "🚎", "🏎️", "🚓", "🚑",
            "🚒", "🚐", "🛻", "🚚", "🚛", "🚜", "🛴", "🚲",
            "🛵", "🏍️", "🛺", "🚨", "🚔", "🚍", "🚘", "🚖",
            "🚡", "🚠", "🚟", "🚃", "🚋", "🚞", "🚝", "🚄",
            "🚅", "🚈", "🚂", "🚆", "🚇", "🚊", "🚉", "✈️",
            "🛫", "🛬", "🛩️", "💺", "🛰️", "🚀", "🛸", "🚁",
            "🛶", "⛵", "🚤", "🛥️", "🛳️", "⛴️", "🚢", "⚓",
            "🗺️", "🗾", "🧭", "🏔️", "⛰️", "🌋", "🗻", "🏕️"
        )
    ),
    OBJECTS(
        titleRes = R.string.emoji_objects,
        tabIcon = "💡",
        emojis = listOf(
            "⌚", "📱", "📲", "💻", "⌨️", "🖥️", "🖨️", "🖱️",
            "🖲️", "🕹️", "💾", "💿", "📀", "📼", "📷", "📸",
            "📹", "🎥", "📽️", "🎞️", "📞", "☎️", "📟", "📠",
            "📺", "📻", "🎙️", "🎚️", "🎛️", "🧭", "⏰", "⌛",
            "⏳", "📡", "🔋", "🪫", "🔌", "💡", "🔦", "🕯️",
            "🪔", "🧯", "💰", "💴", "💵", "💶", "💷", "💸",
            "💳", "🪙", "💎", "⚖️", "🪜", "🧲", "🔧", "🪛",
            "🔨", "⚒️", "🛠️", "⛏️", "🪚", "🔑", "🗝️", "🔒"
        )
    ),
    SYMBOLS(
        titleRes = R.string.emoji_symbols,
        tabIcon = "❤️",
        emojis = listOf(
            "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍",
            "🤎", "❤️‍🔥", "❤️‍🩹", "💔", "❣️", "💕", "💞", "💓",
            "💗", "💖", "💘", "💝", "💟", "☮️", "✝️", "☪️",
            "🕉️", "☸️", "✡️", "🔯", "🕎", "☯️", "☦️", "🛐",
            "♈", "♉", "♊", "♋", "♌", "♍", "♎", "♏",
            "♐", "♑", "♒", "♓", "⛎", "🔀", "🔁", "🔂",
            "▶️", "⏩", "⏭️", "⏯️", "◀️", "⏪", "⏮️", "🔼",
            "⏫", "🔽", "⏬", "⏸️", "⏹️", "⏺️", "🎦", "🔅",
            "🔆", "📶", "📳", "📴", "📵", "📴", "🔕", "🔔",
            "🔈", "🔉", "🔊", "📢", "📣", "💯", "🔔", "🛎️",
            "✅", "☑️", "❎", "🆗", "🆕", "🆙", "🆒", "🆓",
            "🆖", "🆘", "🆙", "⭕", "❌", "❓", "❔", "❕",
            "❗", "💱", "💲", "♻️", "🔱", "📛", "🔰", "⭐",
            "🌟", "✨", "💫", "🔥", "🎉", "🎊", "🎋", "🎍"
        )
    ),
    CUSTOM(
        titleRes = R.string.emoji_custom,
        tabIcon = "⭐"
    )
}

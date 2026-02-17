package com.worldmates.messenger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.worldmates.messenger.data.model.CustomEmoji
import com.worldmates.messenger.data.repository.EmojiRepository
import kotlinx.coroutines.launch

/**
 * 😊 Emoji Picker - Повноцінна клавіатура емоджі
 *
 * Використання:
 * ```
 * var showEmojiPicker by remember { mutableStateOf(false) }
 *
 * if (showEmojiPicker) {
 *     EmojiPicker(
 *         onEmojiSelected = { emoji ->
 *             messageText += emoji
 *         },
 *         onDismiss = { showEmojiPicker = false }
 *     )
 * }
 * ```
 */
@Composable
fun EmojiPicker(
    onEmojiSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf(EmojiCategory.SMILEYS) }
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val emojiRepository = remember { EmojiRepository.getInstance(context) }

    // Завантажуємо кастомні емоджі з API
    val customEmojis by emojiRepository.customEmojis.collectAsState()

    LaunchedEffect(Unit) {
        scope.launch {
            emojiRepository.fetchEmojiPacks()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(350.dp),
        color = colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Column {
            // Заголовок
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Емоджі",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Закрити")
                }
            }

            Divider()

            // Сітка емоджі
            LazyVerticalGrid(
                columns = GridCells.Fixed(8),
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp),
                contentPadding = PaddingValues(4.dp)
            ) {
                if (selectedCategory == EmojiCategory.CUSTOM) {
                    // Показуємо кастомні емоджі
                    items(customEmojis) { customEmoji ->
                        CustomEmojiItem(
                            customEmoji = customEmoji,
                            onClick = { onEmojiSelected(customEmoji.code) }
                        )
                    }
                } else {
                    // Показуємо стандартні емоджі
                    items(selectedCategory.emojis) { emoji ->
                        EmojiItem(
                            emoji = emoji,
                            onClick = { onEmojiSelected(emoji) }
                        )
                    }
                }
            }

            Divider()

            // Категорії
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colorScheme.surfaceVariant)
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                EmojiCategory.values().forEach { category ->
                    CategoryTab(
                        category = category,
                        isSelected = selectedCategory == category,
                        onClick = { selectedCategory = category }
                    )
                }
            }
        }
    }
}

/**
 * Елемент емоджі в сітці
 */
@Composable
private fun EmojiItem(
    emoji: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            fontSize = 28.sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Елемент кастомного емоджі в сітці (зображення)
 */
@Composable
private fun CustomEmojiItem(
    customEmoji: CustomEmoji,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = customEmoji.url,
            contentDescription = customEmoji.name ?: customEmoji.code,
            modifier = Modifier.size(32.dp)
        )
    }
}

/**
 * Вкладка категорії
 */
@Composable
private fun CategoryTab(
    category: EmojiCategory,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp)
    ) {
        Icon(
            imageVector = category.icon,
            contentDescription = category.title,
            tint = if (isSelected) colorScheme.primary else colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Категорії емоджі
 */
enum class EmojiCategory(
    val title: String,
    val icon: ImageVector,
    val emojis: List<String>
) {
    SMILEYS(
        title = "Смайлики",
        icon = Icons.Default.Face,
        emojis = listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂",
            "🙂", "🙃", "😉", "😊", "😇", "🥰", "😍", "🤩",
            "😘", "😗", "😚", "😙", "🥲", "😋", "😛", "😜",
            "🤪", "😝", "🤑", "🤗", "🤭", "🤫", "🤔", "🤐",
            "🤨", "😐", "😑", "😶", "😏", "😒", "🙄", "😬",
            "🤥", "😌", "😔", "😪", "🤤", "😴", "😷", "🤒",
            "🤕", "🤢", "🤮", "🤧", "🥵", "🥶", "😶‍🌫️", "🥴",
            "😵", "😵‍💫", "🤯", "🤠", "🥳", "🥸", "😎", "🤓"
        )
    ),
    GESTURES(
        title = "Жести",
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
        title = "Тварини",
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
        title = "Їжа",
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
        title = "Активності",
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
        title = "Подорожі",
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
        title = "Об'єкти",
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
        title = "Символи",
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
        title = "Кастомні",
        icon = Icons.Default.AddCircle,
        emojis = emptyList()  // Кастомні емоджі завантажуються з API
    )
}

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.worldmates.messenger.data.model.Sticker
import com.worldmates.messenger.data.model.StickerPack
import com.worldmates.messenger.data.repository.StickerRepository
import com.worldmates.messenger.data.stickers.EmbeddedStickerPacks
import kotlinx.coroutines.launch

/**
 * 🎭 Sticker Picker - Панель вибору стікерів
 *
 * Використання:
 * ```
 * var showStickerPicker by remember { mutableStateOf(false) }
 *
 * if (showStickerPicker) {
 *     StickerPicker(
 *         onStickerSelected = { sticker ->
 *             viewModel.sendSticker(sticker.id)
 *         },
 *         onDismiss = { showStickerPicker = false }
 *     )
 * }
 * ```
 */
@Composable
fun StickerPicker(
    onStickerSelected: (Sticker) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stickerRepository = remember { StickerRepository.getInstance(context) }

    // ✨ Вбудовані анімовані паки (200 стікерів)
    val embeddedPacks = remember { EmbeddedStickerPacks.getAllEmbeddedPacks() }

    // Стандартний пак стікерів (emoji fallback)
    val standardPack = remember {
        StickerPack(
            id = 1,
            name = "Стандартний пак",
            description = "Базові стікери для спілкування",
            stickers = getStandardStickers(),
            isActive = true
        )
    }

    // Завантажуємо кастомні паки з API (Strapi/CDN)
    val customPacks by stickerRepository.stickerPacks.collectAsState()

    // Об'єднуємо всі паки: вбудовані + стандартні + з API
    val activePacks = remember(customPacks) {
        val packs = mutableListOf<StickerPack>()
        // Спочатку вбудовані анімовані паки
        packs.addAll(embeddedPacks)
        // Потім стандартні emoji
        packs.add(standardPack)
        // Потім паки з Strapi/CDN
        packs.addAll(customPacks.filter { it.isActive && it.stickers?.isNotEmpty() == true })
        packs
    }

    var selectedPack by remember { mutableStateOf(embeddedPacks.firstOrNull() ?: standardPack) }

    // Завантажуємо паки при відкритті
    LaunchedEffect(Unit) {
        scope.launch {
            stickerRepository.fetchStickerPacks()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(400.dp),
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
                    text = "Стікери",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Закрити")
                }
            }

            Divider()

            // Сітка стікерів
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(selectedPack.stickers ?: emptyList()) { sticker ->
                    StickerItem(
                        sticker = sticker,
                        onClick = {
                            onStickerSelected(sticker)
                            onDismiss()
                        }
                    )
                }
            }

            Divider()

            // Паки стікерів (якщо більше одного)
            if (activePacks.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colorScheme.surfaceVariant)
                        .padding(vertical = 8.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    activePacks.forEach { pack ->
                        StickerPackTab(
                            pack = pack,
                            isSelected = selectedPack.id == pack.id,
                            onClick = { selectedPack = pack }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Елемент стікера в сітці
 * Підтримує як анімовані (Lottie/TGS), так і статичні стікери
 */
@Composable
private fun StickerItem(
    sticker: Sticker,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val stickerUrl = when {
        // Вбудований анімований стікер (lottie://)
        sticker.fileUrl.startsWith("lottie://") -> {
            EmbeddedStickerPacks.getEmbeddedStickerResourceUrl(context, sticker.fileUrl)
                ?: sticker.emoji // Fallback на emoji якщо ресурс не знайдено
        }
        // Звичайний стікер з URL
        else -> sticker.thumbnailUrl ?: sticker.fileUrl
    }

    Card(
        modifier = Modifier
            .size(80.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when {
                // Анімований стікер (Lottie, TGS, або GIF)
                sticker.format in listOf("lottie", "tgs", "gif") ||
                stickerUrl?.endsWith(".json") == true ||
                stickerUrl?.endsWith(".tgs") == true ||
                stickerUrl?.endsWith(".gif") == true -> {
                    AnimatedStickerView(
                        url = stickerUrl ?: "",
                        size = 64.dp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                // Emoji fallback (як текст)
                stickerUrl == sticker.emoji -> {
                    Text(
                        text = sticker.emoji ?: "🎭",
                        fontSize = 40.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                // Статичне зображення (WebP, PNG)
                else -> {
                    AsyncImage(
                        model = stickerUrl,
                        contentDescription = sticker.emoji,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}

/**
 * Вкладка паку стікерів
 */
@Composable
private fun StickerPackTab(
    pack: StickerPack,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Card(
        modifier = Modifier
            .size(60.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                colorScheme.primaryContainer
            } else {
                colorScheme.surface
            }
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, colorScheme.primary)
        } else null
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (pack.iconUrl != null || pack.thumbnailUrl != null) {
                AsyncImage(
                    model = pack.iconUrl ?: pack.thumbnailUrl,
                    contentDescription = pack.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                )
            } else {
                Text(
                    text = pack.name.take(2),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) {
                        colorScheme.primary
                    } else {
                        colorScheme.onSurface
                    }
                )
            }
        }
    }
}

/**
 * Стандартний пак стікерів (emoji-стікери як приклад)
 */
private fun getStandardStickers(): List<Sticker> {
    return listOf(
        // Емоції
        Sticker(1, 1, "😊", "😊", "😊", listOf("smile", "happy")),
        Sticker(2, 1, "😂", "😂", "😂", listOf("laugh", "lol")),
        Sticker(3, 1, "😍", "😍", "😍", listOf("love", "heart")),
        Sticker(4, 1, "🥰", "🥰", "🥰", listOf("love", "cute")),
        Sticker(5, 1, "😎", "😎", "😎", listOf("cool", "sunglasses")),
        Sticker(6, 1, "🤔", "🤔", "🤔", listOf("thinking", "hmm")),
        Sticker(7, 1, "😅", "😅", "😅", listOf("sweat", "nervous")),
        Sticker(8, 1, "😭", "😭", "😭", listOf("cry", "sad")),
        // Жести
        Sticker(9, 1, "👍", "👍", "👍", listOf("thumbs", "up", "ok")),
        Sticker(10, 1, "👎", "👎", "👎", listOf("thumbs", "down")),
        Sticker(11, 1, "👏", "👏", "👏", listOf("clap", "applause")),
        Sticker(12, 1, "🙏", "🙏", "🙏", listOf("pray", "please")),
        Sticker(13, 1, "✌️", "✌️", "✌️", listOf("peace", "victory")),
        Sticker(14, 1, "👋", "👋", "👋", listOf("wave", "hello")),
        Sticker(15, 1, "🤝", "🤝", "🤝", listOf("handshake", "deal")),
        Sticker(16, 1, "💪", "💪", "💪", listOf("strong", "muscle")),
        // Серця
        Sticker(17, 1, "❤️", "❤️", "❤️", listOf("heart", "love")),
        Sticker(18, 1, "💙", "💙", "💙", listOf("blue", "heart")),
        Sticker(19, 1, "💚", "💚", "💚", listOf("green", "heart")),
        Sticker(20, 1, "💛", "💛", "💛", listOf("yellow", "heart")),
        Sticker(21, 1, "💜", "💜", "💜", listOf("purple", "heart")),
        Sticker(22, 1, "🧡", "🧡", "🧡", listOf("orange", "heart")),
        Sticker(23, 1, "🖤", "🖤", "🖤", listOf("black", "heart")),
        Sticker(24, 1, "🤍", "🤍", "🤍", listOf("white", "heart")),
        // Святкування
        Sticker(25, 1, "🎉", "🎉", "🎉", listOf("party", "celebrate")),
        Sticker(26, 1, "🎊", "🎊", "🎊", listOf("confetti", "party")),
        Sticker(27, 1, "🎁", "🎁", "🎁", listOf("gift", "present")),
        Sticker(28, 1, "🎂", "🎂", "🎂", listOf("cake", "birthday")),
        Sticker(29, 1, "🎈", "🎈", "🎈", listOf("balloon", "party")),
        Sticker(30, 1, "🎆", "🎆", "🎆", listOf("fireworks", "celebrate")),
        Sticker(31, 1, "✨", "✨", "✨", listOf("sparkles", "shine")),
        Sticker(32, 1, "🌟", "🌟", "🌟", listOf("star", "shine"))
    )
}

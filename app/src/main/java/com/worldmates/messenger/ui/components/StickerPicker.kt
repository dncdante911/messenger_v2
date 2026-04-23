package com.worldmates.messenger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.res.stringResource
import com.worldmates.messenger.R
import com.worldmates.messenger.data.model.Sticker
import com.worldmates.messenger.data.model.StickerPack
import com.worldmates.messenger.data.repository.StickerRepository
import com.worldmates.messenger.data.repository.StrapiStickerRepository
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
    val standardPackName = stringResource(R.string.sticker_standard_pack_name)
    val standardPackDesc = stringResource(R.string.sticker_standard_pack_desc)
    val standardPack = remember(standardPackName, standardPackDesc) {
        StickerPack(
            id = 1,
            name = standardPackName,
            description = standardPackDesc,
            stickers = getStandardStickers(),
            isActive = true
        )
    }

    // Завантажуємо кастомні паки з API (Strapi/CDN)
    val customPacks by stickerRepository.stickerPacks.collectAsState()

    // Об'єднуємо всі паки: вбудовані + стандартні + з API
    val activePacks = remember(customPacks) {
        val packs = mutableListOf<StickerPack>()
        packs.addAll(embeddedPacks)
        packs.add(standardPack)
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

    var showManageSheet by remember { mutableStateOf(false) }

    if (showManageSheet) {
        StickerPackManagementSheet(onDismiss = { showManageSheet = false })
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
                    text = stringResource(R.string.stickers_label),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showManageSheet = true }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.sticker_manage_title))
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }
            }

            Divider()

            // Pack name label
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = getPackTabIcon(selectedPack), fontSize = 14.sp)
                Text(
                    text = selectedPack.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val columns = 4
            val itemSize = 80.dp
            val animSize = 64.dp

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                contentPadding = PaddingValues(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(selectedPack.stickers ?: emptyList()) { sticker ->
                    StickerItem(
                        sticker = sticker,
                        itemSize = itemSize,
                        animSize = animSize,
                        onClick = {
                            onStickerSelected(sticker)
                            onDismiss()
                        }
                    )
                }
            }

            Divider()

            // Нижня панель паків — горизонтальний скролл з emoji-іконками
            if (activePacks.size > 1) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colorScheme.surfaceVariant)
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(activePacks) { pack ->
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
 * Елемент стікера в сітці.
 * [itemSize] — розмір картки, [animSize] — розмір анімації всередині.
 */
@Composable
private fun StickerItem(
    sticker: Sticker,
    onClick: () -> Unit,
    itemSize: androidx.compose.ui.unit.Dp = 80.dp,
    animSize: androidx.compose.ui.unit.Dp = 64.dp,
) {
    val context = LocalContext.current
    val stickerUrl = when {
        sticker.fileUrl.startsWith("lottie://") -> {
            EmbeddedStickerPacks.getEmbeddedStickerResourceUrl(context, sticker.fileUrl)
                ?: sticker.emoji
        }
        else -> sticker.thumbnailUrl ?: sticker.fileUrl
    }

    Box(
        modifier = Modifier
            .size(itemSize)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when {
            sticker.format in listOf("lottie", "tgs", "gif") ||
            stickerUrl?.endsWith(".json") == true ||
            stickerUrl?.endsWith(".tgs") == true ||
            stickerUrl?.endsWith(".gif") == true -> {
                AnimatedStickerView(
                    url = stickerUrl ?: "",
                    size = animSize
                )
            }
            stickerUrl == sticker.emoji -> {
                Text(
                    text = sticker.emoji ?: "🎭",
                    fontSize = (animSize.value * 0.6).sp
                )
            }
            else -> {
                AsyncImage(
                    model = stickerUrl,
                    contentDescription = sticker.emoji,
                    modifier = Modifier.size(animSize)
                )
            }
        }
    }
}

/**
 * Таб паку стікерів — показує emoji-іконку та підпис назви.
 */
@Composable
private fun StickerPackTab(
    pack: StickerPack,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .width(52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = getPackTabIcon(pack),
            fontSize = 22.sp,
            textAlign = TextAlign.Center
        )
        Text(
            text = pack.name.split(" ").first().take(8),
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = if (isSelected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant
        )
    }
}

/** Returns a representative emoji icon for a sticker pack tab. */
private fun getPackTabIcon(pack: StickerPack): String = when {
    pack.name.contains("Емоці", ignoreCase = true) ||
        pack.name.contains("Emotion", ignoreCase = true)                  -> "😊"
    pack.name.contains("Тварин", ignoreCase = true) ||
        pack.name.contains("Animal", ignoreCase = true)                   -> "🐾"
    pack.name.contains("Святкув", ignoreCase = true) ||
        pack.name.contains("Celebr", ignoreCase = true)                   -> "🎉"
    pack.name.contains("Жест", ignoreCase = true) ||
        pack.name.contains("Gesture", ignoreCase = true)                  -> "👋"
    pack.name.contains("Серц", ignoreCase = true) ||
        pack.name.contains("Heart", ignoreCase = true)                    -> "❤️"
    pack.name.contains("WorldMates", ignoreCase = true)                   -> "🎭"
    pack.name.contains("Emoji", ignoreCase = true) ||
        pack.name.contains("Емодж", ignoreCase = true)                    -> "😄"
    else                                                                   -> "🗂️"
}

/**
 * ⚙️ StickerPackManagementSheet — Управління паками стікерів
 *
 * Показує всі паки з Strapi CDN + вбудовані, з можливістю
 * активувати / деактивувати кожен пак через StickerRepository.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerPackManagementSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val strapiRepo = remember { StrapiStickerRepository.getInstance(context) }
    val stickerRepo = remember { StickerRepository.getInstance(context) }

    val allStrapiPacks by strapiRepo.stickerPacks.collectAsState()
    val isLoading by strapiRepo.isLoading.collectAsState()
    // Track toggled packs locally (mirrors server state; true = enabled)
    val enabledPackIds = remember { mutableStateMapOf<Int, Boolean>() }

    // Fetch from CDN on open
    LaunchedEffect(Unit) { strapiRepo.fetchAllPacks() }
    // Pre-populate all packs as enabled by default on first load
    LaunchedEffect(allStrapiPacks) {
        allStrapiPacks.forEach { pack ->
            if (!enabledPackIds.containsKey(pack.id)) enabledPackIds[pack.id] = true
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.sticker_manage_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = null)
                }
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            if (isLoading && allStrapiPacks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.sticker_packs_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (allStrapiPacks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.sticker_pack_no_available), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
                    items(allStrapiPacks) { pack ->
                        val isEnabled = enabledPackIds[pack.id] ?: true
                        val firstItemUrl = pack.items.firstOrNull()?.url
                        val isAnimated = firstItemUrl?.let {
                            it.endsWith(".gif") || it.endsWith(".json") || it.endsWith(".tgs")
                        } == true

                        val packCountStr = stringResource(R.string.sticker_pack_count, pack.items.size)
                        val animatedBadge = stringResource(R.string.sticker_pack_animated_badge)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Pack thumbnail (first item's URL)
                            if (!firstItemUrl.isNullOrEmpty()) {
                                AsyncImage(
                                    model = firstItemUrl,
                                    contentDescription = pack.name,
                                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(pack.name.take(2), fontWeight = FontWeight.Bold)
                                }
                            }

                            // Pack info
                            Column(modifier = Modifier.weight(1f)) {
                                Text(pack.name, fontWeight = FontWeight.Medium)
                                Text(
                                    text = buildString {
                                        append(packCountStr)
                                        if (isAnimated) append(" • $animatedBadge")
                                        if (pack.isPro) append(" • PRO")
                                    },
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Toggle switch — also calls server API
                            Switch(
                                checked = isEnabled,
                                onCheckedChange = { enable ->
                                    enabledPackIds[pack.id] = enable
                                    scope.launch {
                                        if (enable) stickerRepo.activateStickerPack(pack.id.toLong())
                                        else stickerRepo.deactivateStickerPack(pack.id.toLong())
                                    }
                                }
                            )
                        }
                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
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

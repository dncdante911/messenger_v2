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
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.res.stringResource
import com.worldmates.messenger.R
import com.worldmates.messenger.data.model.Sticker
import com.worldmates.messenger.data.model.StickerPack
import com.worldmates.messenger.data.repository.StickerRepository
import com.worldmates.messenger.data.repository.StrapiStickerRepository
import com.worldmates.messenger.data.stickers.EmbeddedStickerPacks
import com.worldmates.messenger.network.NodeRetrofitClient
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

    // Telegram animated emoji pack
    var telegramPack by remember { mutableStateOf<StickerPack?>(null) }
    var telegramLoading by remember { mutableStateOf(false) }
    var telegramError by remember { mutableStateOf<String?>(null) }

    // Об'єднуємо всі паки: вбудовані + Telegram + стандартні + з API
    val activePacks = remember(customPacks, telegramPack) {
        val packs = mutableListOf<StickerPack>()
        packs.addAll(embeddedPacks)
        telegramPack?.let { packs.add(it) }
        packs.add(standardPack)
        packs.addAll(customPacks.filter { it.isActive && it.stickers?.isNotEmpty() == true })
        packs
    }

    var selectedPack by remember { mutableStateOf(embeddedPacks.firstOrNull() ?: standardPack) }

    // Sync selectedPack when telegramPack loads and was the intended selection
    LaunchedEffect(telegramPack) {
        val tg = telegramPack ?: return@LaunchedEffect
        if (selectedPack.id == tg.id) selectedPack = tg
    }

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
                    // Telegram animated emoji button
                    TelegramEmojiButton(
                        isLoading = telegramLoading,
                        isLoaded = telegramPack != null,
                        onClick = {
                            if (telegramPack != null) {
                                selectedPack = telegramPack!!
                            } else if (!telegramLoading) {
                                telegramLoading = true
                                telegramError = null
                                scope.launch {
                                    try {
                                        val response = NodeRetrofitClient.api.getTelegramAnimatedEmoji()
                                        if (response.apiStatus == 200 && response.pack != null) {
                                            telegramPack = response.pack
                                            selectedPack = response.pack
                                        } else {
                                            telegramError = response.errorMessage ?: "Failed to load"
                                        }
                                    } catch (e: Exception) {
                                        telegramError = e.message
                                    } finally {
                                        telegramLoading = false
                                    }
                                }
                            }
                        }
                    )
                    IconButton(onClick = { showManageSheet = true }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.sticker_manage_title))
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }
            }

            // Telegram error snackbar
            if (telegramError != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "Telegram: $telegramError",
                        modifier = Modifier.padding(8.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.labelSmall
                    )
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
            when {
                // Telegram пак — показуємо ✈️ замість .tgs іконки
                pack.author == "Telegram" -> {
                    Text(
                        text = "✈️",
                        fontSize = 24.sp
                    )
                }
                pack.iconUrl != null || pack.thumbnailUrl != null -> {
                    AsyncImage(
                        model = pack.iconUrl ?: pack.thumbnailUrl,
                        contentDescription = pack.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    )
                }
                else -> {
                    Text(
                        text = pack.name.take(2),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) colorScheme.primary else colorScheme.onSurface
                    )
                }
            }
        }
    }
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
 * Кнопка для завантаження та перегляду Telegram анімованих емоджі.
 * Показує ✈️ якщо не завантажено, спінер під час завантаження,
 * та ✅ коли пак уже доступний.
 */
@Composable
private fun TelegramEmojiButton(
    isLoading: Boolean,
    isLoaded: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier
            .padding(end = 4.dp)
            .clickable(enabled = !isLoading, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (isLoaded) colorScheme.primaryContainer else colorScheme.surfaceVariant,
        shadowElevation = if (isLoaded) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = colorScheme.primary
                )
            } else {
                Text(
                    text = if (isLoaded) "✅" else "✈️",
                    fontSize = 16.sp
                )
            }
            Text(
                text = "Telegram",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (isLoaded) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant
            )
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

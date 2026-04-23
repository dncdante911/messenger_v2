package com.worldmates.messenger.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.worldmates.messenger.R
import com.worldmates.messenger.data.model.Sticker
import com.worldmates.messenger.data.repository.EmojiRepository
import com.worldmates.messenger.data.repository.GifItem
import com.worldmates.messenger.data.repository.GiphyRepository
import com.worldmates.messenger.data.repository.StickerRepository
import com.worldmates.messenger.data.stickers.EmbeddedStickerPacks
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class MediaPickerTab { EMOJI, GIF, STICKERS }

/**
 * Unified media picker — emoji, GIF, and sticker packs in one panel.
 * Opened via the 😊 button in the input bar.
 *
 * @param onEmojiSelected    Called when user taps an emoji. Does NOT auto-dismiss.
 * @param onGifSelected      Called when user taps a GIF. Auto-dismisses after selection.
 * @param onStickerSelected  Called when user taps a sticker. Auto-dismisses.
 * @param onDismiss          Called to close the panel.
 * @param onBackspace        Optional backspace button handler (for inline emoji input).
 * @param initialTab         Which tab to open on first show.
 */
@Composable
fun UnifiedMediaPicker(
    onEmojiSelected: (String) -> Unit,
    onGifSelected: (String) -> Unit,
    onStickerSelected: (Sticker) -> Unit,
    onDismiss: () -> Unit,
    onBackspace: (() -> Unit)? = null,
    initialTab: MediaPickerTab = MediaPickerTab.EMOJI,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf(initialTab) }
    val cs = MaterialTheme.colorScheme

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(320.dp),
        color = cs.surface,
        shadowElevation = 6.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column {
            // ── Content area ──────────────────────────────────────────────────
            Box(modifier = Modifier.weight(1f)) {
                when (activeTab) {
                    MediaPickerTab.EMOJI -> EmojiTabContent(
                        onEmojiSelected = onEmojiSelected
                    )
                    MediaPickerTab.GIF -> GifTabContent(
                        onGifSelected = { url ->
                            onGifSelected(url)
                            onDismiss()
                        }
                    )
                    MediaPickerTab.STICKERS -> StickersTabContent(
                        onStickerSelected = { sticker ->
                            onStickerSelected(sticker)
                            onDismiss()
                        }
                    )
                }
            }

            // ── Main section tab row ──────────────────────────────────────────
            HorizontalDivider(
                color = cs.outlineVariant.copy(alpha = 0.15f),
                thickness = 0.5.dp
            )
            MainSectionTabRow(
                activeTab = activeTab,
                onTabSelected = { activeTab = it },
                onBackspace = onBackspace
            )
        }
    }
}

// ── Main section tab row ──────────────────────────────────────────────────────

@Composable
private fun MainSectionTabRow(
    activeTab: MediaPickerTab,
    onTabSelected: (MediaPickerTab) -> Unit,
    onBackspace: (() -> Unit)?
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(cs.surfaceVariant.copy(alpha = 0.25f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(
            Triple(MediaPickerTab.EMOJI,    "😊", "Emoji"),
            Triple(MediaPickerTab.GIF,      "🎬", "GIF"),
            Triple(MediaPickerTab.STICKERS, "🎭", "Стикеры"),
        ).forEach { (tab, icon, _) ->
            val selected = activeTab == tab
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) cs.primary.copy(alpha = 0.13f) else Color.Transparent
                    )
                    .clickable { onTabSelected(tab) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = icon,
                    fontSize = if (selected) 24.sp else 21.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        if (onBackspace != null) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onBackspace),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Backspace,
                    contentDescription = null,
                    tint = cs.onSurfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ── Emoji tab ─────────────────────────────────────────────────────────────────

@Composable
private fun EmojiTabContent(onEmojiSelected: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val emojiRepository = remember { EmojiRepository.getInstance(context) }

    val customEmojis by emojiRepository.customEmojis.collectAsState()
    val recentEmojis  by emojiRepository.recentEmojis.collectAsState()

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

    Column(modifier = Modifier.fillMaxSize()) {
        // Emoji grid
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
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onEmoji(ce.code) },
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = ce.url,
                                    contentDescription = ce.name ?: ce.code,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
                category == EmojiCategory.RECENT && recentEmojis.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🙂", fontSize = 32.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.emoji_no_recent),
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurface.copy(alpha = 0.4f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                else -> {
                    val emojis = if (category == EmojiCategory.RECENT) recentEmojis else category.emojis
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(8),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(emojis) { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onEmoji(emoji) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(emoji, fontSize = 24.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }

        // Emoji category sub-tabs
        HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.12f), thickness = 0.5.dp)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(cs.surfaceVariant.copy(alpha = 0.15f))
                .padding(horizontal = 2.dp, vertical = 2.dp)
        ) {
            itemsIndexed(categories) { index, category ->
                val selected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) cs.primary.copy(alpha = 0.14f) else Color.Transparent
                        )
                        .clickable { scope.launch { pagerState.animateScrollToPage(index) } },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = category.tabIcon,
                        fontSize = if (selected) 20.sp else 18.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// ── GIF tab ───────────────────────────────────────────────────────────────────

@Composable
private fun GifTabContent(onGifSelected: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val giphyRepo = remember { GiphyRepository.getInstance(context) }

    var searchQuery by remember { mutableStateOf("") }
    var gifs by remember { mutableStateOf<List<GifItem>>(emptyList()) }
    val isLoading by giphyRepo.isLoading.collectAsState()
    var searchJob: Job? by remember { mutableStateOf(null) }
    val cs = MaterialTheme.colorScheme

    LaunchedEffect(Unit) {
        val result = giphyRepo.fetchTrendingGifs(limit = 50)
        result.onSuccess { data -> gifs = data }
    }

    LaunchedEffect(searchQuery) {
        searchJob?.cancel()
        if (searchQuery.isBlank()) {
            val result = giphyRepo.fetchTrendingGifs(limit = 50)
            result.onSuccess { data -> gifs = data }
        } else {
            searchJob = scope.launch {
                delay(450)
                val result = giphyRepo.searchGifs(searchQuery, limit = 50)
                result.onSuccess { data -> gifs = data }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .height(44.dp),
            placeholder = {
                Text(
                    stringResource(R.string.search_gif_hint),
                    fontSize = 13.sp,
                    color = cs.onSurfaceVariant.copy(alpha = 0.6f)
                )
            },
            leadingIcon = {
                Icon(Icons.Default.Search, null,
                    modifier = Modifier.size(18.dp),
                    tint = cs.onSurfaceVariant.copy(alpha = 0.6f))
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" },
                        modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = cs.primary.copy(alpha = 0.5f),
                unfocusedBorderColor = cs.outlineVariant.copy(alpha = 0.4f),
                focusedContainerColor = cs.surfaceVariant.copy(alpha = 0.2f),
                unfocusedContainerColor = cs.surfaceVariant.copy(alpha = 0.2f)
            ),
            shape = RoundedCornerShape(10.dp)
        )

        // GIF grid
        Box(modifier = Modifier.weight(1f)) {
            when {
                isLoading && gifs.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                    }
                }
                gifs.isEmpty() && searchQuery.isNotEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("😕", fontSize = 36.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.gif_empty_title),
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant
                            )
                        }
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(gifs) { gifItem ->
                            val previewUrl = gifItem.downsizedMediumUrl.ifEmpty {
                                gifItem.fixedWidthUrl.ifEmpty { gifItem.previewUrl }
                            }
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(cs.surfaceVariant)
                                    .clickable {
                                        val urls = giphyRepo.getGifUrls(gifItem)
                                        val url = urls.getBestForChat()
                                        if (url.isNotEmpty()) onGifSelected(url)
                                    }
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(previewUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = gifItem.title.ifEmpty { "GIF" },
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }

        // GIPHY attribution
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF111111))
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Powered by GIPHY",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.55f)
            )
        }
    }
}

// ── Stickers tab ──────────────────────────────────────────────────────────────

@Composable
private fun StickersTabContent(onStickerSelected: (Sticker) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stickerRepository = remember { StickerRepository.getInstance(context) }

    val embeddedPacks = remember { EmbeddedStickerPacks.getAllEmbeddedPacks() }
    val customPacks by stickerRepository.stickerPacks.collectAsState()

    val activePacks = remember(customPacks) {
        buildList {
            addAll(embeddedPacks)
            addAll(customPacks.filter { it.isActive && it.stickers?.isNotEmpty() == true })
        }
    }

    var selectedPack by remember(activePacks) {
        mutableStateOf(activePacks.firstOrNull())
    }
    var showManageSheet by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme

    LaunchedEffect(Unit) { scope.launch { stickerRepository.fetchStickerPacks() } }

    if (showManageSheet) {
        StickerPackManagementSheet(onDismiss = { showManageSheet = false })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Sticker grid
        if (selectedPack == null || selectedPack!!.stickers.isNullOrEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎭", fontSize = 40.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.sticker_pack_no_available),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = { showManageSheet = true }) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.sticker_manage_title), fontSize = 13.sp)
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                contentPadding = PaddingValues(vertical = 6.dp, horizontal = 2.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(selectedPack!!.stickers ?: emptyList()) { sticker ->
                    UmpStickerItem(
                        sticker = sticker,
                        onClick = { onStickerSelected(sticker) }
                    )
                }
            }
        }

        // Pack selector + manage button
        HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.12f), thickness = 0.5.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(cs.surfaceVariant.copy(alpha = 0.15f))
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(activePacks) { pack ->
                    val isSelected = selectedPack?.id == pack.id
                    val packIcon = getUmpPackIcon(pack.name)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) cs.primary.copy(alpha = 0.14f)
                                else Color.Transparent
                            )
                            .clickable { selectedPack = pack },
                        contentAlignment = Alignment.Center
                    ) {
                        // Try to show first sticker thumbnail, fall back to emoji icon
                        val firstSticker = pack.stickers?.firstOrNull()
                        val thumbUrl = firstSticker?.thumbnailUrl
                        if (!thumbUrl.isNullOrEmpty() && !thumbUrl.startsWith("lottie://")) {
                            AsyncImage(
                                model = thumbUrl,
                                contentDescription = pack.name,
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Text(packIcon, fontSize = 20.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }

            // "+" manage packs button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showManageSheet = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.sticker_manage_title),
                    tint = cs.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ── Sticker item ──────────────────────────────────────────────────────────────

@Composable
private fun UmpStickerItem(sticker: Sticker, onClick: () -> Unit) {
    val context = LocalContext.current
    val url = when {
        sticker.fileUrl.startsWith("lottie://") ->
            EmbeddedStickerPacks.getEmbeddedStickerResourceUrl(context, sticker.fileUrl) ?: sticker.emoji
        else -> sticker.thumbnailUrl ?: sticker.fileUrl
    }

    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when {
            sticker.format in listOf("lottie", "tgs", "gif") ||
            url?.endsWith(".json") == true ||
            url?.endsWith(".tgs") == true ||
            url?.endsWith(".gif") == true -> {
                AnimatedStickerView(url = url ?: "", size = 48.dp)
            }
            url == sticker.emoji -> {
                Text(sticker.emoji ?: "🎭", fontSize = 30.sp)
            }
            else -> {
                AsyncImage(
                    model = url,
                    contentDescription = sticker.emoji,
                    modifier = Modifier.size(48.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

private fun getUmpPackIcon(name: String): String = when {
    name.contains("Емоці", ignoreCase = true) || name.contains("Emotion", ignoreCase = true) -> "😊"
    name.contains("Тварин", ignoreCase = true) || name.contains("Animal", ignoreCase = true) -> "🐾"
    name.contains("Святкув", ignoreCase = true) || name.contains("Celebr", ignoreCase = true) -> "🎉"
    name.contains("Жест", ignoreCase = true) || name.contains("Gesture", ignoreCase = true) -> "👋"
    name.contains("Серц", ignoreCase = true) || name.contains("Heart", ignoreCase = true) -> "❤️"
    name.contains("WorldMates", ignoreCase = true) -> "🎭"
    name.contains("Emoji", ignoreCase = true) || name.contains("Емодж", ignoreCase = true) -> "😄"
    else -> "🗂️"
}

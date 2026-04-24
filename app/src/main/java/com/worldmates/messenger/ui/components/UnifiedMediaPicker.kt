package com.worldmates.messenger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.worldmates.messenger.R
import com.worldmates.messenger.data.model.CustomEmoji
import com.worldmates.messenger.data.model.Sticker
import com.worldmates.messenger.data.model.StickerPack
import com.worldmates.messenger.data.repository.EmojiRepository
import com.worldmates.messenger.data.repository.GifItem
import com.worldmates.messenger.data.repository.GiphyRepository
import com.worldmates.messenger.data.repository.StickerRepository
import com.worldmates.messenger.data.stickers.EmbeddedStickerPacks
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class MediaPickerTab { EMOJI, GIF, STICKERS }

// Internal sealed class — tracks exactly which section is active
private sealed class PickerSection {
    data class EmojiSection(val cat: EmojiCategory) : PickerSection()
    object GifSection : PickerSection()
    data class PackSection(val packId: Long) : PickerSection()
}

/**
 * Telegram-style unified media picker — emoji categories, GIF, and sticker packs
 * all share ONE scrollable bottom navigation bar with a bottom-underline selection indicator.
 *
 * @param onEmojiSelected    Called when user taps an emoji (does NOT auto-dismiss).
 * @param onGifSelected      Called with the chosen GIF URL; auto-dismisses.
 * @param onStickerSelected  Called with the chosen Sticker; auto-dismisses.
 * @param onDismiss          Closes the panel.
 * @param onBackspace        Optional backspace handler pinned to the right of the nav bar.
 * @param initialTab         Which section opens first.
 */
@Composable
fun UnifiedMediaPicker(
    onEmojiSelected:   (String)  -> Unit,
    onGifSelected:     (String)  -> Unit,
    onStickerSelected: (Sticker) -> Unit,
    onDismiss:         ()        -> Unit,
    onBackspace:       (() -> Unit)? = null,
    initialTab:        MediaPickerTab = MediaPickerTab.EMOJI,
    modifier:          Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // ── Repositories ──────────────────────────────────────────────────────────
    val emojiRepo   = remember { EmojiRepository.getInstance(context) }
    val giphyRepo   = remember { GiphyRepository.getInstance(context) }
    val stickerRepo = remember { StickerRepository.getInstance(context) }

    // ── Emoji data ────────────────────────────────────────────────────────────
    val customEmojis by emojiRepo.customEmojis.collectAsState()
    val recentEmojis by emojiRepo.recentEmojis.collectAsState()
    val emojiCategories = remember(recentEmojis) {
        buildList {
            if (recentEmojis.isNotEmpty()) add(EmojiCategory.RECENT)
            addAll(listOf(
                EmojiCategory.SMILEYS, EmojiCategory.PEOPLE, EmojiCategory.GESTURES,
                EmojiCategory.ANIMALS, EmojiCategory.FOOD, EmojiCategory.ACTIVITIES,
                EmojiCategory.TRAVEL,  EmojiCategory.OBJECTS, EmojiCategory.SYMBOLS,
                EmojiCategory.CUSTOM
            ))
        }
    }

    // ── Sticker data ──────────────────────────────────────────────────────────
    val embeddedPacks = remember { EmbeddedStickerPacks.getAllEmbeddedPacks() }
    val customPacks by stickerRepo.stickerPacks.collectAsState()
    val activePacks = remember(customPacks) {
        buildList {
            addAll(embeddedPacks)
            addAll(customPacks.filter { it.isActive && it.stickers?.isNotEmpty() == true })
        }
    }

    // ── GIF data ──────────────────────────────────────────────────────────────
    var gifs by remember { mutableStateOf<List<GifItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    val isGifLoading by giphyRepo.isLoading.collectAsState()
    var searchJob: Job? by remember { mutableStateOf(null) }

    // ── Active section ────────────────────────────────────────────────────────
    var activeSection by remember {
        mutableStateOf<PickerSection>(
            when (initialTab) {
                MediaPickerTab.GIF      -> PickerSection.GifSection
                MediaPickerTab.STICKERS -> PickerSection.PackSection(-1L) // resolved when packs load
                else                    -> PickerSection.EmojiSection(EmojiCategory.SMILEYS)
            }
        )
    }
    var showManageSheet by remember { mutableStateOf(false) }

    // ── Data loading ──────────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        scope.launch { emojiRepo.fetchEmojiPacks() }
        scope.launch { stickerRepo.fetchStickerPacks() }
        giphyRepo.fetchTrendingGifs(limit = 50).onSuccess { gifs = it }
    }

    LaunchedEffect(searchQuery) {
        searchJob?.cancel()
        if (searchQuery.isBlank()) {
            giphyRepo.fetchTrendingGifs(limit = 50).onSuccess { gifs = it }
        } else {
            searchJob = scope.launch {
                delay(450)
                giphyRepo.searchGifs(searchQuery, limit = 50).onSuccess { gifs = it }
            }
        }
    }

    // Resolve placeholder pack ID once packs are loaded
    LaunchedEffect(activePacks) {
        val s = activeSection
        if (s is PickerSection.PackSection && s.packId == -1L && activePacks.isNotEmpty()) {
            activeSection = PickerSection.PackSection(activePacks.first().id)
        }
    }

    if (showManageSheet) {
        StickerPackManagementSheet(onDismiss = { showManageSheet = false })
    }

    val cs = MaterialTheme.colorScheme

    Surface(
        modifier        = modifier.fillMaxWidth().height(340.dp),
        color           = cs.surface,
        shadowElevation = 6.dp,
        shape           = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column {
            // ── Content area ──────────────────────────────────────────────────
            Box(modifier = Modifier.weight(1f)) {
                when (val s = activeSection) {
                    is PickerSection.EmojiSection -> EmojiCategoryContent(
                        category     = s.cat,
                        recentEmojis = recentEmojis,
                        customEmojis = customEmojis,
                        onEmojiSelected = { emoji ->
                            emojiRepo.trackEmojiUsage(emoji)
                            onEmojiSelected(emoji)
                        }
                    )
                    is PickerSection.GifSection -> GifGridContent(
                        gifs          = gifs,
                        isLoading     = isGifLoading,
                        searchQuery   = searchQuery,
                        giphyRepo     = giphyRepo,
                        onQueryChange = { searchQuery = it },
                        onGifSelected = { url -> onGifSelected(url); onDismiss() }
                    )
                    is PickerSection.PackSection -> StickerPackContent(
                        packId      = s.packId,
                        activePacks = activePacks,
                        onStickerSelected = { sticker -> onStickerSelected(sticker); onDismiss() }
                    )
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = cs.outlineVariant.copy(alpha = 0.2f))

            // ── Unified bottom nav bar ────────────────────────────────────────
            UnifiedNavBar(
                activeSection   = activeSection,
                emojiCategories = emojiCategories,
                activePacks     = activePacks,
                onSectionChange = { activeSection = it },
                onAddPacks      = { showManageSheet = true },
                onBackspace     = onBackspace
            )
        }
    }
}

// ── Unified bottom nav bar ────────────────────────────────────────────────────

@Composable
private fun UnifiedNavBar(
    activeSection:   PickerSection,
    emojiCategories: List<EmojiCategory>,
    activePacks:     List<StickerPack>,
    onSectionChange: (PickerSection) -> Unit,
    onAddPacks:      () -> Unit,
    onBackspace:     (() -> Unit)?
) {
    val cs = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(cs.surface),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Scrollable part — emoji categories + GIF + sticker packs
        LazyRow(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            // 1. Emoji category icons
            items(emojiCategories) { cat ->
                val sel = activeSection is PickerSection.EmojiSection &&
                          (activeSection as PickerSection.EmojiSection).cat == cat
                NavItem(selected = sel, onClick = { onSectionChange(PickerSection.EmojiSection(cat)) }) {
                    Text(
                        text      = cat.tabIcon,
                        fontSize  = 20.sp,
                        textAlign = TextAlign.Center,
                        color     = if (sel) cs.primary else cs.onSurfaceVariant.copy(alpha = 0.65f)
                    )
                }
            }

            // Divider
            item { NavDivider() }

            // 2. GIF button
            item {
                val sel = activeSection is PickerSection.GifSection
                NavItem(selected = sel, onClick = { onSectionChange(PickerSection.GifSection) }) {
                    Text(
                        text       = "GIF",
                        fontSize   = 11.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                        color      = if (sel) cs.primary else cs.onSurfaceVariant.copy(alpha = 0.65f),
                        textAlign  = TextAlign.Center
                    )
                }
            }

            // Sticker packs + add button (only when packs are available)
            if (activePacks.isNotEmpty()) {
                item { NavDivider() }

                // 3. One thumbnail per sticker pack
                items(activePacks) { pack ->
                    val sel = activeSection is PickerSection.PackSection &&
                              (activeSection as PickerSection.PackSection).packId == pack.id
                    NavItem(selected = sel, onClick = { onSectionChange(PickerSection.PackSection(pack.id)) }) {
                        val thumbUrl = pack.stickers?.firstOrNull()?.thumbnailUrl
                                       ?: pack.thumbnailUrl
                                       ?: pack.iconUrl
                        if (!thumbUrl.isNullOrEmpty() && !thumbUrl.startsWith("lottie://")) {
                            AsyncImage(
                                model              = thumbUrl,
                                contentDescription = pack.name,
                                modifier           = Modifier.size(26.dp).clip(RoundedCornerShape(4.dp)),
                                contentScale       = ContentScale.Fit
                            )
                        } else {
                            Text(
                                text      = getUmpPackIcon(pack.name),
                                fontSize  = 20.sp,
                                color     = if (sel) cs.primary else cs.onSurfaceVariant.copy(alpha = 0.65f)
                            )
                        }
                    }
                }

                // "+" button to open pack management
                item {
                    Box(
                        modifier           = Modifier.width(40.dp).height(50.dp).clickable(onClick = onAddPacks),
                        contentAlignment   = Alignment.Center
                    ) {
                        Icon(
                            imageVector        = Icons.Default.Add,
                            contentDescription = null,
                            modifier           = Modifier.size(20.dp),
                            tint               = cs.onSurfaceVariant.copy(alpha = 0.45f)
                        )
                    }
                }
            }
        }

        // Backspace — fixed, pinned to right, outside the scrollable row
        if (onBackspace != null) {
            Box(
                modifier         = Modifier.size(48.dp).clickable(onClick = onBackspace),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Default.Backspace,
                    contentDescription = null,
                    modifier           = Modifier.size(20.dp),
                    tint               = cs.onSurfaceVariant.copy(alpha = 0.38f)
                )
            }
        }
    }
}

// Single nav bar item — 44dp wide, 50dp tall, bottom underline when selected
@Composable
private fun NavItem(
    selected: Boolean,
    onClick:  () -> Unit,
    content:  @Composable () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier         = Modifier.width(44.dp).height(50.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
        // Telegram-style selection: 2dp underline at bottom, NOT a background highlight
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .width(22.dp)
                .height(2.dp)
                .background(
                    color = if (selected) cs.primary else Color.Transparent,
                    shape = RoundedCornerShape(topStart = 1.dp, topEnd = 1.dp)
                )
        )
    }
}

// Thin vertical divider separating nav sections
@Composable
private fun NavDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(24.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    )
}

// ── Emoji category content ────────────────────────────────────────────────────

@Composable
private fun EmojiCategoryContent(
    category:     EmojiCategory,
    recentEmojis: List<String>,
    customEmojis: List<CustomEmoji>,
    onEmojiSelected: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    when {
        // Custom emoji pack — show image grid
        category == EmojiCategory.CUSTOM && customEmojis.isNotEmpty() -> {
            LazyVerticalGrid(
                columns        = GridCells.Fixed(8),
                modifier       = Modifier.fillMaxSize().padding(4.dp),
                contentPadding = PaddingValues(4.dp)
            ) {
                items(customEmojis) { ce ->
                    Box(
                        modifier         = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                                           .clickable { onEmojiSelected(ce.code) },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model              = ce.url,
                            contentDescription = ce.name ?: ce.code,
                            modifier           = Modifier.size(30.dp)
                        )
                    }
                }
            }
        }
        // Empty states
        (category == EmojiCategory.RECENT && recentEmojis.isEmpty()) ||
        (category == EmojiCategory.CUSTOM  && customEmojis.isEmpty()) -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (category == EmojiCategory.RECENT) "🙂" else "✨", fontSize = 32.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text      = stringResource(R.string.emoji_no_recent),
                        style     = MaterialTheme.typography.bodySmall,
                        color     = cs.onSurface.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        // Standard emoji grid
        else -> {
            val emojis = if (category == EmojiCategory.RECENT) recentEmojis else category.emojis
            LazyVerticalGrid(
                columns        = GridCells.Fixed(8),
                modifier       = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(emojis) { emoji ->
                    Box(
                        modifier         = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                                           .clickable { onEmojiSelected(emoji) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emoji, fontSize = 24.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

// ── GIF content ───────────────────────────────────────────────────────────────

@Composable
private fun GifGridContent(
    gifs:          List<GifItem>,
    isLoading:     Boolean,
    searchQuery:   String,
    giphyRepo:     GiphyRepository,
    onQueryChange: (String) -> Unit,
    onGifSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val cs      = MaterialTheme.colorScheme

    Column(modifier = Modifier.fillMaxSize()) {
        // Search field
        OutlinedTextField(
            value         = searchQuery,
            onValueChange = onQueryChange,
            modifier      = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .height(44.dp),
            placeholder = {
                Text(
                    text  = stringResource(R.string.search_gif_hint),
                    fontSize = 13.sp,
                    color = cs.onSurfaceVariant.copy(alpha = 0.55f)
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint     = cs.onSurfaceVariant.copy(alpha = 0.55f)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine  = true,
            textStyle   = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            colors      = OutlinedTextFieldDefaults.colors(
                focusedBorderColor      = cs.primary.copy(alpha = 0.5f),
                unfocusedBorderColor    = cs.outlineVariant.copy(alpha = 0.35f),
                focusedContainerColor   = cs.surfaceVariant.copy(alpha = 0.15f),
                unfocusedContainerColor = cs.surfaceVariant.copy(alpha = 0.15f)
            ),
            shape = RoundedCornerShape(10.dp)
        )

        Box(modifier = Modifier.weight(1f)) {
            when {
                isLoading && gifs.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                    }
                }
                gifs.isEmpty() && searchQuery.isNotEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("😕", fontSize = 36.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text  = stringResource(R.string.gif_empty_title),
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant
                            )
                        }
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns               = GridCells.Fixed(3),
                        contentPadding        = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement   = Arrangement.spacedBy(4.dp),
                        modifier              = Modifier.fillMaxSize()
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
                                        val url  = urls.getBestForChat()
                                        if (url.isNotEmpty()) onGifSelected(url)
                                    }
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(previewUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = gifItem.title.ifEmpty { "GIF" },
                                    contentScale       = ContentScale.Crop,
                                    modifier           = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }

        // GIPHY attribution (required by terms of service)
        Box(
            modifier         = Modifier.fillMaxWidth().background(Color(0xFF111111)).padding(vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Powered by GIPHY", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
        }
    }
}

// ── Sticker pack content ──────────────────────────────────────────────────────

@Composable
private fun StickerPackContent(
    packId:            Long,
    activePacks:       List<StickerPack>,
    onStickerSelected: (Sticker) -> Unit
) {
    val pack = activePacks.firstOrNull { it.id == packId }
    val cs   = MaterialTheme.colorScheme

    if (pack == null || pack.stickers.isNullOrEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🎭", fontSize = 40.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    text  = stringResource(R.string.sticker_pack_no_available),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    } else {
        LazyVerticalGrid(
            columns               = GridCells.Fixed(4),
            modifier              = Modifier.fillMaxSize().padding(horizontal = 4.dp),
            contentPadding        = PaddingValues(vertical = 6.dp, horizontal = 2.dp),
            verticalArrangement   = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(pack.stickers!!) { sticker ->
                UmpStickerItem(sticker = sticker, onClick = { onStickerSelected(sticker) })
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
        modifier         = Modifier.size(68.dp).clip(MaterialTheme.shapes.medium).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when {
            sticker.format in listOf("lottie", "tgs", "gif") ||
            url?.endsWith(".json") == true ||
            url?.endsWith(".tgs")  == true ||
            url?.endsWith(".gif")  == true -> {
                AnimatedStickerView(url = url ?: "", size = 52.dp)
            }
            url == sticker.emoji -> {
                Text(sticker.emoji ?: "🎭", fontSize = 30.sp)
            }
            else -> {
                AsyncImage(
                    model              = url,
                    contentDescription = sticker.emoji,
                    modifier           = Modifier.size(52.dp),
                    contentScale       = ContentScale.Fit
                )
            }
        }
    }
}

// ── Pack icon fallback ────────────────────────────────────────────────────────

private fun getUmpPackIcon(name: String): String = when {
    name.contains("Емоці",   ignoreCase = true) || name.contains("Emotion", ignoreCase = true) -> "😊"
    name.contains("Тварин",  ignoreCase = true) || name.contains("Animal",  ignoreCase = true) -> "🐾"
    name.contains("Святкув", ignoreCase = true) || name.contains("Celebr",  ignoreCase = true) -> "🎉"
    name.contains("Жест",    ignoreCase = true) || name.contains("Gesture", ignoreCase = true) -> "👋"
    name.contains("Серц",    ignoreCase = true) || name.contains("Heart",   ignoreCase = true) -> "❤️"
    name.contains("WorldMates", ignoreCase = true)                                              -> "🎭"
    name.contains("Emoji",   ignoreCase = true) || name.contains("Емодж",   ignoreCase = true) -> "😄"
    else                                                                                         -> "🗂️"
}

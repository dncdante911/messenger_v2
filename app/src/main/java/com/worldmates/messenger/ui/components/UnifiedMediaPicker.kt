package com.worldmates.messenger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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

// Public API — used by MessagesScreen and MessageInputComponents
enum class MediaPickerTab { EMOJI, GIF, STICKERS }

// Internal: which of the 3 main sections is open
private enum class MainTab { EMOJI, GIF, STICKERS }

/**
 * Modern unified media picker — 3 fixed section tabs at the bottom,
 * swipeable emoji categories, secondary sub-nav row for packs/categories,
 * full-size content area.
 *
 * Layout (340 dp total):
 *   ┌───────────────────────────────────┐
 *   │   Content (weight 1f)             │  emoji pager / gif grid / sticker grid
 *   ├───────────────────────────────────┤  divider
 *   │   Sub-nav (38 dp)                 │  category icons OR pack thumbnails
 *   ├───────────────────────────────────┤  divider
 *   │   Main tabs (48 dp)               │  😊  GIF  🎭
 *   └───────────────────────────────────┘
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
    val scope   = rememberCoroutineScope()
    val context = LocalContext.current

    // ── Repositories ──────────────────────────────────────────────────────────
    val emojiRepo   = remember { EmojiRepository.getInstance(context) }
    val giphyRepo   = remember { GiphyRepository.getInstance(context) }
    val stickerRepo = remember { StickerRepository.getInstance(context) }

    // ── Emoji ─────────────────────────────────────────────────────────────────
    val customEmojis by emojiRepo.customEmojis.collectAsState()
    val recentEmojis by emojiRepo.recentEmojis.collectAsState()
    val emojiCategories = remember(recentEmojis) {
        buildList {
            if (recentEmojis.isNotEmpty()) add(EmojiCategory.RECENT)
            addAll(listOf(
                EmojiCategory.SMILEYS, EmojiCategory.PEOPLE, EmojiCategory.GESTURES,
                EmojiCategory.ANIMALS, EmojiCategory.FOOD,   EmojiCategory.ACTIVITIES,
                EmojiCategory.TRAVEL,  EmojiCategory.OBJECTS, EmojiCategory.SYMBOLS,
                EmojiCategory.CUSTOM
            ))
        }
    }

    // ── Stickers ──────────────────────────────────────────────────────────────
    val embeddedPacks = remember { EmbeddedStickerPacks.getAllEmbeddedPacks() }
    val customPacks  by stickerRepo.stickerPacks.collectAsState()
    val activePacks   = remember(customPacks) {
        buildList {
            addAll(embeddedPacks)
            addAll(customPacks.filter { it.isActive && it.stickers?.isNotEmpty() == true })
        }
    }
    var selectedPackId by remember { mutableStateOf(-1L) }
    LaunchedEffect(activePacks) {
        if (selectedPackId == -1L && activePacks.isNotEmpty())
            selectedPackId = activePacks.first().id
    }

    // ── GIFs ──────────────────────────────────────────────────────────────────
    var gifs         by remember { mutableStateOf<List<GifItem>>(emptyList()) }
    var gifQuery     by remember { mutableStateOf("") }
    val isGifLoading by giphyRepo.isLoading.collectAsState()
    var gifSearchJob: Job? by remember { mutableStateOf(null) }

    // ── Active tab ────────────────────────────────────────────────────────────
    var activeTab by remember {
        mutableStateOf(when (initialTab) {
            MediaPickerTab.GIF      -> MainTab.GIF
            MediaPickerTab.STICKERS -> MainTab.STICKERS
            else                    -> MainTab.EMOJI
        })
    }

    // ── Emoji pager + sub-nav sync ────────────────────────────────────────────
    val pagerState  = rememberPagerState(initialPage = 0) { emojiCategories.size }
    val catRowState = rememberLazyListState()
    LaunchedEffect(pagerState.currentPage) {
        // Keep the active category icon visible in the sub-nav row
        catRowState.animateScrollToItem(
            index        = pagerState.currentPage,
            scrollOffset = -120
        )
    }

    var showManageSheet by remember { mutableStateOf(false) }

    // ── Data loading ──────────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        scope.launch { emojiRepo.fetchEmojiPacks() }
        scope.launch { stickerRepo.fetchStickerPacks() }
        giphyRepo.fetchTrendingGifs(limit = 50).onSuccess { gifs = it }
    }
    LaunchedEffect(gifQuery) {
        gifSearchJob?.cancel()
        if (gifQuery.isBlank()) {
            giphyRepo.fetchTrendingGifs(limit = 50).onSuccess { gifs = it }
        } else {
            gifSearchJob = scope.launch {
                delay(400)
                giphyRepo.searchGifs(gifQuery, limit = 50).onSuccess { gifs = it }
            }
        }
    }

    if (showManageSheet) StickerPackManagementSheet(onDismiss = { showManageSheet = false })

    val cs = MaterialTheme.colorScheme

    // ── Root surface ──────────────────────────────────────────────────────────
    Surface(
        modifier        = modifier.fillMaxWidth().height(340.dp),
        color           = cs.surface,
        shadowElevation = 8.dp,
        shape           = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
    ) {
        Column {

            // ═══════════════════════════════════════════════════════════════════
            // CONTENT AREA — fills all remaining space
            // ═══════════════════════════════════════════════════════════════════
            Box(modifier = Modifier.weight(1f)) {
                when (activeTab) {
                    MainTab.EMOJI -> HorizontalPager(
                        state    = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        EmojiPageContent(
                            category        = emojiCategories[page],
                            recentEmojis    = recentEmojis,
                            customEmojis    = customEmojis,
                            onEmojiSelected = { emoji ->
                                emojiRepo.trackEmojiUsage(emoji)
                                onEmojiSelected(emoji)
                            }
                        )
                    }
                    MainTab.GIF -> GifContent(
                        gifs          = gifs,
                        isLoading     = isGifLoading,
                        query         = gifQuery,
                        giphyRepo     = giphyRepo,
                        onQueryChange = { gifQuery = it },
                        onGifSelected = { url -> onGifSelected(url); onDismiss() }
                    )
                    MainTab.STICKERS -> StickerContent(
                        packId            = selectedPackId,
                        activePacks       = activePacks,
                        onStickerSelected = { s -> onStickerSelected(s); onDismiss() }
                    )
                }
            }

            // ═══════════════════════════════════════════════════════════════════
            // SUB-NAV (38 dp) — emoji categories OR sticker packs
            // ═══════════════════════════════════════════════════════════════════
            HorizontalDivider(thickness = 0.5.dp, color = cs.outlineVariant.copy(alpha = 0.18f))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(cs.surfaceVariant.copy(alpha = 0.12f))
            ) {
                when (activeTab) {
                    MainTab.EMOJI -> EmojiSubNav(
                        categories    = emojiCategories,
                        pagerState    = pagerState,
                        rowState      = catRowState,
                        onBackspace   = onBackspace,
                        onCategoryTap = { idx ->
                            scope.launch { pagerState.animateScrollToPage(idx) }
                        }
                    )
                    MainTab.STICKERS -> StickerSubNav(
                        activePacks    = activePacks,
                        selectedPackId = selectedPackId,
                        onPackSelected = { selectedPackId = it },
                        onAddPacks     = { showManageSheet = true }
                    )
                    MainTab.GIF -> {
                        // GIF has no sub-nav — the box stays as a subtle visual spacer
                    }
                }
            }

            // ═══════════════════════════════════════════════════════════════════
            // MAIN SECTION TABS (48 dp) — 😊  GIF  🎭
            // ═══════════════════════════════════════════════════════════════════
            HorizontalDivider(thickness = 0.5.dp, color = cs.outlineVariant.copy(alpha = 0.18f))
            Row(
                modifier          = Modifier.fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionTab(
                    modifier = Modifier.weight(1f),
                    selected = activeTab == MainTab.EMOJI,
                    onClick  = { activeTab = MainTab.EMOJI },
                    label    = "😊",
                    isEmoji  = true
                )
                SectionTab(
                    modifier = Modifier.weight(1f),
                    selected = activeTab == MainTab.GIF,
                    onClick  = { activeTab = MainTab.GIF },
                    label    = "GIF",
                    isEmoji  = false
                )
                SectionTab(
                    modifier = Modifier.weight(1f),
                    selected = activeTab == MainTab.STICKERS,
                    onClick  = { activeTab = MainTab.STICKERS },
                    label    = "🎭",
                    isEmoji  = true
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MAIN SECTION TAB
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionTab(
    modifier: Modifier,
    selected: Boolean,
    onClick:  () -> Unit,
    label:    String,
    isEmoji:  Boolean      // true = render as emoji Text, false = as styled text label
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier         = modifier.fillMaxHeight().clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isEmoji) {
            Text(
                text     = label,
                fontSize = if (selected) 24.sp else 21.sp,
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                text       = label,
                fontSize   = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color      = if (selected) cs.primary else cs.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign  = TextAlign.Center
            )
        }
        // Selection indicator — 2 dp line at the TOP of the tab (border between sub-nav and tabs)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .width(28.dp)
                .height(2.dp)
                .background(
                    color = if (selected) cs.primary else Color.Transparent,
                    shape = RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp)
                )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// EMOJI SUB-NAV — scrollable category icons + backspace
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmojiSubNav(
    categories:    List<EmojiCategory>,
    pagerState:    androidx.compose.foundation.pager.PagerState,
    rowState:      androidx.compose.foundation.lazy.LazyListState,
    onBackspace:   (() -> Unit)?,
    onCategoryTap: (Int) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier          = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            state          = rowState,
            modifier       = Modifier.weight(1f).fillMaxHeight(),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            itemsIndexed(categories) { idx, cat ->
                val selected = pagerState.currentPage == idx
                SubNavItem(
                    selected = selected,
                    onClick  = { onCategoryTap(idx) }
                ) {
                    Text(
                        text      = cat.tabIcon,
                        fontSize  = if (selected) 20.sp else 18.sp,
                        color     = if (selected) cs.primary
                                    else cs.onSurfaceVariant.copy(alpha = 0.55f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        // Backspace — visible only on emoji tab
        if (onBackspace != null) {
            Box(
                modifier         = Modifier.size(40.dp).clickable(onClick = onBackspace),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Default.Backspace,
                    contentDescription = null,
                    modifier           = Modifier.size(18.dp),
                    tint               = cs.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
            Spacer(modifier = Modifier.width(2.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STICKER SUB-NAV — pack thumbnails + add button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StickerSubNav(
    activePacks:    List<StickerPack>,
    selectedPackId: Long,
    onPackSelected: (Long) -> Unit,
    onAddPacks:     () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier          = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            modifier       = Modifier.weight(1f).fillMaxHeight(),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(activePacks) { pack ->
                val selected = pack.id == selectedPackId
                SubNavItem(
                    selected = selected,
                    onClick  = { onPackSelected(pack.id) }
                ) {
                    val thumb = pack.stickers?.firstOrNull()?.thumbnailUrl
                                ?: pack.thumbnailUrl ?: pack.iconUrl
                    if (!thumb.isNullOrEmpty() && !thumb.startsWith("lottie://")) {
                        AsyncImage(
                            model              = thumb,
                            contentDescription = pack.name,
                            modifier           = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp)),
                            contentScale       = ContentScale.Fit
                        )
                    } else {
                        Text(
                            text  = getUmpPackIcon(pack.name),
                            fontSize = if (selected) 20.sp else 18.sp,
                            color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        )
                    }
                }
            }
        }
        // Add packs button
        Box(
            modifier         = Modifier.size(40.dp).clickable(onClick = onAddPacks),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = Icons.Default.Add,
                contentDescription = null,
                modifier           = Modifier.size(18.dp),
                tint               = cs.onSurfaceVariant.copy(alpha = 0.45f)
            )
        }
        Spacer(modifier = Modifier.width(2.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SUB-NAV ITEM — shared item shell: 40 dp wide, fills height, bottom underline
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SubNavItem(
    selected: Boolean,
    onClick:  () -> Unit,
    content:  @Composable () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier         = Modifier.width(40.dp).fillMaxHeight().clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .width(20.dp)
                .height(2.dp)
                .background(
                    color = if (selected) cs.primary else Color.Transparent,
                    shape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)
                )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// EMOJI PAGE CONTENT — one grid per category (swipeable via HorizontalPager)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmojiPageContent(
    category:        EmojiCategory,
    recentEmojis:    List<String>,
    customEmojis:    List<CustomEmoji>,
    onEmojiSelected: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    when {
        category == EmojiCategory.CUSTOM && customEmojis.isNotEmpty() -> {
            LazyVerticalGrid(
                columns        = GridCells.Fixed(8),
                modifier       = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(customEmojis) { ce ->
                    Box(
                        modifier         = Modifier.size(42.dp).clip(RoundedCornerShape(8.dp))
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
        (category == EmojiCategory.RECENT && recentEmojis.isEmpty()) ||
        (category == EmojiCategory.CUSTOM  && customEmojis.isEmpty()) -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (category == EmojiCategory.RECENT) "🙂" else "✨", fontSize = 34.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text      = stringResource(R.string.emoji_no_recent),
                        style     = MaterialTheme.typography.bodySmall,
                        color     = cs.onSurface.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        else -> {
            val emojis = if (category == EmojiCategory.RECENT) recentEmojis else category.emojis
            LazyVerticalGrid(
                columns             = GridCells.Fixed(8),
                modifier            = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                contentPadding      = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(emojis) { emoji ->
                    Box(
                        modifier         = Modifier.size(42.dp).clip(RoundedCornerShape(8.dp))
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

// ─────────────────────────────────────────────────────────────────────────────
// GIF CONTENT — search field + grid + GIPHY attribution
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GifContent(
    gifs:          List<GifItem>,
    isLoading:     Boolean,
    query:         String,
    giphyRepo:     GiphyRepository,
    onQueryChange: (String) -> Unit,
    onGifSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val cs      = MaterialTheme.colorScheme

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value         = query,
            onValueChange = onQueryChange,
            modifier      = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .height(44.dp),
            placeholder  = {
                Text(
                    stringResource(R.string.search_gif_hint),
                    fontSize = 13.sp,
                    color    = cs.onSurfaceVariant.copy(alpha = 0.5f)
                )
            },
            leadingIcon  = {
                Icon(Icons.Default.Search, null,
                    modifier = Modifier.size(18.dp),
                    tint     = cs.onSurfaceVariant.copy(alpha = 0.5f))
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine  = true,
            textStyle   = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            colors      = OutlinedTextFieldDefaults.colors(
                focusedBorderColor      = cs.primary.copy(alpha = 0.5f),
                unfocusedBorderColor    = cs.outlineVariant.copy(alpha = 0.3f),
                focusedContainerColor   = cs.surfaceVariant.copy(alpha = 0.15f),
                unfocusedContainerColor = cs.surfaceVariant.copy(alpha = 0.15f)
            ),
            shape = RoundedCornerShape(10.dp)
        )

        Box(modifier = Modifier.weight(1f)) {
            when {
                isLoading && gifs.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                }
                gifs.isEmpty() && query.isNotEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
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
                else -> LazyVerticalGrid(
                    columns               = GridCells.Fixed(3),
                    contentPadding        = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement   = Arrangement.spacedBy(4.dp),
                    modifier              = Modifier.fillMaxSize()
                ) {
                    items(gifs) { gif ->
                        val preview = gif.downsizedMediumUrl.ifEmpty { gif.fixedWidthUrl.ifEmpty { gif.previewUrl } }
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(cs.surfaceVariant)
                                .clickable {
                                    val url = giphyRepo.getGifUrls(gif).getBestForChat()
                                    if (url.isNotEmpty()) onGifSelected(url)
                                }
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(preview).crossfade(true).build(),
                                contentDescription = gif.title.ifEmpty { "GIF" },
                                contentScale       = ContentScale.Crop,
                                modifier           = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier         = Modifier.fillMaxWidth().background(Color(0xFF0D0D0D)).padding(vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Powered by GIPHY", fontSize = 10.sp, color = Color.White.copy(alpha = 0.45f))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STICKER CONTENT — grid for the selected pack
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StickerContent(
    packId:            Long,
    activePacks:       List<StickerPack>,
    onStickerSelected: (Sticker) -> Unit
) {
    val pack = activePacks.firstOrNull { it.id == packId }
    val cs   = MaterialTheme.colorScheme

    if (pack == null || pack.stickers.isNullOrEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🎭", fontSize = 42.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    text  = stringResource(R.string.sticker_pack_no_available),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant.copy(alpha = 0.55f)
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

// ─────────────────────────────────────────────────────────────────────────────
// STICKER ITEM
// ─────────────────────────────────────────────────────────────────────────────

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
            url?.endsWith(".json") == true || url?.endsWith(".tgs") == true || url?.endsWith(".gif") == true ->
                AnimatedStickerView(url = url ?: "", size = 52.dp)
            url == sticker.emoji ->
                Text(sticker.emoji ?: "🎭", fontSize = 32.sp)
            else -> AsyncImage(
                model              = url,
                contentDescription = sticker.emoji,
                modifier           = Modifier.size(52.dp),
                contentScale       = ContentScale.Fit
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HELPERS
// ─────────────────────────────────────────────────────────────────────────────

private fun getUmpPackIcon(name: String): String = when {
    name.contains("Емоці",    ignoreCase = true) || name.contains("Emotion", ignoreCase = true) -> "😊"
    name.contains("Тварин",   ignoreCase = true) || name.contains("Animal",  ignoreCase = true) -> "🐾"
    name.contains("Святкув",  ignoreCase = true) || name.contains("Celebr",  ignoreCase = true) -> "🎉"
    name.contains("Жест",     ignoreCase = true) || name.contains("Gesture", ignoreCase = true) -> "👋"
    name.contains("Серц",     ignoreCase = true) || name.contains("Heart",   ignoreCase = true) -> "❤️"
    name.contains("WorldMates", ignoreCase = true)                                               -> "🎭"
    name.contains("Emoji",    ignoreCase = true) || name.contains("Емодж",   ignoreCase = true) -> "😄"
    else                                                                                          -> "🗂️"
}

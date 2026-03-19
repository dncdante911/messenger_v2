package com.worldmates.messenger.ui.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.worldmates.messenger.data.model.Message
import androidx.compose.ui.res.stringResource
import com.worldmates.messenger.R
import com.worldmates.messenger.utils.EncryptedMediaHandler

/**
 * 🔍 MEDIA SEARCH SCREEN (ENHANCED)
 *
 * Улучшенный экран поиска медиа-файлов:
 * - Поддержка личных и групповых чатов
 * - Фильтры по типу медиа
 * - Сортировка (дата, размер)
 * - Массовый выбор и экспорт
 * - Миниатюры видео
 * - Кэширование результатов
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MediaSearchScreen(
    chatId: Long? = null,
    groupId: Long? = null,
    onDismiss: () -> Unit,
    onMediaClick: (Message) -> Unit,
    viewModel: MediaSearchViewModel = viewModel()
) {
    val context = LocalContext.current
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedSort by viewModel.selectedSort.collectAsState()
    val selectionMode by viewModel.selectionMode.collectAsState()
    val selectedMessages by viewModel.selectedMessages.collectAsState()

    // 🖼️ Full screen image viewer state
    var showFullScreenImage by remember { mutableStateOf(false) }
    var currentImageIndex by remember { mutableStateOf(0) }
    val imageMessages = remember(searchResults) {
        searchResults.filter { it.type == "image" || it.type == "photo" }
    }

    // 🎨 Photo editor state
    var showPhotoEditor by remember { mutableStateOf(false) }
    var editImageUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(chatId, groupId) {
        viewModel.setChatId(chatId, groupId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (selectionMode) {
                            "Выбрано: ${selectedMessages.size}"
                        } else {
                            "Поиск медиа"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectionMode) {
                            viewModel.exitSelectionMode()
                        } else {
                            onDismiss()
                        }
                    }) {
                        Icon(
                            if (selectionMode) Icons.Default.Close else Icons.Default.ArrowBack,
                            if (selectionMode) stringResource(R.string.cancel) else stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    if (selectionMode) {
                        // Кнопка скачать выбранные
                        IconButton(onClick = { viewModel.exportSelectedMedia(context) }) {
                            Icon(Icons.Default.Download, "Скачать выбранные")
                        }
                        // Кнопка выбрать все
                        IconButton(onClick = { viewModel.selectAll() }) {
                            Icon(Icons.Default.SelectAll, "Выбрать все")
                        }
                    } else {
                        // Меню сортировки
                        var showSortMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.Sort, "Сортировка")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            SortOption.values().forEach { sortOption ->
                                DropdownMenuItem(
                                    text = { Text(sortOption.displayName) },
                                    onClick = {
                                        viewModel.setSortOption(sortOption)
                                        showSortMenu = false
                                    },
                                    leadingIcon = {
                                        if (selectedSort == sortOption) {
                                            Icon(Icons.Default.Check, null)
                                        }
                                    }
                                )
                            }
                        }

                        // Кнопка очистить
                        IconButton(onClick = { viewModel.clearSearch() }) {
                            Icon(Icons.Default.Clear, "Очистить")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 🔍 Search Bar
            SearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.updateSearchQuery(it) },
                onSearch = { viewModel.performSearch() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 🎨 Filter Chips
            MediaFilterChips(
                selectedFilter = selectedFilter,
                onFilterSelected = { viewModel.selectFilter(it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 📊 Results
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                searchResults.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SearchOff,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Text(
                                text = if (searchQuery.isEmpty()) {
                                    "Введите запрос для поиска"
                                } else {
                                    "Ничего не найдено"
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                else -> {
                    MediaResultsGrid(
                        messages = searchResults,
                        filter = selectedFilter,
                        onMediaClick = { message ->
                            // Open images in full screen viewer
                            if (message.type == "image" || message.type == "photo") {
                                val index = imageMessages.indexOf(message)
                                if (index >= 0) {
                                    currentImageIndex = index
                                    showFullScreenImage = true
                                }
                            } else {
                                // For other media types, call the original handler
                                onMediaClick(message)
                            }
                        },
                        selectionMode = selectionMode,
                        selectedMessages = selectedMessages,
                        onToggleSelection = { messageId ->
                            viewModel.toggleSelection(messageId)
                        }
                    )
                }
            }
        }
    }

    // 🖼️ Full Screen Image Viewer
    if (showFullScreenImage && imageMessages.isNotEmpty()) {
        FullScreenImageViewer(
            images = imageMessages,
            initialIndex = currentImageIndex,
            onDismiss = { showFullScreenImage = false },
            onEdit = { imageUrl ->
                editImageUrl = imageUrl
                showPhotoEditor = true
                showFullScreenImage = false
            },
            onDownload = { message ->
                viewModel.downloadSingleMedia(message, context)
            }
        )
    }

    // 🎨 Photo Editor
    if (showPhotoEditor && editImageUrl != null) {
        com.worldmates.messenger.ui.editor.PhotoEditorScreen(
            imageUrl = editImageUrl!!,
            onDismiss = {
                showPhotoEditor = false
                editImageUrl = null
            },
            onSave = { savedFile ->
                android.widget.Toast.makeText(
                    context,
                    "Фото сохранено: ${savedFile.name}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                showPhotoEditor = false
                editImageUrl = null
            }
        )
    }
}

/**
 * 🔍 Search Bar Component
 */
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        placeholder = { Text("Поиск медиа...") },
        leadingIcon = {
            Icon(Icons.Default.Search, "Поиск")
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, "Очистить")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(24.dp)
    )
}

/**
 * 🎨 Media Filter Chips
 */
@Composable
private fun MediaFilterChips(
    selectedFilter: MediaFilter,
    onFilterSelected: (MediaFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MediaFilter.values().forEach { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = { Text(filter.displayName) },
                leadingIcon = {
                    Icon(
                        imageVector = filter.icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }
    }
}

/**
 * 📊 Media Results Grid
 */
@Composable
private fun MediaResultsGrid(
    messages: List<Message>,
    filter: MediaFilter,
    onMediaClick: (Message) -> Unit,
    selectionMode: Boolean = false,
    selectedMessages: Set<Long> = emptySet(),
    onToggleSelection: (Long) -> Unit = {}
) {
    when (filter) {
        MediaFilter.ALL, MediaFilter.PHOTO, MediaFilter.VIDEO -> {
            // Grid layout для фото/видео
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(messages) { message ->
                    MediaGridItem(
                        message = message,
                        onClick = {
                            if (selectionMode) {
                                onToggleSelection(message.id)
                            } else {
                                onMediaClick(message)
                            }
                        },
                        onLongClick = {
                            if (!selectionMode) {
                                onToggleSelection(message.id)
                            }
                        },
                        isSelected = selectedMessages.contains(message.id),
                        selectionMode = selectionMode
                    )
                }
            }
        }
        MediaFilter.AUDIO, MediaFilter.FILE -> {
            // List layout для аудио/файлов
            LazyVerticalGrid(
                columns = GridCells.Fixed(1),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message ->
                    MediaListItem(
                        message = message,
                        onClick = {
                            if (selectionMode) {
                                onToggleSelection(message.id)
                            } else {
                                onMediaClick(message)
                            }
                        },
                        onLongClick = {
                            if (!selectionMode) {
                                onToggleSelection(message.id)
                            }
                        },
                        isSelected = selectedMessages.contains(message.id),
                        selectionMode = selectionMode
                    )
                }
            }
        }
    }
}

/**
 * 📸 Media Grid Item (Photo/Video) with Selection Support
 */
@Composable
private fun MediaGridItem(
    message: Message,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    isSelected: Boolean = false,
    selectionMode: Boolean = false
) {
    val context = LocalContext.current
    val mediaUrl = message.decryptedMediaUrl ?: message.mediaUrl

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        // Изображение или миниатюра видео
        AsyncImage(
            model = if (message.type == "video") {
                // Для видео используем миниатюру (если есть)
                message.mediaUrl?.let { url ->
                    // Добавляем параметр для генерации thumbnail
                    val thumbnailUrl = url.replace("/upload/", "/upload/t_thumbnail/")
                    EncryptedMediaHandler.getFullMediaUrl(thumbnailUrl, message.type)
                }
            } else {
                EncryptedMediaHandler.getFullMediaUrl(mediaUrl, message.type)
            },
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Затемнение при выборе
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            )
        }

        // Checkbox для выбора
        if (selectionMode || isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.White.copy(alpha = 0.7f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Выбрано",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Индикатор видео
        if (message.type == "video" && !selectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Видео",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Длительность (если есть)
        if (message.mediaDuration != null && message.mediaDuration > 0) {
            Text(
                text = formatDuration(message.mediaDuration),
                fontSize = 12.sp,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

/**
 * 📄 Media List Item (Audio/File) with Selection Support
 */
@Composable
private fun MediaListItem(
    message: Message,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    isSelected: Boolean = false,
    selectionMode: Boolean = false
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox для выбора
            if (selectionMode || isSelected) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null  // Handled by onClick
                )
            }

            // Иконка типа файла
            Icon(
                imageVector = when (message.type) {
                    "audio", "voice" -> Icons.Default.AudioFile
                    else -> Icons.Default.InsertDriveFile
                },
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            // Информация о файле
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message.mediaUrl?.substringAfterLast("/") ?: "Файл",
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (message.mediaSize != null && message.mediaSize > 0) {
                        Text(
                            text = formatFileSize(message.mediaSize),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (message.mediaDuration != null && message.mediaDuration > 0) {
                        Text(
                            text = formatDuration(message.mediaDuration),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Кнопка загрузки (только если не в режиме выбора)
            if (!selectionMode) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = stringResource(R.string.download_file),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * 🎨 Media Filter Enum
 */
enum class MediaFilter(
    val displayName: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val mediaTypes: List<String>
) {
    ALL("Все", Icons.Default.GridView, listOf("image", "video", "audio", "voice", "file")),
    PHOTO("Фото", Icons.Default.Image, listOf("image")),
    VIDEO("Видео", Icons.Default.VideoLibrary, listOf("video")),
    AUDIO("Аудио", Icons.Default.AudioFile, listOf("audio", "voice")),
    FILE("Файлы", Icons.Default.InsertDriveFile, listOf("file"))
}

/**
 * 📊 Sort Option Enum
 */
enum class SortOption(
    val displayName: String
) {
    DATE_DESC("Сначала новые"),
    DATE_ASC("Сначала старые"),
    SIZE_DESC("Сначала большие"),
    SIZE_ASC("Сначала маленькие"),
    NAME_ASC("По имени (А-Я)"),
    NAME_DESC("По имени (Я-А)")
}

/**
 * 🕐 Format duration (seconds to MM:SS)
 */
private fun formatDuration(seconds: Long): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(minutes, secs)
}

/**
 * 📦 Format file size
 */
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}

/**
 * 🖼️ FULL SCREEN IMAGE VIEWER
 *
 * Full screen image viewer with:
 * - Pinch to zoom
 * - Swipe to navigate
 * - Image counter
 * - Download button
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun FullScreenImageViewer(
    images: List<Message>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onEdit: (String) -> Unit = {},
    onDownload: (Message) -> Unit = {}
) {
    val context = LocalContext.current
    var currentPage by remember { mutableStateOf(initialIndex) }
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = initialIndex,
        pageCount = { images.size }
    )

    LaunchedEffect(pagerState.currentPage) {
        currentPage = pagerState.currentPage
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Image Pager
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val message = images[page]
            val mediaUrl = message.decryptedMediaUrl ?: message.mediaUrl
            val fullUrl = EncryptedMediaHandler.getFullMediaUrl(mediaUrl, message.type)

            // Zoomable Image
            var scale by remember { mutableStateOf(1f) }
            var offsetX by remember { mutableStateOf(0f) }
            var offsetY by remember { mutableStateOf(0f) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = fullUrl,
                    contentDescription = "Full screen image",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        ),
                    contentScale = if (scale > 1f) ContentScale.Crop else ContentScale.Fit
                )

                // Double tap to reset zoom
                if (scale > 1f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            }
                    )
                }
            }
        }

        // Top Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            color = Color.Black.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = Color.White
                    )
                }

                // Counter
                Text(
                    text = "${currentPage + 1} / ${images.size}",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Row {
                    // Edit button
                    IconButton(
                        onClick = {
                            val message = images[currentPage]
                            val mediaUrl = message.decryptedMediaUrl ?: message.mediaUrl
                            val fullUrl = EncryptedMediaHandler.getFullMediaUrl(mediaUrl, message.type)

                            if (fullUrl != null) {
                                onEdit(fullUrl)
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.edit),
                            tint = Color.White
                        )
                    }

                    // Download button
                    IconButton(
                        onClick = {
                            val message = images[currentPage]
                            onDownload(message)
                        }
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = stringResource(R.string.download_file),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

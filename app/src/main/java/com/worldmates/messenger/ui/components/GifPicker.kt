package com.worldmates.messenger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.res.stringResource
import com.worldmates.messenger.R
import com.worldmates.messenger.data.repository.GiphyRepository
import com.worldmates.messenger.data.repository.GifItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 🎬 GIF Picker - Панель выбора GIF через GIPHY SDK
 *
 * Использование:
 * ```
 * var showGifPicker by remember { mutableStateOf(false) }
 *
 * if (showGifPicker) {
 *     GifPicker(
 *         onGifSelected = { gifUrl ->
 *             viewModel.sendGif(gifUrl)
 *         },
 *         onDismiss = { showGifPicker = false }
 *     )
 * }
 * ```
 *
 * Требуется GIPHY API Key в GiphyRepository.kt
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GifPicker(
    onGifSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val giphyRepo = remember { GiphyRepository.getInstance(context) }

    // State
    var searchQuery by remember { mutableStateOf("") }
    var gifs by remember { mutableStateOf<List<GifItem>>(emptyList()) }
    val isLoading by giphyRepo.isLoading.collectAsState()
    var searchJob: Job? = remember { null }
    var currentMode by remember { mutableStateOf(GifPickerMode.TRENDING) }

    // Загружаем trending GIF при открытии
    LaunchedEffect(Unit) {
        scope.launch {
            val result = giphyRepo.fetchTrendingGifs(limit = 50)
            result.onSuccess { data ->
                gifs = data
                currentMode = GifPickerMode.TRENDING
            }
        }
    }

    // Поиск с debounce
    LaunchedEffect(searchQuery) {
        searchJob?.cancel()

        if (searchQuery.isBlank()) {
            // Возвращаемся к trending
            scope.launch {
                val result = giphyRepo.fetchTrendingGifs(limit = 50)
                result.onSuccess { data ->
                    gifs = data
                    currentMode = GifPickerMode.TRENDING
                }
            }
        } else {
            searchJob = scope.launch {
                delay(500) // Debounce 500ms
                val result = giphyRepo.searchGifs(searchQuery, limit = 50)
                result.onSuccess { data ->
                    gifs = data
                    currentMode = GifPickerMode.SEARCH
                }
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(550.dp),
        color = colorScheme.surface,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column {
            // Заголовок
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "🎬 GIF",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = when (currentMode) {
                            GifPickerMode.TRENDING -> "Популярные"
                            GifPickerMode.SEARCH -> "Результаты: ${gifs.size}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Закрыть",
                        tint = colorScheme.onSurface
                    )
                }
            }

            Divider(color = colorScheme.outlineVariant)

            // Поиск
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.search_gif_hint)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Очистить"
                            )
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colorScheme.primary,
                    unfocusedBorderColor = colorScheme.outlineVariant
                )
            )

            // Контент
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when {
                    isLoading && gifs.isEmpty() -> {
                        // Загрузка
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Загрузка GIF...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    gifs.isEmpty() && searchQuery.isNotEmpty() -> {
                        // Ничего не найдено
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "😕",
                                fontSize = 48.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Ничего не найдено",
                                style = MaterialTheme.typography.titleMedium,
                                color = colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Попробуйте другой запрос",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    gifs.isNotEmpty() -> {
                        // Сетка GIF
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(gifs) { gifItem ->
                                GifGridItem(
                                    gifItem = gifItem,
                                    onClick = {
                                        val gifUrls = giphyRepo.getGifUrls(gifItem)
                                        val gifUrl = gifUrls.getBestForChat()
                                        if (gifUrl.isNotEmpty()) {
                                            onGifSelected(gifUrl)
                                            onDismiss()
                                        }
                                    }
                                )
                            }
                        }

                        // Индикатор загрузки (при подгрузке)
                        if (isLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color.White)
                            }
                        }
                    }
                }
            }

            // Powered by GIPHY
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF121212))
                    .padding(vertical = 8.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Powered by GIPHY",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * GIF Grid Item - элемент сетки
 */
@Composable
private fun GifGridItem(
    gifItem: GifItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    // Получаем URL превью (используем downsizedMediumUrl для баланса качества/размера)
    val previewUrl = gifItem.downsizedMediumUrl.ifEmpty {
        gifItem.fixedWidthUrl.ifEmpty {
            gifItem.downsizedUrl.ifEmpty {
                gifItem.previewUrl
            }
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        if (previewUrl.isNotEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(previewUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = gifItem.title.ifEmpty { "GIF" },
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            // Placeholder
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🎬",
                    fontSize = 32.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Затемнение при наведении (визуальная обратная связь)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.1f))
        )
    }
}

/**
 * Режимы GIF Picker
 */
private enum class GifPickerMode {
    TRENDING,   // Популярные GIF
    SEARCH      // Результаты поиска
}

/**
 * 🎬 GIF Message Bubble - отображение GIF в чате
 */
@Composable
fun GifMessageBubble(
    gifUrl: String,
    isSelf: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = modifier
            .widthIn(max = 280.dp)
            .heightIn(min = 150.dp, max = 200.dp),
        color = if (isSelf) colorScheme.primaryContainer else colorScheme.secondaryContainer,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(gifUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "GIF",
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Fit
            )
        }
    }
}

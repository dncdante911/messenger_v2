package com.worldmates.messenger.ui.strapi

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.worldmates.messenger.R

/**
 * Bottom Sheet для вибору стікерів/гіфок з Strapi
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrapiContentPicker(
    onDismiss: () -> Unit,
    onItemSelected: (String) -> Unit,
    viewModel: StrapiContentViewModel = viewModel()
) {
    val allPacks by viewModel.allPacks.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val filteredPacks by viewModel.filteredPacks.collectAsState()
    val selectedPack by viewModel.selectedPack.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Обробка кнопки "Назад" - повернення до списку паків або закриття
    BackHandler(enabled = selectedPack != null) {
        viewModel.selectPack(null)
    }

    ModalBottomSheet(
        onDismissRequest = {
            // При закритті скидаємо вибраний пак
            viewModel.selectPack(null)
            onDismiss()
        },
        sheetState = sheetState,
        modifier = Modifier.fillMaxHeight(0.7f)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Заголовок з кнопками
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedPack != null) selectedPack?.name ?: stringResource(R.string.strapi_picker_title) else stringResource(R.string.strapi_picker_title),
                    style = MaterialTheme.typography.titleLarge
                )

                Row {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, stringResource(R.string.strapi_refresh))
                    }
                    IconButton(onClick = {
                        viewModel.selectPack(null)
                        onDismiss()
                    }) {
                        Icon(Icons.Default.Close, stringResource(R.string.strapi_close))
                    }
                }
            }

            // Вкладки (тільки коли не обрано пак)
            if (selectedPack == null) {
                TabRow(selectedTabIndex = selectedTab.ordinal) {
                    Tab(
                        selected = selectedTab == StrapiContentViewModel.ContentTab.ALL,
                        onClick = { viewModel.selectTab(StrapiContentViewModel.ContentTab.ALL) },
                        text = { Text(stringResource(R.string.strapi_tab_all)) }
                    )
                    Tab(
                        selected = selectedTab == StrapiContentViewModel.ContentTab.STICKERS,
                        onClick = { viewModel.selectTab(StrapiContentViewModel.ContentTab.STICKERS) },
                        text = { Text(stringResource(R.string.strapi_tab_stickers)) }
                    )
                    Tab(
                        selected = selectedTab == StrapiContentViewModel.ContentTab.GIFS,
                        onClick = { viewModel.selectTab(StrapiContentViewModel.ContentTab.GIFS) },
                        text = { Text(stringResource(R.string.strapi_tab_gifs)) }
                    )
                    Tab(
                        selected = selectedTab == StrapiContentViewModel.ContentTab.EMOJIS,
                        onClick = { viewModel.selectTab(StrapiContentViewModel.ContentTab.EMOJIS) },
                        text = { Text(stringResource(R.string.strapi_tab_emojis)) }
                    )
                }
            }

            when {
                isLoading && allPacks.isEmpty() -> {
                    // Індикатор завантаження
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(stringResource(R.string.strapi_loading), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                error != null && allPacks.isEmpty() -> {
                    // Помилка завантаження
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.strapi_error_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = error ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.refresh() }) {
                                Text(stringResource(R.string.strapi_retry))
                            }
                        }
                    }
                }

                selectedPack != null -> {
                    // Показуємо вміст паку
                    val packItems = selectedPack?.items ?: emptyList()

                    Column {
                        // Кнопка "Назад"
                        TextButton(
                            onClick = { viewModel.selectPack(null) },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Text(stringResource(R.string.strapi_back_to_packs))
                        }

                        if (packItems.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(stringResource(R.string.strapi_pack_empty), style = MaterialTheme.typography.bodyMedium)
                            }
                        } else {
                            // Сітка з елементами
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(4),
                                contentPadding = PaddingValues(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(packItems) { item ->
                                    StrapiItemCard(
                                        item = item,
                                        onClick = {
                                            onItemSelected(item.url)
                                            viewModel.selectPack(null)
                                            onDismiss()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                filteredPacks.isEmpty() -> {
                    // Порожній стан
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Text(stringResource(R.string.strapi_no_content), style = MaterialTheme.typography.bodyMedium)
                            if (!isLoading) {
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(onClick = { viewModel.refresh() }) {
                                    Text(stringResource(R.string.strapi_refresh))
                                }
                            }
                        }
                    }
                }

                else -> {
                    // Показуємо список паків
                    if (isLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredPacks) { pack ->
                            StrapiPackCard(
                                pack = pack,
                                onClick = { viewModel.selectPack(pack) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Картка паку стікерів
 */
@Composable
fun StrapiPackCard(
    pack: com.worldmates.messenger.data.model.StrapiContentPack,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Перший елемент як іконка паку
            if (pack.items.isNotEmpty()) {
                AsyncImage(
                    model = pack.items.first().url,
                    contentDescription = pack.name,
                    modifier = Modifier
                        .size(64.dp)
                        .padding(bottom = 8.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Text(
                text = pack.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2
            )

            Text(
                text = stringResource(R.string.strapi_items_count, pack.items.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Картка окремого стікера/GIF
 */
@Composable
fun StrapiItemCard(
    item: com.worldmates.messenger.data.model.StrapiContentItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = item.url,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}

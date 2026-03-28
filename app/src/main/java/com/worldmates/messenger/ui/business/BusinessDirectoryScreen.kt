package com.worldmates.messenger.ui.business

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.worldmates.messenger.R
import com.worldmates.messenger.data.model.BusinessDirectoryItem

// ─── Brand colors (matching existing business UI palette) ────────────────────
private val DirDeep    = Color(0xFF0D1B2A)
private val DirDark    = Color(0xFF1A2942)
private val DirMid     = Color(0xFF243B55)
private val DirAccent  = Color(0xFF1E90FF)
private val DirGold    = Color(0xFFFFD166)
private val DirCard    = Color(0xFF233044)
private val DirSurface = Color(0xFF1E2D40)

// ─── Main screen composable ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessDirectoryScreen(
    onBack: () -> Unit,
    onChatClick: (Long, String) -> Unit,
    onProfileClick: (Long) -> Unit = {},
    viewModel: BusinessDirectoryViewModel = viewModel()
) {
    val businesses     by viewModel.businesses.collectAsState()
    val categories     by viewModel.categories.collectAsState()
    val isLoading      by viewModel.isLoading.collectAsState()
    val selectedCat    by viewModel.selectedCategory.collectAsState()
    val searchQuery    by viewModel.searchQuery.collectAsState()
    val error          by viewModel.error.collectAsState()

    var showSearch by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }

    // Sync local search text → viewModel
    LaunchedEffect(searchText) {
        viewModel.setSearch(searchText)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DirDeep)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        if (showSearch) {
                            OutlinedTextField(
                                value         = searchText,
                                onValueChange = { searchText = it },
                                placeholder   = {
                                    Text(
                                        text  = stringResource(R.string.business_directory_search_hint),
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 14.sp
                                    )
                                },
                                singleLine    = true,
                                colors        = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor   = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = DirAccent,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                    cursorColor        = DirAccent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                text       = stringResource(R.string.business_directory),
                                color      = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize   = 18.sp
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector        = Icons.Default.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                                tint               = Color.White
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            showSearch = !showSearch
                            if (!showSearch) {
                                searchText = ""
                            }
                        }) {
                            Icon(
                                imageVector        = if (showSearch) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = stringResource(R.string.search),
                                tint               = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = DirDark
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // ── Category chips ────────────────────────────────────────────
                CategoryChipsRow(
                    categories      = categories,
                    selectedCategory = selectedCat,
                    onCategorySelect = { viewModel.setCategory(it) }
                )

                // ── Business list ─────────────────────────────────────────────
                when {
                    isLoading -> LoadingState()
                    businesses.isEmpty() -> EmptyState()
                    else -> BusinessList(
                        businesses     = businesses,
                        onChatClick    = onChatClick,
                        onProfileClick = onProfileClick
                    )
                }
            }
        }

        // Error snackbar
        error?.let { errMsg ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                containerColor = Color(0xFFB00020)
            ) {
                Text(text = errMsg, color = Color.White)
            }
        }
    }
}

// ─── Category chips row ───────────────────────────────────────────────────────

@Composable
private fun CategoryChipsRow(
    categories: List<String>,
    selectedCategory: String?,
    onCategorySelect: (String?) -> Unit
) {
    LazyRow(
        modifier            = Modifier
            .fillMaxWidth()
            .background(DirDark)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // "All" chip
        item {
            CategoryChip(
                label    = stringResource(R.string.business_directory_all_categories),
                selected = selectedCategory == null,
                onClick  = { onCategorySelect(null) }
            )
        }
        items(categories) { cat ->
            CategoryChip(
                label    = cat,
                selected = selectedCategory == cat,
                onClick  = { onCategorySelect(cat) }
            )
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape       = RoundedCornerShape(50),
        color       = if (selected) DirAccent else DirMid,
        modifier    = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text     = label,
            color    = Color.White,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

// ─── Business list ────────────────────────────────────────────────────────────

@Composable
private fun BusinessList(
    businesses: List<BusinessDirectoryItem>,
    onChatClick: (Long, String) -> Unit,
    onProfileClick: (Long) -> Unit
) {
    LazyColumn(
        modifier              = Modifier.fillMaxSize(),
        contentPadding        = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement   = Arrangement.spacedBy(10.dp)
    ) {
        items(businesses, key = { it.userId }) { item ->
            BusinessCard(item = item, onChatClick = onChatClick, onProfileClick = onProfileClick)
        }
    }
}

@Composable
private fun BusinessCard(
    item: BusinessDirectoryItem,
    onChatClick: (Long, String) -> Unit,
    onProfileClick: (Long) -> Unit
) {
    Surface(
        shape          = RoundedCornerShape(14.dp),
        color          = DirCard,
        tonalElevation = 2.dp,
        modifier       = Modifier
            .fillMaxWidth()
            .clickable { onProfileClick(item.userId) }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // ── Avatar ────────────────────────────────────────────────────────
            BusinessAvatar(
                avatarUrl    = item.avatar,
                businessName = item.businessName,
                modifier     = Modifier.size(56.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // ── Content ───────────────────────────────────────────────────────
            Column(modifier = Modifier.weight(1f)) {
                // Business name + verified badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text       = item.businessName,
                        color      = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 15.sp,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        modifier   = Modifier.weight(1f, fill = false)
                    )
                    if (item.isVerified) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector        = Icons.Default.Verified,
                            contentDescription = stringResource(R.string.business_directory_verified),
                            tint               = DirAccent,
                            modifier           = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Category chip
                Surface(
                    shape = RoundedCornerShape(50),
                    color = DirMid
                ) {
                    Text(
                        text     = item.category,
                        color    = DirGold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }

                // Description
                item.description?.takeIf { it.isNotBlank() }?.let { desc ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text     = desc,
                        color    = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Address
                item.address?.takeIf { it.isNotBlank() }?.let { addr ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, null,
                            tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text     = if (item.distance != null) "$addr • ${item.distance} km" else addr,
                            color    = Color.White.copy(alpha = 0.55f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Phone
                item.phone?.takeIf { it.isNotBlank() }?.let { phone ->
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Phone, null,
                            tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(text = phone, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, maxLines = 1)
                    }
                }

                // Website
                item.website?.takeIf { it.isNotBlank() }?.let { website ->
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Language, null,
                            tint = DirAccent.copy(alpha = 0.7f), modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(text = website, color = DirAccent.copy(alpha = 0.7f), fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // "Написати" button
                Button(
                    onClick  = { onChatClick(item.userId, item.businessName) },
                    shape    = RoundedCornerShape(8.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = DirAccent),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.Message, null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text       = stringResource(R.string.business_directory_message_btn),
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ─── Avatar ───────────────────────────────────────────────────────────────────

@Composable
private fun BusinessAvatar(
    avatarUrl: String?,
    businessName: String,
    modifier: Modifier = Modifier
) {
    if (!avatarUrl.isNullOrBlank()) {
        AsyncImage(
            model             = avatarUrl,
            contentDescription = businessName,
            contentScale      = ContentScale.Crop,
            modifier          = modifier
                .clip(CircleShape)
                .background(DirMid)
        )
    } else {
        // Initials fallback
        val initials = businessName
            .split(" ")
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .ifEmpty { "?" }

        Box(
            modifier          = modifier
                .clip(CircleShape)
                .background(DirAccent),
            contentAlignment  = Alignment.Center
        ) {
            Text(
                text       = initials,
                color      = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize   = 18.sp
            )
        }
    }
}

// ─── Loading skeleton ─────────────────────────────────────────────────────────

@Composable
private fun LoadingState() {
    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(6) {
            SkeletonCard()
        }
    }
}

@Composable
private fun SkeletonCard() {
    Surface(
        shape  = RoundedCornerShape(14.dp),
        color  = DirCard,
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(DirMid)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.fillMaxWidth(0.55f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(DirMid))
                Box(modifier = Modifier.fillMaxWidth(0.35f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(DirMid))
                Box(modifier = Modifier.fillMaxWidth(0.75f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(DirMid))
            }
        }
    }
}

// ─── Empty state ──────────────────────────────────────────────────────────────

@Composable
private fun EmptyState() {
    Box(
        modifier         = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector        = Icons.Default.SearchOff,
                contentDescription = null,
                tint               = Color.White.copy(alpha = 0.35f),
                modifier           = Modifier.size(64.dp)
            )
            Text(
                text     = stringResource(R.string.business_directory_empty),
                color    = Color.White.copy(alpha = 0.55f),
                fontSize = 16.sp
            )
        }
    }
}

package com.worldmates.messenger.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.request.ImageRequest
import com.worldmates.messenger.data.model.UserAvatar

// ─── Avatar pager (Telegram-style header) ────────────────────────────────────

/**
 * Horizontal pager for user avatars — shows in the profile header.
 * Swiping left/right browses photos; dots indicator below.
 * Own profile: long-press opens management sheet.
 */
private val PREMIUM_RING_COLORS = listOf(
    Color(0xFF667EEA),
    Color(0xFF764BA2),
    Color(0xFFf953c6),
    Color(0xFFb91d73),
    Color(0xFF667EEA),
)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AvatarPager(
    avatars: List<UserAvatar>,
    isOwnProfile: Boolean,
    modifier: Modifier = Modifier,
    isPremium: Boolean = false,
    onAddPhotoClick: (() -> Unit)? = null,
    onManageClick: ((UserAvatar) -> Unit)? = null,
) {
    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(GifDecoder.Factory()) }
            .build()
    }

    if (avatars.isEmpty()) {
        Box(
            modifier = modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector  = Icons.Default.Person,
                contentDescription = null,
                tint   = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(60.dp)
            )
            if (isOwnProfile && onAddPhotoClick != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { onAddPhotoClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
        return
    }

    val pagerState = rememberPagerState { avatars.size }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            // Premium gradient ring drawn behind the avatar
            if (isPremium) {
                Box(
                    modifier = Modifier
                        .size(126.dp)
                        .background(
                            brush = Brush.sweepGradient(PREMIUM_RING_COLORS),
                            shape = CircleShape
                        )
                )
            }

            HorizontalPager(
                state    = pagerState,
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
            ) { page ->
                val avatar = avatars[page]
                val model  = ImageRequest.Builder(context)
                    .data(avatar.url)
                    .crossfade(true)
                    .build()

                AsyncImage(
                    model         = model,
                    imageLoader   = imageLoader,
                    contentDescription = null,
                    contentScale  = ContentScale.Crop,
                    modifier      = Modifier
                        .fillMaxSize()
                        .then(
                            if (isOwnProfile && onManageClick != null)
                                Modifier.pointerInput(avatar.id) {
                                    detectTapGestures(onLongPress = { onManageClick(avatar) })
                                }
                            else Modifier
                        )
                )
            }

            // "+" button overlay for own profile
            if (isOwnProfile && onAddPhotoClick != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(
                            x = if (isPremium) (-3).dp else 0.dp,
                            y = if (isPremium) (-3).dp else 0.dp
                        )
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { onAddPhotoClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }

        // Dots indicator
        if (avatars.size > 1) {
            Spacer(Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(avatars.size) { idx ->
                    Box(
                        modifier = Modifier
                            .size(if (idx == pagerState.currentPage) 8.dp else 5.dp)
                            .clip(CircleShape)
                            .background(
                                if (idx == pagerState.currentPage)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                    )
                }
            }
        }
    }
}

// ─── Compact pager for other user's profile (read-only, circular) ────────────

/**
 * Used in UserProfileActivity header — loads the user's avatar gallery
 * and shows as a circular pager. No add/manage controls.
 */
@Composable
fun UserAvatarPagerInProfile(
    userId: Long,
    fallbackUrl: String?,
    modifier: Modifier = Modifier,
    isPremium: Boolean = false,
    galleryViewModel: AvatarGalleryViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        key = "avatars_$userId"
    )
) {
    val avatars by galleryViewModel.avatars.collectAsState()

    LaunchedEffect(userId) { galleryViewModel.loadAvatars(userId) }

    val displayAvatars = if (avatars.isNotEmpty()) avatars else {
        fallbackUrl?.let {
            listOf(com.worldmates.messenger.data.model.UserAvatar(
                id = 0, url = it, filePath = it
            ))
        } ?: emptyList()
    }

    AvatarPager(
        avatars      = displayAvatars,
        isOwnProfile = false,
        modifier     = modifier,
        isPremium    = isPremium
    )
}

// ─── Avatar management bottom sheet ──────────────────────────────────────────

/**
 * Bottom sheet to manage avatars:
 *  – Grid of existing avatars
 *  – Set as main / delete actions
 *  – Upload button (+ limit badge for premium)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarManagementSheet(
    viewModel: AvatarGalleryViewModel = viewModel(),
    userId: Long,
    isPremium: Boolean,
    onDismiss: () -> Unit,
) {
    val context  = LocalContext.current
    val avatars  by viewModel.avatars.collectAsState()
    val loading  by viewModel.isLoading.collectAsState()
    val limitInfo by viewModel.limitInfo.collectAsState()

    var selectedAvatar by remember { mutableStateOf<UserAvatar?>(null) }
    var showConfirmDelete by remember { mutableStateOf(false) }

    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(GifDecoder.Factory()) }
            .build()
    }

    val maxAvatars = if (isPremium) 25 else 10
    val count      = avatars.size

    // Photo picker
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.uploadAvatar(context, it, setAsMain = count == 0) }
    }

    LaunchedEffect(userId) { viewModel.loadAvatars(userId) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Фото профілю",
                    style     = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                // Limit badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isPremium)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        "$count / $maxAvatars",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 12.sp,
                        color    = if (isPremium)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!isPremium && count >= 8) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Row(
                        Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Star,
                            null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Premium: до 25 фото та анімованих аватарок",
                            fontSize = 12.sp,
                            color    = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (loading) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                // Avatar grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.heightIn(max = 400.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement   = Arrangement.spacedBy(4.dp),
                ) {
                    // "Add photo" tile
                    if (count < maxAvatars) {
                        item {
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { launcher.launch("image/*") },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Add, null,
                                        tint     = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp))
                                    Text("Додати", fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }

                    items(avatars, key = { it.id }) { avatar ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedAvatar = avatar }
                        ) {
                            val model = ImageRequest.Builder(context)
                                .data(avatar.url)
                                .crossfade(true)
                                .build()
                            AsyncImage(
                                model         = model,
                                imageLoader   = imageLoader,
                                contentDescription = null,
                                contentScale  = ContentScale.Crop,
                                modifier      = Modifier.fillMaxSize()
                            )

                            // Main avatar crown indicator
                            if (avatar.position == 0) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopStart)
                                        .padding(4.dp)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.5f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Star, null, tint = Color.Yellow,
                                        modifier = Modifier.size(12.dp))
                                }
                            }

                            // Animated badge
                            if (avatar.isAnimated) {
                                Box(
                                    Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.Black.copy(alpha = 0.6f))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text("GIF", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // Avatar action dialog (set main / delete)
    selectedAvatar?.let { avatar ->
        AlertDialog(
            onDismissRequest = { selectedAvatar = null },
            title = { Text(if (avatar.position == 0) "Основне фото" else "Фото профілю") },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (avatar.position != 0) {
                        TextButton(
                            onClick = {
                                viewModel.setMainAvatar(avatar.id)
                                selectedAvatar = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Star, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Зробити основним")
                        }
                    }
                    TextButton(
                        onClick = { showConfirmDelete = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Видалити фото")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedAvatar = null }) { Text("Скасувати") }
            }
        )
    }

    // Delete confirmation
    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            title = { Text("Видалити фото?") },
            text  = { Text("Це фото буде видалено з вашого профілю.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedAvatar?.let { viewModel.deleteAvatar(it.id) }
                        selectedAvatar  = null
                        showConfirmDelete = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Видалити") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDelete = false }) { Text("Скасувати") }
            }
        )
    }
}

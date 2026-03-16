package com.worldmates.messenger.ui.profile

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worldmates.messenger.R
import com.worldmates.messenger.data.model.User
import kotlinx.coroutines.delay

/**
 * Экран профиля (собственный).
 * Дизайн: плавные spring-анимации появления, QR-диалог, вкладки Медиа/Архив.
 */
@Composable
fun MyProfileScreen(
    user: User,
    onEditClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onThemesClick: () -> Unit,
    onAvatarSelected: (Uri) -> Unit,
    onQrCodeClick: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    profileViewModel: UserProfileViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    avatarGalleryViewModel: AvatarGalleryViewModel = viewModel()
) {
    val context = LocalContext.current
    var showQrDialog          by remember { mutableStateOf(false) }
    var showAvatarSheet       by remember { mutableStateOf(false) }
    var showAppearanceDialog  by remember { mutableStateOf(false) }

    val avatars by avatarGalleryViewModel.avatars.collectAsState()

    LaunchedEffect(user.userId) {
        avatarGalleryViewModel.loadAvatars(user.userId)
    }

    // Staggered entrance animation states
    var avatarVisible  by remember { mutableStateOf(false) }
    var nameVisible    by remember { mutableStateOf(false) }
    var buttonsVisible by remember { mutableStateOf(false) }
    var infoVisible    by remember { mutableStateOf(false) }
    var tabsVisible    by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        avatarVisible  = true
        delay(110)
        nameVisible    = true
        delay(90)
        buttonsVisible = true
        delay(80)
        infoVisible    = true
        delay(70)
        tabsVisible    = true
    }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { onAvatarSelected(it) } }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        // ── Top bar (only QR, no 3-dots) ──────────────────────────────────
        item {
            ProfileTopBar(onQrCodeClick = { showQrDialog = true })
        }

        // ── Avatar ─────────────────────────────────────────────────────────
        item {
            AnimatedVisibility(
                visible = avatarVisible,
                enter = scaleIn(
                    initialScale = 0.65f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness    = Spring.StiffnessMedium
                    )
                ) + fadeIn(tween(380))
            ) {
                if (avatars.isEmpty()) {
                    // Fallback to single-avatar view until gallery loads
                    ProfileAvatarSection(
                        avatarUrl  = user.avatar,
                        isPro      = user.isPro > 0,
                        onCameraClick = { showAvatarSheet = true }
                    )
                } else {
                    AvatarPager(
                        avatars       = avatars,
                        isOwnProfile  = true,
                        modifier      = Modifier.padding(vertical = 4.dp),
                        onAddPhotoClick = { showAvatarSheet = true },
                        onManageClick   = { showAvatarSheet = true }
                    )
                }
            }
        }

        // ── Name + status ──────────────────────────────────────────────────
        item {
            AnimatedVisibility(
                visible = nameVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec  = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness    = Spring.StiffnessMediumLow
                    )
                ) + fadeIn(tween(300))
            ) {
                ProfileNameSection(user = user)
            }
        }

        // ── Action buttons ─────────────────────────────────────────────────
        item {
            AnimatedVisibility(
                visible = buttonsVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec  = spring(stiffness = Spring.StiffnessMediumLow)
                ) + fadeIn(tween(260))
            ) {
                ProfileActionButtons(
                    onPhotoClick       = { avatarPicker.launch("image/*") },
                    onEditClick        = onEditClick,
                    onSettingsClick    = onSettingsClick,
                    onThemesClick      = onThemesClick,
                    onAppearanceClick  = { showAppearanceDialog = true }
                )
            }
        }

        // ── Info card ──────────────────────────────────────────────────────
        item {
            AnimatedVisibility(
                visible = infoVisible,
                enter = slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec  = spring(stiffness = Spring.StiffnessMediumLow)
                ) + fadeIn(tween(300))
            ) {
                ProfileInfoCard(user = user)
            }
        }

        // ── Медиа / Архив tabs ─────────────────────────────────────────────
        item {
            AnimatedVisibility(
                visible = tabsVisible,
                enter   = fadeIn(tween(420)) + expandVertically()
            ) {
                ProfileContentTabs()
            }
        }
    }

    // ── QR Dialog ──────────────────────────────────────────────────────────
    if (showQrDialog) {
        ProfileQrDialog(user = user, onDismiss = { showQrDialog = false })
    }

    // ── Avatar Gallery Management Sheet ────────────────────────────────────
    if (showAvatarSheet) {
        AvatarManagementSheet(
            viewModel  = avatarGalleryViewModel,
            userId     = user.userId,
            isPremium  = user.isPro > 0,
            onDismiss  = { showAvatarSheet = false }
        )
    }

    // ── Profile Appearance Dialog ───────────────────────────────────────────
    if (showAppearanceDialog) {
        ProfileAppearanceDialog(
            user      = user,
            onDismiss = { showAppearanceDialog = false },
            onSave    = { accent, badge, style ->
                profileViewModel.updateAppearance(
                    profileAccent      = accent,
                    profileBadge       = badge,
                    profileHeaderStyle = style,
                )
                showAppearanceDialog = false
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TOP BAR  (только QR, без 3-точек)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileTopBar(onQrCodeClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        IconButton(
            onClick  = onQrCodeClick,
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Icon(
                Icons.Default.QrCode2,
                contentDescription = "QR-код",
                tint     = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// АВАТАР  (spring-scale + camera overlay + gradient ring для Pro)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileAvatarSection(
    avatarUrl: String?,
    isPro: Boolean,
    onCameraClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val avatarScale by animateFloatAsState(
        targetValue    = if (isPressed) 0.95f else 1f,
        animationSpec  = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMedium
        ),
        label = "avatar_press_scale"
    )

    Box(
        modifier = Modifier
            .padding(top = 4.dp, bottom = 4.dp)
            .size(134.dp)
            .scale(avatarScale),
        contentAlignment = Alignment.Center
    ) {
        // Gradient ring (Pro)
        if (isPro) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush  = Brush.sweepGradient(
                            listOf(
                                Color(0xFF667EEA),
                                Color(0xFF764BA2),
                                Color(0xFFf953c6),
                                Color(0xFFb91d73),
                                Color(0xFF667EEA)
                            )
                        ),
                        shape  = CircleShape
                    )
            )
        }

        AsyncImage(
            model            = avatarUrl,
            contentDescription = "Аватар",
            modifier         = Modifier
                .size(if (isPro) 126.dp else 134.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                .then(
                    if (!isPro) Modifier.border(
                        2.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        CircleShape
                    ) else Modifier
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication        = null,
                    onClick           = onCameraClick
                ),
            contentScale = ContentScale.Crop
        )

        // Camera button (bottom-right)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(36.dp)
                .shadow(6.dp, CircleShape)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .clickable(onClick = onCameraClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.CameraAlt,
                contentDescription = "Сменить фото",
                tint     = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ИМЯ + СТАТУС
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileNameSection(user: User) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Имя + галочка верификации
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text       = "${user.firstName ?: ""} ${user.lastName ?: ""}".trim()
                    .ifBlank { user.username },
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center
            )
            if (user.verified == 1) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Верифицирован",
                    tint     = Color(0xFF0084FF),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(Modifier.height(3.dp))

        Text(
            text  = "@${user.username}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(6.dp))

        // Статус онлайн — chip
        val online = user.lastSeenStatus == "online"
        Surface(
            color = if (online) Color(0xFF4CAF50).copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            if (online) Color(0xFF4CAF50)
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.45f),
                            CircleShape
                        )
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text  = if (online) stringResource(R.string.online) else stringResource(R.string.last_seen_recently),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (online) Color(0xFF4CAF50)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4 КНОПКИ ДЕЙСТВИЙ  (с press-анимацией)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileActionButtons(
    onPhotoClick:      () -> Unit,
    onEditClick:       () -> Unit,
    onSettingsClick:   () -> Unit,
    onThemesClick:     () -> Unit,
    onAppearanceClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ProfileActionButton(Icons.Default.Edit,         stringResource(R.string.profile_action_edit),      onEditClick,       Modifier.weight(1f))
        ProfileActionButton(Icons.Default.CameraAlt,    stringResource(R.string.profile_action_photo),     onPhotoClick,      Modifier.weight(1f))
        ProfileActionButton(Icons.Default.ColorLens,    stringResource(R.string.profile_action_customize), onAppearanceClick, Modifier.weight(1f))
        ProfileActionButton(Icons.Default.Settings,     stringResource(R.string.profile_action_settings),  onSettingsClick,   Modifier.weight(1f))
    }
}

@Composable
private fun ProfileActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue   = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMedium
        ),
        label = "btn_scale"
    )

    Card(
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick           = onClick
            ),
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint     = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(5.dp))
            Text(
                label,
                style     = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines  = 1,
                color     = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// INFO CARD  (collapsible + animateContentSize)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileInfoCard(user: User) {
    var expanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness    = Spring.StiffnessMediumLow
                )
            ),
        shape  = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {

            // Заголовок карточки со стрелкой
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.profile_info),
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onSurface
                )
                val chevronAngle by animateFloatAsState(
                    targetValue   = if (expanded) 180f else 0f,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label         = "chevron_rotation"
                )
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(chevronAngle),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Содержимое
            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                ) {
                    if (!user.phoneNumber.isNullOrBlank()) {
                        ProfileInfoRow(user.phoneNumber, stringResource(R.string.phone_label), Icons.Default.Phone)
                        InfoDivider()
                    }
                    if (!user.about.isNullOrBlank()) {
                        ProfileInfoRow(user.about, stringResource(R.string.about), Icons.Default.Info)
                        InfoDivider()
                    }
                    ProfileInfoRow("@${user.username}", stringResource(R.string.username_label), Icons.Default.Person)
                    if (!user.birthday.isNullOrBlank()) {
                        InfoDivider()
                        ProfileInfoRow(formatBirthday(user.birthday), stringResource(R.string.birthday_profile_label), Icons.Default.Cake)
                    }
                    if (!user.city.isNullOrBlank()) {
                        InfoDivider()
                        ProfileInfoRow(user.city, stringResource(R.string.city), Icons.Default.LocationOn)
                    }
                    if (!user.working.isNullOrBlank()) {
                        InfoDivider()
                        ProfileInfoRow(user.working, stringResource(R.string.work_profile_label), Icons.Default.Work)
                    }
                    if (!user.website.isNullOrBlank()) {
                        InfoDivider()
                        ProfileInfoRow(user.website, stringResource(R.string.website_profile_label), Icons.Default.Language)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileInfoRow(value: String, label: String, icon: ImageVector) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint     = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(11.dp))
        Column {
            Text(
                value,
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InfoDivider() {
    Divider(
        modifier = Modifier.padding(vertical = 2.dp),
        color    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// ВКЛАДКИ: Медиа / Архив
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileContentTabs() {
    val tabs        = listOf(stringResource(R.string.tab_media), stringResource(R.string.tab_archive))
    var selectedTab by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor   = Color.Transparent,
            contentColor     = MaterialTheme.colorScheme.primary,
            indicator        = { positions ->
                SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(positions[selectedTab]),
                    height   = 2.5.dp,
                    color    = MaterialTheme.colorScheme.primary
                )
            },
            divider = {
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick  = { selectedTab = index },
                    text = {
                        Text(
                            title,
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal,
                            color      = if (selectedTab == index)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        }

        Crossfade(
            targetState  = selectedTab,
            animationSpec = tween(durationMillis = 280),
            label        = "tab_content_crossfade"
        ) { tab ->
            when (tab) {
                0 -> MediaTabContent()
                1 -> ArchiveTabContent()
            }
        }
    }
}

// ─── Вкладка "Медиа" ──────────────────────────────────────────────────────────

@Composable
private fun MediaTabContent() {
    val subTabs     = listOf(stringResource(R.string.tab_media_personal), stringResource(R.string.tab_media_received))
    var selectedSub by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxWidth()) {
        // Sub-tabs (pill style)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            subTabs.forEachIndexed { index, title ->
                val active = selectedSub == index
                val subScale by animateFloatAsState(
                    targetValue   = if (active) 1f else 0.96f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness    = Spring.StiffnessMedium
                    ),
                    label = "sub_tab_scale_$index"
                )
                Surface(
                    color    = if (active)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape    = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .scale(subScale)
                        .clickable { selectedSub = index }
                        .animateContentSize(
                            animationSpec = spring(stiffness = Spring.StiffnessMedium)
                        )
                ) {
                    Text(
                        title,
                        modifier   = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                        style      = MaterialTheme.typography.labelMedium,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color      = if (active)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Crossfade(
            targetState   = selectedSub,
            animationSpec = tween(240),
            label         = "media_sub_crossfade"
        ) { sub ->
            EmptyMediaPlaceholder(
                icon     = if (sub == 0) Icons.Default.Collections else Icons.Default.Photo,
                title    = if (sub == 0) stringResource(R.string.media_personal_empty_title) else stringResource(R.string.media_received_empty_title),
                subtitle = if (sub == 0) stringResource(R.string.media_personal_empty_subtitle) else stringResource(R.string.media_received_empty_subtitle)
            )
        }
    }
}

// ─── Вкладка "Архив" ──────────────────────────────────────────────────────────

@Composable
private fun ArchiveTabContent() {
    EmptyMediaPlaceholder(
        icon     = Icons.Default.Archive,
        title    = stringResource(R.string.archive_empty_title),
        subtitle = stringResource(R.string.archive_empty_subtitle)
    )
}

@Composable
private fun EmptyMediaPlaceholder(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(54.dp),
            tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        )
        Spacer(Modifier.height(14.dp))
        Text(
            title,
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color      = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(5.dp))
        Text(
            subtitle,
            style     = MaterialTheme.typography.bodySmall,
            color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            lineHeight = 19.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// QR-ДИАЛОГ ПРОФИЛЯ
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileQrDialog(user: User, onDismiss: () -> Unit) {
    val qrContent = "https://worldmates.club/u/${user.username}"
    val qrBitmap  = remember(qrContent) { generateProfileQrCode(qrContent) }
    val context   = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape        = RoundedCornerShape(26.dp),
            color        = MaterialTheme.colorScheme.surface,
            tonalElevation = 10.dp,
            modifier     = Modifier.padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Аватар пользователя в диалоге
                AsyncImage(
                    model              = user.avatar,
                    contentDescription = null,
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    contentScale = ContentScale.Crop
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text       = "${user.firstName ?: ""} ${user.lastName ?: ""}".trim()
                        .ifBlank { user.username },
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text  = "@${user.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(22.dp))

                // QR-код
                Surface(
                    shape    = RoundedCornerShape(18.dp),
                    color    = Color.White,
                    modifier = Modifier.size(224.dp)
                ) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap             = qrBitmap.asImageBitmap(),
                            contentDescription = "QR-код профиля",
                            modifier           = Modifier
                                .fillMaxSize()
                                .padding(18.dp)
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(34.dp))
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    stringResource(R.string.qr_scan_hint),
                    style     = MaterialTheme.typography.bodySmall,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(22.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val shareText = context.getString(R.string.qr_share_text, qrContent)
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_SEND
                            ).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(
                                android.content.Intent.createChooser(intent, context.getString(R.string.share))
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Share, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.share))
                    }

                    OutlinedButton(
                        onClick  = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(14.dp)
                    ) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        }
    }
}

private fun generateProfileQrCode(text: String): Bitmap? {
    return try {
        val size      = 512
        val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val bitmap    = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(
                    x, y,
                    if (bitMatrix[x, y]) android.graphics.Color.BLACK
                    else android.graphics.Color.WHITE
                )
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// UTILS
// ─────────────────────────────────────────────────────────────────────────────

private fun formatBirthday(birthday: String): String {
    return try {
        val parts = birthday.split("-")
        if (parts.size == 3) {
            val year  = parts[0].toInt()
            val month = parts[1].toInt()
            val day   = parts[2].toInt()
            val names = listOf("", "янв.", "февр.", "мар.", "апр.", "мая", "июн.",
                "июл.", "авг.", "сент.", "окт.", "нояб.", "дек.")
            val age   = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) - year
            "$day ${names.getOrElse(month) { "" }} $year ($age лет)"
        } else birthday
    } catch (e: Exception) { birthday }
}

// ─────────────────────────────────────────────────────────────────────────────
// PROFILE APPEARANCE DIALOG
// ─────────────────────────────────────────────────────────────────────────────

/** Accent color presets — same list as on the server. */
private val ACCENT_PRESETS = listOf(
    "#667EEA", "#764BA2", "#FF6B35", "#4CAF50",
    "#F44336", "#00BCD4", "#E91E63", "#FF9800",
    "#795548", "#607D8B", "#009688", "#3F51B5",
)

/** Badge emoji presets shown as quick-picks. */
private val BADGE_PRESETS = listOf("", "🔥", "⭐", "💎", "🎮", "🎵", "📸", "✈️", "🌍", "💼", "🎓", "🏆")

@Composable
fun ProfileAppearanceDialog(
    user:     com.worldmates.messenger.data.model.User,
    onDismiss: () -> Unit,
    onSave:   (accent: String?, badge: String?, style: String?) -> Unit
) {
    var selectedAccent by remember { mutableStateOf(user.profileAccent ?: "#667EEA") }
    var selectedBadge  by remember { mutableStateOf(user.profileBadge  ?: "") }
    var selectedStyle  by remember { mutableStateOf(user.profileHeaderStyle ?: "gradient") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape          = RoundedCornerShape(24.dp),
            color          = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier       = Modifier.padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text       = stringResource(R.string.profile_customize_title),
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.padding(bottom = 16.dp)
                )

                // ── Accent color ──────────────────────────────────────────
                Text(
                    text     = stringResource(R.string.profile_accent_color),
                    style    = MaterialTheme.typography.labelMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ACCENT_PRESETS.forEach { hex ->
                        val color = try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.Gray }
                        val isSelected = hex.equals(selectedAccent, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .shadow(if (isSelected) 6.dp else 1.dp, CircleShape)
                                .background(color, CircleShape)
                                .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.background, CircleShape) else Modifier)
                                .clickable { selectedAccent = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint     = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                // ── Header style ──────────────────────────────────────────
                Text(
                    text     = stringResource(R.string.profile_header_style_label),
                    style    = MaterialTheme.typography.labelMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "gradient" to stringResource(R.string.header_style_gradient),
                        "minimal"  to stringResource(R.string.header_style_minimal),
                        "pattern"  to stringResource(R.string.header_style_pattern),
                    ).forEach { (value, label) ->
                        val isSelected = selectedStyle == value
                        val bgColor = try { Color(android.graphics.Color.parseColor(selectedAccent)) } catch (e: Exception) { Color(0xFF667EEA) }
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedStyle = value },
                            shape    = RoundedCornerShape(12.dp),
                            color    = if (isSelected) bgColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border   = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, bgColor) else null
                        ) {
                            Text(
                                text       = label,
                                style      = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color      = if (isSelected) bgColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign  = TextAlign.Center,
                                modifier   = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                // ── Badge emoji ───────────────────────────────────────────
                Text(
                    text     = stringResource(R.string.profile_badge_label),
                    style    = MaterialTheme.typography.labelMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BADGE_PRESETS.forEach { emoji ->
                        val isSelected = selectedBadge == emoji
                        val accentColor = try { Color(android.graphics.Color.parseColor(selectedAccent)) } catch (e: Exception) { Color(0xFF667EEA) }
                        Surface(
                            modifier = Modifier
                                .size(40.dp)
                                .clickable { selectedBadge = emoji },
                            shape    = RoundedCornerShape(10.dp),
                            color    = if (isSelected) accentColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border   = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, accentColor) else null
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (emoji.isBlank()) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = null,
                                        tint     = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    Text(text = emoji, fontSize = 20.sp, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(22.dp))

                // ── Buttons ───────────────────────────────────────────────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick  = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick  = {
                            onSave(
                                if (selectedAccent != user.profileAccent) selectedAccent else null,
                                if (selectedBadge  != user.profileBadge)  selectedBadge  else null,
                                if (selectedStyle  != user.profileHeaderStyle) selectedStyle  else null,
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(14.dp)
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }
    }
}

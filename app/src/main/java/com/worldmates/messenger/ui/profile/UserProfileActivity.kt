package com.worldmates.messenger.ui.profile

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.worldmates.messenger.R
import com.worldmates.messenger.data.model.User
import com.worldmates.messenger.ui.theme.WorldMatesThemedApp
import com.worldmates.messenger.utils.LanguageManager

class UserProfileActivity : AppCompatActivity() {

    private lateinit var viewModel: UserProfileViewModel

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this).get(UserProfileViewModel::class.java)

        // Get user ID from intent (null = current user)
        val userId = intent.getLongExtra("user_id", -1L).takeIf { it != -1L }

        setContent {
            WorldMatesThemedApp {
                UserProfileScreen(
                    viewModel = viewModel,
                    userId = userId,
                    onBackClick = { finish() },
                    onSettingsClick = {
                        startActivity(Intent(this, com.worldmates.messenger.ui.settings.SettingsActivity::class.java))
                    },
                    onThemesClick = {
                        startActivity(Intent(this, com.worldmates.messenger.ui.settings.SettingsActivity::class.java).apply {
                            putExtra("open_screen", "theme")
                        })
                    }
                )
            }
        }

        // Load profile data
        viewModel.loadUserProfile(userId)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    viewModel: UserProfileViewModel,
    userId: Long?,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    onThemesClick: () -> Unit = {}
) {
    val profileState by viewModel.profileState.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val ratingState by viewModel.ratingState.collectAsState()
    val avatarUploadState by viewModel.avatarUploadState.collectAsState()

    var showEditDialog by remember { mutableStateOf(false) }
    val isOwnProfile = userId == null
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Повідомлення про завантаження аватара
    val avatarUpdatedStr = stringResource(R.string.avatar_updated)
    LaunchedEffect(avatarUploadState) {
        when (avatarUploadState) {
            is AvatarUploadState.Success -> {
                snackbarHostState.showSnackbar(avatarUpdatedStr)
                viewModel.resetAvatarUploadState()
            }
            is AvatarUploadState.Error -> {
                snackbarHostState.showSnackbar(
                    (avatarUploadState as AvatarUploadState.Error).message
                )
                viewModel.resetAvatarUploadState()
            }
            else -> {}
        }
    }

    if (isOwnProfile) {
        // Власний профіль - новий дизайн
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (val state = profileState) {
                    is ProfileState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    is ProfileState.Error -> {
                        ProfileErrorState(
                            message = state.message,
                            onRetry = { viewModel.loadUserProfile(userId) }
                        )
                    }
                    is ProfileState.Success -> {
                        MyProfileScreen(
                            user = state.user,
                            onEditClick = { showEditDialog = true },
                            onSettingsClick = onSettingsClick,
                            onThemesClick = onThemesClick,
                            onAvatarSelected = { uri ->
                                viewModel.uploadAvatar(uri, context)
                            }
                        )
                    }
                }

                // Індикатор завантаження аватара
                if (avatarUploadState is AvatarUploadState.Loading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(stringResource(R.string.uploading_avatar))
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Чужий профіль - старий дизайн з TopAppBar
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.profile)) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (val state = profileState) {
                    is ProfileState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    is ProfileState.Error -> {
                        ProfileErrorState(
                            message = state.message,
                            onRetry = { viewModel.loadUserProfile(userId) }
                        )
                    }
                    is ProfileState.Success -> {
                        UserProfileContent(
                            user = state.user,
                            isOwnProfile = false,
                            ratingState = ratingState,
                            onRateUser = { ratingType, comment ->
                                viewModel.rateUser(state.user.userId, ratingType, comment)
                            }
                        )
                    }
                }
            }
        }
    }

    // Edit Profile Dialog
    if (showEditDialog && profileState is ProfileState.Success) {
        EditProfileDialog(
            user = (profileState as ProfileState.Success).user,
            updateState = updateState,
            onDismiss = {
                showEditDialog = false
                viewModel.resetUpdateState()
            },
            onSave = { firstName, lastName, about, birthday, gender, phoneNumber, website, working, address, city, school ->
                viewModel.updateProfile(
                    firstName = firstName,
                    lastName = lastName,
                    about = about,
                    birthday = birthday,
                    gender = gender,
                    phoneNumber = phoneNumber,
                    website = website,
                    working = working,
                    address = address,
                    city = city,
                    school = school
                )
            }
        )

        // Auto-dismiss on success
        LaunchedEffect(updateState) {
            if (updateState is UpdateState.Success) {
                showEditDialog = false
                viewModel.resetUpdateState()
            }
        }
    }
}

@Composable
private fun ProfileErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text(stringResource(R.string.retry))
        }
    }
}

/** Parse a hex color string like "#RRGGBB" safely, returns null on failure. */
private fun parseHexColor(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    return try {
        Color(android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex"))
    } catch (e: Exception) {
        null
    }
}

/** Returns white or dark color for readable text on given background. */
private fun contentColorFor(bgColor: Color): Color {
    val luminance = ColorUtils.calculateLuminance(bgColor.toArgb())
    return if (luminance > 0.4) Color(0xFF1A1A2E) else Color.White
}

@Composable
fun UserProfileContent(
    user: User,
    isOwnProfile: Boolean,
    ratingState: RatingState,
    onRateUser: (String, String?) -> Unit
) {
    val accentColor = parseHexColor(user.profileAccent) ?: Color(0xFF667EEA)
    val headerStyle = user.profileHeaderStyle ?: "gradient"

    LazyColumn(modifier = Modifier.fillMaxSize()) {

        // ── Hero header ─────────────────────────────────────────────────────
        item {
            ProfileHeroHeader(
                user        = user,
                accentColor = accentColor,
                headerStyle = headerStyle,
            )
        }

        // ── Name + bio block ────────────────────────────────────────────────
        item {
            ProfileIdentityBlock(user = user, accentColor = accentColor)
        }

        // ── Stats row ───────────────────────────────────────────────────────
        item {
            ProfileStatsRow(user = user)
        }

        // ── Rating/Karma ─────────────────────────────────────────────────
        if (!isOwnProfile) {
            item {
                Spacer(Modifier.height(4.dp))
                when (ratingState) {
                    is RatingState.Loading -> Box(
                        Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(Modifier.size(26.dp)) }
                    is RatingState.Success -> UserRatingCard(
                        rating         = ratingState.rating,
                        accentColor    = accentColor,
                        onLikeClick    = { onRateUser("like", null) },
                        onDislikeClick = { onRateUser("dislike", null) }
                    )
                    is RatingState.Error -> Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            text     = ratingState.message,
                            color    = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        // ── Info section ─────────────────────────────────────────────────
        item {
            ProfileInfoSection(user = user, accentColor = accentColor)
            Spacer(Modifier.height(40.dp))
        }
    }
}

// ─── Hero Header ──────────────────────────────────────────────────────────────

@Composable
private fun ProfileHeroHeader(user: User, accentColor: Color, headerStyle: String) {
    val darkerAccent = Color(
        android.graphics.Color.HSVToColor(
            FloatArray(3).also { hsv ->
                android.graphics.Color.colorToHSV(accentColor.toArgb(), hsv)
                hsv[2] = (hsv[2] * 0.65f).coerceIn(0f, 1f)
            }
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
    ) {
        // Background: cover image or styled gradient/pattern
        if (!user.cover.isNullOrBlank() && headerStyle != "minimal") {
            AsyncImage(
                model              = user.cover,
                contentDescription = null,
                modifier           = Modifier.fillMaxSize(),
                contentScale       = ContentScale.Crop
            )
            // Tinted overlay using accent color
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(accentColor.copy(alpha = 0.35f))
            )
        } else {
            // Accent-based background
            val bgBrush = when (headerStyle) {
                "pattern" -> Brush.sweepGradient(
                    listOf(accentColor, darkerAccent, accentColor.copy(alpha = 0.7f), darkerAccent, accentColor)
                )
                "minimal" -> Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.background)
                )
                else -> Brush.linearGradient(
                    listOf(accentColor, darkerAccent)
                )
            }
            Box(modifier = Modifier.fillMaxSize().background(bgBrush))
        }

        // Bottom gradient scrim for readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.65f to Color.Transparent,
                        1f to MaterialTheme.colorScheme.background
                    )
                )
        )

        // Avatar — bottom-left, overlapping the header bottom edge
        UserAvatarPagerInProfile(
            userId      = user.userId,
            fallbackUrl = user.avatar,
            modifier    = Modifier
                .size(96.dp)
                .align(Alignment.BottomStart)
                .offset(x = 18.dp, y = 48.dp)
                .shadow(8.dp, CircleShape)
                .border(3.dp, MaterialTheme.colorScheme.background, CircleShape)
        )

        // Online dot on avatar
        val isOnline = user.lastSeenStatus == "online"
        if (isOnline) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = 94.dp, y = 38.dp)
                    .size(16.dp)
                    .border(2.5.dp, MaterialTheme.colorScheme.background, CircleShape)
                    .background(Color(0xFF4CAF50), CircleShape)
            )
        }
    }

    // Spacer to compensate the avatar overlap
    Spacer(Modifier.height(52.dp))
}

// ─── Identity block (name + badge + username + bio + action buttons) ──────────

@Composable
private fun ProfileIdentityBlock(user: User, accentColor: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Name row
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text       = "${user.firstName ?: ""} ${user.lastName ?: ""}".trim().ifBlank { user.username },
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier   = Modifier.weight(1f, fill = false)
            )
            if (!user.profileBadge.isNullOrBlank()) {
                Spacer(Modifier.width(6.dp))
                Text(text = user.profileBadge, fontSize = 20.sp)
            }
            if (user.verified == 1) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint     = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(Modifier.height(3.dp))

        // Username + online status chip
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text  = "@${user.username}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (user.lastSeenStatus == "online") {
                Spacer(Modifier.width(8.dp))
                Surface(
                    color = Color(0xFF4CAF50).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text     = stringResource(R.string.online),
                        style    = MaterialTheme.typography.labelSmall,
                        color    = Color(0xFF4CAF50),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        // Bio
        if (!user.about.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text  = user.about,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.height(14.dp))
    }
}

// ─── Stats row ────────────────────────────────────────────────────────────────

@Composable
private fun ProfileStatsRow(user: User) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape     = RoundedCornerShape(18.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ProfileStatChip(
                count = user.followersCount ?: "0",
                label = stringResource(R.string.followers)
            )
            VerticalDivider()
            ProfileStatChip(
                count = user.followingCount ?: "0",
                label = stringResource(R.string.following_count)
            )
            VerticalDivider()
            ProfileStatChip(
                count = user.details?.postCount?.toString() ?: "0",
                label = stringResource(R.string.posts)
            )
        }
    }
}

@Composable
private fun VerticalDivider() {
    Box(
        modifier = Modifier
            .height(36.dp)
            .width(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    )
}

@Composable
private fun ProfileStatChip(count: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text       = count,
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─── Info section ─────────────────────────────────────────────────────────────

@Composable
private fun ProfileInfoSection(user: User, accentColor: Color) {
    val genderMaleStr   = stringResource(R.string.gender_male)
    val genderFemaleStr = stringResource(R.string.gender_female)

    val hasAnyInfo = listOf(user.working, user.school, user.city, user.website, user.birthday, user.gender)
        .any { !it.isNullOrBlank() }

    if (!hasAnyInfo) return

    Spacer(Modifier.height(8.dp))

    Card(
        modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape     = RoundedCornerShape(18.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text       = stringResource(R.string.profile_info),
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.onSurface,
                modifier   = Modifier.padding(bottom = 10.dp)
            )
            if (!user.working.isNullOrBlank()) {
                InfoItemCompact(Icons.Default.Work,       stringResource(R.string.work_label),      user.working,   accentColor)
            }
            if (!user.school.isNullOrBlank()) {
                InfoItemCompact(Icons.Default.School,     stringResource(R.string.education_label), user.school,    accentColor)
            }
            if (!user.city.isNullOrBlank()) {
                InfoItemCompact(Icons.Default.LocationOn, stringResource(R.string.city),            user.city,      accentColor)
            }
            if (!user.website.isNullOrBlank()) {
                InfoItemCompact(Icons.Default.Language,   stringResource(R.string.website_label),   user.website,   accentColor)
            }
            if (!user.birthday.isNullOrBlank()) {
                InfoItemCompact(Icons.Default.Cake,       stringResource(R.string.birthday_label),  user.birthday,  accentColor)
            }
            if (!user.gender.isNullOrBlank()) {
                val genderStr = when (user.gender) { "male" -> genderMaleStr; "female" -> genderFemaleStr; else -> user.gender }
                InfoItemCompact(Icons.Default.Person,     stringResource(R.string.gender),          genderStr,      accentColor)
            }
        }
    }
}

@Composable
private fun InfoItemCompact(
    icon:       androidx.compose.ui.graphics.vector.ImageVector,
    label:      String,
    value:      String,
    accentColor: Color
) {
    Row(
        modifier          = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier         = Modifier
                .size(34.dp)
                .background(accentColor.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.bodySmall,  color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun StatItem(label: String, count: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = count, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun InfoItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun EditProfileDialog(
    user: User,
    updateState: UpdateState,
    onDismiss: () -> Unit,
    onSave: (String?, String?, String?, String?, String?, String?, String?, String?, String?, String?, String?) -> Unit
) {
    var firstName by remember { mutableStateOf(user.firstName ?: "") }
    var lastName by remember { mutableStateOf(user.lastName ?: "") }
    var about by remember { mutableStateOf(user.about ?: "") }
    var birthday by remember { mutableStateOf(user.birthday ?: "") }
    var gender by remember { mutableStateOf(user.gender ?: "male") }
    var phoneNumber by remember { mutableStateOf(user.phoneNumber ?: "") }
    var website by remember { mutableStateOf(user.website ?: "") }
    var working by remember { mutableStateOf(user.working ?: "") }
    var address by remember { mutableStateOf(user.address ?: "") }
    var city by remember { mutableStateOf(user.city ?: "") }
    var school by remember { mutableStateOf(user.school ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_profile)) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = firstName,
                        onValueChange = { firstName = it },
                        label = { Text(stringResource(R.string.first_name)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }

                item {
                    OutlinedTextField(
                        value = lastName,
                        onValueChange = { lastName = it },
                        label = { Text(stringResource(R.string.last_name)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }

                item {
                    OutlinedTextField(
                        value = about,
                        onValueChange = { about = it },
                        label = { Text(stringResource(R.string.about)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        maxLines = 3
                    )
                }

                item {
                    Text(
                        text = stringResource(R.string.gender),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = gender == "male",
                            onClick = { gender = "male" },
                            label = { Text(stringResource(R.string.gender_male)) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = gender == "female",
                            onClick = { gender = "female" },
                            label = { Text(stringResource(R.string.gender_female)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = working,
                        onValueChange = { working = it },
                        label = { Text(stringResource(R.string.work_label)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }

                item {
                    OutlinedTextField(
                        value = school,
                        onValueChange = { school = it },
                        label = { Text(stringResource(R.string.education_label)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }

                item {
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text(stringResource(R.string.city)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }

                item {
                    OutlinedTextField(
                        value = website,
                        onValueChange = { website = it },
                        label = { Text(stringResource(R.string.website_label)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }

                if (updateState is UpdateState.Error) {
                    item {
                        Text(
                            text = updateState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        firstName.ifBlank { null },
                        lastName.ifBlank { null },
                        about.ifBlank { null },
                        birthday.ifBlank { null },
                        gender,
                        phoneNumber.ifBlank { null },
                        website.ifBlank { null },
                        working.ifBlank { null },
                        address.ifBlank { null },
                        city.ifBlank { null },
                        school.ifBlank { null }
                    )
                },
                enabled = updateState !is UpdateState.Loading
            ) {
                if (updateState is UpdateState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(stringResource(R.string.save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * Modern karma card with progress bar and compact vote buttons.
 */
@Composable
fun UserRatingCard(
    rating:        com.worldmates.messenger.data.model.UserRating,
    accentColor:   Color = Color(0xFF667EEA),
    onLikeClick:   () -> Unit,
    onDislikeClick: () -> Unit
) {
    val trustColor = when (rating.trustLevel) {
        "verified"  -> Color(0xFF4CAF50)
        "trusted"   -> Color(0xFF2196F3)
        "untrusted" -> Color(0xFFF44336)
        else        -> Color(0xFF9E9E9E)
    }
    val likeActive    = rating.myRating?.type == "like"
    val dislikeActive = rating.myRating?.type == "dislike"

    val verifiedStr   = stringResource(R.string.status_verified)
    val trustedStr    = stringResource(R.string.status_trusted)
    val neutralStr    = stringResource(R.string.status_neutral)
    val untrustedStr  = stringResource(R.string.status_untrusted)
    val trustLabel    = when (rating.trustLevel) {
        "verified"  -> verifiedStr
        "trusted"   -> trustedStr
        "untrusted" -> untrustedStr
        else        -> neutralStr
    }

    Card(
        modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {

            // ── Header: title + trust badge ─────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text       = stringResource(R.string.user_karma),
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(text = rating.trustLevelEmoji, fontSize = 16.sp)
                }
                Surface(color = trustColor.copy(alpha = 0.15f), shape = RoundedCornerShape(20.dp)) {
                    Text(
                        text     = trustLabel,
                        style    = MaterialTheme.typography.labelSmall,
                        color    = trustColor,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Score + counts in one line ──────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // Score circle
                Box(
                    modifier         = Modifier
                        .size(56.dp)
                        .background(accentColor.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text       = String.format("%.1f", rating.score),
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color      = accentColor
                        )
                        Text(
                            text  = stringResource(R.string.rating_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor.copy(alpha = 0.7f),
                            fontSize = 9.sp
                        )
                    }
                }

                // Progress bar + counts
                Column(modifier = Modifier.weight(1f)) {
                    // Like/dislike bar
                    val likeRatio = if (rating.totalRatings > 0)
                        rating.likes.toFloat() / rating.totalRatings else 0f
                    val animatedRatio by animateFloatAsState(
                        targetValue   = likeRatio,
                        animationSpec = tween(600),
                        label         = "like_ratio"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFF44336).copy(alpha = 0.25f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animatedRatio)
                                .background(
                                    Brush.horizontalGradient(listOf(Color(0xFF4CAF50), Color(0xFF8BC34A))),
                                    RoundedCornerShape(4.dp)
                                )
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ThumbUp, null, Modifier.size(14.dp), tint = Color(0xFF4CAF50))
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text  = rating.likes.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text  = stringResource(R.string.rating_total_votes, rating.totalRatings),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text  = rating.dislikes.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFFF44336),
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.width(3.dp))
                            Icon(Icons.Default.ThumbDown, null, Modifier.size(14.dp), tint = Color(0xFFF44336))
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            Spacer(Modifier.height(12.dp))

            // ── Rate buttons ────────────────────────────────────────────
            Text(
                text     = stringResource(R.string.rate_user),
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RatingVoteButton(
                    modifier  = Modifier.weight(1f),
                    isActive  = likeActive,
                    activeColor = Color(0xFF4CAF50),
                    icon      = Icons.Default.ThumbUp,
                    label     = if (likeActive) stringResource(R.string.liked) else stringResource(R.string.like),
                    onClick   = onLikeClick
                )
                RatingVoteButton(
                    modifier  = Modifier.weight(1f),
                    isActive  = dislikeActive,
                    activeColor = Color(0xFFF44336),
                    icon      = Icons.Default.ThumbDown,
                    label     = if (dislikeActive) stringResource(R.string.disliked) else stringResource(R.string.dislike),
                    onClick   = onDislikeClick
                )
            }

            // Already-rated hint
            if (rating.myRating != null) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text  = stringResource(R.string.already_rated),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun RatingVoteButton(
    modifier:    Modifier,
    isActive:    Boolean,
    activeColor: Color,
    icon:        androidx.compose.ui.graphics.vector.ImageVector,
    label:       String,
    onClick:     () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue   = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label         = "vote_btn_scale"
    )
    val containerColor by animateColorAsState(
        targetValue   = if (isActive) activeColor else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(200),
        label         = "vote_btn_color"
    )
    val contentColor = if (isActive) Color.White else activeColor

    Surface(
        modifier          = modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick           = onClick
            ),
        shape             = RoundedCornerShape(14.dp),
        color             = containerColor,
        border            = if (!isActive) androidx.compose.foundation.BorderStroke(
            1.dp, activeColor.copy(alpha = 0.35f)
        ) else null
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = contentColor)
        }
    }
}

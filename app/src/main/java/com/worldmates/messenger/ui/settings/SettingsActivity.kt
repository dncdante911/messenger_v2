package com.worldmates.messenger.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.R
import androidx.lifecycle.ViewModelProvider
import coil.compose.AsyncImage
import com.worldmates.messenger.BuildConfig
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.ui.login.LoginActivity
import com.worldmates.messenger.ui.settings.security.AppLockSettingsScreen
import com.worldmates.messenger.ui.settings.security.DeleteAccountScreen
import com.worldmates.messenger.ui.settings.security.SessionsScreen
import com.worldmates.messenger.ui.settings.security.TwoFactorAuthScreen
import com.worldmates.messenger.ui.theme.ThemeManager
import com.worldmates.messenger.ui.theme.ThemeSettingsScreen
import com.worldmates.messenger.ui.theme.WorldMatesThemedApp
import com.worldmates.messenger.update.AppUpdateManager
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worldmates.messenger.ui.language.LanguageSelectionScreen
import com.worldmates.messenger.utils.LanguageManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var viewModel: SettingsViewModel

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        ThemeManager.initialize(this)

        viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        ).get(SettingsViewModel::class.java)

        viewModel.fetchUserData()

        val openScreen = intent.getStringExtra("open_screen")

        setContent {
            var currentScreen by remember {
                mutableStateOf<SettingsScreen>(
                    when (openScreen) {
                        "theme" -> SettingsScreen.Theme
                        else -> SettingsScreen.Main
                    }
                )
            }

            WorldMatesThemedApp {
                when (currentScreen) {
                    SettingsScreen.Main -> {
                        SettingsMainScreen(
                            viewModel = viewModel,
                            onBackPressed = { finish() },
                            onNavigate = { screen -> currentScreen = screen },
                            onLogout = {
                                com.worldmates.messenger.services.NotificationKeepAliveManager.shutdown(this)
                                UserSession.clearSession()
                                startActivity(Intent(this, LoginActivity::class.java))
                                finishAffinity()
                            }
                        )
                    }
                    SettingsScreen.EditProfile -> {
                        EditProfileScreen(
                            viewModel = viewModel,
                            onBackClick = { currentScreen = SettingsScreen.Main }
                        )
                    }
                    SettingsScreen.Privacy -> {
                        PrivacySettingsScreen(
                            viewModel = viewModel,
                            onBackClick = { currentScreen = SettingsScreen.Main }
                        )
                    }
                    SettingsScreen.Notifications -> {
                        NotificationSettingsScreen(
                            viewModel = viewModel,
                            onBackClick = { currentScreen = SettingsScreen.Main }
                        )
                    }
                    SettingsScreen.Theme -> {
                        ThemeSettingsScreen(
                            onBackClick = { currentScreen = SettingsScreen.Main },
                            onNavigateToCallFrame = { currentScreen = SettingsScreen.CallFrameStyle },
                            onNavigateToVideoFrame = { currentScreen = SettingsScreen.VideoMessageFrameStyle }
                        )
                    }
                    SettingsScreen.CallFrameStyle -> {
                        CallFrameSettingsScreen(
                            onBackClick = { currentScreen = SettingsScreen.Theme }
                        )
                    }
                    SettingsScreen.VideoMessageFrameStyle -> {
                        VideoMessageFrameSettingsScreen(
                            onBackClick = { currentScreen = SettingsScreen.Theme }
                        )
                    }
                    SettingsScreen.MyGroups -> {
                        MyGroupsScreen(
                            viewModel = viewModel,
                            onBackClick = { currentScreen = SettingsScreen.Main }
                        )
                    }
                    SettingsScreen.TwoFactorAuth -> {
                        TwoFactorAuthScreen(
                            onBackClick = { currentScreen = SettingsScreen.Main }
                        )
                    }
                    SettingsScreen.ActiveSessions -> {
                        SessionsScreen(
                            onBackClick = { currentScreen = SettingsScreen.Main }
                        )
                    }
                    SettingsScreen.DeleteAccount -> {
                        DeleteAccountScreen(
                            onBackClick = { currentScreen = SettingsScreen.Main },
                            onAccountDeleted = {
                                com.worldmates.messenger.services.NotificationKeepAliveManager.shutdown(this@SettingsActivity)
                                UserSession.clearSession()
                                startActivity(Intent(this@SettingsActivity, LoginActivity::class.java))
                                finishAffinity()
                            }
                        )
                    }
                    SettingsScreen.AppLock -> {
                        AppLockSettingsScreen(
                            activity = this@SettingsActivity,
                            onBackClick = { currentScreen = SettingsScreen.Main }
                        )
                    }
                    SettingsScreen.CloudBackup -> {
                        CloudBackupSettingsScreen(
                            onBackClick = { currentScreen = SettingsScreen.Main }
                        )
                    }
                    SettingsScreen.BlockedUsers -> {
                        BlockedUsersScreen(
                            onBackClick = { currentScreen = SettingsScreen.Main }
                        )
                    }
                    SettingsScreen.Language -> {
                        LanguageSelectionScreen(
                            onLanguageSelected = { lang ->
                                LanguageManager.setLanguage(lang)
                                val restartIntent = packageManager.getLaunchIntentForPackage(packageName)
                                    ?: Intent(this@SettingsActivity, LoginActivity::class.java)
                                restartIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                                startActivity(restartIntent)
                            },
                            currentLanguage = LanguageManager.currentLanguage,
                            onBackClick = { currentScreen = SettingsScreen.Main }
                        )
                    }
                    SettingsScreen.CustomStatus -> {
                        CustomStatusScreen(
                            onBackClick = { currentScreen = SettingsScreen.Main }
                        )
                    }
                    SettingsScreen.Premium -> {
                        startActivity(
                            Intent(this@SettingsActivity,
                                com.worldmates.messenger.ui.premium.PremiumActivity::class.java)
                        )
                        currentScreen = SettingsScreen.Main
                    }
                    SettingsScreen.Business -> {
                        startActivity(
                            Intent(this@SettingsActivity,
                                com.worldmates.messenger.ui.business.BusinessActivity::class.java)
                        )
                        currentScreen = SettingsScreen.Main
                    }
                }
            }
        }
    }
}

sealed class SettingsScreen {
    object Main : SettingsScreen()
    object EditProfile : SettingsScreen()
    object Privacy : SettingsScreen()
    object Notifications : SettingsScreen()
    object Theme : SettingsScreen()
    object CallFrameStyle : SettingsScreen()
    object VideoMessageFrameStyle : SettingsScreen()
    object MyGroups : SettingsScreen()
    object TwoFactorAuth : SettingsScreen()
    object AppLock : SettingsScreen()
    object CloudBackup : SettingsScreen()
    object BlockedUsers : SettingsScreen()
    object Language : SettingsScreen()
    object Premium : SettingsScreen()
    object CustomStatus : SettingsScreen()
    object ActiveSessions : SettingsScreen()
    object DeleteAccount : SettingsScreen()
    object Business : SettingsScreen()
}

// ─────────────────────────────────────────────────────────────────────────────
// Main Settings Screen — clean, grouped Material3 design
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsMainScreen(
    viewModel: SettingsViewModel,
    onBackPressed: () -> Unit,
    onNavigate: (SettingsScreen) -> Unit,
    onLogout: () -> Unit
) {
    val username = UserSession.username ?: stringResource(R.string.unknown)
    val avatar = UserSession.avatar
    val userData by viewModel.userData.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    val updateState by AppUpdateManager.state.collectAsState()
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme

    LaunchedEffect(successMessage) {
        if (successMessage != null) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearSuccess()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.checkUpdates(force = false)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_header),
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.surface,
                    titleContentColor = colorScheme.onSurface,
                    navigationIconContentColor = colorScheme.onSurface
                )
            )
        },
        containerColor = colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Success message
            if (successMessage != null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF4CAF50).copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = successMessage ?: "",
                                color = Color(0xFF2E7D32),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // ── Profile Card ──
            item {
                ProfileCard(
                    username = username,
                    userId = UserSession.userId,
                    avatar = avatar,
                    email = userData?.email,
                    about = userData?.about,
                    onEditProfile = { onNavigate(SettingsScreen.EditProfile) }
                )
            }

            // ── PRO Banner ──
            item {
                ProBanner(
                    isPro = userData?.isPro == 1,
                    onClick = { onNavigate(SettingsScreen.Premium) }
                )
            }

            // ── Account ──
            item {
                SettingsGroupHeader(title = stringResource(R.string.account_section))
                SettingsGroup {
                    SettingsRow(
                        icon = Icons.Outlined.Person,
                        title = stringResource(R.string.edit_profile),
                        subtitle = stringResource(R.string.edit_profile_subtitle),
                        onClick = { onNavigate(SettingsScreen.EditProfile) }
                    )
                    GroupDivider()
                    val statusEmoji by UserSession.statusEmojiFlow.collectAsState()
                    val statusText by UserSession.statusTextFlow.collectAsState()
                    val statusSubtitle = when {
                        !statusEmoji.isNullOrBlank() && !statusText.isNullOrBlank() -> "$statusEmoji $statusText"
                        !statusEmoji.isNullOrBlank() -> statusEmoji!!
                        !statusText.isNullOrBlank() -> statusText!!
                        else -> stringResource(R.string.custom_status_subtitle)
                    }
                    SettingsRow(
                        icon = Icons.Outlined.EmojiEmotions,
                        title = if (UserSession.isProActive) stringResource(R.string.custom_status_title)
                                else "${stringResource(R.string.custom_status_title)} ⭐",
                        subtitle = statusSubtitle,
                        onClick = { onNavigate(SettingsScreen.CustomStatus) }
                    )
                }
            }

            // ── Notifications & Privacy ──
            item {
                SettingsGroupHeader(title = stringResource(R.string.notification_settings) + " & " + stringResource(R.string.privacy_settings))
                SettingsGroup {
                    SettingsRow(
                        icon = Icons.Outlined.Notifications,
                        title = stringResource(R.string.notification_settings),
                        subtitle = stringResource(R.string.notification_subtitle),
                        onClick = { onNavigate(SettingsScreen.Notifications) }
                    )
                    GroupDivider()
                    SettingsRow(
                        icon = Icons.Outlined.Lock,
                        title = stringResource(R.string.privacy_settings),
                        subtitle = stringResource(R.string.privacy_subtitle),
                        onClick = { onNavigate(SettingsScreen.Privacy) }
                    )
                    GroupDivider()
                    SettingsRow(
                        icon = Icons.Outlined.Block,
                        title = stringResource(R.string.blocked_users),
                        subtitle = stringResource(R.string.blocked_users_subtitle),
                        onClick = { onNavigate(SettingsScreen.BlockedUsers) }
                    )
                }
            }

            // ── Security ──
            item {
                SettingsGroupHeader(title = stringResource(R.string.security_section))
                SettingsGroup {
                    val twoFactorEnabled = stringResource(R.string.two_factor_enabled)
                    val twoFactorDisabled = stringResource(R.string.two_factor_disabled)
                    val twoFactorSubtitle = try {
                        if (com.worldmates.messenger.utils.security.SecurePreferences.is2FAEnabled) twoFactorEnabled else twoFactorDisabled
                    } catch (_: Exception) { twoFactorDisabled }
                    SettingsRow(
                        icon = Icons.Outlined.Shield,
                        title = stringResource(R.string.two_factor_auth),
                        subtitle = twoFactorSubtitle,
                        onClick = { onNavigate(SettingsScreen.TwoFactorAuth) }
                    )
                    GroupDivider()
                    val pinActive = stringResource(R.string.app_lock_pin_active)
                    val pinDisabled = stringResource(R.string.two_factor_disabled)
                    val appLockSubtitle = try {
                        if (com.worldmates.messenger.utils.security.SecurePreferences.isPINEnabled()) pinActive else pinDisabled
                    } catch (_: Exception) { pinDisabled }
                    SettingsRow(
                        icon = Icons.Outlined.Fingerprint,
                        title = stringResource(R.string.app_lock),
                        subtitle = appLockSubtitle,
                        onClick = { onNavigate(SettingsScreen.AppLock) }
                    )
                    GroupDivider()
                    SettingsRow(
                        icon = Icons.Outlined.Devices,
                        title = stringResource(R.string.active_sessions),
                        subtitle = stringResource(R.string.active_sessions_subtitle),
                        onClick = { onNavigate(SettingsScreen.ActiveSessions) }
                    )
                }
            }

            // ── Social ──
            item {
                SettingsGroupHeader(title = stringResource(R.string.social_section))
                SettingsGroup {
                    SettingsRow(
                        icon = Icons.Outlined.Group,
                        title = stringResource(R.string.my_groups_settings),
                        subtitle = stringResource(R.string.groups_count_fmt, userData?.groupsCount ?: "0"),
                        onClick = { onNavigate(SettingsScreen.MyGroups) }
                    )
                    GroupDivider()
                    SettingsRow(
                        icon = Icons.Outlined.People,
                        title = stringResource(R.string.followers_settings),
                        subtitle = stringResource(R.string.followers_count_fmt, userData?.followersCount ?: "0"),
                        onClick = { /* TODO */ }
                    )
                    GroupDivider()
                    SettingsRow(
                        icon = Icons.Outlined.PersonAdd,
                        title = stringResource(R.string.following_settings),
                        subtitle = stringResource(R.string.following_count_fmt, userData?.followingCount ?: "0"),
                        onClick = { /* TODO */ }
                    )
                }
            }

            // ── Storage & Data ──
            item {
                SettingsGroupHeader(title = stringResource(R.string.storage_backup))
                SettingsGroup {
                    SettingsRow(
                        icon = Icons.Outlined.CloudUpload,
                        title = stringResource(R.string.storage_backup),
                        subtitle = stringResource(R.string.storage_backup_subtitle),
                        onClick = { onNavigate(SettingsScreen.CloudBackup) }
                    )
                }
            }

            // ── Appearance ──
            item {
                SettingsGroupHeader(title = stringResource(R.string.app_section))
                SettingsGroup {
                    SettingsRow(
                        icon = Icons.Outlined.Palette,
                        title = stringResource(R.string.theme_settings),
                        subtitle = stringResource(R.string.theme_subtitle),
                        onClick = { onNavigate(SettingsScreen.Theme) }
                    )
                    GroupDivider()
                    SettingsRow(
                        icon = Icons.Outlined.Language,
                        title = stringResource(R.string.language_settings),
                        subtitle = LanguageManager.getDisplayName(LanguageManager.currentLanguage),
                        onClick = { onNavigate(SettingsScreen.Language) }
                    )
                    GroupDivider()
                    SettingsRow(
                        icon = Icons.Outlined.SystemUpdate,
                        title = stringResource(R.string.app_update),
                        subtitle = if (updateState.hasUpdate) stringResource(R.string.update_available_version, updateState.latestVersion ?: "") else stringResource(R.string.update_auto_check),
                        showBadge = updateState.hasUpdate,
                        onClick = { viewModel.checkUpdates(force = true) }
                    )
                    GroupDivider()
                    SettingsRow(
                        icon = Icons.Outlined.Info,
                        title = stringResource(R.string.about_app),
                        subtitle = "v${BuildConfig.VERSION_NAME}",
                        onClick = { showAboutDialog = true }
                    )
                }
            }

            // ── Business ──
            item {
                SettingsGroupHeader(title = stringResource(R.string.biz_section_title))
                SettingsGroup {
                    SettingsRow(
                        icon = Icons.Outlined.Business,
                        title = stringResource(R.string.biz_mode_title),
                        subtitle = stringResource(R.string.biz_mode_subtitle),
                        onClick = { onNavigate(SettingsScreen.Business) }
                    )
                }
            }

            // ── Danger zone ──
            item {
                SettingsGroup {
                    SettingsRow(
                        icon = Icons.Outlined.DeleteForever,
                        title = stringResource(R.string.delete_account),
                        tintColor = MaterialTheme.colorScheme.error,
                        onClick = { onNavigate(SettingsScreen.DeleteAccount) }
                    )
                    GroupDivider()
                    SettingsRow(
                        icon = Icons.Outlined.ExitToApp,
                        title = stringResource(R.string.logout),
                        tintColor = MaterialTheme.colorScheme.error,
                        onClick = { showLogoutDialog = true }
                    )
                }
            }

            // Bottom spacing
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    // ── Dialogs ──
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(stringResource(R.string.logout_confirm_title)) },
            text = { Text(stringResource(R.string.logout_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    }
                ) {
                    Text(stringResource(R.string.logout), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (updateState.hasUpdate && updateState.apkUrl != null) {
        AlertDialog(
            onDismissRequest = {
                if (!updateState.isMandatory) {
                    viewModel.snoozeUpdatePrompt()
                }
            },
            title = { Text(stringResource(R.string.update_available_title, updateState.latestVersion ?: "")) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(stringResource(R.string.update_available_text))
                    if (updateState.changelog.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.whats_new),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        updateState.changelog.forEach { change ->
                            Text(
                                text = "• $change",
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { AppUpdateManager.openUpdateUrl(context) }) {
                    Text(stringResource(R.string.update_now))
                }
            },
            dismissButton = {
                if (!updateState.isMandatory) {
                    TextButton(onClick = { viewModel.snoozeUpdatePrompt() }) {
                        Text(stringResource(R.string.later))
                    }
                }
            }
        )
    }

    if (showAboutDialog) {
        com.worldmates.messenger.ui.components.AboutAppDialog(
            onDismiss = { showAboutDialog = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ProfileCard(
    username: String,
    userId: Long,
    avatar: String?,
    email: String?,
    about: String?,
    onEditProfile: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val statusEmoji by UserSession.statusEmojiFlow.collectAsState()
    val statusText by UserSession.statusTextFlow.collectAsState()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEditProfile),
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = avatar ?: "https://worldmates.club/upload/photos/d-avatar.jpg",
                contentDescription = "Avatar",
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = username,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!statusEmoji.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = statusEmoji!!, fontSize = 16.sp)
                    }
                }
                if (!statusText.isNullOrBlank()) {
                    Text(
                        text = statusText!!,
                        fontSize = 13.sp,
                        color = colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Text(
                    text = "ID: $userId",
                    fontSize = 13.sp,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
                if (!email.isNullOrEmpty()) {
                    Text(
                        text = email,
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun ProBanner(
    isPro: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    if (isPro) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFFFFD700).copy(alpha = 0.15f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Stars,
                    contentDescription = null,
                    tint = Color(0xFFDAA520),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.pro_account),
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFDAA520),
                        fontSize = 15.sp
                    )
                    Text(
                        stringResource(R.string.thank_you_support),
                        color = Color(0xFFDAA520).copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color(0xFFDAA520).copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    } else {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(14.dp),
            color = colorScheme.primaryContainer.copy(alpha = 0.4f)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Stars,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.premium_get_pro_title),
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.primary,
                        fontSize = 15.sp
                    )
                    Text(
                        stringResource(R.string.premium_get_pro_subtitle),
                        color = colorScheme.primary.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = colorScheme.primary.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun SettingsGroupHeader(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
    )
}

@Composable
fun SettingsGroup(
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

@Composable
fun GroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    tintColor: Color? = null,
    showBadge: Boolean = false,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val iconColor = tintColor ?: colorScheme.onSurfaceVariant
    val titleColor = tintColor ?: colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }
        if (showBadge) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(colorScheme.error, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        if (tintColor == null) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

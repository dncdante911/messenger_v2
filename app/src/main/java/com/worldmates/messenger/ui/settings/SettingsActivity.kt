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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.R
import androidx.lifecycle.ViewModelProvider
import coil.compose.AsyncImage
import com.worldmates.messenger.BuildConfig
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.ui.login.LoginActivity
import com.worldmates.messenger.ui.settings.security.AppLockSettingsScreen
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

        // Edge-to-edge so Compose controls insets
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        // Инициализируем ThemeManager
        ThemeManager.initialize(this)

        viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        ).get(SettingsViewModel::class.java)

        // Загрузить данные пользователя при открытии настроек
        viewModel.fetchUserData()

        // Перевіряємо чи потрібно відкрити конкретний екран
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
                        SettingsScreen(
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
                                // Перезапустити весь стек Activity щоб нова мова застосувалась скрізь
                                val restartIntent = packageManager.getLaunchIntentForPackage(packageName)
                                    ?: Intent(this@SettingsActivity, LoginActivity::class.java)
                                restartIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                                startActivity(restartIntent)
                            },
                            currentLanguage = LanguageManager.currentLanguage,
                            onBackClick = { currentScreen = SettingsScreen.Main }
                        )
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
    object VideoMessageFrameStyle : SettingsScreen()  // 📹 Стиль відеоповідомлень
    object MyGroups : SettingsScreen()
    object TwoFactorAuth : SettingsScreen()
    object AppLock : SettingsScreen()
    object CloudBackup : SettingsScreen()
    object BlockedUsers : SettingsScreen()
    object Language : SettingsScreen()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
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

    // Показать сообщение об успехе
    LaunchedEffect(successMessage) {
        if (successMessage != null) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearSuccess()
        }
    }

    // Анімація появи екрану
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
        viewModel.checkUpdates(force = false)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF667eea),
                        Color(0xFF764ba2),
                        Color(0xFFF093FB)
                    )
                )
            )
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) + fadeIn(animationSpec = tween(300))
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Красивий Header з градієнтом
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF667eea),
                                    Color(0xFF764ba2)
                                )
                            )
                        )
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBackPressed,
                            modifier = Modifier
                                .shadow(4.dp, CircleShape)
                                .background(Color.White.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = stringResource(R.string.settings_header),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            // Success Message
            if (successMessage != null) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFE8F5E9)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = successMessage ?: "",
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }
                }
            }

            // Profile Section
            item {
                ProfileSection(
                    username = username,
                    userId = UserSession.userId,
                    avatar = avatar,
                    email = userData?.email,
                    about = userData?.about,
                    onEditProfile = { onNavigate(SettingsScreen.EditProfile) }
                )
            }

            // Settings sections
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Account Settings
            item {
                SettingsSection(title = stringResource(R.string.account_section))
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Person,
                    title = stringResource(R.string.edit_profile),
                    subtitle = stringResource(R.string.edit_profile_subtitle),
                    onClick = { onNavigate(SettingsScreen.EditProfile) }
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.privacy_settings),
                    subtitle = stringResource(R.string.privacy_subtitle),
                    onClick = { onNavigate(SettingsScreen.Privacy) }
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Notifications,
                    title = stringResource(R.string.notification_settings),
                    subtitle = stringResource(R.string.notification_subtitle),
                    onClick = { onNavigate(SettingsScreen.Notifications) }
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Security Settings
            item {
                SettingsSection(title = stringResource(R.string.security_section))
            }
            item {
                val twoFactorEnabled = stringResource(R.string.two_factor_enabled)
                val twoFactorDisabled = stringResource(R.string.two_factor_disabled)
                val twoFactorSubtitle = try {
                    if (com.worldmates.messenger.utils.security.SecurePreferences.is2FAEnabled) twoFactorEnabled else twoFactorDisabled
                } catch (_: Exception) { twoFactorDisabled }
                SettingsItem(
                    icon = Icons.Default.Shield,
                    title = stringResource(R.string.two_factor_auth),
                    subtitle = twoFactorSubtitle,
                    onClick = { onNavigate(SettingsScreen.TwoFactorAuth) }
                )
            }
            item {
                val pinActive = stringResource(R.string.app_lock_pin_active)
                val pinDisabled = stringResource(R.string.two_factor_disabled)
                val appLockSubtitle = try {
                    if (com.worldmates.messenger.utils.security.SecurePreferences.isPINEnabled()) pinActive else pinDisabled
                } catch (_: Exception) { pinDisabled }
                SettingsItem(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.app_lock),
                    subtitle = appLockSubtitle,
                    onClick = { onNavigate(SettingsScreen.AppLock) }
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Block,
                    title = stringResource(R.string.blocked_users),
                    subtitle = stringResource(R.string.blocked_users_subtitle),
                    onClick = { onNavigate(SettingsScreen.BlockedUsers) }
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Social Settings
            item {
                SettingsSection(title = stringResource(R.string.social_section))
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Group,
                    title = stringResource(R.string.my_groups_settings),
                    subtitle = stringResource(R.string.groups_count_fmt, userData?.groupsCount ?: "0"),
                    onClick = { onNavigate(SettingsScreen.MyGroups) }
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.People,
                    title = stringResource(R.string.followers_settings),
                    subtitle = stringResource(R.string.followers_count_fmt, userData?.followersCount ?: "0"),
                    onClick = { /* TODO */ }
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.PersonAdd,
                    title = stringResource(R.string.following_settings),
                    subtitle = stringResource(R.string.following_count_fmt, userData?.followingCount ?: "0"),
                    onClick = { /* TODO */ }
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Chat Settings
            item {
                SettingsSection(title = stringResource(R.string.chats_section))
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Chat,
                    title = stringResource(R.string.chat_background),
                    subtitle = stringResource(R.string.chat_background_subtitle),
                    onClick = { /* TODO */ }
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Storage,
                    title = stringResource(R.string.storage_backup),
                    subtitle = stringResource(R.string.storage_backup_subtitle),
                    onClick = { onNavigate(SettingsScreen.CloudBackup) }
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            // App Settings
            item {
                SettingsSection(title = stringResource(R.string.app_section))
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Language,
                    title = stringResource(R.string.language_settings),
                    subtitle = LanguageManager.getDisplayName(LanguageManager.currentLanguage),
                    onClick = { onNavigate(SettingsScreen.Language) }
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.DarkMode,
                    title = stringResource(R.string.theme_settings),
                    subtitle = stringResource(R.string.theme_subtitle),
                    onClick = { onNavigate(SettingsScreen.Theme) }
                )
            }
            // Video frame styles moved to Theme settings
            item {
                SettingsItem(
                    icon = Icons.Default.SystemUpdate,
                    title = stringResource(R.string.app_update),
                    subtitle = if (updateState.hasUpdate) stringResource(R.string.update_available_version, updateState.latestVersion ?: "") else stringResource(R.string.update_auto_check),
                    onClick = { viewModel.checkUpdates(force = true) }
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.about_app),
                    subtitle = "Версія ${BuildConfig.VERSION_NAME}",
                    onClick = { showAboutDialog = true }
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Account Info (if Pro)
            if (userData?.isPro == 1) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFD700)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Stars,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    stringResource(R.string.pro_account),
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 18.sp
                                )
                                Text(
                                    stringResource(R.string.thank_you_support),
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Logout
            item {
                SettingsItem(
                    icon = Icons.Default.ExitToApp,
                    title = stringResource(R.string.logout),
                    textColor = Color.Red,
                    onClick = { showLogoutDialog = true }
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
            }  // Кінець Column
        }  // Кінець AnimatedVisibility
    }  // Кінець Box

    // Logout confirmation dialog
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
                    Text(stringResource(R.string.logout), color = Color.Red)
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

    // About App dialog
    if (showAboutDialog) {
        com.worldmates.messenger.ui.components.AboutAppDialog(
            onDismiss = { showAboutDialog = false }
        )
    }
}

@Composable
fun ProfileSection(
    username: String,
    userId: Long,
    avatar: String?,
    email: String?,
    about: String?,
    onEditProfile: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEditProfile() },
        color = Color.White
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar
                AsyncImage(
                    model = avatar ?: "https://worldmates.club/upload/photos/d-avatar.jpg",
                    contentDescription = "Profile Avatar",
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )

                // User info
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = username,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Text(
                        text = "ID: $userId",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    if (!email.isNullOrEmpty()) {
                        Text(
                            text = email,
                            fontSize = 13.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                // Edit icon
                Icon(
                    Icons.Default.Edit,
                    contentDescription = stringResource(R.string.edit_profile),
                    tint = Color(0xFF0084FF)
                )
            }

            // About section
            if (!about.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = about,
                    fontSize = 14.sp,
                    color = Color.Gray,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
fun SettingsSection(title: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0084FF),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    textColor: Color = Color.Black,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    isPressed = false
                    onClick()
                }
            )
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        isPressed = event.changes.any { it.pressed }
                    }
                }
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon з градієнтним фоном
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = if (textColor == Color.Red) {
                                listOf(Color(0xFFFF6B6B), Color(0xFFFF8E53))
                            } else {
                                listOf(Color(0xFF667eea), Color(0xFF764ba2))
                            }
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Text
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (textColor == Color.Red) Color.Red else Color(0xFF2C3E50)
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = 13.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Arrow
            if (textColor != Color.Red) {
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color(0xFF667eea),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

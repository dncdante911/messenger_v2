package com.worldmates.messenger.ui.theme

import android.content.Intent
import android.net.Uri
import android.os.Build
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.ui.premium.PremiumActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.R
import com.worldmates.messenger.ui.preferences.BubbleStyle
import com.worldmates.messenger.ui.preferences.ChannelViewStyle
import com.worldmates.messenger.ui.preferences.UIStyle
import com.worldmates.messenger.ui.preferences.UIStylePreferences
import com.worldmates.messenger.ui.preferences.rememberBubbleStyle
import com.worldmates.messenger.ui.preferences.rememberChannelViewStyle
import com.worldmates.messenger.ui.preferences.rememberUIStyle
import androidx.compose.foundation.lazy.LazyRow

/**
 * Готові фонові градієнти для чатів.
 * Назви беруться з ресурсів рядків (nameResId) для підтримки локалізації.
 * id — стабільний ключ для DataStore та одноклікових пакетів.
 *
 * Видалені: ocean, deep_space, forest, cherry, neon_city, messenger,
 *           rose_quartz, golden_hour, velvet_night.
 * Перероблені: spring (квіткові відтінки) та winter (льодова палітра).
 */
enum class PresetBackground(
    val id: String,
    @StringRes val nameResId: Int,
    val gradientColors: List<Color>
) {
    // ── Нейтральні темні ────────────────────────────────────────────────────
    MIDNIGHT(
        id = "midnight",
        nameResId = R.string.bg_midnight,
        gradientColors = listOf(Color(0xFF0A0E27), Color(0xFF1A237E), Color(0xFF0D2137))
    ),

    // ── Тепла палітра ───────────────────────────────────────────────────────
    SUNSET(
        id = "sunset",
        nameResId = R.string.bg_sunset,
        // Приємний захід: золото → помаранчевий → насичений пурпур
        gradientColors = listOf(Color(0xFFFFB347), Color(0xFFFF6B35), Color(0xFFD44000), Color(0xFF7B1FA2))
    ),
    PEACH(
        id = "peach",
        nameResId = R.string.bg_peach,
        gradientColors = listOf(Color(0xFFFFF3E0), Color(0xFFFFCC80), Color(0xFFFF9A5C))
    ),
    FIRE(
        id = "fire",
        nameResId = R.string.bg_fire,
        gradientColors = listOf(Color(0xFF7F0000), Color(0xFFBF360C), Color(0xFFFF6F00), Color(0xFFFFD54F))
    ),
    SAND_DUNES(
        id = "sand_dunes",
        nameResId = R.string.bg_sand_dunes,
        gradientColors = listOf(Color(0xFFFFF8DC), Color(0xFFEDCB84), Color(0xFFC8965C))
    ),

    // ── Природа (перероблені) ────────────────────────────────────────────────
    SPRING(
        id = "spring",
        nameResId = R.string.bg_spring,
        // Японська весна: м'який рожевий → ніжна зелень → молода листва
        gradientColors = listOf(
            Color(0xFFFFF0F5),
            Color(0xFFFFD6E8),
            Color(0xFFFFC8C8),
            Color(0xFFD4EDAA),
            Color(0xFF8BC34A)
        )
    ),
    WINTER(
        id = "winter",
        nameResId = R.string.bg_winter,
        // Глибока зима: перлинний → льодовий синій → сталевий
        gradientColors = listOf(
            Color(0xFFF8FBFF),
            Color(0xFFD6EAF8),
            Color(0xFF85C1E9),
            Color(0xFF2E86C1),
            Color(0xFF1A5276)
        )
    ),

    // ── Пастельна палітра ────────────────────────────────────────────────────
    LAVENDER(
        id = "lavender",
        nameResId = R.string.bg_lavender,
        gradientColors = listOf(Color(0xFFEDE7F6), Color(0xFFD1C4E9), Color(0xFF9575CD))
    ),
    COTTON_CANDY(
        id = "cotton_candy",
        nameResId = R.string.bg_cotton_candy,
        gradientColors = listOf(Color(0xFFFFD6E0), Color(0xFFFFAFCC), Color(0xFFCDB4DB), Color(0xFFA2D2FF))
    ),
    MORNING_MIST(
        id = "morning_mist",
        nameResId = R.string.bg_morning_mist,
        gradientColors = listOf(Color(0xFFF5F7FA), Color(0xFFDDE8F5), Color(0xFFB0CCE9))
    ),

    // ── Ефектні ──────────────────────────────────────────────────────────────
    AURORA(
        id = "aurora",
        nameResId = R.string.bg_aurora,
        gradientColors = listOf(Color(0xFF0A1628), Color(0xFF004D40), Color(0xFF00BFA5), Color(0xFF7B1FA2))
    ),
    COSMIC(
        id = "cosmic",
        nameResId = R.string.bg_cosmic,
        gradientColors = listOf(Color(0xFF0D0221), Color(0xFF2D1B69), Color(0xFF6D3FC0), Color(0xFF9C6EE8))
    ),

    // ── М'ятно-блакитна / арктична ───────────────────────────────────────────
    MINT_SKY(
        id = "mint_sky",
        nameResId = R.string.bg_mint_sky,
        gradientColors = listOf(Color(0xFFE0FFF4), Color(0xFF80FFCC), Color(0xFF00D4AA), Color(0xFF0096C7))
    ),
    ARCTIC_BLUE(
        id = "arctic_blue",
        nameResId = R.string.bg_arctic_blue,
        gradientColors = listOf(Color(0xFFF0FAFF), Color(0xFFB8E8FF), Color(0xFF56CCF2), Color(0xFF2F80ED))
    ),
    DEEP_PLUM(
        id = "deep_plum",
        nameResId = R.string.bg_deep_plum,
        gradientColors = listOf(Color(0xFF1A0025), Color(0xFF3B0060), Color(0xFF5C0080), Color(0xFF2D0040))
    );

    companion object {
        fun fromId(id: String?): PresetBackground? {
            return entries.find { it.id == id }
        }
    }
}

/**
 * Категорії налаштувань теми
 */
private enum class ThemeCategory {
    MAIN_UI, CHANNELS, CALL_FRAMES, VIDEO_MSG_FRAMES
}

/**
 * Екран настройки темы (hub + 4 submenus)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(
    onBackClick: () -> Unit,
    onNavigateToCallFrame: (() -> Unit)? = null,
    onNavigateToVideoFrame: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val themeViewModel = rememberThemeViewModel()
    val themeState = rememberThemeState()
    val context = LocalContext.current

    var currentCategory by remember { mutableStateOf<ThemeCategory?>(null) }

    // Image picker for custom background
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                themeViewModel.setBackgroundImageUri(it.toString())
            } catch (e: Exception) {
                themeViewModel.setBackgroundImageUri(it.toString())
            }
        }
    }

    // Handle system back when inside a submenu
    androidx.activity.compose.BackHandler(enabled = currentCategory != null) {
        currentCategory = null
    }

    val screenTitle = when (currentCategory) {
        null -> stringResource(R.string.themes_title)
        ThemeCategory.MAIN_UI -> stringResource(R.string.theme_cat_main_ui)
        ThemeCategory.CHANNELS -> stringResource(R.string.theme_cat_channels)
        ThemeCategory.CALL_FRAMES -> stringResource(R.string.theme_cat_calls)
        ThemeCategory.VIDEO_MSG_FRAMES -> stringResource(R.string.theme_cat_video_msg)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = screenTitle, fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentCategory != null) currentCategory = null
                        else onBackClick()
                    }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        val currentBubbleStyle = rememberBubbleStyle()
        val currentUIStyle = rememberUIStyle()
        val currentChannelViewStyle = rememberChannelViewStyle()
        val currentQuickReaction by UIStylePreferences.quickReaction.collectAsState()

        when (currentCategory) {
            null -> ThemeHubContent(
                themeViewModel = themeViewModel,
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                onSelect = { cat ->
                    // Video categories open external activities directly.
                    when (cat) {
                        ThemeCategory.CALL_FRAMES -> {
                            if (onNavigateToCallFrame != null) onNavigateToCallFrame()
                            else currentCategory = cat
                        }
                        ThemeCategory.VIDEO_MSG_FRAMES -> {
                            if (onNavigateToVideoFrame != null) onNavigateToVideoFrame()
                            else currentCategory = cat
                        }
                        else -> currentCategory = cat
                    }
                }
            )
            ThemeCategory.MAIN_UI -> ThemeMainUIContent(
                themeState = themeState,
                themeViewModel = themeViewModel,
                currentBubbleStyle = currentBubbleStyle,
                currentUIStyle = currentUIStyle,
                currentQuickReaction = currentQuickReaction,
                onPickBackgroundImage = { imagePickerLauncher.launch("image/*") },
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
            ThemeCategory.CHANNELS -> ThemeChannelsContent(
                currentChannelViewStyle = currentChannelViewStyle,
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
            ThemeCategory.CALL_FRAMES -> ThemeCallFramesContent(
                onNavigateToCallFrame = onNavigateToCallFrame,
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
            ThemeCategory.VIDEO_MSG_FRAMES -> ThemeVideoMessagesContent(
                onNavigateToVideoFrame = onNavigateToVideoFrame,
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        }
    }
}

@Composable
private fun ThemeHubContent(
    themeViewModel: ThemeViewModel,
    onSelect: (ThemeCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        // Pinned: One-click interface packs
        item {
            OneClickInterfacePacksSection(
                themeViewModel = themeViewModel,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)
            )
        }

        item {
            Text(
                text = stringResource(R.string.theme_cat_section_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 10.dp)
            )
        }

        item {
            ThemeCategoryTile(
                emoji = "🎨",
                accentColor = Color(0xFF7C4DFF),
                title = stringResource(R.string.theme_cat_main_ui),
                subtitle = stringResource(R.string.theme_cat_main_ui_desc),
                onClick = { onSelect(ThemeCategory.MAIN_UI) }
            )
        }
        item {
            ThemeCategoryTile(
                emoji = "📺",
                accentColor = Color(0xFFD81B60),
                title = stringResource(R.string.theme_cat_channels),
                subtitle = stringResource(R.string.theme_cat_channels_desc),
                onClick = { onSelect(ThemeCategory.CHANNELS) }
            )
        }
        item {
            ThemeCategoryTile(
                emoji = "📞",
                accentColor = Color(0xFF1E88E5),
                title = stringResource(R.string.theme_cat_calls),
                subtitle = stringResource(R.string.theme_cat_calls_desc),
                onClick = { onSelect(ThemeCategory.CALL_FRAMES) }
            )
        }
        item {
            ThemeCategoryTile(
                emoji = "🎥",
                accentColor = Color(0xFF00897B),
                title = stringResource(R.string.theme_cat_video_msg),
                subtitle = stringResource(R.string.theme_cat_video_msg_desc),
                onClick = { onSelect(ThemeCategory.VIDEO_MSG_FRAMES) }
            )
        }
    }
}

@Composable
private fun ThemeCategoryTile(
    emoji: String,
    accentColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, fontSize = 22.sp)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ThemeMainUIContent(
    themeState: ThemeState,
    themeViewModel: ThemeViewModel,
    currentBubbleStyle: BubbleStyle,
    currentUIStyle: UIStyle,
    currentQuickReaction: String,
    onPickBackgroundImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        // Тема кольорів
        item {
            ThemeSectionHeader(
                emoji = "🎨",
                title = stringResource(R.string.select_theme),
                accentColor = Color(0xFF7C4DFF),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)
            )
        }
        item {
            ThemeVariantsLazyRow(
                selectedVariant = themeState.variant,
                onVariantSelected = { themeViewModel.setThemeVariant(it) }
            )
        }
        item {
            val resetDoneText = stringResource(R.string.theme_reset_done)
            OutlinedButton(
                onClick = {
                    themeViewModel.resetToDefaults()
                    android.widget.Toast.makeText(context, resetDoneText, android.widget.Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.theme_reset_to_default))
            }
        }

        // Відображення
        item {
            ThemeSectionHeader(
                emoji = "🌙",
                title = stringResource(R.string.appearance_section),
                accentColor = Color(0xFF1976D2),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 12.dp)
            )
        }
        item {
            ThemeModeSectionCard(
                themeState = themeState,
                viewModel = themeViewModel,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                MaterialYouCard(
                    enabled = themeState.useDynamicColor,
                    onToggle = { themeViewModel.toggleDynamicColor() },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        // Фон чату
        item {
            ThemeSectionHeader(
                emoji = "🖼️",
                title = stringResource(R.string.bg_section_title),
                subtitle = stringResource(R.string.bg_section_desc),
                accentColor = Color(0xFF00897B),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 12.dp)
            )
        }
        item {
            BackgroundPresetsLazyRow(
                currentPresetId = themeState.presetBackgroundId,
                onSelectPreset = { themeViewModel.setPresetBackgroundId(it) }
            )
        }
        item {
            BackgroundCustomImageRow(
                currentUri = themeState.backgroundImageUri,
                onSelectImage = onPickBackgroundImage,
                onRemoveImage = {
                    themeViewModel.setBackgroundImageUri(null)
                    themeViewModel.setPresetBackgroundId(null)
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }

        // Анімований фон
        item {
            ThemeSectionHeader(
                emoji = "✨",
                title = stringResource(R.string.animated_bg_section_title),
                subtitle = stringResource(R.string.animated_bg_section_desc),
                accentColor = Color(0xFF8E24AA),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp)
            )
        }
        item {
            AnimatedBackgroundPickerRow()
        }

        // Стиль бульбашок
        item {
            ThemeSectionHeader(
                emoji = "💬",
                title = stringResource(R.string.bubble_style_title),
                subtitle = stringResource(R.string.bubble_style_desc),
                accentColor = Color(0xFF0097A7),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 12.dp)
            )
        }
        item {
            BubbleStylesLazyRow(
                currentStyle = currentBubbleStyle,
                onStyleSelected = { UIStylePreferences.setBubbleStyle(context, it) }
            )
        }

        // Стиль інтерфейсу
        item {
            ThemeSectionHeader(
                emoji = "🎛️",
                title = stringResource(R.string.interface_style),
                subtitle = stringResource(R.string.interface_style_desc),
                accentColor = Color(0xFF2E7D32),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 12.dp)
            )
        }
        item {
            UIStyleToggleRow(
                currentStyle = currentUIStyle,
                onStyleSelected = { UIStylePreferences.setStyle(context, it) },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // Швидка реакція
        item {
            ThemeSectionHeader(
                emoji = "❤️",
                title = stringResource(R.string.quick_reaction_title),
                subtitle = stringResource(R.string.quick_reaction_desc),
                accentColor = Color(0xFFF4511E),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 12.dp)
            )
        }
        item {
            QuickReactionLazyRow(
                currentReaction = currentQuickReaction,
                onReactionSelected = { UIStylePreferences.setQuickReaction(context, it) }
            )
        }
    }
}

@Composable
private fun ThemeChannelsContent(
    currentChannelViewStyle: ChannelViewStyle,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        // Стиль каналів
        item {
            ThemeSectionHeader(
                emoji = "📺",
                title = stringResource(R.string.channel_view_style_title),
                subtitle = stringResource(R.string.channel_view_style_desc),
                accentColor = Color(0xFFD81B60),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)
            )
        }
        item {
            ChannelViewStyleSelector(
                currentStyle = currentChannelViewStyle,
                onStyleSelected = { UIStylePreferences.setChannelViewStyle(context, it) },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // Преміум канали — дизайн
        item {
            ThemeSectionHeader(
                emoji = "👑",
                title = stringResource(R.string.premium_channels_design_title),
                subtitle = stringResource(R.string.premium_channels_design_desc),
                accentColor = Color(0xFFC8A24B),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 12.dp)
            )
        }
        item {
            PremiumChannelsDesignTile(
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun ThemeCallFramesContent(
    onNavigateToCallFrame: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ThemeSectionHeader(
            emoji = "📞",
            title = stringResource(R.string.call_frame_section),
            subtitle = stringResource(R.string.call_frame_styles_list),
            accentColor = Color(0xFF1E88E5)
        )
        if (onNavigateToCallFrame != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToCallFrame),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📞", fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.theme_cat_calls_open),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Text(
                text = stringResource(R.string.theme_cat_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ThemeVideoMessagesContent(
    onNavigateToVideoFrame: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ThemeSectionHeader(
            emoji = "🎥",
            title = stringResource(R.string.video_message_frame_title),
            accentColor = Color(0xFF00897B)
        )
        if (onNavigateToVideoFrame != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToVideoFrame),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🎥", fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.theme_cat_video_msg_open),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Text(
                text = stringResource(R.string.theme_cat_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Секція стилів рамок відеодзвінків та відеоповідомлень
 */
@Composable
fun VideoFrameStylesSection(
    onNavigateToCallFrame: (() -> Unit)?,
    onNavigateToVideoFrame: (() -> Unit)?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.video_frame_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (onNavigateToCallFrame != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToCallFrame),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📹", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.call_frame_section),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.call_frame_styles_list),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (onNavigateToCallFrame != null && onNavigateToVideoFrame != null) {
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (onNavigateToVideoFrame != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToVideoFrame),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🎬", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.video_message_frame_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.video_frame_styles_list),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Карточка переключения режима темы (светлая/темная/системная)
 */
@Composable
fun ThemeModeSectionCard(
    themeState: ThemeState,
    viewModel: ThemeViewModel,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (themeState.isDark) Icons.Default.DarkMode else Icons.Default.LightMode,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.dark_theme),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                Switch(
                    checked = themeState.isDark,
                    onCheckedChange = { viewModel.toggleDarkTheme() },
                    enabled = !themeState.useSystemTheme,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.toggleSystemTheme() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.follow_system_theme),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.follow_system_theme_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = themeState.useSystemTheme,
                    onCheckedChange = { viewModel.toggleSystemTheme() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
    }
}

/**
 * Карточка Material You
 */
@Composable
fun MaterialYouCard(
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = null,
                    tint = if (enabled) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Material You 🎨",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.colors_from_wallpaper),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Switch(
                checked = enabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}

/**
 * Сетка вариантов тем
 */
@Composable
fun ThemeVariantsGrid(
    selectedVariant: ThemeVariant,
    onVariantSelected: (ThemeVariant) -> Unit
) {
    // Фильтруем темы: Material You только на Android 12+
    val availableThemes = ThemeVariant.values().filter { variant ->
        if (variant == ThemeVariant.MATERIAL_YOU) {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        } else {
            true
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.height(600.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(availableThemes) { variant ->
            ThemeVariantCard(
                variant = variant,
                isSelected = variant == selectedVariant,
                onClick = { onVariantSelected(variant) }
            )
        }
    }
}

/**
 * Карточка варианта темы
 */
@Composable
fun ThemeVariantCard(
    variant: ThemeVariant,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val palette = variant.getPalette()
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) palette.primary else Color.Transparent,
        animationSpec = tween(300),
        label = "borderColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .border(
                width = if (isSelected) 3.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 8.dp else 4.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Emoji
            Text(
                text = variant.emoji,
                fontSize = 32.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Название
            Text(
                text = variant.localizedDisplayName(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            // Описание
            Text(
                text = variant.localizedDescription(),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Цветовая палитра
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ColorCircle(color = palette.primary)
                Spacer(modifier = Modifier.width(4.dp))
                ColorCircle(color = palette.secondary)
                Spacer(modifier = Modifier.width(4.dp))
                ColorCircle(color = palette.accent)
            }

            // Индикатор выбора
            if (isSelected) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = stringResource(R.string.selected),
                        tint = palette.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.selected),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Цветной кружок для палитры
 */
@Composable
fun ColorCircle(color: Color) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.3f),
                shape = CircleShape
            )
    )
}


/**
 * Секція для вибору стилю інтерфейсу
 */
@Composable
fun UIStyleSection() {
    val context = LocalContext.current
    val currentStyle = com.worldmates.messenger.ui.preferences.rememberUIStyle()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.interface_style),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = stringResource(R.string.interface_style_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // WorldMates стиль
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        com.worldmates.messenger.ui.preferences.UIStylePreferences.setStyle(
                            context,
                            com.worldmates.messenger.ui.preferences.UIStyle.WORLDMATES
                        )
                    }
                    .background(
                        if (currentStyle == com.worldmates.messenger.ui.preferences.UIStyle.WORLDMATES)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surface
                    )
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = currentStyle == com.worldmates.messenger.ui.preferences.UIStyle.WORLDMATES,
                    onClick = {
                        com.worldmates.messenger.ui.preferences.UIStylePreferences.setStyle(
                            context,
                            com.worldmates.messenger.ui.preferences.UIStyle.WORLDMATES
                        )
                    }
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "WorldMates",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.interface_modern_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Telegram стиль
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        com.worldmates.messenger.ui.preferences.UIStylePreferences.setStyle(
                            context,
                            com.worldmates.messenger.ui.preferences.UIStyle.TELEGRAM
                        )
                    }
                    .background(
                        if (currentStyle == com.worldmates.messenger.ui.preferences.UIStyle.TELEGRAM)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surface
                    )
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = currentStyle == com.worldmates.messenger.ui.preferences.UIStyle.TELEGRAM,
                    onClick = {
                        com.worldmates.messenger.ui.preferences.UIStylePreferences.setStyle(
                            context,
                            com.worldmates.messenger.ui.preferences.UIStyle.TELEGRAM
                        )
                    }
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.frame_style_classic),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.interface_classic_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 🎨 Секція для вибору стилю бульбашок повідомлень
 */
@Composable
fun BubbleStyleSection() {
    val context = LocalContext.current
    val currentBubbleStyle = rememberBubbleStyle()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.bubble_style_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = stringResource(R.string.bubble_style_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Сітка з 10 стилями бульбашок
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.height(660.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(BubbleStyle.values()) { style ->
                    BubbleStyleCard(
                        bubbleStyle = style,
                        isSelected = style == currentBubbleStyle,
                        onClick = {
                            UIStylePreferences.setBubbleStyle(context, style)
                        }
                    )
                }
            }
        }
    }
}

/**
 * 💬 Карточка для вибору стилю бульбашки
 */
@Composable
fun BubbleStyleCard(
    bubbleStyle: BubbleStyle,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(300),
        label = "borderColor"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .border(
                width = if (isSelected) 3.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 8.dp else 4.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Іконка стилю
            Text(
                text = bubbleStyle.icon,
                fontSize = 32.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Назва стилю
            Text(
                text = bubbleStyle.localizedDisplayName(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            // Опис стилю
            Text(
                text = bubbleStyle.localizedDescription(),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Індикатор вибору
            if (isSelected) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = stringResource(R.string.selected),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.selected),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * ❤️ Секція для вибору емодзі швидкої реакції
 */
@Composable
fun QuickReactionSection() {
    val context = LocalContext.current
    val currentQuickReaction by UIStylePreferences.quickReaction.collectAsState()

    // Список популярних емодзі для швидкої реакції
    val popularEmojis = listOf(
        "❤️", "👍", "👎", "😂", "😮", "😢",
        "🔥", "✨", "🎉", "💯", "👏", "🙏"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.quick_reaction_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = stringResource(R.string.quick_reaction_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Сітка з емодзі
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier.height(160.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(popularEmojis) { emoji ->
                    EmojiReactionCard(
                        emoji = emoji,
                        isSelected = emoji == currentQuickReaction,
                        onClick = {
                            UIStylePreferences.setQuickReaction(context, emoji)
                        }
                    )
                }
            }
        }
    }
}

/**
 * ❤️ Карточка емодзі для швидкої реакції
 */
@Composable
fun EmojiReactionCard(
    emoji: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(300),
        label = "borderColor"
    )

    Card(
        modifier = Modifier
            .size(48.dp)
            .scale(scale)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 2.dp
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emoji,
                fontSize = 24.sp
            )
        }
    }
}

@Composable
fun ThemeVariant.localizedDescription(): String {
    return when (this) {
        ThemeVariant.CLASSIC          -> stringResource(R.string.theme_classic_desc)
        ThemeVariant.OCEAN            -> stringResource(R.string.theme_ocean_desc)
        ThemeVariant.PURPLE           -> stringResource(R.string.theme_purple_desc)
        ThemeVariant.MONOCHROME       -> stringResource(R.string.theme_monochrome_desc)
        ThemeVariant.NORD             -> stringResource(R.string.theme_nord_desc)
        ThemeVariant.DRACULA          -> stringResource(R.string.theme_dracula_desc)
        ThemeVariant.MATERIAL_YOU     -> stringResource(R.string.theme_material_you_desc)
        ThemeVariant.STRANGER_THINGS  -> stringResource(R.string.theme_stranger_things_desc)
        ThemeVariant.LORD_OF_THE_RINGS-> stringResource(R.string.theme_lotr_desc)
        ThemeVariant.TERMINATOR       -> stringResource(R.string.theme_terminator_desc)
        ThemeVariant.SUPERNATURAL     -> stringResource(R.string.theme_supernatural_desc)
        ThemeVariant.MARVEL           -> stringResource(R.string.theme_marvel_desc)
        ThemeVariant.CYBERPUNK        -> stringResource(R.string.theme_cyberpunk_desc)
        ThemeVariant.INTERSTELLAR     -> stringResource(R.string.theme_interstellar_desc)
        ThemeVariant.HARRY_POTTER     -> stringResource(R.string.theme_harry_potter_desc)
        ThemeVariant.DUNE             -> stringResource(R.string.theme_dune_desc)
        ThemeVariant.DEMON_SLAYER     -> stringResource(R.string.theme_demon_slayer_desc)
    }
}

@Composable
fun ThemeVariant.localizedDisplayName(): String {
    return when (this) {
        ThemeVariant.CLASSIC          -> stringResource(R.string.theme_classic_name)
        ThemeVariant.OCEAN            -> stringResource(R.string.theme_ocean_name)
        ThemeVariant.PURPLE           -> stringResource(R.string.theme_purple_name)
        ThemeVariant.MONOCHROME       -> stringResource(R.string.theme_monochrome_name)
        ThemeVariant.NORD             -> stringResource(R.string.theme_nord_name)
        ThemeVariant.DRACULA          -> stringResource(R.string.theme_dracula_name)
        ThemeVariant.MATERIAL_YOU     -> stringResource(R.string.theme_material_you_name)
        ThemeVariant.STRANGER_THINGS  -> stringResource(R.string.theme_stranger_things_name)
        ThemeVariant.LORD_OF_THE_RINGS-> stringResource(R.string.theme_lotr_name)
        ThemeVariant.TERMINATOR       -> stringResource(R.string.theme_terminator_name)
        ThemeVariant.SUPERNATURAL     -> stringResource(R.string.theme_supernatural_name)
        ThemeVariant.MARVEL           -> stringResource(R.string.theme_marvel_name)
        ThemeVariant.CYBERPUNK        -> stringResource(R.string.theme_cyberpunk_name)
        ThemeVariant.INTERSTELLAR     -> stringResource(R.string.theme_interstellar_name)
        ThemeVariant.HARRY_POTTER     -> stringResource(R.string.theme_harry_potter_name)
        ThemeVariant.DUNE             -> stringResource(R.string.theme_dune_name)
        ThemeVariant.DEMON_SLAYER     -> stringResource(R.string.theme_demon_slayer_name)
    }
}

@Composable
fun BubbleStyle.localizedDisplayName(): String {
    return when (this) {
        BubbleStyle.STANDARD -> stringResource(R.string.bubble_standard)
        BubbleStyle.COMIC -> stringResource(R.string.bubble_comic)
        BubbleStyle.TELEGRAM -> stringResource(R.string.bubble_classic_name)
        BubbleStyle.MINIMAL -> stringResource(R.string.bubble_minimal)
        BubbleStyle.MODERN -> stringResource(R.string.bubble_modern)
        BubbleStyle.RETRO -> stringResource(R.string.bubble_retro)
        BubbleStyle.GLASS -> stringResource(R.string.bubble_glass)
        BubbleStyle.NEON -> stringResource(R.string.bubble_neon)
        BubbleStyle.GRADIENT -> stringResource(R.string.bubble_gradient)
        BubbleStyle.NEUMORPHISM -> stringResource(R.string.bubble_neumorphism)
        BubbleStyle.SOFT -> stringResource(R.string.bubble_soft)
        BubbleStyle.OUTLINED -> stringResource(R.string.bubble_outlined)
    }
}

@Composable
fun BubbleStyle.localizedDescription(): String {
    return when (this) {
        BubbleStyle.STANDARD -> stringResource(R.string.bubble_standard_desc)
        BubbleStyle.COMIC -> stringResource(R.string.bubble_comic_desc)
        BubbleStyle.TELEGRAM -> stringResource(R.string.bubble_classic_desc)
        BubbleStyle.MINIMAL -> stringResource(R.string.bubble_minimal_desc)
        BubbleStyle.MODERN -> stringResource(R.string.bubble_modern_desc)
        BubbleStyle.RETRO -> stringResource(R.string.bubble_retro_desc)
        BubbleStyle.GLASS -> stringResource(R.string.bubble_glass_desc)
        BubbleStyle.NEON -> stringResource(R.string.bubble_neon_desc)
        BubbleStyle.GRADIENT -> stringResource(R.string.bubble_gradient_desc)
        BubbleStyle.NEUMORPHISM -> stringResource(R.string.bubble_neumorphism_desc)
        BubbleStyle.SOFT -> stringResource(R.string.bubble_soft_desc)
        BubbleStyle.OUTLINED -> stringResource(R.string.bubble_outlined_desc)
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// НОВІ КОМПОНЕНТИ ДЛЯ ПЕРЕРОБЛЕНОГО ЕКРАНУ ТЕМ
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Заголовок-роздільник секції з кольоровим значком та необов'язковим підзаголовком.
 */
@Composable
fun ThemeSectionHeader(
    emoji: String,
    title: String,
    subtitle: String? = null,
    accentColor: Color? = null,
    modifier: Modifier = Modifier
) {
    val boxColor = accentColor ?: MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = if (subtitle != null) Alignment.Top else Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(boxColor, boxColor.copy(alpha = 0.65f)),
                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                        end = androidx.compose.ui.geometry.Offset(40f, 40f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(text = emoji, fontSize = 20.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

/**
 * Горизонтальний LazyRow карток вибору теми кольорів.
 * Замінює LazyVerticalGrid з фіксованою висотою 600 dp — усуває вкладений скрол.
 */
@Composable
fun ThemeVariantsLazyRow(
    selectedVariant: ThemeVariant,
    onVariantSelected: (ThemeVariant) -> Unit
) {
    val context = LocalContext.current
    val canUsePremium = remember { UserSession.isProActive }
    val availableThemes = ThemeVariant.entries.filter { v ->
        v != ThemeVariant.MATERIAL_YOU || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(availableThemes) { variant ->
            val isLocked = variant.isPremium && !canUsePremium
            ThemeVariantChip(
                variant = variant,
                isSelected = variant == selectedVariant,
                isLocked = isLocked,
                onClick = {
                    if (isLocked) {
                        context.startActivity(Intent(context, PremiumActivity::class.java))
                    } else {
                        onVariantSelected(variant)
                    }
                }
            )
        }
    }
}

/**
 * Компактна картка теми в горизонтальному ряду: емодзі + назва + 3 кольорових кружки.
 * Для PRO/підписки показує золотий значок і замок, якщо не підписаний.
 */
@Composable
fun ThemeVariantChip(
    variant: ThemeVariant,
    isSelected: Boolean,
    isLocked: Boolean = false,
    onClick: () -> Unit
) {
    val palette = variant.getPalette()
    val borderColor by animateColorAsState(
        targetValue = when {
            isSelected -> palette.primary
            variant.isSubscriptionOnly -> Color(0xFFFFD700).copy(alpha = 0.6f)
            variant.isPremium          -> Color(0xFFFFD700).copy(alpha = 0.4f)
            else                       -> Color.Transparent
        },
        animationSpec = tween(300),
        label = "chipBorder"
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "chipScale"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .width(88.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(2.dp, borderColor, RoundedCornerShape(14.dp))
                .background(
                    when {
                        isSelected -> palette.primary.copy(alpha = 0.12f)
                        isLocked   -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        else       -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
                .clickable(
                    onClick = onClick,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                )
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = variant.emoji, fontSize = 28.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = variant.localizedDisplayName(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                color = if (isSelected) palette.primary
                        else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                lineHeight = 13.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                listOf(palette.primary, palette.secondary, palette.accent).forEach { c ->
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(c)
                    )
                }
            }
            if (isSelected) {
                Spacer(modifier = Modifier.height(5.dp))
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = palette.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // PRO / замок бейдж (верхній правий кут)
        if (variant.isPremium) {
            val badgeText = stringResource(
                if (variant.isSubscriptionOnly) R.string.theme_badge_exclusive
                else R.string.theme_badge_pro
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp)
                    .background(
                        color = if (variant.isSubscriptionOnly) Color(0xFFFF6D00)
                                else Color(0xFFFFD700),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (isLocked) "🔒 $badgeText" else badgeText,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212121)
                )
            }
        }
    }
}

/**
 * Горизонтальний LazyRow карток готових градієнтних фонів.
 * Замінює LazyVerticalGrid з фіксованою висотою 480 dp.
 */
@Composable
fun BackgroundPresetsLazyRow(
    currentPresetId: String?,
    onSelectPreset: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(PresetBackground.values()) { preset ->
            BackgroundPresetChip(
                preset = preset,
                isSelected = preset.id == currentPresetId,
                onClick = { onSelectPreset(preset.id) }
            )
        }
    }
}

/**
 * Картка одного готового градієнтного фону з назвою та індикатором вибору.
 */
@Composable
fun BackgroundPresetChip(
    preset: PresetBackground,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.06f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "presetScale"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color.Transparent,
        animationSpec = tween(300),
        label = "presetBorder"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .width(76.dp)
            .height(110.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(2.dp, borderColor, RoundedCornerShape(14.dp))
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = preset.gradientColors
                )
            )
            .clickable(onClick = onClick)
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(18.dp)
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                    )
                )
                .padding(vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(preset.nameResId),
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Рядок для перегляду, вибору та видалення власного фото фону.
 */
@Composable
fun BackgroundCustomImageRow(
    currentUri: String?,
    onSelectImage: () -> Unit,
    onRemoveImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (currentUri != null) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                coil.compose.AsyncImage(
                    model = android.net.Uri.parse(currentUri),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    onClick = onRemoveImage,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(26.dp)
                        .background(MaterialTheme.colorScheme.errorContainer, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.remove_background),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
        androidx.compose.material3.OutlinedButton(
            onClick = onSelectImage,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (currentUri != null) stringResource(R.string.bg_change)
                       else stringResource(R.string.bg_pick),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Горизонтальний LazyRow карток стилів бульбашок.
 * Замінює LazyVerticalGrid з фіксованою висотою 660 dp.
 */
@Composable
fun BubbleStylesLazyRow(
    currentStyle: BubbleStyle,
    onStyleSelected: (BubbleStyle) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(BubbleStyle.values()) { style ->
            BubbleStyleChip(
                bubbleStyle = style,
                isSelected = style == currentStyle,
                onClick = { onStyleSelected(style) }
            )
        }
    }
}

/**
 * Компактна картка стилю бульбашок для горизонтального ряду.
 */
@Composable
fun BubbleStyleChip(
    bubbleStyle: BubbleStyle,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(300),
        label = "bubbleBorder"
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "bubbleScale"
    )

    Column(
        modifier = Modifier
            .scale(scale)
            .width(92.dp)
            .heightIn(min = 92.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(2.dp, borderColor, RoundedCornerShape(14.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = bubbleStyle.icon, fontSize = 28.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = bubbleStyle.localizedDisplayName(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Дві картки вибору стилю інтерфейсу з візуальним превью.
 */
@Composable
fun UIStyleToggleRow(
    currentStyle: UIStyle,
    onStyleSelected: (UIStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UIStyle.values().forEach { style ->
            val isSelected = style == currentStyle
            val icon = when (style) {
                UIStyle.WORLDMATES -> "🪟"
                UIStyle.TELEGRAM   -> "📋"
            }
            val label = when (style) {
                UIStyle.WORLDMATES -> "WorldMates"
                UIStyle.TELEGRAM   -> stringResource(R.string.frame_style_classic)
            }
            val description = when (style) {
                UIStyle.WORLDMATES -> stringResource(R.string.interface_modern_desc)
                UIStyle.TELEGRAM   -> stringResource(R.string.interface_classic_desc)
            }
            StyleOptionRow(
                icon = icon,
                label = label,
                description = description,
                isSelected = isSelected,
                onClick = { onStyleSelected(style) }
            )
        }
    }
}

@Composable
private fun StyleOptionRow(
    icon: String,
    label: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        animationSpec = tween(250),
        label = "style_row_bg"
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        border = if (isSelected)
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        else
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        tonalElevation = if (isSelected) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        color = if (isSelected)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = icon, fontSize = 22.sp)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            RadioButton(
                selected = isSelected,
                onClick = null,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

/**
 * Горизонтальний LazyRow вибору емодзі для швидкої реакції.
 * Замінює LazyVerticalGrid з фіксованою висотою 160 dp.
 */
@Composable
fun QuickReactionLazyRow(
    currentReaction: String,
    onReactionSelected: (String) -> Unit
) {
    val popularEmojis = listOf(
        "❤️", "👍", "👎", "😂", "😮", "😢",
        "🔥", "✨", "🎉", "💯", "👏", "🙏"
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(popularEmojis) { emoji ->
            EmojiReactionCard(
                emoji = emoji,
                isSelected = emoji == currentReaction,
                onClick = { onReactionSelected(emoji) }
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════
// CHANNEL VIEW STYLE SELECTOR
// ══════════════════════════════════════════════════════════════════

/**
 * Селектор стилю відображення каналів — дві картки поруч
 */
@Composable
fun ChannelViewStyleSelector(
    currentStyle: ChannelViewStyle,
    onStyleSelected: (ChannelViewStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChannelViewStyleCard(
            title = stringResource(R.string.channel_style_classic),
            description = stringResource(R.string.channel_style_classic_desc),
            emoji = "📺",
            isSelected = currentStyle == ChannelViewStyle.CLASSIC,
            onClick = { onStyleSelected(ChannelViewStyle.CLASSIC) }
        )
        ChannelViewStyleCard(
            title = stringResource(R.string.channel_style_premium),
            description = stringResource(R.string.channel_style_premium_desc),
            emoji = "⭐",
            isSelected = currentStyle == ChannelViewStyle.PREMIUM,
            onClick = { onStyleSelected(ChannelViewStyle.PREMIUM) }
        )
    }
}

@Composable
private fun ChannelViewStyleCard(
    title: String,
    description: String,
    emoji: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        animationSpec = tween(250),
        label = "channel_card_bg"
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        border = if (isSelected)
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        else
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        tonalElevation = if (isSelected) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        color = if (isSelected)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, fontSize = 22.sp)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            RadioButton(
                selected = isSelected,
                onClick = null,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

/**
 * Картка-вхід у налаштування Obsidian-Gold оформлення для преміум-каналів.
 * Відкриває PremiumChannelsThemeActivity — там живий прев'ю, перемикач
 * «новий вигляд» і вибір акценту / рамки / банера / пакета емодзі.
 * Залочена, доки користувач не активує PRO — тап веде на PremiumActivity.
 */
@Composable
fun PremiumChannelsDesignTile(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val canUsePremium = remember { UserSession.isProActive }
    val gold = Color(0xFFC8A24B)
    val onyx = Color(0xFF0E0E12)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                if (canUsePremium) {
                    context.startActivity(
                        com.worldmates.messenger.ui.channels.premium.appearance
                            .PremiumChannelsThemeActivity.createIntent(context)
                    )
                } else {
                    context.startActivity(Intent(context, PremiumActivity::class.java))
                }
            },
        shape = RoundedCornerShape(18.dp),
        color = onyx,
        border = BorderStroke(1.dp, gold.copy(alpha = 0.55f)),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(gold.copy(alpha = 0.16f))
                    .border(1.dp, gold.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "👑", fontSize = 20.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = stringResource(R.string.premium_channels_design_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFF2E7C8)
                )
                Text(
                    text = if (canUsePremium)
                        stringResource(R.string.premium_channels_design_desc)
                    else
                        stringResource(R.string.premium_channels_design_locked),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFF2E7C8).copy(alpha = 0.7f),
                    maxLines = 2
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = if (canUsePremium) "›" else "🔒",
                fontSize = 20.sp,
                color = gold,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

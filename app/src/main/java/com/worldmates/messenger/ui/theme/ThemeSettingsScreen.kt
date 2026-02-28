package com.worldmates.messenger.ui.theme

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.worldmates.messenger.ui.preferences.UIStylePreferences
import com.worldmates.messenger.ui.preferences.rememberBubbleStyle

/**
 * Готові фонові градієнти для чатів
 */
enum class PresetBackground(
    val id: String,
    val displayName: String,
    val gradientColors: List<Color>
) {
    OCEAN(
        id = "ocean",
        displayName = "Океан",
        gradientColors = listOf(Color(0xFF667eea), Color(0xFF764ba2))
    ),
    SUNSET(
        id = "sunset",
        displayName = "Захід сонця",
        gradientColors = listOf(Color(0xFFf83600), Color(0xFFf9d423))
    ),
    FOREST(
        id = "forest",
        displayName = "Ліс",
        gradientColors = listOf(Color(0xFF11998e), Color(0xFF38ef7d))
    ),
    LAVENDER(
        id = "lavender",
        displayName = "Лаванда",
        gradientColors = listOf(Color(0xFFa8edea), Color(0xFFfed6e3))
    ),
    MIDNIGHT(
        id = "midnight",
        displayName = "Опівночі",
        gradientColors = listOf(Color(0xFF2c3e50), Color(0xFF3498db))
    ),
    PEACH(
        id = "peach",
        displayName = "Персик",
        gradientColors = listOf(Color(0xFFffecd2), Color(0xFFfcb69f))
    ),
    FIRE(
        id = "fire",
        displayName = "Вогонь",
        gradientColors = listOf(Color(0xFFFF0000), Color(0xFFFF7F00), Color(0xFFFFFF00))
    ),
    AURORA(
        id = "aurora",
        displayName = "Полярне сяйво",
        gradientColors = listOf(Color(0xFF00FFA3), Color(0xFF03E1FF), Color(0xFFDC1FFF))
    ),
    CHERRY(
        id = "cherry",
        displayName = "Вишня",
        gradientColors = listOf(Color(0xFFEB3349), Color(0xFFF45C43))
    ),
    COSMIC(
        id = "cosmic",
        displayName = "Космос",
        gradientColors = listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0))
    ),
    COTTON_CANDY(
        id = "cotton_candy",
        displayName = "Цукрова вата",
        gradientColors = listOf(Color(0xFFFFAFBD), Color(0xFFffc3a0))
    ),
    DEEP_SPACE(
        id = "deep_space",
        displayName = "Глибокий космос",
        gradientColors = listOf(Color(0xFF000428), Color(0xFF004e92))
    ),
    SPRING(
        id = "spring",
        displayName = "Весна",
        gradientColors = listOf(Color(0xFF00b09b), Color(0xFF96c93d))
    ),
    NEON_CITY(
        id = "neon_city",
        displayName = "Неон-Сіті",
        gradientColors = listOf(Color(0xFFFC466B), Color(0xFF3F5EFB))
    ),
    WINTER(
        id = "winter",
        displayName = "Зима",
        gradientColors = listOf(Color(0xFFE6DADA), Color(0xFF274046))
    );

    companion object {
        fun fromId(id: String?): PresetBackground? {
            return values().find { it.id == id }
        }
    }
}

/**
 * Екран настройки темы
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

    // Лаунчер для вибору зображення
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                // Получаем постоянное разрешение на URI
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                android.util.Log.d("ThemeSettings", "Persistable permission granted for: $it")
                themeViewModel.setBackgroundImageUri(it.toString())
                android.util.Log.d("ThemeSettings", "Background image saved: ${it.toString()}")
            } catch (e: Exception) {
                android.util.Log.e("ThemeSettings", "Failed to take persistable permission", e)
                // Все равно пробуем сохранить URI
                themeViewModel.setBackgroundImageUri(it.toString())
            }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.themes_title),
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
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
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Секция темной темы
            item {
                ThemeModeSectionCard(
                    themeState = themeState,
                    viewModel = themeViewModel
                )
            }

            // Секция фонового изображения
            item {
                BackgroundImageSection(
                    currentBackgroundUri = themeState.backgroundImageUri,
                    currentPresetId = themeState.presetBackgroundId,
                    onSelectImage = {
                        android.util.Log.d("ThemeSettings", "Opening image picker for background")
                        imagePickerLauncher.launch("image/*")
                    },
                    onRemoveImage = {
                        android.util.Log.d("ThemeSettings", "Removing background")
                        themeViewModel.setBackgroundImageUri(null)
                        themeViewModel.setPresetBackgroundId(null)
                    },
                    onSelectPreset = { presetId ->
                        android.util.Log.d("ThemeSettings", "User selected preset background: $presetId")
                        themeViewModel.setPresetBackgroundId(presetId)
                        android.util.Log.d("ThemeSettings", "setPresetBackgroundId called with: $presetId")
                    }
                )
            }

            // Секция выбора стиля интерфейса (WorldMates/Telegram)
            item {
                UIStyleSection()
            }

            // Секция выбора стиля бульбашок
            item {
                BubbleStyleSection()
            }

            // Секція вибору швидкої реакції
            item {
                QuickReactionSection()
            }

            // One-click готові набори інтерфейсу
            item {
                OneClickInterfacePacksSection(themeViewModel = themeViewModel)
            }

            // Стилі рамок відео (перенесено з налаштувань)
            if (onNavigateToCallFrame != null || onNavigateToVideoFrame != null) {
                item {
                    VideoFrameStylesSection(
                        onNavigateToCallFrame = onNavigateToCallFrame,
                        onNavigateToVideoFrame = onNavigateToVideoFrame
                    )
                }
            }

            // Сетка вариантов тем
            item {
                Column {
                    Text(
                        text = stringResource(R.string.select_theme),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    ThemeVariantsGrid(
                        selectedVariant = themeState.variant,
                        onVariantSelected = { themeViewModel.setThemeVariant(it) }
                    )
                }
            }
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
    viewModel: ThemeViewModel
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                text = variant.displayName,
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
        ThemeVariant.CLASSIC -> stringResource(R.string.theme_classic_desc)
        ThemeVariant.OCEAN -> stringResource(R.string.theme_ocean_desc)
        ThemeVariant.SUNSET -> stringResource(R.string.theme_sunset_desc)
        ThemeVariant.FOREST -> stringResource(R.string.theme_forest_desc)
        ThemeVariant.PURPLE -> stringResource(R.string.theme_purple_desc)
        ThemeVariant.ROSE_GOLD -> stringResource(R.string.theme_rose_gold_desc)
        ThemeVariant.MONOCHROME -> stringResource(R.string.theme_monochrome_desc)
        ThemeVariant.NORD -> stringResource(R.string.theme_nord_desc)
        ThemeVariant.DRACULA -> stringResource(R.string.theme_dracula_desc)
        ThemeVariant.MATERIAL_YOU -> stringResource(R.string.theme_material_you_desc)
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
    }
}

package com.worldmates.messenger.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.data.model.User
import kotlinx.coroutines.launch

/**
 * Новий екран профілю у стилі VK/Telegram:
 * - Великий аватар по центру
 * - Ім'я + статус онлайн
 * - 4 кнопки дій (Вибрати фото, Змінити, Налаштування, Теми)
 * - Інфо-карточка (телефон, про себе, username, дн)
 * - Вкладки Публікації / Архів публікацій
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Запуск галереї для вибору фото
    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onAvatarSelected(it) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Верхня панель з іконками QR та MoreVert
        item {
            ProfileTopBar(
                onQrCodeClick = onQrCodeClick,
                onMoreClick = onMoreClick
            )
        }

        // Великий аватар по центру
        item {
            ProfileAvatarSection(
                avatarUrl = user.avatar,
                isPro = user.isPro > 0
            )
        }

        // Ім'я та статус
        item {
            ProfileNameSection(
                user = user
            )
        }

        // 4 кнопки дій
        item {
            ProfileActionButtons(
                onPhotoClick = { avatarPicker.launch("image/*") },
                onEditClick = onEditClick,
                onSettingsClick = onSettingsClick,
                onThemesClick = onThemesClick
            )
        }

        // Інфо-карточка
        item {
            ProfileInfoCard(user = user)
        }

        // Вкладки Публікації / Архів
        item {
            ProfilePublicationsTabs()
        }

        // Порожній стан публікацій
        item {
            EmptyPublicationsPlaceholder()
        }
    }
}

// ============================================
// Компоненти профілю
// ============================================

/**
 * Верхня панель профілю (QR-код зліва, MoreVert справа)
 */
@Composable
private fun ProfileTopBar(
    onQrCodeClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onQrCodeClick) {
            Icon(
                Icons.Default.QrCode2,
                contentDescription = "QR-код",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(28.dp)
            )
        }
        IconButton(onClick = onMoreClick) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "Більше",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/**
 * Секція з великим аватаром по центру
 */
@Composable
private fun ProfileAvatarSection(
    avatarUrl: String?,
    isPro: Boolean
) {
    Box(
        modifier = Modifier
            .padding(top = 8.dp)
            .size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        // Аватар
        AsyncImage(
            model = avatarUrl,
            contentDescription = "Аватар",
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                .then(
                    if (isPro) Modifier.border(
                        3.dp,
                        MaterialTheme.colorScheme.primary,
                        CircleShape
                    ) else Modifier
                ),
            contentScale = ContentScale.Crop
        )
    }
}

/**
 * Ім'я, верифікація та статус онлайн
 */
@Composable
private fun ProfileNameSection(user: User) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Ім'я з верифікацією
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${user.firstName ?: ""} ${user.lastName ?: ""}".trim()
                    .ifBlank { user.username },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            if (user.verified == 1) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    Icons.Default.QrCode2,  // Можна замінити на іконку верифікації
                    contentDescription = "Verified",
                    tint = Color(0xFF0084FF),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Статус онлайн
        Text(
            text = when (user.lastSeenStatus) {
                "online" -> "в мережі"
                else -> "був(ла) нещодавно"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (user.lastSeenStatus == "online")
                Color(0xFF4CAF50)
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/**
 * 4 кнопки дій: Вибрати фото, Змінити, Налаштування, Теми
 */
@Composable
private fun ProfileActionButtons(
    onPhotoClick: () -> Unit,
    onEditClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onThemesClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ProfileActionButton(
            icon = Icons.Default.CameraAlt,
            label = "Выбрать\nфото",
            onClick = onPhotoClick,
            modifier = Modifier.weight(1f)
        )
        ProfileActionButton(
            icon = Icons.Default.Edit,
            label = "Изменить",
            onClick = onEditClick,
            modifier = Modifier.weight(1f)
        )
        ProfileActionButton(
            icon = Icons.Default.Settings,
            label = "Настройки",
            onClick = onSettingsClick,
            modifier = Modifier.weight(1f)
        )
        ProfileActionButton(
            icon = Icons.Default.Palette,
            label = "Темы",
            onClick = onThemesClick,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Окрема кнопка дії
 */
@Composable
private fun ProfileActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Інфо-карточка з даними профілю
 */
@Composable
private fun ProfileInfoCard(user: User) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Телефон
            if (!user.phoneNumber.isNullOrBlank()) {
                ProfileInfoRow(
                    value = user.phoneNumber,
                    label = "Телефон"
                )
                InfoDivider()
            }

            // Про себе
            if (!user.about.isNullOrBlank()) {
                ProfileInfoRow(
                    value = user.about,
                    label = "О себе"
                )
                InfoDivider()
            }

            // Username
            ProfileInfoRow(
                value = "@${user.username}",
                label = "Имя пользователя"
            )

            // День народження
            if (!user.birthday.isNullOrBlank()) {
                InfoDivider()
                ProfileInfoRow(
                    value = formatBirthday(user.birthday),
                    label = "День рождения"
                )
            }

            // Місто
            if (!user.city.isNullOrBlank()) {
                InfoDivider()
                ProfileInfoRow(
                    value = user.city,
                    label = "Город"
                )
            }

            // Робота
            if (!user.working.isNullOrBlank()) {
                InfoDivider()
                ProfileInfoRow(
                    value = user.working,
                    label = "Работа"
                )
            }

            // Сайт
            if (!user.website.isNullOrBlank()) {
                InfoDivider()
                ProfileInfoRow(
                    value = user.website,
                    label = "Веб-сайт"
                )
            }
        }
    }
}

/**
 * Рядок інфо в карточці (значення + лейбл)
 */
@Composable
private fun ProfileInfoRow(
    value: String,
    label: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun InfoDivider() {
    Divider(
        modifier = Modifier.padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

/**
 * Вкладки Публікації / Архів публікацій
 */
@Composable
private fun ProfilePublicationsTabs() {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Публикации", "Архив публикаций")

    TabRow(
        selectedTabIndex = selectedTab,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary,
        indicator = { tabPositions ->
            SecondaryIndicator(
                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                height = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    ) {
        tabs.forEachIndexed { index, title ->
            Tab(
                selected = selectedTab == index,
                onClick = { selectedTab = index },
                text = {
                    Text(
                        text = title,
                        fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selectedTab == index)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }
    }
}

/**
 * Порожній стан публікацій
 */
@Composable
private fun EmptyPublicationsPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Публикаций пока нет...",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Публикуйте фото и видео в своём профиле",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/**
 * Форматування дати народження
 */
private fun formatBirthday(birthday: String): String {
    return try {
        val parts = birthday.split("-")
        if (parts.size == 3) {
            val year = parts[0].toInt()
            val month = parts[1].toInt()
            val day = parts[2].toInt()
            val monthNames = listOf(
                "", "янв.", "февр.", "мар.", "апр.", "мая", "июн.",
                "июл.", "авг.", "сент.", "окт.", "нояб.", "дек."
            )
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            val age = currentYear - year
            "$day ${monthNames.getOrElse(month) { "" }} $year ($age лет)"
        } else {
            birthday
        }
    } catch (e: Exception) {
        birthday
    }
}

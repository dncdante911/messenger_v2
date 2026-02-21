package com.worldmates.messenger.ui.messages

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage
import com.worldmates.messenger.data.Constants
import com.worldmates.messenger.ui.media.ImageGalleryViewer
import com.worldmates.messenger.ui.media.InlineVideoPlayer
import com.worldmates.messenger.ui.media.MiniAudioPlayer
import com.worldmates.messenger.ui.media.FullscreenVideoPlayer
import com.worldmates.messenger.data.model.Message
import com.worldmates.messenger.data.model.ReactionGroup
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.network.FileManager
import com.worldmates.messenger.network.NetworkQualityMonitor
import com.worldmates.messenger.ui.theme.rememberThemeState
import com.worldmates.messenger.ui.theme.PresetBackground
import com.worldmates.messenger.ui.components.UserProfileMenuSheet
import com.worldmates.messenger.ui.components.UserMenuData
import com.worldmates.messenger.ui.components.UserMenuAction
import com.worldmates.messenger.ui.preferences.rememberBubbleStyle
import com.worldmates.messenger.ui.preferences.rememberQuickReaction
import com.worldmates.messenger.ui.preferences.rememberUIStyle
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import com.worldmates.messenger.ui.components.AnimatedStickerView
import com.worldmates.messenger.utils.VoiceRecorder
import com.worldmates.messenger.utils.VoicePlayer
import kotlinx.coroutines.launch

// 🔥 Імпорти нових компонентів для режиму вибору повідомлень
import com.worldmates.messenger.ui.messages.selection.SelectionBottomBar
import com.worldmates.messenger.ui.messages.selection.SelectionTopBarActions
import com.worldmates.messenger.ui.messages.selection.MediaActionMenu
import com.worldmates.messenger.ui.messages.selection.QuickReactionAnimation
import com.worldmates.messenger.ui.messages.selection.ForwardMessageDialog

// 📌 Імпорт компонента закріпленого повідомлення
import com.worldmates.messenger.ui.groups.components.PinnedMessageBanner

// 🔍 Імпорт компонента пошуку
import com.worldmates.messenger.ui.messages.components.GroupSearchBar
import com.worldmates.messenger.ui.search.MediaSearchScreen

// 📝 Імпорти системи форматування тексту
import com.worldmates.messenger.ui.components.formatting.FormattedText
import com.worldmates.messenger.ui.components.formatting.FormattingSettings
import com.worldmates.messenger.ui.components.formatting.FormattingToolbar
import com.worldmates.messenger.ui.components.formatting.FormattedTextColors

// 💬 Імпорти компонентів форматованих повідомлень
import com.worldmates.messenger.ui.messages.FormattedMessageContent
import com.worldmates.messenger.ui.messages.FormattedMessageText

// 👆 Імпорт покращеного обробника дотиків
import com.worldmates.messenger.ui.messages.MessageTouchWrapper
import com.worldmates.messenger.ui.messages.MessageTouchConfig
import com.worldmates.messenger.ui.components.CompactMediaMenu
import com.worldmates.messenger.ui.components.media.VideoMessageComponent

// 🎯 Enum для режимів введення (як в Telegram/Viber)
enum class InputMode {
    TEXT,       // Звичайне текстове повідомлення
    VOICE,      // Голосове повідомлення
    VIDEO,      // Відео-повідомлення (майбутнє)
    EMOJI,      // Емодзі пікер
    STICKER,    // Стікери
    GIF         // GIF пікер
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessagesScreen(
    viewModel: MessagesViewModel,
    fileManager: FileManager,
    voiceRecorder: VoiceRecorder,
    voicePlayer: VoicePlayer,
    recipientName: String,
    recipientAvatar: String,
    isGroup: Boolean,
    onBackPressed: () -> Unit,
    onRequestAudioPermission: () -> Boolean = { true },  // Default для preview
    onRequestVideoPermissions: () -> Boolean = { true }
) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val uploadProgress by viewModel.uploadProgress.collectAsState()
    val recordingState by voiceRecorder.recordingState.collectAsState()
    val recordingDuration by voiceRecorder.recordingDuration.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val isOnline by viewModel.recipientOnlineStatus.collectAsState()
    val connectionQuality by viewModel.connectionQuality.collectAsState()
    val pinnedPrivateMessage by viewModel.pinnedPrivateMessage.collectAsState()

    // 📝 Draft state
    val currentDraft by viewModel.currentDraft.collectAsState()
    val isDraftSaving by viewModel.isDraftSaving.collectAsState()

    // 📌 Group state (for pinned messages)
    val currentGroup by viewModel.currentGroup.collectAsState()

    // 🔍 Search state (for group search)
    var showSearchBar by remember { mutableStateOf(false) }
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searchTotalCount by viewModel.searchTotalCount.collectAsState()
    val currentSearchIndex by viewModel.currentSearchIndex.collectAsState()

    // 🔍 Media search state
    var showSearchTypeDialog by remember { mutableStateOf(false) }
    var showMediaSearch by remember { mutableStateOf(false) }

    var messageText by remember { mutableStateOf("") }

    // Загружаем черновик в messageText при изменении
    LaunchedEffect(currentDraft) {
        if (currentDraft.isNotEmpty() && messageText.isEmpty()) {
            messageText = currentDraft
        }
    }
    var showMediaOptions by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showStickerPicker by remember { mutableStateOf(false) }
    var showGifPicker by remember { mutableStateOf(false) }  // 🎬 GIF Picker
    var showLocationPicker by remember { mutableStateOf(false) }  // 📍 Location Picker
    var showContactPicker by remember { mutableStateOf(false) }  // 📇 Contact Picker
    var showStrapiPicker by remember { mutableStateOf(false) }  // 🛍️ Strapi Content Picker

    // 🎵 Вибір якості аудіо (як в Telegram: стиснутий/оригінальний)
    var showAudioQualityDialog by remember { mutableStateOf(false) }
    var pendingAudioFile by remember { mutableStateOf<java.io.File?>(null) }

    // 🎯 Режим введення (Swipeable як в Telegram/Viber)
    var currentInputMode by remember { mutableStateOf(InputMode.TEXT) }

    var isCurrentlyTyping by remember { mutableStateOf(false) }
    var selectedMessage by remember { mutableStateOf<Message?>(null) }
    var showContextMenu by remember { mutableStateOf(false) }
    var replyToMessage by remember { mutableStateOf<Message?>(null) }
    var editingMessage by remember { mutableStateOf<Message?>(null) }

    // 🗑️ Діалог підтвердження видалення (тільки для себе / для всіх)
    var messageToDelete by remember { mutableStateOf<Message?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // ✅ Режим множественного выбора
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedMessages by remember { mutableStateOf(setOf<Long>()) }

    // 📤 Пересилання повідомлень
    var showForwardDialog by remember { mutableStateOf(false) }
    var messageToForward by remember { mutableStateOf<Message?>(null) }
    val forwardContacts by viewModel.forwardContacts.collectAsState()
    val forwardGroups by viewModel.forwardGroups.collectAsState()

    // Завантажуємо контакти та групи при відкритті діалогу
    LaunchedEffect(showForwardDialog) {
        if (showForwardDialog) {
            viewModel.loadForwardContacts()
            viewModel.loadForwardGroups()
        }
    }

    // 👤 Меню профілю користувача (при кліку на ім'я в групі)
    var showUserProfileMenu by remember { mutableStateOf(false) }
    var selectedUserForMenu by remember { mutableStateOf<UserMenuData?>(null) }

    // ❤️ Быстрая реакция при двойном тапе
    var showQuickReaction by remember { mutableStateOf(false) }
    var quickReactionMessageId by remember { mutableStateOf<Long?>(null) }
    val defaultQuickReaction = rememberQuickReaction()  // Налаштовується в темах

    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val themeState = rememberThemeState()

    // 📝 Налаштування форматування тексту
    // Для особистих чатів - всі функції доступні
    // Для груп/каналів - беремо з налаштувань групи (якщо admin) або з permissions
    val formattingSettings = remember(isGroup, currentGroup) {
        val group = currentGroup  // Fix smart cast issue
        if (isGroup && group != null) {
            // Загружаем настройки из SharedPreferences
            try {
                val prefs = context.getSharedPreferences("group_formatting_prefs", android.content.Context.MODE_PRIVATE)
                val json = prefs.getString("formatting_${group.id}", null)
                if (json != null) {
                    val permissions = com.google.gson.Gson().fromJson(json, com.worldmates.messenger.ui.groups.GroupFormattingPermissions::class.java)
                    // Конвертируем GroupFormattingPermissions в FormattingSettings
                    // Админы имеют все права, участники - только разрешенные
                    if (group.isAdmin) {
                        FormattingSettings() // All permissions for admins
                    } else {
                        FormattingSettings(
                            allowMentions = permissions.membersCanUseMentions,
                            allowHashtags = permissions.membersCanUseHashtags,
                            allowBold = permissions.membersCanUseBold,
                            allowItalic = permissions.membersCanUseItalic,
                            allowCode = permissions.membersCanUseCode,
                            allowStrikethrough = permissions.membersCanUseStrikethrough,
                            allowUnderline = permissions.membersCanUseUnderline,
                            allowSpoilers = permissions.membersCanUseSpoilers,
                            allowQuotes = permissions.membersCanUseQuotes,
                            allowLinks = permissions.membersCanUseLinks
                        )
                    }
                } else {
                    FormattingSettings() // Default settings
                }
            } catch (e: Exception) {
                Log.e("MessagesScreen", "Error loading formatting settings", e)
                FormattingSettings() // Default on error
            }
        } else {
            // Особисті чати - всі функції доступні
            FormattingSettings()
        }
    }

    // 🔗 Обробники кліків на форматування
    val onMentionClick: (String) -> Unit = { username ->
        // Навігація до профілю користувача
        Log.d("MessagesScreen", "Клік на згадку: @$username")
        // TODO: Відкрити профіль користувача або показати меню
        // selectedUserForMenu = UserMenuData(username = username, ...)
        // showUserProfileMenu = true
    }

    val onHashtagClick: (String) -> Unit = { tag ->
        // Пошук повідомлень з цим хештегом
        Log.d("MessagesScreen", "Клік на хештег: #$tag")
        viewModel.setSearchQuery(tag)
        showSearchBar = true
    }

    val onLinkClick: (String) -> Unit = { url ->
        // Відкриття URL в браузері
        Log.d("MessagesScreen", "Клік на посилання: $url")
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("MessagesScreen", "Помилка відкриття URL: ${e.message}")
            android.widget.Toast.makeText(
                context,
                "Не вдалося відкрити посилання",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    // 📜 Auto-scroll для автоматичної прокрутки до нових повідомлень
    val listState = rememberLazyListState()

    // 🔥 КРИТИЧНО: Auto-scroll при додаванні нового повідомлення
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            // Прокрутити до останнього повідомлення (reversed, тому index 0)
            // Використовуємо animateScrollToItem для плавної анімації
            try {
                listState.animateScrollToItem(index = 0)
                Log.d("MessagesScreen", "✅ Auto-scrolled to latest message (index 0)")
            } catch (e: Exception) {
                Log.e("MessagesScreen", "❌ Auto-scroll error: ${e.message}")
            }
        }
    }

    // 📸 Галерея фото - збір всіх фото з чату
    var showImageGallery by remember { mutableStateOf(false) }
    var selectedImageIndex by remember { mutableStateOf(0) }
    // Для випадку коли imageUrls порожній, але клік по фото відбувся
    var clickedImageUrl by remember { mutableStateOf<String?>(null) }

    // 📹 Відеоповідомлення - показати рекордер камери
    var showVideoMessageRecorder by remember { mutableStateOf(false) }
    val imageUrls = remember(messages) {
        val urls = messages.mapNotNull { message ->
            // Перевіряємо тип повідомлення (підтримка різних форматів типу)
            val msgType = message.type?.lowercase() ?: ""
            val isImageType = msgType == "image" || msgType == "photo" ||
                    msgType.contains("image") || msgType == "right_image" ||
                    msgType == "left_image"

            // Шукаємо URL медіа в різних полях
            val mediaUrl = message.decryptedMediaUrl ?: message.mediaUrl ?: message.decryptedText

            if (mediaUrl != null && !mediaUrl.isBlank() && (isImageType || isImageUrl(mediaUrl))) {
                Log.d("MessagesScreen", "✅ Додано фото до галереї: $mediaUrl (тип: ${message.type})")
                mediaUrl
            } else {
                // Додатковий fallback: перевіряємо detectMediaType
                if (mediaUrl != null && !mediaUrl.isBlank()) {
                    val detectedType = detectMediaType(mediaUrl, message.type)
                    if (detectedType == "image") {
                        Log.d("MessagesScreen", "✅ Додано фото (через detectMediaType): $mediaUrl")
                        mediaUrl
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        }
        Log.d("MessagesScreen", "📸 Всього фото в галереї: ${urls.size}")
        urls
    }

    // 🎵 Мінімізований аудіо плеєр
    val playbackState by voicePlayer.playbackState.collectAsState()
    val currentPosition by voicePlayer.currentPosition.collectAsState()
    val duration by voicePlayer.duration.collectAsState()
    // Керуємо відображенням плеєра вручну, а не через playbackState
    var showMiniPlayer by remember { mutableStateOf(false) }

    // Оновлюємо showMiniPlayer при зміні playbackState
    LaunchedEffect(playbackState) {
        showMiniPlayer = playbackState !is com.worldmates.messenger.utils.VoicePlayer.PlaybackState.Idle
    }

    // Логування стану теми
    LaunchedEffect(themeState) {
        Log.d("MessagesScreen", "=== THEME STATE ===")
        Log.d("MessagesScreen", "Variant: ${themeState.variant}")
        Log.d("MessagesScreen", "IsDark: ${themeState.isDark}")
        Log.d("MessagesScreen", "BackgroundImageUri: ${themeState.backgroundImageUri}")
        Log.d("MessagesScreen", "PresetBackgroundId: ${themeState.presetBackgroundId}")
        Log.d("MessagesScreen", "==================")
    }

    // Управление индикатором "печатает" с автоматическим сбросом через 2 секунды
    LaunchedEffect(messageText) {
        if (messageText.isNotBlank() && !isCurrentlyTyping) {
            // Начали печатать
            viewModel.sendTypingStatus(true)
            isCurrentlyTyping = true
        } else if (messageText.isBlank() && isCurrentlyTyping) {
            // Очистили поле
            viewModel.sendTypingStatus(false)
            isCurrentlyTyping = false
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            Log.d("MessagesScreen", "Вибрано зображення: $it")
            val file = fileManager.copyUriToCache(it)
            if (file != null) {
                viewModel.uploadAndSendMedia(file, "image")
            } else {
                Log.e("MessagesScreen", "Не вдалося скопіювати зображення")
            }
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            Log.d("MessagesScreen", "Вибрано відео: $it")
            val file = fileManager.copyUriToCache(it)
            if (file != null) {
                viewModel.uploadAndSendMedia(file, "video")
            } else {
                Log.e("MessagesScreen", "Не вдалося скопіювати відео")
            }
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            Log.d("MessagesScreen", "Вибрано аудіо: $it")
            val file = fileManager.copyUriToCache(it)
            if (file != null) {
                pendingAudioFile = file
                showAudioQualityDialog = true
            } else {
                Log.e("MessagesScreen", "Не вдалося скопіювати аудіо файл")
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            Log.d("MessagesScreen", "Вибрано файл: $it")
            val file = fileManager.copyUriToCache(it)
            if (file != null) {
                viewModel.uploadAndSendMedia(file, "file")
            } else {
                Log.e("MessagesScreen", "Не вдалося скопіювати файл")
            }
        }
    }

    // Для выбора нескольких файлов (до 15 штук)
    val multipleFilesPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            if (uris.size > Constants.MAX_FILES_PER_MESSAGE) {
                Log.w("MessagesScreen", "Вибрано занадто багато файлів: ${uris.size}, макс: ${Constants.MAX_FILES_PER_MESSAGE}")
                android.widget.Toast.makeText(
                    context,
                    "Максимум ${Constants.MAX_FILES_PER_MESSAGE} файлів за раз",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } else {
                // Обробляємо множинні файли через viewModel
                Log.d("MessagesScreen", "Вибрано ${uris.size} файлів для завантаження")
                uris.forEach { uri ->
                    val file = fileManager.copyUriToCache(uri)
                    if (file != null) {
                        viewModel.uploadAndSendMedia(file, "file")
                    } else {
                        Log.e("MessagesScreen", "Не вдалося скопіювати файл: $uri")
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        // Застосування фону в залежності від налаштувань
        when {
            // Кастомне зображення
            themeState.backgroundImageUri != null -> {
                Log.d("MessagesScreen", "Applying custom background image: ${themeState.backgroundImageUri}")
                AsyncImage(
                    model = Uri.parse(themeState.backgroundImageUri),
                    contentDescription = "Chat background",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.3f  // Напівпрозорість для кращої читабельності
                )
            }
            // Preset градієнт
            themeState.presetBackgroundId != null -> {
                Log.d("MessagesScreen", "Applying preset background: ${themeState.presetBackgroundId}")
                val preset = PresetBackground.fromId(themeState.presetBackgroundId)
                if (preset != null) {
                    Log.d("MessagesScreen", "Preset found: ${preset.displayName}")
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.linearGradient(
                                    colors = preset.gradientColors.map { it.copy(alpha = 0.3f) }
                                )
                            )
                    )
                } else {
                    Log.e("MessagesScreen", "Preset not found for ID: ${themeState.presetBackgroundId}")
                }
            }
            // Стандартний фон з теми
            else -> {
                Log.d("MessagesScreen", "Using default MaterialTheme background")
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                )
            }
        }

        // Контент поверх фону
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding() // Автоматичний padding для клавіатури
                .navigationBarsPadding() // Padding для системних кнопок навігації
        ) {
            // Header
            MessagesHeaderBar(
                recipientName = recipientName,
                recipientAvatar = recipientAvatar,
                isOnline = isOnline,
                isTyping = isTyping,
                isRecording = isRecording,
                onBackPressed = onBackPressed,
                onUserProfileClick = {
                    Log.d("MessagesScreen", "Відкриваю профіль користувача: $recipientName")
                    // Відкриваємо профіль користувача
                    if (!isGroup) {
                        val intent = android.content.Intent(context, com.worldmates.messenger.ui.profile.UserProfileActivity::class.java).apply {
                            putExtra("user_id", viewModel.getRecipientId())
                        }
                        context.startActivity(intent)
                    } else {
                        // Для груп - відкриваємо деталі групи
                        val intent = android.content.Intent(context, com.worldmates.messenger.ui.groups.GroupDetailsActivity::class.java).apply {
                            putExtra("group_id", viewModel.getGroupId())
                        }
                        context.startActivity(intent)
                    }
                },
                onCallClick = {
                    // 📞 Аудіо дзвінок
                    val intent = android.content.Intent(context, com.worldmates.messenger.ui.calls.CallsActivity::class.java).apply {
                        putExtra("recipientId", viewModel.getRecipientId())
                        putExtra("recipientName", recipientName)
                        putExtra("recipientAvatar", recipientAvatar)
                        putExtra("callType", "audio")
                        putExtra("isGroup", isGroup)
                        if (isGroup) {
                            putExtra("groupId", viewModel.getGroupId())
                        }
                    }
                    context.startActivity(intent)
                    Log.d("MessagesScreen", "Запускаємо аудіо дзвінок до: $recipientName")
                },
                onVideoCallClick = {
                    // 📹 Відеодзвінок
                    val intent = android.content.Intent(context, com.worldmates.messenger.ui.calls.CallsActivity::class.java).apply {
                        putExtra("recipientId", viewModel.getRecipientId())
                        putExtra("recipientName", recipientName)
                        putExtra("recipientAvatar", recipientAvatar)
                        putExtra("callType", "video")
                        putExtra("isGroup", isGroup)
                        if (isGroup) {
                            putExtra("groupId", viewModel.getGroupId())
                        }
                    }
                    context.startActivity(intent)
                    Log.d("MessagesScreen", "Запускаємо відеодзвінок до: $recipientName")
                },
                onSearchClick = {
                    // Show search type dialog for both groups and personal chats
                    showSearchTypeDialog = true
                },
                onMuteClick = {
                    if (isGroup && currentGroup != null) {
                        // Для груп - перемикаємо сповіщення
                        if (currentGroup!!.isMuted) {
                            viewModel.unmuteGroup(
                                onSuccess = {
                                    android.widget.Toast.makeText(context, "Сповіщення увімкнено для $recipientName", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                onError = { error ->
                                    android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                        } else {
                            viewModel.muteGroup(
                                onSuccess = {
                                    android.widget.Toast.makeText(context, "Сповіщення вимкнено для $recipientName", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                onError = { error ->
                                    android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    } else {
                        // Для особистих чатів - TODO
                        Log.d("MessagesScreen", "Вимкнення сповіщень для: $recipientName")
                        android.widget.Toast.makeText(context, "Сповіщення вимкнено для $recipientName", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                onClearHistoryClick = {
                    Log.d("MessagesScreen", "Очищення історії чату")
                    viewModel.clearChatHistory(
                        onSuccess = {
                            android.widget.Toast.makeText(context, "Історію очищено", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        onError = { error ->
                            android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )
                },
                onChangeWallpaperClick = {
                    Log.d("MessagesScreen", "Відкриваю налаштування теми для зміни фону")
                    // Відкриваємо налаштування теми для вибору фону
                    val intent = android.content.Intent(context, com.worldmates.messenger.ui.theme.ThemeSettingsActivity::class.java)
                    context.startActivity(intent)
                },
                isMuted = if (isGroup) currentGroup?.isMuted == true else false,
                // 🔥 Group-specific parameters
                isGroup = isGroup,
                isGroupAdmin = currentGroup?.isAdmin == true || (isGroup && currentGroup?.let {
                    it.adminId == UserSession.userId
                } == true),
                onAddMembersClick = {
                    // Open add members dialog in group details
                    val intent = android.content.Intent(context, com.worldmates.messenger.ui.groups.GroupDetailsActivity::class.java).apply {
                        putExtra("group_id", viewModel.getGroupId())
                        putExtra("open_add_members", true)
                    }
                    context.startActivity(intent)
                },
                onCreateSubgroupClick = {
                    // Open group details with create subgroup dialog
                    val intent = android.content.Intent(context, com.worldmates.messenger.ui.groups.GroupDetailsActivity::class.java).apply {
                        putExtra("group_id", viewModel.getGroupId())
                        putExtra("open_create_subgroup", true)
                    }
                    context.startActivity(intent)
                },
                onGroupSettingsClick = {
                    // Open group settings
                    val intent = android.content.Intent(context, com.worldmates.messenger.ui.groups.GroupDetailsActivity::class.java).apply {
                        putExtra("group_id", viewModel.getGroupId())
                    }
                    context.startActivity(intent)
                },
                // 🔥 Параметри режиму вибору
                isSelectionMode = isSelectionMode,
                selectedCount = selectedMessages.size,
                totalCount = messages.size,
                canEdit = selectedMessages.size == 1 && messages.find { it.id == selectedMessages.first() }?.fromId == UserSession.userId,
                canPin = selectedMessages.size == 1 && (
                        (isGroup && (currentGroup?.isAdmin == true || currentGroup?.isModerator == true)) ||
                                !isGroup  // В особистих чатах будь-хто може закріпити повідомлення
                        ),
                onSelectAll = {
                    // Вибираємо всі повідомлення
                    selectedMessages = messages.map { it.id }.toSet()
                },
                onEditSelected = {
                    // Редагуємо вибране повідомлення
                    if (selectedMessages.size == 1) {
                        val messageToEdit = messages.find { it.id == selectedMessages.first() }
                        if (messageToEdit != null && messageToEdit.fromId == UserSession.userId) {
                            editingMessage = messageToEdit
                            messageText = messageToEdit.decryptedText ?: ""
                            isSelectionMode = false
                            selectedMessages = emptySet()
                            android.widget.Toast.makeText(context, "Редагування повідомлення", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onPinSelected = {
                    // Закріплюємо вибране повідомлення
                    if (selectedMessages.size == 1) {
                        val messageId = selectedMessages.first()
                        if (isGroup) {
                            viewModel.pinGroupMessage(
                                messageId = messageId,
                                onSuccess = {
                                    android.widget.Toast.makeText(context, "Повідомлення закріплено", android.widget.Toast.LENGTH_SHORT).show()
                                    isSelectionMode = false
                                    selectedMessages = emptySet()
                                },
                                onError = { error ->
                                    android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                        } else {
                            viewModel.pinPrivateMessage(messageId, true)
                            android.widget.Toast.makeText(context, "Повідомлення закріплено", android.widget.Toast.LENGTH_SHORT).show()
                            isSelectionMode = false
                            selectedMessages = emptySet()
                        }
                    }
                },
                onDeleteSelected = {
                    // Видаляємо вибрані повідомлення
                    selectedMessages.forEach { messageId ->
                        viewModel.deleteMessage(messageId)
                    }
                    android.widget.Toast.makeText(context, "Видалено ${selectedMessages.size} повідомлень", android.widget.Toast.LENGTH_SHORT).show()
                    isSelectionMode = false
                    selectedMessages = emptySet()
                },
                onCloseSelectionMode = {
                    // Закриваємо режим вибору
                    isSelectionMode = false
                    selectedMessages = emptySet()
                }
            )

            // 📶 Connection Quality Banner (показується при поганому з'єднанні)
            ConnectionQualityBanner(quality = connectionQuality)

            // 📌 Pinned Message Banner (for groups only)
            if (isGroup && currentGroup?.pinnedMessage != null) {
                val pinnedMsg = currentGroup!!.pinnedMessage!!
                val decryptedText = pinnedMsg.decryptedText ?: pinnedMsg.encryptedText ?: ""

                // Перевіряємо чи є користувач адміном/модератором
                val canUnpin = currentGroup?.isAdmin == true || currentGroup?.isModerator == true

                PinnedMessageBanner(
                    pinnedMessage = pinnedMsg,
                    decryptedText = decryptedText,
                    onBannerClick = {
                        // Прокручуємо до закріпленого повідомлення
                        val messageIndex = messages.indexOfFirst { it.id == pinnedMsg.id }
                        if (messageIndex != -1) {
                            // Реверсимо індекс, оскільки LazyColumn має reverseLayout = true
                            val reversedIndex = messages.size - messageIndex - 1
                            scope.launch {
                                listState.animateScrollToItem(reversedIndex)
                            }
                            android.widget.Toast.makeText(
                                context,
                                "Переміщення до закріпленого повідомлення",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            android.widget.Toast.makeText(
                                context,
                                "Закріплене повідомлення не знайдено в історії",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onUnpinClick = {
                        viewModel.unpinGroupMessage(
                            onSuccess = {
                                android.widget.Toast.makeText(
                                    context,
                                    "Повідомлення відкріплено",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            },
                            onError = { error ->
                                android.widget.Toast.makeText(
                                    context,
                                    error,
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    },
                    canUnpin = canUnpin
                )
            }

            // 📌 Pinned Message Banner для особистих чатів
            if (!isGroup && pinnedPrivateMessage != null) {
                val pinnedMsg = pinnedPrivateMessage!!
                val pinnedText = pinnedMsg.decryptedText ?: pinnedMsg.encryptedText ?: ""
                PinnedMessageBanner(
                    pinnedMessage = pinnedMsg,
                    decryptedText = pinnedText,
                    onBannerClick = {
                        val messageIndex = messages.indexOfFirst { it.id == pinnedMsg.id }
                        if (messageIndex != -1) {
                            val reversedIndex = messages.size - messageIndex - 1
                            scope.launch { listState.animateScrollToItem(reversedIndex) }
                        }
                    },
                    onUnpinClick = {
                        viewModel.pinPrivateMessage(pinnedMsg.id, false)
                        android.widget.Toast.makeText(context, "Повідомлення відкріплено", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    canUnpin = true
                )
            }

            // 🔍 Search Bar (for both groups and private chats)
            GroupSearchBar(
                visible = showSearchBar,
                query = searchQuery,
                onQueryChange = { query ->
                    if (isGroup) {
                        viewModel.searchGroupMessages(query)
                    } else {
                        viewModel.searchPrivateMessages(query)
                    }
                },
                searchResultsCount = searchTotalCount,
                currentResultIndex = currentSearchIndex,
                onNextResult = {
                    viewModel.nextSearchResult()
                    // Scroll to next result
                    if (searchResults.isNotEmpty() && currentSearchIndex >= 0) {
                        val nextMessage = searchResults[currentSearchIndex]
                        val messageIndex = messages.indexOfFirst { it.id == nextMessage.id }
                        if (messageIndex != -1) {
                            val reversedIndex = messages.size - messageIndex - 1
                            scope.launch {
                                listState.animateScrollToItem(reversedIndex)
                            }
                        }
                    }
                },
                onPreviousResult = {
                    viewModel.previousSearchResult()
                    // Scroll to previous result
                    if (searchResults.isNotEmpty() && currentSearchIndex >= 0) {
                        val prevMessage = searchResults[currentSearchIndex]
                        val messageIndex = messages.indexOfFirst { it.id == prevMessage.id }
                        if (messageIndex != -1) {
                            val reversedIndex = messages.size - messageIndex - 1
                            scope.launch {
                                listState.animateScrollToItem(reversedIndex)
                            }
                        }
                    }
                },
                onClose = {
                    showSearchBar = false
                    viewModel.clearSearch()
                }
            )

            // 🔍 Search Type Dialog
            if (showSearchTypeDialog) {
                AlertDialog(
                    onDismissRequest = { showSearchTypeDialog = false },
                    title = { Text("Виберіть тип пошуку") },
                    text = {
                        Column {
                            Text("Текстовий пошук — пошук за вмістом повідомлень")
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Медіа пошук — пошук файлів (фото, відео, аудіо)")
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showSearchTypeDialog = false
                                showMediaSearch = true
                            }
                        ) {
                            Text("Медіа пошук")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showSearchTypeDialog = false
                                showSearchBar = true
                            }
                        ) {
                            Text("Текстовий пошук")
                        }
                    }
                )
            }

            // 🔍 Media Search Screen
            if (showMediaSearch) {
                MediaSearchScreen(
                    chatId = if (!isGroup) viewModel.getRecipientId() else null,
                    groupId = if (isGroup) viewModel.getGroupId() else null,
                    onDismiss = { showMediaSearch = false },
                    onMediaClick = { message ->
                        // Handle media click - open in gallery/video player
                        when (message.type) {
                            "image" -> {
                                val mediaUrl = message.decryptedMediaUrl ?: message.mediaUrl
                                if (mediaUrl != null && imageUrls.contains(mediaUrl)) {
                                    // Find image in existing gallery and show it
                                    selectedImageIndex = imageUrls.indexOf(mediaUrl).coerceAtLeast(0)
                                    showImageGallery = true
                                    showMediaSearch = false
                                } else {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Прокрутите чат, чтобы увидеть это изображение",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            "video" -> {
                                // Videos are shown inline - scroll to message or show toast
                                android.widget.Toast.makeText(
                                    context,
                                    "Прокрутите чат, чтобы воспроизвести видео",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                showMediaSearch = false
                            }
                            else -> {
                                android.widget.Toast.makeText(
                                    context,
                                    "Открытие ${message.type} файлов - в разработке",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )
            }

            // Messages List
            LazyColumn(
                state = listState,  // 🔥 Додано для auto-scroll
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                reverseLayout = true
            ) {
                items(
                    items = messages.reversed(),
                    key = { it.id }
                ) { message ->
                    // ✨ Анімація появи повідомлення
                    AnimatedVisibility(
                        visible = true,
                        enter = slideInVertically(
                            initialOffsetY = { it / 4 }
                        ) + fadeIn(
                            initialAlpha = 0.3f
                        ),
                        modifier = Modifier.animateItem()
                    ) {
                        MessageBubbleComposable(
                            message = message,
                            voicePlayer = voicePlayer,
                            replyToMessage = replyToMessage,
                            onLongPress = {
                                // 🔥 Активуємо режим вибору при довгому натисканні
                                if (!isSelectionMode) {
                                    isSelectionMode = true
                                    // 📳 Вібрація при активації
                                    performSelectionVibration(context)
                                }
                            },
                            onImageClick = { imageUrl ->
                                Log.d("MessagesScreen", "🖼️ onImageClick викликано! URL: $imageUrl")
                                Log.d("MessagesScreen", "📋 Всього imageUrls: ${imageUrls.size}")
                                // Зберігаємо URL натиснутого фото (fallback якщо галерея порожня)
                                clickedImageUrl = imageUrl
                                // Знаходимо індекс вибраного фото в списку
                                selectedImageIndex = imageUrls.indexOf(imageUrl).coerceAtLeast(0)
                                showImageGallery = true
                                Log.d("MessagesScreen", "🎬 showImageGallery = true")
                            },
                            onReply = { msg ->
                                // Встановлюємо повідомлення для відповіді
                                replyToMessage = msg
                            },
                            onToggleReaction = { messageId, emoji ->
                                viewModel.toggleReaction(messageId, emoji)
                            },
                            // 🔥 Нові параметри для режиму вибору
                            isSelectionMode = isSelectionMode,
                            isSelected = selectedMessages.contains(message.id),
                            onToggleSelection = { messageId ->
                                selectedMessages = if (selectedMessages.contains(messageId)) {
                                    selectedMessages - messageId
                                } else {
                                    selectedMessages + messageId
                                }
                                // Якщо нічого не вибрано - виходимо з режиму
                                if (selectedMessages.isEmpty()) {
                                    isSelectionMode = false
                                }
                            },
                            onDoubleTap = { messageId ->
                                // ❤️ Швидка реакція при подвійному тапі
                                quickReactionMessageId = messageId
                                showQuickReaction = true
                                // Додаємо реакцію
                                viewModel.toggleReaction(messageId, defaultQuickReaction)
                                // Ховаємо анімацію через 1 секунду
                                scope.launch {
                                    kotlinx.coroutines.delay(1000)
                                    showQuickReaction = false
                                }
                            },
                            // 👤 Параметри для відображення імені в групових чатах
                            isGroup = isGroup,
                            onSenderNameClick = { senderId ->
                                // Шукаємо повідомлення з цим відправником для отримання даних
                                val senderMessage = messages.find { it.fromId == senderId }
                                selectedUserForMenu = UserMenuData(
                                    userId = senderId,
                                    username = senderMessage?.senderName ?: "User",
                                    name = senderMessage?.senderName,
                                    avatar = senderMessage?.senderAvatar,
                                    isVerified = false,
                                    isOnline = false
                                )
                                showUserProfileMenu = true
                            },
                            // 📝 Параметри для форматування тексту
                            formattingSettings = formattingSettings,
                            onMentionClick = onMentionClick,
                            onHashtagClick = onHashtagClick,
                            onLinkClick = onLinkClick,
                            viewModel = viewModel
                        )
                    }  // Закриття AnimatedVisibility
                }
            }

            // 📸 ГАЛЕРЕЯ ФОТО
            var showPhotoEditor by remember { mutableStateOf(false) }
            var editImageUrl by remember { mutableStateOf<String?>(null) }

            if (showImageGallery && !showPhotoEditor) {
                if (imageUrls.isNotEmpty()) {
                    Log.d("MessagesScreen", "✅ Показуємо ImageGalleryViewer! URLs: ${imageUrls.size}, page: $selectedImageIndex")
                    ImageGalleryViewer(
                        imageUrls = imageUrls,
                        initialPage = selectedImageIndex,
                        onDismiss = {
                            Log.d("MessagesScreen", "❌ Закриваємо галерею")
                            showImageGallery = false
                            clickedImageUrl = null
                        },
                        onEdit = { imageUrl ->
                            Log.d("MessagesScreen", "✏️ Відкриваємо редактор для: $imageUrl")
                            editImageUrl = imageUrl
                            showImageGallery = false
                            showPhotoEditor = true
                        }
                    )
                } else if (clickedImageUrl != null) {
                    // Fallback: якщо imageUrls порожній, відкриваємо одне фото
                    Log.d("MessagesScreen", "📸 Fallback: показуємо FullscreenImageViewer для: $clickedImageUrl")
                    com.worldmates.messenger.ui.media.FullscreenImageViewer(
                        imageUrl = clickedImageUrl!!,
                        onDismiss = {
                            showImageGallery = false
                            clickedImageUrl = null
                        },
                        onEdit = { imageUrl ->
                            editImageUrl = imageUrl
                            showImageGallery = false
                            showPhotoEditor = true
                        }
                    )
                } else {
                    // Нічого показати
                    Log.e("MessagesScreen", "⚠️ showImageGallery=true але imageUrls та clickedImageUrl порожні!")
                    showImageGallery = false
                }
            }

            // 🎨 ФОТОРЕДАКТОР
            if (showPhotoEditor && editImageUrl != null) {
                com.worldmates.messenger.ui.editor.PhotoEditorScreen(
                    imageUrl = editImageUrl!!,
                    onDismiss = {
                        showPhotoEditor = false
                        editImageUrl = null
                    },
                    onSave = { savedFile ->
                        android.widget.Toast.makeText(
                            context,
                            "Фото збережено: ${savedFile.name}",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        showPhotoEditor = false
                        editImageUrl = null
                    }
                )
            }

            // 📹 ВІДЕОПОВІДОМЛЕННЯ РЕКОРДЕР
            if (showVideoMessageRecorder) {
                Log.d("MessagesScreen", "✅ Показуємо VideoMessageRecorder!")
                VideoMessageRecorder(
                    maxDurationSeconds = 120,  // 2 хвилини для звичайних користувачів
                    isPremiumUser = false,     // TODO: перевірити статус преміум
                    onVideoRecorded = { videoFile ->
                        Log.d("MessagesScreen", "📹 Відео записано: ${videoFile.absolutePath}")
                        showVideoMessageRecorder = false
                        // Відправити відеоповідомлення
                        viewModel.uploadAndSendMedia(videoFile, "video")
                    },
                    onCancel = {
                        Log.d("MessagesScreen", "❌ Запис відео скасовано")
                        showVideoMessageRecorder = false
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Message Context Menu Bottom Sheet
            if (showContextMenu && selectedMessage != null) {
                MessageContextMenu(
                    message = selectedMessage!!,
                    onDismiss = {
                        showContextMenu = false
                        selectedMessage = null
                    },
                    onReply = { message ->
                        replyToMessage = message
                        showContextMenu = false
                        selectedMessage = null
                    },
                    onEdit = { message ->
                        val text = message.decryptedText ?: ""
                        val trimmedText = text.trim()
                        // Не ставимо URL медіа в текстове поле
                        val isUrl = (trimmedText.startsWith("http://") || trimmedText.startsWith("https://") || trimmedText.startsWith("upload/")) &&
                                !trimmedText.contains(" ") && !trimmedText.contains("\n")
                        if (!isUrl) {
                            editingMessage = message
                            messageText = text
                        }
                        showContextMenu = false
                        selectedMessage = null
                    },
                    onForward = { message ->
                        // Відкриваємо діалог вибору чату для пересилання
                        messageToForward = message
                        showForwardDialog = true
                        showContextMenu = false
                        selectedMessage = null
                    },
                    onDelete = { message ->
                        showContextMenu = false
                        selectedMessage = null
                        // Для своїх повідомлень у приватному чаті — питаємо "для мене" чи "для всіх"
                        if (!isGroup && message.fromId == UserSession.userId) {
                            messageToDelete = message
                            showDeleteDialog = true
                        } else {
                            viewModel.deleteMessage(message.id, "just_me")
                        }
                    },
                    onPin = { message ->
                        viewModel.pinPrivateMessage(message.id, true)
                        showContextMenu = false
                        selectedMessage = null
                        android.widget.Toast.makeText(context, "Повідомлення закріплено", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    isPrivateChat = !isGroup,
                    onCopy = { message ->
                        message.decryptedText?.let {
                            clipboardManager.setText(AnnotatedString(it))
                            android.widget.Toast.makeText(
                                context,
                                "Текст скопійовано",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                        showContextMenu = false
                        selectedMessage = null
                    }
                )
            }

            // 🗑️ Діалог підтвердження видалення повідомлення
            if (showDeleteDialog && messageToDelete != null) {
                AlertDialog(
                    onDismissRequest = {
                        showDeleteDialog = false
                        messageToDelete = null
                    },
                    title = { Text("Видалити повідомлення") },
                    text = { Text("Видалити повідомлення для всіх учасників чату або тільки для себе?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.deleteMessage(messageToDelete!!.id, "everyone")
                                showDeleteDialog = false
                                messageToDelete = null
                            }
                        ) {
                            Text("Видалити для всіх", color = Color(0xFFD32F2F))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                viewModel.deleteMessage(messageToDelete!!.id, "just_me")
                                showDeleteDialog = false
                                messageToDelete = null
                            }
                        ) {
                            Text("Видалити для мене")
                        }
                    }
                )
            }

            // 👤 User Profile Menu (при кліку на ім'я в групі)
            if (showUserProfileMenu && selectedUserForMenu != null) {
                UserProfileMenuSheet(
                    user = selectedUserForMenu!!,
                    onDismiss = {
                        showUserProfileMenu = false
                        selectedUserForMenu = null
                    },
                    onAction = { action ->
                        when (action) {
                            is UserMenuAction.ViewProfile -> {
                                // Відкриваємо повний профіль
                                context.startActivity(
                                    android.content.Intent(context, com.worldmates.messenger.ui.profile.UserProfileActivity::class.java).apply {
                                        putExtra("user_id", selectedUserForMenu?.userId)
                                    }
                                )
                            }
                            is UserMenuAction.SendMessage -> {
                                // Відкриваємо приватний чат з користувачем
                                context.startActivity(
                                    android.content.Intent(context, com.worldmates.messenger.ui.messages.MessagesActivity::class.java).apply {
                                        putExtra("recipient_id", selectedUserForMenu?.userId)
                                        putExtra("recipient_name", selectedUserForMenu?.name ?: selectedUserForMenu?.username)
                                        putExtra("recipient_avatar", selectedUserForMenu?.avatar ?: "")
                                    }
                                )
                            }
                            is UserMenuAction.CopyUsername -> {
                                // Копіюємо username
                                clipboardManager.setText(AnnotatedString("@${selectedUserForMenu?.username}"))
                                android.widget.Toast.makeText(context, "Username скопійовано", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            else -> {
                                // Інші дії - показуємо toast
                                android.widget.Toast.makeText(context, "Дія: $action", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                        showUserProfileMenu = false
                        selectedUserForMenu = null
                    },
                    showChatOptions = false
                )
            }

            // Upload Progress
            if (uploadProgress > 0 && uploadProgress < 100) {
                LinearProgressIndicator(
                    progress = uploadProgress / 100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                )
            }

            // Reply Indicator
            ReplyIndicator(
                replyToMessage = replyToMessage,
                onCancelReply = { replyToMessage = null }
            )

            // Edit Indicator
            EditIndicator(
                editingMessage = editingMessage,
                onCancelEdit = {
                    editingMessage = null
                    messageText = ""
                    viewModel.updateDraftText("") // Явно очищаємо черновик
                }
            )

            // 🎵 Мінімізований аудіо плеєр (новий, через MusicPlaybackService)
            val musicServiceTrack by com.worldmates.messenger.services.MusicPlaybackService.currentTrackInfo.collectAsState()
            var showExpandedMusicPlayer by remember { mutableStateOf(false) }

            if (musicServiceTrack.url.isNotEmpty()) {
                com.worldmates.messenger.ui.music.MusicMiniBar(
                    onExpand = { showExpandedMusicPlayer = true },
                    onStop = { /* сервіс зупинено */ }
                )
            }

            // Повноекранний плеєр з міні-бара
            if (showExpandedMusicPlayer && musicServiceTrack.url.isNotEmpty()) {
                com.worldmates.messenger.ui.music.AdvancedMusicPlayer(
                    audioUrl = musicServiceTrack.url,
                    title = musicServiceTrack.title,
                    artist = musicServiceTrack.artist,
                    onDismiss = { showExpandedMusicPlayer = false }
                )
            }

            // 🔥 Нижня панель дій (режим вибору)
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.BottomCenter
            ) {
                if (isSelectionMode) {
                    SelectionBottomBar(
                        selectedCount = selectedMessages.size,
                        onForward = {
                            // Відкриваємо діалог вибору отримувачів
                            showForwardDialog = true
                        },
                        onReply = {
                            // Відповідаємо на вибране повідомлення
                            if (selectedMessages.size == 1) {
                                val messageId = selectedMessages.first()
                                replyToMessage = messages.find { it.id == messageId }
                                isSelectionMode = false
                                selectedMessages = emptySet()
                            }
                        }
                    )
                }
            }

            // ❤️ Анімація швидкої реакції
            if (showQuickReaction) {
                QuickReactionAnimation(
                    visible = showQuickReaction,
                    emoji = defaultQuickReaction,
                    onAnimationEnd = {
                        showQuickReaction = false
                        quickReactionMessageId = null
                    }
                )
            }

            // Message Input (ховається в режимі вибору)
            if (!isSelectionMode) {
                MessageInputBar(
                    currentInputMode = currentInputMode,
                    onInputModeChange = { newMode ->
                        currentInputMode = newMode
                        // Автоматично відкриваємо відповідні пікери
                        when (newMode) {
                            InputMode.EMOJI -> {
                                showEmojiPicker = true
                                showStickerPicker = false
                                showGifPicker = false
                            }
                            InputMode.STICKER -> {
                                showEmojiPicker = false
                                showStickerPicker = true
                                showGifPicker = false
                            }
                            InputMode.GIF -> {
                                showEmojiPicker = false
                                showStickerPicker = false
                                showGifPicker = true
                            }
                            else -> {
                                showEmojiPicker = false
                                showStickerPicker = false
                                showGifPicker = false
                            }
                        }
                    },
                    messageText = messageText,
                    onMessageChange = {
                        messageText = it
                        viewModel.updateDraftText(it) // Автосохранение черновика
                    },
                    onSendClick = {
                        if (messageText.isNotBlank()) {
                            if (editingMessage != null) {
                                // 🧪 ТЕСТОВЕ ПОВІДОМЛЕННЯ
                                android.widget.Toast.makeText(
                                    context,
                                    "💾 Зберігаю зміни для повідомлення ID: ${editingMessage!!.id}",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                                // Редагуємо повідомлення
                                viewModel.editMessage(editingMessage!!.id, messageText)
                                messageText = ""
                                viewModel.updateDraftText("") // Явно очищаємо черновик
                                editingMessage = null
                            } else {
                                // Надсилаємо нове повідомлення
                                viewModel.sendMessage(messageText, replyToMessage?.id)
                                messageText = ""
                                viewModel.updateDraftText("") // Явно очищаємо черновик
                                replyToMessage = null  // Очищаємо reply після відправки
                            }
                        }
                    },
                    isLoading = isLoading,
                    recordingState = recordingState,
                    recordingDuration = recordingDuration,
                    voiceRecorder = voiceRecorder,
                    onStartVoiceRecord = {
                        // Перевіряємо permission перед записом
                        if (onRequestAudioPermission()) {
                            scope.launch {
                                voiceRecorder.startRecording()
                                // Повідомляємо співрозмовника що ми записуємо голосове
                                if (!isGroup) viewModel.sendRecordingStatus()
                            }
                        }
                    },
                    onCancelVoiceRecord = {
                        scope.launch {
                            voiceRecorder.cancelRecording()
                        }
                    },
                    onStopVoiceRecord = {
                        scope.launch {
                            val stopped = voiceRecorder.stopRecording()
                            if (stopped && voiceRecorder.recordingState.value is VoiceRecorder.RecordingState.Completed) {
                                val filePath = (voiceRecorder.recordingState.value as VoiceRecorder.RecordingState.Completed).filePath
                                viewModel.uploadAndSendMedia(java.io.File(filePath), "voice")
                            }
                        }
                    },
                    onShowMediaOptions = { showMediaOptions = !showMediaOptions },
                    onPickImage = { imagePickerLauncher.launch("image/*") },
                    onPickVideo = { videoPickerLauncher.launch("video/*") },  // Галерея відео
                    onPickAudio = { audioPickerLauncher.launch("audio/*") },
                    onPickFile = { filePickerLauncher.launch("*/*") },
                    onCameraClick = { imagePickerLauncher.launch("image/*") },  // Поки що також галерея
                    onVideoCameraClick = {
                        if (onRequestVideoPermissions()) {
                            showVideoMessageRecorder = true
                        }
                    },
                    showMediaOptions = showMediaOptions,
                    showEmojiPicker = showEmojiPicker,
                    onToggleEmojiPicker = { showEmojiPicker = !showEmojiPicker },
                    showStickerPicker = showStickerPicker,
                    onToggleStickerPicker = { showStickerPicker = !showStickerPicker },
                    showGifPicker = showGifPicker,
                    onToggleGifPicker = { showGifPicker = !showGifPicker },
                    showLocationPicker = showLocationPicker,
                    onToggleLocationPicker = { showLocationPicker = !showLocationPicker },
                    showContactPicker = showContactPicker,
                    onToggleContactPicker = { showContactPicker = !showContactPicker },
                    showStrapiPicker = showStrapiPicker,
                    onToggleStrapiPicker = { showStrapiPicker = !showStrapiPicker },
                    onRequestAudioPermission = onRequestAudioPermission,
                    onRequestVideoPermissions = onRequestVideoPermissions,
                    viewModel = viewModel,
                    formattingSettings = formattingSettings
                )

                // 💾 Draft saving indicator
                if (isDraftSaving && messageText.isNotEmpty()) {
                    androidx.compose.foundation.layout.Box(
                        modifier = androidx.compose.ui.Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        contentAlignment = androidx.compose.ui.Alignment.CenterEnd
                    ) {
                        androidx.compose.material3.Text(
                            text = "💾 Сохраняется...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }  // Закриття if (!isSelectionMode)

            // 😊 Emoji Picker
            if (showEmojiPicker) {
                com.worldmates.messenger.ui.components.EmojiPicker(
                    onEmojiSelected = { emoji ->
                        messageText += emoji
                        // Не закриваємо picker автоматично, щоб можна було вибрати кілька емоджі
                    },
                    onDismiss = { showEmojiPicker = false }
                )
            }

            // 🎭 Sticker Picker
            if (showStickerPicker) {
                com.worldmates.messenger.ui.components.StickerPicker(
                    onStickerSelected = { sticker ->
                        viewModel.sendSticker(sticker.id)
                        showStickerPicker = false
                    },
                    onDismiss = { showStickerPicker = false }
                )
            }

            // 🎬 GIF Picker
            if (showGifPicker) {
                com.worldmates.messenger.ui.components.GifPicker(
                    onGifSelected = { gifUrl ->
                        viewModel.sendGif(gifUrl)
                        showGifPicker = false
                    },
                    onDismiss = { showGifPicker = false }
                )
            }

            // 📍 Location Picker
            if (showLocationPicker) {
                com.worldmates.messenger.ui.components.LocationPicker(
                    onLocationSelected = { locationData ->
                        viewModel.sendLocation(locationData)
                        showLocationPicker = false
                    },
                    onDismiss = { showLocationPicker = false }
                )
            }

            // 📇 Contact Picker
            if (showContactPicker) {
                com.worldmates.messenger.ui.components.ContactPicker(
                    onContactSelected = { contact ->
                        viewModel.sendContact(contact)
                        showContactPicker = false
                    },
                    onDismiss = { showContactPicker = false }
                )
            }

            // 🛍️ Strapi Content Picker (стікери/GIF/емодзі з Strapi CMS)
            if (showStrapiPicker) {
                com.worldmates.messenger.ui.strapi.StrapiContentPicker(
                    onItemSelected = { contentUrl ->
                        // Відправляємо стікер/GIF з Strapi як медіа
                        viewModel.sendGif(contentUrl)
                        showStrapiPicker = false
                    },
                    onDismiss = { showStrapiPicker = false }
                )
            }

            // 🎵 Діалог якості аудіо (як в Telegram: стиснутий/оригінальний)
            if (showAudioQualityDialog && pendingAudioFile != null) {
                AudioQualityDialog(
                    fileName = pendingAudioFile!!.name,
                    fileSize = pendingAudioFile!!.length(),
                    onSendOriginal = {
                        viewModel.uploadAndSendMedia(pendingAudioFile!!, "audio")
                        showAudioQualityDialog = false
                        pendingAudioFile = null
                    },
                    onSendCompressed = {
                        viewModel.uploadAndSendMedia(pendingAudioFile!!, "voice")
                        showAudioQualityDialog = false
                        pendingAudioFile = null
                    },
                    onDismiss = {
                        showAudioQualityDialog = false
                        pendingAudioFile = null
                    }
                )
            }

            // 📤 Діалог пересилання повідомлень
            ForwardMessageDialog(
                visible = showForwardDialog,
                contacts = forwardContacts,  // Реальні дані з ViewModel
                groups = forwardGroups,      // Реальні дані з ViewModel
                selectedCount = selectedMessages.size,
                onForward = { recipientIds ->
                    // Викликаємо метод ViewModel для пересилання
                    viewModel.forwardMessages(selectedMessages, recipientIds)

                    android.widget.Toast.makeText(
                        context,
                        "✅ Переслано ${selectedMessages.size} повідомлень до ${recipientIds.size} отримувачів",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()

                    // Виходимо з режиму вибору
                    isSelectionMode = false
                    selectedMessages = emptySet()
                },
                onDismiss = { showForwardDialog = false }
            )
        }  // Кінець Column
    }  // Кінець Box
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesHeaderBar(
    recipientName: String,
    recipientAvatar: String,
    isOnline: Boolean,
    isTyping: Boolean,
    isRecording: Boolean = false,
    onBackPressed: () -> Unit,
    onUserProfileClick: () -> Unit = {},
    onCallClick: () -> Unit = {},
    onVideoCallClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onMuteClick: () -> Unit = {},
    onClearHistoryClick: () -> Unit = {},
    onChangeWallpaperClick: () -> Unit = {},
    onBlockClick: () -> Unit = {},
    isUserBlocked: Boolean = false,
    isMuted: Boolean = false,
    // 🔥 Group-specific parameters
    isGroup: Boolean = false,
    isGroupAdmin: Boolean = false,
    onCreateSubgroupClick: () -> Unit = {},
    onAddMembersClick: () -> Unit = {},
    onGroupSettingsClick: () -> Unit = {},
    // 🔥 Параметри для режиму вибору
    isSelectionMode: Boolean = false,
    selectedCount: Int = 0,
    totalCount: Int = 0,
    canEdit: Boolean = false,
    canPin: Boolean = false,
    onEditSelected: () -> Unit = {},
    onPinSelected: () -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    onSelectAll: () -> Unit = {},
    onCloseSelectionMode: () -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    var showUserMenu by remember { mutableStateOf(false) }

    // Telegram-style AppBar - четкий и читаемый
    TopAppBar(
        title = {
            // 🔥 В режимі вибору показуємо кількість вибраних
            if (isSelectionMode) {
                Text(
                    text = "$selectedCount вибрано",
                    color = colorScheme.onPrimary,
                    fontSize = 20.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxHeight()
                        .clickable { onUserProfileClick() }
                ) {
                    // Аватар с индикатором онлайн-статуса
                    if (recipientAvatar.isNotEmpty()) {
                        Box {
                            AsyncImage(
                                model = recipientAvatar,
                                contentDescription = recipientName,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            // Зелёная/серая точка онлайн-статуса
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .align(Alignment.BottomEnd)
                                    .clip(CircleShape)
                                    .background(if (isOnline) Color(0xFF4CAF50) else Color.Gray)
                                    .border(2.dp, Color.White, CircleShape)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    // Ім'я та статус ("печатає", "записує голосове" тощо)
                    Column {
                        Text(recipientName, color = colorScheme.onPrimary)
                        when {
                            isRecording -> Text(
                                text = "пише голосове...",
                                fontSize = 12.sp,
                                color = colorScheme.onPrimary.copy(alpha = 0.8f)
                            )
                            isTyping -> Text(
                                text = "печатає...",
                                fontSize = 12.sp,
                                color = colorScheme.onPrimary.copy(alpha = 0.8f)
                            )
                            isOnline -> Text(
                                text = "онлайн",
                                fontSize = 12.sp,
                                color = colorScheme.onPrimary.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBackPressed) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Назад",
                    tint = colorScheme.onPrimary
                )
            }
        },
        actions = {
            // 🔥 Режим вибору - показуємо кнопки дій
            if (isSelectionMode) {
                SelectionTopBarActions(
                    selectedCount = selectedCount,
                    totalCount = totalCount,
                    canEdit = canEdit,
                    canPin = canPin,
                    onEdit = onEditSelected,
                    onPin = onPinSelected,
                    onDelete = onDeleteSelected,
                    onSelectAll = onSelectAll,
                    onClose = onCloseSelectionMode
                )
            } else {
                // Звичайні кнопки
                // Кнопка пошуку
                IconButton(onClick = onSearchClick) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Пошук",
                        tint = colorScheme.onPrimary
                    )
                }

                // Кнопка дзвінка
                IconButton(onClick = onCallClick) {
                    Icon(
                        Icons.Default.Call,
                        contentDescription = "Дзвінок",
                        tint = colorScheme.onPrimary
                    )
                }

                // Кнопка меню (3 крапки)
                Box {
                    IconButton(onClick = { showUserMenu = !showUserMenu }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Більше",
                            tint = colorScheme.onPrimary
                        )
                    }

                    // Випадаюче меню
                    DropdownMenu(
                        expanded = showUserMenu,
                        onDismissRequest = { showUserMenu = false }
                    ) {
                        // ✅ Common options for both groups and users
                        DropdownMenuItem(
                            text = { Text(if (isGroup) "Деталі групи" else "Переглянути профіль") },
                            onClick = {
                                showUserMenu = false
                                onUserProfileClick()
                            },
                            leadingIcon = {
                                Icon(if (isGroup) Icons.Default.Group else Icons.Default.Person, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Відеодзвінок") },
                            onClick = {
                                showUserMenu = false
                                onVideoCallClick()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.VideoCall, contentDescription = null)
                            }
                        )

                        // ✅ GROUP-SPECIFIC OPTIONS
                        if (isGroup) {
                            Divider()
                            // Add members option
                            DropdownMenuItem(
                                text = { Text("Додати учасників") },
                                onClick = {
                                    showUserMenu = false
                                    onAddMembersClick()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color(0xFF0084FF))
                                }
                            )
                            // Create subgroup/folder option (for admins)
                            if (isGroupAdmin) {
                                DropdownMenuItem(
                                    text = { Text("Створити підгрупу/папку") },
                                    onClick = {
                                        showUserMenu = false
                                        onCreateSubgroupClick()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = Color(0xFF4CAF50))
                                    }
                                )
                            }
                            // Group settings (for admins)
                            if (isGroupAdmin) {
                                DropdownMenuItem(
                                    text = { Text("Налаштування групи") },
                                    onClick = {
                                        showUserMenu = false
                                        onGroupSettingsClick()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Settings, contentDescription = null)
                                    }
                                )
                            }
                        }

                        Divider()
                        DropdownMenuItem(
                            text = {
                                Text(if (isMuted) "Увімкнути сповіщення" else "Вимкнути сповіщення")
                            },
                            onClick = {
                                showUserMenu = false
                                onMuteClick()
                            },
                            leadingIcon = {
                                Icon(
                                    if (isMuted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = if (isMuted) Color(0xFFF44336) else LocalContentColor.current
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Змінити обої") },
                            onClick = {
                                showUserMenu = false
                                onChangeWallpaperClick()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Image, contentDescription = null)
                            }
                        )
                        Divider()
                        DropdownMenuItem(
                            text = { Text("Очистити історію") },
                            onClick = {
                                showUserMenu = false
                                onClearHistoryClick()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, contentDescription = null)
                            }
                        )

                        // ✅ User-only option: block user
                        if (!isGroup) {
                            Divider()
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (isUserBlocked) "Розблокувати користувача" else "Заблокувати користувача",
                                        color = if (isUserBlocked) Color(0xFF4CAF50) else Color(0xFFF44336)
                                    )
                                },
                                onClick = {
                                    showUserMenu = false
                                    onBlockClick()
                                },
                                leadingIcon = {
                                    Icon(
                                        if (isUserBlocked) Icons.Default.LockOpen else Icons.Default.Block,
                                        contentDescription = null,
                                        tint = if (isUserBlocked) Color(0xFF4CAF50) else Color(0xFFF44336)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colorScheme.primary,  // Цвет темы
            titleContentColor = colorScheme.onPrimary,
            navigationIconContentColor = colorScheme.onPrimary,
            actionIconContentColor = colorScheme.onPrimary
        )
    )  // Конец TopAppBar
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubbleComposable(
    message: Message,
    voicePlayer: VoicePlayer,
    replyToMessage: Message? = null,
    onLongPress: () -> Unit = {},
    onImageClick: (String) -> Unit = {},
    onReply: (Message) -> Unit = {},
    onToggleReaction: (Long, String) -> Unit = { _, _ -> },
    // 🔥 Параметри для режиму вибору
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: (Long) -> Unit = {},
    onDoubleTap: (Long) -> Unit = {},
    // 👤 Параметри для відображення імені відправника в групових чатах
    isGroup: Boolean = false,
    onSenderNameClick: (Long) -> Unit = {},
    // 📝 Параметри для форматування тексту
    formattingSettings: FormattingSettings = FormattingSettings(),
    onMentionClick: (String) -> Unit = {},
    onHashtagClick: (String) -> Unit = {},
    onLinkClick: (String) -> Unit = {},
    // 🗑️ ViewModel для видалення повідомлень
    viewModel: MessagesViewModel? = null
) {
    val context = LocalContext.current
    val isOwn = message.fromId == UserSession.userId
    val colorScheme = MaterialTheme.colorScheme
    val bubbleStyle = rememberBubbleStyle()  // 🎨 Отримуємо вибраний стиль бульбашок
    val uiStyle = com.worldmates.messenger.ui.preferences.rememberUIStyle()  // 🎨 Отримуємо стиль інтерфейсу

    // 💬 Свайп для Reply
    var offsetX by remember { mutableStateOf(0f) }
    val maxSwipeDistance = 100f  // Максимальна відстань свайпу

    // ❤️ Реакції
    var showReactionPicker by remember { mutableStateOf(false) }

    // Групуємо реакції по емоджі для відображення
    val reactionGroups = remember(message.reactions) {
        message.reactions?.groupBy { it.reaction }?.map { (emoji, reactionList) ->
            ReactionGroup(
                emoji = emoji,
                count = reactionList.size,
                userIds = reactionList.map { it.userId },
                hasMyReaction = reactionList.any { it.userId == UserSession.userId }
            )
        } ?: emptyList()
    }

    // 🎨 Кольори бульбашок залежать від стилю інтерфейсу
    val bgColor = when (uiStyle) {
        com.worldmates.messenger.ui.preferences.UIStyle.WORLDMATES -> {
            // WorldMates стиль - яскраві градієнтні кольори
            if (isOwn) {
                Color(0xFF4A90E2)  // Яскравий синій для власних
            } else {
                Color(0xFFF0F0F0)  // Світло-сірий для вхідних
            }
        }
        com.worldmates.messenger.ui.preferences.UIStyle.TELEGRAM -> {
            // Telegram/Класичний стиль - м'які нейтральні тони
            if (isOwn) {
                Color(0xFFDCF8C6)  // Світло-зелений як в Telegram
            } else {
                Color(0xFFFFFFFF)  // Білий для вхідних
            }
        }
    }

    val textColor = when (uiStyle) {
        com.worldmates.messenger.ui.preferences.UIStyle.WORLDMATES -> {
            if (isOwn) {
                Color.White  // Білий текст на яскравому фоні
            } else {
                Color(0xFF1F1F1F)  // Темний текст
            }
        }
        com.worldmates.messenger.ui.preferences.UIStyle.TELEGRAM -> {
            // Класичний стиль - завжди темний текст
            Color(0xFF1F1F1F)
        }
    }

    val playbackState by voicePlayer.playbackState.collectAsState()
    val currentPosition by voicePlayer.currentPosition.collectAsState()
    val duration by voicePlayer.duration.collectAsState()

    var showVideoPlayer by remember { mutableStateOf(false) }

    // 📱 Меню для медіа файлів
    var showMediaMenu by remember { mutableStateOf(false) }

    // 💬 Обгортка з іконкою Reply для свайпу
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        // Іконка Reply (показується при свайпі)
        if (offsetX > 20f) {
            Icon(
                imageVector = Icons.Default.Reply,
                contentDescription = "Reply",
                tint = colorScheme.primary.copy(alpha = (offsetX / maxSwipeDistance).coerceIn(0f, 1f)),
                modifier = Modifier
                    .align(if (isOwn) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 16.dp)
                    .size(24.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(message.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX > maxSwipeDistance / 2) {
                                // Свайп достатньо далеко - викликаємо reply
                                onReply(message)
                            }
                            // Повертаємо на місце
                            offsetX = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            // Свайп тільки праворуч для reply
                            offsetX = (offsetX + dragAmount).coerceIn(0f, maxSwipeDistance)
                        }
                    )
                },
            horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start
        ) {
            // ✅ Індикатор вибору (галочка) - показується в режимі вибору
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .padding(4.dp)
                        .align(Alignment.CenterVertically)
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Вибрано",
                            tint = colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Circle,
                            contentDescription = "Не вибрано",
                            tint = colorScheme.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Перевіряємо чи це emoji-only повідомлення
            val isEmojiMessage = message.decryptedText?.let { isEmojiOnly(it) } ?: false

            if (isEmojiMessage) {
                // 😊 ЕМОДЗІ БЕЗ БУЛЬБАШКИ - просто на прозорому фоні
                Column(
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            if (isSelectionMode) {
                                onToggleSelection(message.id)
                            }
                        },
                        onLongClick = {
                            if (!isSelectionMode) {
                                onLongPress()
                                onToggleSelection(message.id)
                            }
                        },
                        onDoubleClick = {
                            if (!isSelectionMode) {
                                onDoubleTap(message.id)
                            }
                        }
                    )
                ) {
                    // Text message - буде рендеритися далі в коді
                    if (!message.decryptedText.isNullOrEmpty()) {
                        Text(
                            text = message.decryptedText!!,
                            fontSize = getEmojiSize(message.decryptedText!!),
                            lineHeight = (getEmojiSize(message.decryptedText!!).value + 4).sp,
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
                        )
                    }
                }
            } else {
                // 💬 ТЕКСТ В БУЛЬБАШЦІ - використовуємо вибраний стиль
                Column {
                    // 👤 Ім'я відправника (тільки для групових чатів/каналів, і не для власних повідомлень)
                    if (isGroup && !isOwn && !message.senderName.isNullOrEmpty()) {
                        Text(
                            text = message.senderName!!,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(start = 12.dp, bottom = 2.dp)
                                .clickable {
                                    onSenderNameClick(message.fromId)
                                }
                        )
                    }

                    StyledBubble(
                        bubbleStyle = bubbleStyle,
                        isOwn = isOwn,
                        bgColor = bgColor,
                        modifier = Modifier
                            .wrapContentWidth()
                            .widthIn(min = 60.dp, max = 260.dp)
                            .padding(horizontal = 12.dp)
                            .combinedClickable(
                                onClick = {
                                    if (isSelectionMode) {
                                        onToggleSelection(message.id)
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        onLongPress()
                                        onToggleSelection(message.id)
                                    }
                                },
                                onDoubleClick = {
                                    if (!isSelectionMode) {
                                        onDoubleTap(message.id)
                                    }
                                }
                            )
                    ) {
                        // Получаем URL медиа из разных источников
                        var effectiveMediaUrl: String? = null

                        // 1. Сначала пытаемся использовать decryptedMediaUrl
                        if (!message.decryptedMediaUrl.isNullOrEmpty()) {
                            effectiveMediaUrl = message.decryptedMediaUrl
                            Log.d("MessageBubble", "Використовую decryptedMediaUrl: $effectiveMediaUrl")
                        }
                        // 2. Если пусто, проверяем mediaUrl
                        else if (!message.mediaUrl.isNullOrEmpty()) {
                            effectiveMediaUrl = message.mediaUrl
                            Log.d("MessageBubble", "Використовую mediaUrl: $effectiveMediaUrl")
                        }
                        // 3. Если все еще пусто, пытаемся извлечь URL из decryptedText
                        else if (!message.decryptedText.isNullOrEmpty()) {
                            effectiveMediaUrl = extractMediaUrlFromText(message.decryptedText!!)
                            Log.d("MessageBubble", "Витягнуто з тексту: $effectiveMediaUrl")
                        }

                        // Определяем тип медиа по URL
                        val detectedMediaType = detectMediaType(effectiveMediaUrl ?: "", message.type)
                        Log.d("MessageBubble", "ID повідомлення: ${message.id}, Тип: ${message.type}, Визначений тип: $detectedMediaType, URL: $effectiveMediaUrl")

                        // Показываем текст ТОЛЬКО если:
                        // 1. Текст есть И не пустой
                        // 2. И это НЕ чистый URL медиа (текст + медиа можно, чистый URL - нет)
                        val shouldShowText = !message.decryptedText.isNullOrEmpty() &&
                                !isOnlyMediaUrl(message.decryptedText!!)

                        // 💬 Цитата Reply (якщо є)
                        if (message.replyToId != null && message.replyToText != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = textColor.copy(alpha = 0.1f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Вертикальна лінія
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(40.dp)
                                            .background(
                                                color = colorScheme.primary,
                                                shape = RoundedCornerShape(2.dp)
                                            )
                                    )
                                    // Текст цитати
                                    Column {
                                        Text(
                                            text = "Відповідь",
                                            color = colorScheme.primary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = message.replyToText!!,
                                            color = textColor.copy(alpha = 0.7f),
                                            fontSize = 14.sp,
                                            maxLines = 2,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }

                        // Text message
                        if (shouldShowText) {
                            // 📇 Проверяем, является ли сообщение vCard контактом
                            val isContactMessage = com.worldmates.messenger.ui.components.isVCardMessage(message.decryptedText!!)

                            if (isContactMessage) {
                                // Рендерим контакт
                                val contact = com.worldmates.messenger.ui.components.parseContactFromMessage(message.decryptedText!!)
                                if (contact != null) {
                                    com.worldmates.messenger.ui.components.ContactMessageBubble(
                                        contact = contact
                                    )
                                } else {
                                    // Если не удалось распарсить, показываем как обычный текст з форматуванням
                                    FormattedMessageText(
                                        text = message.decryptedText!!,
                                        textColor = textColor,
                                        settings = formattingSettings,
                                        onMentionClick = onMentionClick,
                                        onHashtagClick = onHashtagClick,
                                        onLinkClick = onLinkClick
                                    )
                                }
                            } else {
                                // 💬 ТЕКСТ В БУЛЬБАШЦІ з форматуванням (emoji-only handled inside)
                                FormattedMessageContent(
                                    message = message,
                                    textColor = textColor,
                                    settings = formattingSettings,
                                    onMentionClick = onMentionClick,
                                    onHashtagClick = onHashtagClick,
                                    onLinkClick = onLinkClick
                                )
                            }
                        }

                        // Image - показываем если тип "image" или если URL указывает на изображение
                        if (!effectiveMediaUrl.isNullOrEmpty() && detectedMediaType == "image") {
                            Box(
                                modifier = Modifier
                                    .wrapContentWidth()  // Адаптується під розмір зображення
                                    .widthIn(max = 250.dp)  // Максимальна ширина для зображень
                                    .heightIn(min = 120.dp, max = 300.dp)
                                    .padding(top = if (shouldShowText) 6.dp else 0.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black.copy(alpha = 0.1f))
                                    .combinedClickable(
                                        onClick = {
                                            if (isSelectionMode) {
                                                onToggleSelection(message.id)
                                            } else {
                                                // Звичайний клік - відкриваємо галерею
                                                Log.d("MessageBubble", "📸 Клік по зображенню: $effectiveMediaUrl")
                                                onImageClick(effectiveMediaUrl)
                                            }
                                        },
                                        onLongClick = {
                                            if (!isSelectionMode) {
                                                onLongPress()
                                                onToggleSelection(message.id)
                                            }
                                        }
                                    )
                            ) {
                                AsyncImage(
                                    model = effectiveMediaUrl,
                                    contentDescription = "Media",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                    onError = {
                                        Log.e("MessageBubble", "Помилка завантаження зображення: $effectiveMediaUrl, error: ${it.result.throwable}")
                                    }
                                )
                            }
                        }

                        // Video - інлайн плеєр з автоматичним дешифруванням
                        if (!effectiveMediaUrl.isNullOrEmpty() && detectedMediaType == "video") {
                            VideoMessageComponent(
                                message = message,
                                videoUrl = effectiveMediaUrl,
                                showTextAbove = shouldShowText,
                                enablePiP = true,
                                modifier = Modifier
                            )
                        }

                        // 🎭 Animated Sticker message
                        if (!effectiveMediaUrl.isNullOrEmpty() && detectedMediaType == "sticker") {
                            Log.d("MessageBubble", "🎭 Відображаю стікер: $effectiveMediaUrl")
                            AnimatedStickerView(
                                url = effectiveMediaUrl,
                                size = 150.dp,
                                autoPlay = true,
                                loop = true,
                                modifier = Modifier.padding(top = if (shouldShowText) 8.dp else 0.dp)
                            )
                        }

                        // Voice/Audio message player
                        if (!effectiveMediaUrl.isNullOrEmpty() &&
                            (detectedMediaType == "voice" || detectedMediaType == "audio")) {
                            VoiceMessagePlayer(
                                message = message,
                                voicePlayer = voicePlayer,
                                textColor = textColor,
                                mediaUrl = effectiveMediaUrl
                            )
                        }

                        // File attachment
                        if (!effectiveMediaUrl.isNullOrEmpty() && detectedMediaType == "file") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = if (shouldShowText) 8.dp else 0.dp)
                            ) {
                                Icon(
                                    Icons.Default.InsertDriveFile,
                                    contentDescription = "File",
                                    tint = textColor,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = effectiveMediaUrl.substringAfterLast("/"),
                                    color = textColor,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Время с более стильным форматированием + галочки прочитано
                        Row(
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = formatTime(message.timeStamp),
                                color = textColor.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )

                            // ✓✓ Галочки прочитано (тільки для власних повідомлень)
                            if (isOwn) {
                                Spacer(modifier = Modifier.width(4.dp))
                                MessageStatusIcon(
                                    isRead = message.isRead ?: false,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }

                    // ❤️ Реакції під повідомленням
                    MessageReactions(
                        reactions = reactionGroups,
                        onReactionClick = { emoji ->
                            onToggleReaction(message.id, emoji)
                        },
                        modifier = Modifier.align(if (isOwn) Alignment.End else Alignment.Start)
                    )
                }  // Закриття Column
            }  // Закриття else block
        }  // Закриття Row

        // 🎯 ReactionPicker overlay (показується при довгому натисканні)
        if (showReactionPicker) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-40).dp)  // Зміщення вгору над повідомленням
            ) {
                ReactionPicker(
                    onReactionSelected = { emoji ->
                        onToggleReaction(message.id, emoji)
                        showReactionPicker = false
                    },
                    onDismiss = { showReactionPicker = false }
                )
            }
        }
    }  // Закриття Box зі свайпом

    // 📱 Меню для медіа файлів (показується при довгому натисканні на медіа)
    var showDeleteMediaConfirmation by remember { mutableStateOf(false) }

    MediaActionMenu(
        visible = showMediaMenu,
        isOwnMessage = isOwn,
        onShare = {
            // Поділитися медіа файлом через Intent
            val mediaUrl = message.decryptedMediaUrl ?: message.mediaUrl
            if (!mediaUrl.isNullOrEmpty()) {
                try {
                    val shareIntent = android.content.Intent().apply {
                        action = android.content.Intent.ACTION_SEND
                        type = when {
                            mediaUrl.contains(".jpg", ignoreCase = true) ||
                                    mediaUrl.contains(".png", ignoreCase = true) ||
                                    mediaUrl.contains(".jpeg", ignoreCase = true) -> "image/*"
                            mediaUrl.contains(".mp4", ignoreCase = true) ||
                                    mediaUrl.contains(".mov", ignoreCase = true) -> "video/*"
                            else -> "*/*"
                        }
                        putExtra(android.content.Intent.EXTRA_TEXT, mediaUrl)
                    }
                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Поділитися медіа"))
                    showMediaMenu = false
                } catch (e: Exception) {
                    android.widget.Toast.makeText(
                        context,
                        "Не вдалося поділитися медіа",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        },
        onDelete = {
            // Показуємо підтвердження видалення
            showDeleteMediaConfirmation = true
            showMediaMenu = false
        },
        onDismiss = { showMediaMenu = false }
    )

    // 🗑️ Діалог підтвердження видалення медіа
    if (showDeleteMediaConfirmation) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteMediaConfirmation = false },
            title = { androidx.compose.material3.Text("Видалити медіа?") },
            text = { androidx.compose.material3.Text("Це повідомлення буде видалено назавжди") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        viewModel?.deleteMessage(message.id)
                        showDeleteMediaConfirmation = false
                    }
                ) {
                    androidx.compose.material3.Text("Видалити", color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showDeleteMediaConfirmation = false }
                ) {
                    androidx.compose.material3.Text("Скасувати")
                }
            }
        )
    }
}

@Composable
fun VoiceMessagePlayer(
    message: Message,
    voicePlayer: VoicePlayer,
    textColor: Color,
    mediaUrl: String
) {
    val context = LocalContext.current
    val servicePlaybackState by com.worldmates.messenger.services.MusicPlaybackService.playbackState.collectAsState()
    val serviceTrackInfo by com.worldmates.messenger.services.MusicPlaybackService.currentTrackInfo.collectAsState()

    // Чи саме цей трек грає у сервісі
    val isThisTrackPlaying = serviceTrackInfo.url == mediaUrl && servicePlaybackState.isPlaying
    val isThisTrackLoaded = serviceTrackInfo.url == mediaUrl

    val colorScheme = MaterialTheme.colorScheme

    // Стан для відображення повноекранного плеєра
    var showAdvancedPlayer by remember { mutableStateOf(false) }

    // Компактний аудіо плеєр
    Surface(
        modifier = Modifier
            .wrapContentWidth()
            .widthIn(min = 200.dp, max = 260.dp),
        shape = RoundedCornerShape(18.dp),
        color = textColor.copy(alpha = 0.1f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            // Кнопка відтворення
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = colorScheme.primary
            ) {
                IconButton(
                    onClick = {
                        if (isThisTrackPlaying) {
                            com.worldmates.messenger.services.MusicPlaybackService.pausePlayback(context)
                        } else if (isThisTrackLoaded) {
                            com.worldmates.messenger.services.MusicPlaybackService.resumePlayback(context)
                        } else {
                            // Запускаємо через MusicPlaybackService для фонового відтворення
                            com.worldmates.messenger.services.MusicPlaybackService.startPlayback(
                                context = context,
                                audioUrl = mediaUrl,
                                title = message.senderName ?: "Аудіо",
                                artist = "",
                                timestamp = message.timeStamp,
                                iv = message.iv,
                                tag = message.tag
                            )
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isThisTrackPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Прогрес + час
            Column(modifier = Modifier.weight(1f)) {
                Slider(
                    value = if (isThisTrackLoaded && servicePlaybackState.duration > 0)
                        servicePlaybackState.currentPosition.toFloat() else 0f,
                    onValueChange = { newPos ->
                        if (isThisTrackLoaded) {
                            com.worldmates.messenger.services.MusicPlaybackService.seekTo(context, newPos.toLong())
                        }
                    },
                    valueRange = 0f..(if (isThisTrackLoaded) servicePlaybackState.duration.toFloat().coerceAtLeast(1f) else 1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = colorScheme.primary,
                        activeTrackColor = colorScheme.primary,
                        inactiveTrackColor = textColor.copy(alpha = 0.2f)
                    )
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Час
            Text(
                text = if (isThisTrackLoaded)
                    formatAudioTime(servicePlaybackState.currentPosition)
                else
                    "0:00",
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )

            // Кнопка розгортання плеєра
            IconButton(
                onClick = { showAdvancedPlayer = true },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = "Відкрити плеєр",
                    tint = textColor.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }

    // Повноекранний плеєр
    if (showAdvancedPlayer) {
        com.worldmates.messenger.ui.music.AdvancedMusicPlayer(
            audioUrl = mediaUrl,
            title = message.senderName ?: "Аудіо",
            artist = "",
            timestamp = message.timeStamp,
            iv = message.iv,
            tag = message.tag,
            onDismiss = { showAdvancedPlayer = false }
        )
    }
}

private fun formatAudioTime(millis: Long): String {
    if (millis <= 0) return "0:00"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

@Composable
fun MessageInputBar(
    currentInputMode: InputMode,
    onInputModeChange: (InputMode) -> Unit,
    messageText: String,
    onMessageChange: (String) -> Unit,
    onSendClick: () -> Unit,
    isLoading: Boolean,
    recordingState: VoiceRecorder.RecordingState,
    recordingDuration: Long,
    voiceRecorder: VoiceRecorder,
    onStartVoiceRecord: () -> Unit,
    onCancelVoiceRecord: () -> Unit,
    onStopVoiceRecord: () -> Unit,
    onShowMediaOptions: () -> Unit,
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onPickAudio: () -> Unit,
    onPickFile: () -> Unit,
    onCameraClick: () -> Unit,
    onVideoCameraClick: () -> Unit,
    showMediaOptions: Boolean,
    showEmojiPicker: Boolean,
    onToggleEmojiPicker: () -> Unit,
    showStickerPicker: Boolean,
    onToggleStickerPicker: () -> Unit,
    showGifPicker: Boolean,
    onToggleGifPicker: () -> Unit,
    showLocationPicker: Boolean,
    onToggleLocationPicker: () -> Unit,
    showContactPicker: Boolean,
    onToggleContactPicker: () -> Unit,
    showStrapiPicker: Boolean,  // Додано
    onToggleStrapiPicker: () -> Unit,  // Додано
    onRequestAudioPermission: () -> Boolean = { true },
    onRequestVideoPermissions: () -> Boolean = { true },
    viewModel: MessagesViewModel? = null,
    formattingSettings: FormattingSettings = FormattingSettings()
) {
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val context = LocalContext.current  // Додано для вібрації

    // 📝 State для панелі форматування (перенесено на рівень функції)
    var showFormattingToolbar by remember { mutableStateOf(false) }
    var showLinkInsertDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorScheme.surface)
            .navigationBarsPadding()
    ) {

        // 📎 Компактне меню медіа (BottomSheet)
        CompactMediaMenu(
            visible = showMediaOptions,
            onDismiss = { onShowMediaOptions() },
            onPhotoClick = { onPickImage() },
            onCameraClick = { onCameraClick() },
            onVideoClick = { onPickVideo() },
            onVideoCameraClick = { onVideoCameraClick() },
            onAudioClick = { onPickAudio() },
            onFileClick = { onPickFile() },
            onLocationClick = { onToggleLocationPicker() },
            onContactClick = { onToggleContactPicker() },
            onStickerClick = { onToggleStickerPicker() },
            onGifClick = { onToggleGifPicker() },
            onEmojiClick = { onToggleEmojiPicker() },
            onStrapiClick = { onToggleStrapiPicker() }
        )
    }

    // Voice Recording UI
    if (recordingState is VoiceRecorder.RecordingState.Recording ||
        recordingState is VoiceRecorder.RecordingState.Paused) {
        VoiceRecordingBar(
            duration = recordingDuration,
            voiceRecorder = voiceRecorder,
            onCancel = onCancelVoiceRecord,
            onStop = onStopVoiceRecord,
            isRecording = recordingState is VoiceRecorder.RecordingState.Recording
        )
    }

    // Message Input - Telegram/Viber Style з swipeable tabs
    if (recordingState !is VoiceRecorder.RecordingState.Recording &&
        recordingState !is VoiceRecorder.RecordingState.Paused) {

        Column {
            // 🎯 Swipeable tabs для швидкого перемикання режимів
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Text mode
                InputModeTab(
                    icon = Icons.Default.Chat,
                    label = "Текст",
                    isSelected = currentInputMode == InputMode.TEXT,
                    onClick = { onInputModeChange(InputMode.TEXT) }
                )

                // Voice mode
                InputModeTab(
                    icon = Icons.Default.Mic,
                    label = "Голос",
                    isSelected = currentInputMode == InputMode.VOICE,
                    onClick = { onInputModeChange(InputMode.VOICE) }
                )

                // Video mode (майбутнє)
                InputModeTab(
                    icon = Icons.Default.Videocam,
                    label = "Відео",
                    isSelected = currentInputMode == InputMode.VIDEO,
                    onClick = { onInputModeChange(InputMode.VIDEO) }
                )

                // Emoji mode
                InputModeTab(
                    icon = Icons.Default.EmojiEmotions,
                    label = "Емодзі",
                    isSelected = currentInputMode == InputMode.EMOJI,
                    onClick = { onInputModeChange(InputMode.EMOJI) }
                )

                // Sticker mode
                InputModeTab(
                    icon = Icons.Default.StickyNote2,
                    label = "Стікери",
                    isSelected = currentInputMode == InputMode.STICKER,
                    onClick = { onInputModeChange(InputMode.STICKER) }
                )

                // GIF mode
                InputModeTab(
                    icon = Icons.Default.Gif,
                    label = "GIF",
                    isSelected = currentInputMode == InputMode.GIF,
                    onClick = { onInputModeChange(InputMode.GIF) }
                )
            }

            // 📝 Панель форматування тексту (показується при фокусі на текстове поле)
            FormattingToolbar(
                isVisible = showFormattingToolbar && currentInputMode == InputMode.TEXT,
                hasSelection = messageText.isNotEmpty(),
                settings = formattingSettings,
                onBoldClick = {
                    viewModel?.applyFormatting(messageText, "**", "**")?.let { formatted ->
                        onMessageChange(formatted)
                    }
                },
                onItalicClick = {
                    viewModel?.applyFormatting(messageText, "*", "*")?.let { formatted ->
                        onMessageChange(formatted)
                    }
                },
                onStrikethroughClick = {
                    viewModel?.applyFormatting(messageText, "~~", "~~")?.let { formatted ->
                        onMessageChange(formatted)
                    }
                },
                onUnderlineClick = {
                    viewModel?.applyFormatting(messageText, "<u>", "</u>")?.let { formatted ->
                        onMessageChange(formatted)
                    }
                },
                onCodeClick = {
                    viewModel?.applyFormatting(messageText, "`", "`")?.let { formatted ->
                        onMessageChange(formatted)
                    }
                },
                onSpoilerClick = {
                    viewModel?.applyFormatting(messageText, "||", "||")?.let { formatted ->
                        onMessageChange(formatted)
                    }
                },
                onQuoteClick = {
                    // Додаємо > на початку тексту
                    if (messageText.isNotEmpty()) {
                        val lines = messageText.lines()
                        val quoted = lines.joinToString("\n") { "> $it" }
                        onMessageChange(quoted)
                    }
                },
                onLinkClick = {
                    showLinkInsertDialog = true
                },
                onMentionClick = {
                    // Додаємо @ для початку згадки
                    onMessageChange(messageText + "@")
                },
                onHashtagClick = {
                    // Додаємо # для початку хештегу
                    onMessageChange(messageText + "#")
                }
            )

            // Main input row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // Кнопка "+" - показує опції (файли, локація, контакт)
                IconButton(
                    onClick = onShowMediaOptions,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (showMediaOptions) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = "Опції",
                        tint = if (showMediaOptions) colorScheme.primary else colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Різний контент залежно від режиму
                when (currentInputMode) {
                    InputMode.TEXT -> {
                        // 📝 Кнопка форматування
                        IconButton(
                            onClick = { showFormattingToolbar = !showFormattingToolbar },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.TextFormat,
                                contentDescription = "Форматування",
                                tint = if (showFormattingToolbar) colorScheme.primary else colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Звичайне поле введення
                        TextField(
                            value = messageText,
                            onValueChange = onMessageChange,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 40.dp, max = 120.dp)
                                .background(colorScheme.surfaceVariant, RoundedCornerShape(20.dp)),
                            placeholder = {
                                Text(
                                    "Повідомлення",
                                    color = colorScheme.onSurfaceVariant,
                                    fontSize = 16.sp
                                )
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = colorScheme.onSurface,
                                unfocusedTextColor = colorScheme.onSurface
                            ),
                            textStyle = MaterialTheme.typography.bodyLarge,
                            maxLines = 4
                        )
                    }

                    InputMode.VOICE -> {
                        // Підказка для голосового повідомлення
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .background(colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(20.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Натисни і утримуй для запису →",
                                    color = colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    InputMode.VIDEO -> {
                        // 📹 Відеоповідомлення - кнопка запису
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .background(colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = null,
                                tint = Color.Red,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Натисніть 📹 справа для запису",
                                color = colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                        }
                    }

                    InputMode.EMOJI, InputMode.STICKER, InputMode.GIF -> {
                        // Показуємо текстове поле для коментаря
                        TextField(
                            value = messageText,
                            onValueChange = onMessageChange,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 40.dp, max = 120.dp)
                                .background(colorScheme.surfaceVariant, RoundedCornerShape(20.dp)),
                            placeholder = {
                                Text(
                                    "Додати коментар...",
                                    color = colorScheme.onSurfaceVariant,
                                    fontSize = 16.sp
                                )
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = colorScheme.onSurface,
                                unfocusedTextColor = colorScheme.onSurface
                            ),
                            textStyle = MaterialTheme.typography.bodyLarge,
                            maxLines = 4
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Права кнопка залежить від режиму
                when (currentInputMode) {
                    InputMode.TEXT -> {
                        if (messageText.isNotBlank()) {
                            // Кнопка відправки
                            IconButton(
                                onClick = onSendClick,
                                enabled = !isLoading,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "Відправити",
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        } else {
                            // Кнопка голосового запису (для швидкого доступу)
                            IconButton(
                                onClick = onStartVoiceRecord,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Голосове повідомлення",
                                    tint = colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    InputMode.VOICE -> {
                        // Велика кнопка для запису зі swipe gesture (як в Telegram)
                        var isRecordingLocked by remember { mutableStateOf(false) }

                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(colorScheme.primary, CircleShape)
                                .pointerInput(Unit) {
                                    var startY = 0f
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            startY = offset.y
                                            // Починаємо запис при натисканні
                                            if (onRequestAudioPermission()) {
                                                scope.launch {
                                                    voiceRecorder.startRecording()
                                                }
                                            }
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            val currentOffsetY = change.position.y - startY

                                            // Swipe вгору для lock (> 100px вгору)
                                            if (currentOffsetY < -100f && !isRecordingLocked) {
                                                isRecordingLocked = true
                                                // Вібрація
                                                try {
                                                    @Suppress("DEPRECATION")
                                                    val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                                        vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                                                    } else {
                                                        @Suppress("DEPRECATION")
                                                        vibrator?.vibrate(50)
                                                    }
                                                } catch (e: Exception) {
                                                    // Ignore vibration errors
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            if (!isRecordingLocked) {
                                                // Якщо не locked - зупиняємо запис і надсилаємо
                                                scope.launch {
                                                    val stopped = voiceRecorder.stopRecording()
                                                    if (stopped && voiceRecorder.recordingState.value is VoiceRecorder.RecordingState.Completed) {
                                                        val filePath = (voiceRecorder.recordingState.value as VoiceRecorder.RecordingState.Completed).filePath
                                                        viewModel?.uploadAndSendMedia(java.io.File(filePath), "voice")
                                                    }
                                                }
                                            }
                                        },
                                        onDragCancel = {
                                            // Скасування
                                            if (!isRecordingLocked) {
                                                scope.launch {
                                                    voiceRecorder.cancelRecording()
                                                }
                                            }
                                            isRecordingLocked = false
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isRecordingLocked) Icons.Default.Lock else Icons.Default.Mic,
                                contentDescription = "Записати",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )

                            // Підказка при записі
                            if (recordingState is VoiceRecorder.RecordingState.Recording && !isRecordingLocked) {
                                Text(
                                    text = "⬆️ Свайп вгору",
                                    fontSize = 10.sp,
                                    color = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .offset(y = (-60).dp)
                                )
                            }
                        }

                        // Кнопка Stop коли locked
                        if (isRecordingLocked && recordingState is VoiceRecorder.RecordingState.Recording) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        val stopped = voiceRecorder.stopRecording()
                                        if (stopped && voiceRecorder.recordingState.value is VoiceRecorder.RecordingState.Completed) {
                                            val filePath = (voiceRecorder.recordingState.value as VoiceRecorder.RecordingState.Completed).filePath
                                            viewModel?.uploadAndSendMedia(java.io.File(filePath), "voice")
                                        }
                                        isRecordingLocked = false
                                    }
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Зупинити",
                                    tint = colorScheme.error,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    InputMode.VIDEO -> {
                        // 📹 Кнопка запису відеоповідомлення - відкриває камеру
                        IconButton(
                            onClick = onVideoCameraClick,  // ✅ Відкриває VideoMessageRecorder для запису через камеру
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = "Записати відео",
                                tint = Color.Red,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    InputMode.EMOJI, InputMode.STICKER, InputMode.GIF -> {
                        // Відкрито пікер - кнопка Send якщо є текст
                        if (messageText.isNotBlank()) {
                            IconButton(
                                onClick = onSendClick,
                                enabled = !isLoading,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "Відправити",
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        } else {
                            // Просто placeholder
                            Spacer(modifier = Modifier.size(40.dp))
                        }
                    }
                }
            }
        }
    }

    // 🔗 Діалог вставки посилання
    if (showLinkInsertDialog) {
        com.worldmates.messenger.ui.components.formatting.LinkInsertDialog(
            selectedText = "", // Empty or selected text
            onDismiss = { showLinkInsertDialog = false },
            onConfirm = { url ->
                val linkMarkdown = "[$url]($url)" // If no selectedText, use URL as text
                onMessageChange(messageText + linkMarkdown)
                showLinkInsertDialog = false
            }
        )
    }
}
/**
 * Діалог вибору якості аудіо при відправці (як в Telegram)
 */
@Composable
fun AudioQualityDialog(
    fileName: String,
    fileSize: Long,
    onSendOriginal: () -> Unit,
    onSendCompressed: () -> Unit,
    onDismiss: () -> Unit
) {
    val fileSizeMB = String.format("%.1f", fileSize / (1024.0 * 1024.0))

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Надіслати аудіо",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Розмір: $fileSizeMB МБ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Divider()

                // Оригінальна якість
                Surface(
                    onClick = onSendOriginal,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.HighQuality,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Оригінальна якість",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Без стиснення, повна якість звуку",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Стиснута якість
                Surface(
                    onClick = onSendCompressed,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Compress,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Стиснутий (економія трафіку)",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Зменшений розмір, менше трафіку",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Скасувати")
            }
        }
    )
}

@Composable
fun VoiceRecordingBar(
    duration: Long,
    voiceRecorder: VoiceRecorder,
    onCancel: () -> Unit,
    onStop: () -> Unit,
    isRecording: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFF3E0))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Default.Mic,
            contentDescription = "Recording",
            tint = Color.Red,
            modifier = Modifier.size(20.dp)
        )

        Text(
            text = voiceRecorder.formatDuration(duration),
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )

        IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Cancel")
        }

        Button(
            onClick = onStop,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0084FF))
        ) {
            Text("Надіслати", color = Color.White)
        }
    }
}

// 🎯 Tab для перемикання режимів введення (Telegram/Viber style)
@Composable
fun InputModeTab(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        onClick = onClick,
        modifier = Modifier
            .height(36.dp)
            .padding(horizontal = 2.dp),
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) colorScheme.primary else colorScheme.surfaceVariant,
        contentColor = if (isSelected) Color.White else colorScheme.onSurfaceVariant,
        tonalElevation = if (isSelected) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(18.dp)
            )
            if (isSelected) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun MediaOptionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0xFF0084FF),
            modifier = Modifier.size(48.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Text(label, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp * 1000))
}

/**
 * Определяет тип медиа по URL или явному типу сообщения.
 * Если message.type указан явно (не "text"), используем его.
 * Иначе определяем по расширению файла или пути в URL.
 */
private fun detectMediaType(url: String?, messageType: String?): String? {
    // Если URL пустой, используем тип сообщения
    if (url.isNullOrEmpty()) {
        Log.d("detectMediaType", "URL пустий, тип повідомлення: $messageType")
        return if (messageType?.isNotEmpty() == true && messageType != "text") messageType else "text"
    }

    val lowerUrl = url.lowercase()
    Log.d("detectMediaType", "Аналіз URL: $lowerUrl, тип повідомлення: $messageType")

    // Спочатку перевіряємо за шляхом (найнадійніше)
    val typeByPath = when {
        lowerUrl.contains("/upload/photos/") || lowerUrl.contains("/upload/images/") -> "image"
        lowerUrl.contains("/upload/videos/") -> "video"
        lowerUrl.contains("/upload/sounds/") || lowerUrl.contains("/upload/audio/") -> "audio"
        lowerUrl.contains("/upload/files/") -> "file"
        else -> null
    }

    if (typeByPath != null) {
        Log.d("detectMediaType", "Визначено за шляхом: $typeByPath")
        return typeByPath
    }

    // Потім перевіряємо за розширенням
    val typeByExtension = when {
        // Анімовані стікери
        lowerUrl.endsWith(".json") || lowerUrl.endsWith(".lottie") ||
                lowerUrl.endsWith(".tgs") || lowerUrl.startsWith("lottie://") ||
                lowerUrl.contains("/stickers/") -> "sticker"

        // Изображения
        lowerUrl.endsWith(".jpg") || lowerUrl.endsWith(".jpeg") ||
                lowerUrl.endsWith(".png") || lowerUrl.endsWith(".gif") ||
                lowerUrl.endsWith(".webp") || lowerUrl.endsWith(".bmp") -> "image"

        // Видео
        lowerUrl.endsWith(".mp4") || lowerUrl.endsWith(".webm") ||
                lowerUrl.endsWith(".mov") || lowerUrl.endsWith(".avi") ||
                lowerUrl.endsWith(".mkv") || lowerUrl.endsWith(".3gp") -> "video"

        // Аудио/Голос
        lowerUrl.endsWith(".mp3") || lowerUrl.endsWith(".wav") ||
                lowerUrl.endsWith(".ogg") || lowerUrl.endsWith(".m4a") ||
                lowerUrl.endsWith(".aac") || lowerUrl.endsWith(".opus") -> "audio"

        // Файлы
        lowerUrl.endsWith(".pdf") || lowerUrl.endsWith(".doc") ||
                lowerUrl.endsWith(".docx") || lowerUrl.endsWith(".xls") ||
                lowerUrl.endsWith(".xlsx") || lowerUrl.endsWith(".zip") ||
                lowerUrl.endsWith(".rar") || lowerUrl.endsWith(".txt") -> "file"

        else -> null
    }

    if (typeByExtension != null) {
        Log.d("detectMediaType", "Визначено за розширенням: $typeByExtension")
        return typeByExtension
    }

    // Якщо нічого не знайшли, використовуємо messageType
    if (messageType?.isNotEmpty() == true && messageType != "text") {
        Log.d("detectMediaType", "Використовую тип повідомлення: $messageType")
        return messageType
    }

    Log.d("detectMediaType", "Не вдалося визначити тип, повертаю 'text'")
    return "text"
}

/**
 * Извлекает URL медиа-файла из текста сообщения.
 * Возвращает URL если он найден, иначе null.
 */
private fun extractMediaUrlFromText(text: String): String? {
    val trimmed = text.trim()

    // Проверяем, является ли весь текст URL
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        val lowerText = trimmed.lowercase()
        if (lowerText.contains("/upload/photos/") ||
            lowerText.contains("/upload/videos/") ||
            lowerText.contains("/upload/sounds/") ||
            lowerText.contains("/upload/files/") ||
            lowerText.matches(Regex(".*\\.(jpg|jpeg|png|gif|webp|mp4|webm|mov|mp3|wav|ogg|pdf|doc|docx)$"))) {
            return trimmed
        }
    }

    // Пытаемся найти URL медиа внутри текста
    val urlPattern = "(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)".toRegex()
    val match = urlPattern.find(trimmed)

    return match?.value?.let { url ->
        val lowerUrl = url.lowercase()
        if (lowerUrl.contains("/upload/photos/") ||
            lowerUrl.contains("/upload/videos/") ||
            lowerUrl.contains("/upload/sounds/") ||
            lowerUrl.contains("/upload/files/") ||
            lowerUrl.matches(Regex(".*\\.(jpg|jpeg|png|gif|webp|mp4|webm|mov|mp3|wav|ogg|pdf|doc|docx)$"))) {
            url
        } else {
            null
        }
    }
}

/**
 * Проверяет, является ли текст только URL медиа-файла.
 * Если да, не нужно показывать текст отдельно (покажем только медиа).
 */
private fun isOnlyMediaUrl(text: String): Boolean {
    val trimmed = text.trim()

    // Если текст не похож на URL, это не чистый URL
    if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
        return false
    }

    // Проверяем, содержит ли URL только медиа-ресурс без дополнительного текста
    val lowerText = trimmed.lowercase()
    val isMediaUrl = lowerText.contains("/upload/photos/") ||
            lowerText.contains("/upload/videos/") ||
            lowerText.contains("/upload/sounds/") ||
            lowerText.contains("/upload/files/") ||
            lowerText.endsWith(".jpg") ||
            lowerText.endsWith(".jpeg") ||
            lowerText.endsWith(".png") ||
            lowerText.endsWith(".gif") ||
            lowerText.endsWith(".mp4") ||
            lowerText.endsWith(".mp3") ||
            lowerText.endsWith(".webm")

    // Если это URL медиа и нет дополнительного текста после URL
    return isMediaUrl && !trimmed.contains(" ") && !trimmed.contains("\n")
}

/**
 * Контекстне меню для повідомлень (Reply, Edit, Forward, Delete, Copy)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageContextMenu(
    message: Message,
    onDismiss: () -> Unit,
    onReply: (Message) -> Unit,
    onEdit: (Message) -> Unit,
    onForward: (Message) -> Unit,
    onDelete: (Message) -> Unit,
    onCopy: (Message) -> Unit,
    onPin: (Message) -> Unit = {},
    isPrivateChat: Boolean = false
) {
    val sheetState = rememberModalBottomSheetState()
    val colorScheme = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            // Заголовок
            Text(
                text = "Дії з повідомленням",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                color = colorScheme.onSurface
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Reply
            ContextMenuItem(
                icon = Icons.Default.Reply,
                text = "Відповісти",
                onClick = { onReply(message) }
            )

            // Edit (тільки для своїх ТЕКСТОВИХ повідомлень, не медіа)
            val msgType = message.type?.lowercase() ?: ""
            val isMediaMessage = msgType.contains("image") || msgType.contains("video") ||
                    msgType.contains("audio") || msgType == "sticker" || msgType == "file" ||
                    msgType.contains("photo")
            val textIsMediaUrl = message.decryptedText?.let { text ->
                val trimmed = text.trim()
                (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("upload/")) &&
                        !trimmed.contains(" ") && !trimmed.contains("\n")
            } ?: false
            if (message.fromId == UserSession.userId && !message.decryptedText.isNullOrEmpty() && !isMediaMessage && !textIsMediaUrl) {
                ContextMenuItem(
                    icon = Icons.Default.Edit,
                    text = "Редагувати",
                    onClick = { onEdit(message) }
                )
            }

            // Forward
            ContextMenuItem(
                icon = Icons.Default.Forward,
                text = "Переслати",
                onClick = { onForward(message) }
            )

            // Pin (для приватних чатів)
            if (isPrivateChat) {
                ContextMenuItem(
                    icon = Icons.Default.PushPin,
                    text = "Закріпити",
                    onClick = { onPin(message) }
                )
            }

            // Copy (якщо є текст і це не просто URL медіа)
            if (!message.decryptedText.isNullOrEmpty() && !isMediaMessage && !textIsMediaUrl) {
                ContextMenuItem(
                    icon = Icons.Default.ContentCopy,
                    text = "Копіювати текст",
                    onClick = { onCopy(message) }
                )
            }

            // Delete (тільки для своїх повідомлень)
            if (message.fromId == UserSession.userId) {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                ContextMenuItem(
                    icon = Icons.Default.Delete,
                    text = "Видалити",
                    onClick = { onDelete(message) },
                    isDestructive = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Елемент контекстного меню
 */
@Composable
private fun ContextMenuItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    val colorScheme = MaterialTheme.colorScheme
    val contentColor = if (isDestructive) {
        Color(0xFFD32F2F)  // Червоний для видалення
    } else {
        colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor
        )
    }
}

/**
 * Індикатор повідомлення, на яке відповідаємо
 */
@Composable
fun ReplyIndicator(
    replyToMessage: Message?,
    onCancelReply: () -> Unit
) {
    if (replyToMessage != null) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(40.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(2.dp)
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (replyToMessage.fromId == UserSession.userId) "Ви" else "Користувач",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = replyToMessage.decryptedText ?: "[Медіа]",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                IconButton(onClick = onCancelReply) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Скасувати відповідь",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Індикатор повідомлення, яке редагується
 */
@Composable
fun EditIndicator(
    editingMessage: Message?,
    onCancelEdit: () -> Unit
) {
    if (editingMessage != null) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            color = Color(0xFFFFF3E0), // Помаранчевий відтінок для редагування
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(40.dp)
                        .background(
                            Color(0xFFFF9800), // Помаранчевий
                            RoundedCornerShape(2.dp)
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Редагування",
                            tint = Color(0xFFFF9800),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Редагування повідомлення",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFFF9800)
                        )
                    }
                    Text(
                        text = editingMessage.decryptedText ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                IconButton(onClick = onCancelEdit) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Скасувати редагування",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * ❤️ Панель вибору реакцій емоджі (з'являється при довгому тапі)
 */
@Composable
fun ReactionPicker(
    onReactionSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val reactions = listOf("❤️", "👍", "😂", "😮", "😢", "🙏", "🔥", "👏")

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = Modifier.padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            reactions.forEach { emoji ->
                Surface(
                    onClick = {
                        onReactionSelected(emoji)
                        onDismiss()
                    },
                    shape = CircleShape,
                    modifier = Modifier.size(44.dp),
                    color = Color.Transparent
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = emoji,
                            fontSize = 28.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * 👍 Показ реакцій під повідомленням
 */
@Composable
fun MessageReactions(
    reactions: List<ReactionGroup>,
    onReactionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (reactions.isNotEmpty()) {
        Row(
            modifier = modifier
                .padding(top = 4.dp, start = 8.dp)
                .wrapContentWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            reactions.forEach { reactionGroup ->
                // ✨ Анімація scale для реакцій
                var isVisible by remember { mutableStateOf(false) }
                val scale by animateFloatAsState(
                    targetValue = if (isVisible) 1f else 0.5f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )

                LaunchedEffect(Unit) {
                    isVisible = true
                }

                Surface(
                    onClick = { onReactionClick(reactionGroup.emoji) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (reactionGroup.hasMyReaction) {
                        Color(0xFF0084FF).copy(alpha = 0.2f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    border = if (reactionGroup.hasMyReaction) {
                        BorderStroke(1.dp, Color(0xFF0084FF))
                    } else null,
                    modifier = Modifier
                        .height(24.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = reactionGroup.emoji,
                            fontSize = 12.sp
                        )
                        if (reactionGroup.count > 1) {
                            Text(
                                text = reactionGroup.count.toString(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (reactionGroup.hasMyReaction) {
                                    Color(0xFF0084FF)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * ✓✓ Індикатор статусу повідомлення (прочитано/доставлено)
 * Покращена видимість: більший розмір, яскравіші кольори, тінь
 */
@Composable
fun MessageStatusIcon(
    isRead: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .shadow(
                elevation = 1.dp,
                shape = CircleShape
            )
            .background(
                color = Color.White.copy(alpha = 0.3f),
                shape = CircleShape
            )
            .padding(horizontal = 3.dp, vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy((-6).dp)  // Накладання галочок
    ) {
        // Перша галочка - більший розмір і краща видимість
        Icon(
            imageVector = Icons.Default.Done,
            contentDescription = if (isRead) "Прочитано" else "Відправлено",
            tint = if (isRead) Color(0xFF0084FF) else Color(0xFF8E8E93),  // Світліший сірий
            modifier = Modifier.size(16.dp)  // Збільшено з 14dp до 16dp
        )
        // Друга галочка (тільки коли доставлено або прочитано)
        Icon(
            imageVector = Icons.Default.Done,
            contentDescription = if (isRead) "Прочитано" else "Доставлено",
            tint = if (isRead) Color(0xFF0084FF) else Color(0xFF8E8E93),  // Світліший сірий
            modifier = Modifier.size(16.dp)  // Збільшено з 14dp до 16dp
        )
    }
}

/**
 * Перевірка чи URL вказує на зображення
 */
private fun isImageUrl(url: String): Boolean {
    val imageExtensions = listOf(".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp")
    val lowerUrl = url.lowercase()
    return imageExtensions.any { lowerUrl.contains(it) } ||
            lowerUrl.contains("image") ||
            lowerUrl.contains("/img/") ||
            lowerUrl.contains("/images/")
}

/**
 * 📳 Вібрація при активації режиму вибору
 */
fun performSelectionVibration(context: Context) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Короткий подвійний імпульс: 50ms → пауза 30ms → 50ms
                val timings = longArrayOf(0, 50, 30, 50)
                val amplitudes = intArrayOf(0, 150, 0, 200)
                it.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(100) // Проста вібрація 100ms для старих версій
            }
        }
    } catch (e: Exception) {
        Log.e("MessagesScreen", "Помилка вібрації: ${e.message}")
    }
}

/**
 * 📶 Banner якості з'єднання (показується при поганому з'єднанні)
 */
@Composable
fun ConnectionQualityBanner(quality: NetworkQualityMonitor.ConnectionQuality) {
    // Показуємо banner тільки якщо з'єднання не EXCELLENT
    if (quality == NetworkQualityMonitor.ConnectionQuality.EXCELLENT) {
        return
    }

    val (text, color, icon) = when (quality) {
        NetworkQualityMonitor.ConnectionQuality.GOOD ->
            Triple(
                "🟡 Добре з'єднання. Медіа завантажуються як превью.",
                Color(0xFFFFA500),
                Icons.Default.SignalCellularAlt
            )
        NetworkQualityMonitor.ConnectionQuality.POOR ->
            Triple(
                "🟠 Погане з'єднання. Завантажується тільки текст.",
                Color(0xFFFF6B6B),
                Icons.Default.SignalCellularAlt
            )
        NetworkQualityMonitor.ConnectionQuality.OFFLINE ->
            Triple(
                "🔴 Немає з'єднання. Показуються кешовані повідомлення.",
                Color(0xFFE74C3C),
                Icons.Default.WifiOff
            )
        else -> return // Не показуємо для EXCELLENT
    }

    AnimatedVisibility(
        visible = true,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            color = color.copy(alpha = 0.15f),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = text,
                    fontSize = 14.sp,
                    color = color,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

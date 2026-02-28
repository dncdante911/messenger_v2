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
    onRequestVideoPermissions: () -> Boolean = { true }  // Default для preview
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
                    val isGroupAdmin = currentGroup?.isAdmin == true || currentGroup?.isOwner == true
                    if (isGroup && isGroupAdmin) {
                        // Admin gets a choice: for me / for all
                        android.app.AlertDialog.Builder(context)
                            .setTitle("Очистити історію")
                            .setMessage("Виберіть тип очищення:")
                            .setPositiveButton("Тільки для мене") { _, _ ->
                                viewModel.clearChatHistory(
                                    onSuccess = { android.widget.Toast.makeText(context, "Твою історію очищено", android.widget.Toast.LENGTH_SHORT).show() },
                                    onError   = { e -> android.widget.Toast.makeText(context, e, android.widget.Toast.LENGTH_SHORT).show() }
                                )
                            }
                            .setNeutralButton("Для всіх учасників") { _, _ ->
                                viewModel.clearGroupHistoryForAll(
                                    onSuccess = { android.widget.Toast.makeText(context, "Історію очищено для всіх", android.widget.Toast.LENGTH_SHORT).show() },
                                    onError   = { e -> android.widget.Toast.makeText(context, e, android.widget.Toast.LENGTH_SHORT).show() }
                                )
                            }
                            .setNegativeButton("Скасувати", null)
                            .show()
                    } else {
                        viewModel.clearChatHistory(
                            onSuccess = { android.widget.Toast.makeText(context, "Історію очищено", android.widget.Toast.LENGTH_SHORT).show() },
                            onError   = { e -> android.widget.Toast.makeText(context, e, android.widget.Toast.LENGTH_SHORT).show() }
                        )
                    }
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
                    onVideoCameraClick = { if (onRequestVideoPermissions()) showVideoMessageRecorder = true },
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

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
import androidx.compose.foundation.lazy.itemsIndexed
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
import kotlin.math.sin
import androidx.compose.ui.geometry.Offset
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
import com.worldmates.messenger.data.SavedMessageItem
import com.worldmates.messenger.data.SavedMessagesManager
import com.worldmates.messenger.ui.media.ImageGalleryViewer
import com.worldmates.messenger.ui.media.InlineVideoPlayer
import com.worldmates.messenger.ui.media.MiniAudioPlayer
import com.worldmates.messenger.ui.media.FullscreenVideoPlayer
import com.worldmates.messenger.data.model.Message
import com.worldmates.messenger.data.model.ReactionGroup
import com.worldmates.messenger.data.model.isOwner
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.network.FileManager
import com.worldmates.messenger.network.NetworkQualityMonitor
import com.worldmates.messenger.ui.theme.rememberThemeState
import com.worldmates.messenger.ui.theme.PresetBackground
import com.worldmates.messenger.ui.theme.AnimatedBgPrefs
import com.worldmates.messenger.ui.theme.AnimatedBgVariant
import com.worldmates.messenger.ui.theme.ChatAnimatedBackground
import com.worldmates.messenger.ui.theme.DefaultChatBackground
import com.worldmates.messenger.ui.components.UserProfileMenuSheet
import com.worldmates.messenger.ui.components.UserMenuData
import com.worldmates.messenger.ui.components.UserMenuAction
import com.worldmates.messenger.ui.components.ReportUserDialog
import com.worldmates.messenger.ui.preferences.rememberBubbleStyle
import com.worldmates.messenger.ui.preferences.rememberQuickReaction
import com.worldmates.messenger.ui.preferences.rememberUIStyle
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import com.worldmates.messenger.utils.VoiceRecorder
import com.worldmates.messenger.utils.VoicePlayer
import com.worldmates.messenger.utils.SpeechCommandManager
import com.worldmates.messenger.utils.ShakeDetector
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// 🔥 Імпорти нових компонентів для режиму вибору повідомлень
import com.worldmates.messenger.ui.messages.selection.SelectionBottomBar
import com.worldmates.messenger.ui.messages.selection.SelectionTopBarActions
import com.worldmates.messenger.ui.messages.selection.MediaActionMenu
import com.worldmates.messenger.ui.messages.selection.QuickReactionAnimation
import com.worldmates.messenger.ui.messages.selection.ForwardMessageDialog

// 📌 Імпорт компонента закріпленого повідомлення
import com.worldmates.messenger.ui.groups.components.PinnedMessageBanner

// 🔒 Secret chat imports
import com.worldmates.messenger.data.SecretChatManager
import com.worldmates.messenger.ui.messages.SelfDestructTimerDialog
import com.worldmates.messenger.ui.messages.SecretChatTimerBadge

// 🗑️ Media auto-delete imports
import com.worldmates.messenger.data.model.MediaAutoDeleteOption
import com.worldmates.messenger.ui.messages.MediaAutoDeleteDialog

// 🔍 Імпорт компонента пошуку
import com.worldmates.messenger.ui.messages.components.GroupSearchBar
import com.worldmates.messenger.ui.search.MediaSearchScreen
import com.worldmates.messenger.ui.messages.components.ExportChatBottomSheet
import com.worldmates.messenger.ui.messages.components.CreatePollDialog
import com.worldmates.messenger.network.NodeRetrofitClient

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
import com.worldmates.messenger.ui.components.media.MediaAlbumComponent
import com.worldmates.messenger.ui.components.media.AudioAlbumComponent
import com.worldmates.messenger.ui.components.media.AudioItem
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import com.worldmates.messenger.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
    isBusinessChat: Boolean = false,
    isBotChat: Boolean = false,
    botDescription: String? = null,
    onBackPressed: () -> Unit,
    onRequestAudioPermission: () -> Boolean = { true },  // Default для preview
    onRequestVideoPermissions: () -> Boolean = { true }  // Default для preview
) {
    val messages by viewModel.messages.collectAsState()
    val deletingMessages by viewModel.deletingMessages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val uploadProgress by viewModel.uploadProgress.collectAsState()
    val recordingState by voiceRecorder.recordingState.collectAsState()
    val recordingDuration by voiceRecorder.recordingDuration.collectAsState()
    val presenceStatus by viewModel.presenceStatus.collectAsState()
    val connectionQuality by viewModel.connectionQuality.collectAsState()
    val pinnedPrivateMessage by viewModel.pinnedPrivateMessage.collectAsState()
    val isMutedPrivate by viewModel.isMutedPrivate.collectAsState()
    val mediaAutoDeleteOptionState by viewModel.mediaAutoDeleteOption.collectAsState()

    // 🤖 Bot chat: detect from intent flag OR from messages that carry bot_id.
    // This way the START button appears even when opened from Chats/Search, not just Bot Store.
    val hasMessagesWithBotId = messages.any { it.botId != null }
    val effectiveIsBotChat = isBotChat || hasMessagesWithBotId

    // Track whether the user has already started the bot (any messages loaded → yes).
    // rememberSaveable persists across recompositions; becomes true once messages load or START pressed.
    var botStarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(messages, isLoading) {
        if (!isLoading && messages.isNotEmpty()) botStarted = true
    }
    // Show START bar only when: bot chat + not started + no messages + finished loading.
    // The !isLoading && messages.isEmpty() guard prevents a flash for returning users
    // whose messages are still loading when the screen first renders.
    val showBotStart = effectiveIsBotChat && !botStarted && !isLoading && messages.isEmpty()

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

    // 📤 Export chat sheet
    var showExportSheet by remember { mutableStateOf(false) }
    // 🔒 Secret chat timer dialog
    var showSelfDestructDialog by remember { mutableStateOf(false) }
    // 🗑️ Media auto-delete dialog
    var showMediaAutoDeleteDialog by remember { mutableStateOf(false) }
    // 📊 Create poll dialog
    var showCreatePollDialog by remember { mutableStateOf(false) }

    var messageText by remember { mutableStateOf("") }

    // Загружаем черновик в messageText при изменении
    LaunchedEffect(currentDraft) {
        if (currentDraft.isNotEmpty() && messageText.isEmpty()) {
            messageText = currentDraft
        }
    }
    var showMediaOptions by remember { mutableStateOf(false) }
    var showUnifiedPicker by remember { mutableStateOf(false) }
    var unifiedPickerTab by remember { mutableStateOf(com.worldmates.messenger.ui.components.MediaPickerTab.EMOJI) }
    var showLocationPicker by remember { mutableStateOf(false) }  // 📍 Location Picker
    var showContactPicker by remember { mutableStateOf(false) }  // 📇 Contact Picker
    var showStrapiPicker by remember { mutableStateOf(false) }  // 🛍️ Strapi Content Picker

    // 🎵 Вибір якості аудіо (як в Telegram: стиснутий/оригінальний)
    var showAudioQualityDialog by remember { mutableStateOf(false) }
    var pendingAudioFile by remember { mutableStateOf<java.io.File?>(null) }

    // Video quality selection sheet (Telegram-like)
    var showVideoQualitySheet by remember { mutableStateOf(false) }
    var pendingVideoFile by remember { mutableStateOf<java.io.File?>(null) }

    // Compression progress (collected from ViewModel)
    val isCompressing by viewModel.isCompressing.collectAsState()
    val compressionProgress by viewModel.compressionProgress.collectAsState()

    // 🎯 Режим введення (Swipeable як в Telegram/Viber)
    var currentInputMode by remember { mutableStateOf(InputMode.TEXT) }

    var isCurrentlyTyping by remember { mutableStateOf(false) }
    var selectedMessage by remember { mutableStateOf<Message?>(null) }
    var showContextMenu by remember { mutableStateOf(false) }
    var replyToMessage by remember { mutableStateOf<Message?>(null) }
    var editingMessage by remember { mutableStateOf<Message?>(null) }

    // ✏️ Діалог вибору "де редагувати": для всіх або тільки для мене
    var showEditScopeDialog by remember { mutableStateOf(false) }
    var pendingEditText by remember { mutableStateOf("") }
    var pendingEditMessageId by remember { mutableStateOf(0L) }

    // 🗑️ Діалог підтвердження видалення (тільки для себе / для всіх)
    var messageToDelete by remember { mutableStateOf<Message?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    // 🗑️ Діалог видалення в режимі мульти-вибору
    var showSelectionDeleteDialog by remember { mutableStateOf(false) }

    // ✅ Режим множественного выбора
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedMessages by remember { mutableStateOf(setOf<Long>()) }

    // ✨ Підсвічування повідомлення при навігації по цитаті
    var highlightedMessageId by remember { mutableStateOf<Long?>(null) }

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
    var showReportDialog by remember { mutableStateOf(false) }
    var reportTargetUser by remember { mutableStateOf<UserMenuData?>(null) }

    // ❤️ Быстрая реакция при двойном тапе
    var showQuickReaction by remember { mutableStateOf(false) }
    var quickReactionMessageId by remember { mutableStateOf<Long?>(null) }
    val defaultQuickReaction = rememberQuickReaction()  // Налаштовується в темах

    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val themeState = rememberThemeState()

    // Sync animated background variant from SharedPreferences into the StateFlow
    LaunchedEffect(Unit) { AnimatedBgPrefs.syncFromPrefs(context) }
    val animatedBgVariant by AnimatedBgPrefs.variantFlow.collectAsState()

    // 🎙️ Auto-play next voice message when the current one ends
    val latestMessages by rememberUpdatedState(messages)
    LaunchedEffect(Unit) {
        com.worldmates.messenger.services.MusicPlaybackService.voiceEndedEvent.collect { endedId ->
            // Build a chronological list of voice messages (ascending id = oldest first)
            val voiceMessages = latestMessages.filter { msg ->
                msg.type?.lowercase() == "voice" ||
                msg.mediaType?.lowercase() == "voice" ||
                msg.mediaFileName?.startsWith("VOICE_", ignoreCase = true) == true ||
                (msg.mediaUrl ?: "").substringAfterLast("/").startsWith("VOICE_", ignoreCase = true)
            }.sortedBy { it.id }

            val idx = voiceMessages.indexOfFirst { it.id == endedId }
            if (idx >= 0 && idx + 1 < voiceMessages.size) {
                val next = voiceMessages[idx + 1]
                val nextUrl = next.decryptedMediaUrl ?: next.mediaUrl ?: return@collect
                com.worldmates.messenger.services.MusicPlaybackService.startPlayback(
                    context  = context,
                    audioUrl = nextUrl,
                    title    = context.getString(R.string.voice_message),
                    artist   = "",
                    timestamp = next.timeStamp,
                    iv       = next.iv,
                    tag      = next.tag,
                    messageId = next.id,
                    isVoice  = true
                )
            }
        }
    }

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
        Log.d("MessagesScreen", "Клік на посилання: $url")
        InstantViewActivity.start(context, url, UserSession.accessToken ?: "")
    }

    // 📜 Auto-scroll для автоматичної прокрутки до нових повідомлень
    val listState = rememberLazyListState()

    // Auto-scroll only when a NEW message arrives (newest message id changes).
    // Keying on messages.lastOrNull()?.id means loading older messages via
    // pagination (which prepends to the list, keeping the newest id unchanged)
    // will NOT trigger this effect — so the user's scroll position is preserved.
    // Also guard: only scroll if the user is already near the bottom (index ≤ 2),
    // so we don't forcibly jump them back while they're reading history.
    LaunchedEffect(messages.lastOrNull()?.id) {
        if (messages.isNotEmpty()) {
            val firstVisibleIndex = listState.firstVisibleItemIndex
            if (firstVisibleIndex <= 2) {
                try {
                    listState.animateScrollToItem(index = 0)
                    Log.d("MessagesScreen", "✅ Auto-scrolled to latest message")
                } catch (e: Exception) {
                    Log.e("MessagesScreen", "❌ Auto-scroll error: ${e.message}")
                }
            }
        }
    }

    // ⬆️ Load more when user scrolls near the top (reverseLayout = true → "top" is last item)
    val canLoadMore by viewModel.canLoadMore.collectAsState()
    LaunchedEffect(listState, canLoadMore) {
        snapshotFlow { listState.layoutInfo }
            .collect { layoutInfo ->
                val totalItems = layoutInfo.totalItemsCount
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@collect
                // reverseLayout=true: last rendered item = oldest message. Load when within 5 items of the top.
                if (canLoadMore && totalItems > 0 && lastVisible >= totalItems - 5) {
                    viewModel.loadMore()
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

    // 📸 Album grouping: map albumId -> ordered list of messages in that album
    //    and a set of message IDs that should be SKIPPED (already rendered inside
    //    their album's first cell).
    val albumGroups: Map<Long, List<com.worldmates.messenger.data.model.Message>> = remember(messages) {
        messages
            .filter { it.albumId != null }
            .groupBy { it.albumId ?: 0L }
    }
    val albumSecondaryIds: Set<Long> = remember(albumGroups) {
        albumGroups.flatMap { (_, msgs) ->
            msgs.drop(1).map { it.id }  // skip first message (it is the album renderer)
        }.toSet()
    }

    // Reversed list used both by the LazyColumn and date separator logic
    val reversedMessages = remember(messages) { messages.reversed() }

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

    // Typing indicator with debounce: sends typing=true immediately, auto-stops 3s after last keystroke
    LaunchedEffect(messageText) {
        if (messageText.isNotBlank()) {
            if (!isCurrentlyTyping) {
                viewModel.sendTypingStatus(true)
                isCurrentlyTyping = true
            }
            // When messageText changes the old LaunchedEffect is cancelled, resetting this delay.
            // If the user stops typing for 3s the delay completes and we send typing=false.
            delay(3_000)
            if (isCurrentlyTyping) {
                viewModel.sendTypingStatus(false)
                isCurrentlyTyping = false
            }
        } else if (isCurrentlyTyping) {
            // Field was cleared — stop immediately
            viewModel.sendTypingStatus(false)
            isCurrentlyTyping = false
        }
    }

    // Pending image waiting for "Send / Edit" choice
    var pendingImageFile by remember { mutableStateOf<java.io.File?>(null) }
    var showImagePreviewDialog by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            Log.d("MessagesScreen", "Вибрано зображення: $it")
            val file = fileManager.copyUriToCache(it)
            if (file != null) {
                // Show "Send / Edit" preview instead of uploading immediately
                pendingImageFile = file
                showImagePreviewDialog = true
            } else {
                Log.e("MessagesScreen", "Не вдалося скопіювати зображення")
            }
        }
    }

    // URI temp file for camera capture (TakePicture requires pre-created URI)
    var cameraCaptureUri by remember { mutableStateOf<Uri?>(null) }

    val cameraCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            cameraCaptureUri?.let { uri ->
                Log.d("MessagesScreen", "Знято фото камерою: $uri")
                val file = fileManager.copyUriToCache(uri)
                if (file != null) {
                    pendingImageFile = file
                    showImagePreviewDialog = true
                } else {
                    Log.e("MessagesScreen", "Не вдалося скопіювати фото з камери")
                }
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
                // Show quality selection sheet — user chooses how to send
                pendingVideoFile = file
                showVideoQualitySheet = true
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
                replyToMessage = null
            } else {
                Log.e("MessagesScreen", "Не вдалося скопіювати файл")
            }
        }
    }

    // Для вибору кількох фото/файлів (до 15 штук) — пакетне завантаження
    val multipleFilesPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            if (uris.size > Constants.MAX_FILES_PER_MESSAGE) {
                Log.w("MessagesScreen", "Вибрано занадто багато файлів: ${uris.size}, макс: ${Constants.MAX_FILES_PER_MESSAGE}")
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.max_files_per_message, Constants.MAX_FILES_PER_MESSAGE),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } else {
                Log.d("MessagesScreen", "Пакетне завантаження: ${uris.size} файлів")
                val files = uris.mapNotNull { uri -> fileManager.copyUriToCache(uri) }
                if (files.isNotEmpty()) {
                    viewModel.uploadMultipleAndSendMedia(files)
                }
            }
        }
    }

    // ── Voice command: state ──────────────────────────────────────────────────
    var voiceCommandActive by remember { mutableStateOf(false) }
    // True only while a recording was triggered by voice command (enables auto-send)
    var voiceTriggeredRecording by remember { mutableStateOf(false) }
    val silenceMs by voiceRecorder.continuousSilenceMs.collectAsState()

    // Derive send-countdown displayed in the recording bar (3…2…1 before auto-send)
    val autoSendCountdown: Int = when {
        !voiceTriggeredRecording -> -1
        recordingState !is VoiceRecorder.RecordingState.Recording -> -1
        silenceMs < 600 -> -1          // small pause — don't alarm yet
        silenceMs >= 3000 -> 0         // about to send
        else -> ((3000 - silenceMs) / 1000).toInt() + 1   // 3, 2, 1
    }

    val speechCommandManager = remember { SpeechCommandManager(context) }
    val shakeDetector = remember { ShakeDetector(context) }

    // ── Voice command: lifecycle ──────────────────────────────────────────────
    DisposableEffect(Unit) {
        onDispose {
            speechCommandManager.stop()
            shakeDetector.stop()
        }
    }

    LaunchedEffect(voiceCommandActive) {
        if (voiceCommandActive) speechCommandManager.start()
        else speechCommandManager.stop()
    }

    // Pause recogniser while the mic is occupied
    LaunchedEffect(recordingState) {
        speechCommandManager.setMicBusy(
            recordingState is VoiceRecorder.RecordingState.Recording ||
            recordingState is VoiceRecorder.RecordingState.Paused
        )
    }

    // Collect trigger phrases
    LaunchedEffect(Unit) {
        speechCommandManager.commands.collect { cmd ->
            if (cmd is SpeechCommandManager.Command.StartVoiceMessage &&
                recordingState is VoiceRecorder.RecordingState.Idle &&
                onRequestAudioPermission()) {
                voiceTriggeredRecording = true
                voiceRecorder.silenceDetectionEnabled = true
                voiceRecorder.maxDurationMs =
                    if (UserSession.isProActive) VoiceRecorder.DURATION_PRO_MS
                    else VoiceRecorder.DURATION_FREE_MS
                scope.launch {
                    // Brief pause to let the SpeechRecognizer fully release the mic
                    // before MediaRecorder tries to acquire it (avoids EBUSY / beep errors).
                    delay(250)
                    voiceRecorder.startRecording()
                    if (!isGroup) viewModel.sendRecordingStatus()
                }
            }
        }
    }

    // Auto-send when silence reaches 3 s (only for voice-triggered recordings)
    LaunchedEffect(Unit) {
        snapshotFlow { Triple(voiceTriggeredRecording, recordingState, silenceMs) }
            .collect { (vt, rs, sms) ->
                if (vt && rs is VoiceRecorder.RecordingState.Recording && sms >= 3000L) {
                    voiceTriggeredRecording = false
                    val stopped = voiceRecorder.stopRecording()
                    if (stopped) {
                        val st = voiceRecorder.recordingState.value
                        if (st is VoiceRecorder.RecordingState.Completed) {
                            viewModel.uploadAndSendMedia(java.io.File(st.filePath), "voice")
                        }
                    }
                }
            }
    }

    // Shake detection — only during voice-triggered recordings
    LaunchedEffect(voiceTriggeredRecording) {
        if (voiceTriggeredRecording) shakeDetector.start() else shakeDetector.stop()
    }

    // Shake → cancel voice-triggered recording
    LaunchedEffect(Unit) {
        shakeDetector.shakeEvents.collect {
            if (voiceTriggeredRecording &&
                (recordingState is VoiceRecorder.RecordingState.Recording ||
                 recordingState is VoiceRecorder.RecordingState.Paused)) {
                voiceTriggeredRecording = false
                scope.launch { voiceRecorder.cancelRecording() }
            }
        }
    }

    // ── Main UI ───────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // Застосування фону в залежності від налаштувань
        when {
            // Кастомне зображення
            themeState.backgroundImageUri != null -> {
                Log.d("MessagesScreen", "Applying custom background image: ${themeState.backgroundImageUri}")
                AsyncImage(
                    model = Uri.parse(themeState.backgroundImageUri),
                    contentDescription = stringResource(R.string.chat_background),
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
                    Log.d("MessagesScreen", "Preset found: ${preset.id}")
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
            // Стандартний фон — Telegram-подібний (градієнт + крапковий патерн)
            else -> {
                DefaultChatBackground(modifier = Modifier.fillMaxSize())
            }
        }

        // Animated background layer (shown over the static background if selected)
        if (animatedBgVariant != AnimatedBgVariant.NONE) {
            ChatAnimatedBackground(
                variant = animatedBgVariant,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Seasonal particle overlay for Spring/Winter preset backgrounds
        if (themeState.presetBackgroundId != null) {
            val seasonPreset = PresetBackground.fromId(themeState.presetBackgroundId)
            when (seasonPreset) {
                PresetBackground.SPRING -> SeasonalParticlesOverlay(
                    isSpring = true,
                    modifier = Modifier.fillMaxSize()
                )
                PresetBackground.WINTER -> SeasonalParticlesOverlay(
                    isSpring = false,
                    modifier = Modifier.fillMaxSize()
                )
                else -> {}
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
                presenceStatus = presenceStatus,
                onBackPressed = onBackPressed,
                isBusinessChat = isBusinessChat,
                myUsername = if (!isGroup) UserSession.username.orEmpty() else "",
                onUserProfileClick = {
                    Log.d("MessagesScreen", "Відкриваю профіль: $recipientName (business=$isBusinessChat)")
                    when {
                        isBusinessChat -> {
                            val intent = android.content.Intent(context,
                                com.worldmates.messenger.ui.business.BusinessProfileViewActivity::class.java).apply {
                                putExtra("user_id", viewModel.getRecipientId())
                            }
                            context.startActivity(intent)
                        }
                        !isGroup -> {
                            val intent = android.content.Intent(context,
                                com.worldmates.messenger.ui.profile.UserProfileActivity::class.java).apply {
                                putExtra("user_id", viewModel.getRecipientId())
                            }
                            context.startActivity(intent)
                        }
                        else -> {
                            val intent = android.content.Intent(context,
                                com.worldmates.messenger.ui.groups.GroupDetailsActivity::class.java).apply {
                                putExtra("group_id", viewModel.getGroupId())
                            }
                            context.startActivity(intent)
                        }
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
                        if (currentGroup?.isMuted == true) {
                            viewModel.unmuteGroup(
                                onSuccess = {
                                    android.widget.Toast.makeText(context, context.getString(R.string.notifications_enabled_for, recipientName), android.widget.Toast.LENGTH_SHORT).show()
                                },
                                onError = { error ->
                                    android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                        } else {
                            viewModel.muteGroup(
                                onSuccess = {
                                    android.widget.Toast.makeText(context, context.getString(R.string.notifications_disabled_for, recipientName), android.widget.Toast.LENGTH_SHORT).show()
                                },
                                onError = { error ->
                                    android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    } else {
                        // Приватний чат — перемикаємо через Node.js
                        if (isMutedPrivate) {
                            viewModel.unmutePrivateChat(
                                onSuccess = {
                                    android.widget.Toast.makeText(context, context.getString(R.string.notifications_enabled_for, recipientName), android.widget.Toast.LENGTH_SHORT).show()
                                },
                                onError = { error ->
                                    android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                        } else {
                            viewModel.mutePrivateChat(
                                onSuccess = {
                                    android.widget.Toast.makeText(context, context.getString(R.string.notifications_disabled_for, recipientName), android.widget.Toast.LENGTH_SHORT).show()
                                },
                                onError = { error ->
                                    android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                },
                onClearHistoryClick = {
                    val isGroupAdmin = currentGroup?.isAdmin == true || currentGroup?.isOwner == true
                    if (isGroup && isGroupAdmin) {
                        // Group admin: choice between for me / for all members
                        android.app.AlertDialog.Builder(context)
                            .setTitle(context.getString(R.string.clear_history))
                            .setMessage(context.getString(R.string.search_type_title))
                            .setPositiveButton(context.getString(R.string.for_me_only)) { _, _ ->
                                viewModel.clearChatHistory(
                                    onSuccess = { android.widget.Toast.makeText(context, context.getString(R.string.history_cleared_for_me_toast), android.widget.Toast.LENGTH_SHORT).show() },
                                    onError   = { e -> android.widget.Toast.makeText(context, e, android.widget.Toast.LENGTH_SHORT).show() }
                                )
                            }
                            .setNeutralButton(context.getString(R.string.for_all_members)) { _, _ ->
                                viewModel.clearGroupHistoryForAll(
                                    onSuccess = { android.widget.Toast.makeText(context, context.getString(R.string.history_cleared_for_all_toast), android.widget.Toast.LENGTH_SHORT).show() },
                                    onError   = { e -> android.widget.Toast.makeText(context, e, android.widget.Toast.LENGTH_SHORT).show() }
                                )
                            }
                            .setNegativeButton(context.getString(R.string.cancel), null)
                            .show()
                    } else if (!isGroup) {
                        // Private chat: choice between for me / for both parties
                        android.app.AlertDialog.Builder(context)
                            .setTitle(context.getString(R.string.clear_history))
                            .setMessage(context.getString(R.string.search_type_title))
                            .setPositiveButton(context.getString(R.string.for_me_only)) { _, _ ->
                                viewModel.clearChatHistory(
                                    onSuccess = { android.widget.Toast.makeText(context, context.getString(R.string.history_cleared_for_me_toast), android.widget.Toast.LENGTH_SHORT).show() },
                                    onError   = { e -> android.widget.Toast.makeText(context, e, android.widget.Toast.LENGTH_SHORT).show() }
                                )
                            }
                            .setNeutralButton(context.getString(R.string.for_all_members)) { _, _ ->
                                viewModel.clearPrivateChatHistoryForAll(
                                    onSuccess = { android.widget.Toast.makeText(context, context.getString(R.string.history_cleared_for_all_toast), android.widget.Toast.LENGTH_SHORT).show() },
                                    onError   = { e -> android.widget.Toast.makeText(context, e, android.widget.Toast.LENGTH_SHORT).show() }
                                )
                            }
                            .setNegativeButton(context.getString(R.string.cancel), null)
                            .show()
                    } else {
                        // Non-admin in a group: clear for self only
                        viewModel.clearChatHistory(
                            onSuccess = { android.widget.Toast.makeText(context, context.getString(R.string.history_cleared_for_me_toast), android.widget.Toast.LENGTH_SHORT).show() },
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
                onExportClick = { showExportSheet = true },
                onSelfDestructClick = {
                    if (!isGroup) showSelfDestructDialog = true
                },
                onMediaAutoDeleteClick = {
                    if (!isGroup) showMediaAutoDeleteDialog = true
                },
                isMuted = if (isGroup) currentGroup?.isMuted == true else isMutedPrivate,
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
                            android.widget.Toast.makeText(context, context.getString(R.string.editing_message_label), android.widget.Toast.LENGTH_SHORT).show()
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
                                    android.widget.Toast.makeText(context, context.getString(R.string.message_pinned_toast), android.widget.Toast.LENGTH_SHORT).show()
                                    isSelectionMode = false
                                    selectedMessages = emptySet()
                                },
                                onError = { error ->
                                    android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                        } else {
                            viewModel.pinPrivateMessage(messageId, true)
                            android.widget.Toast.makeText(context, context.getString(R.string.message_pinned_toast), android.widget.Toast.LENGTH_SHORT).show()
                            isSelectionMode = false
                            selectedMessages = emptySet()
                        }
                    }
                },
                onSaveSelected = {
                    // Зберігаємо вибрані повідомлення
                    val chatType = if (isGroup) "group" else "chat"
                    val chatId = if (isGroup) viewModel.getGroupId() else viewModel.getRecipientId()
                    selectedMessages.forEach { messageId ->
                        val msg = messages.find { it.id == messageId } ?: return@forEach
                        SavedMessagesManager.saveMessage(
                            SavedMessageItem(
                                messageId   = msg.id,
                                chatType    = chatType,
                                chatId      = chatId,
                                chatName    = recipientName,
                                senderName  = msg.senderName ?: "",
                                text        = msg.decryptedText ?: msg.encryptedText ?: "",
                                mediaUrl    = msg.mediaUrl,
                                mediaType   = msg.mediaType ?: msg.type?.takeIf { it != "text" },
                                originalTime = msg.timeStamp
                            )
                        )
                    }
                    android.widget.Toast.makeText(context, context.getString(R.string.saved_toast, selectedMessages.size), android.widget.Toast.LENGTH_SHORT).show()
                    isSelectionMode = false
                    selectedMessages = emptySet()
                },
                onDeleteSelected = {
                    // Видаляємо вибрані повідомлення
                    selectedMessages.forEach { messageId ->
                        viewModel.deleteMessage(messageId)
                    }
                    android.widget.Toast.makeText(context, context.getString(R.string.messages_deleted_count, selectedMessages.size), android.widget.Toast.LENGTH_SHORT).show()
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

            // 📌 Pinned Message Banner (for groups only) — supports multi-pin
            if (isGroup) {
                val allPinned = currentGroup?.pinnedMessages?.takeIf { it.isNotEmpty() }
                    ?: listOfNotNull(currentGroup?.pinnedMessage)
                val canUnpin = currentGroup?.isAdmin == true || currentGroup?.isModerator == true

                allPinned.forEachIndexed { index, pinnedMsg ->
                    val decryptedText = pinnedMsg.decryptedText ?: pinnedMsg.encryptedText ?: ""
                    PinnedMessageBanner(
                        pinnedMessage = pinnedMsg,
                        decryptedText = decryptedText,
                        onBannerClick = {
                            val messageIndex = messages.indexOfFirst { it.id == pinnedMsg.id }
                            if (messageIndex != -1) {
                                val reversedIndex = messages.size - messageIndex - 1
                                scope.launch { listState.animateScrollToItem(reversedIndex) }
                                android.widget.Toast.makeText(context, context.getString(R.string.message_pinned_toast), android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                android.widget.Toast.makeText(context, context.getString(R.string.pinned_message_not_found), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        onUnpinClick = {
                            viewModel.unpinGroupMessage(
                                messageId = pinnedMsg.id,
                                onSuccess = {
                                    android.widget.Toast.makeText(context, context.getString(R.string.message_unpinned_toast), android.widget.Toast.LENGTH_SHORT).show()
                                },
                                onError = { error ->
                                    android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                        canUnpin = canUnpin
                    )
                }
            }

            // 🔒 Secret chat timer badge (private chats only)
            if (!isGroup) {
                val chatId = viewModel.getRecipientId()
                if (SecretChatManager.getTimer(chatId, "user") > 0L) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f))
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        SecretChatTimerBadge(
                            chatId = chatId,
                            chatType = "user",
                            onClick = { showSelfDestructDialog = true }
                        )
                    }
                }
            }

            // 📌 Pinned Message Banner для особистих чатів
            if (!isGroup) pinnedPrivateMessage?.let { pinnedMsg ->
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
                        android.widget.Toast.makeText(context, context.getString(R.string.message_unpinned_toast), android.widget.Toast.LENGTH_SHORT).show()
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
                    title = { Text(stringResource(R.string.search_type_title)) },
                    text = {
                        Column {
                            Text(stringResource(R.string.text_search_desc))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(R.string.media_search_desc))
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showSearchTypeDialog = false
                                showMediaSearch = true
                            }
                        ) {
                            Text(stringResource(R.string.media_search))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showSearchTypeDialog = false
                                showSearchBar = true
                            }
                        ) {
                            Text(stringResource(R.string.text_search))
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
                                        context.getString(R.string.scroll_to_see_image),
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            "video" -> {
                                // Videos are shown inline - scroll to message or show toast
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(R.string.scroll_to_play_video),
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                showMediaSearch = false
                            }
                            else -> {
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(R.string.file_type_in_dev, message.type),
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )
            }

            // 🎙️ Sticky voice mini-bar (Telegram-style)
            // Shows when the playing voice message is scrolled out of the visible viewport.
            val voiceTrack    by com.worldmates.messenger.services.MusicPlaybackService.currentTrackInfo.collectAsState()
            val voicePlayback by com.worldmates.messenger.services.MusicPlaybackService.playbackState.collectAsState()
            val visibleItemKeys = remember(listState.layoutInfo) {
                listState.layoutInfo.visibleItemsInfo.map { it.key }.toSet()
            }
            val showVoiceBar = voiceTrack.isVoice &&
                voiceTrack.url.isNotEmpty() &&
                !visibleItemKeys.contains(voiceTrack.messageId)

            AnimatedVisibility(
                visible = showVoiceBar,
                enter = slideInVertically(
                    initialOffsetY = { -it },
                    animationSpec = tween(200, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(150)),
                exit = slideOutVertically(
                    targetOffsetY = { -it },
                    animationSpec = tween(180, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(120))
            ) {
                VoiceMessageStickyBar(
                    title = voiceTrack.title,
                    isPlaying = voicePlayback.isPlaying,
                    currentPositionMs = voicePlayback.currentPosition,
                    durationMs = voicePlayback.duration,
                    playbackSpeed = VoicePlaybackState.speed,
                    onPlayPause = {
                        if (voicePlayback.isPlaying)
                            com.worldmates.messenger.services.MusicPlaybackService.pausePlayback(context)
                        else
                            com.worldmates.messenger.services.MusicPlaybackService.resumePlayback(context)
                    },
                    onSpeedChange = { newSpeed ->
                        VoicePlaybackState.speed = newSpeed
                        com.worldmates.messenger.services.MusicPlaybackService.setSpeed(newSpeed)
                    },
                    onClose = {
                        com.worldmates.messenger.services.MusicPlaybackService.stopPlayback(context)
                    },
                    onTap = {
                        val idx = reversedMessages.indexOfFirst { it.id == voiceTrack.messageId }
                        if (idx >= 0) scope.launch { listState.animateScrollToItem(idx) }
                    }
                )
            }

            // Messages List
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,  // 🔥 Додано для auto-scroll
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                reverseLayout = true,
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                itemsIndexed(
                    items = reversedMessages,
                    key = { _, msg -> msg.id }
                ) { index, message ->
                    // 🖼️ Skip secondary album messages — they are rendered inside the album block
                    if (message.id in albumSecondaryIds) return@itemsIndexed

                    // 📅 Floating date separator: show when day changes going upward
                    val nextMsg = reversedMessages.getOrNull(index + 1)
                    if (nextMsg == null || !chatIsSameDay(message.timeStamp, nextMsg.timeStamp)) {
                        FloatingDateChip(message.timeStamp)
                    }

                    // 💫 Thanos disintegration when the message is being deleted
                    ThanosDisintegrationEffect(
                        isDisintegrating = message.id in deletingMessages,
                        seed = message.id
                    ) {
                    // ✨ Анімація появи повідомлення (animateItem handles placement)
                    Box(modifier = Modifier.animateItem()) {
                        // 📸 Album rendering: first message of an album → MediaAlbumComponent
                        val albumId = message.albumId
                        if (albumId != null) {
                            val albumMessages = albumGroups[albumId] ?: listOf(message)
                            val isOwn = message.fromId == com.worldmates.messenger.data.UserSession.userId
                            val albumMediaUrls  = albumMessages.mapNotNull {
                                it.decryptedMediaUrl ?: it.mediaUrl
                            }
                            val albumMediaTypes = albumMessages.map { msg ->
                                val t = msg.typeTwo?.lowercase() ?: msg.type?.lowercase() ?: ""
                                when {
                                    t.contains("video") -> "video"
                                    else                -> "image"
                                }
                            }
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                contentAlignment = if (isOwn) Alignment.CenterEnd else Alignment.CenterStart
                            ) {
                                MediaAlbumComponent(
                                    mediaUrls  = albumMediaUrls,
                                    mediaTypes = albumMediaTypes,
                                    onMediaClick = { index ->
                                        val url = albumMediaUrls.getOrNull(index)
                                        if (url != null) {
                                            clickedImageUrl    = url
                                            selectedImageIndex = imageUrls.indexOf(url).coerceAtLeast(0)
                                            showImageGallery   = true
                                        }
                                    },
                                    modifier = Modifier.widthIn(max = 280.dp)
                                )
                            }
                        } else {
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
                            isHighlighted = highlightedMessageId == message.id,
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
                            // 👤 Параметри для відображення імені (групові та приватні чати)
                            isGroup = isGroup,
                            recipientName = if (!isGroup) recipientName else "",
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
                            viewModel = viewModel,
                            onReplyClick = { replyId ->
                                val idx = reversedMessages.indexOfFirst { it.id == replyId }
                                if (idx >= 0) scope.launch {
                                    listState.animateScrollToItem(idx)
                                    // Flash-highlight the target message (Telegram-style)
                                    highlightedMessageId = replyId
                                    kotlinx.coroutines.delay(1400)
                                    highlightedMessageId = null
                                }
                            },
                            onBotButtonClick = { botId, msgId, button ->
                                when (button.type) {
                                    com.worldmates.messenger.data.model.BotButtonType.CALLBACK -> {
                                        viewModel.sendBotCallbackQuery(botId, msgId, button.callbackData ?: "")
                                    }
                                    com.worldmates.messenger.data.model.BotButtonType.URL -> {
                                        val url = button.url
                                        if (url != null) {
                                            try {
                                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                android.util.Log.w("MessagesScreen", "Cannot open URL: $url")
                                            }
                                        }
                                    }
                                    else -> {}
                                }
                            }
                        )
                        }  // Закриття else (album/normal branch)
                    }  // Закриття AnimatedVisibility
                    }  // Закриття ThanosDisintegrationEffect
                }
            }  // LazyColumn

            // 🤖 Bot info card — shown in the message area when chat is empty and not yet started
            if (showBotStart && messages.isEmpty() && !isLoading) {
                com.worldmates.messenger.ui.bots.BotStartOverlay(
                    botName = recipientName,
                    botDescription = botDescription,
                    botAvatar = recipientAvatar.ifEmpty { null },
                    onStartClick = {},  // START action is in BotStartBar at the bottom
                    modifier = Modifier.fillMaxSize()
                )
            }
            }  // Box

            // 📸 ГАЛЕРЕЯ ФОТО
            var showPhotoEditor by remember { mutableStateOf(false) }
            var editImageUrl by remember { mutableStateOf<String?>(null) }

            // 🖼️ "Send / Edit" preview dialog after image picker
            if (showImagePreviewDialog && pendingImageFile != null) {
                AlertDialog(
                    onDismissRequest = {
                        showImagePreviewDialog = false
                        pendingImageFile?.delete()
                        pendingImageFile = null
                    },
                    title = { Text(stringResource(R.string.preview_before_send)) },
                    text = {
                        coil.compose.AsyncImage(
                            model = pendingImageFile,
                            contentDescription = null,
                            modifier = androidx.compose.ui.Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showImagePreviewDialog = false
                            pendingImageFile?.let { viewModel.uploadAndSendMedia(it, "image") }
                            pendingImageFile = null
                            replyToMessage = null
                        }) {
                            Text(stringResource(R.string.send_original))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showImagePreviewDialog = false
                            editImageUrl = pendingImageFile?.absolutePath
                            showPhotoEditor = true
                            pendingImageFile = null
                        }) {
                            Text(stringResource(R.string.edit_before_send))
                        }
                    }
                )
            }

            if (showImageGallery && !showPhotoEditor) {
                // Build gallery: always include the tapped URL (imageUrls may miss it)
                val galleryUrls = remember(imageUrls, clickedImageUrl) {
                    val clicked = clickedImageUrl
                    when {
                        clicked == null -> imageUrls
                        imageUrls.contains(clicked) -> imageUrls
                        else -> listOf(clicked) + imageUrls.filter { it != clicked }
                    }
                }
                val galleryIndex = remember(galleryUrls, clickedImageUrl) {
                    val idx = galleryUrls.indexOf(clickedImageUrl)
                    if (idx >= 0) idx else 0
                }

                if (galleryUrls.isNotEmpty()) {
                    Log.d("MessagesScreen", "✅ ImageGalleryViewer: ${galleryUrls.size} фото, page $galleryIndex")
                    ImageGalleryViewer(
                        imageUrls = galleryUrls,
                        initialPage = galleryIndex,
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
                    Log.e("MessagesScreen", "⚠️ showImageGallery=true але немає фото")
                    showImageGallery = false
                }
            }

            // 🎨 ФОТОРЕДАКТОР
            if (showPhotoEditor) editImageUrl?.let { editorUrl ->
                com.worldmates.messenger.ui.editor.PhotoEditorScreen(
                    imageUrl = editorUrl,
                    onDismiss = {
                        showPhotoEditor = false
                        editImageUrl = null
                    },
                    onSave = { savedFile ->
                        // Upload the edited photo and send it as a new message
                        viewModel.uploadAndSendMedia(savedFile, "image")
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.edited_photo_sending),
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
                    isPremiumUser = com.worldmates.messenger.data.UserSession.isProActive,
                    onVideoRecorded = { videoFile ->
                        Log.d("MessagesScreen", "📹 Відео записано: ${videoFile.absolutePath}")
                        showVideoMessageRecorder = false
                        // Recorded in-app clips always use VIDEO_MESSAGE quality (480p, compact)
                        viewModel.uploadAndSendVideo(
                            file       = videoFile,
                            quality    = com.worldmates.messenger.utils.VideoCompressor.Quality.VIDEO_MESSAGE,
                            sendAsFile = false,
                        )
                    },
                    onCancel = {
                        Log.d("MessagesScreen", "❌ Запис відео скасовано")
                        showVideoMessageRecorder = false
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Message Context Menu Bottom Sheet
            if (showContextMenu) selectedMessage?.let { msg ->
                MessageContextMenu(
                    message = msg,
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
                        android.widget.Toast.makeText(context, context.getString(R.string.message_pinned_toast), android.widget.Toast.LENGTH_SHORT).show()
                    },
                    isPrivateChat = !isGroup,
                    onCopy = { message ->
                        message.decryptedText?.let {
                            clipboardManager.setText(AnnotatedString(it))
                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.text_copied),
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                        showContextMenu = false
                        selectedMessage = null
                    }
                )
            }

            // 🗑️ Діалог підтвердження видалення повідомлення
            MessagesDeletionDialogs(
                showDeleteDialog = showDeleteDialog,
                messageToDelete = messageToDelete,
                showEditScopeDialog = showEditScopeDialog,
                showSelectionDeleteDialog = showSelectionDeleteDialog,
                selectedCount = selectedMessages.size,
                onDeleteForEveryone = {
                    messageToDelete?.let { viewModel.deleteMessage(it.id, "everyone") }
                    showDeleteDialog = false
                    messageToDelete = null
                },
                onDeleteForMe = {
                    messageToDelete?.let { viewModel.deleteMessage(it.id, "just_me") }
                    showDeleteDialog = false
                    messageToDelete = null
                },
                onDeleteDismiss = {
                    showDeleteDialog = false
                    messageToDelete = null
                },
                onEditForEveryone = {
                    viewModel.editMessage(pendingEditMessageId, pendingEditText)
                    messageText = ""
                    viewModel.updateDraftText("")
                    editingMessage = null
                    showEditScopeDialog = false
                },
                onEditForMe = {
                    viewModel.editMessageLocally(pendingEditMessageId, pendingEditText)
                    messageText = ""
                    viewModel.updateDraftText("")
                    editingMessage = null
                    showEditScopeDialog = false
                },
                onEditDismiss = { showEditScopeDialog = false },
                onSelectionDeleteForEveryone = {
                    selectedMessages.forEach { id -> viewModel.deleteMessage(id, "everyone") }
                    isSelectionMode = false
                    selectedMessages = emptySet()
                    showSelectionDeleteDialog = false
                },
                onSelectionDeleteForMe = {
                    selectedMessages.forEach { id -> viewModel.deleteMessage(id, "just_me") }
                    isSelectionMode = false
                    selectedMessages = emptySet()
                    showSelectionDeleteDialog = false
                },
                onSelectionDeleteDismiss = { showSelectionDeleteDialog = false },
            )

            // 👤 User Profile Menu (при кліку на ім'я в групі)
            if (showUserProfileMenu) selectedUserForMenu?.let { menuUser ->
                UserProfileMenuSheet(
                    user = menuUser,
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
                                        putExtra("user_id", menuUser.userId)
                                    }
                                )
                            }
                            is UserMenuAction.SendMessage -> {
                                // Відкриваємо приватний чат з користувачем
                                context.startActivity(
                                    android.content.Intent(context, com.worldmates.messenger.ui.messages.MessagesActivity::class.java).apply {
                                        putExtra("recipient_id", menuUser.userId)
                                        putExtra("recipient_name", menuUser.name ?: menuUser.username)
                                        putExtra("recipient_avatar", menuUser.avatar ?: "")
                                    }
                                )
                            }
                            is UserMenuAction.CopyUsername -> {
                                clipboardManager.setText(AnnotatedString("@${menuUser.username}"))
                                android.widget.Toast.makeText(context, context.getString(R.string.username_copied_toast), android.widget.Toast.LENGTH_SHORT).show()
                            }
                            is UserMenuAction.Report -> {
                                reportTargetUser = menuUser
                                showReportDialog = true
                            }
                            else -> {
                                android.widget.Toast.makeText(context, "Дія: $action", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                        showUserProfileMenu = false
                        selectedUserForMenu = null
                    },
                    showChatOptions = false
                )
            }

            // 🚩 Report user dialog
            if (showReportDialog) reportTargetUser?.let { target ->
                ReportUserDialog(
                    userName = target.username,
                    onDismiss = {
                        showReportDialog = false
                        reportTargetUser = null
                    },
                    onReport = { reason, details ->
                        val userId = target.userId
                        showReportDialog = false
                        reportTargetUser = null
                        scope.launch {
                            try {
                                NodeRetrofitClient.api.reportUser(
                                    userId = userId,
                                    text   = "$reason: $details".trim().trimEnd(':').trim()
                                )
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(R.string.report_sent),
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            } catch (_: Exception) {}
                        }
                    }
                )
            }

            // ── Upload Progress ────────────────────────────────────────────
            AnimatedVisibility(visible = uploadProgress in 1..99) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(
                        progress    = { uploadProgress / 100f },
                        modifier    = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        trackColor  = MaterialTheme.colorScheme.surfaceVariant,
                        color       = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text     = stringResource(R.string.upload_progress_label, uploadProgress),
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )
                }
            }

            // Reply Indicator
            ReplyIndicator(
                replyToMessage = replyToMessage,
                onCancelReply = { replyToMessage = null },
                recipientName = recipientName
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

            // Mini bar and full player only for music (not voice messages)
            if (musicServiceTrack.url.isNotEmpty() && !musicServiceTrack.isVoice) {
                com.worldmates.messenger.ui.music.MusicMiniBar(
                    onExpand = { showExpandedMusicPlayer = true },
                    onStop = { /* сервіс зупинено */ }
                )
            }

            // Повноекранний плеєр з міні-бара (тільки для музики)
            if (showExpandedMusicPlayer && musicServiceTrack.url.isNotEmpty() && !musicServiceTrack.isVoice) {
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
                        onForward = { showForwardDialog = true },
                        onReply = {
                            if (selectedMessages.size == 1) {
                                val messageId = selectedMessages.first()
                                replyToMessage = messages.find { it.id == messageId }
                                isSelectionMode = false
                                selectedMessages = emptySet()
                            }
                        },
                        onDelete = { showSelectionDeleteDialog = true }
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

            // 🤖 Bot START bar — bottom CTA replacing input until user starts the bot
            if (showBotStart && !isSelectionMode) {
                com.worldmates.messenger.ui.bots.BotStartBar(
                    botName = recipientName,
                    onStartClick = {
                        botStarted = true
                        viewModel.sendMessage("/start")
                    }
                )
            }

            // Message Input (hidden in selection mode, hidden when showing bot start bar)
            if (!isSelectionMode && !showBotStart) {
                MessageInputBar(
                    currentInputMode = currentInputMode,
                    onInputModeChange = { newMode ->
                        currentInputMode = newMode
                        if (newMode !in listOf(InputMode.TEXT, InputMode.VOICE, InputMode.VIDEO)) {
                            showUnifiedPicker = false
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
                                // Show scope dialog: edit for everyone vs just for me
                                pendingEditMessageId = editingMessage?.id ?: 0L
                                pendingEditText = messageText
                                showEditScopeDialog = true
                            } else {
                                // Надсилаємо нове повідомлення
                                viewModel.sendMessage(messageText, replyToMessage)
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
                                replyToMessage = null
                            }
                        }
                    },
                    onShowMediaOptions = { showMediaOptions = !showMediaOptions },
                    onPickImage = { multipleFilesPickerLauncher.launch("image/*") },
                    onPickVideo = { videoPickerLauncher.launch("video/*") },
                    onPickAudio = { audioPickerLauncher.launch("audio/*") },
                    onPickFile = { filePickerLauncher.launch("*/*") },
                    onCameraClick = {
                        try {
                            val photoFile = File.createTempFile(
                                "camera_${System.currentTimeMillis()}", ".jpg", context.cacheDir
                            )
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                photoFile
                            )
                            cameraCaptureUri = uri
                            cameraCaptureLauncher.launch(uri)
                        } catch (e: Exception) {
                            Log.e("MessagesScreen", "Не вдалося запустити камеру: ${e.message}")
                        }
                    },
                    onBatchClick = { multipleFilesPickerLauncher.launch("image/*") },
                    onVideoCameraClick = { if (onRequestVideoPermissions()) showVideoMessageRecorder = true },
                    showMediaOptions = showMediaOptions,
                    showUnifiedPicker = showUnifiedPicker,
                    onToggleUnifiedPicker = { tab ->
                        if (showUnifiedPicker && unifiedPickerTab == tab) {
                            showUnifiedPicker = false
                        } else {
                            unifiedPickerTab = tab
                            showUnifiedPicker = true
                        }
                    },
                    showLocationPicker = showLocationPicker,
                    onToggleLocationPicker = { showLocationPicker = !showLocationPicker },
                    showContactPicker = showContactPicker,
                    onToggleContactPicker = { showContactPicker = !showContactPicker },
                    showStrapiPicker = showStrapiPicker,
                    onToggleStrapiPicker = { showStrapiPicker = !showStrapiPicker },
                    onPollClick = if (isGroup) { { showCreatePollDialog = true } } else null,
                    onRequestAudioPermission = onRequestAudioPermission,
                    viewModel = viewModel,
                    formattingSettings = formattingSettings,
                    voiceCommandActive = voiceCommandActive,
                    onToggleVoiceCommand = { voiceCommandActive = !voiceCommandActive },
                    autoSendCountdown = autoSendCountdown
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
                            text = stringResource(R.string.draft_saving),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }  // Закриття if (!isSelectionMode)

            // 🔒 Secret chat self-destruct timer dialog
            if (showSelfDestructDialog && !isGroup) {
                val chatId = viewModel.getRecipientId()
                SelfDestructTimerDialog(
                    currentTimer = SecretChatManager.getTimer(chatId, "user"),
                    onTimerSelected = { seconds ->
                        SecretChatManager.setTimer(chatId, "user", seconds)
                    },
                    onDismiss = { showSelfDestructDialog = false }
                )
            }

            // 🗑️ Media auto-delete dialog
            if (showMediaAutoDeleteDialog && !isGroup) {
                MediaAutoDeleteDialog(
                    currentOption = mediaAutoDeleteOptionState,
                    onOptionSelected = { option ->
                        viewModel.saveMediaAutoDeleteSetting(option)
                    },
                    onDismiss = { showMediaAutoDeleteDialog = false }
                )
            }

            // 📤 Export chat bottom sheet (client-side, uses already-decrypted messages)
            if (showExportSheet) {
                ExportChatBottomSheet(
                    chatName = recipientName,
                    isGroup = isGroup,
                    messages = messages,
                    onDismiss = { showExportSheet = false }
                )
            }

            // 📊 Create poll dialog (groups only)
            if (showCreatePollDialog) {
                CreatePollDialog(
                    onDismiss = { showCreatePollDialog = false },
                    onCreate = { question, options, isAnonymous, allowsMultiple ->
                        showCreatePollDialog = false
                        scope.launch {
                            try {
                                NodeRetrofitClient.groupApi.createPoll(
                                    groupId = viewModel.getGroupId(),
                                    question = question,
                                    options = options,
                                    isAnonymous = if (isAnonymous) "1" else "0",
                                    allowsMultiple = if (allowsMultiple) "1" else "0"
                                )
                            } catch (e: Exception) {
                                Log.e("MessagesScreen", "Помилка створення опитування: ${e.message}")
                            }
                        }
                    }
                )
            }

            // ── Video compression progress overlay ────────────────────────────
            if (isCompressing) {
                VideoCompressionOverlay(progress = compressionProgress)
            }

            // ── Video quality selection sheet (from gallery picker) ────────────
            if (showVideoQualitySheet && pendingVideoFile != null) {
                VideoQualitySheet(
                    videoFile = pendingVideoFile!!,
                    onSend = { quality, sendAsFile ->
                        val file = pendingVideoFile
                        showVideoQualitySheet = false
                        pendingVideoFile = null
                        if (file != null) {
                            viewModel.uploadAndSendVideo(
                                file       = file,
                                quality    = quality,
                                sendAsFile = sendAsFile,
                            )
                            replyToMessage = null
                        }
                    },
                    onDismiss = {
                        showVideoQualitySheet = false
                        pendingVideoFile = null
                    },
                )
            }

            MessagesMediaPickersAndDialogs(
                showUnifiedPicker = showUnifiedPicker,
                unifiedPickerTab = unifiedPickerTab,
                onEmojiSelected = { emoji -> messageText += emoji },
                onGifSelected = { gifUrl -> viewModel.sendGif(gifUrl); showUnifiedPicker = false },
                onStickerSelected = { sticker -> viewModel.sendSticker(sticker.fileUrl); showUnifiedPicker = false },
                onPickerDismiss = { showUnifiedPicker = false },
                showLocationPicker = showLocationPicker,
                onLocationSelected = { locationData -> viewModel.sendLocation(locationData); showLocationPicker = false },
                onLocationDismiss = { showLocationPicker = false },
                showContactPicker = showContactPicker,
                onContactSelected = { contact -> viewModel.sendContact(contact); showContactPicker = false },
                onContactDismiss = { showContactPicker = false },
                showStrapiPicker = showStrapiPicker,
                onStrapiSelected = { contentUrl -> viewModel.sendGif(contentUrl); showStrapiPicker = false },
                onStrapiDismiss = { showStrapiPicker = false },
                showAudioQualityDialog = showAudioQualityDialog,
                pendingAudioFile = pendingAudioFile,
                onSendOriginalAudio = {
                    pendingAudioFile?.let { viewModel.uploadAndSendMedia(it, "audio") }
                    showAudioQualityDialog = false
                    pendingAudioFile = null
                    replyToMessage = null
                },
                onSendCompressedAudio = {
                    pendingAudioFile?.let { viewModel.uploadAndSendMedia(it, "voice") }
                    showAudioQualityDialog = false
                    pendingAudioFile = null
                    replyToMessage = null
                },
                onAudioDismiss = { showAudioQualityDialog = false; pendingAudioFile = null },
                showForwardDialog = showForwardDialog,
                forwardContacts = forwardContacts,
                forwardGroups = forwardGroups,
                selectedCount = selectedMessages.size,
                onForward = { recipientIds ->
                    viewModel.forwardMessages(selectedMessages, recipientIds)
                    android.widget.Toast.makeText(
                        context,
                        "✅ Переслано ${selectedMessages.size} повідомлень до ${recipientIds.size} отримувачів",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    isSelectionMode = false
                    selectedMessages = emptySet()
                },
                onForwardDismiss = { showForwardDialog = false },
            )
        }  // Кінець Column
    }  // Кінець Box
}

// ---------------------------------------------------------------------------
// Private helper composables — extracted to keep MessagesScreen under the
// D8 "method exceeds compiler instruction limit" threshold.
// ---------------------------------------------------------------------------

@Composable
private fun MessagesDeletionDialogs(
    showDeleteDialog: Boolean,
    messageToDelete: com.worldmates.messenger.data.model.Message?,
    showEditScopeDialog: Boolean,
    showSelectionDeleteDialog: Boolean,
    selectedCount: Int,
    onDeleteForEveryone: () -> Unit,
    onDeleteForMe: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onEditForEveryone: () -> Unit,
    onEditForMe: () -> Unit,
    onEditDismiss: () -> Unit,
    onSelectionDeleteForEveryone: () -> Unit,
    onSelectionDeleteForMe: () -> Unit,
    onSelectionDeleteDismiss: () -> Unit,
) {
    if (showDeleteDialog && messageToDelete != null) {
        AlertDialog(
            onDismissRequest = onDeleteDismiss,
            title = { Text(stringResource(R.string.delete_message)) },
            text = { Text(stringResource(R.string.delete_message_choice_desc)) },
            confirmButton = {
                TextButton(onClick = onDeleteForEveryone) {
                    Text(stringResource(R.string.delete_for_everyone), color = Color(0xFFD32F2F))
                }
            },
            dismissButton = {
                TextButton(onClick = onDeleteForMe) {
                    Text(stringResource(R.string.delete_for_me))
                }
            }
        )
    }

    if (showEditScopeDialog) {
        AlertDialog(
            onDismissRequest = onEditDismiss,
            title = { Text(stringResource(R.string.edit_message)) },
            text = { Text(stringResource(R.string.edit_apply_where)) },
            confirmButton = {
                TextButton(onClick = onEditForEveryone) {
                    Text(stringResource(R.string.for_everyone))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = onEditForMe) { Text(stringResource(R.string.for_me_only)) }
                    TextButton(onClick = onEditDismiss) { Text(stringResource(R.string.cancel)) }
                }
            }
        )
    }

    if (showSelectionDeleteDialog) {
        AlertDialog(
            onDismissRequest = onSelectionDeleteDismiss,
            title = { Text(stringResource(R.string.delete_n_messages_title, selectedCount)) },
            text = { Text(stringResource(R.string.delete_messages_choice_desc)) },
            confirmButton = {
                TextButton(onClick = onSelectionDeleteForEveryone) {
                    Text(stringResource(R.string.delete_for_everyone), color = Color(0xFFD32F2F))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = onSelectionDeleteForMe) { Text(stringResource(R.string.delete_for_me)) }
                    TextButton(onClick = onSelectionDeleteDismiss) { Text(stringResource(R.string.cancel)) }
                }
            }
        )
    }
}

@Composable
private fun MessagesMediaPickersAndDialogs(
    showUnifiedPicker: Boolean,
    unifiedPickerTab: com.worldmates.messenger.ui.components.MediaPickerTab,
    onEmojiSelected: (String) -> Unit,
    onGifSelected: (String) -> Unit,
    onStickerSelected: (com.worldmates.messenger.data.model.Sticker) -> Unit,
    onPickerDismiss: () -> Unit,
    showLocationPicker: Boolean,
    onLocationSelected: (com.worldmates.messenger.data.repository.LocationData) -> Unit,
    onLocationDismiss: () -> Unit,
    showContactPicker: Boolean,
    onContactSelected: (com.worldmates.messenger.data.model.Contact) -> Unit,
    onContactDismiss: () -> Unit,
    showStrapiPicker: Boolean,
    onStrapiSelected: (String) -> Unit,
    onStrapiDismiss: () -> Unit,
    showAudioQualityDialog: Boolean,
    pendingAudioFile: java.io.File?,
    onSendOriginalAudio: () -> Unit,
    onSendCompressedAudio: () -> Unit,
    onAudioDismiss: () -> Unit,
    showForwardDialog: Boolean,
    forwardContacts: List<com.worldmates.messenger.ui.messages.selection.ForwardRecipient>,
    forwardGroups: List<com.worldmates.messenger.ui.messages.selection.ForwardRecipient>,
    selectedCount: Int,
    onForward: (List<Long>) -> Unit,
    onForwardDismiss: () -> Unit,
) {
    if (showUnifiedPicker) {
        com.worldmates.messenger.ui.components.UnifiedMediaPicker(
            onEmojiSelected = onEmojiSelected,
            onGifSelected = onGifSelected,
            onStickerSelected = onStickerSelected,
            onDismiss = onPickerDismiss,
            initialTab = unifiedPickerTab
        )
    }

    if (showLocationPicker) {
        com.worldmates.messenger.ui.components.LocationPicker(
            onLocationSelected = onLocationSelected,
            onDismiss = onLocationDismiss
        )
    }

    if (showContactPicker) {
        com.worldmates.messenger.ui.components.ContactPicker(
            onContactSelected = onContactSelected,
            onDismiss = onContactDismiss
        )
    }

    if (showStrapiPicker) {
        com.worldmates.messenger.ui.strapi.StrapiContentPicker(
            onItemSelected = onStrapiSelected,
            onDismiss = onStrapiDismiss
        )
    }

    if (showAudioQualityDialog && pendingAudioFile != null) {
        AudioQualityDialog(
            fileName = pendingAudioFile.name,
            fileSize = pendingAudioFile.length(),
            onSendOriginal = onSendOriginalAudio,
            onSendCompressed = onSendCompressedAudio,
            onDismiss = onAudioDismiss
        )
    }

    ForwardMessageDialog(
        visible = showForwardDialog,
        contacts = forwardContacts,
        groups = forwardGroups,
        selectedCount = selectedCount,
        onForward = onForward,
        onDismiss = onForwardDismiss
    )
}

// ── Date separator helpers ────────────────────────────────────────────────────

/** Normalise Unix seconds or milliseconds to milliseconds. */
private fun normChatTs(ts: Long): Long = if (ts < 1_000_000_000_000L) ts * 1000L else ts

/** True when two timestamps fall on the same calendar day. */
private fun chatIsSameDay(ts1: Long, ts2: Long): Boolean {
    val c1 = Calendar.getInstance().apply { timeInMillis = normChatTs(ts1) }
    val c2 = Calendar.getInstance().apply { timeInMillis = normChatTs(ts2) }
    return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
           c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
}

/**
 * Frosted-glass pill shown between groups of messages from different days.
 * Uses R.string.today / R.string.yesterday so both RU and UK locales work.
 */
@Composable
private fun FloatingDateChip(timestamp: Long) {
    val colorScheme = MaterialTheme.colorScheme
    // Resolve locale-aware strings outside remember (stringResource is @Composable)
    val todayStr     = stringResource(R.string.today)
    val yesterdayStr = stringResource(R.string.yesterday)

    val label = remember(timestamp, todayStr, yesterdayStr) {
        val ms = normChatTs(timestamp)
        val msgCal = Calendar.getInstance().apply {
            timeInMillis = ms
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        when (msgCal.timeInMillis) {
            today.timeInMillis     -> todayStr
            yesterday.timeInMillis -> yesterdayStr
            else -> {
                val pattern = if (msgCal.get(Calendar.YEAR) == today.get(Calendar.YEAR))
                    "d MMMM" else "d MMMM yyyy"
                SimpleDateFormat(pattern, Locale.getDefault()).format(Date(ms))
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = colorScheme.surfaceVariant.copy(alpha = 0.88f),
            shadowElevation = 2.dp,
            tonalElevation = 1.dp
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Telegram-style sticky mini voice player bar (36 dp tall).
 *
 * Slides in below the chat header when the playing voice message is not in
 * the visible viewport; slides out when it scrolls back into view.
 * Tapping anywhere scrolls back to the originating bubble.
 *
 * Layout:
 *   [▶/⏸]  ▌▌▌  [title  0:23 / 1:05]  [1.5x]  [✕]
 *   ──── 2 dp progress line ────────────────────────
 */
@Composable
fun VoiceMessageStickyBar(
    title: String,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    playbackSpeed: Float,
    onPlayPause: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onClose: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val speedSteps = listOf(1f, 1.5f, 2f, 0.5f)

    // Animated waveform: 3 bars that bounce when playing, static when paused
    val waveTransition = rememberInfiniteTransition(label = "sticky_wave")
    val bar0 = if (isPlaying) waveTransition.animateFloat(0.3f, 1f,
        infiniteRepeatable(tween(340, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b0").value else 0.45f
    val bar1 = if (isPlaying) waveTransition.animateFloat(0.5f, 1f,
        infiniteRepeatable(tween(480, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b1").value else 0.7f
    val bar2 = if (isPlaying) waveTransition.animateFloat(0.25f, 1f,
        infiniteRepeatable(tween(390, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b2").value else 0.4f
    val bar3 = if (isPlaying) waveTransition.animateFloat(0.6f, 1f,
        infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b3").value else 0.55f
    val barScales = listOf(bar0, bar1, bar2, bar3)

    val progress = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    fun Long.fmt(): String {
        val s = (this / 1000).coerceAtLeast(0)
        return "%d:%02d".format(s / 60, s % 60)
    }
    val timeStr = if (durationMs > 0) "${currentPositionMs.fmt()} / ${durationMs.fmt()}"
                  else currentPositionMs.fmt()

    val speedLabel = if (playbackSpeed == playbackSpeed.toLong().toFloat())
        "${playbackSpeed.toInt()}x" else "${playbackSpeed}x"

    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onTap),
        color = colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .padding(start = 4.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ▶ / ⏸
                IconButton(onClick = onPlayPause, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Animated waveform bars
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(horizontal = 5.dp)
                ) {
                    barScales.forEach { scale ->
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height((16 * scale).dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(colorScheme.primary.copy(alpha = 0.85f))
                        )
                    }
                }

                // Title · time — fills remaining space
                Text(
                    text = "$title · $timeStr",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )

                // Speed button: cycles 1x → 1.5x → 2x → 0.5x → 1x
                TextButton(
                    onClick = {
                        val next = speedSteps[(speedSteps.indexOf(playbackSpeed) + 1) % speedSteps.size]
                        onSpeedChange(next)
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    modifier = Modifier
                        .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                        .height(36.dp)
                ) {
                    Text(
                        text = speedLabel,
                        fontSize = 11.sp,
                        fontWeight = if (playbackSpeed != 1f) FontWeight.Bold else FontWeight.Normal,
                        color = if (playbackSpeed != 1f) colorScheme.primary
                                else colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                // ✕ Close / Stop
                IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = colorScheme.onSurface.copy(alpha = 0.45f),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            // 2 dp progress line at the bottom
            Box(
                modifier = Modifier.fillMaxWidth().height(2.dp)
                    .background(colorScheme.primary.copy(alpha = 0.12f))
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(progress).fillMaxHeight()
                        .background(colorScheme.primary)
                )
            }
        }
    }
}

/**
 * Lightweight animated particle overlay for seasonal preset backgrounds.
 * Spring: floating pink cherry blossom petals.
 * Winter: drifting white snowflake crystals.
 */
@Composable
private fun SeasonalParticlesOverlay(
    isSpring: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "seasonal_particles")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isSpring) 9000 else 13000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "seasonal_fall"
    )

    val count = if (isSpring) 22 else 32
    // Deterministic particle layout — x position, phase offset, drift scale, radius
    val particles = remember(isSpring) {
        List(count) { i ->
            val x = (i.toFloat() / count)
            val phase = (i * 7.31f) % 1f
            val drift = 0.18f + (i % 7) * 0.07f
            val radius = if (isSpring) 4f + (i % 5) * 2f else 2.5f + (i % 4) * 1.5f
            floatArrayOf(x, phase, drift, radius)
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        particles.forEach { p ->
            val yFrac = (progress + p[1]) % 1f
            val y = yFrac * (h + 80f) - 40f
            val sineArg = ((progress + p[1]) * 2.0 * Math.PI * 1.5).let { sin(it).toFloat() }
            val x = (p[0] * w + sineArg * p[2] * 65f).coerceIn(0f, w)
            val alpha = 0.25f + (p[1] % 0.35f)
            if (isSpring) {
                // Pink petal dot
                drawCircle(
                    color = Color(0xFFFFB6D0).copy(alpha = alpha.coerceIn(0.2f, 0.65f)),
                    radius = p[3],
                    center = Offset(x, y)
                )
                // Tiny inner highlight
                drawCircle(
                    color = Color(0xFFFFFFFF).copy(alpha = 0.3f),
                    radius = p[3] * 0.4f,
                    center = Offset(x - p[3] * 0.25f, y - p[3] * 0.25f)
                )
            } else {
                val snowColor = Color(0xFFE8F4FF).copy(alpha = alpha.coerceIn(0.2f, 0.6f))
                val r = p[3]
                // Snowflake: 3 crossing lines
                for (angle in listOf(0.0, 1.047, 2.094)) {
                    val cosA = kotlin.math.cos(angle).toFloat()
                    val sinA = sin(angle).toFloat()
                    drawLine(
                        color = snowColor,
                        start = Offset(x - cosA * r, y - sinA * r),
                        end = Offset(x + cosA * r, y + sinA * r),
                        strokeWidth = 1.5f
                    )
                }
            }
        }
    }
}

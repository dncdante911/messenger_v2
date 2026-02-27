package com.worldmates.messenger.ui.messages

import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.data.model.Message
import com.worldmates.messenger.data.model.ReactionGroup
import com.worldmates.messenger.ui.components.AnimatedStickerView
import com.worldmates.messenger.ui.components.formatting.FormattingSettings
import com.worldmates.messenger.ui.components.media.VideoMessageComponent
import com.worldmates.messenger.ui.messages.selection.MediaActionMenu
import com.worldmates.messenger.ui.preferences.rememberBubbleStyle
import com.worldmates.messenger.utils.VoicePlayer
import kotlin.math.roundToInt

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
                // ── Resolve media URL & type before deciding bubble style ─────────
                val effectiveMediaUrl: String? = when {
                    !message.decryptedMediaUrl.isNullOrEmpty() -> message.decryptedMediaUrl.also {
                        Log.d("MessageBubble", "Використовую decryptedMediaUrl: $it")
                    }
                    !message.mediaUrl.isNullOrEmpty() -> message.mediaUrl.also {
                        Log.d("MessageBubble", "Використовую mediaUrl: $it")
                    }
                    !message.decryptedText.isNullOrEmpty() -> extractMediaUrlFromText(message.decryptedText!!).also {
                        Log.d("MessageBubble", "Витягнуто з тексту: $it")
                    }
                    else -> null
                }
                val detectedMediaType = detectMediaType(effectiveMediaUrl ?: "", message.type)
                Log.d("MessageBubble", "ID: ${message.id}, Type: ${message.type}, Detected: $detectedMediaType, URL: $effectiveMediaUrl")

                val shouldShowText = !message.decryptedText.isNullOrEmpty() &&
                        !isOnlyMediaUrl(message.decryptedText!!)

                // Transparent media = image/video/sticker/gif without a text caption
                val isTransparentMedia = !effectiveMediaUrl.isNullOrEmpty() &&
                        detectedMediaType in listOf("image", "video", "sticker") &&
                        !shouldShowText

                // 💬 ТЕКСТ/МЕДІА В БУЛЬБАШЦІ - використовуємо вибраний стиль
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

                    if (isTransparentMedia) {
                        // ── 🖼️ TRANSPARENT MEDIA BUBBLE (like Telegram) ───────────
                        when (detectedMediaType) {
                            "sticker" -> {
                                // Sticker/GIF: bare content, no bubble wrapper at all
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp)
                                        .combinedClickable(
                                            onClick = { if (isSelectionMode) onToggleSelection(message.id) },
                                            onLongClick = { if (!isSelectionMode) { onLongPress(); onToggleSelection(message.id) } },
                                            onDoubleClick = { if (!isSelectionMode) onDoubleTap(message.id) }
                                        )
                                ) {
                                    AnimatedStickerView(
                                        url = effectiveMediaUrl!!,
                                        size = 150.dp,
                                        autoPlay = true,
                                        loop = true
                                    )
                                }
                            }
                            else -> {
                                // Image or Video: rounded box + overlaid dark time pill
                                Box(
                                    modifier = Modifier
                                        .wrapContentWidth()
                                        .widthIn(max = 260.dp)
                                        .heightIn(min = 120.dp, max = 300.dp)
                                        .padding(horizontal = 8.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .combinedClickable(
                                            onClick = {
                                                if (isSelectionMode) {
                                                    onToggleSelection(message.id)
                                                } else if (detectedMediaType == "image") {
                                                    onImageClick(effectiveMediaUrl!!)
                                                }
                                            },
                                            onLongClick = { if (!isSelectionMode) { onLongPress(); onToggleSelection(message.id) } },
                                            onDoubleClick = { if (!isSelectionMode) onDoubleTap(message.id) }
                                        )
                                ) {
                                    if (detectedMediaType == "image") {
                                        AsyncImage(
                                            model = effectiveMediaUrl,
                                            contentDescription = "Media",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                            onError = { Log.e("MessageBubble", "Image load error: $effectiveMediaUrl") }
                                        )
                                    } else if (detectedMediaType == "video") {
                                        VideoMessageComponent(
                                            message = message,
                                            videoUrl = effectiveMediaUrl!!,
                                            showTextAbove = false,
                                            enablePiP = true,
                                            modifier = Modifier
                                        )
                                    }
                                    // ⏱ Overlaid dark time pill on bottom-right
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(6.dp)
                                            .background(
                                                color = Color.Black.copy(alpha = 0.45f),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Text(
                                                text = formatTime(message.timeStamp),
                                                color = Color.White,
                                                fontSize = 11.sp
                                            )
                                            if (isOwn) {
                                                MessageStatusIcon(
                                                    isRead = message.isRead ?: false,
                                                    modifier = Modifier
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
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
                    }  // end else (StyledBubble path)

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

    // Витягуємо інформацію про трек з URL або оригінального імені файлу
    val trackInfo = remember(mediaUrl, message.mediaFileName) {
        extractAudioTrackInfo(mediaUrl, message.mediaFileName)
    }
    val displayTitle = trackInfo.title
    val displayArtist = trackInfo.artist
    val isVoiceMessage = message.type?.lowercase() == "voice"

    // Компактний аудіо плеєр
    Surface(
        modifier = Modifier
            .wrapContentWidth()
            .widthIn(min = 200.dp, max = 260.dp),
        shape = RoundedCornerShape(18.dp),
        color = textColor.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            // Назва треку та виконавець (тільки для аудіо, не для голосових)
            if (!isVoiceMessage) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 4.dp, start = 4.dp, end = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (trackInfo.extension.isNotEmpty() && displayTitle == "Unknown Track")
                                "$displayTitle.${trackInfo.extension}" else displayTitle,
                            color = textColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        if (displayArtist.isNotEmpty()) {
                            Text(
                                text = displayArtist,
                                color = textColor.copy(alpha = 0.6f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically
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
                                    title = if (!isVoiceMessage) displayTitle else "Голосове повідомлення",
                                    artist = displayArtist,
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
    }

    // Повноекранний плеєр
    if (showAdvancedPlayer) {
        com.worldmates.messenger.ui.music.AdvancedMusicPlayer(
            audioUrl = mediaUrl,
            title = if (!isVoiceMessage) displayTitle else "Голосове повідомлення",
            artist = displayArtist,
            timestamp = message.timeStamp,
            iv = message.iv,
            tag = message.tag,
            onDismiss = { showAdvancedPlayer = false }
        )
    }
}

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

@Composable
fun ContextMenuItem(
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

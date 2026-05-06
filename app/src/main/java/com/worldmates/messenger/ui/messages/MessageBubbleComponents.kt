package com.worldmates.messenger.ui.messages

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
import com.worldmates.messenger.network.NodeRetrofitClient
import com.worldmates.messenger.ui.components.AnimatedStickerView
import com.worldmates.messenger.ui.components.formatting.FormattingSettings
import com.worldmates.messenger.ui.components.media.VideoMessageComponent
import com.worldmates.messenger.ui.messages.selection.MediaActionMenu
import com.worldmates.messenger.ui.preferences.rememberBubbleStyle
import com.worldmates.messenger.utils.VoicePlayer
import com.worldmates.messenger.utils.VoiceTranscriptCache
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import com.worldmates.messenger.R
import kotlinx.coroutines.launch
import com.worldmates.messenger.utils.extractFirstUrl
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextOverflow
import com.google.gson.Gson
import com.worldmates.messenger.data.model.BotInlineButton
import com.worldmates.messenger.data.model.GroupPollData
import com.worldmates.messenger.network.Poll
import com.worldmates.messenger.ui.bots.BotInlineKeyboard
import com.worldmates.messenger.network.PollOption
import com.worldmates.messenger.ui.messages.components.PollMessageComponent

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
    // 👤 Параметри для відображення імені відправника (групові та приватні чати)
    isGroup: Boolean = false,
    recipientName: String = "",
    onSenderNameClick: (Long) -> Unit = {},
    // 📝 Параметри для форматування тексту
    formattingSettings: FormattingSettings = FormattingSettings(),
    onMentionClick: (String) -> Unit = {},
    onHashtagClick: (String) -> Unit = {},
    onLinkClick: (String) -> Unit = {},
    // 🗑️ ViewModel для видалення повідомлень
    viewModel: MessagesViewModel? = null,
    // 💬 Reply bubble navigation — tap to scroll to the original replied-to message
    onReplyClick: ((Long) -> Unit)? = null,
    // 🤖 Bot inline keyboard button click handler
    onBotButtonClick: (botId: String, messageId: Long, button: BotInlineButton) -> Unit = { _, _, _ -> }
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

    // 🎨 Кольори бульбашок залежать від стилю інтерфейсу та теми
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val bgColor = when (uiStyle) {
        com.worldmates.messenger.ui.preferences.UIStyle.WORLDMATES -> {
            if (isOwn) {
                if (isDarkTheme) Color(0xFF2B5FA4) else Color(0xFF3B82D9)
            } else {
                if (isDarkTheme) Color(0xFF2A2F3A) else Color(0xFFF0F2F5)
            }
        }
        com.worldmates.messenger.ui.preferences.UIStyle.TELEGRAM -> {
            if (isOwn) {
                if (isDarkTheme) Color(0xFF2B6B3E) else Color(0xFFD8F9C2)
            } else {
                if (isDarkTheme) Color(0xFF2A2F3A) else Color(0xFFFFFFFF)
            }
        }
    }

    val textColor = when (uiStyle) {
        com.worldmates.messenger.ui.preferences.UIStyle.WORLDMATES -> {
            if (isOwn) {
                Color.White
            } else {
                if (isDarkTheme) Color(0xFFE4E8EE) else Color(0xFF1A1C20)
            }
        }
        com.worldmates.messenger.ui.preferences.UIStyle.TELEGRAM -> {
            if (isDarkTheme) {
                if (isOwn) Color(0xFFE8F5E9) else Color(0xFFE4E8EE)
            } else {
                Color(0xFF1A1C20)
            }
        }
    }

    val playbackState by voicePlayer.playbackState.collectAsState()
    val currentPosition by voicePlayer.currentPosition.collectAsState()
    val duration by voicePlayer.duration.collectAsState()

    var showVideoPlayer by remember { mutableStateOf(false) }

    // 📱 Меню для медіа файлів
    var showMediaMenu by remember { mutableStateOf(false) }

    // ✅ Анімований колір підсвічування при виборі
    val selectionBgColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent,
        label = "selectionBg"
    )

    // 💬 Обгортка з іконкою Reply для свайпу
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(selectionBgColor)
            .padding(vertical = 3.dp)
    ) {
        // Іконка Reply (показується при свайпі)
        if (offsetX > 20f) {
            Icon(
                imageVector = Icons.Default.Reply,
                contentDescription = stringResource(R.string.reply),
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
                    // Custom swipe detector that doesn't compete with child long-press:
                    // - awaitFirstDown(requireUnconsumed = false) lets child keep the pointer
                    // - only activates when horizontal delta clearly exceeds vertical (2×)
                    // - requires minimum horizontal distance before locking into swipe mode
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var totalX = 0f
                        var totalY = 0f
                        var isSwipeActive = false
                        val pointer = down.id

                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointer }
                                ?: event.changes.firstOrNull()
                                ?: break

                            val dx = change.position.x - change.previousPosition.x
                            val dy = change.position.y - change.previousPosition.y
                            totalX += dx
                            totalY += dy

                            if (!isSwipeActive) {
                                val absX = abs(totalX)
                                val absY = abs(totalY)
                                // Only start swipe if: horizontal movement > 20px
                                // AND horizontal is at least 2× more than vertical (clear intent)
                                // AND direction is right-to-left (positive x)
                                if (absX > 20f && absX > absY * 2f && totalX > 0f) {
                                    isSwipeActive = true
                                }
                            }

                            if (isSwipeActive) {
                                change.consume()
                                offsetX = totalX.coerceIn(0f, maxSwipeDistance)
                            }
                        } while (event.changes.any { it.pressed })

                        if (isSwipeActive && offsetX > maxSwipeDistance / 2) {
                            onReply(message)
                        }
                        offsetX = 0f
                    }
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
                            contentDescription = stringResource(R.string.message_selected),
                            tint = colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Circle,
                            contentDescription = stringResource(R.string.message_not_selected),
                            tint = colorScheme.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // ── 📞 GROUP CALL CARD ────────────────────────────────────────────
            if (message.typeTwo == "group_call" && !message.encryptedText.isNullOrEmpty()) {
                val ctx = LocalContext.current
                val meta = runCatching {
                    org.json.JSONObject(message.encryptedText)
                }.getOrNull()
                if (meta != null) {
                    val roomName = meta.optString("roomName", "")
                    GroupCallMessageCard(
                        callType        = meta.optString("callType", "audio"),
                        initiatorName   = meta.optString("initiatorName", ""),
                        maxParticipants = meta.optInt("maxParticipants", 5),
                        isPremium       = meta.optBoolean("isPremiumCall", false),
                        onJoin          = {
                            if (roomName.isNotEmpty()) {
                                val intent = android.content.Intent(
                                    ctx,
                                    com.worldmates.messenger.ui.calls.CallsActivity::class.java
                                ).apply {
                                    putExtra("isGroup", true)
                                    putExtra("group_room_name", roomName)
                                    putExtra("is_incoming_group", true)
                                    putExtra("group_id", message.groupId?.toString() ?: "0")
                                    putExtra("recipient_name", meta.optString("initiatorName", ""))
                                    putExtra("call_type", meta.optString("callType", "audio"))
                                    putExtra("group_max_participants", meta.optInt("maxParticipants", 5))
                                }
                                ctx.startActivity(intent)
                            }
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    return@Row
                }
            }

            // ── 📇 CONTACT MESSAGE (Node.js private chat — raw vCard in contact field) ──
            if (message.typeTwo == "contact" && !message.contact.isNullOrBlank()) {
                val contact = com.worldmates.messenger.data.model.Contact.fromVCard(message.contact.orEmpty())
                if (contact != null) {
                    com.worldmates.messenger.ui.components.ContactMessageBubble(
                        contact  = contact,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                    return@Row
                }
            }

            // ── 📍 LOCATION / MAP MESSAGE ────────────────────────────────────
            val isLocationMessage = message.type == "map" &&
                !message.lat.isNullOrBlank() && !message.lng.isNullOrBlank()
            if (isLocationMessage) {
                val lat = message.lat?.toDoubleOrNull()
                val lng = message.lng?.toDoubleOrNull()
                if (lat != null && lng != null) {
                    com.worldmates.messenger.ui.components.LocationMessageBubble(
                        lat     = lat,
                        lng     = lng,
                        address = message.decryptedText?.takeIf { it.isNotBlank() },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                    return@Row
                }
            }

            // ── 📊 POLL MESSAGE ──────────────────────────────────────────────
            val isPollMessage = (message.type?.contains("poll") == true || message.typeTwo == "poll") && !message.stickers.isNullOrEmpty()
            if (isPollMessage) {
                val pollData = runCatching {
                    Gson().fromJson(message.stickers, GroupPollData::class.java)
                }.getOrNull()
                if (pollData != null) {
                    val poll = Poll(
                        id           = pollData.id,
                        question     = pollData.question,
                        pollType     = pollData.pollType,
                        isAnonymous  = pollData.isAnonymous,
                        isClosed     = pollData.isClosed,
                        totalVotes   = pollData.totalVotes,
                        createdBy    = pollData.createdBy,
                        options      = pollData.options.map {
                            PollOption(it.id, it.text, it.voteCount, it.percent, it.isVoted)
                        }
                    )
                    PollMessageComponent(
                        poll    = poll,
                        isMine  = isOwn,
                        onVote  = { optionId -> viewModel?.voteOnGroupPoll(message, optionId) },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    return@Row
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
                        val decryptedText = message.decryptedText.orEmpty()
                        Text(
                            text = decryptedText,
                            fontSize = getEmojiSize(decryptedText),
                            lineHeight = (getEmojiSize(decryptedText).value + 4).sp,
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
                        )
                    }
                }
            } else {
                // ── Resolve media URL & type before deciding bubble style ─────────
                // stickers field holds GIF/sticker URL when it's an http URL (not JSON poll data)
                val stickerMediaUrl: String? = message.stickers
                    ?.takeIf { it.startsWith("http") || it.startsWith("lottie://") }

                val effectiveMediaUrl: String? = when {
                    !message.decryptedMediaUrl.isNullOrEmpty() -> message.decryptedMediaUrl.also {
                        Log.d("MessageBubble", "Використовую decryptedMediaUrl: $it")
                    }
                    !message.mediaUrl.isNullOrEmpty() -> message.mediaUrl.also {
                        Log.d("MessageBubble", "Використовую mediaUrl: $it")
                    }
                    stickerMediaUrl != null -> stickerMediaUrl.also {
                        Log.d("MessageBubble", "Використовую stickers URL: $it")
                    }
                    !message.decryptedText.isNullOrEmpty() -> extractMediaUrlFromText(message.decryptedText.orEmpty()).also {
                        Log.d("MessageBubble", "Витягнуто з тексту: $it")
                    }
                    else -> null
                }
                // When URL comes from stickers field → always "sticker" (AnimatedStickerView handles both GIFs and animated stickers)
                val detectedMediaType = when {
                    stickerMediaUrl != null && effectiveMediaUrl == stickerMediaUrl -> "sticker"
                    else -> detectMediaType(effectiveMediaUrl ?: "", message.type)
                }
                Log.d("MessageBubble", "ID: ${message.id}, Type: ${message.type}, Detected: $detectedMediaType, URL: $effectiveMediaUrl")

                val shouldShowText = !message.decryptedText.isNullOrEmpty() &&
                        !isOnlyMediaUrl(message.decryptedText.orEmpty())

                // Transparent media = image/video/sticker/gif without a text caption
                val isTransparentMedia = !effectiveMediaUrl.isNullOrEmpty() &&
                        detectedMediaType in listOf("image", "video", "sticker") &&
                        !shouldShowText

                // 💬 ТЕКСТ/МЕДІА В БУЛЬБАШЦІ - використовуємо вибраний стиль
                Column {
                    // 👤 Ім'я відправника: в групах — з message.senderName;
                    //    у приватних чатах — recipientName (якщо повідомлення не моє)
                    val displaySenderName = when {
                        isOwn -> null
                        isGroup && !message.senderName.isNullOrEmpty() -> message.senderName
                        !isGroup && recipientName.isNotBlank() -> recipientName
                        else -> null
                    }
                    if (displaySenderName != null) {
                        Text(
                            text = displaySenderName,
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

                    if (isTransparentMedia && effectiveMediaUrl != null) {
                        // ── 🖼️ TRANSPARENT MEDIA BUBBLE (like Telegram) ───────────
                        when (detectedMediaType) {
                            "sticker" -> {
                                // Transparent sticker bubble: sticker + overlaid timestamp pill (Telegram/Viber style)
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
                                        url = effectiveMediaUrl,
                                        size = 150.dp,
                                        autoPlay = true,
                                        loop = true
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                            .background(
                                                color = Color.Black.copy(alpha = 0.35f),
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
                                                if (message.isLocalPending) {
                                                    PendingMessageIcon()
                                                } else {
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
                                                    onImageClick(effectiveMediaUrl)
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
                                            videoUrl = effectiveMediaUrl,
                                            showTextAbove = false,
                                            enablePiP = true,
                                            onLongPress = { onLongPress(); onToggleSelection(message.id) },
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
                                                if (message.isLocalPending) {
                                                    PendingMessageIcon()
                                                } else {
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
                        // 💬 Reply quote block — TG/Viber style
                        if ((message.replyToId ?: 0L) > 0L) {
                            val replyAuthor = when {
                                !message.replyToName.isNullOrBlank() -> message.replyToName.orEmpty()
                                message.replyToId == UserSession.userId -> stringResource(R.string.you_label)
                                else -> stringResource(R.string.reply_to)
                            }
                            val replyPreview = when {
                                !message.replyToText.isNullOrBlank() -> message.replyToText.orEmpty()
                                else -> "🔒 Повідомлення"
                            }

                            // Pick reply colors based on bubble bg luminance:
                            // dark bg (WM blue, TG dark) → white elements
                            // light bg (TG light green, incoming white) → primary/dark elements
                            val isBubbleDark = bgColor.luminance() < 0.4f
                            val replyBg   = if (isBubbleDark) Color.White.copy(alpha = 0.18f)
                                            else colorScheme.primary.copy(alpha = 0.10f)
                            val replyBar  = if (isBubbleDark) Color.White
                                            else colorScheme.primary
                            val replyName = if (isBubbleDark) Color.White
                                            else colorScheme.primary
                            val replyText = if (isBubbleDark) Color.White.copy(alpha = 0.80f)
                                            else textColor.copy(alpha = 0.75f)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(replyBg)
                                    .height(IntrinsicSize.Min)  // lets the bar fill row height
                                    .clickable(enabled = onReplyClick != null) {
                                        message.replyToId?.let { onReplyClick?.invoke(it) }
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // ▌ Left accent bar — stretches full height of the card
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .fillMaxHeight()
                                        .background(replyBar)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(top = 8.dp, bottom = 8.dp, end = 10.dp),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = replyAuthor,
                                        color = replyName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = replyPreview,
                                        color = replyText,
                                        fontSize = 12.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Text message
                        if (shouldShowText) {
                            val decryptedText = message.decryptedText.orEmpty()
                            // 📇 Проверяем, является ли сообщение vCard контактом
                            val isContactMessage = com.worldmates.messenger.ui.components.isVCardMessage(decryptedText)

                            if (isContactMessage) {
                                // Рендерим контакт
                                val contact = com.worldmates.messenger.ui.components.parseContactFromMessage(decryptedText)
                                if (contact != null) {
                                    com.worldmates.messenger.ui.components.ContactMessageBubble(
                                        contact = contact
                                    )
                                } else {
                                    // Если не удалось распарсить, показываем как обычный текст з форматуванням
                                    FormattedMessageText(
                                        text = decryptedText,
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

                        // 🔗 Link preview — shown for any message containing a URL (no server media)
                        if (effectiveMediaUrl.isNullOrEmpty()) {
                            val firstUrl = remember(message.decryptedText) {
                                message.decryptedText?.let { extractFirstUrl(it) }
                            }
                            if (firstUrl != null) {
                                MessageLinkPreview(
                                    url = firstUrl,
                                    accentColor = if (isOwn) {
                                        MaterialTheme.colorScheme.inversePrimary
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
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
                                onLongPress = { onLongPress(); onToggleSelection(message.id) },
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

                        // Час з покращеним форматуванням + галочки прочитано
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
                            Row(
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formatTime(message.timeStamp),
                                    color = textColor.copy(alpha = 0.45f),
                                    fontSize = 11.sp,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(top = 2.dp)
                                )

                                // ✓✓ Галочки прочитано (тільки для власних повідомлень)
                                if (isOwn) {
                                    Spacer(modifier = Modifier.width(3.dp))
                                    if (message.isLocalPending) {
                                        PendingMessageIcon(modifier = Modifier.padding(top = 2.dp))
                                    } else {
                                        MessageStatusIcon(
                                            isRead = message.isRead ?: false,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
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

                    // 🤖 Inline keyboard (bot messages with reply_markup)
                    if (message.replyMarkup != null) {
                        val msgBotId = message.botId ?: ""
                        BotInlineKeyboard(
                            markup = message.replyMarkup,
                            onButtonClick = { button ->
                                onBotButtonClick(msgBotId, message.id, button)
                            },
                            botId = msgBotId,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
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
                    context.startActivity(android.content.Intent.createChooser(shareIntent, context.getString(R.string.share)))
                    showMediaMenu = false
                } catch (e: Exception) {
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.share_media_failed),
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
            title = { androidx.compose.material3.Text(stringResource(R.string.delete_media_title)) },
            text = { androidx.compose.material3.Text(stringResource(R.string.delete_media_warning)) },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        viewModel?.deleteMessage(message.id)
                        showDeleteMediaConfirmation = false
                    }
                ) {
                    androidx.compose.material3.Text(stringResource(R.string.delete), color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showDeleteMediaConfirmation = false }
                ) {
                    androidx.compose.material3.Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/** Persists voice playback speed globally across all voice messages in the session. */
object VoicePlaybackState {
    var speed: Float = 1f
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
    val listenedVoiceIds by com.worldmates.messenger.services.MusicPlaybackService.listenedVoiceIds.collectAsState()

    // Чи саме цей трек грає у сервісі
    val isThisTrackPlaying = serviceTrackInfo.url == mediaUrl && servicePlaybackState.isPlaying
    val isThisTrackLoaded = serviceTrackInfo.url == mediaUrl

    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    // Стан для відображення повноекранного плеєра
    var showAdvancedPlayer by remember { mutableStateOf(false) }

    // Швидкість відтворення для голосових (1x → 1.5x → 2x → 0.5x → 1x)
    // Зберігається глобально через VoicePlaybackState, щоб не скидатися при скролі
    var playbackSpeed by remember { mutableStateOf(VoicePlaybackState.speed) }
    val speedSteps = listOf(1f, 1.5f, 2f, 0.5f)

    // Transcript state (PRO only).
    // Ініціалізуємо зі singleton-кешу: якщо користувач вже розпізнав голосове і
    // прокрутив список — при поверненні транскрипт вже є, без повторного запиту до Whisper.
    var transcript       by remember(message.id) { mutableStateOf(VoiceTranscriptCache[message.id]) }
    var transcriptLoading by remember(message.id) { mutableStateOf(false) }
    var transcriptError  by remember(message.id) { mutableStateOf<String?>(null) }
    val isPro = remember { UserSession.isProActive }

    // Витягуємо інформацію про трек з URL або оригінального імені файлу
    val trackInfo = remember(mediaUrl, message.mediaFileName) {
        extractAudioTrackInfo(mediaUrl, message.mediaFileName)
    }
    val displayTitle = trackInfo.title
    val displayArtist = trackInfo.artist
    // Detect voice messages by type OR by server filename pattern (VOICE_*)
    // Also handles socket events where type may arrive as null/audio but media_type="voice"
    // or the encrypted URL ends in .m4a (voice recordings are always m4a, music is mp3/flac/etc.)
    val isVoiceMessage = message.type?.lowercase() == "voice" ||
        message.mediaType?.lowercase() == "voice" ||
        message.mediaFileName?.startsWith("VOICE_", ignoreCase = true) == true ||
        mediaUrl.substringAfterLast("/").startsWith("VOICE_", ignoreCase = true) ||
        (mediaUrl.substringAfterLast(".").lowercase() == "m4a" && message.type?.lowercase() != "audio")
    val voiceMessageLabel = stringResource(R.string.voice_message)

    // Компактний аудіо плеєр (без фону — бульбашка повідомлення вже є контейнером)
    Column(
        modifier = Modifier
            .wrapContentWidth()
            .widthIn(min = 200.dp, max = 260.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp)
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
                                    title = if (!isVoiceMessage) displayTitle else voiceMessageLabel,
                                    artist = displayArtist,
                                    timestamp = message.timeStamp,
                                    iv = message.iv,
                                    tag = message.tag,
                                    messageId = message.id,
                                    isVoice = isVoiceMessage
                                )
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isThisTrackPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.record_voice),
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Прогрес + час
                Column(modifier = Modifier.weight(1f)) {
                    if (isVoiceMessage) {
                        // 🎙️ Waveform bars (like Telegram)
                        val barCount = 32
                        val barHeights = remember(message.id) {
                            val rng = java.util.Random(message.id)
                            List(barCount) { rng.nextFloat() * 0.7f + 0.3f }
                        }
                        val progress = if (isThisTrackLoaded && servicePlaybackState.duration > 0)
                            servicePlaybackState.currentPosition.toFloat() / servicePlaybackState.duration.toFloat()
                        else 0f
                        val playedCount = (barCount * progress).toInt()
                        val primaryColor = colorScheme.primary
                        val inactiveColor = textColor.copy(alpha = 0.25f)
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                        ) {
                            val gap = size.width / (barCount * 2 - 1)
                            barHeights.forEachIndexed { i, h ->
                                val barH = size.height * h
                                val x = i * gap * 2
                                val y = (size.height - barH) / 2f
                                drawRoundRect(
                                    color = if (i <= playedCount) primaryColor else inactiveColor,
                                    topLeft = Offset(x, y),
                                    size = Size(gap, barH),
                                    cornerRadius = CornerRadius(gap / 2, gap / 2)
                                )
                            }
                        }
                    } else {
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

                // Кнопка швидкості відтворення (тільки для голосових повідомлень)
                if (isVoiceMessage) {
                    TextButton(
                        onClick = {
                            val nextIndex = (speedSteps.indexOf(playbackSpeed) + 1) % speedSteps.size
                            playbackSpeed = speedSteps[nextIndex]
                            VoicePlaybackState.speed = playbackSpeed
                            com.worldmates.messenger.services.MusicPlaybackService.setSpeed(playbackSpeed)
                        },
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
                        modifier = Modifier
                            .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                            .height(28.dp)
                    ) {
                        Text(
                            text = if (playbackSpeed == playbackSpeed.toLong().toFloat())
                                "${playbackSpeed.toInt()}x"
                            else
                                "${playbackSpeed}x",
                            color = if (playbackSpeed != 1f) colorScheme.primary else textColor.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            fontWeight = if (playbackSpeed != 1f) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    // 👁️ Listened indicator — double checkmark shown after voice message was played
                    if (message.id in listenedVoiceIds) {
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = null,
                            tint = colorScheme.primary.copy(alpha = 0.75f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Кнопка розгортання плеєра — тільки для музики, не для голосових
                if (!isVoiceMessage) {
                    IconButton(
                        onClick = { showAdvancedPlayer = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = stringResource(R.string.open_player),
                            tint = textColor.copy(alpha = 0.5f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
        }
    }

    // Voice transcript — доступно всім, але з різним лімітом символів:
    // звичайний = 200, PRO = 500
    val transcriptCharLimit = if (isPro) 500 else 200
    if (isVoiceMessage) {
        Spacer(modifier = Modifier.height(4.dp))
        transcript?.let { t ->
            val isTruncated = t.length > transcriptCharLimit
            val displayText = if (isTruncated) t.take(transcriptCharLimit) else t
            // Show transcript text (truncated to limit)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = displayText,
                    color = textColor.copy(alpha = 0.85f),
                    fontSize = 12.sp
                )
                // Підказка "показати повністю (PRO)" для обрізаного тексту у звичайних користувачів
                if (isTruncated && !isPro) {
                    Text(
                        text = stringResource(R.string.voice_transcript_truncated),
                        color = colorScheme.primary,
                        fontSize = 10.sp
                    )
                }
            }
        } ?: run {
            // Show "Transcribe" button — доступно всім
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (transcriptLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = colorScheme.primary,
                        strokeWidth = 1.5.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.voice_transcribing),
                        color = textColor.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                } else {
                    TextButton(
                        onClick = {
                            transcriptError = null
                            transcriptLoading = true
                            scope.launch {
                                try {
                                    val resp = NodeRetrofitClient.voiceApi.transcribe(mediaUrl)
                                    if (resp.apiStatus == 200 && resp.transcript.isNotBlank()) {
                                        transcript = resp.transcript
                                        // Зберігаємо в кеш — при прокрутці назад повторний запит не потрібен
                                        VoiceTranscriptCache[message.id] = resp.transcript
                                    } else {
                                        transcriptError = resp.errorMessage ?: "Error"
                                    }
                                } catch (e: Exception) {
                                    transcriptError = e.message
                                } finally {
                                    transcriptLoading = false
                                }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TextFields,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.voice_transcribe),
                            color = colorScheme.primary,
                            fontSize = 11.sp
                        )
                    }
                }
                transcriptError?.let { error ->
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }

    // Повноекранний плеєр
    if (showAdvancedPlayer) {
        com.worldmates.messenger.ui.music.AdvancedMusicPlayer(
            audioUrl = mediaUrl,
            title = if (!isVoiceMessage) displayTitle else voiceMessageLabel,
            artist = displayArtist,
            timestamp = message.timeStamp,
            iv = message.iv,
            tag = message.tag,
            onDismiss = { showAdvancedPlayer = false }
        )
    }
}

/**
 * Clock icon shown for locally-queued messages waiting for network connectivity.
 */
@Composable
fun PendingMessageIcon(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Default.Schedule,
        contentDescription = null,
        tint = Color(0xFF8E8E93),
        modifier = modifier.size(14.dp)
    )
}

@Composable
fun MessageStatusIcon(
    isRead: Boolean,
    modifier: Modifier = Modifier
) {
    val readColor = Color(0xFF0084FF)
    val greyColor = Color(0xFF8E8E93)
    // First checkmark animates from grey (sent) to blue (read)
    val firstTint by animateColorAsState(
        targetValue = if (isRead) readColor else greyColor,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "receipt_tint"
    )

    Row(
        modifier = modifier.padding(horizontal = 1.dp, vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy((-6).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // First checkmark — always visible (sent / read)
        Icon(
            imageVector = Icons.Default.Done,
            contentDescription = if (isRead) stringResource(R.string.message_read) else stringResource(R.string.message_sent),
            tint = firstTint,
            modifier = Modifier.size(16.dp)
        )
        // Second checkmark — only appears when message is actually read
        AnimatedVisibility(
            visible = isRead,
            enter = fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.6f),
            exit = fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.6f)
        ) {
            Icon(
                imageVector = Icons.Default.Done,
                contentDescription = stringResource(R.string.message_read),
                tint = readColor,
                modifier = Modifier.size(16.dp)
            )
        }
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
                text = stringResource(R.string.message_actions_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                color = colorScheme.onSurface
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Reply
            ContextMenuItem(
                icon = Icons.Default.Reply,
                text = stringResource(R.string.reply),
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
                    text = stringResource(R.string.edit_message_short),
                    onClick = { onEdit(message) }
                )
            }

            // Forward
            ContextMenuItem(
                icon = Icons.Default.Forward,
                text = stringResource(R.string.forward),
                onClick = { onForward(message) }
            )

            // Pin (для приватних чатів)
            if (isPrivateChat) {
                ContextMenuItem(
                    icon = Icons.Default.PushPin,
                    text = stringResource(R.string.pin_message_short),
                    onClick = { onPin(message) }
                )
            }

            // Copy (якщо є текст і це не просто URL медіа)
            if (!message.decryptedText.isNullOrEmpty() && !isMediaMessage && !textIsMediaUrl) {
                ContextMenuItem(
                    icon = Icons.Default.ContentCopy,
                    text = stringResource(R.string.copy_text),
                    onClick = { onCopy(message) }
                )
            }

            // Delete (тільки для своїх повідомлень)
            if (message.fromId == UserSession.userId) {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                ContextMenuItem(
                    icon = Icons.Default.Delete,
                    text = stringResource(R.string.delete),
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
    onCancelReply: () -> Unit,
    recipientName: String = ""
) {
    // Keep last non-null message so the exit animation renders the last content, not empty
    var cachedMsg by remember { mutableStateOf(replyToMessage) }
    if (replyToMessage != null) cachedMsg = replyToMessage

    androidx.compose.animation.AnimatedVisibility(
        visible = replyToMessage != null,
        enter = slideInVertically(animationSpec = tween(200)) { it } + fadeIn(animationSpec = tween(200)),
        exit = slideOutVertically(animationSpec = tween(150)) { it } + fadeOut(animationSpec = tween(150))
    ) {
        val msg = cachedMsg ?: return@AnimatedVisibility
        val colorScheme = MaterialTheme.colorScheme
        val senderName = when {
            msg.fromId == UserSession.userId    -> stringResource(R.string.you_label)
            !msg.senderName.isNullOrBlank()     -> msg.senderName.orEmpty()
            recipientName.isNotBlank()          -> recipientName
            else                               -> stringResource(R.string.user_label)
        }
        val mediaType = when {
            msg.type == "voice"                                                       -> "voice"
            msg.type == "audio"                                                       -> "audio"
            msg.type == "video"                                                       -> "video"
            msg.type == "image" || msg.type == "photo"                                -> "image"
            msg.type == "sticker"                                                     -> "sticker"
            !msg.stickers.isNullOrEmpty() && msg.stickers!!.startsWith("http")       -> "sticker"
            !msg.mediaUrl.isNullOrBlank()                                             -> "media"
            else                                                                      -> null
        }
        val previewText = when {
            !msg.decryptedText.isNullOrBlank() -> msg.decryptedText.orEmpty()
            mediaType == "voice"   -> stringResource(R.string.reply_type_voice)
            mediaType == "audio"   -> stringResource(R.string.reply_type_audio)
            mediaType == "video"   -> stringResource(R.string.reply_type_video)
            mediaType == "image"   -> stringResource(R.string.reply_type_photo)
            mediaType == "sticker" -> stringResource(R.string.reply_type_sticker)
            mediaType == "media"   -> stringResource(R.string.reply_type_media)
            else                   -> stringResource(R.string.reply_type_message)
        }
        val mediaIcon: androidx.compose.ui.graphics.vector.ImageVector? = when (mediaType) {
            "voice"   -> Icons.Default.Mic
            "audio"   -> Icons.Default.MusicNote
            "video"   -> Icons.Default.Videocam
            "image"   -> Icons.Default.Image
            "sticker" -> Icons.Default.EmojiEmotions
            else      -> null
        }
        val mediaImageUrl = when {
            mediaType == "image" -> msg.decryptedMediaUrl ?: msg.mediaUrl
            else                 -> null
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            shape = RoundedCornerShape(12.dp),
            color = colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left accent bar
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(
                            colorScheme.primary,
                            RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                        )
                )

                // Reply icon or media thumbnail
                Box(
                    modifier = Modifier
                        .padding(start = 10.dp, top = 8.dp, bottom = 8.dp)
                        .size(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (mediaImageUrl != null) {
                        AsyncImage(
                            model = mediaImageUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else if (mediaIcon != null) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(
                                    colorScheme.primary.copy(alpha = 0.12f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = mediaIcon,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(
                                    colorScheme.primary.copy(alpha = 0.12f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Reply,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 8.dp, bottom = 8.dp, end = 4.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = senderName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = previewText,
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onCancelReply,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.cancel_reply_desc),
                        tint = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
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
                            contentDescription = stringResource(R.string.editing_indicator_desc),
                            tint = Color(0xFFFF9800),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.editing_message_label),
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
                        contentDescription = stringResource(R.string.cancel_editing_desc),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ─── Group Call Message Card ──────────────────────────────────────────────────

/**
 * Card shown in group chat when a conference call has been started.
 * Clicking "Join" opens CallsActivity for the active call.
 */
@Composable
fun GroupCallMessageCard(
    callType: String,
    initiatorName: String,
    maxParticipants: Int,
    isPremium: Boolean,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isVideo    = callType == "video"
    val accentColor = MaterialTheme.colorScheme.primary
    val cardColor   = MaterialTheme.colorScheme.surfaceContainerHigh

    Surface(
        modifier  = modifier.widthIn(max = 280.dp),
        shape     = RoundedCornerShape(16.dp),
        color     = cardColor,
        border    = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(accentColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.Call,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            if (isVideo) R.string.group_call_started_video
                            else R.string.group_call_started_audio
                        ),
                        fontWeight = FontWeight.Bold,
                        fontSize   = 13.sp,
                        color      = MaterialTheme.colorScheme.onSurface
                    )
                    if (initiatorName.isNotEmpty()) {
                        Text(
                            text  = initiatorName,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                        )
                    }
                }

                if (isPremium) {
                    Text(
                        text = "PRO",
                        color = Color(0xFFFFD700),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color(0xFFFFD700).copy(alpha = 0.18f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text  = stringResource(R.string.group_call_up_to_n, maxParticipants),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(10.dp))

            Button(
                onClick  = onJoin,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(10.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = accentColor),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.group_call_join_btn),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

package com.worldmates.messenger.ui.messages

import android.os.VibrationEffect
import android.util.Log
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import com.worldmates.messenger.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.ui.components.CompactMediaMenu
import com.worldmates.messenger.ui.components.formatting.FormattingSettings
import com.worldmates.messenger.ui.components.formatting.FormattingToolbar
import com.worldmates.messenger.ui.fonts.FontPickerSheet
import com.worldmates.messenger.ui.fonts.FontStyle
import com.worldmates.messenger.ui.fonts.FontStyleConverter
import com.worldmates.messenger.utils.VoiceRecorder
import kotlinx.coroutines.launch

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
    onBatchClick: () -> Unit = {},
    onPollClick: (() -> Unit)? = null,
    onRequestAudioPermission: () -> Boolean = { true },
    viewModel: MessagesViewModel? = null,
    formattingSettings: FormattingSettings = FormattingSettings(),
    voiceCommandActive: Boolean = false,
    onToggleVoiceCommand: () -> Unit = {},
    autoSendCountdown: Int = -1     // 3/2/1 before auto-send, -1 = no countdown
) {
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val context = LocalContext.current  // Додано для вібрації

    // 📝 State для панелі форматування (перенесено на рівень функції)
    var showFormattingToolbar by remember { mutableStateOf(false) }
    var showLinkInsertDialog by remember { mutableStateOf(false) }
    var showFontPicker by remember { mutableStateOf(false) }
    var activeFontStyle by remember { mutableStateOf(FontStyle.NORMAL) }
    // Plain (unstyled) text saved before first font conversion.
    var rawBeforeFont by remember { mutableStateOf("") }
    // Was the message blank when the font picker was opened?
    // If yes → don't inject the picker's preview text into the real message field.
    var messageWasEmptyOnPickerOpen by remember { mutableStateOf(false) }

    // Reset font state only when the message is explicitly cleared by the user
    // (effectiveOnChange handles this via the empty branch).
    // We do NOT reset here on blank so that the user can:
    //   1. Select a style while message is empty
    //   2. Then type with that style active

    // Intercepts TextField input to apply live font conversion while typing.
    // When a style is active, each new character is appended to rawBeforeFont
    // and the whole raw string is re-converted so the styled text stays correct.
    val effectiveOnChange: (String) -> Unit = { newText ->
        if (activeFontStyle == FontStyle.NORMAL) {
            Log.d("FontStyle", "effectiveOnChange: NORMAL, passthrough newText='$newText'")
            onMessageChange(newText)
        } else when {
            newText.isEmpty() -> {
                Log.d("FontStyle", "effectiveOnChange: cleared")
                rawBeforeFont = ""
                onMessageChange("")
            }
            newText.length > messageText.length -> {
                // Characters added at end — append raw chars to rawBeforeFont
                val addedCount = newText.length - messageText.length
                val added = newText.takeLast(addedCount)
                val newRaw = rawBeforeFont + added
                rawBeforeFont = newRaw
                val converted = FontStyleConverter.convert(newRaw, activeFontStyle)
                Log.d("FontStyle", "effectiveOnChange: added='$added' raw='$newRaw' style=$activeFontStyle converted='$converted'")
                onMessageChange(converted)
            }
            rawBeforeFont.isNotEmpty() && messageText.length > newText.length -> {
                // Backspace — remove from raw text and re-convert.
                // Use codePointCount because styled chars are surrogate pairs (length=2 per char).
                val deletedCodePoints = messageText.codePointCount(0, messageText.length) -
                        newText.codePointCount(0, newText.length)
                val rawCodePoints = rawBeforeFont.codePointCount(0, rawBeforeFont.length)
                val newRawCodePoints = maxOf(0, rawCodePoints - deletedCodePoints)
                // Rebuild raw string by keeping the first newRawCodePoints code points
                val newRaw = buildString {
                    var cp = 0; var idx = 0
                    while (cp < newRawCodePoints && idx < rawBeforeFont.length) {
                        val c = rawBeforeFont.codePointAt(idx)
                        appendCodePoint(c)
                        idx += Character.charCount(c)
                        cp++
                    }
                }
                rawBeforeFont = newRaw
                val converted = if (newRaw.isEmpty()) "" else FontStyleConverter.convert(newRaw, activeFontStyle)
                Log.d("FontStyle", "effectiveOnChange: backspace raw='$newRaw' converted='$converted'")
                onMessageChange(converted)
            }
            else -> {
                // Paste / selection replacement — reset style
                Log.d("FontStyle", "effectiveOnChange: complex edit, resetting style")
                rawBeforeFont = ""
                activeFontStyle = FontStyle.NORMAL
                onMessageChange(newText)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorScheme.surfaceContainerLow)
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
            onPollClick = onPollClick
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
                // Voice command toggle + listening indicator
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // "Слушаю..." badge (shown only when active + not recording)
                    if (voiceCommandActive && recordingState is VoiceRecorder.RecordingState.Idle) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .background(
                                    colorScheme.primaryContainer.copy(alpha = 0.7f),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = stringResource(R.string.voice_cmd_listening),
                                fontSize = 11.sp,
                                color = colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else if (autoSendCountdown >= 0) {
                        // Countdown badge: "Отправка через N..."
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .background(
                                    colorScheme.errorContainer.copy(alpha = 0.8f),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                tint = colorScheme.error,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (autoSendCountdown == 0)
                                    stringResource(R.string.voice_cmd_sending)
                                else
                                    stringResource(R.string.voice_cmd_send_countdown, autoSendCountdown),
                                fontSize = 11.sp,
                                color = colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        Spacer(Modifier)
                    }

                    // Toggle button
                    IconButton(
                        onClick = onToggleVoiceCommand,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (voiceCommandActive) Icons.Default.MicOff else Icons.Default.RecordVoiceOver,
                            contentDescription = stringResource(
                                if (voiceCommandActive) R.string.voice_cmd_disable
                                else R.string.voice_cmd_enable
                            ),
                            tint = if (voiceCommandActive) colorScheme.primary else colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

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
                        label = stringResource(R.string.input_mode_text),
                        isSelected = currentInputMode == InputMode.TEXT,
                        onClick = { onInputModeChange(InputMode.TEXT) }
                    )

                    // Voice mode
                    InputModeTab(
                        icon = Icons.Default.Mic,
                        label = stringResource(R.string.input_mode_voice),
                        isSelected = currentInputMode == InputMode.VOICE,
                        onClick = { onInputModeChange(InputMode.VOICE) }
                    )

                    // Video mode (майбутнє)
                    InputModeTab(
                        icon = Icons.Default.Videocam,
                        label = stringResource(R.string.input_mode_video),
                        isSelected = currentInputMode == InputMode.VIDEO,
                        onClick = { onInputModeChange(InputMode.VIDEO) }
                    )

                    // Emoji mode
                    InputModeTab(
                        icon = Icons.Default.EmojiEmotions,
                        label = stringResource(R.string.input_mode_emoji),
                        isSelected = currentInputMode == InputMode.EMOJI,
                        onClick = { onInputModeChange(InputMode.EMOJI) }
                    )

                    // Sticker mode
                    InputModeTab(
                        icon = Icons.Default.StickyNote2,
                        label = stringResource(R.string.input_mode_stickers),
                        isSelected = currentInputMode == InputMode.STICKER,
                        onClick = { onInputModeChange(InputMode.STICKER) }
                    )

                    // GIF mode
                    InputModeTab(
                        icon = Icons.Default.Gif,
                        label = stringResource(R.string.input_mode_gif),
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
                            contentDescription = stringResource(R.string.media_options_label),
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
                                    contentDescription = stringResource(R.string.formatted_text),
                                    tint = if (showFormattingToolbar) colorScheme.primary else colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // ✨ Кнопка стилю шрифту (Premium)
                            IconButton(
                                onClick = {
                                    messageWasEmptyOnPickerOpen = messageText.isBlank()
                                    showFontPicker = true
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text(
                                    text = if (activeFontStyle == FontStyle.NORMAL) "Аа" else activeFontStyle.emoji,
                                    fontSize = 13.sp,
                                    color = if (activeFontStyle != FontStyle.NORMAL) colorScheme.primary else colorScheme.onSurfaceVariant
                                )
                            }

                            // Звичайне поле введення
                            TextField(
                                value = messageText,
                                onValueChange = effectiveOnChange,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 40.dp, max = 120.dp)
                                    .background(colorScheme.surfaceContainerHighest, RoundedCornerShape(20.dp)),
                                placeholder = {
                                    Text(
                                        stringResource(R.string.messages),
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
                                keyboardOptions = if (activeFontStyle != FontStyle.NORMAL) {
                                    // Disable autocorrect/autocomplete when a decorative font
                                    // style is active — IME text replacement corrupts the
                                    // Unicode-styled characters stored in the field.
                                    KeyboardOptions(
                                        autoCorrect = false,
                                        keyboardType = KeyboardType.Text
                                    )
                                } else KeyboardOptions.Default,
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
                                    .background(colorScheme.surfaceContainerHighest.copy(alpha = 0.7f), RoundedCornerShape(20.dp)),
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
                                        text = stringResource(R.string.voice_hold_to_record_hint),
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
                                    text = stringResource(R.string.video_record_hint),
                                    color = colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        InputMode.EMOJI, InputMode.STICKER, InputMode.GIF -> {
                            // Показуємо текстове поле для коментаря
                            TextField(
                                value = messageText,
                                onValueChange = effectiveOnChange,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 40.dp, max = 120.dp)
                                    .background(colorScheme.surfaceVariant, RoundedCornerShape(20.dp)),
                                placeholder = {
                                    Text(
                                        stringResource(R.string.add_comment_placeholder),
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
                                        contentDescription = stringResource(R.string.send_label),
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
                                        contentDescription = stringResource(R.string.voice_message),
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
                                    contentDescription = stringResource(R.string.record_voice),
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )

                                // Підказка при записі
                                if (recordingState is VoiceRecorder.RecordingState.Recording && !isRecordingLocked) {
                                    Text(
                                        text = stringResource(R.string.swipe_up_hint),
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
                                        contentDescription = stringResource(R.string.stop),
                                        tint = colorScheme.error,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        InputMode.VIDEO -> {
                            // 📹 Кнопка запису відеоповідомлення - відкриває камеру
                            IconButton(
                                onClick = onPickVideo,  // ✅ Відкриває VideoMessageRecorder для запису через камеру
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Videocam,
                                    contentDescription = stringResource(R.string.record_video),
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
                                        contentDescription = stringResource(R.string.send_label),
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

        // ✨ Вибір стилю шрифту (Premium)
        if (showFontPicker) {
            // Always preview from the original un-styled text so the user can freely
            // switch styles; rawBeforeFont is empty on the very first open.
            val previewBase = rawBeforeFont.ifEmpty { messageText }
            FontPickerSheet(
                previewText = previewBase.ifBlank { "Привіт" },
                currentStyle = activeFontStyle,
                onStyleSelected = { style, styledText, rawPreview ->
                    Log.d("FontStyle", "onStyleSelected: style=$style styledText='$styledText' rawPreview='$rawPreview' wasEmpty=$messageWasEmptyOnPickerOpen")
                    if (style == FontStyle.NORMAL) {
                        activeFontStyle = FontStyle.NORMAL
                        if (!messageWasEmptyOnPickerOpen) {
                            val plain = rawBeforeFont.ifEmpty { rawPreview }
                            onMessageChange(plain)
                        }
                        rawBeforeFont = ""
                    } else {
                        activeFontStyle = style
                        if (messageWasEmptyOnPickerOpen) {
                            // Don't inject preview text into an empty message field
                            rawBeforeFont = ""
                        } else {
                            rawBeforeFont = rawPreview
                            onMessageChange(styledText)
                        }
                    }
                    showFontPicker = false
                },
                onDismiss = { showFontPicker = false }
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
                text = stringResource(R.string.send_audio_title),
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
                    text = stringResource(R.string.audio_file_size_format, fileSizeMB),
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
                                text = stringResource(R.string.audio_quality_original),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = stringResource(R.string.audio_quality_original_desc),
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
                                text = stringResource(R.string.audio_quality_compressed),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = stringResource(R.string.audio_quality_compressed_desc),
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
                Text(stringResource(R.string.cancel))
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
            contentDescription = stringResource(R.string.recording),
            tint = Color.Red,
            modifier = Modifier.size(20.dp)
        )

        Text(
            text = voiceRecorder.formatDuration(duration),
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )

        IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
        }

        Button(
            onClick = onStop,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0084FF))
        ) {
            Text(stringResource(R.string.send_label), color = Color.White)
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

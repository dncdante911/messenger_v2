package com.worldmates.messenger.ui.messages

import android.os.VibrationEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.ui.components.CompactMediaMenu
import com.worldmates.messenger.ui.components.formatting.FormattingSettings
import com.worldmates.messenger.ui.components.formatting.FormattingToolbar
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
    onRequestAudioPermission: () -> Boolean = { true },
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
                                onClick = onPickVideo,  // ✅ Відкриває VideoMessageRecorder для запису через камеру
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

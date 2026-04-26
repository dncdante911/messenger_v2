package com.worldmates.messenger.ui.components.media

import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.worldmates.messenger.R
import com.worldmates.messenger.data.model.Message
import com.worldmates.messenger.ui.media.InlineVideoPlayer
import com.worldmates.messenger.ui.messages.VideoMessageFrameStyle
import com.worldmates.messenger.ui.messages.getSavedVideoMessageFrameStyle
import com.worldmates.messenger.ui.video.AdvancedVideoPlayer
import com.worldmates.messenger.utils.EncryptedMediaHandler
import kotlinx.coroutines.launch

// Deleted placeholder is defined in ImageMessageComponent.kt (same package)

/**
 * Компонент для отображения видео в сообщении.
 *
 * Поддерживает применение VideoMessageFrameStyle (круг, неон, градиент, радуга и т.д.)
 * согласно настройкам пользователя в разделе Настройки → Темы → Видеосообщения.
 *
 * @param message Повідомлення з відео (для дешифрування потрібні iv, tag, timestamp)
 * @param videoUrl URL відеофайла (може бути зашифрованим)
 * @param showTextAbove Чи є текст над відео (для відступу)
 * @param enablePiP Включити Picture-in-Picture
 * @param modifier Додатковий модифікатор
 */
@Composable
fun VideoMessageComponent(
    message: Message,
    videoUrl: String,
    showTextAbove: Boolean = false,
    enablePiP: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (videoUrl == "deleted") {
        DeletedMediaPlaceholder(showTextAbove = showTextAbove, modifier = modifier)
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val frameStyle = remember { getSavedVideoMessageFrameStyle(context) }

    val containerShape = when (frameStyle) {
        VideoMessageFrameStyle.CIRCLE  -> CircleShape
        VideoMessageFrameStyle.MINIMAL -> RoundedCornerShape(6.dp)
        else                           -> RoundedCornerShape(20.dp)
    }

    // Animations for dynamic border styles
    val infiniteTransition = rememberInfiniteTransition(label = "frameAnim")
    val neonAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "neonAlpha"
    )
    var rainbowOffset by remember { mutableStateOf(0f) }
    LaunchedEffect(frameStyle) {
        if (frameStyle == VideoMessageFrameStyle.RAINBOW) {
            while (true) {
                kotlinx.coroutines.delay(48)
                rainbowOffset = (rainbowOffset + 8f) % 1000f
            }
        }
    }

    val frameBorder: Modifier = when (frameStyle) {
        VideoMessageFrameStyle.NEON -> Modifier.border(
            width = 2.5.dp,
            color = Color(0xFF00FFFF).copy(alpha = neonAlpha),
            shape = containerShape
        )
        VideoMessageFrameStyle.GRADIENT -> Modifier.border(
            width = 3.dp,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF667eea), Color(0xFF764ba2), Color(0xFFf093fb))
            ),
            shape = containerShape
        )
        VideoMessageFrameStyle.RAINBOW -> Modifier.border(
            width = 3.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFF0000), Color(0xFFFF7F00), Color(0xFFFFFF00),
                    Color(0xFF00FF00), Color(0xFF0000FF), Color(0xFF9400D3)
                ),
                start = Offset(rainbowOffset, 0f),
                end = Offset(rainbowOffset + 400f, 400f)
            ),
            shape = containerShape
        )
        VideoMessageFrameStyle.ROUNDED -> Modifier.border(
            width = 1.5.dp,
            color = Color.White.copy(alpha = 0.25f),
            shape = containerShape
        )
        else -> Modifier
    }

    var decryptedVideoUrl by remember(videoUrl) { mutableStateOf<String?>(null) }
    var isDecrypting by remember(videoUrl) { mutableStateOf(false) }
    var decryptionError by remember(videoUrl) { mutableStateOf(false) }
    var showVideoPlayer by remember { mutableStateOf(false) }

    LaunchedEffect(videoUrl) {
        if (EncryptedMediaHandler.isEncryptedFile(videoUrl)) {
            isDecrypting = true
            android.util.Log.d("VideoMessageComponent", "🔐 Початок дешифрування: $videoUrl")
            scope.launch {
                val decrypted = EncryptedMediaHandler.decryptMediaFile(
                    mediaUrl = videoUrl,
                    timestamp = message.timeStamp,
                    iv = message.iv,
                    tag = message.tag,
                    type = "video",
                    context = context
                )
                if (decrypted != null) {
                    android.util.Log.d("VideoMessageComponent", "✅ Дешифровано: $decrypted")
                    decryptedVideoUrl = decrypted
                    decryptionError = false
                } else {
                    android.util.Log.e("VideoMessageComponent", "❌ Помилка дешифрування")
                    decryptionError = true
                }
                isDecrypting = false
            }
        } else {
            decryptedVideoUrl = EncryptedMediaHandler.getFullMediaUrl(videoUrl, "video")
        }
    }

    val isCircle = frameStyle == VideoMessageFrameStyle.CIRCLE
    val sizeModifier = if (isCircle) {
        Modifier.size(220.dp)
    } else {
        Modifier
            .wrapContentWidth()
            .widthIn(max = 250.dp)
    }

    Box(
        modifier = modifier
            .then(sizeModifier)
            .padding(top = if (showTextAbove) 8.dp else 0.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            isDecrypting -> {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
            }
            decryptionError -> {
                androidx.compose.material3.Text(
                    text = stringResource(R.string.error_video_loading),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error
                )
            }
            decryptedVideoUrl != null -> {
                // Apply frame border outside, clip inside
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(frameBorder)
                        .clip(containerShape)
                ) {
                    InlineVideoPlayer(
                        videoUrl = decryptedVideoUrl!!,
                        shape = containerShape,
                        modifier = Modifier.fillMaxWidth(),
                        onFullscreenClick = { showVideoPlayer = true }
                    )
                }

                if (showVideoPlayer) {
                    AdvancedVideoPlayer(
                        videoUrl = decryptedVideoUrl!!,
                        onDismiss = { showVideoPlayer = false },
                        enablePiP = enablePiP,
                        autoPlay = true
                    )
                }
            }
        }
    }
}

/**
 * Компонент для отображения видео в компактном режиме (например, в превью пересланных сообщений)
 *
 * ⚠️ Важливо: Для превью використовуємо вже дешифрований URL
 */
@Composable
fun VideoMessagePreview(
    videoUrl: String,
    modifier: Modifier = Modifier,
    onFullscreenClick: (() -> Unit)? = null
) {
    InlineVideoPlayer(
        videoUrl = videoUrl,
        modifier = modifier
            .width(120.dp)
            .height(90.dp),
        onFullscreenClick = onFullscreenClick ?: {}
    )
}

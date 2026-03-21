package com.worldmates.messenger.ui.components.media

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.worldmates.messenger.data.model.Message
import com.worldmates.messenger.ui.media.InlineVideoPlayer
import com.worldmates.messenger.ui.video.AdvancedVideoPlayer
import com.worldmates.messenger.utils.EncryptedMediaHandler
import kotlinx.coroutines.launch

// Deleted placeholder is defined in ImageMessageComponent.kt (same package)

/**
 * Компонент для отображения видео в сообщении
 *
 * Извлечено из MessagesScreen.kt (строка 2010-2031) для уменьшения размера файла
 * Обновлено: использует новый AdvancedVideoPlayer с жестами управления
 *
 * ✨ НОВА ФУНКЦІЯ: Автоматично дешифровує зашифровані відео (AES-256-GCM)
 *
 * @param message Повідомлення з відео (для дешифрування потрібні iv, tag, timestamp)
 * @param videoUrl URL видеофайла (може бути зашифрованим)
 * @param showTextAbove Есть ли текст над видео (для отступа)
 * @param enablePiP Включить Picture-in-Picture (по умолчанию true)
 * @param modifier Дополнительный модификатор
 */
@Composable
fun VideoMessageComponent(
    message: Message,
    videoUrl: String,
    showTextAbove: Boolean = false,
    enablePiP: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Show deleted placeholder when media has been auto-deleted from the server
    if (videoUrl == "deleted") {
        DeletedMediaPlaceholder(
            showTextAbove = showTextAbove,
            modifier = modifier
        )
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var decryptedVideoUrl by remember(videoUrl) { mutableStateOf<String?>(null) }
    var isDecrypting by remember(videoUrl) { mutableStateOf(false) }
    var decryptionError by remember(videoUrl) { mutableStateOf(false) }
    var showVideoPlayer by remember { mutableStateOf(false) }

    // 🔐 Дешифруємо відео якщо воно зашифроване
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
            // Якщо не зашифрований - формуємо повний URL
            decryptedVideoUrl = EncryptedMediaHandler.getFullMediaUrl(videoUrl, "video")
        }
    }

    Box(
        modifier = modifier
            .wrapContentWidth()
            .widthIn(max = 250.dp)
            .padding(top = if (showTextAbove) 8.dp else 0.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            // Показуємо індикатор завантаження під час дешифрування
            isDecrypting -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp)
                )
            }

            // Показуємо помилку якщо дешифрування не вдалося
            decryptionError -> {
                androidx.compose.material3.Text(
                    text = "❌ Помилка завантаження відео",
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error
                )
            }

            // Показуємо відео коли воно готове
            decryptedVideoUrl != null -> {
                // Інлайн плеєр з превью
                InlineVideoPlayer(
                    videoUrl = decryptedVideoUrl!!,
                    modifier = Modifier.fillMaxWidth(),
                    onFullscreenClick = {
                        // Відкриваємо новий продвинутий плеєр
                        showVideoPlayer = true
                    }
                )

                // Повноекранний продвинутий плеєр з жестами
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

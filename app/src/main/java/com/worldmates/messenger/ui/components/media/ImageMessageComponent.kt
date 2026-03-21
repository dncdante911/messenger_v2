package com.worldmates.messenger.ui.components.media

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.worldmates.messenger.R

/**
 * Компонент для отображения изображения в сообщении
 *
 * Извлечено из MessagesScreen.kt (строка 1973-2008) для уменьшения размера файла
 *
 * @param imageUrl URL изображения (или "deleted" если медиафайл удалён)
 * @param messageId ID сообщения (для уникального ключа жестов)
 * @param showTextAbove Есть ли текст над изображением (для отступа)
 * @param onImageClick Callback при клике по изображению
 * @param onLongPress Callback при долгом нажатии
 * @param modifier Дополнительный модификатор
 */
@Composable
fun ImageMessageComponent(
    imageUrl: String,
    messageId: Long,
    showTextAbove: Boolean = false,
    onImageClick: (String) -> Unit,
    onLongPress: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Show deleted placeholder when the media file has been auto-deleted from the server
    if (imageUrl == "deleted") {
        DeletedMediaPlaceholder(
            showTextAbove = showTextAbove,
            modifier = modifier
        )
        return
    }

    Box(
        modifier = modifier
            .wrapContentWidth()  // Адаптується під розмір зображення
            .widthIn(max = 250.dp)  // Максимальна ширина для зображень
            .heightIn(min = 120.dp, max = 300.dp)
            .padding(top = if (showTextAbove) 6.dp else 0.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.1f))
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = stringResource(R.string.cd_image_message),
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(messageId) {
                    detectTapGestures(
                        onLongPress = {
                            Log.d("ImageMessageComponent", "🔽 Довге натискання на зображення: $imageUrl")
                            onLongPress()
                        },
                        onTap = {
                            Log.d("ImageMessageComponent", "📸 Клік по зображенню: $imageUrl")
                            onImageClick(imageUrl)
                        }
                    )
                },
            contentScale = ContentScale.Crop,
            onError = {
                Log.e("ImageMessageComponent", "❌ Помилка завантаження зображення: $imageUrl, error: ${it.result.throwable}")
            }
        )
    }
}

/**
 * Preview компонент для зображень в сообщениях
 * Упрощенная версия без жестов для быстрого отображения
 */
@Composable
fun ImageMessagePreview(
    imageUrl: String,
    modifier: Modifier = Modifier
) {
    if (imageUrl == "deleted") {
        DeletedMediaPlaceholder(modifier = modifier.size(100.dp))
        return
    }
    Box(
        modifier = modifier
            .size(100.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.1f))
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = stringResource(R.string.cd_image_preview),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

/**
 * Shared placeholder shown when a media file has been automatically deleted from the server.
 * Used by image, video, and file message components.
 */
@Composable
fun DeletedMediaPlaceholder(
    showTextAbove: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .widthIn(min = 160.dp, max = 250.dp)
            .heightIn(min = 72.dp)
            .padding(top = if (showTextAbove) 6.dp else 0.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = stringResource(R.string.media_deleted_placeholder),
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

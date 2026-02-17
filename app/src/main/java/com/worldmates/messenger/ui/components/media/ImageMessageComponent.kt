package com.worldmates.messenger.ui.components.media

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Компонент для отображения изображения в сообщении
 *
 * Извлечено из MessagesScreen.kt (строка 1973-2008) для уменьшения размера файла
 *
 * @param imageUrl URL изображения
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
            contentDescription = "Image message",
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
    Box(
        modifier = modifier
            .size(100.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.1f))
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = "Image preview",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

package com.worldmates.messenger.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BrandTeal = Color(0xFF4ECDC4)
private val BrandMint = Color(0xFF95E1A3)

/**
 * Полноэкранный экран ошибки с маскотом.
 *
 * @param type Тип ошибки — определяет иконку и сообщение по умолчанию
 * @param title Заголовок (опционально, иначе берётся из type)
 * @param message Сообщение (опционально)
 * @param onRetry Колбек кнопки "Попробовать снова"
 */
@Composable
fun WMErrorScreen(
    type: WMErrorType = WMErrorType.General,
    title: String? = null,
    message: String? = null,
    onRetry: (() -> Unit)? = null
) {
    // Маскот слегка покачивается
    val infiniteTransition = rememberInfiniteTransition(label = "error_mascot")
    val wobble by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wobble"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9F9F9)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Маскот с анимацией покачивания
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(
                        Brush.linearGradient(
                            colors = when (type) {
                                WMErrorType.NoInternet -> listOf(Color(0xFFFFB74D), Color(0xFFFF8A65))
                                WMErrorType.ServerError -> listOf(Color(0xFFEF5350), Color(0xFFE53935))
                                WMErrorType.Empty -> listOf(BrandTeal, BrandMint)
                                else -> listOf(Color(0xFF90CAF9), Color(0xFF42A5F5))
                            }
                        ),
                        shape = RoundedCornerShape(36.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = type.emoji,
                    fontSize = 64.sp,
                    modifier = Modifier.offset(
                        x = (wobble * 0.3f).dp,
                        y = (wobble * 0.5f).dp
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Заголовок
            Text(
                text = title ?: type.defaultTitle,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A1A),
                textAlign = TextAlign.Center
            )

            // Сообщение
            Text(
                text = message ?: type.defaultMessage,
                fontSize = 14.sp,
                color = Color(0xFF757575),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Кнопка retry
            if (onRetry != null) {
                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandTeal
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = "Попробовать снова",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Компактная карточка ошибки — для инлайн использования (в списках, чатах).
 */
@Composable
fun WMErrorCard(
    type: WMErrorType = WMErrorType.General,
    message: String? = null,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3F3))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = type.emoji, fontSize = 32.sp)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message ?: type.defaultTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1A1A1A)
                )
                if (onRetry != null) {
                    TextButton(
                        onClick = onRetry,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "Повторить",
                            fontSize = 13.sp,
                            color = BrandTeal
                        )
                    }
                }
            }
        }
    }
}

enum class WMErrorType(
    val emoji: String,
    val defaultTitle: String,
    val defaultMessage: String
) {
    General(
        emoji = "🐶",
        defaultTitle = "Что-то пошло не так",
        defaultMessage = "Произошла неизвестная ошибка.\nМы уже в курсе и разбираемся!"
    ),
    NoInternet(
        emoji = "🐾",
        defaultTitle = "Нет интернета",
        defaultMessage = "Проверь подключение к сети\nи попробуй снова."
    ),
    ServerError(
        emoji = "😵",
        defaultTitle = "Сервер недоступен",
        defaultMessage = "Технические работы или сервер\nвременно недоступен. Попробуй позже."
    ),
    NotFound(
        emoji = "🔍",
        defaultTitle = "Ничего не найдено",
        defaultMessage = "По твоему запросу ничего не нашлось.\nПопробуй изменить поиск."
    ),
    Empty(
        emoji = "💬",
        defaultTitle = "Пока пусто",
        defaultMessage = "Здесь ещё ничего нет.\nСтань первым!"
    ),
    AuthError(
        emoji = "🔐",
        defaultTitle = "Требуется вход",
        defaultMessage = "Сессия истекла.\nВойди снова, чтобы продолжить."
    )
}

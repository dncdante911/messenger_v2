package com.worldmates.messenger.ui.groups.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmates.messenger.R
import com.worldmates.messenger.data.model.Message
import com.worldmates.messenger.ui.preferences.UIStyle
import com.worldmates.messenger.ui.preferences.rememberUIStyle

/**
 * 📌 Баннер закрепленного сообщения для групповых чатов
 *
 * Поддерживает два стиля:
 * - UIStyle.TELEGRAM - минималистичный стиль Telegram
 * - UIStyle.WORLDMATES - карточный стиль с градиентами и анимациями
 *
 * @param pinnedMessage Закрепленное сообщение
 * @param decryptedText Расшифрованный текст сообщения
 * @param onBannerClick Клик по баннеру (прокрутка к сообщению)
 * @param onUnpinClick Клик по кнопке открепления (только для админов/модераторов)
 * @param canUnpin Может ли пользователь открепить сообщение
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun PinnedMessageBanner(
    pinnedMessage: Message,
    decryptedText: String,
    onBannerClick: () -> Unit,
    onUnpinClick: () -> Unit,
    canUnpin: Boolean = false
) {
    val uiStyle = rememberUIStyle()

    // Анимация появления баннера
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(300)
        ) + fadeOut()
    ) {
        when (uiStyle) {
            UIStyle.TELEGRAM -> {
                TelegramPinnedBanner(
                    pinnedMessage = pinnedMessage,
                    decryptedText = decryptedText,
                    onBannerClick = onBannerClick,
                    onUnpinClick = onUnpinClick,
                    canUnpin = canUnpin
                )
            }
            UIStyle.WORLDMATES -> {
                WorldMatesPinnedBanner(
                    pinnedMessage = pinnedMessage,
                    decryptedText = decryptedText,
                    onBannerClick = onBannerClick,
                    onUnpinClick = onUnpinClick,
                    canUnpin = canUnpin
                )
            }
        }
    }
}

/**
 * 📱 Telegram-style минималистичный баннер
 */
@Composable
private fun TelegramPinnedBanner(
    pinnedMessage: Message,
    decryptedText: String,
    onBannerClick: () -> Unit,
    onUnpinClick: () -> Unit,
    canUnpin: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onBannerClick),
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Иконка Pin
            Icon(
                imageVector = Icons.Default.PushPin,
                contentDescription = stringResource(R.string.pin_message_short),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Текст
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.pinned_label),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = decryptedText.ifBlank { stringResource(R.string.media_label) },
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Кнопка открепления (только для админов)
            if (canUnpin) {
                IconButton(
                    onClick = onUnpinClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.unpin_message_short),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * 🌟 WorldMates-style баннер с градиентами и анимациями
 */
@Composable
private fun WorldMatesPinnedBanner(
    pinnedMessage: Message,
    decryptedText: String,
    onBannerClick: () -> Unit,
    onUnpinClick: () -> Unit,
    canUnpin: Boolean
) {
    // Анимация пульсации иконки
    val infiniteTransition = rememberInfiniteTransition(label = "pin_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pin_scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .clickable(onClick = onBannerClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                        )
                    )
                )
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Анимированная иконка Pin с градиентным фоном
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = stringResource(R.string.pin_message_short),
                        tint = Color.White,
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Текст с красивой типографикой
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.pinned_message_with_emoji),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = decryptedText.ifBlank { stringResource(R.string.media_with_emoji) },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Кнопка открепления (только для админов) с градиентным фоном
                if (canUnpin) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            )
                            .clickable(onClick = onUnpinClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.unpin_message_short),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
package com.worldmates.messenger.ui.music

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.worldmates.messenger.services.MusicPlaybackService

/**
 * Продвинутий музичний плеєр з підтримкою:
 * - Фонове відтворення (працює при згорнутому додатку та з вимкненим екраном)
 * - Управління з шторки повідомлень
 * - Управління з екрану блокування
 * - AES-256-GCM розшифрування аудіо файлів
 * - Перемотка, таймер сну, повтор
 */
@Composable
fun AdvancedMusicPlayer(
    audioUrl: String,
    title: String = "Аудіо",
    artist: String = "",
    timestamp: Long = 0L,
    iv: String? = null,
    tag: String? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val playbackState by MusicPlaybackService.playbackState.collectAsState()
    val trackInfo by MusicPlaybackService.currentTrackInfo.collectAsState()

    // Запускаємо сервіс тільки якщо цей трек ще не грає
    LaunchedEffect(audioUrl) {
        if (trackInfo.url != audioUrl) {
            MusicPlaybackService.startPlayback(
                context = context,
                audioUrl = audioUrl,
                title = title,
                artist = artist,
                timestamp = timestamp,
                iv = iv,
                tag = tag
            )
        }
    }

    // Анімація візуалізатора
    val infiniteTransition = rememberInfiniteTransition(label = "music_wave")
    val waves = (0 until 7).map { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.2f + (index % 3) * 0.15f,
            targetValue = 0.7f + (index % 4) * 0.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 500 + index * 120,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "wave_$index"
        )
    }

    var showSpeedMenu by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableStateOf(1f) }
    var repeatMode by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1A1A2E),
                            Color(0xFF16213E),
                            Color(0xFF0F3460)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Верхній бар
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Плеєр",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )

                    // Кнопка згортання (плеєр продовжить грати у фоні)
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Згорнути",
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Візуалізатор / Обкладинка
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF0084FF).copy(alpha = 0.3f),
                                    Color(0xFF0084FF).copy(alpha = 0.1f),
                                    Color.Transparent
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Анімовані хвилі
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        waves.forEach { wave ->
                            val height = if (playbackState.isPlaying) wave.value else 0.15f
                            Box(
                                modifier = Modifier
                                    .width(10.dp)
                                    .height((120 * height).dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color(0xFF00C6FF),
                                                Color(0xFF0084FF)
                                            )
                                        )
                                    )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Назва треку
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (artist.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = artist,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Прогрес бар
                Slider(
                    value = playbackState.currentPosition.toFloat(),
                    onValueChange = { newPosition ->
                        MusicPlaybackService.seekTo(context, newPosition.toLong())
                    },
                    valueRange = 0f..playbackState.duration.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF00C6FF),
                        activeTrackColor = Color(0xFF0084FF),
                        inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Час
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(playbackState.currentPosition),
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                    Text(
                        text = formatTime(playbackState.duration),
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Основні кнопки управління
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Повтор
                    IconButton(onClick = { repeatMode = !repeatMode }) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = "Повтор",
                            tint = if (repeatMode) Color(0xFF00C6FF) else Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Перемотка назад -15с
                    IconButton(
                        onClick = {
                            val newPos = (playbackState.currentPosition - 15000).coerceAtLeast(0)
                            MusicPlaybackService.seekTo(context, newPos)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "-15с",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Play/Pause
                    Surface(
                        onClick = {
                            if (playbackState.isPlaying) {
                                MusicPlaybackService.pausePlayback(context)
                            } else {
                                MusicPlaybackService.resumePlayback(context)
                            }
                        },
                        shape = CircleShape,
                        color = Color(0xFF0084FF),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (playbackState.isBuffering) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (playbackState.isPlaying) "Пауза" else "Грати",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }

                    // Перемотка вперед +15с
                    IconButton(
                        onClick = {
                            val newPos = (playbackState.currentPosition + 15000).coerceAtMost(playbackState.duration)
                            MusicPlaybackService.seekTo(context, newPos)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "+15с",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Швидкість
                    Surface(
                        onClick = { showSpeedMenu = true },
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.1f),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = "${playbackSpeed}x",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Підказка про фонове відтворення
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.05f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFF00C6FF).copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Музика продовжить грати при згортанні та вимкненні екрану",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Кнопка зупинки
                TextButton(
                    onClick = {
                        MusicPlaybackService.stopPlayback(context)
                        onDismiss()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Зупинити",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp
                    )
                }
            }
        }
    }

    // Меню вибору швидкості
    if (showSpeedMenu) {
        SpeedSelectionDialog(
            currentSpeed = playbackSpeed,
            onSpeedSelect = { speed ->
                playbackSpeed = speed
                MusicPlaybackService.setSpeed(speed)
                showSpeedMenu = false
            },
            onDismiss = { showSpeedMenu = false }
        )
    }
}

/**
 * Мінімізований плеєр-бар для нижньої частини екрану.
 * Показується коли музика грає у фоні.
 * Натисніть будь-де на бар для відкриття повного плеєра.
 */
@Composable
fun MusicMiniBar(
    onExpand: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val playbackState by MusicPlaybackService.playbackState.collectAsState()
    val trackInfo by MusicPlaybackService.currentTrackInfo.collectAsState()

    // Не показуємо якщо сервіс не активний
    if (trackInfo.url.isEmpty()) return

    // Анімація пульсу для індикатора відтворення
    val infiniteTransition = rememberInfiniteTransition(label = "mini_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        onClick = onExpand
    ) {
        Column {
            // Прогрес бар зверху
            LinearProgressIndicator(
                progress = {
                    if (playbackState.duration > 0)
                        playbackState.currentPosition.toFloat() / playbackState.duration.toFloat()
                    else 0f
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = Color(0xFF0084FF),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Іконка з анімацією
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF0084FF), Color(0xFF00C6FF))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Default.Equalizer else Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Назва та час
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = trackInfo.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Живий індикатор відтворення
                        if (playbackState.isPlaying) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50).copy(alpha = pulseAlpha))
                            )
                        }
                        Text(
                            text = "${formatTime(playbackState.currentPosition)} / ${formatTime(playbackState.duration)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Play/Pause
                IconButton(
                    onClick = {
                        if (playbackState.isPlaying) {
                            MusicPlaybackService.pausePlayback(context)
                        } else {
                            MusicPlaybackService.resumePlayback(context)
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Пауза" else "Грати",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Стрілка для відкриття повного плеєра
                IconButton(
                    onClick = onExpand
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Відкрити плеєр",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Зупинка
                IconButton(onClick = {
                    MusicPlaybackService.stopPlayback(context)
                    onStop()
                }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Зупинити",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedSelectionDialog(
    currentSpeed: Float,
    onSpeedSelect: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Швидкість відтворення") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                speeds.forEach { speed ->
                    Surface(
                        onClick = { onSpeedSelect(speed) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (speed == currentSpeed)
                            MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${speed}x",
                                fontWeight = if (speed == currentSpeed) FontWeight.Bold else FontWeight.Normal
                            )
                            if (speed == currentSpeed) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Обрано",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрити")
            }
        }
    )
}

private fun formatTime(millis: Long): String {
    if (millis <= 0) return "0:00"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

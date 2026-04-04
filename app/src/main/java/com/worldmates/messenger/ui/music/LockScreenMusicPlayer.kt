package com.worldmates.messenger.ui.music

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.worldmates.messenger.services.MusicPlaybackService
import kotlin.math.absoluteValue

/**
 * Плеєр для екрану блокування пристрою.
 * Показує музику, яка грає, з повними елементами управління.
 * Підтримує свайп для розблокування на фоні.
 */
@Composable
fun LockScreenMusicPlayer(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val playbackState by MusicPlaybackService.playbackState.collectAsState()
    val trackInfo by MusicPlaybackService.currentTrackInfo.collectAsState()

    // Не показуємо якщо сервіс не активний
    if (trackInfo.url.isEmpty()) {
        return
    }

    var verticalOffset by remember { mutableFloatStateOf(0f) }
    val maxVerticalDrag = 100f

    // Анімація для візуалізатора
    val infiniteTransition = rememberInfiniteTransition(label = "lock_screen_waves")
    val waves = (0 until 5).map { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 600 + index * 100,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "lock_wave_$index"
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F0F1E),
                        Color(0xFF1A1A2E),
                        Color(0xFF0F0F1E)
                    )
                )
            )
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        verticalOffset += dragAmount.y
                        verticalOffset = verticalOffset.coerceIn(-maxVerticalDrag, maxVerticalDrag)
                    },
                    onDragEnd = {
                        verticalOffset = 0f
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Заголовок
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Экран блокирования",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "12:34",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.weight(0.5f))

            // Обкладинка альбому з анімованими хвилями
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF0084FF).copy(alpha = 0.4f),
                                Color(0xFF0084FF).copy(alpha = 0.1f),
                                Color.Transparent
                            )
                        )
                    )
                    .shadow(elevation = 16.dp, shape = RoundedCornerShape(32.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (trackInfo.coverUrl.isNotEmpty()) {
                    AsyncImage(
                        model = trackInfo.coverUrl,
                        contentDescription = trackInfo.title,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(32.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    // Відсутній стиль обкладинки - показуємо анімований музичний символ
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(120.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        waves.forEach { wave ->
                            val height = if (playbackState.isPlaying) wave.value else 0.25f
                            Box(
                                modifier = Modifier
                                    .width(8.dp)
                                    .height((120 * height).dp)
                                    .clip(RoundedCornerShape(4.dp))
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

                // Кнопка Play/Pause в центрі
                if (trackInfo.coverUrl.isNotEmpty()) {
                    Surface(
                        onClick = {
                            if (playbackState.isPlaying) {
                                MusicPlaybackService.pausePlayback(context)
                            } else {
                                MusicPlaybackService.resumePlayback(context)
                            }
                        },
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.5f),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playbackState.isPlaying) "Пауза" else "Грати",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Інформація про трек
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = trackInfo.title,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                if (trackInfo.artist.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = trackInfo.artist,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Прогрес бар
            Column(modifier = Modifier.fillMaxWidth()) {
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
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                    Text(
                        text = formatTime(playbackState.duration),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Кнопки управління
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Попередня
                IconButton(
                    onClick = {
                        val newPos = (playbackState.currentPosition - 15000).coerceAtLeast(0)
                        MusicPlaybackService.seekTo(context, newPos)
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay10,
                        contentDescription = "-15с",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Play/Pause (основна)
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
                    modifier = Modifier
                        .size(72.dp)
                        .shadow(elevation = 8.dp, shape = CircleShape)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (playbackState.isBuffering) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playbackState.isPlaying) "Пауза" else "Грати",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }

                // Наступна
                IconButton(
                    onClick = {
                        val newPos = (playbackState.currentPosition + 15000).coerceAtMost(playbackState.duration)
                        MusicPlaybackService.seekTo(context, newPos)
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Forward10,
                        contentDescription = "+15с",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.5f))

            // Підказка про розблокування
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = verticalOffset.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Потягніть для розблокування",
                    color = Color.White.copy(alpha = 0.3f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * Компактний плеєр для значка на екрані блокування.
 * Показується як невеликий віджет з базовим управлінням.
 */
@Composable
fun LockScreenMusicPlayerCompact(
    modifier: Modifier = Modifier,
    onExpand: () -> Unit = {}
) {
    val context = LocalContext.current
    val playbackState by MusicPlaybackService.playbackState.collectAsState()
    val trackInfo by MusicPlaybackService.currentTrackInfo.collectAsState()

    if (trackInfo.url.isEmpty()) {
        return
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onExpand),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.1f),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Іконка
            Box(
                modifier = Modifier
                    .size(48.dp)
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
                    modifier = Modifier.size(24.dp)
                )
            }

            // Інформація
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = trackInfo.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (trackInfo.artist.isNotEmpty()) {
                    Text(
                        text = trackInfo.artist,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Play/Pause кнопка
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
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Пауза" else "Грати",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

/**
 * Форматує мілісекунди в формат ММ:СС
 */
fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

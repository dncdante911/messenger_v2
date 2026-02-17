package com.worldmates.messenger.ui.video

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 🎬 ПРОДВИНУТЫЙ ВИДЕОПЛЕЕР С УНИКАЛЬНЫМИ ФИЧАМИ
 *
 * Функции:
 * - ✅ Жесты: свайп влево/вправо для перемотки
 * - ✅ Жесты: свайп вверх/вниз слева для яркости
 * - ✅ Жесты: свайп вверх/вниз справа для громкости
 * - ✅ Скорость воспроизведения (0.5x, 0.75x, 1x, 1.25x, 1.5x, 2x)
 * - ✅ Picture-in-Picture (Android 8+)
 * - ✅ Двойной тап слева/справа для перемотки на ±10 сек
 * - ✅ Автоскрытие контролов через 3 секунды
 * - ✅ Индикаторы громкости/яркости/перемотки
 * - ✅ Плавные анимации
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedVideoPlayer(
    videoUrl: String,
    onDismiss: () -> Unit,
    enablePiP: Boolean = true,
    autoPlay: Boolean = true
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val configuration = LocalConfiguration.current

    // Состояния плеера
    var isPlaying by remember { mutableStateOf(autoPlay) }
    var showControls by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var isBuffering by remember { mutableStateOf(false) }

    // Дополнительные UI состояния
    var playbackSpeed by remember { mutableStateOf(1f) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var volumeLevel by remember { mutableStateOf(0f) }
    var brightnessLevel by remember { mutableStateOf(0f) }
    var showVolumeIndicator by remember { mutableStateOf(false) }
    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var showSeekIndicator by remember { mutableStateOf(false) }
    var seekAmount by remember { mutableStateOf(0) }

    val speedOptions = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

    // ExoPlayer
    val exoPlayer = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(Uri.parse(videoUrl))
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = autoPlay

            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onPlaybackStateChanged(state: Int) {
                    isBuffering = state == Player.STATE_BUFFERING
                }
            })
        }
    }

    // Получаем аудио менеджер для громкости
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    // Автоматическое скрытие контролов
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(3000)
            showControls = false
        }
    }

    // Обновление позиции
    LaunchedEffect(exoPlayer) {
        while (isActive) {
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0)
            delay(100)
        }
    }

    // Скрытие индикаторов
    LaunchedEffect(showVolumeIndicator) {
        if (showVolumeIndicator) {
            delay(1000)
            showVolumeIndicator = false
        }
    }

    LaunchedEffect(showBrightnessIndicator) {
        if (showBrightnessIndicator) {
            delay(1000)
            showBrightnessIndicator = false
        }
    }

    LaunchedEffect(showSeekIndicator) {
        if (showSeekIndicator) {
            delay(500)
            showSeekIndicator = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Dialog(
        onDismissRequest = {
            exoPlayer.pause()
            onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // ExoPlayer View
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        useController = false
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        // Жесты для управления
                        detectDragGestures(
                            onDragStart = { offset ->
                                showControls = false
                            },
                            onDrag = { change, dragAmount ->
                                val screenWidth = size.width
                                val screenHeight = size.height

                                // Горизонтальный свайп - перемотка
                                if (abs(dragAmount.x) > abs(dragAmount.y)) {
                                    val seekDelta = (dragAmount.x / screenWidth * 30000).toLong()
                                    val newPos = (currentPosition + seekDelta).coerceIn(0, duration)
                                    exoPlayer.seekTo(newPos)

                                    seekAmount = (seekDelta / 1000).toInt()
                                    showSeekIndicator = true
                                }
                                // Вертикальный свайп слева - яркость
                                else if (change.position.x < screenWidth / 2) {
                                    val delta = -dragAmount.y / screenHeight
                                    activity?.window?.let { window ->
                                        val currentBrightness = window.attributes.screenBrightness
                                        val newBrightness = (currentBrightness + delta).coerceIn(0f, 1f)
                                        val layoutParams = window.attributes
                                        layoutParams.screenBrightness = newBrightness
                                        window.attributes = layoutParams
                                        brightnessLevel = newBrightness
                                        showBrightnessIndicator = true
                                    }
                                }
                                // Вертикальный свайп справа - громкость
                                else {
                                    val delta = (-dragAmount.y / screenHeight * maxVolume).toInt()
                                    val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                    val newVol = (currentVol + delta).coerceIn(0, maxVolume)
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                    volumeLevel = newVol.toFloat() / maxVolume
                                    showVolumeIndicator = true
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        // Тапы для управления
                        detectTapGestures(
                            onTap = {
                                showControls = !showControls
                            },
                            onDoubleTap = { offset ->
                                // Двойной тап слева - перемотка назад
                                if (offset.x < size.width / 3) {
                                    exoPlayer.seekTo((currentPosition - 10000).coerceAtLeast(0))
                                    seekAmount = -10
                                    showSeekIndicator = true
                                }
                                // Двойной тап справа - перемотка вперед
                                else if (offset.x > size.width * 2 / 3) {
                                    exoPlayer.seekTo((currentPosition + 10000).coerceAtMost(duration))
                                    seekAmount = 10
                                    showSeekIndicator = true
                                }
                                // Двойной тап в центре - пауза/плей
                                else {
                                    if (exoPlayer.isPlaying) {
                                        exoPlayer.pause()
                                    } else {
                                        exoPlayer.play()
                                    }
                                }
                            }
                        )
                    }
            )

            // Индикатор буферизации
            if (isBuffering) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            // Индикатор громкости
            AnimatedVisibility(
                visible = showVolumeIndicator,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                VolumeIndicator(level = volumeLevel)
            }

            // Индикатор яркости
            AnimatedVisibility(
                visible = showBrightnessIndicator,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                BrightnessIndicator(level = brightnessLevel)
            }

            // Индикатор перемотки
            AnimatedVisibility(
                visible = showSeekIndicator,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                SeekIndicator(seconds = seekAmount)
            }

            // UI контролы
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Градиентный оверлей
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f))
                    )

                    // Верхний бар
                    TopControlBar(
                        title = "Видео",
                        onClose = {
                            exoPlayer.pause()
                            onDismiss()
                        },
                        onPiP = if (enablePiP && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            {
                                activity?.enterPictureInPictureMode(
                                    PictureInPictureParams.Builder()
                                        .setAspectRatio(Rational(16, 9))
                                        .build()
                                )
                            }
                        } else null
                    )

                    // Центральная кнопка Play/Pause
                    PlayPauseButton(
                        isPlaying = isPlaying,
                        onClick = {
                            if (exoPlayer.isPlaying) {
                                exoPlayer.pause()
                            } else {
                                exoPlayer.play()
                            }
                        },
                        modifier = Modifier.align(Alignment.Center)
                    )

                    // Нижний бар с контролами
                    BottomControlBar(
                        currentPosition = currentPosition,
                        duration = duration,
                        playbackSpeed = playbackSpeed,
                        onSeek = { position ->
                            exoPlayer.seekTo(position)
                        },
                        onSpeedClick = {
                            showSpeedMenu = true
                        },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }

            // Меню выбора скорости
            if (showSpeedMenu) {
                SpeedSelectionMenu(
                    currentSpeed = playbackSpeed,
                    speeds = speedOptions,
                    onSpeedSelect = { speed ->
                        playbackSpeed = speed
                        exoPlayer.setPlaybackSpeed(speed)
                        showSpeedMenu = false
                    },
                    onDismiss = { showSpeedMenu = false }
                )
            }
        }
    }
}

@Composable
private fun TopControlBar(
    title: String,
    onClose: () -> Unit,
    onPiP: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.7f),
                        Color.Transparent
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterStart)
        )

        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // PiP кнопка
            if (onPiP != null) {
                Surface(
                    onClick = onPiP,
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PictureInPictureAlt,
                            contentDescription = "PiP",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Кнопка закрытия
            Surface(
                onClick = onClose,
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.2f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.9f),
        modifier = modifier
            .size(72.dp)
            .shadow(8.dp, CircleShape)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.Black,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

@Composable
private fun BottomControlBar(
    currentPosition: Long,
    duration: Long,
    playbackSpeed: Float,
    onSeek: (Long) -> Unit,
    onSpeedClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.7f)
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Слайдер прогресса
        Slider(
            value = currentPosition.toFloat(),
            onValueChange = { newPosition ->
                onSeek(newPosition.toLong())
            },
            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color(0xFF0084FF),
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Время и скорость
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${formatVideoTime(currentPosition)} / ${formatVideoTime(duration)}",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            Surface(
                onClick = onSpeedClick,
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.2f),
                modifier = Modifier.padding(4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "Speed",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${playbackSpeed}x",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedSelectionMenu(
    currentSpeed: Float,
    speeds: List<Float>,
    onSpeedSelect: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .pointerInput(Unit) {
                detectTapGestures { onDismiss() }
            },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .padding(32.dp)
                .widthIn(max = 300.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Скорость воспроизведения",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                speeds.forEach { speed ->
                    Surface(
                        onClick = { onSpeedSelect(speed) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (speed == currentSpeed)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            Color.Transparent,
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
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (speed == currentSpeed) FontWeight.Bold else FontWeight.Normal
                            )
                            if (speed == currentSpeed) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VolumeIndicator(level: Float) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.8f),
        modifier = Modifier
            .size(120.dp)
            .shadow(8.dp, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (level > 0.5f) Icons.Default.VolumeUp
                             else if (level > 0f) Icons.Default.VolumeDown
                             else Icons.Default.VolumeOff,
                contentDescription = "Volume",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${(level * 100).roundToInt()}%",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun BrightnessIndicator(level: Float) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.8f),
        modifier = Modifier
            .size(120.dp)
            .shadow(8.dp, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (level > 0.5f) Icons.Default.BrightnessHigh
                             else Icons.Default.BrightnessLow,
                contentDescription = "Brightness",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${(level * 100).roundToInt()}%",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SeekIndicator(seconds: Int) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.8f),
        modifier = Modifier
            .size(120.dp)
            .shadow(8.dp, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (seconds > 0) Icons.Default.FastForward else Icons.Default.FastRewind,
                contentDescription = "Seek",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${if (seconds > 0) "+" else ""}$seconds сек",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun formatVideoTime(millis: Long): String {
    val seconds = (millis / 1000).toInt()
    val minutes = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", minutes, secs)
}

package com.worldmates.messenger.ui.media

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.isActive
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import com.worldmates.messenger.R
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * 🎨 УНИКАЛЬНЫЙ СТИЛЬНЫЙ ПРОСМОТРЩИК ИЗОБРАЖЕНИЙ
 * С градиентным фоном, анимациями и индикатором масштаба
 */
@Composable
fun FullscreenImageViewer(
    imageUrl: String,
    onDismiss: () -> Unit,
    onEdit: ((String) -> Unit)? = null
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var showControls by remember { mutableStateOf(true) }

    // Анимация появления
    val alpha by animateFloatAsState(
        targetValue = if (showControls) 1f else 0f,
        animationSpec = tween(300),
        label = "alpha"
    )

    // Анимация пульсации для индикатора масштаба
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    // Градиентный фон
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0A0A0A),
                            Color(0xFF000000),
                            Color(0xFF0A0A0A)
                        )
                    )
                )
        ) {
            // Изображение с жестами
            AsyncImage(
                model = imageUrl,
                contentDescription = "Full screen image",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)

                            // Показываем контролы при жестах
                            showControls = true

                            if (scale > 1f) {
                                val maxX = (size.width * (scale - 1)) / 2
                                val maxY = (size.height * (scale - 1)) / 2
                                offset = Offset(
                                    x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                                    y = (offset.y + pan.y).coerceIn(-maxY, maxY)
                                )
                            } else {
                                offset = Offset.Zero
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                // Double tap для zoom
                                scale = if (scale > 1f) 1f else 2f
                                if (scale == 1f) offset = Offset.Zero
                            },
                            onTap = {
                                // Toggle контролы
                                showControls = !showControls
                            }
                        )
                    },
                contentScale = ContentScale.Fit
            )

            // ✨ СТИЛЬНЫЙ ВЕРХНИЙ БАР С ГРАДИЕНТОМ
            AnimatedVisibility(
                visible = showControls,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Box(
                    modifier = Modifier
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
                    // Кнопки управління
                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Кнопка редагування
                        if (onEdit != null) {
                            Surface(
                                onClick = { onEdit(imageUrl) },
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.2f),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        // Кнопка закриття
                        Surface(
                            onClick = onDismiss,
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    // Название
                    Text(
                        text = "Фото",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                }
            }

            // 🎯 ИНДИКАТОР МАСШТАБА (показываем когда zoom > 1)
            AnimatedVisibility(
                visible = scale > 1f && showControls,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF0084FF).copy(alpha = 0.9f),
                    modifier = Modifier.scale(pulseScale)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "${(scale * 100).roundToInt()}%",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 💡 ПОДСКАЗКА ПРИ ПЕРВОМ ОТКРЫТИИ
            var showHint by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(3000)
                showHint = false
            }

            AnimatedVisibility(
                visible = showHint && scale == 1f,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.viewer_hint_double_tap),
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * 📸 ГАЛЕРЕЯ ФОТО ЗІ СВАЙПОМ
 * Дозволяє гортати фото ліворуч/праворуч, зум, індикатори
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageGalleryViewer(
    imageUrls: List<String>,
    initialPage: Int = 0,
    onDismiss: () -> Unit,
    onEdit: ((String) -> Unit)? = null
) {
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { imageUrls.size }
    )
    var showControls by remember { mutableStateOf(true) }

    // Стани для кожної сторінки (zoom та offset)
    val scaleStates = remember { mutableStateMapOf<Int, Float>() }
    val offsetStates = remember { mutableStateMapOf<Int, Offset>() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0A0A0A),
                            Color(0xFF000000),
                            Color(0xFF0A0A0A)
                        )
                    )
                )
        ) {
            // Горизонтальний пейджер для свайпу між фото
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val currentScale = scaleStates[page] ?: 1f
                val currentOffset = offsetStates[page] ?: Offset.Zero

                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = imageUrls[page],
                        contentDescription = "Image ${page + 1}",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = currentScale,
                                scaleY = currentScale,
                                translationX = currentOffset.x,
                                translationY = currentOffset.y
                            )
                            .pointerInput(page) {
                                // Custom gesture handler: only consume events when pinch/zoom
                                // (multi-touch) OR already zoomed in. Single-finger swipe at
                                // scale=1f passes through to HorizontalPager for photo navigation.
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    do {
                                        val event = awaitPointerEvent()
                                        if (event.changes.any { it.isConsumed }) break
                                        val scale = scaleStates[page] ?: 1f
                                        val isMultiTouch = event.changes.count { it.pressed } >= 2
                                        if (isMultiTouch || scale > 1f) {
                                            val zoom = event.calculateZoom()
                                            val pan = event.calculatePan()
                                            val newScale = (scale * zoom).coerceIn(1f, 5f)
                                            scaleStates[page] = newScale
                                            showControls = true
                                            if (newScale > 1f) {
                                                val maxX = (size.width * (newScale - 1)) / 2f
                                                val maxY = (size.height * (newScale - 1)) / 2f
                                                val off = offsetStates[page] ?: Offset.Zero
                                                offsetStates[page] = Offset(
                                                    x = (off.x + pan.x).coerceIn(-maxX, maxX),
                                                    y = (off.y + pan.y).coerceIn(-maxY, maxY)
                                                )
                                            } else {
                                                offsetStates[page] = Offset.Zero
                                            }
                                            event.changes.forEach { it.consume() }
                                        }
                                        // single-touch at scale=1f → don't consume → pager handles
                                    } while (event.changes.any { it.pressed })
                                }
                            }
                            .pointerInput(page) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        val newScale = if (currentScale > 1f) 1f else 2f
                                        scaleStates[page] = newScale
                                        if (newScale == 1f) {
                                            offsetStates[page] = Offset.Zero
                                        }
                                    },
                                    onTap = {
                                        showControls = !showControls
                                    }
                                )
                            },
                        contentScale = ContentScale.Fit
                    )
                }
            }

            // ✨ ВЕРХНІЙ БАР З ІНДИКАТОРОМ СТОРІНКИ
            AnimatedVisibility(
                visible = showControls,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Box(
                    modifier = Modifier
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
                    // Індикатор сторінки (X з Y)
                    Text(
                        text = "${pagerState.currentPage + 1} з ${imageUrls.size}",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )

                    // Кнопки управління
                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Кнопка редагування
                        if (onEdit != null) {
                            Surface(
                                onClick = {
                                    val currentUrl = imageUrls.getOrNull(pagerState.currentPage)
                                    if (currentUrl != null) onEdit(currentUrl)
                                },
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.2f),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        // Кнопка закриття
                        Surface(
                            onClick = onDismiss,
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 🎯 ІНДИКАТОР МАСШТАБУ
            val currentPageScale = scaleStates[pagerState.currentPage] ?: 1f
            AnimatedVisibility(
                visible = currentPageScale > 1f && showControls,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF0084FF).copy(alpha = 0.9f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "${(currentPageScale * 100).roundToInt()}%",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 📍 ІНДИКАТОРИ DOTS ЗНИЗУ
            if (imageUrls.size > 1) {
                AnimatedVisibility(
                    visible = showControls,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .background(
                                color = Color.Black.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        imageUrls.forEachIndexed { index, _ ->
                            val isSelected = pagerState.currentPage == index
                            Box(
                                modifier = Modifier
                                    .size(
                                        width = if (isSelected) 24.dp else 8.dp,
                                        height = 8.dp
                                    )
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (isSelected) {
                                            Color.White
                                        } else {
                                            Color.White.copy(alpha = 0.4f)
                                        }
                                    )
                            )
                        }
                    }
                }
            }

            // 💡 ПІДКАЗКА ПРИ ПЕРШОМУ ВІДКРИТТІ
            var showHint by remember { mutableStateOf(imageUrls.size > 1) }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(3000)
                showHint = false
            }

            AnimatedVisibility(
                visible = showHint,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwipeLeft,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.viewer_hint_swipe),
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            text = stringResource(R.string.viewer_hint_double_tap),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * 🎬 ІНЛАЙН ВІДЕО ПЛЕЄР (для використання всередині чату)
 *
 * @param shape         Форма контейнера (передається з VideoMessageComponent за вибраним стилем)
 * @param isCircularFrame true для стилю CIRCLE — показує дуговий прогрес замість лінійного
 */
@Composable
fun InlineVideoPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    isCircularFrame: Boolean = false,
    onFullscreenClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }

    val exoPlayer = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(Uri.parse(videoUrl))
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = false
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            })
        }
    }

    LaunchedEffect(exoPlayer) {
        while (isActive) {
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0)
            kotlinx.coroutines.delay(100)
        }
    }

    // Auto-hide controls 0.8s after playback starts
    LaunchedEffect(isPlaying, showControls) {
        if (isPlaying && showControls) {
            kotlinx.coroutines.delay(800)
            showControls = false
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp, max = 300.dp)
            .clip(shape)
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    setOnClickListener { showControls = !showControls }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Arc progress track drawn around the circle edge
        if (isCircularFrame && duration > 0) {
            val progress = currentPosition.toFloat() / duration.toFloat()
            Canvas(modifier = Modifier.fillMaxSize().padding(5.dp)) {
                val strokeW = 3.5.dp.toPx()
                val r = (minOf(size.width, size.height) / 2f) - strokeW
                val cx = size.width / 2f
                val cy = size.height / 2f
                val arcSz = Size(r * 2f, r * 2f)
                val arcTL = Offset(cx - r, cy - r)
                val arcStroke = Stroke(width = strokeW, cap = StrokeCap.Round)
                // Background ring
                drawArc(
                    color = Color.White.copy(alpha = 0.22f),
                    startAngle = -90f, sweepAngle = 360f, useCenter = false,
                    style = arcStroke, topLeft = arcTL, size = arcSz
                )
                // Progress arc
                if (progress > 0f) {
                    drawArc(
                        color = Color.White,
                        startAngle = -90f,
                        sweepAngle = 360f * progress.coerceIn(0f, 1f),
                        useCenter = false,
                        style = arcStroke, topLeft = arcTL, size = arcSz
                    )
                }
            }
        }

        // Dim overlay
        AnimatedVisibility(
            visible = showControls || !isPlaying,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.matchParentSize()
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    if (isCircularFrame)
                        Brush.radialGradient(listOf(Color.Black.copy(0.35f), Color.Transparent))
                    else
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(0.4f), Color.Transparent, Color.Black.copy(0.6f))
                        )
                )
            )
        }

        // Compact play/pause button (40 dp), auto-hides with playback
        AnimatedVisibility(
            visible = showControls || !isPlaying,
            enter = scaleIn(tween(160)) + fadeIn(tween(160)),
            exit = scaleOut(tween(220)) + fadeOut(tween(220)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                onClick = {
                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                    showControls = true
                },
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.52f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Fullscreen button — compact (28 dp)
        if (onFullscreenClick != null) {
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(250)),
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
            ) {
                Surface(
                    onClick = { exoPlayer.pause(); onFullscreenClick() },
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.52f),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "На весь екран",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }

        // Bottom bar: thin progress + time (only for non-circular frame)
        if (!isCircularFrame) {
            AnimatedVisibility(
                visible = showControls || !isPlaying,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(tween(200)),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(250)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(0.62f))
                            )
                        )
                        .padding(horizontal = 10.dp, bottom = 8.dp, top = 14.dp)
                ) {
                    // Thin 3 dp progress bar (no Slider thumb)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.25f))
                    ) {
                        val p = if (duration > 0)
                            (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                        else 0f
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(p)
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF60A5FA), Color(0xFF3B82F6))
                                    )
                                )
                        )
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatVideoTime(currentPosition),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = formatVideoTime(duration),
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        } else {
            // Circular: remaining time label
            AnimatedVisibility(
                visible = showControls || !isPlaying,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(250)),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
            ) {
                Text(
                    text = formatVideoTime(if (duration > 0) duration - currentPosition else 0L),
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * 🎬 УНИКАЛЬНЫЙ СТИЛЬНЫЙ ВИДЕОПЛЕЕР
 * С кастомным UI и анимациями
 */
@Composable
fun FullscreenVideoPlayer(
    videoUrl: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }

    val exoPlayer = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(Uri.parse(videoUrl))
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF

            // Слушатель состояния
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
            })
        }
    }

    // Оновлення позиції та тривалості
    LaunchedEffect(exoPlayer) {
        while (isActive) {
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0)
            kotlinx.coroutines.delay(100)
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
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
            // ExoPlayer
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        useController = false // Используем свой UI
                        setOnClickListener {
                            showControls = !showControls
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // ✨ ГРАДИЕНТНЫЙ ОВЕРЛЕЙ ДЛЯ КОНТРОЛОВ
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                        .clickable { showControls = false }
                )
            }

            // 🎮 ВЕРХНИЙ БАР С КНОПКОЙ ЗАКРЫТИЯ
            AnimatedVisibility(
                visible = showControls,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Box(
                    modifier = Modifier
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
                    Surface(
                        onClick = {
                            exoPlayer.pause()
                            onDismiss()
                        },
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier
                            .size(48.dp)
                            .align(Alignment.CenterEnd)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Text(
                        text = "Відео",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                }
            }

            // 🎯 ЦЕНТРАЛЬНАЯ КНОПКА PLAY/PAUSE
            AnimatedVisibility(
                visible = showControls,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Surface(
                    onClick = {
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                        } else {
                            exoPlayer.play()
                        }
                        isPlaying = !isPlaying
                    },
                    shape = CircleShape,
                    color = Color(0xFF0084FF).copy(alpha = 0.9f),
                    modifier = Modifier
                        .size(80.dp)
                        .border(4.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }

            // 📊 НИЖНІЙ БАР З ЧАСОВОЮ ШКАЛОЮ
            AnimatedVisibility(
                visible = showControls,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.7f)
                                )
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Слайдер
                        Slider(
                            value = currentPosition.toFloat(),
                            onValueChange = { newPosition ->
                                exoPlayer.seekTo(newPosition.toLong())
                            },
                            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color(0xFF0084FF),
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Час
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatVideoTime(currentPosition),
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = formatVideoTime(duration),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// Функція для форматування часу відео (MM:SS)
private fun formatVideoTime(millis: Long): String {
    if (millis <= 0) return "0:00"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

/**
 * 🎵 МІНІМІЗОВАНИЙ АУДІО ПЛЕЄР (внизу екрану)
 * Залишається при переході між чатами, як в Spotify
 */
@Composable
fun MiniAudioPlayer(
    audioUrl: String,
    audioTitle: String = "Аудіо",
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    onPlayPauseClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF667eea).copy(alpha = 0.1f),
                            Color(0xFF764ba2).copy(alpha = 0.1f)
                        )
                    )
                )
        ) {
            // Прогресбар зверху
            LinearProgressIndicator(
                progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = Color(0xFF0084FF),
                trackColor = Color.Gray.copy(alpha = 0.2f)
            )

            // Основний контент
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Ліва частина - іконка + інфо
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Іконка аудіо
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF0084FF),
                                        Color(0xFF00C6FF)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Назва та час
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = audioTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${formatVideoTime(currentPosition)} / ${formatVideoTime(duration)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Права частина - кнопки
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Кнопка Play/Pause
                    Surface(
                        onClick = onPlayPauseClick,
                        shape = CircleShape,
                        color = Color(0xFF0084FF),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Пауза" else "Грати",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Кнопка закриття
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрити",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * 🎵 МУЗИЧНИЙ ПЛЕЄР
 * Делегує відтворення до AdvancedMusicPlayer з підтримкою фонового відтворення.
 * Зберігається для зворотної сумісності.
 */
@Composable
fun SimpleAudioPlayer(
    audioUrl: String,
    onDismiss: () -> Unit,
    title: String = "Аудіо",
    artist: String = "",
    timestamp: Long = 0L,
    iv: String? = null,
    tag: String? = null
) {
    com.worldmates.messenger.ui.music.AdvancedMusicPlayer(
        audioUrl = audioUrl,
        title = title,
        artist = artist,
        timestamp = timestamp,
        iv = iv,
        tag = tag,
        onDismiss = onDismiss
    )
}
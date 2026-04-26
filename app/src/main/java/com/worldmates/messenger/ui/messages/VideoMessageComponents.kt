package com.worldmates.messenger.ui.messages

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor

private const val TAG = "VideoMessageRecorder"

/**
 * 🎬 Стилі рамок для відеоповідомлень
 */
enum class VideoMessageFrameStyle(val label: String, val emoji: String) {
    CIRCLE("Круглий", "⭕"),
    ROUNDED("Заокруглений", "🔲"),
    NEON("Неоновий", "💡"),
    GRADIENT("Градієнт", "🌈"),
    MINIMAL("Мінімальний", "⚪"),
    RAINBOW("Веселковий", "🌈")
}

/**
 * 📹 Отримати збережений стиль рамки відеоповідомлень
 */
fun getSavedVideoMessageFrameStyle(context: Context): VideoMessageFrameStyle {
    val prefs = context.getSharedPreferences("worldmates_prefs", Context.MODE_PRIVATE)
    val styleName = prefs.getString("video_message_frame_style", VideoMessageFrameStyle.CIRCLE.name)
    return try {
        VideoMessageFrameStyle.valueOf(styleName ?: VideoMessageFrameStyle.CIRCLE.name)
    } catch (e: Exception) {
        VideoMessageFrameStyle.CIRCLE
    }
}

/**
 * 📹 Зберегти стиль рамки відеоповідомлень
 */
fun saveVideoMessageFrameStyle(context: Context, style: VideoMessageFrameStyle) {
    val prefs = context.getSharedPreferences("worldmates_prefs", Context.MODE_PRIVATE)
    prefs.edit().putString("video_message_frame_style", style.name).apply()
}

/**
 * 🎬 Компонент для запису відеоповідомлень з реальним записом через CameraX
 */
@Composable
fun VideoMessageRecorder(
    maxDurationSeconds: Int = 120,  // 2 хвилини за замовчуванням
    isPremiumUser: Boolean = false,  // Преміум користувачі мають 5 хвилин
    onVideoRecorded: (File) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Максимальна тривалість залежить від статусу користувача
    val actualMaxDuration = if (isPremiumUser) 300 else maxDurationSeconds  // 5 хв для преміум

    var isRecording by remember { mutableStateOf(false) }
    var recordingDuration by remember { mutableStateOf(0) }
    var isFrontCamera by remember { mutableStateOf(true) }
    var videoFile by remember { mutableStateOf<File?>(null) }

    // ✅ CameraX Video Recording state
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var preview by remember { mutableStateOf<Preview?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    // Таймер для відліку часу запису
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingDuration = 0
            while (isRecording && recordingDuration < actualMaxDuration) {
                delay(1000)
                recordingDuration++
                // Автоматично зупинити при досягненні ліміту
                if (recordingDuration >= actualMaxDuration) {
                    Log.d(TAG, "⏱️ Max duration reached, stopping recording")
                    recording?.stop()
                    isRecording = false
                }
            }
        }
    }

    // Стиль рамки — re-read on every composition so changes in settings take effect
    var frameStyle by remember { mutableStateOf(getSavedVideoMessageFrameStyle(context)) }
    LaunchedEffect(Unit) {
        frameStyle = getSavedVideoMessageFrameStyle(context)
    }

    // ✅ Функція для початку запису
    fun startRecording() {
        val videoCapt = videoCapture ?: run {
            Log.e(TAG, "❌ VideoCapture is null, cannot start recording")
            return
        }

        // Створити файл для відео
        val file = createVideoFile(context)
        videoFile = file
        Log.d(TAG, "📹 Starting video recording to: ${file.absolutePath}")

        val outputOptions = FileOutputOptions.Builder(file).build()

        recording = videoCapt.output
            .prepareRecording(context, outputOptions)
            .apply {
                // ✅ Записувати аудіо якщо є дозвіл
                if (PermissionChecker.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PermissionChecker.PERMISSION_GRANTED
                ) {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        Log.d(TAG, "✅ Recording started")
                        isRecording = true
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!event.hasError()) {
                            Log.d(TAG, "✅ Recording finished successfully: ${file.absolutePath}")
                            Log.d(TAG, "📊 File size: ${file.length()} bytes")
                            if (file.exists() && file.length() > 0) {
                                onVideoRecorded(file)
                            } else {
                                Log.e(TAG, "❌ Video file is empty or doesn't exist")
                            }
                        } else {
                            Log.e(TAG, "❌ Recording error: ${event.error}, cause: ${event.cause?.message}")
                            file.delete()
                        }
                        isRecording = false
                        recording = null
                    }
                    is VideoRecordEvent.Status -> {
                        // Оновлення статусу запису
                    }
                    is VideoRecordEvent.Pause -> {
                        Log.d(TAG, "⏸️ Recording paused")
                    }
                    is VideoRecordEvent.Resume -> {
                        Log.d(TAG, "▶️ Recording resumed")
                    }
                }
            }
    }

    // ✅ Функція для зупинки запису
    fun stopRecording() {
        Log.d(TAG, "⏹️ Stopping recording...")
        recording?.stop()
    }

    // ✅ Функція для ініціалізації камери з VideoCapture
    fun bindCameraUseCases(
        camProvider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        previewViewInstance: PreviewView,
        useFrontCamera: Boolean
    ) {
        Log.d(TAG, "📷 Binding camera use cases, front camera: $useFrontCamera")

        // Відв'язати попередні use cases
        camProvider.unbindAll()

        // Camera selector
        val cameraSelector = if (useFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        // Preview
        val newPreview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(previewViewInstance.surfaceProvider)
            }
        preview = newPreview

        // ✅ Recorder для відео з аудіо
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD))  // 720p
            .build()

        val newVideoCapture = VideoCapture.withOutput(recorder)
        videoCapture = newVideoCapture

        try {
            camProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                newPreview,
                newVideoCapture
            )
            Log.d(TAG, "✅ Camera bound successfully with Preview + VideoCapture")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to bind camera use cases", e)
        }
    }

    // ✅ Ініціалізація камери при mount — тільки отримуємо provider
    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            Log.d(TAG, "🧹 Disposing camera resources")
            recording?.stop()
            cameraProvider?.unbindAll()
        }
    }

    // ✅ Прив'язуємо камеру коли і provider, і previewView готові
    LaunchedEffect(cameraProvider, previewView, isFrontCamera) {
        val provider = cameraProvider ?: return@LaunchedEffect
        val pv = previewView ?: return@LaunchedEffect
        bindCameraUseCases(provider, lifecycleOwner, pv, isFrontCamera)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ✅ Camera Preview з VideoCapture
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    // COMPATIBLE використовує TextureView — коректно рендерить
                    // всередині Compose overlay (SurfaceView в PERFORMANCE ховається за canvas)
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    previewView = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Накладення рамки
        VideoMessageFrameOverlay(
            style = frameStyle,
            isRecording = isRecording,
            modifier = Modifier.fillMaxSize()
        )

        // Верхня панель
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Кнопка закриття
            IconButton(
                onClick = {
                    if (isRecording) {
                        recording?.stop()
                    }
                    onCancel()
                },
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Закрити",
                    tint = Color.White
                )
            }

            // Таймер
            if (isRecording) {
                Box(
                    modifier = Modifier
                        .background(Color.Red.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Пульсуюча точка запису
                        val infiniteTransition = rememberInfiniteTransition(label = "recording")
                        val alpha by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 0.3f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(500),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "alpha"
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color.White.copy(alpha = alpha), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatRecordingTime(recordingDuration),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = " / ${formatRecordingTime(actualMaxDuration)}",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Кнопка перемикання камери (тільки коли не записуємо)
            IconButton(
                onClick = {
                    if (!isRecording) {
                        isFrontCamera = !isFrontCamera
                    }
                },
                enabled = !isRecording,
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (isRecording) Color.Gray.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.5f),
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = "Перемкнути камеру",
                    tint = if (isRecording) Color.Gray else Color.White
                )
            }
        }

        // Нижня панель з кнопкою запису
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
                .align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Підказка
            if (!isRecording) {
                Text(
                    text = "Натисніть для запису (до ${actualMaxDuration / 60} хв)",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // Кнопка запису
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .border(4.dp, Color.White, CircleShape)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(if (isRecording) Color.Red else Color.White)
                    .clickable {
                        if (isRecording) {
                            // ✅ Зупинити запис
                            stopRecording()
                        } else {
                            // ✅ Почати запис
                            startRecording()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isRecording) {
                    // Показати квадрат "стоп"
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color.White, RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }
}

// VideoMessageCameraPreview видалено - тепер preview інтегровано в VideoMessageRecorder

/**
 * 🎨 Накладення рамки на відеоповідомлення під час запису.
 *
 * Малює темну маску поверх превью камери з «вирізом» у формі, що відповідає
 * вибраному стилю, щоб користувач бачив саме ту область, яка потрапить у відео.
 */
@Composable
fun VideoMessageFrameOverlay(
    style: VideoMessageFrameStyle,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "overlay")
    val neonAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(950), RepeatMode.Reverse),
        label = "neon"
    )
    val rainbowHue by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "hue"
    )

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f

        val isCircle = style == VideoMessageFrameStyle.CIRCLE
        val isOval   = style == VideoMessageFrameStyle.GRADIENT

        // Capture area dimensions
        val frameR = minOf(size.width, size.height) * 0.43f
        val frameW = size.width * 0.90f
        val frameH = if (isOval) frameW * 0.68f else size.height * 0.52f
        val cornerR = when (style) {
            VideoMessageFrameStyle.MINIMAL -> 14.dp.toPx()
            else -> 28.dp.toPx()
        }
        val frameLeft   = (size.width  - frameW) / 2f
        val frameTop    = (size.height - frameH) / 2f
        val frameRight  = (size.width  + frameW) / 2f
        val frameBottom = (size.height + frameH) / 2f

        // Dark cutout mask: full screen minus the capture window
        val outerPath = Path().apply { addRect(Rect(0f, 0f, size.width, size.height)) }
        val innerPath = Path().apply {
            when {
                isCircle -> addOval(Rect(cx - frameR, cy - frameR, cx + frameR, cy + frameR))
                isOval   -> addOval(Rect(frameLeft, frameTop, frameRight, frameBottom))
                else     -> addRoundRect(RoundRect(frameLeft, frameTop, frameRight, frameBottom,
                                                   cornerRadius = CornerRadius(cornerR)))
            }
        }
        drawPath(Path.combine(PathOperation.Difference, outerPath, innerPath), Color.Black.copy(alpha = 0.60f))

        // Border colour per style
        val borderColor = when (style) {
            VideoMessageFrameStyle.NEON    -> Color(0xFF00FFFF).copy(alpha = neonAlpha)
            VideoMessageFrameStyle.RAINBOW -> {
                val rgb = android.graphics.Color.HSVToColor(floatArrayOf(rainbowHue % 360f, 1f, 1f))
                Color(rgb)
            }
            VideoMessageFrameStyle.MINIMAL -> Color.White.copy(alpha = 0.45f)
            else -> Color.White.copy(alpha = 0.82f)
        }
        val borderW  = 2.5.dp.toPx()
        val bStroke  = Stroke(borderW, cap = StrokeCap.Round)
        when {
            isCircle -> drawCircle(borderColor, frameR, Offset(cx, cy), style = bStroke)
            isOval   -> drawOval(
                color = borderColor,
                topLeft = Offset(frameLeft, frameTop),
                size = Size(frameW, frameH),
                style = bStroke
            )
            else -> drawRoundRect(
                color = borderColor,
                topLeft = Offset(frameLeft, frameTop),
                size = Size(frameW, frameH),
                cornerRadius = CornerRadius(cornerR),
                style = bStroke
            )
        }

        // Recording: red pulsing outline just outside the border
        if (isRecording) {
            val pad = borderW + 3.dp.toPx()
            val recStroke = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round)
            val recColor  = Color.Red.copy(alpha = 0.82f)
            when {
                isCircle -> drawCircle(recColor, frameR + pad, Offset(cx, cy), style = recStroke)
                isOval   -> drawOval(
                    color = recColor,
                    topLeft = Offset(frameLeft - pad, frameTop - pad),
                    size = Size(frameW + pad * 2, frameH + pad * 2),
                    style = recStroke
                )
                else -> drawRoundRect(
                    color = recColor,
                    topLeft = Offset(frameLeft - pad, frameTop - pad),
                    size = Size(frameW + pad * 2, frameH + pad * 2),
                    cornerRadius = CornerRadius(cornerR + pad),
                    style = recStroke
                )
            }
        }
    }
}

/**
 * 📹 Bubble для відображення відеоповідомлення в чаті
 */
@Composable
fun VideoMessageBubble(
    videoUrl: String,
    duration: Int,  // в секундах
    frameStyle: VideoMessageFrameStyle,
    isFromMe: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = when (frameStyle) {
        VideoMessageFrameStyle.CIRCLE -> CircleShape
        else -> RoundedCornerShape(16.dp)
    }

    val thumbnail by produceState<Bitmap?>(initialValue = null, videoUrl) {
        value = withContext(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(videoUrl, HashMap())
                val bmp = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                retriever.release()
                bmp
            } catch (e: Exception) {
                null
            }
        }
    }

    Box(
        modifier = modifier
            .size(if (frameStyle == VideoMessageFrameStyle.CIRCLE) 200.dp else 220.dp)
            .clip(shape)
            .background(Color.Black)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // Thumbnail відео — перший кадр через MediaMetadataRetriever
        thumbnail?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Кнопка play
        Icon(
            imageVector = Icons.Default.PlayCircle,
            contentDescription = "Відтворити",
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(48.dp)
        )

        // Тривалість
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = formatRecordingTime(duration),
                color = Color.White,
                fontSize = 12.sp
            )
        }

        // Рамка
        when (frameStyle) {
            VideoMessageFrameStyle.NEON -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(2.dp, Color(0xFF00FFFF), shape)
                )
            }
            VideoMessageFrameStyle.GRADIENT -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            2.dp,
                            Brush.linearGradient(
                                listOf(Color(0xFF667eea), Color(0xFF764ba2), Color(0xFFf093fb))
                            ),
                            shape
                        )
                )
            }
            else -> {}
        }
    }
}

/**
 * 📹 Селектор стилю рамки відеоповідомлень
 */
@Composable
fun VideoMessageFrameSelector(
    currentStyle: VideoMessageFrameStyle,
    onStyleSelected: (VideoMessageFrameStyle) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(32.dp)
                .clickable(enabled = false) { },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a1a))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🎬 Стиль відеоповідомлень",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                VideoMessageFrameStyle.entries.forEach { style ->
                    val isSelected = style == currentStyle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) Color(0xFF4CAF50).copy(alpha = 0.3f)
                                else Color.Transparent
                            )
                            .clickable {
                                saveVideoMessageFrameStyle(context, style)
                                onStyleSelected(style)
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = style.emoji,
                            fontSize = 24.sp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = style.label,
                            fontSize = 16.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.White else Color(0xFFbbbbbb)
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50)
                            )
                        }
                    }
                }
            }
        }
    }
}

// Helper functions

private fun formatRecordingTime(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%d:%02d", mins, secs)
}

private fun createVideoFile(context: Context): File {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val storageDir = context.cacheDir
    return File.createTempFile("VIDEO_${timestamp}_", ".mp4", storageDir)
}

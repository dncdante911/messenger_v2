package com.worldmates.messenger.ui.calls

import android.Manifest
import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModelProvider
import coil.compose.AsyncImage
import com.worldmates.messenger.network.WebRTCManager
import com.worldmates.messenger.ui.theme.ThemeManager
import com.worldmates.messenger.ui.theme.WorldMatesThemedApp
import com.worldmates.messenger.ui.settings.getSavedCallFrameStyle
import kotlinx.coroutines.delay
import org.json.JSONObject
import org.webrtc.EglBase
import org.webrtc.MediaStream
import org.webrtc.SurfaceViewRenderer
import kotlin.math.abs

/**
 * 🎨 Стилі кастомних рамок для відеодзвінків
 */
enum class CallFrameStyle {
    CLASSIC,    // Класична рамка з легкою тінню
    NEON,       // Неонова рамка з пульсуючим світінням
    GRADIENT,   // Градієнтна рамка з кольоровим переходом
    MINIMAL,    // Мінімалістична без рамки
    GLASS,      // Скляний ефект з blur
    RAINBOW     // Веселкова анімована рамка
}

class CallsActivity : ComponentActivity() {

    private lateinit var callsViewModel: CallsViewModel
    private var shouldInitiateCall = false
    private var callInitiated = false
    private var isIncomingCall = false  // ✅ Додано для відстеження вхідних дзвінків

    // 📋 Параметри дзвінка з Intent
    private var recipientId: Long = 0
    private var recipientName: String = ""
    private var recipientAvatar: String = ""
    private var callType: String = "audio"  // "audio" або "video"
    private var isGroup: Boolean = false
    private var groupId: Long = 0

    // Screen sharing & recording managers
    private var screenSharingManager: ScreenSharingManager? = null
    private var callRecordingManager: CallRecordingManager? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val audioGranted = permissions.getOrDefault(Manifest.permission.RECORD_AUDIO, false)
            val cameraGranted = permissions.getOrDefault(Manifest.permission.CAMERA, false)

            if (audioGranted && (callType == "audio" || cameraGranted)) {
                // ✅ Дозволи отримано - ініціюємо дзвінок
                if (shouldInitiateCall && !callInitiated) {
                    initiateCall()
                }
            } else {
                // ❌ Дозволи не надано
                Toast.makeText(
                    this,
                    "Для дзвінків потрібні дозволи на мікрофон" + if (callType == "video") " та камеру" else "",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Инициализируем ThemeManager
        ThemeManager.initialize(this)

        callsViewModel = ViewModelProvider(this).get(CallsViewModel::class.java)

        // Initialize managers
        callRecordingManager = CallRecordingManager(this)

        // 📥 Отримати параметри з Intent
        // Перевіряємо чи це вхідний дзвінок
        isIncomingCall = intent.getBooleanExtra("is_incoming", false)

        if (isIncomingCall) {
            // ✅ Вхідний дзвінок - отримуємо дані від IncomingCallActivity
            recipientId = intent.getIntExtra("from_id", 0).toLong()
            recipientName = intent.getStringExtra("from_name") ?: "Користувач"
            recipientAvatar = intent.getStringExtra("from_avatar") ?: ""
            callType = intent.getStringExtra("call_type") ?: "audio"
            val roomName = intent.getStringExtra("room_name") ?: ""
            val sdpOffer = intent.getStringExtra("sdp_offer")
            shouldInitiateCall = false  // ✅ НЕ ініціюємо дзвінок

            android.util.Log.d("CallsActivity", "✅ Incoming call from: $recipientName (ID: $recipientId), room: $roomName")

            // ✅ КРИТИЧНО: Прийняти дзвінок через ViewModel
            // ViewModel виконає: отримання ICE, створення PeerConnection, answer, відправку call:accept
            val callData = CallData(
                callId = 0,
                fromId = recipientId.toInt(),  // ID того, хто дзвонить (інініатор)
                fromName = recipientName,
                fromAvatar = recipientAvatar,
                toId = callsViewModel.getUserId(),  // ✅ ID поточного користувача (отримувач)
                callType = callType,
                roomName = roomName,
                sdpOffer = sdpOffer
            )

            // Викликаємо acceptCall() - ViewModel зачекає на Socket і виконає все необхідне
            callsViewModel.acceptCall(callData)
        } else {
            // ✅ Вихідний дзвінок - ініціюємо звонок
            recipientId = intent.getLongExtra("recipientId", 0)
            recipientName = intent.getStringExtra("recipientName") ?: "Користувач"
            recipientAvatar = intent.getStringExtra("recipientAvatar") ?: ""
            callType = intent.getStringExtra("callType") ?: "audio"
            isGroup = intent.getBooleanExtra("isGroup", false)
            groupId = intent.getLongExtra("groupId", 0)

            // Якщо є recipientId або groupId - потрібно ініціювати дзвінок
            shouldInitiateCall = (recipientId > 0 || groupId > 0)

            android.util.Log.d("CallsActivity", "✅ Outgoing call to: $recipientName (ID: $recipientId)")
        }

        // Налаштувати Socket.IO listeners
        setupSocketListeners()

        // Запросити дозволи
        requestPermissions()

        setContent {
            WorldMatesThemedApp {
                CallsScreen(
                    callsViewModel,
                    this,
                    isInitiating = shouldInitiateCall && !callInitiated,
                    isIncoming = isIncomingCall,  // ✅ Передаємо флаг вхідного дзвінка
                    calleeName = recipientName,
                    calleeAvatar = recipientAvatar,
                    callType = callType
                )
            }
        }

        // Обробити завершення дзвінка
        callsViewModel.callEnded.observe(this) { ended ->
            if (ended == true) {
                finish()
            }
        }
    }

    /**
     * 📞 Ініціювати дзвінок
     */
    private fun initiateCall() {
        callInitiated = true
        Log.d("CallsActivity", "Ініціація дзвінка: recipientId=$recipientId, type=$callType, isGroup=$isGroup")

        if (isGroup && groupId > 0) {
            // Груповий дзвінок
            callsViewModel.initiateGroupCall(
                groupId = groupId.toInt(),
                groupName = recipientName,
                callType = callType
            )
        } else if (recipientId > 0) {
            // Особистий дзвінок
            callsViewModel.initiateCall(
                recipientId = recipientId.toInt(),
                recipientName = recipientName,
                recipientAvatar = recipientAvatar,
                callType = callType
            )
        }
    }

    /**
     * 🔌 Налаштувати Socket.IO listeners для вхідних подій
     */
    private fun setupSocketListeners() {
        Log.d("CallsActivity", "Налаштування Socket.IO listeners для дзвінків...")

        // 📞 Вхідний дзвінок
        callsViewModel.socketManager.on("call:incoming") { args ->
            if (args.isNotEmpty()) {
                (args[0] as? JSONObject)?.let { data ->
                    Log.d("CallsActivity", "📞 Вхідний дзвінок: ${data.optInt("fromId")}")
                    // Передаємо нативний JSONObject прямо у ViewModel
                    callsViewModel.onIncomingCall(data)
                }
            }
        }

// ✅ Відповідь на дзвінок
        callsViewModel.socketManager.on("call:answer") { args ->
            if (args.isNotEmpty()) {
                (args[0] as? JSONObject)?.let { data ->
                    callsViewModel.onCallAnswer(data)
                }
            }
        }

// 🧊 ICE candidate
        callsViewModel.socketManager.on("ice:candidate") { args ->
            if (args.isNotEmpty()) {
                (args[0] as? JSONObject)?.let { data ->
                    callsViewModel.onIceCandidate(data)
                }
            }
        }

// ❌ Завершення дзвінка
        callsViewModel.socketManager.on("call:end") {
            Log.d("CallsActivity", "❌ Завершення дзвінка")
            callsViewModel.endCall()
        }

        // 🚫 Відхилення дзвінка
        callsViewModel.socketManager.on("call:reject") {
            Log.d("CallsActivity", "🚫 Відхилено")
            callsViewModel.endCall()
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        requestPermissionLauncher.launch(permissions.toTypedArray())
    }

    /**
     * Auto-enter PiP when user presses home (during active video call)
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (callType == "video" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(android.util.Rational(9, 16))
                    .build()
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                Log.e("CallsActivity", "Failed to enter PiP mode", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        screenSharingManager?.release()
        callRecordingManager?.release()
    }
}

/**
 * Основний екран дзвінків
 */
@Composable
fun CallsScreen(
    viewModel: CallsViewModel,
    activity: CallsActivity,
    isInitiating: Boolean = false,
    isIncoming: Boolean = false,  // ✅ Додано параметр для вхідних дзвінків
    calleeName: String = "",
    calleeAvatar: String = "",
    callType: String = "audio"
) {
    val incomingCall by viewModel.incomingCall.observeAsState()
    val callConnected by viewModel.callConnected.observeAsState(false)
    val callEnded by viewModel.callEnded.observeAsState(false)
    val remoteStream by viewModel.remoteStreamAdded.observeAsState()
    val connectionState by viewModel.connectionState.observeAsState("IDLE")
    val callError by viewModel.callError.observeAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1a1a1a))
    ) {
        when {
            incomingCall != null && !callConnected && !isIncoming -> {
                // 📞 Вхідний дзвінок (тільки якщо НЕ прийнято через IncomingCallActivity)
                IncomingCallScreen(incomingCall!!, viewModel)
            }
            isInitiating || isIncoming || connectionState != "IDLE" || callConnected -> {
                // ✅ Активний дзвінок (показуємо для вихідних, вхідних прийнятих, та підключених)
                ActiveCallScreen(
                    viewModel = viewModel,
                    remoteStream = remoteStream,
                    connectionState = connectionState ?: if (isIncoming) "ACCEPTING" else "CONNECTING",
                    calleeName = calleeName,
                    calleeAvatar = calleeAvatar,
                    callType = callType  // ✅ Передаємо callType
                )
            }
            callError != null -> {
                // ❌ Помилка дзвінка
                ErrorScreen(callError!!, viewModel)
            }
            else -> {
                // ⏸️ Очікування
                IdleScreen(viewModel)
            }
        }
    }
}

/**
 * Екран очікування вхідного дзвінка
 */
@Composable
fun IncomingCallScreen(callData: CallData, viewModel: CallsViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0d0d0d)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Аватар що телефонує
        if (callData.fromAvatar.isNotEmpty()) {
            AsyncImage(
                model = callData.fromAvatar,
                contentDescription = callData.fromName,
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                tint = Color(0xFF888888)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Ім'я того, хто дзвонить
        Text(
            text = callData.fromName,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Тип дзвінка
        Text(
            text = if (callData.callType == "video") "📹 Вхідний відеодзвінок" else "📞 Вхідний аудіодзвінок",
            fontSize = 16.sp,
            color = Color(0xFFbbbbbb),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(60.dp))

        // Кнопки прийняття/відхилення
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Кнопка відхилення
            IconButton(
                onClick = { viewModel.rejectCall(callData.roomName) },
                modifier = Modifier
                    .size(64.dp)
                    .background(Color(0xFFd32f2f), CircleShape)
                    .clip(CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "Reject",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Кнопка прийняття
            IconButton(
                onClick = { viewModel.acceptCall(callData) },
                modifier = Modifier
                    .size(64.dp)
                    .background(Color(0xFF4caf50), CircleShape)
                    .clip(CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Accept",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

/**
 * 📤 Екран вихідного дзвінка (дзвонимо...)
 */
@Composable
fun OutgoingCallScreen(
    calleeName: String,
    calleeAvatar: String,
    callType: String,
    viewModel: CallsViewModel
) {
    // Анімація пульсації
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0d0d0d)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Аватар
        if (calleeAvatar.isNotEmpty()) {
            AsyncImage(
                model = calleeAvatar,
                contentDescription = calleeName,
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                tint = Color(0xFF888888)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Ім'я
        Text(
            text = calleeName,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Статус з анімацією
        Text(
            text = if (callType == "video") "📹 Відеодзвінок..." else "📞 Дзвонимо...",
            fontSize = 16.sp,
            color = Color(0xFFbbbbbb).copy(alpha = alpha),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(60.dp))

        // Кнопка скасування
        IconButton(
            onClick = { viewModel.endCall() },
            modifier = Modifier
                .size(64.dp)
                .background(Color(0xFFd32f2f), CircleShape)
                .clip(CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.CallEnd,
                contentDescription = "Cancel",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

/**
 * 📞 Екран активного дзвінка з кастомними рамками
 */
@Composable
fun ActiveCallScreen(
    viewModel: CallsViewModel,
    remoteStream: MediaStream?,
    connectionState: String,
    calleeName: String = "Користувач",
    calleeAvatar: String = "",
    callType: String = "audio"  // ✅ Додано параметр callType
) {
    val context = LocalContext.current
    var audioEnabled by remember { mutableStateOf(true) }
    var videoEnabled by remember { mutableStateOf(callType == "video") }  // ✅ Ініціалізувати з callType
    var speakerEnabled by remember { mutableStateOf(false) }
    var showReactions by remember { mutableStateOf(false) }
    var showChatOverlay by remember { mutableStateOf(false) }
    var showQualitySelector by remember { mutableStateOf(false) }  // 📹 Селектор якості
    var callDuration by remember { mutableStateOf(0) }
    var isScreenSharing by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var noiseCancellation by remember { mutableStateOf(true) }

    // 📹 Поточна якість відео
    var currentVideoQuality by remember {
        mutableStateOf(viewModel.getVideoQuality())
    }

    // 🎨 Завантажити збережений стиль рамки з Settings
    var currentFrameStyle by remember {
        mutableStateOf(getSavedCallFrameStyle(context))
    }
    val localStream by viewModel.localStreamAdded.observeAsState()

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            callDuration++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
    ) {
        // Віддалена відео/аудіо потік
        if (remoteStream?.videoTracks?.isNotEmpty() == true) {
            // 🎥 Показати відео з кастомними рамками
            RemoteVideoView(
                remoteStream = remoteStream,
                localStream = null,  // Не показуємо тут, покажемо окремо
                frameStyle = currentFrameStyle,
                onSwitchCamera = { viewModel.switchCamera() }
            )
        } else {
            // Показати аватар/ім'я співрозмовника (під час аудіо дзвінка або поки немає відео)
            Column(
                modifier = Modifier
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (calleeAvatar.isNotEmpty()) {
                    AsyncImage(
                        model = calleeAvatar,
                        contentDescription = calleeName,
                        modifier = Modifier
                            .size(140.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(140.dp),
                        tint = Color(0xFF666666)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = calleeName,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // 📱 Локальне відео (PiP) - показуємо ЗАВЖДИ якщо є localStream
        localStream?.let { stream ->
            if (stream.videoTracks.isNotEmpty()) {
                var pipOffset by remember { mutableStateOf(Offset(0f, 0f)) }
                LocalVideoPiP(
                    localStream = stream,
                    offset = pipOffset,
                    onOffsetChange = { newOffset ->
                        pipOffset = newOffset
                    },
                    viewModel = viewModel,
                    onSwitchCamera = { viewModel.switchCamera() },
                )
            }
        }

        // Топ: інформація про дзвінок + перемикач стилів
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Enhanced status bar
            EnhancedConnectionStatus(
                connectionState = connectionState,
                callDuration = callDuration,
                calleeName = calleeName,
                isRecording = isRecording,
                isScreenSharing = isScreenSharing
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Noise cancellation indicator
            NoiseCancellationIndicator(isEnabled = noiseCancellation)

            // Recording banner
            if (isRecording) {
                Spacer(modifier = Modifier.height(8.dp))
                RecordingNotificationBanner(
                    recordedBy = "Ви",
                    onStop = { isRecording = false },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // Screen sharing banner
            if (isScreenSharing) {
                Spacer(modifier = Modifier.height(8.dp))
                ScreenSharingBanner(
                    onStop = { isScreenSharing = false },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 🎨 Перемикач стилів рамок (тільки для відеодзвінків)
            if (remoteStream?.videoTracks?.isNotEmpty() == true) {
                FrameStyleSelector(
                    currentStyle = currentFrameStyle,
                    onStyleChange = { currentFrameStyle = it }
                )
            }
        }

        // Enhanced control bar with glassmorphism
        EnhancedCallControlBar(
            audioEnabled = audioEnabled,
            videoEnabled = videoEnabled,
            speakerEnabled = speakerEnabled,
            isScreenSharing = isScreenSharing,
            isRecording = isRecording,
            noiseCancellation = noiseCancellation,
            onToggleAudio = {
                audioEnabled = !audioEnabled
                viewModel.toggleAudio(audioEnabled)
            },
            onToggleVideo = {
                videoEnabled = !videoEnabled
                viewModel.toggleVideo(videoEnabled)
            },
            onToggleSpeaker = {
                speakerEnabled = !speakerEnabled
                viewModel.toggleSpeaker(speakerEnabled)
            },
            onSwitchCamera = { viewModel.switchCamera() },
            onToggleScreenShare = { isScreenSharing = !isScreenSharing },
            onToggleRecording = { isRecording = !isRecording },
            onToggleNoiseCancellation = { noiseCancellation = !noiseCancellation },
            onEndCall = { viewModel.endCall() },
            onPiP = { enterPiPMode(context) },
            onShowReactions = { showReactions = !showReactions },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // 🎭 Reactions Overlay - рендериться ПОВЕРХ усього
        if (showReactions) {
            ReactionsOverlay(
                onReactionSelected = { reaction ->
                    // TODO: Send reaction through Socket.IO
                    showReactions = false
                },
                onDismiss = { showReactions = false }
            )
        }

        // 💬 Chat Overlay during call - рендериться ПОВЕРХ усього
        if (showChatOverlay) {
            ChatDuringCallOverlay(
                onDismiss = { showChatOverlay = false }
            )
        }

        // 📹 Video Quality Selector Overlay
        if (showQualitySelector) {
            VideoQualitySelector(
                currentQuality = currentVideoQuality,
                onQualitySelected = { quality ->
                    if (viewModel.setVideoQuality(quality)) {
                        currentVideoQuality = quality
                    }
                    showQualitySelector = false
                },
                onDismiss = { showQualitySelector = false }
            )
        }
    }
}

/**
 * 🎨 Selector для вибору стилю рамки
 */
@Composable
fun FrameStyleSelector(
    currentStyle: CallFrameStyle,
    onStyleChange: (CallFrameStyle) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .background(Color(0x99000000), RoundedCornerShape(8.dp))
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Style,
                contentDescription = "Frame Style",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = currentStyle.name,
                fontSize = 12.sp,
                color = Color.White
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            CallFrameStyle.values().forEach { style ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val emoji = when (style) {
                                CallFrameStyle.CLASSIC -> "🎨"
                                CallFrameStyle.NEON -> "💡"
                                CallFrameStyle.GRADIENT -> "🌈"
                                CallFrameStyle.MINIMAL -> "⚪"
                                CallFrameStyle.GLASS -> "💎"
                                CallFrameStyle.RAINBOW -> "🌈"
                            }
                            Text("$emoji ${style.name}")
                        }
                    },
                    onClick = {
                        onStyleChange(style)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Компонент для кнопки управління дзвінком
 */
@Composable
fun CallControlButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }

    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(backgroundColor, CircleShape)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.White,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 📹 Компонент для відображення віддаленої відео потоку з кастомними рамками
 */
@Composable
fun RemoteVideoView(
    remoteStream: MediaStream,
    localStream: MediaStream? = null,
    frameStyle: CallFrameStyle = CallFrameStyle.CLASSIC,
    onSwitchCamera: () -> Unit = {}
) {
    var isFullscreen by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        // Double tap → fullscreen toggle
                        isFullscreen = !isFullscreen
                    }
                )
            }
    ) {
        // 🎥 Віддалене відео з кастомною рамкою
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isFullscreen) 0.dp else 16.dp)
        ) {
            // Застосовуємо стиль рамки
            when (frameStyle) {
                CallFrameStyle.CLASSIC -> ClassicVideoFrame(remoteStream)
                CallFrameStyle.NEON -> NeonVideoFrame(remoteStream)
                CallFrameStyle.GRADIENT -> GradientVideoFrame(remoteStream)
                CallFrameStyle.MINIMAL -> MinimalVideoFrame(remoteStream)
                CallFrameStyle.GLASS -> GlassVideoFrame(remoteStream)
                CallFrameStyle.RAINBOW -> RainbowVideoFrame(remoteStream)
            }
        }

        // Локальне відео тепер показується окремо в ActiveCallScreen
    }
}

/**
 * 📱 Picture-in-Picture для локального відео (draggable + swipe to switch camera)
 */
@Composable
fun LocalVideoPiP(
    localStream: MediaStream,
    offset: Offset,
    onOffsetChange: (Offset) -> Unit,
    viewModel: CallsViewModel,
    onSwitchCamera: () -> Unit = {}
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragStartOffset by remember { mutableStateOf(Offset.Zero) }

    // ✅ Запам'ятовуємо поточний відео трек для відстеження змін
    var currentVideoTrack by remember { mutableStateOf<org.webrtc.VideoTrack?>(null) }

    Box(
        modifier = Modifier
            .offset(x = offset.x.dp, y = offset.y.dp)
            .padding(16.dp)
            .width(120.dp)
            .height(160.dp)
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1a1a1a))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { dragOffset ->
                        isDragging = true
                        dragStartOffset = dragOffset
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()

                        // Перевірка на swipe (горизонтальний рух більше 100px)
                        val totalDragX = change.position.x - dragStartOffset.x
                        if (abs(totalDragX) > 100f && abs(dragAmount.y) < 50f) {
                            // Swipe left/right → switch camera
                            onSwitchCamera()
                            viewModel.switchCamera()
                            isDragging = false
                        } else {
                            // Normal drag → move PiP
                            onOffsetChange(
                                Offset(
                                    x = (offset.x + dragAmount.x).coerceIn(0f, 800f),
                                    y = (offset.y + dragAmount.y).coerceIn(0f, 1400f)
                                )
                            )
                        }
                    },
                    onDragEnd = {
                        isDragging = false
                    }
                )
            }
    ) {
        // Локальне відео
        AndroidView(
            factory = { androidContext ->
                SurfaceViewRenderer(androidContext).apply {
                    // ✅ КРИТИЧНО: Ініціалізувати SurfaceViewRenderer з EGL контекстом
                    init(WebRTCManager.getEglContext(), null)
                    setZOrderMediaOverlay(true)
                    setEnableHardwareScaler(true)
                    setMirror(true)  // ✅ Дзеркальне відображення для фронтальної камери
                }
            },
            update = { surfaceViewRenderer ->
                // ✅ КРИТИЧНО: Оновлювати sink при зміні stream або відео треку
                val newVideoTrack = if (localStream.videoTracks.isNotEmpty()) {
                    localStream.videoTracks[0]
                } else null

                if (newVideoTrack != currentVideoTrack) {
                    // Видалити старий sink
                    currentVideoTrack?.removeSink(surfaceViewRenderer)
                    // Додати новий sink
                    newVideoTrack?.addSink(surfaceViewRenderer)
                    currentVideoTrack = newVideoTrack
                    Log.d("LocalVideoPiP", "✅ Video track updated: ${newVideoTrack != null}")
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Індикатор перемикання камери
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(32.dp)
                .background(Color(0x99000000), CircleShape)
                .clickable {
                    // TODO: Switch camera
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Cameraswitch,
                contentDescription = "Switch Camera",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        // Рамка при перетягуванні
        if (isDragging) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = Color(0xFF2196F3).copy(alpha = 0.3f),
                        shape = RoundedCornerShape(16.dp)
                    )
            )
        }
    }
}

/**
 * 🎥 Універсальний компонент для рендерингу WebRTC відео
 * Правильно обробляє оновлення відео треку
 *
 * ✅ ВИПРАВЛЕНО: Використовує key для перестворення при зміні stream
 */
@Composable
fun WebRTCVideoRenderer(
    videoStream: MediaStream,
    modifier: Modifier = Modifier,
    isMirrored: Boolean = false,
    isOverlay: Boolean = false
) {
    // ✅ Використовуємо label stream як key для перестворення
    val streamId = remember(videoStream) { videoStream.id ?: "${System.currentTimeMillis()}" }

    // Запам'ятовуємо поточний відео трек для відстеження змін
    var currentVideoTrack by remember(streamId) { mutableStateOf<org.webrtc.VideoTrack?>(null) }

    // ✅ Отримуємо актуальний відео трек
    val newVideoTrack = remember(videoStream) {
        if (videoStream.videoTracks.isNotEmpty()) videoStream.videoTracks[0] else null
    }

    Log.d("WebRTCVideoRenderer", "📹 Rendering stream: $streamId, hasVideo: ${newVideoTrack != null}")

    // ✅ DisposableEffect для cleanup при unmount
    DisposableEffect(streamId) {
        onDispose {
            Log.d("WebRTCVideoRenderer", "📹 Disposing renderer for stream: $streamId")
        }
    }

    AndroidView(
        factory = { androidContext ->
            Log.d("WebRTCVideoRenderer", "📹 Creating SurfaceViewRenderer for stream: $streamId")
            SurfaceViewRenderer(androidContext).apply {
                try {
                    init(WebRTCManager.getEglContext(), null)
                    setZOrderMediaOverlay(isOverlay)
                    setEnableHardwareScaler(true)
                    setMirror(isMirrored)

                    // ✅ КРИТИЧНО: Додати sink одразу при створенні
                    newVideoTrack?.let { track ->
                        track.addSink(this)
                        currentVideoTrack = track
                        Log.d("WebRTCVideoRenderer", "📹 Initial sink added for track: ${track.id()}")
                    }
                } catch (e: Exception) {
                    Log.e("WebRTCVideoRenderer", "Error initializing SurfaceViewRenderer", e)
                }
            }
        },
        update = { surfaceViewRenderer ->
            // ✅ КРИТИЧНО: Оновлювати sink при зміні відео треку
            val latestVideoTrack = if (videoStream.videoTracks.isNotEmpty()) {
                videoStream.videoTracks[0]
            } else null

            if (latestVideoTrack != currentVideoTrack) {
                Log.d("WebRTCVideoRenderer", "📹 Track changed: old=${currentVideoTrack?.id()}, new=${latestVideoTrack?.id()}")
                // Видалити старий sink
                try {
                    currentVideoTrack?.removeSink(surfaceViewRenderer)
                } catch (e: Exception) {
                    Log.w("WebRTCVideoRenderer", "Error removing old sink: ${e.message}")
                }
                // Додати новий sink
                try {
                    latestVideoTrack?.addSink(surfaceViewRenderer)
                    currentVideoTrack = latestVideoTrack
                    Log.d("WebRTCVideoRenderer", "✅ Video track updated: ${latestVideoTrack != null}, id: ${latestVideoTrack?.id()}")
                } catch (e: Exception) {
                    Log.e("WebRTCVideoRenderer", "Error adding new sink: ${e.message}")
                }
            }
        },
        modifier = modifier
    )
}

/**
 * 🎨 CLASSIC: Класична рамка з легкою тінню
 */
@Composable
fun ClassicVideoFrame(remoteStream: MediaStream) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .shadow(8.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black)
    ) {
        WebRTCVideoRenderer(
            videoStream = remoteStream,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * 💡 NEON: Неонова рамка з пульсуючим світінням
 */
@Composable
fun NeonVideoFrame(remoteStream: MediaStream) {
    var animatedAlpha by remember { mutableStateOf(1f) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            animatedAlpha = if (animatedAlpha == 1f) 0.5f else 1f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                color = Color(0xFF00ffff).copy(alpha = animatedAlpha * 0.3f),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black)
    ) {
        WebRTCVideoRenderer(
            videoStream = remoteStream,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * 🌈 GRADIENT: Градієнтна рамка з кольоровим переходом
 */
@Composable
fun GradientVideoFrame(remoteStream: MediaStream) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF667eea),
                        Color(0xFF764ba2),
                        Color(0xFFf093fb)
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black)
    ) {
        WebRTCVideoRenderer(
            videoStream = remoteStream,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * ⚪ MINIMAL: Мінімалістична без рамки
 */
@Composable
fun MinimalVideoFrame(remoteStream: MediaStream) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        WebRTCVideoRenderer(
            videoStream = remoteStream,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * 💎 GLASS: Скляний ефект з blur
 */
@Composable
fun GlassVideoFrame(remoteStream: MediaStream) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                color = Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(2.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.Black)
    ) {
        WebRTCVideoRenderer(
            videoStream = remoteStream,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * 🌈 RAINBOW: Веселкова анімована рамка
 */
@Composable
fun RainbowVideoFrame(remoteStream: MediaStream) {
    var offsetX by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(50)
            offsetX = (offsetX + 10f) % 360f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFff0000),
                        Color(0xFFff7f00),
                        Color(0xFFffff00),
                        Color(0xFF00ff00),
                        Color(0xFF0000ff),
                        Color(0xFF4b0082),
                        Color(0xFF9400d3)
                    ),
                    start = Offset(offsetX, 0f),
                    end = Offset(offsetX + 1000f, 1000f)
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black)
    ) {
        WebRTCVideoRenderer(
            videoStream = remoteStream,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * 📹 Video Quality Selector Overlay
 */
@Composable
fun VideoQualitySelector(
    currentQuality: com.worldmates.messenger.network.VideoQuality,
    onQualitySelected: (com.worldmates.messenger.network.VideoQuality) -> Unit,
    onDismiss: () -> Unit
) {
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
                .clickable(enabled = false) { /* Prevent click through */ },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a1a))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "📹 Якість відео",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                com.worldmates.messenger.network.VideoQuality.entries.forEach { quality ->
                    val isSelected = quality == currentQuality
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) Color(0xFF4CAF50).copy(alpha = 0.3f)
                                else Color.Transparent
                            )
                            .clickable { onQualitySelected(quality) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.Circle,
                            contentDescription = null,
                            tint = if (isSelected) Color(0xFF4CAF50) else Color.Gray,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = quality.label,
                                fontSize = 16.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else Color(0xFFbbbbbb)
                            )
                            Text(
                                text = "${quality.width}x${quality.height} @ ${quality.fps}fps • ${quality.maxBitrate/1000.0}Mbps",
                                fontSize = 12.sp,
                                color = Color(0xFF888888)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "💡 Виберіть нижчу якість для повільного інтернету",
                    fontSize = 12.sp,
                    color = Color(0xFF888888),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * 🎭 Reactions Overlay - анімовані емоції як на YouTube
 */
@Composable
fun ReactionsOverlay(
    onReactionSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val reactions = listOf(
        "❤️" to "Сердечко",
        "👍" to "Лайк",
        "😂" to "Сміх",
        "😮" to "Wow",
        "😢" to "Сумно",
        "🔥" to "Вогонь",
        "👏" to "Аплодисменти",
        "🎉" to "Святкування"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .pointerInput(Unit) {
                detectTapGestures {
                    onDismiss()
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Color(0xFF2a2a2a),
                    RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
                .padding(24.dp)
                .align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Виберіть реакцію",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Grid з реакціями
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                reactions.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row.forEach { (emoji, label) ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .size(70.dp)
                                    .clickable {
                                        onReactionSelected(emoji)
                                    }
                            ) {
                                Text(
                                    text = emoji,
                                    fontSize = 40.sp
                                )
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    color = Color(0xFFaaaaaa)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 💬 Chat Overlay - можливість писати під час дзвінка
 */
@Composable
fun ChatDuringCallOverlay(
    onDismiss: () -> Unit
) {
    var messageText by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // Затемнений фон
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .pointerInput(Unit) {
                    detectTapGestures {
                        onDismiss()
                    }
                }
        )

        // Chat UI
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
                .background(
                    Color(0xFF1a1a1a),
                    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                )
                .align(Alignment.BottomCenter)
                .pointerInput(Unit) {
                    // Prevent click through
                    detectTapGestures { }
                }
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "💬 Чат під час дзвінка",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }

            // Messages area (placeholder)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "Повідомлення з'являться тут...",
                    color = Color(0xFF666666),
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // Input area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2a2a2a))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = {
                        Text("Напишіть повідомлення...", color = Color(0xFF666666))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF333333), RoundedCornerShape(24.dp)),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (messageText.isNotEmpty()) {
                            // TODO: Send message
                            messageText = ""
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = Color(0xFF2196F3)
                    )
                }
            }
        }
    }
}

/**
 * Екран помилки
 */
@Composable
fun ErrorScreen(error: String, viewModel: CallsViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1a1a1a)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = "Error",
            modifier = Modifier.size(64.dp),
            tint = Color(0xFFd32f2f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Помилка дзвінка",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = error,
            fontSize = 14.sp,
            color = Color(0xFFbbbbbb),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { viewModel.endCall() },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFd32f2f))
        ) {
            Text("Закрити")
        }
    }
}

/**
 * Екран без активного дзвінка
 */
@Composable
fun IdleScreen(viewModel: CallsViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1a1a1a)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Phone,
            contentDescription = "Calls",
            modifier = Modifier.size(64.dp),
            tint = Color(0xFF2196F3)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Немає активних дзвінків",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

/**
 * Вспомогательні функції
 */
fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%02d:%02d", minutes, secs)
    }
}

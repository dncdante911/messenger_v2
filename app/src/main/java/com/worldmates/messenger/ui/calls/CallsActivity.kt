package com.worldmates.messenger.ui.calls

import android.Manifest
import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.json.JSONObject
import org.webrtc.EglBase
import org.webrtc.MediaStream
import org.webrtc.SurfaceViewRenderer
import kotlin.math.abs
import androidx.compose.ui.res.stringResource
import com.worldmates.messenger.R
import com.worldmates.messenger.utils.LanguageManager

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
    lateinit var screenSharingIntegration: ScreenSharingIntegration

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

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

        override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Инициализируем ThemeManager
        ThemeManager.initialize(this)

        callsViewModel = ViewModelProvider(this).get(CallsViewModel::class.java)

        // Initialize screen sharing integration (must be before setContent)
        screenSharingIntegration = ScreenSharingIntegration(this, callsViewModel)
        screenSharingIntegration.register()

        // Initialize managers
        callRecordingManager = CallRecordingManager(this)

        // 📥 Отримати параметри з Intent
        // Перевіряємо чи це вхідний дзвінок
        isIncomingCall = intent.getBooleanExtra("is_incoming", false)

        if (isIncomingCall) {
            // ✅ Вхідний дзвінок - отримуємо дані від IncomingCallActivity
            recipientId = intent.getIntExtra("from_id", 0).toLong()
            recipientName = intent.getStringExtra("from_name") ?: getString(R.string.user_label)
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
            recipientId = intent.getLongExtra("recipientId", 0)
            recipientName = intent.getStringExtra("recipientName") ?: getString(R.string.user_label)
            recipientAvatar = intent.getStringExtra("recipientAvatar") ?: ""
            callType = intent.getStringExtra("callType") ?: "audio"
            isGroup = intent.getBooleanExtra("isGroup", false)
            groupId = intent.getLongExtra("groupId", 0)

            val isIncomingGroup = intent.getBooleanExtra("is_incoming_group", false)

            if (isIncomingGroup && isGroup) {
                // Входящий групповой звонок — нужно присоединиться к существующей комнате
                val groupRoomName = intent.getStringExtra("group_room_name") ?: ""
                val groupInitiatedBy = intent.getIntExtra("group_initiated_by", 0)
                val groupMaxParticipants = intent.getIntExtra("group_max_participants", 5)

                android.util.Log.d("CallsActivity", "✅ Joining incoming group call: room=$groupRoomName, group=$groupId")

                val groupCallData = GroupCallIncomingData(
                    groupId = groupId.toInt(),
                    groupName = recipientName,
                    initiatedBy = groupInitiatedBy,
                    initiatorName = intent.getStringExtra("group_initiator_name") ?: getString(R.string.unknown),
                    initiatorAvatar = "",
                    callType = callType,
                    roomName = groupRoomName,
                    maxParticipants = groupMaxParticipants,
                    isPremiumCall = groupMaxParticipants > 5
                )

                // Присоединиться к звонку через ViewModel
                callsViewModel.joinGroupCall(groupCallData)
                shouldInitiateCall = false
            } else {
                // Якщо є recipientId або groupId - потрібно ініціювати дзвінок
                shouldInitiateCall = (recipientId > 0 || groupId > 0)
            }

            android.util.Log.d("CallsActivity", "✅ Outgoing call to: $recipientName (ID: $recipientId), isGroup=$isGroup")
        }

        // Налаштувати Socket.IO listeners
        setupSocketListeners()

        // Запросити дозволи
        requestPermissions()

        setContent {
            WorldMatesThemedApp {
                if (isGroup) {
                    GroupCallScreen(
                        viewModel = callsViewModel,
                        groupName = recipientName,
                        groupId = groupId.toInt(),
                        callType = callType,
                        onCallEnd = { finish() }
                    )
                } else {
                    CallsScreen(
                        callsViewModel,
                        this,
                        isInitiating = shouldInitiateCall && !callInitiated,
                        isIncoming = isIncomingCall,
                        calleeName = recipientName,
                        calleeAvatar = recipientAvatar,
                        callType = callType,
                        calleeId = recipientId  // ← передаємо ID для call transfer
                    )
                }
            }
        }

        // Обробити завершення дзвінка (1-на-1)
        callsViewModel.callEnded.observe(this) { ended ->
            if (ended == true) {
                finish()
            }
        }

        // Обробити завершення групового дзвінка
        callsViewModel.groupCallEnded.observe(this) { ended ->
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
        if (::screenSharingIntegration.isInitialized) screenSharingIntegration.release()
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
    callType: String = "audio",
    calleeId: Long = 0L           // ID співрозмовника — потрібен для call transfer
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
                    callType = callType,
                    calleeId = calleeId
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
 * 📞 Екран активного дзвінка — чистий сучасний дизайн
 */
@Composable
fun ActiveCallScreen(
    viewModel: CallsViewModel,
    remoteStream: MediaStream?,
    connectionState: String,
    calleeName: String = "",
    calleeAvatar: String = "",
    callType: String = "audio",
    calleeId: Long = 0L  // kept for API compat
) {
    val context = LocalContext.current
    var audioEnabled by remember { mutableStateOf(true) }
    var videoEnabled by remember { mutableStateOf(callType == "video") }
    var speakerEnabled by remember { mutableStateOf(false) }
    var showReactions by remember { mutableStateOf(false) }
    var showChatOverlay by remember { mutableStateOf(false) }
    var showQualitySelector by remember { mutableStateOf(false) }
    var callDuration by remember { mutableStateOf(0) }
    var isScreenSharing by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var noiseCancellation by remember { mutableStateOf(true) }

    var showFiltersPanel by remember { mutableStateOf(false) }
    val activeFilter by viewModel.activeVideoFilter.collectAsState()
    var filterIntensity by remember { mutableStateOf(1.0f) }

    var showBgPanel by remember { mutableStateOf(false) }
    val activeVirtualBg by viewModel.activeVirtualBg.collectAsState()

    var currentVideoQuality by remember { mutableStateOf(viewModel.getVideoQuality()) }
    val localStream by viewModel.localStreamAdded.observeAsState()

    val hasRemoteVideo = remoteStream?.videoTracks?.isNotEmpty() == true
    // Show the connecting screen until we have a real connection or video
    val isConnected = connectionState == "CONNECTED" || hasRemoteVideo

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            callDuration++
        }
    }

    var uiVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lastInteractionTime) {
        delay(5_000L)
        uiVisible = false
    }

    val adaptiveQuality by viewModel.adaptiveVideoQuality.collectAsState()
    LaunchedEffect(adaptiveQuality) {
        currentVideoQuality = adaptiveQuality
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitPointerEvent()
                    uiVisible = true
                    lastInteractionTime = System.currentTimeMillis()
                }
            }
    ) {
        if (!isConnected) {
            // ── Connecting / dialing state ──────────────────────────────────
            EnhancedOutgoingCallScreen(
                calleeName = calleeName,
                calleeAvatar = calleeAvatar,
                callType = callType,
                onCancel = { viewModel.endCall() }
            )
        } else {
            // ── Active call ─────────────────────────────────────────────────

            // Background: remote video or audio call gradient
            if (hasRemoteVideo) {
                WebRTCVideoRenderer(
                    videoStream = remoteStream!!,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AudioCallBackground(calleeName = calleeName, calleeAvatar = calleeAvatar)
            }

            // Local camera PiP
            localStream?.let { stream ->
                if (stream.videoTracks.isNotEmpty()) {
                    var pipOffset by remember { mutableStateOf(Offset(0f, 0f)) }
                    LocalVideoPiP(
                        localStream = stream,
                        offset = pipOffset,
                        onOffsetChange = { pipOffset = it },
                        viewModel = viewModel,
                        onSwitchCamera = { viewModel.switchCamera() }
                    )
                }
            }

            // ── Top bar (auto-hide) ─────────────────────────────────────────
            AnimatedVisibility(
                visible = uiVisible,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(600)),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                ModernCallTopBar(
                    calleeName = calleeName,
                    callDuration = callDuration,
                    connectionState = connectionState,
                    isRecording = isRecording,
                    isScreenSharing = isScreenSharing,
                    noiseCancellation = noiseCancellation,
                    onStopRecording = { isRecording = false },
                    onStopScreenShare = { isScreenSharing = false }
                )
            }

            // ── Bottom controls (auto-hide) ─────────────────────────────────
            AnimatedVisibility(
                visible = uiVisible || showFiltersPanel || showBgPanel,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(600)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    // Gradient scrim over video
                    if (hasRemoteVideo) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                    )
                                )
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Expanded panels
                        AnimatedVisibility(
                            visible = showFiltersPanel,
                            enter = expandVertically(expandFrom = Alignment.Bottom),
                            exit = shrinkVertically(shrinkTowards = Alignment.Bottom)
                        ) {
                            VideoFiltersPanel(
                                activeFilter = activeFilter,
                                intensity = filterIntensity,
                                onFilterSelected = { viewModel.applyVideoFilter(it) },
                                onIntensityChanged = { intensity ->
                                    filterIntensity = intensity
                                    viewModel.setFilterIntensity(intensity)
                                },
                                onDismiss = { showFiltersPanel = false }
                            )
                        }
                        AnimatedVisibility(
                            visible = showBgPanel,
                            enter = expandVertically(expandFrom = Alignment.Bottom),
                            exit = shrinkVertically(shrinkTowards = Alignment.Bottom)
                        ) {
                            VirtualBackgroundPanel(
                                activeMode = activeVirtualBg,
                                onModeSelected = { viewModel.applyVirtualBackground(it) },
                                onCustomImageSelected = { viewModel.loadCustomBackground(it) },
                                onDismiss = { showBgPanel = false }
                            )
                        }

                        // Effect pills (only when camera is on and panels are hidden)
                        if (videoEnabled && hasRemoteVideo && !showFiltersPanel && !showBgPanel) {
                            Row(
                                modifier = Modifier.padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val filterActive = activeFilter != VideoFilterType.NONE
                                CallEffectPill(
                                    icon = Icons.Default.AutoFixHigh,
                                    label = stringResource(R.string.video_filters_title),
                                    isActive = filterActive,
                                    activeColor = Color(0xFF7C4DFF),
                                    onClick = {
                                        showFiltersPanel = true; showBgPanel = false
                                        uiVisible = true; lastInteractionTime = System.currentTimeMillis()
                                    }
                                )
                                val bgActive = activeVirtualBg != VirtualBgMode.NONE
                                CallEffectPill(
                                    icon = Icons.Default.BlurOn,
                                    label = stringResource(R.string.virtual_bg_title),
                                    isActive = bgActive,
                                    activeColor = Color(0xFF00BCD4),
                                    onClick = {
                                        showBgPanel = true; showFiltersPanel = false
                                        uiVisible = true; lastInteractionTime = System.currentTimeMillis()
                                    }
                                )
                            }
                        }

                        // Main control bar
                        ModernCallControlBar(
                            audioEnabled = audioEnabled,
                            videoEnabled = videoEnabled,
                            speakerEnabled = speakerEnabled,
                            callType = callType,
                            isScreenSharing = isScreenSharing,
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
                            onToggleScreenShare = {
                                val act = context as? CallsActivity
                                if (act != null) {
                                    act.screenSharingIntegration.toggle()
                                    isScreenSharing = act.screenSharingIntegration.isSharing
                                } else {
                                    isScreenSharing = !isScreenSharing
                                }
                            },
                            onShowReactions = { showReactions = !showReactions },
                            onEndCall = { viewModel.endCall() }
                        )
                    }
                }
            }

            // Floating reaction animation
            val floatingReaction by viewModel.incomingReaction.observeAsState()
            var displayedReaction by remember { mutableStateOf<String?>(null) }
            var reactionVisible by remember { mutableStateOf(false) }
            val reactionAlpha by animateFloatAsState(
                targetValue = if (reactionVisible) 1f else 0f,
                animationSpec = tween(durationMillis = 300),
                label = "reactionAlpha"
            )
            val reactionOffsetY by animateFloatAsState(
                targetValue = if (reactionVisible) 0f else 80f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "reactionOffsetY"
            )

            LaunchedEffect(floatingReaction) {
                floatingReaction?.let { (emoji, _) ->
                    displayedReaction = emoji
                    reactionVisible = true
                    delay(2000)
                    reactionVisible = false
                    delay(300)
                    displayedReaction = null
                    viewModel.incomingReaction.value = null
                }
            }

            if (displayedReaction != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 24.dp)
                        .offset(y = reactionOffsetY.dp)
                        .graphicsLayer(alpha = reactionAlpha)
                ) {
                    Text(text = displayedReaction ?: "", fontSize = 64.sp)
                }
            }

            if (showReactions) {
                ReactionsOverlay(
                    onReactionSelected = { reaction ->
                        viewModel.sendCallReaction(reaction)
                        showReactions = false
                    },
                    onDismiss = { showReactions = false }
                )
            }

            if (showChatOverlay) {
                ChatDuringCallOverlay(viewModel = viewModel, onDismiss = { showChatOverlay = false })
            }

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
        } // end else (isConnected)
    } // end Box
} // end ActiveCallScreen

// ── Modern call UI components ────────────────────────────────────────────────

/**
 * Top status bar shown during an active call (auto-hides after 5 s).
 */
@Composable
fun ModernCallTopBar(
    calleeName: String,
    callDuration: Int,
    connectionState: String,
    isRecording: Boolean,
    isScreenSharing: Boolean,
    noiseCancellation: Boolean,
    onStopRecording: () -> Unit,
    onStopScreenShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "top_bar")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "dot"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Caller name + duration pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column {
                Text(
                    text = calleeName,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (connectionState == "CONNECTED" && callDuration > 0) {
                    Text(
                        text = formatDuration(callDuration),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFC107).copy(alpha = dotAlpha))
                        )
                        Text(
                            text = when (connectionState) {
                                "RECONNECTING" -> "Відновлення..."
                                "ACCEPTING" -> "Підключення..."
                                else -> "З'єднання..."
                            },
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Right-side indicators
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (noiseCancellation) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF9C27B0).copy(alpha = 0.25f))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(text = "NC", fontSize = 10.sp, color = Color(0xFFCE93D8))
                }
            }
            if (isRecording) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE53935).copy(alpha = 0.2f))
                        .clickable(onClick = onStopRecording)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE53935).copy(alpha = dotAlpha))
                        )
                        Text(text = "REC", fontSize = 10.sp, color = Color(0xFFE57373))
                    }
                }
            }
            if (isScreenSharing) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF00BCD4).copy(alpha = 0.2f))
                        .clickable(onClick = onStopScreenShare)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ScreenShare,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color(0xFF00BCD4)
                    )
                }
            }
        }
    }
}

/**
 * Background for audio calls — themed gradient + avatar overlay.
 * Background style is read from [CallBackgroundManager] (user preference).
 */
@Composable
fun AudioCallBackground(calleeName: String, calleeAvatar: String) {
    val context = LocalContext.current
    val selectedBg = remember { CallBackgroundManager.load(context) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // ── Themed background layer ──────────────────────────────────────────
        CallBgLayer(bg = selectedBg, modifier = Modifier.fillMaxSize())

        // Ghost avatar full-screen at low opacity for depth
        if (calleeAvatar.isNotEmpty()) {
            AsyncImage(
                model = calleeAvatar,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.08f
            )
        }

        // ── Avatar + name ────────────────────────────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(172.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.06f))
                )
                if (calleeAvatar.isNotEmpty()) {
                    AsyncImage(
                        model = calleeAvatar,
                        contentDescription = calleeName,
                        modifier = Modifier
                            .size(148.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(148.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2A3A5C)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = calleeName.firstOrNull()?.uppercase() ?: "?",
                            fontSize = 60.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
            Text(
                text = calleeName,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

/**
 * Clean modern control bar for active calls.
 *
 * Audio calls: MIC | SPEAKER | REACT | END
 * Video calls: MIC | VIDEO | FLIP  | SPEAKER | END
 */
@Composable
fun ModernCallControlBar(
    audioEnabled: Boolean,
    videoEnabled: Boolean,
    speakerEnabled: Boolean,
    callType: String,
    isScreenSharing: Boolean = false,
    onToggleAudio: () -> Unit,
    onToggleVideo: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleScreenShare: () -> Unit = {},
    onShowReactions: () -> Unit = {},
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Mute
        CallBtn(
            icon = if (audioEnabled) Icons.Default.Mic else Icons.Default.MicOff,
            label = if (audioEnabled) "Мік" else "Вимк",
            bgColor = if (!audioEnabled) Color(0xFFCC3333) else Color(0xFF2E2E2E),
            onClick = onToggleAudio
        )

        if (callType == "video") {
            // Camera on/off
            CallBtn(
                icon = if (videoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                label = if (videoEnabled) "Відео" else "Вимк",
                bgColor = if (!videoEnabled) Color(0xFFCC3333) else Color(0xFF2E2E2E),
                onClick = onToggleVideo
            )
            // Flip camera
            CallBtn(
                icon = Icons.Default.Cameraswitch,
                label = "Камера",
                bgColor = Color(0xFF2E2E2E),
                onClick = onSwitchCamera
            )
        }

        // Speaker
        CallBtn(
            icon = if (speakerEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
            label = if (speakerEnabled) "Динамік" else "Навушн.",
            bgColor = if (speakerEnabled) Color(0xFF1A4A1A) else Color(0xFF2E2E2E),
            onClick = onToggleSpeaker
        )

        if (callType == "audio") {
            // Reactions for audio calls (handy shortcut)
            CallBtn(
                icon = Icons.Default.EmojiEmotions,
                label = "Реакція",
                bgColor = Color(0xFF2E2E2E),
                onClick = onShowReactions
            )
        }

        // End call — larger red button
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable(onClick = onEndCall)
        ) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFFFF3B30), Color(0xFFCC0000))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "Завершити",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text("Кінець", color = Color(0xFFFF5252), fontSize = 11.sp)
        }
    }
}

/**
 * Individual control button used in [ModernCallControlBar].
 */
@Composable
fun CallBtn(
    icon: ImageVector,
    label: String,
    bgColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Compact pill button for Filters / Background effects.
 */
@Composable
fun CallEffectPill(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isActive) activeColor.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.15f),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Text(text = label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
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
                    onSwitchCamera()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Cameraswitch,
                contentDescription = stringResource(R.string.switch_camera),
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
                    text = "💡 Якість автоматично адаптується до швидкості мережі. При слабкому інтернеті знижується до 480p/240p.",
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
        "❤️" to stringResource(R.string.call_reaction_heart),
        "👍" to stringResource(R.string.call_reaction_like),
        "😂" to stringResource(R.string.call_reaction_laugh),
        "😮" to stringResource(R.string.call_reaction_wow),
        "😢" to stringResource(R.string.call_reaction_sad),
        "🔥" to stringResource(R.string.call_reaction_fire),
        "👏" to stringResource(R.string.call_reaction_applause),
        "🎉" to stringResource(R.string.call_reaction_celebrate)
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
                text = stringResource(R.string.call_choose_reaction),
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
 * Chat Overlay - messaging during a call via Socket.IO
 */
@Composable
fun ChatDuringCallOverlay(
    viewModel: CallsViewModel,
    onDismiss: () -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    val chatMessages = viewModel.callChatMessages.observeAsState(emptyList())

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
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
                    detectTapGestures { }
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.call_chat_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = Color.White
                    )
                }
            }

            // Messages list
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                val messages = chatMessages.value
                if (messages.isEmpty()) {
                    Text(
                        text = stringResource(R.string.call_chat_empty),
                        color = Color(0xFF666666),
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        reverseLayout = true,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(messages.size) { index ->
                            val msg = messages[messages.size - 1 - index]
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = msg.first,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF2196F3)
                                )
                                Text(
                                    text = msg.second,
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

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
                        Text(stringResource(R.string.call_chat_placeholder), color = Color(0xFF666666))
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
                            viewModel.sendCallChatMessage(messageText)
                            messageText = ""
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = stringResource(R.string.send),
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

// ═══════════════════════════════════════════════════════════════════════════
// GROUP CALL SCREEN — Групповой звонок (mesh P2P, до 5 или 25 участников)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Главный экран группового звонка.
 * Использует фиолетово-синий градиент для визуального отличия от 1-на-1 звонков.
 */
@Composable
fun GroupCallScreen(
    viewModel: CallsViewModel,
    groupName: String,
    groupId: Int,
    callType: String,
    onCallEnd: () -> Unit
) {
    val participants by viewModel.groupCallParticipants.observeAsState(emptyList())
    val localStream by viewModel.localStreamAdded.observeAsState(null)

    var audioEnabled by remember { mutableStateOf(true) }
    var videoEnabled by remember { mutableStateOf(callType == "video") }
    var speakerOn by remember { mutableStateOf(true) }
    var callSeconds by remember { mutableStateOf(0) }
    var showInviteSheet by remember { mutableStateOf(false) }
    val maxParticipants = viewModel.getCurrentGroupMaxParticipants()
    val isPremium = maxParticipants > 5
    val context = LocalContext.current
    val roomName = viewModel.currentGroupRoomName ?: ""

    // Таймер звонка
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            callSeconds++
        }
    }

    // Локальный участник
    val localParticipant = GroupCallParticipant(
        userId = viewModel.getUserId().toLong(),
        name = stringResource(R.string.call_you),
        audioEnabled = audioEnabled,
        videoEnabled = videoEnabled,
        mediaStream = localStream,
        connectionState = "connected"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0D0D2B), Color(0xFF1A0A3D), Color(0xFF0D1F3D))
                )
            )
            .systemBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ─── Шапка группового звонка ──────────────────────────────────
            GroupCallTopBar(
                groupName = groupName,
                participantCount = participants.size + 1, // +1 = local
                maxParticipants = maxParticipants,
                isPremium = isPremium,
                callDuration = callSeconds
            )

            // ─── Сетка участников ─────────────────────────────────────────
            Box(modifier = Modifier.weight(1f)) {
                GroupCallGrid(
                    participants = participants,
                    localParticipant = localParticipant,
                    modifier = Modifier.fillMaxSize()
                )

                // Если никого нет — показать подсказку
                if (participants.isEmpty()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF7C4DFF),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.group_call_waiting),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 15.sp
                        )
                    }
                }
            }

            // ─── Панель управления ────────────────────────────────────────
            GroupCallControls(
                audioEnabled = audioEnabled,
                videoEnabled = videoEnabled,
                speakerOn = speakerOn,
                isInitiator = viewModel.isGroupCallInitiator(),
                onAudioToggle = {
                    audioEnabled = !audioEnabled
                    viewModel.toggleGroupAudio(audioEnabled)
                },
                onVideoToggle = {
                    videoEnabled = !videoEnabled
                    viewModel.toggleGroupVideo(videoEnabled)
                },
                onSpeakerToggle = {
                    speakerOn = !speakerOn
                    viewModel.toggleSpeaker(speakerOn)
                },
                onLeave = {
                    if (viewModel.isGroupCallInitiator()) {
                        viewModel.endGroupCallForAll()
                    } else {
                        viewModel.leaveGroupCall()
                    }
                    onCallEnd()
                },
                onInvite = { showInviteSheet = true }
            )
        }
    }

    // ── Invite / Share sheet ───────────────────────────────────────────────────
    if (showInviteSheet) {
        GroupCallInviteSheet(
            roomName    = roomName,
            groupName   = groupName,
            onDismiss   = { showInviteSheet = false }
        )
    }
}

@Composable
private fun GroupCallTopBar(
    groupName: String,
    participantCount: Int,
    maxParticipants: Int,
    isPremium: Boolean,
    callDuration: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Бейдж "ГРУПА"
                Text(
                    text = stringResource(R.string.group_call_badge_short),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(Color(0xFF7C4DFF), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                if (isPremium) {
                    Text(
                        text = "PRO",
                        color = Color(0xFFFFD700),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color(0xFFFFD700).copy(alpha = 0.18f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = groupName,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.group_call_participants_count, participantCount, maxParticipants),
                color = Color(0xFF9E9EC0),
                fontSize = 12.sp
            )
        }

        // Таймер
        Text(
            text = formatDuration(callDuration),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun GroupCallControls(
    audioEnabled: Boolean,
    videoEnabled: Boolean,
    speakerOn: Boolean,
    isInitiator: Boolean,
    onAudioToggle: () -> Unit,
    onVideoToggle: () -> Unit,
    onSpeakerToggle: () -> Unit,
    onLeave: () -> Unit,
    onInvite: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.4f))
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Мікрофон
        GroupControlButton(
            icon = if (audioEnabled) Icons.Default.Mic else Icons.Default.MicOff,
            label = if (audioEnabled) stringResource(R.string.group_call_mic_on)
                    else stringResource(R.string.group_call_mic_off),
            active = audioEnabled,
            onClick = onAudioToggle
        )

        // Камера
        GroupControlButton(
            icon = if (videoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
            label = if (videoEnabled) stringResource(R.string.group_call_camera_on)
                    else stringResource(R.string.group_call_camera_off),
            active = videoEnabled,
            onClick = onVideoToggle
        )

        // Динамік
        GroupControlButton(
            icon = if (speakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
            label = if (speakerOn) stringResource(R.string.group_call_speaker_on)
                    else stringResource(R.string.group_call_speaker_off),
            active = speakerOn,
            onClick = onSpeakerToggle
        )

        // Запросити учасника
        GroupControlButton(
            icon   = Icons.Default.PersonAdd,
            label  = stringResource(R.string.group_call_invite_title),
            active = true,
            onClick = onInvite,
            activeColor = Color(0xFF2196F3)
        )

        // Завершить звонок
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF4444))
                    .clickable(onClick = onLeave),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = if (isInitiator) stringResource(R.string.group_call_end)
                                         else stringResource(R.string.group_call_leave),
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = if (isInitiator) stringResource(R.string.group_call_end)
                       else stringResource(R.string.group_call_leave),
                color = Color(0xFFFF4444),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun GroupControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    activeColor: Color = Color(0xFFAA80FF)
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(
                    if (active) Color(0xFF2A2A5A) else Color(0xFF444444)
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) activeColor else Color.Gray,
                modifier = Modifier.size(26.dp)
            )
        }
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 10.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(64.dp)
        )
    }
}

// ─── Invite / Share Sheet ─────────────────────────────────────────────────────

@Composable
private fun GroupCallInviteSheet(
    roomName: String,
    groupName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = remember {
        context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    }
    val callLink = "worldmates://group_call/$roomName"

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Color(0xFF1A1A3A),
        title = {
            Text(
                text  = stringResource(R.string.group_call_invite_title),
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Call link display
                Surface(
                    color  = Color.White.copy(alpha = 0.08f),
                    shape  = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text     = callLink,
                        color    = Color(0xFFAA80FF),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                // Copy link button
                OutlinedButton(
                    onClick = {
                        val clip = android.content.ClipData.newPlainText("Group Call Link", callLink)
                        clipboardManager.setPrimaryClip(clip)
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.group_call_link_copied),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    border   = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF7C4DFF)),
                    shape    = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFF7C4DFF))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text  = stringResource(R.string.group_call_invite_copy_link),
                        color = Color(0xFF7C4DFF)
                    )
                }
                // Share via system share sheet
                Button(
                    onClick = {
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, callLink)
                        }
                        context.startActivity(
                            android.content.Intent.createChooser(shareIntent, groupName)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF))
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(text = stringResource(R.string.share), color = Color.White)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = Color(0xFF7C4DFF))
            }
        }
    )
}

// ════════════════════════════════════════════════════════════════════════════
// ── VIDEO FILTERS PANEL ──────────────────────────────────────────────────
// Панель відеофільтрів для відеодзвінків (фільтри типу FaceTime)
// Відображається знизу над панеллю керування при натисканні кнопки "Filters"
// ════════════════════════════════════════════════════════════════════════════

/**
 * Кнопка-іконка для відкриття/закриття панелі відеофільтрів.
 * Розміщується серед інших кнопок управління дзвінком.
 */
@Composable
fun VideoFiltersButton(
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(
                    if (isActive) Color(0xFF7C4DFF).copy(alpha = 0.9f)
                    else Color.White.copy(alpha = 0.15f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoFixHigh,
                contentDescription = stringResource(R.string.video_filters_btn_label),
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = stringResource(R.string.video_filters_btn_label),
            color = Color.White,
            fontSize = 11.sp
        )
    }
}

/**
 * Панель вибору відеофільтрів.
 * Виїжджає знизу (AnimatedVisibility) і відображає горизонтальний список фільтрів.
 *
 * @param activeFilter Поточний активний фільтр (підсвічується)
 * @param intensity    Поточна інтенсивність (0.0–1.0)
 * @param onFilterSelected Callback при виборі фільтру
 * @param onIntensityChanged Callback при зміні інтенсивності
 * @param onDismiss    Callback закриття панелі
 */
@Composable
fun VideoFiltersPanel(
    activeFilter: VideoFilterType,
    intensity: Float,
    onFilterSelected: (VideoFilterType) -> Unit,
    onIntensityChanged: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    // Локалізовані назви фільтрів
    val filterLabels = mapOf(
        VideoFilterType.NONE    to stringResource(R.string.video_filters_none),
        VideoFilterType.BEAUTY  to stringResource(R.string.video_filters_beauty),
        VideoFilterType.WARM    to stringResource(R.string.video_filters_warm),
        VideoFilterType.COOL    to stringResource(R.string.video_filters_cool),
        VideoFilterType.BW      to stringResource(R.string.video_filters_bw),
        VideoFilterType.VINTAGE to stringResource(R.string.video_filters_vintage),
        VideoFilterType.VIVID   to stringResource(R.string.video_filters_vivid),
        VideoFilterType.SEPIA   to stringResource(R.string.video_filters_sepia),
    )

    // Кольорові превью для кожного фільтру (для UX)
    val filterColors = mapOf(
        VideoFilterType.NONE    to listOf(Color(0xFF424242), Color(0xFF616161)),
        VideoFilterType.BEAUTY  to listOf(Color(0xFFE91E63), Color(0xFFF48FB1)),
        VideoFilterType.WARM    to listOf(Color(0xFFFF6F00), Color(0xFFFFCA28)),
        VideoFilterType.COOL    to listOf(Color(0xFF1565C0), Color(0xFF42A5F5)),
        VideoFilterType.BW      to listOf(Color(0xFF212121), Color(0xFF9E9E9E)),
        VideoFilterType.VINTAGE to listOf(Color(0xFF795548), Color(0xFFBCAAA4)),
        VideoFilterType.VIVID   to listOf(Color(0xFF6A1B9A), Color(0xFFAB47BC)),
        VideoFilterType.SEPIA   to listOf(Color(0xFF4E342E), Color(0xFFA1887F)),
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF1A1A2E).copy(alpha = 0.95f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Заголовок + кнопка закриття
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.video_filters_title),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Горизонтальна смуга фільтрів
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(VideoFilterType.values().toList()) { filter ->
                    val isSelected = filter == activeFilter
                    val colors = filterColors[filter] ?: listOf(Color.Gray, Color.DarkGray)

                    Column(
                        modifier = Modifier
                            .clickable { onFilterSelected(filter) }
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Brush.linearGradient(colors))
                                .then(
                                    if (isSelected) Modifier.border(
                                        2.dp, Color.White, RoundedCornerShape(12.dp)
                                    ) else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Text(
                            text = filterLabels[filter] ?: filter.name,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }

            // Повзунок інтенсивності (показується тільки коли фільтр активний)
            if (activeFilter != VideoFilterType.NONE) {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.video_filters_intensity),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        modifier = Modifier.width(90.dp)
                    )
                    Slider(
                        value = intensity,
                        onValueChange = onIntensityChanged,
                        valueRange = 0.1f..1f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF7C4DFF),
                            activeTrackColor = Color(0xFF7C4DFF)
                        )
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// ── VIRTUAL BACKGROUND PANEL ─────────────────────────────────────────────
// Панель вибору віртуального фону (розмиття + кольори + кастомне зображення)
// ════════════════════════════════════════════════════════════════════════════

/**
 * Кнопка-іконка для відкриття/закриття панелі фону.
 */
@Composable
fun VirtualBackgroundButton(
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(
                    if (isActive) Color(0xFF00BCD4).copy(alpha = 0.9f)
                    else Color.White.copy(alpha = 0.15f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Wallpaper,
                contentDescription = stringResource(R.string.virtual_bg_btn_label),
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = stringResource(R.string.virtual_bg_btn_label),
            color = Color.White,
            fontSize = 11.sp
        )
    }
}

/**
 * Панель вибору віртуального фону.
 *
 * Відображає режими: None, Blur Light, Blur Strong, Office, Nature, Gradient, Custom Image.
 * Кастомне зображення вибирається через system photo picker.
 */
@Composable
fun VirtualBackgroundPanel(
    activeMode: VirtualBgMode,
    onModeSelected: (VirtualBgMode) -> Unit,
    onCustomImageSelected: (android.net.Uri) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // Локалізовані назви режимів
    val modeLabels = mapOf(
        VirtualBgMode.NONE          to stringResource(R.string.virtual_bg_none),
        VirtualBgMode.BLUR_LIGHT    to stringResource(R.string.virtual_bg_blur_light),
        VirtualBgMode.BLUR_STRONG   to stringResource(R.string.virtual_bg_blur_strong),
        VirtualBgMode.COLOR_OFFICE  to stringResource(R.string.virtual_bg_color_office),
        VirtualBgMode.COLOR_NATURE  to stringResource(R.string.virtual_bg_color_nature),
        VirtualBgMode.COLOR_GRADIENT to stringResource(R.string.virtual_bg_color_gradient),
        VirtualBgMode.CUSTOM_IMAGE  to stringResource(R.string.virtual_bg_image),
    )

    // Кольорові превью для кожного режиму
    val modeColors = mapOf(
        VirtualBgMode.NONE          to listOf(Color(0xFF424242), Color(0xFF616161)),
        VirtualBgMode.BLUR_LIGHT    to listOf(Color(0xFF1565C0).copy(alpha = 0.5f), Color(0xFF42A5F5).copy(alpha = 0.5f)),
        VirtualBgMode.BLUR_STRONG   to listOf(Color(0xFF0D47A1).copy(alpha = 0.3f), Color(0xFF1976D2).copy(alpha = 0.3f)),
        VirtualBgMode.COLOR_OFFICE  to listOf(Color(0xFFF5F0E8), Color(0xFFDDD5C4)),
        VirtualBgMode.COLOR_NATURE  to listOf(Color(0xFF2D5A27), Color(0xFF4CAF50)),
        VirtualBgMode.COLOR_GRADIENT to listOf(Color(0xFF1A237E), Color(0xFF6A1B9A)),
        VirtualBgMode.CUSTOM_IMAGE  to listOf(Color(0xFF263238), Color(0xFF546E7A)),
    )

    // Launcher для вибору зображення з галереї
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { onCustomImageSelected(it) }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF1A1A2E).copy(alpha = 0.95f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Заголовок + закрити
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.virtual_bg_title),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
                }
            }

            // Підказка про освітлення
            Text(
                text = stringResource(R.string.virtual_bg_segmentation_hint),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp
            )

            Spacer(Modifier.height(12.dp))

            // Горизонтальна смуга режимів
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(VirtualBgMode.values().toList()) { mode ->
                    val isSelected = mode == activeMode
                    val colors = modeColors[mode] ?: listOf(Color.Gray, Color.DarkGray)

                    Column(
                        modifier = Modifier
                            .clickable {
                                if (mode == VirtualBgMode.CUSTOM_IMAGE) {
                                    // Відкрити галерею
                                    imagePicker.launch("image/*")
                                    onModeSelected(mode)
                                } else {
                                    onModeSelected(mode)
                                }
                            }
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Brush.linearGradient(colors))
                                .then(
                                    if (isSelected) Modifier.border(
                                        2.dp, Color(0xFF00BCD4), RoundedCornerShape(12.dp)
                                    ) else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            when (mode) {
                                VirtualBgMode.NONE -> Icon(
                                    Icons.Default.Block,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(28.dp)
                                )
                                VirtualBgMode.BLUR_LIGHT, VirtualBgMode.BLUR_STRONG -> Icon(
                                    Icons.Default.BlurOn,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                                VirtualBgMode.CUSTOM_IMAGE -> Icon(
                                    Icons.Default.AddPhotoAlternate,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                                else -> {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            text = modeLabels[mode] ?: mode.name,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(72.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// ── CALL TRANSFER PANEL ──────────────────────────────────────────────────
// Панель передачі активного дзвінка іншому користувачу
// ════════════════════════════════════════════════════════════════════════════

/**
 * Кнопка-іконка для відкриття панелі передачі дзвінка.
 */
@Composable
fun CallTransferButton(
    isTransferring: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(
                    if (isTransferring) Color(0xFFFF9800).copy(alpha = 0.9f)
                    else Color.White.copy(alpha = 0.15f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PhoneForwarded,
                contentDescription = stringResource(R.string.call_transfer_btn_label),
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = stringResource(R.string.call_transfer_btn_label),
            color = Color.White,
            fontSize = 11.sp
        )
    }
}

/**
 * Діалог підтвердження передачі дзвінка.
 * Відображається після вибору контакту перед реальною відправкою запиту.
 *
 * @param target  Ціль передачі (ім'я та аватар)
 * @param onConfirm  Callback при підтвердженні
 * @param onDismiss  Callback при скасуванні
 */
@Composable
fun CallTransferConfirmDialog(
    target: TransferTarget,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.call_transfer_confirm_title, target.userName),
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                Text(stringResource(R.string.call_transfer_confirm_message))
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
            ) {
                Icon(Icons.Default.PhoneForwarded, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.call_transfer_confirm_btn), color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.call_transfer_cancel))
            }
        },
        containerColor = Color(0xFF1A1A2E)
    )
}

/**
 * Індикатор статусу передачі дзвінка (відображається поверх активного дзвінку).
 * Показує поточний стан: INITIATING, PENDING, COMPLETED, FAILED.
 */
@Composable
fun CallTransferStatusBanner(
    state: CallTransferState,
    targetName: String?,
    modifier: Modifier = Modifier
) {
    val (text, color) = when (state) {
        CallTransferState.INITIATING, CallTransferState.PENDING ->
            (stringResource(R.string.call_transfer_initiating)) to Color(0xFFFF9800)
        CallTransferState.COMPLETED ->
            (stringResource(R.string.call_transfer_success, targetName ?: "")) to Color(0xFF4CAF50)
        CallTransferState.FAILED ->
            (stringResource(R.string.call_transfer_failed)) to Color(0xFFF44336)
        CallTransferState.IDLE -> return // нічого не показуємо
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state == CallTransferState.INITIATING || state == CallTransferState.PENDING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = color,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(text = text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// ── CONFERENCE BANDWIDTH MODE SELECTOR ──────────────────────────────────
// Компонент вибору режиму пропускної здатності для групових дзвінків
// ════════════════════════════════════════════════════════════════════════════

/**
 * Рядок чіпів для вибору режиму пропускної здатності конференції.
 * Розміщується у шторці налаштувань групового дзвінка.
 *
 * @param currentMode    Поточний вибраний режим
 * @param onModeSelected Callback при виборі режиму
 */
@Composable
fun ConferenceBandwidthModeSelector(
    currentMode: com.worldmates.messenger.network.GroupWebRTCManager.ConferenceBandwidthMode,
    onModeSelected: (com.worldmates.messenger.network.GroupWebRTCManager.ConferenceBandwidthMode) -> Unit
) {
    val modes = listOf(
        com.worldmates.messenger.network.GroupWebRTCManager.ConferenceBandwidthMode.SAVE to stringResource(R.string.conf_bandwidth_save),
        com.worldmates.messenger.network.GroupWebRTCManager.ConferenceBandwidthMode.AUTO to stringResource(R.string.conf_bandwidth_auto),
        com.worldmates.messenger.network.GroupWebRTCManager.ConferenceBandwidthMode.HIGH to stringResource(R.string.conf_bandwidth_high),
    )

    Column {
        Text(
            text = stringResource(R.string.conf_bandwidth_mode_title),
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 13.sp
        )
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            modes.forEach { (mode, label) ->
                val isSelected = mode == currentMode
                FilterChip(
                    selected = isSelected,
                    onClick = { onModeSelected(mode) },
                    label = { Text(label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF7C4DFF),
                        selectedLabelColor = Color.White,
                        containerColor = Color.White.copy(alpha = 0.1f),
                        labelColor = Color.White.copy(alpha = 0.7f)
                    )
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// ── CALL TRANSFER CONTACT PICKER ─────────────────────────────────────────
// Діалог вибору контакту для передачі дзвінка.
//
// Показує:
//   • Поточний співрозмовник (для 1-на-1 дзвінка) як перший пункт
//   • Учасники групового дзвінка (якщо є)
//   • Поле вводу ID для ручного введення
// ════════════════════════════════════════════════════════════════════════════

/**
 * Діалог вибору контакту перед передачею дзвінка.
 *
 * @param callee             Поточний співрозмовник (1-на-1 дзвінок). Null для групового.
 * @param groupParticipants  Список учасників (для групового дзвінка).
 * @param onContactSelected  Callback: обраний контакт → відкрити підтвердження.
 * @param onDismiss          Callback закриття без вибору.
 */
@Composable
fun CallTransferPickerDialog(
    callee: TransferTarget?,
    groupParticipants: List<com.worldmates.messenger.ui.calls.GroupCallParticipant>,
    onContactSelected: (TransferTarget) -> Unit,
    onDismiss: () -> Unit
) {
    // Ручне введення ID (для 1-на-1 без підготовлених контактів)
    var manualInput by remember { mutableStateOf("") }

    // Список кандидатів для передачі
    // Для 1-на-1: поточний співрозмовник + ручне введення
    // Для групового: учасники (не включаємо себе — getUserId() == participant.userId)
    val candidates: List<TransferTarget> = remember(callee, groupParticipants) {
        val result = mutableListOf<TransferTarget>()
        callee?.let { result.add(it) }
        groupParticipants.forEach { p ->
            // Не додаємо якщо вже є в списку (збігається userId)
            if (result.none { it.userId == p.userId }) {
                result.add(TransferTarget(p.userId, p.name, p.avatar))
            }
        }
        result
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A2E),
        title = {
            Text(
                text = stringResource(R.string.call_transfer_title),
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.call_transfer_subtitle),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(4.dp))

                // Список кандидатів
                if (candidates.isNotEmpty()) {
                    candidates.forEach { target ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .clickable { onContactSelected(target) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Аватар
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF7C4DFF).copy(alpha = 0.4f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!target.userAvatar.isNullOrBlank()) {
                                    AsyncImage(
                                        model = target.userAvatar,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(
                                        text = target.userName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                            // Ім'я
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = target.userName,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                            }
                            // Стрілка
                            Icon(
                                Icons.Default.PhoneForwarded,
                                contentDescription = null,
                                tint = Color(0xFFFF9800),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Поле ручного введення (якщо немає готових кандидатів або як додаткова опція)
                if (candidates.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = manualInput,
                        onValueChange = { manualInput = it },
                        label = {
                            Text(
                                stringResource(R.string.call_transfer_search_hint),
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        },
                        placeholder = {
                            Text("User ID or username", color = Color.White.copy(alpha = 0.3f))
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFF9800),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            }
        },
        confirmButton = {
            // Кнопка підтвердження ручного введення (тільки коли немає кандидатів)
            if (candidates.isEmpty() && manualInput.isNotBlank()) {
                Button(
                    onClick = {
                        val userId = manualInput.toLongOrNull() ?: 0L
                        onContactSelected(TransferTarget(userId, manualInput, null))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                ) {
                    Text(stringResource(R.string.call_transfer_confirm_btn), color = Color.White)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.call_transfer_cancel), color = Color.White.copy(alpha = 0.7f))
            }
        }
    )
}

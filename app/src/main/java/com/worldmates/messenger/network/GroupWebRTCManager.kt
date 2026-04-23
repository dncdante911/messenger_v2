package com.worldmates.messenger.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * GroupWebRTCManager — управляет несколькими WebRTC соединениями (mesh) для групповых звонков.
 *
 * Архитектура mesh: каждый участник имеет отдельный PeerConnection с каждым другим.
 * Поддерживает до 35 участников (Premium) или 5 (Free).
 */
class GroupWebRTCManager(private val context: Context) {

    companion object {
        private const val TAG = "GroupWebRTCManager"

        // 🔐 coturn static-auth-secret (same server as 1-on-1 calls)
        private const val TURN_SECRET = "ad8a76d057d6ba0d6fd79bbc84504e320c8538b92db5c9b84fc3bd18d1c511b9"
        private const val TURN_IP_1 = "195.22.131.11"
        private const val TURN_IP_2 = "46.232.232.38"
        private const val TURN_IP_3 = "93.171.188.229"
        // TURNS (TLS) — по домену, т.к. SSL сертификат привязан к домену
        private const val TURNS_DOMAIN = "worldmates.club"

        /**
         * Generates time-limited TURN credentials via HMAC-SHA1 (coturn use-auth-secret).
         * Used as fallback when the server hasn't provided ICE servers yet.
         */
        fun buildFallbackTurnServers(): List<PeerConnection.IceServer> {
            return try {
                val expiry = System.currentTimeMillis() / 1000 + 86400
                val username = "$expiry:group_user"
                val mac = javax.crypto.Mac.getInstance("HmacSHA1")
                mac.init(javax.crypto.spec.SecretKeySpec(TURN_SECRET.toByteArray(Charsets.UTF_8), "HmacSHA1"))
                val credential = android.util.Base64.encodeToString(
                    mac.doFinal(username.toByteArray(Charsets.UTF_8)),
                    android.util.Base64.NO_WRAP
                )
                listOf(
                    PeerConnection.IceServer.builder("stun:$TURN_IP_1:3478").createIceServer(),
                    PeerConnection.IceServer.builder("stun:$TURN_IP_2:3478").createIceServer(),
                    PeerConnection.IceServer.builder("stun:$TURN_IP_3:3478").createIceServer(),
                    PeerConnection.IceServer.builder(listOf(
                        "turn:$TURN_IP_1:3478?transport=udp",
                        "turn:$TURN_IP_1:3478?transport=tcp"
                    )).setUsername(username).setPassword(credential).createIceServer(),
                    PeerConnection.IceServer.builder(listOf(
                        "turn:$TURN_IP_2:3478?transport=udp",
                        "turn:$TURN_IP_2:3478?transport=tcp"
                    )).setUsername(username).setPassword(credential).createIceServer(),
                    PeerConnection.IceServer.builder(listOf(
                        "turn:$TURN_IP_3:3478?transport=udp",
                        "turn:$TURN_IP_3:3478?transport=tcp"
                    )).setUsername(username).setPassword(credential).createIceServer(),
                    // TURNS TLS — по домену (SSL сертификат привязан к домену, не к IP)
                    PeerConnection.IceServer.builder("turns:$TURNS_DOMAIN:5349?transport=tcp")
                        .setUsername(username).setPassword(credential).createIceServer()
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to build fallback TURN servers", e)
                listOf(PeerConnection.IceServer.builder("stun:$TURN_IP_1:3478").createIceServer())
            }
        }
    }

    // PeerConnection для каждого удалённого участника
    private val peerConnections = mutableMapOf<Long, PeerConnection>()

    // RtpSenders per peer — needed to replace/remove tracks later
    private val peerSenders = mutableMapOf<Long, MutableList<RtpSender>>()

    // Удалённые медиа-потоки от каждого участника
    private val remoteStreams = mutableMapOf<Long, MediaStream>()

    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private var localMediaStream: MediaStream? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    private var iceServersConfig: List<PeerConnection.IceServer> = emptyList()
    private var isInitialized = false
    private var isCapturing = false

    // ─── Callbacks ────────────────────────────────────────────────────────────

    /** Вызывается когда получен удалённый поток от участника */
    var onRemoteStreamAdded: ((userId: Long, stream: MediaStream) -> Unit)? = null

    /** Вызывается когда участник отключился */
    var onRemoteStreamRemoved: ((userId: Long) -> Unit)? = null

    /** Вызывается при генерации ICE candidate — нужно отправить через сокет */
    var onIceCandidateReady: ((userId: Long, candidate: IceCandidate) -> Unit)? = null

    /** Вызывается когда создан SDP offer для участника — отправить через сокет */
    var onOfferReady: ((toUserId: Long, sdpOffer: SessionDescription) -> Unit)? = null

    /** Вызывается когда создан SDP answer для участника — отправить через сокет */
    var onAnswerReady: ((toUserId: Long, sdpAnswer: SessionDescription) -> Unit)? = null

    /** Вызывается при ошибке соединения с участником */
    var onPeerConnectionError: ((userId: Long, error: String) -> Unit)? = null

    /** Вызывается когда ICE-соединение с участником установлено (CONNECTED/COMPLETED) */
    var onPeerConnected: ((userId: Long) -> Unit)? = null

    // ─── Инициализация ────────────────────────────────────────────────────────

    fun initialize() {
        if (isInitialized) return
        Log.d(TAG, "🔧 Initializing GroupWebRTCManager")

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )

        val audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()

        val encoderFactory = DefaultVideoEncoderFactory(
            WebRTCManager.getEglContext(), true, true
        )
        val decoderFactory = DefaultVideoDecoderFactory(WebRTCManager.getEglContext())

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .setOptions(PeerConnectionFactory.Options().apply {
                disableEncryption = false
                disableNetworkMonitor = false
            })
            .createPeerConnectionFactory()

        isInitialized = true
        Log.d(TAG, "✅ GroupWebRTCManager initialized")
    }

    fun setIceServers(servers: List<PeerConnection.IceServer>) {
        iceServersConfig = servers
        Log.d(TAG, "✅ ICE servers set: ${servers.size}")
    }

    // ─── Локальные медиа ──────────────────────────────────────────────────────

    fun setupLocalMedia(audioOnly: Boolean = false, context: Context = this.context) {
        initialize()
        Log.d(TAG, "🎤 Setting up local media (audioOnly=$audioOnly)")

        // Аудио
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("echoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("noiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("autoGainControl", "true"))
        }
        val audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory.createAudioTrack("audio_group", audioSource)
        localAudioTrack?.setEnabled(true)

        // Видео — всегда создаём pipeline; если audioOnly, трек отключён и захват не стартует.
        // Это позволяет пользователю включить камеру во время звонка без пересоздания PeerConnection.
        try {
            surfaceTextureHelper = SurfaceTextureHelper.create(
                "GroupCaptureThread", WebRTCManager.getEglContext()
            )
            videoCapturer = createCameraCapturer()
            videoSource = peerConnectionFactory.createVideoSource(false)
            videoCapturer?.initialize(surfaceTextureHelper, context, videoSource!!.capturerObserver)

            if (!audioOnly) {
                videoCapturer?.startCapture(640, 480, 24)
                isCapturing = true
            }

            localVideoTrack = peerConnectionFactory.createVideoTrack("video_group", videoSource)
            localVideoTrack?.setEnabled(!audioOnly)
            Log.d(TAG, "✅ Local video track created (active=${!audioOnly})")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to create video capturer: ${e.message}")
        }

        // Создать локальный медиа-поток
        localMediaStream = peerConnectionFactory.createLocalMediaStream("local_group_stream")
        localAudioTrack?.let { localMediaStream?.addTrack(it) }
        localVideoTrack?.let { localMediaStream?.addTrack(it) }

        // Добавить локальные треки во все существующие PeerConnections (Unified Plan: addTrack)
        peerConnections.forEach { (userId, pc) ->
            addLocalTracksToPeer(userId, pc)
        }

        Log.d(TAG, "✅ Local media stream ready")
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        // Сначала пробуем фронтальную камеру
        enumerator.deviceNames.find { enumerator.isFrontFacing(it) }?.let { name ->
            try {
                return enumerator.createCapturer(name, null)
            } catch (e: Exception) {
                Log.w(TAG, "Front camera failed: ${e.message}")
            }
        }
        // Иначе любую
        enumerator.deviceNames.firstOrNull()?.let { name ->
            try {
                return enumerator.createCapturer(name, null)
            } catch (e: Exception) {
                Log.e(TAG, "Camera creation failed: ${e.message}")
            }
        }
        return null
    }

    fun getLocalMediaStream(): MediaStream? = localMediaStream

    // ─── Управление PeerConnections ───────────────────────────────────────────

    /**
     * Создать PeerConnection для нового участника.
     * Вызывается при получении group_call:participant_joined (existing participants)
     * и при получении group_call:offer (new joiner).
     */
    fun createPeerConnectionForPeer(userId: Long): PeerConnection? {
        initialize()
        Log.d(TAG, "🔌 Creating PeerConnection for peer $userId")

        if (peerConnections.containsKey(userId)) {
            Log.w(TAG, "⚠️ PeerConnection for $userId already exists, closing old one")
            peerConnections[userId]?.close()
        }

        val iceServers = iceServersConfig.ifEmpty {
            Log.w(TAG, "⚠️ No ICE servers set — using local HMAC TURN fallback")
            buildFallbackTurnServers()
        }

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceTransportsType = PeerConnection.IceTransportsType.ALL
        }

        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                Log.d(TAG, "Peer $userId — signaling: $state")
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "Peer $userId — ICE connection: $state")
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> {
                        // Соединение установлено — уведомить ViewModel даже если поток ещё не пришёл
                        onPeerConnected?.invoke(userId)
                        Log.d(TAG, "✅ Peer $userId — ICE connected!")
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED,
                    PeerConnection.IceConnectionState.FAILED,
                    PeerConnection.IceConnectionState.CLOSED -> {
                        onRemoteStreamRemoved?.invoke(userId)
                    }
                    else -> {}
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {}

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                Log.d(TAG, "Peer $userId — ICE gathering: $state")
            }

            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    Log.d(TAG, "ICE candidate ready for peer $userId")
                    onIceCandidateReady?.invoke(userId, it)
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

            override fun onAddStream(stream: MediaStream?) {
                stream?.let {
                    Log.d(TAG, "✅ Remote stream added from peer $userId: " +
                            "audio=${it.audioTracks.size}, video=${it.videoTracks.size}")
                    remoteStreams[userId] = it
                    onRemoteStreamAdded?.invoke(userId, it)
                }
            }

            override fun onRemoveStream(stream: MediaStream?) {
                Log.d(TAG, "Remote stream removed from peer $userId")
                remoteStreams.remove(userId)
                onRemoteStreamRemoved?.invoke(userId)
            }

            override fun onDataChannel(dataChannel: DataChannel?) {}

            override fun onRenegotiationNeeded() {
                Log.d(TAG, "Renegotiation needed for peer $userId")
            }

            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                val stream = streams?.firstOrNull()
                if (stream != null) {
                    Log.d(TAG, "✅ Track added from peer $userId via onAddTrack (stream=${stream.id}, " +
                            "audio=${stream.audioTracks.size}, video=${stream.videoTracks.size})")
                    remoteStreams[userId] = stream
                    onRemoteStreamAdded?.invoke(userId, stream)
                } else {
                    // Unified Plan may call onAddTrack with empty streams[].
                    // Build/update a synthetic stream so the UI can render the track.
                    val track = receiver?.track()
                    Log.d(TAG, "✅ Track added from peer $userId via onAddTrack (no stream, " +
                            "track=${track?.kind()}, building synthetic stream)")
                    val existing = remoteStreams.getOrPut(userId) {
                        peerConnectionFactory.createLocalMediaStream("remote_$userId")
                    }
                    when (track) {
                        is AudioTrack -> existing.addTrack(track)
                        is VideoTrack -> existing.addTrack(track)
                    }
                    onRemoteStreamAdded?.invoke(userId, existing)
                }
            }
        }

        val pc = peerConnectionFactory.createPeerConnection(rtcConfig, observer)
        if (pc == null) {
            Log.e(TAG, "❌ Failed to create PeerConnection for peer $userId")
            return null
        }

        // Добавить локальные треки в новое соединение (Unified Plan: addTrack)
        peerConnections[userId] = pc
        addLocalTracksToPeer(userId, pc)
        Log.d(TAG, "✅ PeerConnection created for peer $userId")
        return pc
    }

    /**
     * Создать SDP offer для существующего участника (вызывается когда новый участник присоединился).
     * Существующий участник создаёт offer → отправляет новому участнику.
     */
    fun createOfferForPeer(toUserId: Long) {
        val pc = peerConnections[toUserId] ?: createPeerConnectionForPeer(toUserId) ?: return
        Log.d(TAG, "📤 Creating offer for peer $toUserId")

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }

        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp ?: return
                pc.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        Log.d(TAG, "✅ Local description set, sending offer to peer $toUserId")
                        onOfferReady?.invoke(toUserId, sdp)
                    }
                    override fun onCreateFailure(error: String?) {}
                    override fun onSetFailure(error: String?) {
                        Log.e(TAG, "❌ Failed to set local description for peer $toUserId: $error")
                        onPeerConnectionError?.invoke(toUserId, error ?: "Set local desc failed")
                    }
                }, sdp)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "❌ Failed to create offer for peer $toUserId: $error")
                onPeerConnectionError?.invoke(toUserId, error ?: "Create offer failed")
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    /**
     * Обработать входящий offer от участника и создать answer.
     * Новый участник получает offers от каждого существующего.
     */
    fun handleOfferAndCreateAnswer(fromUserId: Long, sdpOffer: String) {
        Log.d(TAG, "📥 Handling offer from peer $fromUserId")

        val pc = peerConnections[fromUserId] ?: createPeerConnectionForPeer(fromUserId) ?: return

        val remoteDesc = SessionDescription(SessionDescription.Type.OFFER, sdpOffer)
        pc.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "✅ Remote offer set from peer $fromUserId, creating answer...")
                createAnswerForPeer(fromUserId, pc)
            }
            override fun onCreateFailure(error: String?) {}
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "❌ Failed to set remote offer from $fromUserId: $error")
                onPeerConnectionError?.invoke(fromUserId, error ?: "Set remote offer failed")
            }
        }, remoteDesc)
    }

    private fun createAnswerForPeer(toUserId: Long, pc: PeerConnection) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }

        pc.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp ?: return
                pc.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        Log.d(TAG, "✅ Answer ready for peer $toUserId")
                        onAnswerReady?.invoke(toUserId, sdp)
                    }
                    override fun onCreateFailure(error: String?) {}
                    override fun onSetFailure(error: String?) {
                        Log.e(TAG, "❌ Failed to set local answer for $toUserId: $error")
                    }
                }, sdp)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "❌ Failed to create answer for peer $toUserId: $error")
                onPeerConnectionError?.invoke(toUserId, error ?: "Create answer failed")
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    /**
     * Установить входящий answer от участника.
     */
    fun handleAnswer(fromUserId: Long, sdpAnswer: String) {
        Log.d(TAG, "📥 Handling answer from peer $fromUserId")
        val pc = peerConnections[fromUserId] ?: run {
            Log.w(TAG, "⚠️ No PeerConnection for peer $fromUserId to handle answer")
            return
        }

        val remoteDesc = SessionDescription(SessionDescription.Type.ANSWER, sdpAnswer)
        pc.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "✅ Remote answer set from peer $fromUserId — P2P connected!")
            }
            override fun onCreateFailure(error: String?) {}
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "❌ Failed to set remote answer from $fromUserId: $error")
                onPeerConnectionError?.invoke(fromUserId, error ?: "Set remote answer failed")
            }
        }, remoteDesc)
    }

    /**
     * Добавить ICE candidate для конкретного участника.
     */
    fun addIceCandidateForPeer(fromUserId: Long, candidate: IceCandidate) {
        val pc = peerConnections[fromUserId]
        if (pc != null) {
            pc.addIceCandidate(candidate)
            Log.d(TAG, "✅ ICE candidate added for peer $fromUserId")
        } else {
            Log.w(TAG, "⚠️ No PeerConnection found for peer $fromUserId to add ICE candidate")
        }
    }

    /**
     * Add local audio/video tracks to a PeerConnection using addTrack (Unified Plan).
     * addStream() is NOT supported with UNIFIED_PLAN and causes a fatal SIGABRT.
     */
    private fun addLocalTracksToPeer(userId: Long, pc: PeerConnection) {
        val senders = peerSenders.getOrPut(userId) { mutableListOf() }
        localAudioTrack?.let { track ->
            val sender = pc.addTrack(track, listOf("local_group_stream"))
            sender?.let { senders.add(it) }
        }
        localVideoTrack?.let { track ->
            val sender = pc.addTrack(track, listOf("local_group_stream"))
            sender?.let { senders.add(it) }
        }
        Log.d(TAG, "✅ Added ${senders.size} local tracks to peer $userId via addTrack")
    }

    /**
     * Удалить PeerConnection участника (когда он вышел).
     */
    fun removePeer(userId: Long) {
        Log.d(TAG, "🔴 Removing peer $userId")
        peerConnections[userId]?.close()
        peerConnections.remove(userId)
        peerSenders.remove(userId)
        remoteStreams.remove(userId)
        onRemoteStreamRemoved?.invoke(userId)
    }

    // ─── Управление медиа ─────────────────────────────────────────────────────

    fun setAudioEnabled(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
        Log.d(TAG, "Audio ${if (enabled) "enabled" else "disabled"}")
    }

    fun setVideoEnabled(enabled: Boolean) {
        if (enabled && !isCapturing) {
            try {
                videoCapturer?.startCapture(640, 480, 24)
                isCapturing = true
                Log.d(TAG, "🎥 Camera capture started")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to start camera capture: ${e.message}")
            }
        } else if (!enabled && isCapturing) {
            try {
                videoCapturer?.stopCapture()
                isCapturing = false
                Log.d(TAG, "🎥 Camera capture stopped")
            } catch (e: Exception) {
                Log.w(TAG, "stopCapture: ${e.message}")
            }
        }
        localVideoTrack?.setEnabled(enabled)
        Log.d(TAG, "Video ${if (enabled) "enabled" else "disabled"}")
    }

    fun isAudioEnabled(): Boolean = localAudioTrack?.enabled() == true
    fun isVideoEnabled(): Boolean = localVideoTrack?.enabled() == true

    fun getRemoteStream(userId: Long): MediaStream? = remoteStreams[userId]

    fun getConnectedPeerIds(): List<Long> = peerConnections.keys.toList()

    // ─── Conference Mode Optimization ────────────────────────────────────────
    //
    // Стратегія оптимізації для групових дзвінків:
    // • При збільшенні кількості учасників → знижуємо bitrate кожного відео,
    //   щоб загальна пропускна здатність залишалась прийнятною.
    // • Домінуючий спікер → його відео отримує пріоритет (вищий bitrate),
    //   всі інші знижуються.
    // • Учасники за межами видимого вікна (off-screen) → їх відеотреки
    //   тимчасово вимикаються (setEnabled(false)) для економії CPU/bandwidth.
    // • Режими: AUTO (за замовчуванням), SAVE (явна економія), HIGH (преміум).

    /** Режими пропускної здатності конференції (керується з UI). */
    enum class ConferenceBandwidthMode {
        AUTO,   // Автоматична адаптація за кількістю учасників
        SAVE,   // Примусово нижча якість (мобільний інтернет / слабкий пристрій)
        HIGH,   // Примусово висока якість (Wi-Fi / Premium)
    }

    @Volatile var bandwidthMode: ConferenceBandwidthMode = ConferenceBandwidthMode.AUTO

    /** ID домінуючого спікера (найактивніший за аудіо). Null = немає пріоритету. */
    @Volatile var dominantSpeakerId: Long? = null

    private val optimizationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var optimizationJob: Job? = null

    /** Множина userId, відео яких зараз вимкнено через off-screen оптимізацію. */
    private val pausedVideoUsers = mutableSetOf<Long>()

    /**
     * Запускає фоновий моніторинг та оптимізацію якості для конференції.
     * Викликати після входу до кімнати.
     */
    fun startConferenceOptimization() {
        optimizationJob?.cancel()
        optimizationJob = optimizationScope.launch {
            while (isActive) {
                delay(8_000L) // перевіряємо кожні 8 секунд
                optimizeBitrateForParticipantCount()
            }
        }
        Log.d(TAG, "🚀 Conference optimization started")
    }

    /** Зупиняє фоновий моніторинг. Викликати при виході з конференції. */
    fun stopConferenceOptimization() {
        optimizationJob?.cancel()
        optimizationJob = null
        Log.d(TAG, "⏹️ Conference optimization stopped")
    }

    /**
     * Адаптує bitrate локального відео залежно від:
     * • [bandwidthMode] — ручний режим
     * • кількості поточних PeerConnections
     * • наявності [dominantSpeakerId]
     *
     * Таблиця bitrate (kbps, для LOCAL відео що ми надсилаємо):
     *   1 peer  → 800 kbps
     *   2 peers → 600 kbps
     *   3–4     → 400 kbps
     *   5–8     → 250 kbps
     *   9–14    → 150 kbps
     *   15+     → 100 kbps  (SAVE mode навіть менше)
     */
    fun optimizeBitrateForParticipantCount() {
        val peerCount = peerConnections.size
        val targetKbps = when (bandwidthMode) {
            ConferenceBandwidthMode.HIGH -> when {
                peerCount <= 1  -> 1200
                peerCount <= 4  -> 800
                peerCount <= 8  -> 500
                else            -> 300
            }
            ConferenceBandwidthMode.SAVE -> when {
                peerCount <= 4  -> 200
                peerCount <= 8  -> 100
                else            -> 80
            }
            ConferenceBandwidthMode.AUTO -> when {
                peerCount <= 1  -> 800
                peerCount <= 2  -> 600
                peerCount <= 4  -> 400
                peerCount <= 8  -> 250
                peerCount <= 14 -> 150
                else            -> 100
            }
        }

        // Застосовуємо bitrate до кожного RtpSender локального відеотреку
        applyBitrateToAllSenders(targetKbps)

        Log.d(TAG, "📊 Conference bitrate adapted: ${peerCount} peers → ${targetKbps} kbps (mode: $bandwidthMode)")
    }

    /**
     * Встановлює домінуючого спікера.
     * Його відео отримує повний bitrate; всі інші знижуються на 30%.
     */
    fun setDominantSpeaker(userId: Long?) {
        dominantSpeakerId = userId
        if (userId == null) return

        // Пріоритизуємо відео домінуючого спікера через SetParameters
        peerConnections.forEach { (peerId, pc) ->
            val boost = (peerId == userId)
            pc.senders.forEach { sender ->
                if (sender.track()?.kind() == MediaStreamTrack.VIDEO_TRACK_KIND) {
                    applyBitrateToSender(sender, if (boost) 800 else 200)
                }
            }
        }
        Log.d(TAG, "🎤 Dominant speaker set: $userId")
    }

    /**
     * Вимикає відеотрек учасника [userId] (off-screen оптимізація).
     * Зберігає userId у [pausedVideoUsers] для подальшого відновлення.
     */
    fun pauseVideoForPeer(userId: Long) {
        val stream = remoteStreams[userId] ?: return
        stream.videoTracks.firstOrNull()?.setEnabled(false)
        pausedVideoUsers.add(userId)
        Log.d(TAG, "⏸️ Video paused for off-screen peer $userId")
    }

    /**
     * Вмикає відеотрек учасника [userId] (коли він з'являється на екрані).
     */
    fun resumeVideoForPeer(userId: Long) {
        val stream = remoteStreams[userId] ?: return
        stream.videoTracks.firstOrNull()?.setEnabled(true)
        pausedVideoUsers.remove(userId)
        Log.d(TAG, "▶️ Video resumed for peer $userId")
    }

    /**
     * Оновлює список видимих учасників.
     * Учасники, що не входять до [visibleUserIds], отримують off-screen оптимізацію.
     * Обмеження: вимикаємо відео лише коли є 5+ учасників (менше — нема сенсу).
     */
    fun updateVisibleParticipants(visibleUserIds: Set<Long>) {
        if (peerConnections.size < 5) return // off-screen оптимізація тільки для великих кімнат

        val allPeers = peerConnections.keys.toSet()
        val shouldPause = allPeers - visibleUserIds
        val shouldResume = visibleUserIds.intersect(pausedVideoUsers)

        shouldPause.forEach { pauseVideoForPeer(it) }
        shouldResume.forEach { resumeVideoForPeer(it) }
    }

    // ─── Bitrate helpers ─────────────────────────────────────────────────────

    private fun applyBitrateToAllSenders(targetKbps: Int) {
        peerConnections.values.forEach { pc ->
            pc.senders.forEach { sender ->
                if (sender.track()?.kind() == MediaStreamTrack.VIDEO_TRACK_KIND) {
                    applyBitrateToSender(sender, targetKbps)
                }
            }
        }
    }

    private fun applyBitrateToSender(sender: RtpSender, targetKbps: Int) {
        try {
            val params = sender.parameters
            if (params.encodings.isEmpty()) return
            params.encodings.forEach { encoding ->
                encoding.maxBitrateBps = targetKbps * 1000
                encoding.minBitrateBps = (targetKbps * 0.3 * 1000).toInt()
            }
            sender.parameters = params
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply bitrate to sender: ${e.message}")
        }
    }

    // ─── Очистка ──────────────────────────────────────────────────────────────

    fun close() {
        stopConferenceOptimization()
        optimizationScope.cancel()
        pausedVideoUsers.clear()
        Log.d(TAG, "🧹 Closing GroupWebRTCManager, peers: ${peerConnections.size}")

        // Закрыть все PeerConnections
        peerConnections.forEach { (userId, pc) ->
            try {
                pc.close()
                Log.d(TAG, "Closed PeerConnection for peer $userId")
            } catch (e: Exception) {
                Log.w(TAG, "Error closing PeerConnection for $userId: ${e.message}")
            }
        }
        peerConnections.clear()
        peerSenders.clear()
        remoteStreams.clear()

        // Остановить камеру
        try {
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping video capturer: ${e.message}")
        }

        // Освободить ресурсы в правильном порядке:
        // 1. Сначала dispose стрим — он вызывает removeTrack() внутри.
        //    Делаем это ДО dispose отдельных треков, иначе removeTrack()
        //    получит уже освобождённый трек и бросит IllegalStateException.
        try { localMediaStream?.dispose() } catch (e: Exception) {
            Log.w(TAG, "MediaStream dispose: ${e.message}")
        }
        // 2. Теперь безопасно освободить треки
        try { localAudioTrack?.dispose() } catch (e: Exception) {
            Log.w(TAG, "AudioTrack dispose: ${e.message}")
        }
        try { localVideoTrack?.dispose() } catch (e: Exception) {
            Log.w(TAG, "VideoTrack dispose: ${e.message}")
        }
        // 3. Освободить видео-пайплайн (зависит от треков)
        try { videoSource?.dispose() } catch (e: Exception) {
            Log.w(TAG, "VideoSource dispose: ${e.message}")
        }
        try { surfaceTextureHelper?.dispose() } catch (e: Exception) {
            Log.w(TAG, "SurfaceTextureHelper dispose: ${e.message}")
        }

        videoCapturer = null
        videoSource = null
        surfaceTextureHelper = null
        localAudioTrack = null
        localVideoTrack = null
        localMediaStream = null
        isCapturing = false

        isInitialized = false
        Log.d(TAG, "✅ GroupWebRTCManager closed")
    }
}

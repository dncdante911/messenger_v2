package com.worldmates.messenger.network

import android.content.Context
import android.util.Log
import org.webrtc.*
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * 📹 Качество видео для разных условий сети
 */
enum class VideoQuality(
    val width: Int,
    val height: Int,
    val fps: Int,
    val minBitrate: Int,  // Кбит/с
    val maxBitrate: Int,  // Кбит/с
    val label: String
) {
    LOW(320, 240, 15, 100, 200, "Низкое (240p)"),             // Для очень медленного интернета
    MEDIUM(640, 480, 24, 300, 600, "Среднее (480p)"),         // Для мобильного интернета
    HIGH(1280, 720, 30, 800, 1500, "Высокое (720p)"),         // Стандартное качество (стабильное)
    FULL_HD(1920, 1080, 30, 1500, 2500, "Full HD (1080p)")    // Для быстрого WiFi
}

/**
 * WebRTCManager - управление WebRTC соединениями для аудио/видео вызовов
 * Поддерживает личные вызовы (1-на-1) и групповые вызовы
 */
class WebRTCManager(private val context: Context) {

    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private var localMediaStream: MediaStream? = null
    private var remoteMediaStream: MediaStream? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null  // ✅ Окремий трек для remote video
    private var videoCapturer: CameraVideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null  // ✅ Зберігаємо для правильного cleanup

    // 📹 Текущее качество видео (по умолчанию HIGH - 720p)
    private var currentVideoQuality: VideoQuality = VideoQuality.HIGH

    private var iceServers: List<PeerConnection.IceServer> = createDefaultIceServers()

    companion object {
        private const val TAG = "WebRTCManager"

        // 🔐 TURN Server Credentials (worldmates.club)
        private const val TURN_SECRET = "ad8a76d057d6ba0d6fd79bbc84504e320c8538b92db5c9b84fc3bd18d1c511b9"
        private const val TURN_REALM = "worldmates.club"
        private const val TURN_IP_1 = "195.22.131.11"
        private const val TURN_IP_2 = "46.232.232.38"

        /**
         * 🔐 Генерирует TURN credentials используя HMAC-SHA1
         * Время-ограниченные credentials для безопасности
         */
        private fun generateTurnCredentials(userId: String = "android_user"): Pair<String, String> {
            val timestamp = (System.currentTimeMillis() / 1000) + 86400 // +24 часа
            val username = "$timestamp:$userId"

            // HMAC-SHA1 для credential
            val credential = try {
                val mac = javax.crypto.Mac.getInstance("HmacSHA1")
                val secretKey = javax.crypto.spec.SecretKeySpec(TURN_SECRET.toByteArray(), "HmacSHA1")
                mac.init(secretKey)
                val hash = mac.doFinal(username.toByteArray())
                android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate TURN credential", e)
                ""
            }

            return Pair(username, credential)
        }

        /**
         * 📡 Создает ICE серверы по умолчанию включая TURN
         */
        private fun createDefaultIceServers(): List<PeerConnection.IceServer> {
            val servers = mutableListOf<PeerConnection.IceServer>()

            // 1. STUN серверы Google (бесплатные, для NAT traversal)
            servers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
            servers.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer())
            servers.add(PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer())

            // 2. TURN серверы WorldMates (с credentials для relay)
            val (username, credential) = generateTurnCredentials()

            if (credential.isNotEmpty()) {
                // TURN UDP (основной - порт 3478)
                servers.add(
                    PeerConnection.IceServer.builder("turn:$TURN_IP_1:3478?transport=udp")
                        .setUsername(username)
                        .setPassword(credential)
                        .createIceServer()
                )
                servers.add(
                    PeerConnection.IceServer.builder("turn:$TURN_IP_2:3478?transport=udp")
                        .setUsername(username)
                        .setPassword(credential)
                        .createIceServer()
                )

                // TURN TCP (fallback для строгих файрволов)
                servers.add(
                    PeerConnection.IceServer.builder("turn:$TURN_IP_1:3478?transport=tcp")
                        .setUsername(username)
                        .setPassword(credential)
                        .createIceServer()
                )
                servers.add(
                    PeerConnection.IceServer.builder("turn:$TURN_IP_2:3478?transport=tcp")
                        .setUsername(username)
                        .setPassword(credential)
                        .createIceServer()
                )

                // TURNS TLS (безопасный - порт 5349)
                servers.add(
                    PeerConnection.IceServer.builder("turns:$TURN_IP_1:5349?transport=tcp")
                        .setUsername(username)
                        .setPassword(credential)
                        .createIceServer()
                )
                servers.add(
                    PeerConnection.IceServer.builder("turns:$TURN_IP_2:5349?transport=tcp")
                        .setUsername(username)
                        .setPassword(credential)
                        .createIceServer()
                )

                Log.d(TAG, "✅ Created ${servers.size} ICE servers (including TURN with credentials)")
            } else {
                Log.w(TAG, "⚠️ TURN credentials generation failed, using STUN only")
            }

            return servers
        }

        /**
         * Получить EGL контекст для инициализации SurfaceViewRenderer
         * Публичная функция для доступа из других классов
         */
        fun getEglContext(): EglBase.Context {
            return EglBaseProvider.context
        }

        // Помощник для инициализации EGL контекста
        object EglBaseProvider {
            private var eglBase: EglBase? = null

            val context: EglBase.Context
                get() {
                    if (eglBase == null) {
                        eglBase = EglBase.create()
                    }
                    return eglBase!!.eglBaseContext
                }

            fun release() {
                eglBase?.release()
                eglBase = null
            }
        }
    }

    /**
     * Установить ICE servers (включая TURN с credentials от сервера)
     * Вызывается из CallsViewModel при получении credentials от сервера
     */
    fun setIceServers(servers: List<PeerConnection.IceServer>) {
        iceServers = servers
        Log.d("WebRTCManager", "ICE servers updated: ${servers.size} servers")
    }

    var onIceCandidateListener: ((IceCandidate) -> Unit)? = null
    var onTrackListener: ((MediaStream) -> Unit)? = null
    // ✅ Callback для renegotiation - вызывается когда добавляется/удаляется track
    var onRenegotiationNeededListener: (() -> Unit)? = null
    var onRemoveTrackListener: (() -> Unit)? = null
    var onConnectionStateChangeListener: ((PeerConnection.PeerConnectionState) -> Unit)? = null
    var onIceConnectionStateChangeListener: ((PeerConnection.IceConnectionState) -> Unit)? = null

    init {
        initializePeerConnectionFactory()
    }

    private fun initializePeerConnectionFactory() {
        try {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context)
                    .setEnableInternalTracer(true)
                    .setFieldTrials("")
                    .createInitializationOptions()
            )

            // ✅ УЛУЧШЕННАЯ аудио конфигурация для надёжной работы на Android 11+
            val audioDeviceModule = JavaAudioDeviceModule.builder(context)
                .setUseHardwareAcousticEchoCanceler(true)  // ✅ Аппаратное эхоподавление
                .setUseHardwareNoiseSuppressor(true)       // ✅ Аппаратное шумоподавление
                .setAudioRecordErrorCallback(object : JavaAudioDeviceModule.AudioRecordErrorCallback {
                    override fun onWebRtcAudioRecordInitError(errorMessage: String?) {
                        Log.e(TAG, "🎤 Audio record init error: $errorMessage")
                    }
                    override fun onWebRtcAudioRecordStartError(
                        errorCode: JavaAudioDeviceModule.AudioRecordStartErrorCode?,
                        errorMessage: String?
                    ) {
                        Log.e(TAG, "🎤 Audio record start error [$errorCode]: $errorMessage")
                    }
                    override fun onWebRtcAudioRecordError(errorMessage: String?) {
                        Log.e(TAG, "🎤 Audio record error: $errorMessage")
                    }
                })
                .setAudioTrackErrorCallback(object : JavaAudioDeviceModule.AudioTrackErrorCallback {
                    override fun onWebRtcAudioTrackInitError(errorMessage: String?) {
                        Log.e(TAG, "🔊 Audio track init error: $errorMessage")
                    }
                    override fun onWebRtcAudioTrackStartError(
                        errorCode: JavaAudioDeviceModule.AudioTrackStartErrorCode?,
                        errorMessage: String?
                    ) {
                        Log.e(TAG, "🔊 Audio track start error [$errorCode]: $errorMessage")
                    }
                    override fun onWebRtcAudioTrackError(errorMessage: String?) {
                        Log.e(TAG, "🔊 Audio track error: $errorMessage")
                    }
                })
                .setAudioRecordStateCallback(object : JavaAudioDeviceModule.AudioRecordStateCallback {
                    override fun onWebRtcAudioRecordStart() {
                        Log.d(TAG, "🎤 Audio recording started")
                    }
                    override fun onWebRtcAudioRecordStop() {
                        Log.d(TAG, "🎤 Audio recording stopped")
                    }
                })
                .setAudioTrackStateCallback(object : JavaAudioDeviceModule.AudioTrackStateCallback {
                    override fun onWebRtcAudioTrackStart() {
                        Log.d(TAG, "🔊 Audio playback started")
                    }
                    override fun onWebRtcAudioTrackStop() {
                        Log.d(TAG, "🔊 Audio playback stopped")
                    }
                })
                .createAudioDeviceModule()

            // ✅ Создаём encoder factory с приоритетом H.264 (аппаратное ускорение)
            // H.264 поддерживается на 99% устройств и использует GPU
            val hwEncoderFactory = HardwareVideoEncoderFactory(
                EglBaseProvider.context,
                true,  // enableIntelVp8Encoder
                true   // enableH264HighProfile - ключевой параметр для качества!
            )
            val swEncoderFactory = SoftwareVideoEncoderFactory()

            // Комбинируем: сначала H.264 (HW), потом VP8/VP9 (SW как fallback)
            val encoderFactory = object : VideoEncoderFactory {
                override fun createEncoder(info: VideoCodecInfo): VideoEncoder? {
                    // Сначала пробуем аппаратный кодек (H.264)
                    val hwEncoder = hwEncoderFactory.createEncoder(info)
                    if (hwEncoder != null) {
                        Log.d(TAG, "🎬 Using HARDWARE encoder for ${info.name}")
                        return hwEncoder
                    }
                    // Fallback на программный
                    Log.d(TAG, "🎬 Using SOFTWARE encoder for ${info.name}")
                    return swEncoderFactory.createEncoder(info)
                }

                override fun getSupportedCodecs(): Array<VideoCodecInfo> {
                    val hwCodecs = hwEncoderFactory.supportedCodecs.toMutableList()
                    val swCodecs = swEncoderFactory.supportedCodecs.toList()

                    // ✅ H.264 должен быть ПЕРВЫМ в списке для приоритета
                    val sortedCodecs = hwCodecs.sortedByDescending {
                        it.name.equals("H264", ignoreCase = true)
                    }.toMutableList()

                    // Добавляем SW кодеки которых нет в HW
                    swCodecs.forEach { swCodec ->
                        if (sortedCodecs.none { it.name == swCodec.name }) {
                            sortedCodecs.add(swCodec)
                        }
                    }

                    Log.d(TAG, "🎬 Supported codecs (prioritized): ${sortedCodecs.map { it.name }}")
                    return sortedCodecs.toTypedArray()
                }
            }

            peerConnectionFactory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(audioDeviceModule)
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(EglBaseProvider.context))
                .setVideoEncoderFactory(encoderFactory)
                .setOptions(PeerConnectionFactory.Options().apply {
                    disableEncryption = false
                    networkIgnoreMask = 0
                })
                .createPeerConnectionFactory()

            Log.d(TAG, "✅ PeerConnectionFactory initialized with H.264 priority encoder")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize PeerConnectionFactory", e)
        }
    }

    /**
     * Создать PeerConnection для вызова
     */
    fun createPeerConnection(): PeerConnection? {
        // ✅ Обновить TURN credentials перед каждым звонком
        iceServers = createDefaultIceServers()
        Log.d(TAG, "📡 ICE servers refreshed: ${iceServers.size} servers configured")

        return try {
            val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
                // ✅ КРИТИЧНО для надёжного соединения через NAT
                iceTransportsType = PeerConnection.IceTransportsType.ALL  // STUN + TURN
                bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
                rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
                tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED  // ✅ TCP fallback
                candidateNetworkPolicy = PeerConnection.CandidateNetworkPolicy.ALL
                // ✅ Увеличенные таймауты для медленных сетей
                iceConnectionReceivingTimeout = 10000  // 10 секунд
                iceBackupCandidatePairPingInterval = 5000  // 5 секунд
            }

            peerConnection = peerConnectionFactory.createPeerConnection(
                rtcConfig,
                object : PeerConnection.Observer {
                    override fun onSignalingChange(newState: PeerConnection.SignalingState) {
                        Log.d("WebRTCManager", "SignalingState: $newState")
                    }

                    override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
                        Log.d("WebRTCManager", "IceConnectionState: $newState")
                        onIceConnectionStateChangeListener?.invoke(newState)
                    }

                    override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                        Log.d("WebRTCManager", "PeerConnectionState: $newState")
                        onConnectionStateChangeListener?.invoke(newState)
                    }

                    override fun onStandardizedIceConnectionChange(newState: PeerConnection.IceConnectionState) {}

                    override fun onIceConnectionReceivingChange(receiving: Boolean) {}

                    override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) {
                        Log.d("WebRTCManager", "IceGatheringState: $newState")
                    }

                    override fun onIceCandidate(candidate: IceCandidate) {
                        Log.d("WebRTCManager", "IceCandidate: ${candidate.sdp}")
                        onIceCandidateListener?.invoke(candidate)
                    }

                    override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) {}

                    override fun onAddStream(stream: MediaStream) {
                        // Deprecated in Unified Plan - use onTrack instead
                    }

                    override fun onRemoveStream(stream: MediaStream) {
                        // Deprecated in Unified Plan
                    }

                    override fun onTrack(transceiver: RtpTransceiver) {
                        val track = transceiver.receiver.track()
                        val direction = transceiver.direction
                        val mid = transceiver.mid
                        Log.d(TAG, "📡 ========== REMOTE TRACK RECEIVED ==========")
                        Log.d(TAG, "📡 Track kind: ${track?.kind()}")
                        Log.d(TAG, "📡 Track ID: ${track?.id()}")
                        Log.d(TAG, "📡 Track enabled: ${track?.enabled()}")
                        Log.d(TAG, "📡 Transceiver direction: $direction")
                        Log.d(TAG, "📡 Transceiver MID: $mid")
                        Log.d(TAG, "📡 ============================================")

                        // ✅ Створити новий remote stream кожен раз для правильної роботи LiveData
                        // LiveData не оновлює UI якщо постити той самий об'єкт
                        val newRemoteStream = peerConnectionFactory.createLocalMediaStream("REMOTE_STREAM_${System.currentTimeMillis()}")

                        // Копіюємо існуючі треки в новий stream
                        remoteMediaStream?.audioTracks?.forEach { audioTrack ->
                            newRemoteStream.addTrack(audioTrack)
                        }
                        remoteMediaStream?.videoTracks?.forEach { videoTrack ->
                            newRemoteStream.addTrack(videoTrack)
                        }

                        // Добавить track в новый stream
                        track?.let {
                            when (it) {
                                is AudioTrack -> {
                                    it.setEnabled(true)
                                    newRemoteStream.addTrack(it)
                                    Log.d("WebRTCManager", "📡 Remote AUDIO track added and enabled")
                                }
                                is VideoTrack -> {
                                    it.setEnabled(true)
                                    remoteVideoTrack = it  // ✅ Зберігаємо для відстеження
                                    newRemoteStream.addTrack(it)
                                    Log.d("WebRTCManager", "📡 Remote VIDEO track added - id: ${it.id()}, enabled: ${it.enabled()}")
                                }
                            }
                        }

                        // Оновити reference і сповістити listener
                        remoteMediaStream = newRemoteStream

                        // ✅ КРИТИЧНО: Сповістити listener якщо є відео трек
                        if (newRemoteStream.videoTracks.isNotEmpty()) {
                            Log.d("WebRTCManager", "📡 Notifying listener about remote video - tracks: ${newRemoteStream.videoTracks.size}")
                            onTrackListener?.invoke(newRemoteStream)
                        } else if (newRemoteStream.audioTracks.isNotEmpty()) {
                            // Сповістити і для аудіо, щоб UI знав про з'єднання
                            Log.d("WebRTCManager", "📡 Notifying listener about remote audio - tracks: ${newRemoteStream.audioTracks.size}")
                            onTrackListener?.invoke(newRemoteStream)
                        }
                    }

                    override fun onDataChannel(dataChannel: DataChannel) {}
                    override fun onRenegotiationNeeded() {
                        Log.d(TAG, "🔄 Renegotiation needed - notifying listener")
                        onRenegotiationNeededListener?.invoke()
                    }
                }
            )

            peerConnection
        } catch (e: Exception) {
            Log.e("WebRTCManager", "Failed to create PeerConnection", e)
            null
        }
    }

    /**
     * Создать локальный медиа поток (аудио + опционально видео)
     */
    fun createLocalMediaStream(
        audioEnabled: Boolean = true,
        videoEnabled: Boolean = false
    ): MediaStream? {
        return try {
            val mediaStream = peerConnectionFactory.createLocalMediaStream("LOCAL_STREAM")

            // Создать аудио трек
            if (audioEnabled) {
                val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
                localAudioTrack = peerConnectionFactory.createAudioTrack("audio_track", audioSource)
                localAudioTrack?.let {
                    it.setEnabled(true)  // ✅ Явно включить аудио трек
                    mediaStream.addTrack(it)
                    // ✅ UNIFIED_PLAN: addTrack вместо addStream
                    peerConnection?.addTrack(it, listOf("LOCAL_STREAM"))
                }
                Log.d("WebRTCManager", "Audio track added and enabled")
            }

            // Создать видео трек (если нужно)
            if (videoEnabled) {
                // Создать CameraVideoCapturer для доступа к камере
                videoCapturer = createCameraVideoCapturer()
                videoSource = peerConnectionFactory.createVideoSource(videoCapturer?.isScreencast ?: false)

                // ✅ Зберігаємо SurfaceTextureHelper для правильного cleanup
                surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", EglBaseProvider.context)

                // Запустити камеру
                videoCapturer?.initialize(
                    surfaceTextureHelper,
                    context,
                    videoSource?.capturerObserver
                )
                videoCapturer?.startCapture(currentVideoQuality.width, currentVideoQuality.height, currentVideoQuality.fps)

                localVideoTrack = peerConnectionFactory.createVideoTrack("video_track", videoSource)
                localVideoTrack?.let {
                    it.setEnabled(true)  // ✅ Явно включить видео трек
                    mediaStream.addTrack(it)
                    // ✅ UNIFIED_PLAN: addTrack вместо addStream
                    peerConnection?.addTrack(it, listOf("LOCAL_STREAM"))
                    // ✅ Применить битрейт после добавления трека
                    applyVideoBitrate(currentVideoQuality)
                }
                Log.d("WebRTCManager", "Video track added with camera capturer and enabled, bitrate: ${currentVideoQuality.maxBitrate} kbps")
            }

            localMediaStream = mediaStream
            mediaStream
        } catch (e: Exception) {
            Log.e("WebRTCManager", "Failed to create local media stream", e)
            null
        }
    }

    /**
     * Создать offer для инициатора вызова
     */
    fun createOffer(onSuccess: (SessionDescription) -> Unit, onError: (String) -> Unit) {
        Log.d(TAG, "📤 Creating offer...")
        Log.d(TAG, "📤 Local audio track: ${localAudioTrack != null}, enabled: ${localAudioTrack?.enabled()}")
        Log.d(TAG, "📤 Local video track: ${localVideoTrack != null}, enabled: ${localVideoTrack?.enabled()}")

        peerConnection?.createOffer(
            object : SdpObserver {
                override fun onCreateSuccess(sessionDescription: SessionDescription) {
                    // ✅ Логируем SDP для диагностики
                    val hasAudio = sessionDescription.description.contains("m=audio")
                    val hasVideo = sessionDescription.description.contains("m=video")
                    Log.d(TAG, "📤 Offer created - hasAudio: $hasAudio, hasVideo: $hasVideo")
                    if (!hasVideo) {
                        Log.w(TAG, "⚠️ WARNING: Offer does NOT contain video m-line!")
                    }

                    peerConnection?.setLocalDescription(
                        object : SdpObserver {
                            override fun onCreateSuccess(p0: SessionDescription?) {}
                            override fun onSetSuccess() {
                                Log.d(TAG, "📤 Local description set successfully")
                                onSuccess(sessionDescription)
                            }
                            override fun onCreateFailure(p0: String?) {}
                            override fun onSetFailure(error: String?) {
                                onError(error ?: "Failed to set local description")
                            }
                        },
                        sessionDescription
                    )
                }

                override fun onCreateFailure(error: String?) {
                    Log.e(TAG, "❌ Failed to create offer: $error")
                    onError(error ?: "Failed to create offer")
                }

                override fun onSetSuccess() {}
                override fun onSetFailure(p0: String?) {}
            },
            MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            }
        )
    }

    /**
     * Создать answer для получателя вызова
     */
    fun createAnswer(onSuccess: (SessionDescription) -> Unit, onError: (String) -> Unit) {
        Log.d(TAG, "📥 Creating answer...")
        Log.d(TAG, "📥 Local audio track: ${localAudioTrack != null}, enabled: ${localAudioTrack?.enabled()}")
        Log.d(TAG, "📥 Local video track: ${localVideoTrack != null}, enabled: ${localVideoTrack?.enabled()}")

        peerConnection?.createAnswer(
            object : SdpObserver {
                override fun onCreateSuccess(sessionDescription: SessionDescription) {
                    // ✅ Логируем SDP для диагностики
                    val hasAudio = sessionDescription.description.contains("m=audio")
                    val hasVideo = sessionDescription.description.contains("m=video")
                    Log.d(TAG, "📥 Answer created - hasAudio: $hasAudio, hasVideo: $hasVideo")
                    if (!hasVideo) {
                        Log.w(TAG, "⚠️ WARNING: Answer does NOT contain video m-line!")
                    }

                    peerConnection?.setLocalDescription(
                        object : SdpObserver {
                            override fun onCreateSuccess(p0: SessionDescription?) {}
                            override fun onSetSuccess() {
                                Log.d(TAG, "📥 Local description (answer) set successfully")
                                onSuccess(sessionDescription)
                            }
                            override fun onCreateFailure(p0: String?) {}
                            override fun onSetFailure(error: String?) {
                                onError(error ?: "Failed to set local description")
                            }
                        },
                        sessionDescription
                    )
                }

                override fun onCreateFailure(error: String?) {
                    Log.e(TAG, "❌ Failed to create answer: $error")
                    onError(error ?: "Failed to create answer")
                }

                override fun onSetSuccess() {}
                override fun onSetFailure(p0: String?) {}
            },
            MediaConstraints().apply {
                // ✅ КРИТИЧНО: Указать что хотим получать audio/video!
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            }
        )
    }

    /**
     * Установить remote description (offer или answer от другого юзера)
     */
    fun setRemoteDescription(sessionDescription: SessionDescription, onError: (String) -> Unit) {
        // ✅ Логируем remote SDP для диагностики
        val hasAudio = sessionDescription.description.contains("m=audio")
        val hasVideo = sessionDescription.description.contains("m=video")
        Log.d(TAG, "📩 Setting remote description (${sessionDescription.type})")
        Log.d(TAG, "📩 Remote SDP - hasAudio: $hasAudio, hasVideo: $hasVideo")
        if (!hasVideo) {
            Log.w(TAG, "⚠️ WARNING: Remote SDP does NOT contain video m-line!")
        }

        peerConnection?.setRemoteDescription(
            object : SdpObserver {
                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onSetSuccess() {
                    Log.d(TAG, "✅ Remote description set successfully")
                    // ✅ После установки remote description проверяем transceivers
                    peerConnection?.transceivers?.forEachIndexed { index, transceiver ->
                        Log.d(TAG, "📡 Transceiver[$index]: mid=${transceiver.mid}, " +
                                "direction=${transceiver.direction}, " +
                                "currentDirection=${transceiver.currentDirection}, " +
                                "receiverTrack=${transceiver.receiver.track()?.kind()}")
                    }
                }
                override fun onCreateFailure(p0: String?) {}
                override fun onSetFailure(error: String?) {
                    Log.e(TAG, "❌ Failed to set remote description: $error")
                    onError(error ?: "Failed to set remote description")
                }
            },
            sessionDescription
        )
    }

    /**
     * Добавить ICE candidate
     */
    fun addIceCandidate(iceCandidate: IceCandidate) {
        peerConnection?.addIceCandidate(iceCandidate)
    }

    /**
     * Закрыть соединение
     */
    fun close() {
        try {
            // ✅ Остановить камеру
            try {
                videoCapturer?.stopCapture()
            } catch (e: Exception) {
                Log.w("WebRTCManager", "Error stopping capture: ${e.message}")
            }
            videoCapturer?.dispose()
            videoCapturer = null

            // ✅ Очистить SurfaceTextureHelper
            surfaceTextureHelper?.dispose()
            surfaceTextureHelper = null

            // Очистить видео источник
            videoSource?.dispose()
            videoSource = null

            // ✅ Очистить remote video track
            remoteVideoTrack = null

            peerConnection?.close()
            peerConnection = null

            // Очистить локальный stream
            localMediaStream?.let {
                it.audioTracks.forEach { track -> track.dispose() }
                it.videoTracks.forEach { track -> track.dispose() }
            }
            localMediaStream = null
            localVideoTrack = null
            localAudioTrack = null

            // Очистить remote stream
            remoteMediaStream?.let {
                it.audioTracks.forEach { track -> track.dispose() }
                it.videoTracks.forEach { track -> track.dispose() }
            }
            remoteMediaStream = null

            Log.d("WebRTCManager", "PeerConnection closed")
        } catch (e: Exception) {
            Log.e("WebRTCManager", "Error closing PeerConnection", e)
        }
    }

    /**
     * Отключить/включить микрофон
     */
    fun setAudioEnabled(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
        Log.d("WebRTCManager", "Audio enabled: $enabled")
    }

    /**
     * Отключить/включить видео
     * Если видео трек не существует, просто выключаем (не создаем новый)
     */
    fun setVideoEnabled(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
        Log.d("WebRTCManager", "Video enabled: $enabled (track exists: ${localVideoTrack != null})")
    }

    /**
     * 📹 Включить видео динамически (создать камеру и видеотрек если их нет)
     * Возвращает true если видео успешно включено
     *
     * ✅ ВИПРАВЛЕНО: Правильно перезапускає камеру після disableVideo()
     */
    fun enableVideo(): Boolean {
        // Создаем видео если PeerConnection существует
        if (peerConnection == null) {
            Log.e("WebRTCManager", "Cannot enable video: PeerConnection is null")
            return false
        }

        return try {
            // ✅ ВИПРАВЛЕНО: Якщо відеотрек і камера існують - перезапустити камеру
            if (localVideoTrack != null && videoCapturer != null) {
                Log.d("WebRTCManager", "📹 Restarting existing camera...")
                try {
                    videoCapturer?.startCapture(currentVideoQuality.width, currentVideoQuality.height, currentVideoQuality.fps)
                    localVideoTrack?.setEnabled(true)
                    Log.d("WebRTCManager", "✅ Camera restarted successfully")
                    return true
                } catch (e: Exception) {
                    Log.w("WebRTCManager", "Failed to restart camera, will recreate: ${e.message}")
                    // Якщо не вдалося перезапустити - очистити і створити заново
                    cleanupVideoResources()
                }
            }

            // ✅ Якщо тільки відеотрек існує (камера була disposed) - очистити і створити заново
            if (localVideoTrack != null && videoCapturer == null) {
                Log.d("WebRTCManager", "📹 Video track exists but camera is null, cleaning up...")
                cleanupVideoResources()
            }

            // 1. Создать CameraVideoCapturer
            Log.d("WebRTCManager", "📹 Creating new camera capturer...")
            videoCapturer = createCameraVideoCapturer()
            if (videoCapturer == null) {
                Log.e("WebRTCManager", "Failed to create camera capturer")
                return false
            }

            // 2. Создать VideoSource
            videoSource = peerConnectionFactory.createVideoSource(videoCapturer?.isScreencast ?: false)

            // 3. ✅ Создать SurfaceTextureHelper
            surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", EglBaseProvider.context)

            // 4. Инициализировать и запустить камеру
            videoCapturer?.initialize(
                surfaceTextureHelper,
                context,
                videoSource?.capturerObserver
            )
            videoCapturer?.startCapture(currentVideoQuality.width, currentVideoQuality.height, currentVideoQuality.fps)

            // 5. Создать видеотрек
            localVideoTrack = peerConnectionFactory.createVideoTrack("video_track_${System.currentTimeMillis()}", videoSource)
            localVideoTrack?.setEnabled(true)

            // 6. Добавить видеотрек в localMediaStream
            localMediaStream?.addTrack(localVideoTrack!!)

            // 7. Добавить видеотрек в PeerConnection (UNIFIED_PLAN)
            peerConnection?.addTrack(localVideoTrack!!, listOf("LOCAL_STREAM"))

            // 8. ✅ Применить битрейт для качества
            applyVideoBitrate(currentVideoQuality)

            Log.d("WebRTCManager", "✅ Video enabled dynamically - camera started, bitrate: ${currentVideoQuality.maxBitrate} kbps")
            true
        } catch (e: Exception) {
            Log.e("WebRTCManager", "Failed to enable video dynamically", e)
            false
        }
    }

    /**
     * 📹 Очистити відео ресурси для повторного створення
     */
    private fun cleanupVideoResources() {
        try {
            localVideoTrack?.setEnabled(false)
            // НЕ dispose трек, тільки видаляємо з stream
            localMediaStream?.removeTrack(localVideoTrack)
            localVideoTrack = null

            try {
                videoCapturer?.stopCapture()
            } catch (e: Exception) {
                Log.w("WebRTCManager", "Error stopping capture: ${e.message}")
            }
            videoCapturer?.dispose()
            videoCapturer = null

            videoSource?.dispose()
            videoSource = null

            surfaceTextureHelper?.dispose()
            surfaceTextureHelper = null

            Log.d("WebRTCManager", "📹 Video resources cleaned up")
        } catch (e: Exception) {
            Log.e("WebRTCManager", "Error cleaning up video resources", e)
        }
    }

    /**
     * 📹 Выключить видео (остановить камеру, НЕ удалять ресурсы для возможности перезапуска)
     */
    fun disableVideo() {
        try {
            // Выключить трек (но не удалять)
            localVideoTrack?.setEnabled(false)

            // ✅ Остановить камеру для экономии батареи
            // НЕ вызываем dispose() чтобы можно было перезапустить
            try {
                videoCapturer?.stopCapture()
            } catch (e: InterruptedException) {
                Log.w("WebRTCManager", "Interrupted while stopping capture: ${e.message}")
            }

            Log.d("WebRTCManager", "📹 Video disabled, camera paused (can be restarted)")
        } catch (e: Exception) {
            Log.e("WebRTCManager", "Error disabling video", e)
        }
    }

    /**
     * 📹 Проверить есть ли видеотрек
     */
    fun hasVideoTrack(): Boolean = localVideoTrack != null

    /**
     * 📹 Получить текущее качество видео
     */
    fun getVideoQuality(): VideoQuality = currentVideoQuality

    /**
     * 📹 Изменить качество видео на лету
     * Перезапускает камеру с новым разрешением и применяет битрейт
     */
    fun setVideoQuality(quality: VideoQuality): Boolean {
        if (currentVideoQuality == quality) {
            Log.d(TAG, "Video quality already set to ${quality.label}")
            return true
        }

        currentVideoQuality = quality
        Log.d(TAG, "📹 Changing video quality to ${quality.label} (${quality.width}x${quality.height}@${quality.fps}fps, ${quality.minBitrate}-${quality.maxBitrate} kbps)")

        // Если камера уже запущена - перезапустить с новым качеством
        if (videoCapturer != null) {
            return try {
                videoCapturer?.stopCapture()
                videoCapturer?.startCapture(quality.width, quality.height, quality.fps)
                // ✅ Применить новый битрейт
                applyVideoBitrate(quality)
                Log.d(TAG, "✅ Video quality changed to ${quality.label}")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to change video quality", e)
                false
            }
        }

        return true
    }

    /**
     * 📹 Применить битрейт для видео через RtpSender
     * Это ключевая настройка для качества видео!
     */
    private fun applyVideoBitrate(quality: VideoQuality) {
        try {
            peerConnection?.senders?.forEach { sender ->
                if (sender.track()?.kind() == "video") {
                    val parameters = sender.parameters
                    if (parameters.encodings.isNotEmpty()) {
                        parameters.encodings.forEach { encoding ->
                            // ✅ Устанавливаем битрейт (в бит/с, не кбит/с!)
                            encoding.minBitrateBps = quality.minBitrate * 1000
                            encoding.maxBitrateBps = quality.maxBitrate * 1000
                            // ✅ Устанавливаем максимальный FPS
                            encoding.maxFramerate = quality.fps
                            // ✅ Отключаем degradation для лучшего качества при хорошей сети
                            encoding.scaleResolutionDownBy = 1.0
                        }
                        sender.parameters = parameters
                        Log.d(TAG, "✅ Video bitrate applied: ${quality.minBitrate}-${quality.maxBitrate} kbps, ${quality.fps} fps")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply video bitrate", e)
        }
    }

    /**
     * 📹 Применить битрейт после добавления видеотрека в PeerConnection
     * Вызывается из createLocalMediaStream и enableVideo
     */
    fun applyCurrentVideoBitrate() {
        applyVideoBitrate(currentVideoQuality)
    }

    /**
     * Получить локальный поток
     */
    fun getLocalMediaStream(): MediaStream? = localMediaStream

    /**
     * 📷 Создать CameraVideoCapturer (по умолчанию фронтальная камера)
     */
    private fun createCameraVideoCapturer(): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames

        // Попробовать фронтальную камеру сначала
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                val capturer = enumerator.createCapturer(deviceName, null)
                if (capturer != null) {
                    Log.d(TAG, "Using front camera: $deviceName")
                    return capturer
                }
            }
        }

        // Если нет фронтальной, попробовать заднюю
        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                val capturer = enumerator.createCapturer(deviceName, null)
                if (capturer != null) {
                    Log.d(TAG, "Using back camera: $deviceName")
                    return capturer
                }
            }
        }

        Log.e(TAG, "No camera found")
        return null
    }

    /**
     * 🔄 Переключить камеру (фронтальная ↔ задняя)
     */
    fun switchCamera() {
        videoCapturer?.let { capturer ->
            if (capturer is CameraVideoCapturer) {
                capturer.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
                    override fun onCameraSwitchDone(isFrontFacing: Boolean) {
                        Log.d(TAG, "Camera switched to ${if (isFrontFacing) "front" else "back"}")
                    }

                    override fun onCameraSwitchError(errorDescription: String?) {
                        Log.e(TAG, "Camera switch error: $errorDescription")
                    }
                })
            }
        } ?: run {
            Log.w(TAG, "Cannot switch camera - videoCapturer is null")
        }
    }

}

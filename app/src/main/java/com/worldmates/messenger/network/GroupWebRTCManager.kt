package com.worldmates.messenger.network

import android.content.Context
import android.util.Log
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
    }

    // PeerConnection для каждого удалённого участника
    private val peerConnections = mutableMapOf<Long, PeerConnection>()

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

        // Видео
        if (!audioOnly) {
            try {
                surfaceTextureHelper = SurfaceTextureHelper.create(
                    "GroupCaptureThread", WebRTCManager.getEglContext()
                )

                videoCapturer = createCameraCapturer()
                videoSource = peerConnectionFactory.createVideoSource(false)

                videoCapturer?.initialize(surfaceTextureHelper, context, videoSource!!.capturerObserver)
                videoCapturer?.startCapture(640, 480, 24)

                localVideoTrack = peerConnectionFactory.createVideoTrack("video_group", videoSource)
                localVideoTrack?.setEnabled(true)

                Log.d(TAG, "✅ Local video track created")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to create video capturer: ${e.message}")
            }
        }

        // Создать локальный медиа-поток
        localMediaStream = peerConnectionFactory.createLocalMediaStream("local_group_stream")
        localAudioTrack?.let { localMediaStream?.addTrack(it) }
        localVideoTrack?.let { localMediaStream?.addTrack(it) }

        // Добавить локальный поток во все существующие PeerConnections
        peerConnections.forEach { (_, pc) ->
            localMediaStream?.let { pc.addStream(it) }
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
            listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
            )
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
                streams?.firstOrNull()?.let { stream ->
                    Log.d(TAG, "✅ Track added from peer $userId via onAddTrack")
                    remoteStreams[userId] = stream
                    onRemoteStreamAdded?.invoke(userId, stream)
                }
            }
        }

        val pc = peerConnectionFactory.createPeerConnection(rtcConfig, observer)
        if (pc == null) {
            Log.e(TAG, "❌ Failed to create PeerConnection for peer $userId")
            return null
        }

        // Добавить локальный поток в новое соединение
        localMediaStream?.let { pc.addStream(it) }

        peerConnections[userId] = pc
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
     * Удалить PeerConnection участника (когда он вышел).
     */
    fun removePeer(userId: Long) {
        Log.d(TAG, "🔴 Removing peer $userId")
        peerConnections[userId]?.close()
        peerConnections.remove(userId)
        remoteStreams.remove(userId)
        onRemoteStreamRemoved?.invoke(userId)
    }

    // ─── Управление медиа ─────────────────────────────────────────────────────

    fun setAudioEnabled(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
        Log.d(TAG, "Audio ${if (enabled) "enabled" else "disabled"}")
    }

    fun setVideoEnabled(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
        Log.d(TAG, "Video ${if (enabled) "enabled" else "disabled"}")
    }

    fun isAudioEnabled(): Boolean = localAudioTrack?.enabled() == true
    fun isVideoEnabled(): Boolean = localVideoTrack?.enabled() == true

    fun getRemoteStream(userId: Long): MediaStream? = remoteStreams[userId]

    fun getConnectedPeerIds(): List<Long> = peerConnections.keys.toList()

    // ─── Очистка ──────────────────────────────────────────────────────────────

    fun close() {
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

        isInitialized = false
        Log.d(TAG, "✅ GroupWebRTCManager closed")
    }
}

package com.worldmates.messenger.network

import android.content.Context
import android.util.Log
import org.webrtc.*

/**
 * Manages WebRTC for channel livestreaming.
 *
 * Host mode:
 *   - Opens camera + mic
 *   - Creates one PeerConnection per viewer who joins
 *   - Exposes [localStream] for local camera preview
 *
 * Viewer mode:
 *   - Creates a single PeerConnection to the host
 *   - Receives remote video and exposes via [onRemoteStreamAdded]
 */
class LivestreamWebRTCManager(private val context: Context) {

    companion object {
        private const val TAG = "LivestreamWebRTC"
    }

    // ── Local media ────────────────────────────────────────────────────────────
    var localStream: MediaStream? = null
        private set

    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null

    // ── Peer connections (host has one per viewer; viewer has exactly one) ─────
    private val peerConnections = mutableMapOf<Long, PeerConnection>()  // key = userId
    private var factory: PeerConnectionFactory? = null

    // ── Recording ──────────────────────────────────────────────────────────────
    private val recorder = WebRTCStreamRecorder()

    // ── Callbacks ──────────────────────────────────────────────────────────────
    /** Host: ICE candidate for [toUserId] ready to send. */
    var onIceCandidateReady: ((toUserId: Long, candidate: IceCandidate) -> Unit)? = null
    /** Host: SDP offer for [toUserId] ready to send. */
    var onOfferReady: ((toUserId: Long, sdp: SessionDescription) -> Unit)? = null
    /** Viewer: SDP answer for host (toUserId = hostUserId) ready to send. */
    var onAnswerReady: ((toUserId: Long, sdp: SessionDescription) -> Unit)? = null
    /** Viewer: Remote video stream received from [fromUserId]. */
    var onRemoteStreamAdded: ((fromUserId: Long, stream: MediaStream) -> Unit)? = null
    /** Host: a viewer's PC connection state changed (for logging/display). */
    var onViewerConnectionStateChanged: ((viewerId: Long, state: String) -> Unit)? = null

    // ─────────────────────────────────────────────────────────────────────────
    // Host: setup local camera + mic
    // ─────────────────────────────────────────────────────────────────────────

    fun setupLocalCamera() {
        try {
            ensureFactory()
            val f = factory ?: return

            // Audio
            val audioConstraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            }
            val audioSource = f.createAudioSource(audioConstraints)
            localAudioTrack = f.createAudioTrack("ls_audio", audioSource)
            localAudioTrack?.setEnabled(true)

            // Video
            val capturer = createCameraVideoCapturer()
            if (capturer != null) {
                videoCapturer = capturer
                val eglContext = WebRTCManager.getEglContext()
                surfaceTextureHelper = SurfaceTextureHelper.create("LsCapture", eglContext)
                videoSource = f.createVideoSource(capturer.isScreencast)
                capturer.initialize(surfaceTextureHelper, context, videoSource!!.capturerObserver)
                capturer.startCapture(1280, 720, 30)
                localVideoTrack = f.createVideoTrack("ls_video", videoSource)
                localVideoTrack?.setEnabled(true)
            }

            // Build local stream
            val stream = f.createLocalMediaStream("ls_local")
            localAudioTrack?.let { stream.addTrack(it) }
            localVideoTrack?.let { stream.addTrack(it) }
            localStream = stream
            Log.d(TAG, "Local camera set up. videoTrack=${localVideoTrack != null}")
        } catch (e: Exception) {
            Log.e(TAG, "setupLocalCamera error", e)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Host: create offer for a viewer who just joined
    // ─────────────────────────────────────────────────────────────────────────

    fun createOfferForViewer(viewerId: Long, iceServers: List<PeerConnection.IceServer>) {
        try {
            val pc = createPeerConnection(viewerId, iceServers) ?: return
            localStream?.let { pc.addStream(it) }

            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
            }
            pc.createOffer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription) {
                    pc.setLocalDescription(LogSdpObserver("setLocal/$viewerId"), sdp)
                    onOfferReady?.invoke(viewerId, sdp)
                }
                override fun onSetSuccess() {}
                override fun onCreateFailure(error: String) { Log.e(TAG, "createOffer fail viewer=$viewerId: $error") }
                override fun onSetFailure(error: String) { Log.e(TAG, "setLocal fail viewer=$viewerId: $error") }
            }, constraints)
        } catch (e: Exception) {
            Log.e(TAG, "createOfferForViewer error", e)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Viewer: receive offer from host, create answer
    // ─────────────────────────────────────────────────────────────────────────

    fun handleOfferFromHost(hostUserId: Long, sdpOffer: String, iceServers: List<PeerConnection.IceServer>) {
        try {
            ensureFactory()  // Viewer never calls setupLocalCamera(), so factory must be initialised here
            val pc = createPeerConnection(hostUserId, iceServers) ?: return
            val offer = SessionDescription(SessionDescription.Type.OFFER, sdpOffer)
            pc.setRemoteDescription(LogSdpObserver("setRemote/$hostUserId"), offer)

            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            }
            pc.createAnswer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription) {
                    pc.setLocalDescription(LogSdpObserver("setLocal_ans/$hostUserId"), sdp)
                    onAnswerReady?.invoke(hostUserId, sdp)
                }
                override fun onSetSuccess() {}
                override fun onCreateFailure(error: String) { Log.e(TAG, "createAnswer fail: $error") }
                override fun onSetFailure(error: String) { Log.e(TAG, "setLocal_ans fail: $error") }
            }, constraints)
        } catch (e: Exception) {
            Log.e(TAG, "handleOfferFromHost error", e)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Host: receive answer from a viewer
    // ─────────────────────────────────────────────────────────────────────────

    fun handleAnswerFromViewer(viewerId: Long, sdpAnswer: String) {
        val pc = peerConnections[viewerId] ?: run {
            Log.w(TAG, "No PC for viewer $viewerId")
            return
        }
        val answer = SessionDescription(SessionDescription.Type.ANSWER, sdpAnswer)
        pc.setRemoteDescription(LogSdpObserver("setRemote_ans/$viewerId"), answer)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Add ICE candidate for a peer (host or viewer)
    // ─────────────────────────────────────────────────────────────────────────

    fun addIceCandidate(fromUserId: Long, sdpMid: String, sdpMLineIndex: Int, candidateSdp: String) {
        val pc = peerConnections[fromUserId] ?: run {
            Log.w(TAG, "No PC for user $fromUserId, dropping ICE candidate")
            return
        }
        pc.addIceCandidate(IceCandidate(sdpMid, sdpMLineIndex, candidateSdp))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Remove a viewer's peer connection (host side)
    // ─────────────────────────────────────────────────────────────────────────

    fun removePeer(userId: Long) {
        peerConnections.remove(userId)?.let {
            try { it.close() } catch (e: Exception) { /* ignore */ }
        }
        Log.d(TAG, "Removed peer $userId")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Flip between front/back camera (host only)
    // ─────────────────────────────────────────────────────────────────────────

    fun switchCamera() {
        (videoCapturer as? CameraVideoCapturer)?.switchCamera(null)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Enable / disable local tracks
    // ─────────────────────────────────────────────────────────────────────────

    fun setAudioEnabled(enabled: Boolean) { localAudioTrack?.setEnabled(enabled) }
    fun setVideoEnabled(enabled: Boolean) { localVideoTrack?.setEnabled(enabled) }

    // ─────────────────────────────────────────────────────────────────────────
    // Recording (host only)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Starts recording the local video+audio to [outputFile].
     * Must be called after [setupLocalCamera] so that [localVideoTrack] exists.
     */
    fun startRecording(outputFile: java.io.File) {
        val track = localVideoTrack
        if (track == null) {
            Log.w(TAG, "startRecording called before local camera is set up")
            return
        }
        recorder.startRecording(outputFile, width = 1280, height = 720)
        track.addSink(recorder)
        Log.d(TAG, "Recording started → ${outputFile.absolutePath}")
    }

    /**
     * Stops recording and returns duration in seconds.
     * Call this before [close] so the output file is properly finalized.
     */
    fun stopRecording(): Long {
        localVideoTrack?.removeSink(recorder)
        val duration = recorder.stopRecording()
        Log.d(TAG, "Recording stopped, duration=${duration}s")
        return duration
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Release all resources
    // ─────────────────────────────────────────────────────────────────────────

    fun close() {
        try {
            peerConnections.values.forEach { runCatching { it.close() } }
            peerConnections.clear()

            // Ensure recorder is stopped before releasing the video track
            localVideoTrack?.removeSink(recorder)

            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            videoCapturer = null

            surfaceTextureHelper?.dispose()
            surfaceTextureHelper = null

            localVideoTrack?.dispose()
            localVideoTrack = null
            localAudioTrack?.dispose()
            localAudioTrack = null
            videoSource?.dispose()
            videoSource = null
            localStream = null

            factory?.dispose()
            factory = null
            Log.d(TAG, "LivestreamWebRTCManager closed")
        } catch (e: Exception) {
            Log.e(TAG, "close error", e)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun ensureFactory() {
        if (factory != null) return
        // Always create a fresh factory — never reuse the call factory from WebRTCManager,
        // which may have been disposed when a P2P call ended.
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )

        val eglContext = WebRTCManager.getEglContext()
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglContext))
            .createPeerConnectionFactory()
    }

    private fun createPeerConnection(userId: Long, iceServers: List<PeerConnection.IceServer>): PeerConnection? {
        val f = factory ?: run { Log.e(TAG, "factory null"); return null }
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        val pc = f.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                onIceCandidateReady?.invoke(userId, candidate)
            }
            override fun onAddStream(stream: MediaStream) {
                // Called in PLAN_B; still called in some UNIFIED_PLAN implementations for compat.
                Log.d(TAG, "onAddStream from user $userId tracks=${stream.videoTracks.size}v ${stream.audioTracks.size}a")
                if (stream.videoTracks.isNotEmpty() || stream.audioTracks.isNotEmpty()) {
                    onRemoteStreamAdded?.invoke(userId, stream)
                }
            }
            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                onViewerConnectionStateChanged?.invoke(userId, newState.name)
                Log.d(TAG, "PC state userId=$userId -> $newState")
            }
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            // UNIFIED_PLAN: onAddTrack fires when the remote peer adds a track.
            // Build a synthetic MediaStream and deliver it to the UI.
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                Log.d(TAG, "onAddTrack from user $userId streams=${streams?.size}")
                // Use an existing stream if the peer sent one; otherwise build a synthetic one.
                val stream = streams?.firstOrNull { it.videoTracks.isNotEmpty() || it.audioTracks.isNotEmpty() }
                if (stream != null) {
                    onRemoteStreamAdded?.invoke(userId, stream)
                } else {
                    val track = receiver?.track() ?: return
                    val f = factory ?: return
                    val synth = f.createLocalMediaStream("remote_$userId") ?: return
                    if (track is VideoTrack) synth.addTrack(track)
                    else if (track is AudioTrack) synth.addTrack(track as AudioTrack)
                    onRemoteStreamAdded?.invoke(userId, synth)
                }
            }
        }) ?: run { Log.e(TAG, "createPeerConnection returned null"); return null }

        peerConnections[userId] = pc
        return pc
    }

    private fun createCameraVideoCapturer(): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        for (name in enumerator.deviceNames) {
            if (enumerator.isFrontFacing(name)) {
                enumerator.createCapturer(name, null)?.let { return it }
            }
        }
        for (name in enumerator.deviceNames) {
            enumerator.createCapturer(name, null)?.let { return it }
        }
        Log.e(TAG, "No camera found")
        return null
    }

    /** Simple SdpObserver that just logs failures. */
    private inner class LogSdpObserver(private val tag: String) : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) { Log.e(TAG, "[$tag] createFailure: $error") }
        override fun onSetFailure(error: String?) { Log.e(TAG, "[$tag] setFailure: $error") }
    }
}

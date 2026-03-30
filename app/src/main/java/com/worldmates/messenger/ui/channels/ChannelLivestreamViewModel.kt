package com.worldmates.messenger.ui.channels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.network.LivestreamWebRTCManager
import com.worldmates.messenger.network.NodeRetrofitClient
import com.worldmates.messenger.network.SocketManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import retrofit2.HttpException
import retrofit2.http.*

// ─── API models ───────────────────────────────────────────────────────────────

data class LivestreamInfo(
    val stream_id: Int?,
    val room_name: String,
    val quality: String,
    val is_premium: Boolean,
    val ice_servers: List<IceServerInfo>?,
    val allowed_qualities: List<String>?,
    val viewer_count: Int = 0
)

data class IceServerInfo(
    val urls: List<String>,
    val username: String? = null,
    val credential: String? = null
)

data class ActiveStreamResponse(
    val api_status: Int,
    val stream: LivestreamInfoRaw?
)

data class LivestreamInfoRaw(
    val id: Int,
    val channel_id: Int,
    val room_name: String,
    val title: String?,
    val quality: String,
    val is_premium: Int,
    val status: String,
    val viewer_count: Int,
    val started_at: String
)

data class StartStreamResponse(
    val api_status: Int,
    val stream_id: Int?,
    val room_name: String?,
    val quality: String?,
    val is_premium: Boolean?,
    val ice_servers: List<Any>?,
    val allowed_qualities: List<String>?,
    val error_message: String?
)

data class JoinStreamResponse(
    val api_status: Int,
    val stream_id: Int?,
    val room_name: String?,
    val quality: String?,
    val is_premium: Boolean?,
    val ice_servers: List<Any>?,
    val error_message: String?
)

interface ChannelLivestreamApi {
    @FormUrlEncoded
    @POST("api/node/channels/{channel_id}/livestream/start")
    suspend fun startStream(
        @Path("channel_id") channelId: Long,
        @Field("quality") quality: String,
        @Field("title") title: String?
    ): StartStreamResponse

    @POST("api/node/channels/{channel_id}/livestream/end")
    suspend fun endStream(
        @Path("channel_id") channelId: Long
    ): retrofit2.Response<Map<String, Any>>

    @GET("api/node/channels/{channel_id}/livestream/active")
    suspend fun getActiveStream(
        @Path("channel_id") channelId: Long
    ): ActiveStreamResponse

    @POST("api/node/channels/{channel_id}/livestream/join")
    suspend fun joinStream(
        @Path("channel_id") channelId: Long
    ): JoinStreamResponse

    @POST("api/node/channels/{channel_id}/livestream/leave")
    suspend fun leaveStream(
        @Path("channel_id") channelId: Long
    ): retrofit2.Response<Map<String, Any>>
}

// ─── UI State ─────────────────────────────────────────────────────────────────

sealed class LivestreamUiState {
    object Idle : LivestreamUiState()
    object Loading : LivestreamUiState()
    data class Hosting(val info: LivestreamInfo) : LivestreamUiState()
    data class Viewing(val info: LivestreamInfo) : LivestreamUiState()
    data class Error(val message: String) : LivestreamUiState()
    object Ended : LivestreamUiState()
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

class ChannelLivestreamViewModel(app: Application) : AndroidViewModel(app), SocketManager.SocketListener {

    companion object {
        private const val TAG = "LivestreamVM"
        val REGULAR_QUALITIES = listOf("240p", "360p", "480p", "720p")
        val PREMIUM_QUALITIES  = listOf("360p", "480p", "720p", "1080p", "1080p60")
    }

    private val api: ChannelLivestreamApi by lazy {
        NodeRetrofitClient.retrofit.create(ChannelLivestreamApi::class.java)
    }

    // ── UI state ───────────────────────────────────────────────────────────────
    private val _uiState = MutableStateFlow<LivestreamUiState>(LivestreamUiState.Idle)
    val uiState: StateFlow<LivestreamUiState> = _uiState

    // ── Video streams for the UI ───────────────────────────────────────────────
    /** Local camera stream (host only) */
    private val _localStream = MutableStateFlow<MediaStream?>(null)
    val localStream: StateFlow<MediaStream?> = _localStream

    /** Remote video stream received from host (viewer only) */
    private val _remoteStream = MutableStateFlow<MediaStream?>(null)
    val remoteStream: StateFlow<MediaStream?> = _remoteStream

    // ── Internal state ─────────────────────────────────────────────────────────
    private var currentChannelId: Long = 0
    private var currentRoomName: String? = null
    private var isHost = false
    private var myUserId: Long = UserSession.userId.toLong()
    /** ICE servers received from the server when the host starts a stream. */
    private var hostIceServers: List<PeerConnection.IceServer> = emptyList()

    // ── WebRTC + Socket ────────────────────────────────────────────────────────
    private val lsManager = LivestreamWebRTCManager(app)
    val socketManager = SocketManager(this, app)

    init {
        setupLivestreamWebRTCCallbacks()
        setupLivestreamSocketListeners()
        socketManager.connect()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Wire WebRTC callbacks → socket events
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupLivestreamWebRTCCallbacks() {
        lsManager.onIceCandidateReady = { toUserId, candidate ->
            currentRoomName?.let { roomName ->
                val payload = JSONObject().apply {
                    put("roomName", roomName)
                    put("toUserId", toUserId)
                    put("candidate", candidate.sdp)
                    put("sdpMid", candidate.sdpMid ?: "")
                    put("sdpMLineIndex", candidate.sdpMLineIndex)
                }
                socketManager.emit("stream:ice", payload)
            }
        }

        lsManager.onOfferReady = { toUserId, sdp ->
            currentRoomName?.let { roomName ->
                val payload = JSONObject().apply {
                    put("roomName", roomName)
                    put("toUserId", toUserId)
                    put("sdpOffer", sdp.description)
                }
                socketManager.emit("stream:offer", payload)
                Log.d(TAG, "📤 Sent stream:offer to viewer $toUserId")
            }
        }

        lsManager.onAnswerReady = { toUserId, sdp ->
            currentRoomName?.let { roomName ->
                val payload = JSONObject().apply {
                    put("roomName", roomName)
                    put("toUserId", toUserId)
                    put("sdpAnswer", sdp.description)
                }
                socketManager.emit("stream:answer", payload)
                Log.d(TAG, "📤 Sent stream:answer to host $toUserId")
            }
        }

        lsManager.onRemoteStreamAdded = { _, stream ->
            Log.d(TAG, "📥 Remote stream received from host")
            _remoteStream.value = stream
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Socket listeners for livestream signaling
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupLivestreamSocketListeners() {
        // Host: a viewer joined → create offer for them
        // Server sends key "userId" (not "viewerUserId"); ICE servers come from the
        // host's own startStream response, not from this event.
        socketManager.on("stream:viewer_joined") { args ->
            val data = args.getOrNull(0) as? JSONObject ?: return@on
            val viewerUserId = data.optLong("userId")
            Log.d(TAG, "👁 Viewer $viewerUserId joined, creating offer")
            lsManager.createOfferForViewer(viewerUserId, hostIceServers)
        }

        // Viewer: received SDP offer from host → handle and create answer
        socketManager.on("stream:offer") { args ->
            val data = args.getOrNull(0) as? JSONObject ?: return@on
            val hostUserId = data.optLong("fromUserId")
            val sdpOffer   = data.optString("sdpOffer")
            val rawIce     = data.optJSONArray("iceServers")
            val iceServers = parseIceServers(rawIce)
            Log.d(TAG, "📥 Received stream:offer from host $hostUserId")
            lsManager.handleOfferFromHost(hostUserId, sdpOffer, iceServers)
        }

        // Host: received SDP answer from a viewer
        socketManager.on("stream:answer") { args ->
            val data = args.getOrNull(0) as? JSONObject ?: return@on
            val viewerUserId = data.optLong("fromUserId")
            val sdpAnswer    = data.optString("sdpAnswer")
            Log.d(TAG, "📥 Received stream:answer from viewer $viewerUserId")
            lsManager.handleAnswerFromViewer(viewerUserId, sdpAnswer)
        }

        // Either side: ICE candidate
        socketManager.on("stream:ice") { args ->
            val data = args.getOrNull(0) as? JSONObject ?: return@on
            val fromUserId   = data.optLong("fromUserId")
            val candidate    = data.optString("candidate")
            val sdpMid       = data.optString("sdpMid", "")
            val sdpMLineIdx  = data.optInt("sdpMLineIndex", 0)
            lsManager.addIceCandidate(fromUserId, sdpMid, sdpMLineIdx, candidate)
        }

        // Viewer: stream has ended
        socketManager.on("stream:ended") { _ ->
            Log.d(TAG, "Stream ended by host")
            LiveChannelTracker.markEnded(currentChannelId)
            cleanup()
            _uiState.value = LivestreamUiState.Ended
        }

        // Host: a viewer left (tear down their peer connection)
        // Server emits key "userId" (not "viewerUserId")
        socketManager.on("stream:viewer_left") { args ->
            val data = args.getOrNull(0) as? JSONObject ?: return@on
            val viewerUserId = data.optLong("userId")
            lsManager.removePeer(viewerUserId)
        }

        // Viewer: join_ack received after stream:join
        socketManager.on("stream:join_ack") { args ->
            val data = args.getOrNull(0) as? JSONObject ?: return@on
            Log.d(TAG, "stream:join_ack received, room=${data.optString("roomName")}")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    fun startStream(channelId: Long, quality: String, title: String? = null) {
        currentChannelId = channelId
        isHost = true
        _uiState.value = LivestreamUiState.Loading
        viewModelScope.launch {
            try {
                val resp = api.startStream(channelId, quality, title)
                if (resp.api_status == 200 && resp.room_name != null) {
                    currentRoomName = resp.room_name
                    hostIceServers = iceServersFromList(resp.ice_servers)
                    onHostStreamReady(
                        LivestreamInfo(
                            stream_id         = resp.stream_id,
                            room_name         = resp.room_name,
                            quality           = resp.quality ?: quality,
                            is_premium        = resp.is_premium == true,
                            ice_servers       = null,
                            allowed_qualities = resp.allowed_qualities
                        )
                    )
                } else {
                    _uiState.value = LivestreamUiState.Error(resp.error_message ?: "Failed to start stream")
                }
            } catch (e: HttpException) {
                if (e.code() == 409) {
                    // Stream already active — resume as host
                    try {
                        val bodyStr = e.response()?.errorBody()?.string()
                        val bodyJson = JSONObject(bodyStr ?: "{}")
                        val stream   = bodyJson.optJSONObject("stream")
                        val roomName = stream?.optString("room_name", "") ?: ""
                        if (roomName.isNotEmpty()) {
                            currentRoomName = roomName
                            // Parse ICE servers if server included them in 409 body
                            hostIceServers = parseIceServers(bodyJson.optJSONArray("ice_servers"))
                            onHostStreamReady(
                                LivestreamInfo(
                                    stream_id  = stream?.optInt("id"),
                                    room_name  = roomName,
                                    quality    = stream?.optString("quality", quality) ?: quality,
                                    is_premium = (stream?.optInt("is_premium") ?: 0) == 1,
                                    ice_servers = null,
                                    allowed_qualities = null
                                )
                            )
                            Log.d(TAG, "Resumed existing stream: room=$roomName")
                            return@launch
                        }
                    } catch (parseEx: Exception) {
                        Log.w(TAG, "Failed to parse 409 body", parseEx)
                    }
                }
                Log.e(TAG, "startStream error", e)
                _uiState.value = LivestreamUiState.Error(e.message ?: "Network error")
            } catch (e: Exception) {
                Log.e(TAG, "startStream error", e)
                _uiState.value = LivestreamUiState.Error(e.message ?: "Network error")
            }
        }
    }

    fun endStream() {
        // Mark ended synchronously — onClose() fires immediately after this call
        // and finishes the Activity, which may cancel viewModelScope before the
        // coroutine below reaches its finally block.
        LiveChannelTracker.markEnded(currentChannelId)
        viewModelScope.launch {
            try { api.endStream(currentChannelId) }
            catch (e: Exception) { Log.w(TAG, "endStream error (ignoring)", e) }
            finally {
                currentRoomName?.let { room ->
                    val payload = JSONObject().apply { put("roomName", room) }
                    socketManager.emit("stream:end", payload)
                }
                cleanup()
                _uiState.value = LivestreamUiState.Ended
            }
        }
    }

    fun joinStream(channelId: Long) {
        currentChannelId = channelId
        isHost = false
        _uiState.value = LivestreamUiState.Loading
        viewModelScope.launch {
            try {
                val resp = api.joinStream(channelId)
                if (resp.api_status == 200 && resp.room_name != null) {
                    currentRoomName = resp.room_name
                    onViewerJoined(
                        LivestreamInfo(
                            stream_id   = resp.stream_id,
                            room_name   = resp.room_name,
                            quality     = resp.quality ?: "720p",
                            is_premium  = resp.is_premium == true,
                            ice_servers = null,
                            allowed_qualities = null
                        )
                    )
                } else {
                    // No active stream — clear any stale live indicator and show waiting screen
                    LiveChannelTracker.markEnded(channelId)
                    _uiState.value = LivestreamUiState.Idle
                }
            } catch (e: HttpException) {
                if (e.code() == 404) {
                    // 404 = "No active stream" — not an error, just no stream yet
                    LiveChannelTracker.markEnded(channelId)
                    _uiState.value = LivestreamUiState.Idle
                } else {
                    Log.e(TAG, "joinStream error", e)
                    _uiState.value = LivestreamUiState.Error(e.message ?: "Network error")
                }
            } catch (e: Exception) {
                Log.e(TAG, "joinStream error", e)
                _uiState.value = LivestreamUiState.Error(e.message ?: "Network error")
            }
        }
    }

    fun leaveStream() {
        viewModelScope.launch {
            try {
                api.leaveStream(currentChannelId)
                currentRoomName?.let { room ->
                    val payload = JSONObject().apply {
                        put("roomName", room)
                        put("userId", myUserId)
                        put("channelId", currentChannelId)
                    }
                    socketManager.emit("stream:viewer_leave", payload)
                }
            } catch (e: Exception) { Log.w(TAG, "leaveStream error", e) }
            finally {
                cleanup()
                _uiState.value = LivestreamUiState.Ended
            }
        }
    }

    fun checkActiveStream(channelId: Long) {
        currentChannelId = channelId
        viewModelScope.launch {
            try {
                val resp = api.getActiveStream(channelId)
                if (resp.api_status == 200 && resp.stream != null) {
                    _uiState.value = LivestreamUiState.Viewing(
                        LivestreamInfo(
                            stream_id   = resp.stream.id,
                            room_name   = resp.stream.room_name,
                            quality     = resp.stream.quality,
                            is_premium  = resp.stream.is_premium == 1,
                            ice_servers = null,
                            allowed_qualities = null,
                            viewer_count = resp.stream.viewer_count
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "checkActiveStream error", e)
            }
        }
    }

    fun toggleAudio(enabled: Boolean) = lsManager.setAudioEnabled(enabled)
    fun toggleVideo(enabled: Boolean) = lsManager.setVideoEnabled(enabled)
    fun switchCamera() = lsManager.switchCamera()

    fun availableQualities(isPremium: Boolean) =
        if (isPremium) PREMIUM_QUALITIES else REGULAR_QUALITIES

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun onHostStreamReady(info: LivestreamInfo) {
        // Start local camera
        lsManager.setupLocalCamera()
        _localStream.value = lsManager.localStream

        // Mark channel as live so ChannelDetailsActivity shows the LIVE banner
        LiveChannelTracker.markLive(currentChannelId)

        // Tell socket server we're the host and ready
        val payload = JSONObject().apply {
            put("roomName", info.room_name)
            put("hostUserId", myUserId)
        }
        socketManager.emit("stream:host_ready", payload)

        _uiState.value = LivestreamUiState.Hosting(info)
        Log.d(TAG, "Host ready: room=${info.room_name}")
    }

    private fun onViewerJoined(info: LivestreamInfo) {
        // Tell server we are ready for WebRTC — server will notify the host to create an offer.
        // Event name and keys must match what channels-listener.js expects.
        val payload = JSONObject().apply {
            put("roomName", info.room_name)
            put("userId", myUserId)
            put("channelId", currentChannelId)
        }
        socketManager.emit("stream:viewer_join", payload)

        _uiState.value = LivestreamUiState.Viewing(info)
        Log.d(TAG, "Viewer joined: room=${info.room_name}")
    }

    private fun cleanup() {
        lsManager.close()
        _localStream.value  = null
        _remoteStream.value = null
        currentRoomName     = null
        hostIceServers      = emptyList()
    }

    /**
     * Converts the Gson-parsed ice_servers list (List<Any>) from Retrofit responses
     * into WebRTC IceServer objects.
     */
    private fun iceServersFromList(list: List<Any>?): List<PeerConnection.IceServer> {
        if (list.isNullOrEmpty()) return emptyList()
        return try {
            val jsonArr = org.json.JSONArray(com.google.gson.Gson().toJson(list))
            parseIceServers(jsonArr)
        } catch (e: Exception) {
            Log.w(TAG, "iceServersFromList parse error", e)
            emptyList()
        }
    }

    private fun parseIceServers(arr: org.json.JSONArray?): List<PeerConnection.IceServer> {
        if (arr == null) return emptyList()
        val result = mutableListOf<PeerConnection.IceServer>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val urlsArray = obj.optJSONArray("urls")
            val urls = buildList {
                if (urlsArray != null) {
                    for (j in 0 until urlsArray.length()) add(urlsArray.optString(j))
                } else {
                    val single = obj.optString("urls")
                    if (single.isNotEmpty()) add(single)
                }
            }
            val username   = obj.optString("username",   "")
            val credential = obj.optString("credential", "")
            val builder    = PeerConnection.IceServer.builder(urls)
            if (username.isNotEmpty()) builder.setUsername(username)
            if (credential.isNotEmpty()) builder.setPassword(credential)
            result.add(builder.createIceServer())
        }
        return result
    }

    // ── SocketManager.SocketListener ──────────────────────────────────────────
    override fun onNewMessage(messageJson: org.json.JSONObject) {}
    override fun onSocketConnected() { Log.d(TAG, "Livestream socket connected") }
    override fun onSocketDisconnected() { Log.d(TAG, "Livestream socket disconnected") }
    override fun onSocketError(error: String) { Log.w(TAG, "Livestream socket error: $error") }

    // ─────────────────────────────────────────────────────────────────────────
    override fun onCleared() {
        super.onCleared()
        // Safety net: ensure LIVE banner is removed even if endStream() didn't complete
        if (isHost && currentChannelId != 0L) LiveChannelTracker.markEnded(currentChannelId)
        cleanup()
        socketManager.disconnect()
    }
}

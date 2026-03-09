package com.worldmates.messenger.ui.channels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.network.NodeRetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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

    @FormUrlEncoded
    @POST("api/node/channels/{channel_id}/livestream/end")
    suspend fun endStream(
        @Path("channel_id") channelId: Long
    ): retrofit2.Response<Map<String, Any>>

    @GET("api/node/channels/{channel_id}/livestream/active")
    suspend fun getActiveStream(
        @Path("channel_id") channelId: Long
    ): ActiveStreamResponse

    @FormUrlEncoded
    @POST("api/node/channels/{channel_id}/livestream/join")
    suspend fun joinStream(
        @Path("channel_id") channelId: Long
    ): JoinStreamResponse

    @FormUrlEncoded
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

class ChannelLivestreamViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val TAG = "LivestreamVM"
        val REGULAR_QUALITIES = listOf("240p", "360p", "480p", "720p")
        val PREMIUM_QUALITIES  = listOf("360p", "480p", "720p", "1080p", "1080p60")
    }

    private val api: ChannelLivestreamApi by lazy {
        NodeRetrofitClient.retrofit.create(ChannelLivestreamApi::class.java)
    }

    private val _uiState = MutableStateFlow<LivestreamUiState>(LivestreamUiState.Idle)
    val uiState: StateFlow<LivestreamUiState> = _uiState

    private var currentChannelId: Long = 0
    private var currentRoomName: String? = null

    fun startStream(channelId: Long, quality: String, title: String? = null) {
        currentChannelId = channelId
        _uiState.value = LivestreamUiState.Loading
        viewModelScope.launch {
            try {
                val resp = api.startStream(channelId, quality, title)
                if (resp.api_status == 200 && resp.room_name != null) {
                    currentRoomName = resp.room_name
                    _uiState.value = LivestreamUiState.Hosting(
                        LivestreamInfo(
                            stream_id       = resp.stream_id,
                            room_name       = resp.room_name,
                            quality         = resp.quality ?: quality,
                            is_premium      = resp.is_premium == true,
                            ice_servers     = null,
                            allowed_qualities = resp.allowed_qualities
                        )
                    )
                    Log.d(TAG, "Stream started: room=${resp.room_name} quality=${resp.quality}")
                } else {
                    _uiState.value = LivestreamUiState.Error(resp.error_message ?: "Failed to start stream")
                }
            } catch (e: Exception) {
                Log.e(TAG, "startStream error", e)
                _uiState.value = LivestreamUiState.Error(e.message ?: "Network error")
            }
        }
    }

    fun endStream() {
        viewModelScope.launch {
            try {
                api.endStream(currentChannelId)
            } catch (e: Exception) {
                Log.w(TAG, "endStream error (ignoring)", e)
            } finally {
                currentRoomName = null
                _uiState.value = LivestreamUiState.Ended
            }
        }
    }

    fun joinStream(channelId: Long) {
        currentChannelId = channelId
        _uiState.value = LivestreamUiState.Loading
        viewModelScope.launch {
            try {
                val resp = api.joinStream(channelId)
                if (resp.api_status == 200 && resp.room_name != null) {
                    currentRoomName = resp.room_name
                    _uiState.value = LivestreamUiState.Viewing(
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
                    _uiState.value = LivestreamUiState.Error(resp.error_message ?: "No active stream")
                }
            } catch (e: Exception) {
                Log.e(TAG, "joinStream error", e)
                _uiState.value = LivestreamUiState.Error(e.message ?: "Network error")
            }
        }
    }

    fun leaveStream() {
        viewModelScope.launch {
            try { api.leaveStream(currentChannelId) }
            catch (e: Exception) { Log.w(TAG, "leaveStream error", e) }
            finally { _uiState.value = LivestreamUiState.Ended }
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

    fun availableQualities(isPremium: Boolean) =
        if (isPremium) PREMIUM_QUALITIES else REGULAR_QUALITIES
}

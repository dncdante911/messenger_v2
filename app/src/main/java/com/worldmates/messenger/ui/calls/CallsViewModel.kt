package com.worldmates.messenger.ui.calls

import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.data.model.*
import com.worldmates.messenger.network.SocketManager
import com.worldmates.messenger.network.WebRTCManager
import kotlinx.coroutines.*
import org.webrtc.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import org.json.JSONObject

data class CallData(
    val callId: Int,
    val fromId: Int,
    val fromName: String,
    val fromAvatar: String,
    val toId: Int? = null,
    val groupId: Int? = null,
    val callType: String, // "audio" или "video"
    val roomName: String,
    val sdpOffer: String? = null,
    val sdpAnswer: String? = null
)

data class IceCandidateData(
    val roomName: String,
    val candidate: String,
    val sdpMLineIndex: Int,
    val sdpMid: String
)

class CallsViewModel(application: Application) : AndroidViewModel(application), SocketManager.SocketListener {

    private val webRTCManager = WebRTCManager(application)
    val socketManager = SocketManager(this, application)  // ✅ public для доступу з CallsActivity
    private val gson = Gson()

    // LiveData для UI
    val incomingCall = MutableLiveData<CallData?>()
    val callConnected = MutableLiveData<Boolean>()
    val callEnded = MutableLiveData<Boolean>()
    val callError = MutableLiveData<String>()
    val remoteStreamAdded = MutableLiveData<MediaStream>()
    val localStreamAdded = MutableLiveData<MediaStream>()
    val connectionState = MutableLiveData<String>()
    val socketConnected = MutableLiveData<Boolean>(false)  // ✅ Додано для відстеження підключення

    private var currentCallData: CallData? = null
    private var currentCallId: Int = 0
    private var isInitiator = false
    private var pendingCallInitiation: (() -> Unit)? = null  // ✅ Очікуючий вихідний виклик
    private var pendingCallAcceptance: (() -> Unit)? = null  // ✅ Очікуюче прийняття вхідного виклику

    // 🔊 Audio management
    private val audioManager: AudioManager by lazy {
        getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    private var audioFocusRequest: AudioFocusRequest? = null
    private var savedAudioMode: Int = AudioManager.MODE_NORMAL
    private var savedIsSpeakerphoneOn: Boolean = false

    init {
        socketManager.connect()
        setupWebRTCListeners()
        // registerForCalls() перенесено в onSocketConnected() для правильного таймінгу
    }

    /**
     * 📞 Зареєструвати користувача для отримання вхідних дзвінків
     */
    private fun registerForCalls() {
        val userId = getUserId()
        val registerData = JSONObject().apply {
            put("userId", userId)
            put("user_id", userId)  // Для сумісності
        }
        socketManager.emit("call:register", registerData)
        Log.d("CallsViewModel", "📞 Registered for calls: userId=$userId")
    }

    /**
     * 🔌 Налаштувати Socket.IO listeners для call events
     */
    private fun setupCallSocketListeners() {
        Log.d("CallsViewModel", "🔌 Setting up call Socket.IO listeners...")

        // 📞 Вхідний дзвінок
        socketManager.on("call:incoming") { args ->
            try {
                if (args.isNotEmpty()) {
                    val data = args[0] as? org.json.JSONObject // Используем нативный тип
                    data?.let {
                        Log.d("CallsViewModel", "📞 Incoming call received")
                        // Передаем напрямую объект org.json.JSONObject
                        onIncomingCall(it)
                    }
                }
            } catch (e: Exception) {
                Log.e("CallsViewModel", "Error processing call:incoming", e)
            }
        }

        // ✅ Відповідь на дзвінок (SDP answer)
        socketManager.on("call:answer") { args ->
            try {
                if (args.isNotEmpty()) {
                    val data = args[0] as? JSONObject
                    data?.let {
                        Log.d("CallsViewModel", "✅ Call answer received")
                        val roomName = it.optString("roomName")
                        val sdpAnswer = it.optString("sdpAnswer")

                        // Встановити remote description
                        val answerSdp = SessionDescription(SessionDescription.Type.ANSWER, sdpAnswer)
                        webRTCManager.setRemoteDescription(answerSdp) { error ->
                            Log.e("CallsViewModel", "Failed to set remote description: $error")
                            callError.postValue("Failed to set remote description: $error")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("CallsViewModel", "Error processing call:answer", e)
            }
        }

        // 🧊 ICE candidate від іншого користувача
        socketManager.on("ice:candidate") { args ->
            try {
                if (args.isNotEmpty()) {
                    val data = args[0] as? JSONObject
                    data?.let {
                        val candidate = it.optString("candidate")
                        val sdpMLineIndex = it.optInt("sdpMLineIndex")
                        val sdpMid = it.optString("sdpMid")

                        val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex, candidate)
                        webRTCManager.addIceCandidate(iceCandidate)
                        Log.d("CallsViewModel", "🧊 ICE candidate added")
                    }
                }
            } catch (e: Exception) {
                Log.e("CallsViewModel", "Error processing ice:candidate", e)
            }
        }

        // ❌ Дзвінок відхилено
        socketManager.on("call:rejected") { args ->
            try {
                if (args.isNotEmpty()) {
                    val data = args[0] as? JSONObject
                    data?.let {
                        val roomName = it.optString("roomName")
                        val rejectedBy = it.optInt("rejectedBy")
                        Log.d("CallsViewModel", "❌ Call rejected by user $rejectedBy")
                        callEnded.postValue(true)
                        endCall()
                    }
                }
            } catch (e: Exception) {
                Log.e("CallsViewModel", "Error processing call:rejected", e)
            }
        }

        // 🔄 Renegotiation offer от peer'а (когда он включил видео)
        socketManager.on("call:renegotiate") { args ->
            try {
                if (args.isNotEmpty()) {
                    val data = args[0] as? JSONObject
                    data?.let {
                        Log.d("CallsViewModel", "🔄 Renegotiation offer received")
                        val sdpOffer = it.optString("sdpOffer")
                        val fromUserId = it.optInt("fromUserId")

                        // Установить новый remote description
                        val offerSdp = SessionDescription(SessionDescription.Type.OFFER, sdpOffer)
                        webRTCManager.setRemoteDescription(offerSdp) { error ->
                            Log.e("CallsViewModel", "Failed to set renegotiation offer: $error")
                        }

                        // Создать answer
                        webRTCManager.createAnswer(
                            onSuccess = { answer ->
                                currentCallData?.let { callData ->
                                    val answerEvent = JSONObject().apply {
                                        put("roomName", callData.roomName)
                                        put("fromUserId", getUserId())
                                        put("toUserId", fromUserId)
                                        put("sdpAnswer", answer.description)
                                        put("type", "renegotiate_answer")
                                    }
                                    socketManager.emit("call:renegotiate_answer", answerEvent)
                                    Log.d("CallsViewModel", "✅ Renegotiation answer sent")
                                }
                            },
                            onError = { error ->
                                Log.e("CallsViewModel", "Failed to create renegotiation answer: $error")
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("CallsViewModel", "Error processing call:renegotiate", e)
            }
        }

        // 🔄 Renegotiation answer от peer'а
        socketManager.on("call:renegotiate_answer") { args ->
            try {
                if (args.isNotEmpty()) {
                    val data = args[0] as? JSONObject
                    data?.let {
                        Log.d("CallsViewModel", "🔄 Renegotiation answer received")
                        val sdpAnswer = it.optString("sdpAnswer")

                        val answerSdp = SessionDescription(SessionDescription.Type.ANSWER, sdpAnswer)
                        webRTCManager.setRemoteDescription(answerSdp) { error ->
                            Log.e("CallsViewModel", "Failed to set renegotiation answer: $error")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("CallsViewModel", "Error processing call:renegotiate_answer", e)
            }
        }

        // 📴 Дзвінок завершено
        socketManager.on("call:ended") { args ->
            try {
                if (args.isNotEmpty()) {
                    val data = args[0] as? JSONObject
                    data?.let {
                        val roomName = it.optString("roomName")
                        val reason = it.optString("reason")
                        Log.d("CallsViewModel", "📴 Call ended: $reason")
                        callEnded.postValue(true)
                        endCall()
                    }
                }
            } catch (e: Exception) {
                Log.e("CallsViewModel", "Error processing call:ended", e)
            }
        }

        Log.d("CallsViewModel", "✅ Call Socket.IO listeners configured")
    }

    private fun setupWebRTCListeners() {
        webRTCManager.onIceCandidateListener = { candidate ->
            currentCallData?.let {
                // ✅ Використовуємо org.json.JSONObject для Socket.IO
                val iceCandidateData = JSONObject().apply {
                    put("roomName", it.roomName)
                    put("fromUserId", getUserId())
                    // ✅ CRITICAL: Add toUserId so server knows who to send the candidate to
                    put("toUserId", if (it.toId == getUserId()) it.fromId else it.toId)
                    put("candidate", candidate.sdp)
                    put("sdpMLineIndex", candidate.sdpMLineIndex)
                    put("sdpMid", candidate.sdpMid ?: "")
                }
                socketManager.emit("ice:candidate", iceCandidateData)
                Log.d("CallsViewModel", "🧊 Sent ICE candidate to peer")
            }
        }

        // ✅ UNIFIED_PLAN: используем onTrack вместо onAddStream
        webRTCManager.onTrackListener = { stream ->
            remoteStreamAdded.postValue(stream)
            Log.d("CallsViewModel", "Remote stream updated: ${stream.audioTracks.size} audio, ${stream.videoTracks.size} video tracks")
        }

        webRTCManager.onConnectionStateChangeListener = { state ->
            connectionState.postValue(state.toString())
            when (state) {
                PeerConnection.PeerConnectionState.CONNECTED -> {
                    callConnected.postValue(true)
                    Log.d("CallsViewModel", "Call connected!")
                }
                PeerConnection.PeerConnectionState.FAILED -> {
                    callError.postValue("Connection failed")
                    endCall()
                }
                PeerConnection.PeerConnectionState.DISCONNECTED -> {
                    callEnded.postValue(true)
                }
                else -> {}
            }
        }

        webRTCManager.onIceConnectionStateChangeListener = { state ->
            Log.d("CallsViewModel", "ICE Connection State: $state")
        }

        // ✅ Обработка renegotiation когда добавляется/удаляется track
        webRTCManager.onRenegotiationNeededListener = {
            Log.d("CallsViewModel", "🔄 Renegotiation needed - creating new offer")
            // Только если мы инициатор или уже в звонке
            if (currentCallData != null) {
                performRenegotiation()
            }
        }
    }

    /**
     * 🔄 Выполнить renegotiation - создать новый offer и отправить peer'у
     */
    private fun performRenegotiation() {
        viewModelScope.launch {
            try {
                webRTCManager.createOffer(
                    onSuccess = { offer ->
                        currentCallData?.let { callData ->
                            val renegotiateEvent = JSONObject().apply {
                                put("roomName", callData.roomName)
                                put("fromUserId", getUserId())
                                put("toUserId", if (callData.toId == getUserId()) callData.fromId else callData.toId)
                                put("sdpOffer", offer.description)
                                put("type", "renegotiate")
                            }
                            socketManager.emit("call:renegotiate", renegotiateEvent)
                            Log.d("CallsViewModel", "✅ Renegotiation offer sent")
                        }
                    },
                    onError = { error ->
                        Log.e("CallsViewModel", "❌ Failed to create renegotiation offer: $error")
                    }
                )
            } catch (e: Exception) {
                Log.e("CallsViewModel", "❌ Renegotiation error", e)
            }
        }
    }

    /**
     * Ініціювати вызов користувачу (1-на-1)
     */
    fun initiateCall(recipientId: Int, recipientName: String, recipientAvatar: String, callType: String = "audio") {
        Log.d("CallsViewModel", "📞 Initiating call to $recipientName (ID: $recipientId), type: $callType")

        val callLogic: () -> Unit = {
            // 🔊 CRITICAL: Setup audio for calls BEFORE creating WebRTC connection
            setupCallAudio(isVideoCall = callType == "video")

            viewModelScope.launch {
                try {
                    Log.d("CallsViewModel", "🔧 Fetching ICE servers before creating PeerConnection...")

                    // ✅ 1. Fetch ICE servers via Socket.IO BEFORE creating PeerConnection
                    val iceServers = fetchIceServersFromApi()
                    if (iceServers != null && iceServers.isNotEmpty()) {
                        webRTCManager.setIceServers(iceServers)
                        Log.d("CallsViewModel", "✅ ICE servers set before creating PeerConnection: ${iceServers.size} servers")
                    } else {
                        Log.w("CallsViewModel", "⚠️ Failed to fetch ICE servers via Socket.IO, using default STUN servers")
                        // Fallback to default STUN servers (may fail through restrictive NATs)
                    }

                    // 2. Создать PeerConnection (with TURN credentials if fetched successfully)
                    webRTCManager.createPeerConnection()

                    // 3. Создать локальный медиа стрим
                    val audioEnabled = true
                    val videoEnabled = (callType == "video")
                    webRTCManager.createLocalMediaStream(audioEnabled, videoEnabled)

                    // Опубліковати локальний стрім
                    val localStream = getLocalStream()
                    Log.d("CallsViewModel", "Local stream created: audio=${localStream?.audioTracks?.size}, video=${localStream?.videoTracks?.size}")
                    localStream?.let { localStreamAdded.postValue(it) }

                    // 4. Создать offer
                    webRTCManager.createOffer(
                        onSuccess = { offer ->
                            // 5. Отправить через Socket.IO
                            val roomName = generateRoomName()
                            currentCallData = CallData(
                                callId = 0,
                                fromId = getUserId(),
                                fromName = getUserName(),
                                fromAvatar = getUserAvatar(),
                                toId = recipientId,
                                callType = callType,
                                roomName = roomName,
                                sdpOffer = offer.description
                            )
                            isInitiator = true

                            // ✅ Використовуємо org.json.JSONObject для Socket.IO
                            val callEvent = JSONObject().apply {
                                put("fromId", getUserId())
                                put("toId", recipientId)
                                put("callType", callType)
                                put("roomName", roomName)
                                put("fromName", getUserName())
                                put("fromAvatar", getUserAvatar())  // ✅ Додано аватар
                                put("sdpOffer", offer.description)
                            }

                            Log.d("CallsViewModel", "🚀 Emitting call:initiate:")
                            Log.d("CallsViewModel", "   fromId: ${getUserId()}")
                            Log.d("CallsViewModel", "   toId: $recipientId")
                            Log.d("CallsViewModel", "   callType: $callType")
                            Log.d("CallsViewModel", "   roomName: $roomName")
                            Log.d("CallsViewModel", "   fromName: ${getUserName()}")
                            Log.d("CallsViewModel", "   fromAvatar: ${getUserAvatar()}")

                            socketManager.emit("call:initiate", callEvent)
                            Log.d("CallsViewModel", "✅ call:initiate emitted successfully")

                            // ✅ Join the Socket.IO room for this call
                            val joinRoomData = JSONObject().apply {
                                put("roomName", roomName)
                                put("userId", getUserId())
                            }
                            socketManager.emit("call:join_room", joinRoomData)
                            Log.d("CallsViewModel", "📍 Joined call room: $roomName")
                        },
                        onError = { error ->
                            callError.postValue(error)
                            Log.e("CallsViewModel", "Failed to create offer: $error")
                        }
                    )
                } catch (e: Exception) {
                    callError.postValue(e.message ?: "Unknown error")
                    Log.e("CallsViewModel", "Error initiating call", e)
                }
            }
        }

        // ✅ Перевірити чи Socket підключений
        if (socketConnected.value == true) {
            Log.d("CallsViewModel", "Socket ready, initiating call immediately")
            callLogic()
        } else {
            Log.d("CallsViewModel", "Socket not ready, pending call initiation...")
            pendingCallInitiation = callLogic
        }
    }

    /**
     * Ініціювати групповой вызов
     */
    fun initiateGroupCall(groupId: Int, groupName: String, callType: String = "audio") {
        val callLogic: () -> Unit = {
            // 🔊 CRITICAL: Setup audio for calls BEFORE creating WebRTC connection
            setupCallAudio(isVideoCall = callType == "video")

            viewModelScope.launch {
                try {
                    // ✅ Fetch ICE servers from API FIRST
                    val iceServers = fetchIceServersFromApi()
                    if (iceServers != null) {
                        webRTCManager.setIceServers(iceServers)
                        Log.d("CallsViewModel", "✅ ICE servers set for group call: ${iceServers.size} servers")
                    }

                    webRTCManager.createPeerConnection()
                    webRTCManager.createLocalMediaStream(audioEnabled = true, videoEnabled = (callType == "video"))

                    // Опубліковати локальний стрім
                    getLocalStream()?.let { localStreamAdded.postValue(it) }

                    webRTCManager.createOffer(
                        onSuccess = { offer ->
                            val roomName = generateRoomName()
                            currentCallData = CallData(
                                callId = 0,
                                fromId = getUserId(),
                                fromName = getUserName(),
                                fromAvatar = getUserAvatar(),
                                groupId = groupId,
                                callType = callType,
                                roomName = roomName,
                                sdpOffer = offer.description
                            )
                            isInitiator = true

                            // ✅ Використовуємо org.json.JSONObject для Socket.IO
                            val groupCallEvent = JSONObject().apply {
                                put("groupId", groupId)
                                put("initiatedBy", getUserId())
                                put("callType", callType)
                                put("roomName", roomName)
                                put("sdpOffer", offer.description)
                            }

                            socketManager.emit("group_call:initiate", groupCallEvent)
                            Log.d("CallsViewModel", "Group call initiated for group $groupId")
                        },
                        onError = { error ->
                            callError.postValue(error)
                        }
                    )
                } catch (e: Exception) {
                    callError.postValue(e.message)
                }
            }
        }

        // ✅ Перевірити чи Socket підключений
        if (socketConnected.value == true) {
            Log.d("CallsViewModel", "Socket ready, initiating group call immediately")
            callLogic()
        } else {
            Log.d("CallsViewModel", "Socket not ready, pending group call initiation...")
            pendingCallInitiation = callLogic
        }
    }

    /**
     * Прийняти вхідний вызов
     *
     * ✅ ВИПРАВЛЕНО: Тепер правильно обробляє випадок коли Socket ще не підключений
     * і отримує ICE сервери ПЕРЕД створенням PeerConnection
     */
    fun acceptCall(callData: CallData) {
        Log.d("CallsViewModel", "📞 acceptCall() called for room: ${callData.roomName}")

        val acceptLogic: () -> Unit = {
            // 🔊 CRITICAL: Setup audio for calls BEFORE creating WebRTC connection
            setupCallAudio(isVideoCall = callData.callType == "video")

            viewModelScope.launch {
                try {
                    currentCallData = callData
                    isInitiator = false

                    Log.d("CallsViewModel", "🔧 Fetching ICE servers before accepting call...")

                    // ✅ 1. КРИТИЧНО: Отримати ICE сервери ПЕРЕД створенням PeerConnection
                    val iceServers = fetchIceServersFromApi()
                    if (iceServers != null && iceServers.isNotEmpty()) {
                        webRTCManager.setIceServers(iceServers)
                        Log.d("CallsViewModel", "✅ ICE servers set for incoming call: ${iceServers.size} servers")
                    } else {
                        Log.w("CallsViewModel", "⚠️ Failed to fetch ICE servers, using default STUN")
                    }

                    // 2. Создать PeerConnection (з правильними ICE серверами)
                    webRTCManager.createPeerConnection()
                    Log.d("CallsViewModel", "✅ PeerConnection created")

                    // 3. Создать локальный стрим
                    val videoEnabled = (callData.callType == "video")
                    webRTCManager.createLocalMediaStream(audioEnabled = true, videoEnabled = videoEnabled)
                    Log.d("CallsViewModel", "✅ Local media stream created (video=$videoEnabled)")

                    // Опубліковати локальний стрім
                    getLocalStream()?.let { localStreamAdded.postValue(it) }

                    // 4. Установить remote description (offer от другого юзера)
                    callData.sdpOffer?.let { offerSdp ->
                        val remoteDescription = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
                        webRTCManager.setRemoteDescription(remoteDescription) { error ->
                            Log.e("CallsViewModel", "❌ Failed to set remote description: $error")
                            callError.postValue(error)
                        }
                        Log.d("CallsViewModel", "✅ Remote description (offer) set")
                    } ?: run {
                        Log.e("CallsViewModel", "❌ No SDP offer in call data!")
                        callError.postValue("No SDP offer received")
                        return@launch
                    }

                    // ✅ Join the Socket.IO room for this call BEFORE creating answer
                    val joinRoomData = JSONObject().apply {
                        put("roomName", callData.roomName)
                        put("userId", getUserId())
                    }
                    socketManager.emit("call:join_room", joinRoomData)
                    Log.d("CallsViewModel", "📍 Joined call room: ${callData.roomName}")

                    // 5. Создать answer
                    webRTCManager.createAnswer(
                        onSuccess = { answer ->
                            // ✅ Використовуємо org.json.JSONObject для Socket.IO
                            val acceptEvent = JSONObject().apply {
                                put("roomName", callData.roomName)
                                put("userId", getUserId())
                                put("sdpAnswer", answer.description)
                            }
                            socketManager.emit("call:accept", acceptEvent)
                            Log.d("CallsViewModel", "✅ Call accepted and answer sent successfully!")
                        },
                        onError = { error ->
                            Log.e("CallsViewModel", "❌ Failed to create answer: $error")
                            callError.postValue(error)
                        }
                    )
                } catch (e: Exception) {
                    Log.e("CallsViewModel", "❌ Error accepting call", e)
                    callError.postValue(e.message ?: "Unknown error accepting call")
                }
            }
        }

        // ✅ Перевірити чи Socket підключений
        if (socketConnected.value == true) {
            Log.d("CallsViewModel", "Socket ready, accepting call immediately")
            acceptLogic()
        } else {
            Log.d("CallsViewModel", "Socket not ready, pending call acceptance...")
            pendingCallAcceptance = acceptLogic  // ✅ Окрема черга для прийняття
        }
    }

    /**
     * Отклонить вызов
     */
    fun rejectCall(roomName: String) {
        // ✅ Використовуємо org.json.JSONObject для Socket.IO
        val rejectEvent = JSONObject().apply {
            put("roomName", roomName)
            put("userId", getUserId())
        }
        socketManager.emit("call:reject", rejectEvent)
        incomingCall.postValue(null)
    }

    /**
     * Завершить вызов
     */
    fun endCall() {
        currentCallData?.let { callData ->
            // ✅ Використовуємо org.json.JSONObject для Socket.IO
            val endEvent = JSONObject().apply {
                put("roomName", callData.roomName)
                put("userId", getUserId())
                put("reason", "user_ended")
            }
            socketManager.emit("call:end", endEvent)

            // ✅ Leave the Socket.IO room
            val leaveRoomData = JSONObject().apply {
                put("roomName", callData.roomName)
                put("userId", getUserId())
            }
            socketManager.emit("call:leave_room", leaveRoomData)
            Log.d("CallsViewModel", "📍 Left call room: ${callData.roomName}")
        }

        webRTCManager.close()

        // 🔊 Release audio after call ends
        releaseCallAudio()

        callEnded.postValue(true)
        currentCallData = null
    }

    /**
     * 🔇 Увімкнути/вимкнути мікрофон
     */
    fun toggleAudio(enabled: Boolean) {
        webRTCManager.setAudioEnabled(enabled)
        Log.d("CallsViewModel", "Audio ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * 📹 Увімкнути/вимкнути відео
     *
     * ✅ ВИПРАВЛЕНО: Тепер динамічно створює камеру якщо її немає
     */
    fun toggleVideo(enabled: Boolean) {
        if (enabled) {
            // ✅ Включити відео - створити камеру якщо її немає
            val success = webRTCManager.enableVideo()
            if (success) {
                // Оновити local stream в UI
                getLocalStream()?.let { localStreamAdded.postValue(it) }
                Log.d("CallsViewModel", "📹 Video enabled successfully")
            } else {
                Log.e("CallsViewModel", "❌ Failed to enable video")
            }
        } else {
            // Вимкнути відео (камера зупиняється)
            webRTCManager.disableVideo()
            Log.d("CallsViewModel", "📹 Video disabled")
        }
    }

    /**
     * 🔊 Увімкнути/вимкнути громку зв'язок (speaker)
     */
    fun toggleSpeaker(enabled: Boolean) {
        audioManager.isSpeakerphoneOn = enabled
        Log.d("CallsViewModel", "Speaker ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * 🔊 Setup audio for call - CRITICAL for hearing the other party
     * This requests audio focus and sets the audio mode to MODE_IN_COMMUNICATION
     */
    private fun setupCallAudio(isVideoCall: Boolean = false) {
        try {
            // Save current state to restore later
            savedAudioMode = audioManager.mode
            savedIsSpeakerphoneOn = audioManager.isSpeakerphoneOn

            // Request audio focus for voice call
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(audioAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener { focusChange ->
                        Log.d("CallsViewModel", "🔊 Audio focus changed: $focusChange")
                    }
                    .build()

                val result = audioManager.requestAudioFocus(audioFocusRequest!!)
                Log.d("CallsViewModel", "🔊 Audio focus request result: $result")
            } else {
                @Suppress("DEPRECATION")
                val result = audioManager.requestAudioFocus(
                    { focusChange -> Log.d("CallsViewModel", "🔊 Audio focus changed: $focusChange") },
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
                Log.d("CallsViewModel", "🔊 Audio focus request result: $result")
            }

            // ✅ CRITICAL: Set audio mode to MODE_IN_COMMUNICATION for WebRTC
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

            // Enable speakerphone for video calls by default, earpiece for audio calls
            audioManager.isSpeakerphoneOn = isVideoCall

            // ✅ Enable Bluetooth SCO if headset is connected
            if (audioManager.isBluetoothScoAvailableOffCall) {
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
                Log.d("CallsViewModel", "🔊 Bluetooth SCO started")
            }

            Log.d("CallsViewModel", "🔊 Call audio setup complete - mode: MODE_IN_COMMUNICATION, speaker: $isVideoCall")
        } catch (e: Exception) {
            Log.e("CallsViewModel", "🔊 Error setting up call audio", e)
        }
    }

    /**
     * 🔊 Release audio after call ends
     */
    private fun releaseCallAudio() {
        try {
            // Stop Bluetooth SCO
            if (audioManager.isBluetoothScoOn) {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
                Log.d("CallsViewModel", "🔊 Bluetooth SCO stopped")
            }

            // Abandon audio focus
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let {
                    audioManager.abandonAudioFocusRequest(it)
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }

            // Restore previous audio state
            audioManager.mode = savedAudioMode
            audioManager.isSpeakerphoneOn = savedIsSpeakerphoneOn

            Log.d("CallsViewModel", "🔊 Call audio released, mode restored to: $savedAudioMode")
        } catch (e: Exception) {
            Log.e("CallsViewModel", "🔊 Error releasing call audio", e)
        }
    }

    /**
     * 🔄 Переключити камеру (передня/задня)
     */
    fun switchCamera() {
        webRTCManager.switchCamera()
        Log.d("CallsViewModel", "Camera switched")
    }

    /**
     * 📹 Отримати поточну якість відео
     */
    fun getVideoQuality(): com.worldmates.messenger.network.VideoQuality {
        return webRTCManager.getVideoQuality()
    }

    /**
     * 📹 Змінити якість відео
     */
    fun setVideoQuality(quality: com.worldmates.messenger.network.VideoQuality): Boolean {
        val success = webRTCManager.setVideoQuality(quality)
        if (success) {
            Log.d("CallsViewModel", "📹 Video quality changed to ${quality.label}")
        }
        return success
    }

    /**
     * Socket.IO слушатели
     */
    // Required implementation from SocketListener
    override fun onNewMessage(messageJson: JSONObject) {
        // Not used for calls, but required by interface
    }

    override fun onSocketConnected() {
        Log.i("CallsViewModel", "Socket connected for calls")
        socketConnected.postValue(true)

        // ✅ Зареєструватись для дзвінків ПІСЛЯ підключення
        registerForCalls()

        // ✅ Налаштувати listeners для call events
        setupCallSocketListeners()

        // ✅ Виконати відкладений вихідний дзвінок якщо є
        pendingCallInitiation?.let {
            Log.d("CallsViewModel", "Executing pending call initiation...")
            it.invoke()
            pendingCallInitiation = null
        }

        // ✅ Виконати відкладене прийняття дзвінка якщо є
        pendingCallAcceptance?.let {
            Log.d("CallsViewModel", "Executing pending call acceptance...")
            it.invoke()
            pendingCallAcceptance = null
        }
    }

    override fun onSocketDisconnected() {
        Log.w("CallsViewModel", "Socket disconnected")
        socketConnected.postValue(false)
    }

    override fun onSocketError(error: String) {
        Log.e("CallsViewModel", "Socket error: $error")
        callError.postValue(error)
    }

    // Call-specific handlers (not part of SocketListener interface)
    fun onIncomingCall(data: org.json.JSONObject) { // Работаем напрямую с JSONObject
        val roomName = data.optString("roomName", "")
        try {
            // ✅ ВИПРАВЛЕНО: Парсити fromName з різних можливих полів (camelCase та snake_case)
            val fromNameRaw = data.optString("fromName", "")
            val fromNameSnake = data.optString("from_name", "")
            val callerNameRaw = data.optString("callerName", "")
            val nameRaw = data.optString("name", "")

            // Вибираємо перше непусте ім'я
            val fromName = listOf(fromNameRaw, fromNameSnake, callerNameRaw, nameRaw)
                .firstOrNull { it.isNotEmpty() } ?: "Користувач"

            Log.d("CallsViewModel", "📞 Parsing incoming call - fromNameRaw: '$fromNameRaw', fromNameSnake: '$fromNameSnake', callerNameRaw: '$callerNameRaw', result: '$fromName'")

            // ✅ Парсити fromAvatar з різних полів
            val fromAvatarRaw = data.optString("fromAvatar", "")
            val fromAvatarSnake = data.optString("from_avatar", "")
            val avatarRaw = data.optString("avatar", "")
            val fromAvatar = listOf(fromAvatarRaw, fromAvatarSnake, avatarRaw)
                .firstOrNull { it.isNotEmpty() } ?: ""

            // ✅ Парсити fromId з різних полів
            val fromIdCamel = data.optInt("fromId", 0)
            val fromIdSnake = data.optInt("from_id", 0)
            val callerIdRaw = data.optInt("callerId", 0)
            val fromId = listOf(fromIdCamel, fromIdSnake, callerIdRaw)
                .firstOrNull { it > 0 } ?: 0

            val callData = CallData(
                // optInt/optString никогда не вызовут NullPointerException
                callId = data.optInt("callId", 0),
                fromId = fromId,
                fromName = fromName,
                fromAvatar = fromAvatar,
                toId = getUserId(),
                callType = data.optString("callType", data.optString("call_type", "audio")),
                roomName = data.optString("roomName", data.optString("room_name", "")),
                sdpOffer = data.optString("sdpOffer", data.optString("sdp_offer", null))
            )

            // ✅ CRITICAL: Ignore calls from yourself (initiator receiving their own call)
            if (callData.fromId == getUserId()) {
                Log.w("CallsViewModel", "⚠️ Ignoring incoming call from myself (fromId=${callData.fromId}, userId=${getUserId()})")
                return
            }

            if (currentCallData?.roomName == roomName) {
                Log.d("CallsViewModel", "⚠️ Игнорируем дубликат входящего звонка для комнаты: $roomName")
                return
            }

            // ✅ Парсим и устанавливаем ICE servers с TURN credentials от сервера
            val iceServersArray = data.optJSONArray("iceServers")
            if (iceServersArray != null) {
                val iceServers = parseIceServers(iceServersArray)
                webRTCManager.setIceServers(iceServers)
                Log.d("CallsViewModel", "✅ ICE servers received from server: ${iceServers.size} servers")
            }
            if (callData.roomName.isEmpty()) {
                Log.e("CallsViewModel", "❌ Room name is empty, ignoring call")
                return
            }

            incomingCall.postValue(callData)

            Log.d("CallsViewModel", "📞 Incoming call from ${callData.fromName}")

            // Запуск Activity через контекст приложения
            val intent = IncomingCallActivity.createIntent(
                context = getApplication(),
                fromId = callData.fromId,
                fromName = callData.fromName,
                fromAvatar = callData.fromAvatar,
                callType = callData.callType,
                roomName = callData.roomName,
                sdpOffer = callData.sdpOffer
            ).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            getApplication<Application>().startActivity(intent)

        } catch (e: Exception) {
            Log.e("CallsViewModel", "🔥 Error parsing incoming call safely: ${e.message}")
        }
    }

    fun onCallAnswer(data: org.json.JSONObject) { // Проверь, что тут JSONObject
        try {
            // ✅ Парсим и устанавливаем ICE servers с TURN credentials от сервера
            val iceServersArray = data.optJSONArray("iceServers")
            if (iceServersArray != null) {
                val iceServers = parseIceServers(iceServersArray)
                webRTCManager.setIceServers(iceServers)
                Log.d("CallsViewModel", "✅ ICE servers received from server in answer: ${iceServers.size} servers")
            }

            // В org.json используем optString вместо get().asString
            val sdpAnswer = data.optString("sdpAnswer", "")
            if (sdpAnswer.isNotEmpty()) {
                val remoteDescription = SessionDescription(SessionDescription.Type.ANSWER, sdpAnswer)
                webRTCManager.setRemoteDescription(remoteDescription) { error ->
                    callError.postValue(error)
                }
                Log.d("CallsViewModel", "✅ Received answer and set remote description")
            }
        } catch (e: Exception) {
            Log.e("CallsViewModel", "Error handling answer", e)
        }
    }

    fun onIceCandidate(data: org.json.JSONObject) { // И тут JSONObject
        try {
            // В org.json используем optString и optInt
            val candidate = data.optString("candidate", "")
            val sdpMLineIndex = data.optInt("sdpMLineIndex", 0)
            val sdpMid = data.optString("sdpMid", "")

            if (candidate.isNotEmpty()) {
                val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex, candidate)
                webRTCManager.addIceCandidate(iceCandidate)
                Log.d("CallsViewModel", "🧊 ICE candidate added from remote")
            }
        } catch (e: Exception) {
            Log.e("CallsViewModel", "Error adding ICE candidate", e)
        }
    }

    fun onCallEnded(data: JSONObject) { // Изменили тип с JsonObject на JSONObject
        webRTCManager.close()
        callEnded.postValue(true)
        currentCallData = null
    }

    // Вспомогательные функции
    fun getUserId(): Int {
        return com.worldmates.messenger.data.UserSession.userId.toInt()
    }

    private fun getUserName(): String {
        return com.worldmates.messenger.data.UserSession.username ?: "Current User"
    }

    private fun getUserAvatar(): String {
        return com.worldmates.messenger.data.UserSession.avatar ?: ""
    }

    private fun generateRoomName(): String {
        return "room_${System.currentTimeMillis()}"
    }

    /**
     * Парсинг ICE servers из JSONArray от сервера
     */
    private fun parseIceServers(iceServersArray: org.json.JSONArray): List<PeerConnection.IceServer> {
        val iceServers = mutableListOf<PeerConnection.IceServer>()

        try {
            for (i in 0 until iceServersArray.length()) {
                val serverObj = iceServersArray.getJSONObject(i)

                // Парсим urls (может быть строкой или массивом)
                val urlsList = mutableListOf<String>()
                val urlsField = serverObj.opt("urls")

                when (urlsField) {
                    is String -> urlsList.add(urlsField)
                    is org.json.JSONArray -> {
                        for (j in 0 until urlsField.length()) {
                            urlsList.add(urlsField.getString(j))
                        }
                    }
                }

                // Создаём IceServer
                if (urlsList.isNotEmpty()) {
                    val username = serverObj.optString("username", null)
                    val credential = serverObj.optString("credential", null)

                    val builder = if (urlsList.size == 1) {
                        PeerConnection.IceServer.builder(urlsList[0])
                    } else {
                        PeerConnection.IceServer.builder(urlsList)
                    }

                    // Добавляем credentials если есть (для TURN серверов)
                    if (username != null && credential != null) {
                        builder.setUsername(username)
                        builder.setPassword(credential)
                    }

                    iceServers.add(builder.createIceServer())
                    Log.d("CallsViewModel", "Parsed ICE server: ${urlsList.joinToString()}")
                }
            }
        } catch (e: Exception) {
            Log.e("CallsViewModel", "Error parsing ICE servers", e)
        }

        return iceServers
    }

    /**
     * Fetch ICE servers via Socket.IO (more reliable than HTTP API)
     * Uses Socket.IO acknowledgments for synchronous request-response
     */
    private suspend fun fetchIceServersFromApi(): List<PeerConnection.IceServer>? {
        return try {
            val userId = getUserId()
            Log.d("CallsViewModel", "🧊 Requesting ICE servers via Socket.IO for user $userId...")

            val response = socketManager.requestIceServers(userId)

            if (response?.optBoolean("success") == true) {
                val iceServersArray = response.optJSONArray("iceServers")
                if (iceServersArray != null) {
                    val iceServers = parseIceServers(iceServersArray)
                    Log.d("CallsViewModel", "✅ Total ICE servers fetched via Socket.IO: ${iceServers.size}")
                    return iceServers
                } else {
                    Log.w("CallsViewModel", "⚠️ ICE servers array is null in response")
                }
            } else {
                Log.w("CallsViewModel", "⚠️ Failed to fetch ICE servers via Socket.IO: success=${response?.optBoolean("success")}")
            }

            null
        } catch (e: Exception) {
            Log.e("CallsViewModel", "❌ Error fetching ICE servers via Socket.IO", e)
            null
        }
    }

    /**
     * 🎥 Отримати локальний медіа стрім
     */
    fun getLocalStream(): MediaStream? {
        return webRTCManager.getLocalMediaStream()
    }

    override fun onCleared() {
        super.onCleared()
        webRTCManager.close()
        socketManager.disconnect()
    }
}

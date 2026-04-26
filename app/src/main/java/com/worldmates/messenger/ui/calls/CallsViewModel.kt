package com.worldmates.messenger.ui.calls

import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.R
import com.worldmates.messenger.data.model.*
import com.worldmates.messenger.network.GroupWebRTCManager
import com.worldmates.messenger.network.SocketManager
import com.worldmates.messenger.network.WebRTCManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import org.json.JSONObject
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

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

data class ParticipantLimitError(
    val message: String,
    val maxParticipants: Int,
    val isPro: Boolean
)

/** Данные для отображения входящего группового звонка */
data class GroupCallIncomingData(
    val groupId: Int,
    val groupName: String,
    val initiatedBy: Int,
    val initiatorName: String,
    val initiatorAvatar: String,
    val callType: String,
    val roomName: String,
    val maxParticipants: Int = 5,
    val isPremiumCall: Boolean = com.worldmates.messenger.data.UserSession.isProActive
)

class CallsViewModel(application: Application) : AndroidViewModel(application), SocketManager.SocketListener {

    companion object {
        // coturn static-auth-secret from worldmates.club server config
        private const val TURN_SECRET = "ad8a76d057d6ba0d6fd79bbc84504e320c8538b92db5c9b84fc3bd18d1c511b9"
        private const val TURN_HOST_1 = "195.22.131.11"
        private const val TURN_HOST_2 = "46.232.232.38"
        private const val TURN_HOST_3 = "93.171.188.229"
        // TURNS (TLS) — по домену, т.к. SSL сертификат привязан к домену
        private const val TURNS_DOMAIN = "worldmates.club"

        // Class-level set of roomNames currently showing an IncomingCallActivity.
        // Prevents two CallsViewModel instances (e.g. ChatsActivity + IncomingCallActivity)
        // from each launching a second instance of IncomingCallActivity for the same room.
        val pendingIncomingRooms = java.util.concurrent.CopyOnWriteArraySet<String>()
    }

    private val webRTCManager = WebRTCManager(application)
    val socketManager = SocketManager(this, application)  // ✅ public для доступу з CallsActivity
    private val gson = Gson()

    // ───── Group Call WebRTC ─────────────────────────────────────────────────
    val groupWebRTCManager = GroupWebRTCManager(application)
    var currentGroupRoomName: String? = null
        private set
    private var currentGroupId: Int = 0
    private var currentGroupMaxParticipants: Int = 5
    private var isGroupCallInitiator = false

    // LiveData для UI
    val incomingCall = MutableLiveData<CallData?>()
    /** Posted with the roomName when call:accepted_elsewhere arrives (dismiss IncomingCallActivity). */
    val callDismissed = MutableLiveData<String>()
    val callConnected = MutableLiveData<Boolean>()
    val callEnded = MutableLiveData<Boolean>()
    val callError = MutableLiveData<String>()
    /** Non-null when server rejected the group call due to participant count limit. */
    val participantLimitError = MutableLiveData<ParticipantLimitError?>()
    val remoteStreamAdded = MutableLiveData<MediaStream>()
    val localStreamAdded = MutableLiveData<MediaStream>()
    val connectionState = MutableLiveData<String>()
    val socketConnected = MutableLiveData<Boolean>(false)  // ✅ Додано для відстеження підключення

    // ───── Group Call LiveData ────────────────────────────────────────────────
    /** Список участников текущего группового звонка (обновляется в реальном времени) */
    val groupCallParticipants = MutableLiveData<List<GroupCallParticipant>>(emptyList())
    /** Входящий групповой звонок — показать уведомление */
    val incomingGroupCall = MutableLiveData<GroupCallIncomingData?>()
    /** Групповой звонок начался (после joinGroupCall) */
    val groupCallStarted = MutableLiveData<Boolean>()
    /** Групповой звонок завершён */
    val groupCallEnded = MutableLiveData<Boolean>()

    // ───── Video Filters State ────────────────────────────────────────────────
    // Поточний активний фільтр (для UI — підсвічення вибраного фільтру в панелі)
    private val _activeVideoFilter = MutableStateFlow(VideoFilterType.NONE)
    val activeVideoFilter: StateFlow<VideoFilterType> = _activeVideoFilter.asStateFlow()

    // ───── Virtual Background State ───────────────────────────────────────────
    // Поточний режим фону (для UI — відображення вибраного режиму)
    private val _activeVirtualBg = MutableStateFlow(VirtualBgMode.NONE)
    val activeVirtualBg: StateFlow<VirtualBgMode> = _activeVirtualBg.asStateFlow()

    // ───── Call Transfer ──────────────────────────────────────────────────────
    // Менеджер передачі дзвінка (lazy: ініціалізується після підключення сокету)
    private var _callTransferManager: CallTransferManager? = null
    val callTransferManager: CallTransferManager? get() = _callTransferManager

    // Стан передачі (прокидаємо зі StateFlow менеджера)
    val callTransferState: StateFlow<CallTransferState>
        get() = _callTransferManager?.state
            ?: MutableStateFlow(CallTransferState.IDLE).asStateFlow()

    val callTransferTarget: StateFlow<TransferTarget?>
        get() = _callTransferManager?.pendingTarget
            ?: MutableStateFlow<TransferTarget?>(null).asStateFlow()

    // ───── Conference Optimization State ─────────────────────────────────────
    private val _conferenceBandwidthMode =
        MutableStateFlow(GroupWebRTCManager.ConferenceBandwidthMode.AUTO)
    val conferenceBandwidthMode: StateFlow<GroupWebRTCManager.ConferenceBandwidthMode> =
        _conferenceBandwidthMode.asStateFlow()

    private var currentCallData: CallData? = null
    private var currentCallId: Int = 0
    private var isInitiator = false
    private var pendingCallInitiation: (() -> Unit)? = null  // ✅ Очікуючий вихідний виклик
    private var pendingCallAcceptance: (() -> Unit)? = null  // ✅ Очікуюче прийняття вхідного виклику
    private var callListenersSetUp = false      // guard against duplicate socket listeners on reconnect
    private var callFullyEstablished = false    // true once ICE reaches CONNECTED; gates renegotiation

    // Вспомогательная карта: userId → info участника (для обновления состояния)
    private val groupParticipantInfoMap = mutableMapOf<Long, GroupCallParticipant>()

    // 🔊 Audio management
    private val audioManager: AudioManager by lazy {
        getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    private var audioFocusRequest: AudioFocusRequest? = null
    private var savedAudioMode: Int = AudioManager.MODE_NORMAL
    private var callWakeLock: PowerManager.WakeLock? = null
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

        // Incoming chat message during call
        socketManager.on("call:chat_message") { args ->
            try {
                if (args.isNotEmpty()) {
                    val data = args[0] as? JSONObject
                    data?.let {
                        val senderId = it.optInt("userId", 0)
                        val senderName = it.optString("userName", "")
                        val text = it.optString("text", "")
                        if (text.isNotEmpty() && senderId != getUserId()) {
                            val current = callChatMessages.value ?: emptyList()
                            callChatMessages.postValue(current + Pair(senderName, text))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("CallsViewModel", "Error processing call:chat_message", e)
            }
        }

        // Incoming call reaction from the other party
        socketManager.on("call:reaction") { args ->
            try {
                if (args.isNotEmpty()) {
                    val data = args[0] as? JSONObject
                    data?.let {
                        val emoji = it.optString("emoji", "")
                        val userName = it.optString("userName", "")
                        val senderId = it.optInt("userId", 0)
                        if (emoji.isNotEmpty() && senderId != getUserId()) {
                            Log.d("CallsViewModel", "Received call reaction: $emoji from $userName")
                            incomingReaction.postValue(Pair(emoji, userName))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("CallsViewModel", "Error processing call:reaction", e)
            }
        }

        // 📞 Call accepted on another socket/device — dismiss pending call screens
        socketManager.on("call:accepted_elsewhere") { args ->
            try {
                val data = args.getOrNull(0) as? org.json.JSONObject ?: return@on
                val roomName = data.optString("roomName", "")
                Log.d("CallsViewModel", "📞 call:accepted_elsewhere for room $roomName")
                if (roomName.isNotEmpty()) {
                    pendingIncomingRooms.remove(roomName)
                    callDismissed.postValue(roomName)
                }
            } catch (e: Exception) {
                Log.e("CallsViewModel", "Error processing call:accepted_elsewhere", e)
            }
        }

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
        // ❌ Server-side call error (e.g. participant limit exceeded)
        socketManager.on("call:error") { args ->
            try {
                val data = args.getOrNull(0) as? JSONObject ?: return@on
                val type = data.optString("type", "")
                val message = data.optString("message", "Call error")
                Log.w("CallsViewModel", "📵 call:error type=$type message=$message")
                if (type == "PARTICIPANT_LIMIT_EXCEEDED") {
                    participantLimitError.postValue(
                        ParticipantLimitError(
                            message = message,
                            maxParticipants = data.optInt("maxParticipants", 5),
                            isPro = data.optBoolean("isPro", false)
                        )
                    )
                } else {
                    callError.postValue(message)
                }
            } catch (e: Exception) {
                Log.e("CallsViewModel", "Error processing call:error", e)
            }
        }

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

        // ==================== GROUP CALL SOCKET LISTENERS ====================

        // 📞 Входящий групповой звонок
        socketManager.on("group_call:incoming") { args ->
            try {
                val data = args.getOrNull(0) as? JSONObject ?: return@on
                Log.d("CallsViewModel", "📞 Group call incoming")

                val groupId = data.optInt("groupId", 0)
                val initiatedBy = data.optInt("initiatedBy", 0)
                val initiatorName = data.optString("initiatorName", "Unknown")
                val callType = data.optString("callType", "audio")
                val roomName = data.optString("roomName", "")
                val maxParticipants = data.optInt("maxParticipants", 5)
                val isPremiumCall = data.optBoolean("isPremiumCall", false)
                val groupName = data.optString("groupName", "Групповой звонок")

                if (initiatedBy == getUserId()) return@on // Не уведомлять инициатора
                if (roomName.isEmpty()) return@on

                val iceServersArray = data.optJSONArray("iceServers")
                if (iceServersArray != null) {
                    val iceServers = parseIceServers(iceServersArray)
                    groupWebRTCManager.setIceServers(iceServers)
                }

                val groupIncoming = GroupCallIncomingData(
                    groupId = groupId,
                    groupName = groupName,
                    initiatedBy = initiatedBy,
                    initiatorName = initiatorName,
                    initiatorAvatar = data.optString("initiatorAvatar", ""),
                    callType = callType,
                    roomName = roomName,
                    maxParticipants = maxParticipants,
                    isPremiumCall = isPremiumCall
                )
                incomingGroupCall.postValue(groupIncoming)

                // Service handles activity launch in both foreground and background.
                // ViewModel only as fallback if service was killed.
                if (!com.worldmates.messenger.services.MessageNotificationService.isRunning) {
                    val intent = android.content.Intent(
                        getApplication(), IncomingGroupCallActivity::class.java
                    ).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra("group_id", groupId)
                        putExtra("group_name", groupName)
                        putExtra("initiated_by", initiatedBy)
                        putExtra("initiator_name", initiatorName)
                        putExtra("initiator_avatar", data.optString("initiatorAvatar", ""))
                        putExtra("call_type", callType)
                        putExtra("room_name", roomName)
                        putExtra("max_participants", maxParticipants)
                        putExtra("is_premium_call", isPremiumCall)
                    }
                    getApplication<Application>().startActivity(intent)
                    Log.d("CallsViewModel", "📞 Launched IncomingGroupCallActivity (service not running)")
                } else {
                    Log.d("CallsViewModel", "📞 Service will handle group call UI")
                }
            } catch (e: Exception) {
                Log.e("CallsViewModel", "Error processing group_call:incoming", e)
            }
        }

        // 👥 Список текущих участников (получает новый участник при присоединении)
        socketManager.on("group_call:current_participants") { args ->
            try {
                val data = args.getOrNull(0) as? JSONObject ?: return@on
                val participants = data.optJSONArray("participants") ?: return@on
                Log.d("CallsViewModel", "👥 Current participants: ${participants.length()}")

                for (i in 0 until participants.length()) {
                    val p = participants.getJSONObject(i)
                    val userId = p.optLong("userId", 0L)
                    val userName = p.optString("userName", "Unknown")
                    val userAvatar = p.optString("userAvatar", "")
                    if (userId > 0 && userId != getUserId().toLong()) {
                        addOrUpdateGroupParticipant(userId, userName, userAvatar.ifEmpty { null })
                        // Заранее создаём PeerConnection, чтобы он был готов принять offer
                        // когда существующий участник его пришлёт
                        groupWebRTCManager.createPeerConnectionForPeer(userId)
                        Log.d("CallsViewModel", "🔌 Pre-created PeerConnection for existing peer $userId")
                    }
                }
            } catch (e: Exception) {
                Log.e("CallsViewModel", "Error processing group_call:current_participants", e)
            }
        }

        // 🔔 Новый участник присоединился — создать offer для него (существующие участники)
        socketManager.on("group_call:participant_joined") { args ->
            try {
                val data = args.getOrNull(0) as? JSONObject ?: return@on
                val userId = data.optLong("userId", 0L)
                val userName = data.optString("userName", "Unknown")
                val userAvatar = data.optString("userAvatar", "")
                // Дефолт — true: если сервер не прислал поле, существующий участник
                // всё равно создаёт offer (иначе новый участник застрянет в "connecting")
                val shouldCreateOffer = data.optBoolean("shouldCreateOffer", true)
                val roomName = data.optString("roomName", "")
                val iceServersArray = data.optJSONArray("iceServers")

                if (userId == getUserId().toLong()) return@on
                Log.d("CallsViewModel", "👤 Participant joined: $userName ($userId)")

                addOrUpdateGroupParticipant(userId, userName, userAvatar.ifEmpty { null })

                if (shouldCreateOffer) {
                    if (iceServersArray != null) {
                        val iceServers = parseIceServers(iceServersArray)
                        groupWebRTCManager.setIceServers(iceServers)
                    }
                    groupWebRTCManager.createPeerConnectionForPeer(userId)
                    groupWebRTCManager.createOfferForPeer(userId)
                }
            } catch (e: Exception) {
                Log.e("CallsViewModel", "Error processing group_call:participant_joined", e)
            }
        }

        // 📤 Получен offer от существующего участника (новый участник создаёт answer)
        socketManager.on("group_call:offer") { args ->
            try {
                val data = args.getOrNull(0) as? JSONObject ?: return@on
                val fromUserId = data.optLong("fromUserId", 0L)
                val sdpOffer = data.optString("sdpOffer", "")
                val iceServersArray = data.optJSONArray("iceServers")

                if (fromUserId == 0L || sdpOffer.isEmpty()) return@on
                Log.d("CallsViewModel", "📤 Received group offer from $fromUserId")

                if (iceServersArray != null) {
                    val iceServers = parseIceServers(iceServersArray)
                    groupWebRTCManager.setIceServers(iceServers)
                }

                groupWebRTCManager.handleOfferAndCreateAnswer(fromUserId, sdpOffer)
            } catch (e: Exception) {
                Log.e("CallsViewModel", "Error processing group_call:offer", e)
            }
        }

        // 📥 Получен answer от участника (после отправки ему offer)
        socketManager.on("group_call:answer") { args ->
            try {
                val data = args.getOrNull(0) as? JSONObject ?: return@on
                val fromUserId = data.optLong("fromUserId", 0L)
                val sdpAnswer = data.optString("sdpAnswer", "")

                if (fromUserId == 0L || sdpAnswer.isEmpty()) return@on
                Log.d("CallsViewModel", "📥 Received group answer from $fromUserId")

                groupWebRTCManager.handleAnswer(fromUserId, sdpAnswer)
            } catch (e: Exception) {
                Log.e("CallsViewModel", "Error processing group_call:answer", e)
            }
        }

        // 🧊 ICE candidate для конкретного участника группового звонка
        socketManager.on("group_call:ice_candidate") { args ->
            try {
                val data = args.getOrNull(0) as? JSONObject ?: return@on
                val fromUserId = data.optLong("fromUserId", 0L)
                val candidateSdp = data.optString("candidate", "")
                val sdpMLineIndex = data.optInt("sdpMLineIndex", 0)
                val sdpMid = data.optString("sdpMid", "")

                if (fromUserId == 0L || candidateSdp.isEmpty()) return@on

                val candidate = IceCandidate(sdpMid, sdpMLineIndex, candidateSdp)
                groupWebRTCManager.addIceCandidateForPeer(fromUserId, candidate)
            } catch (e: Exception) {
                Log.e("CallsViewModel", "Error processing group_call:ice_candidate", e)
            }
        }

        // 🚪 Участник покинул групповой звонок
        socketManager.on("group_call:participant_left") { args ->
            try {
                val data = args.getOrNull(0) as? JSONObject ?: return@on
                val userId = data.optLong("userId", 0L)
                if (userId == 0L) return@on
                Log.d("CallsViewModel", "🚪 Participant left: $userId")

                groupWebRTCManager.removePeer(userId)
                removeGroupParticipant(userId)
            } catch (e: Exception) {
                Log.e("CallsViewModel", "Error processing group_call:participant_left", e)
            }
        }

        // 🔴 Групповой звонок завершён инициатором
        socketManager.on("group_call:ended") { args ->
            try {
                val data = args.getOrNull(0) as? JSONObject ?: return@on
                val roomName = data.optString("roomName", "")
                Log.d("CallsViewModel", "🔴 Group call ended: $roomName")

                cleanupGroupCall()
                groupCallEnded.postValue(true)
            } catch (e: Exception) {
                Log.e("CallsViewModel", "Error processing group_call:ended", e)
            }
        }

        // 🔊 Изменение медиа участника
        socketManager.on("user:media_changed") { args ->
            try {
                val data = args.getOrNull(0) as? JSONObject ?: return@on
                val userId = data.optLong("userId", 0L)
                val audio = data.optBoolean("audio", true)
                val video = data.optBoolean("video", false)

                val existing = groupParticipantInfoMap[userId] ?: return@on
                groupParticipantInfoMap[userId] = existing.copy(
                    audioEnabled = audio,
                    videoEnabled = video
                )
                groupCallParticipants.postValue(groupParticipantInfoMap.values.toList())
            } catch (e: Exception) {
                Log.e("CallsViewModel", "Error processing user:media_changed", e)
            }
        }

        Log.d("CallsViewModel", "✅ Call Socket.IO listeners configured")
    }

    private fun setupGroupWebRTCCallbacks() {
        groupWebRTCManager.onIceCandidateReady = { toUserId, candidate ->
            currentGroupRoomName?.let { roomName ->
                val event = JSONObject().apply {
                    put("roomName", roomName)
                    put("fromUserId", getUserId())
                    put("toUserId", toUserId)
                    put("candidate", candidate.sdp)
                    put("sdpMLineIndex", candidate.sdpMLineIndex)
                    put("sdpMid", candidate.sdpMid ?: "")
                }
                socketManager.emit("group_call:ice_candidate", event)
            }
        }

        groupWebRTCManager.onOfferReady = { toUserId, sdp ->
            currentGroupRoomName?.let { roomName ->
                Log.d("CallsViewModel", "📤 Sending group_call:offer to user $toUserId")
                val event = JSONObject().apply {
                    put("roomName", roomName)
                    put("fromUserId", getUserId())
                    put("toUserId", toUserId)
                    put("sdpOffer", sdp.description)
                }
                socketManager.emit("group_call:offer", event)
            }
        }

        groupWebRTCManager.onAnswerReady = { toUserId, sdp ->
            currentGroupRoomName?.let { roomName ->
                Log.d("CallsViewModel", "📥 Sending group_call:answer to user $toUserId")
                val event = JSONObject().apply {
                    put("roomName", roomName)
                    put("fromUserId", getUserId())
                    put("toUserId", toUserId)
                    put("sdpAnswer", sdp.description)
                }
                socketManager.emit("group_call:answer", event)
            }
        }

        groupWebRTCManager.onRemoteStreamAdded = { userId, stream ->
            Log.d("CallsViewModel", "✅ Group: remote stream from user $userId")
            updateGroupParticipantStream(userId, stream)
        }

        groupWebRTCManager.onRemoteStreamRemoved = { userId ->
            Log.d("CallsViewModel", "🔴 Group: remote stream removed from user $userId")
            removeGroupParticipant(userId)
        }

        groupWebRTCManager.onPeerConnected = { userId ->
            Log.d("CallsViewModel", "🔗 Group: ICE connected with user $userId")
            val existing = groupParticipantInfoMap[userId]
            if (existing != null) {
                groupParticipantInfoMap[userId] = existing.copy(connectionState = "connected")
                groupCallParticipants.postValue(groupParticipantInfoMap.values.toList())
            }
        }
    }

    private fun updateGroupParticipantStream(userId: Long, stream: MediaStream) {
        val existing = groupParticipantInfoMap[userId]
        if (existing != null) {
            groupParticipantInfoMap[userId] = existing.copy(
                mediaStream = stream,
                videoEnabled = stream.videoTracks.isNotEmpty() && stream.videoTracks[0].enabled(),
                connectionState = "connected"
            )
        } else {
            groupParticipantInfoMap[userId] = GroupCallParticipant(
                userId = userId,
                name = getApplication<Application>().getString(R.string.group_call_participant_fallback),
                mediaStream = stream,
                videoEnabled = stream.videoTracks.isNotEmpty(),
                connectionState = "connected"
            )
        }
        groupCallParticipants.postValue(groupParticipantInfoMap.values.toList())
    }

    private fun removeGroupParticipant(userId: Long) {
        groupParticipantInfoMap.remove(userId)
        groupCallParticipants.postValue(groupParticipantInfoMap.values.toList())
    }

    private fun addOrUpdateGroupParticipant(userId: Long, name: String, avatar: String?, connectionState: String = "connecting") {
        val existing = groupParticipantInfoMap[userId]
        groupParticipantInfoMap[userId] = existing?.copy(
            name = name,
            avatar = avatar,
            connectionState = connectionState
        ) ?: GroupCallParticipant(
            userId = userId,
            name = name,
            avatar = avatar,
            connectionState = connectionState
        )
        groupCallParticipants.postValue(groupParticipantInfoMap.values.toList())
    }

    private fun setupWebRTCListeners() {
        setupGroupWebRTCCallbacks()
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
            if (state == PeerConnection.IceConnectionState.CONNECTED ||
                state == PeerConnection.IceConnectionState.COMPLETED) {
                callFullyEstablished = true
            }
        }

        // ✅ Renegotiation (добавление/удаление треков во время активного звонка).
        // Намеренно NOT вызываем до полного установления ICE, иначе первый onRenegotiationNeeded,
        // который браузерный WebRTC вызывает сразу после addTrack() на стороне callee
        // (пока signaling state ещё STABLE после ответа), создаёт лишний offer и ломает хендшейк.
        webRTCManager.onRenegotiationNeededListener = {
            Log.d("CallsViewModel", "🔄 Renegotiation needed (established=$callFullyEstablished)")
            if (currentCallData != null && callFullyEstablished) {
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
     * Ініціювати груповий дзвінок (mesh архітектура).
     * Ініціатор заходить у кімнату та чекає поки інші приймуть дзвінок.
     */
    fun initiateGroupCall(groupId: Int, groupName: String, callType: String = "audio") {
        val callLogic: () -> Unit = {
            setupCallAudio(isVideoCall = callType == "video")
            viewModelScope.launch {
                try {
                    val iceServers = fetchIceServersFromApi()
                    if (iceServers != null) {
                        groupWebRTCManager.setIceServers(iceServers)
                    }

                    val audioOnly = callType != "video"
                    groupWebRTCManager.setupLocalMedia(audioOnly = audioOnly)

                    val localStream = groupWebRTCManager.getLocalMediaStream()
                    localStream?.let { localStreamAdded.postValue(it) }

                    val roomName = generateRoomName()
                    currentGroupRoomName = roomName
                    currentGroupId = groupId
                    isGroupCallInitiator = true

                    // Уведомить сервер о начале группового звонка (без SDP — mesh)
                    val groupCallEvent = JSONObject().apply {
                        put("fromId", getUserId())
                        put("groupId", groupId)
                        put("callType", callType)
                        put("roomName", roomName)
                        put("groupName", groupName)
                    }
                    socketManager.emit("call:initiate", groupCallEvent)
                    Log.d("CallsViewModel", "Group call initiated for group $groupId, room=$roomName")

                    // Инициатор также присоединяется к комнате
                    joinGroupCallRoom(roomName, groupId)

                } catch (e: Exception) {
                    callError.postValue(e.message)
                    Log.e("CallsViewModel", "Error initiating group call", e)
                }
            }
        }

        if (socketConnected.value == true) {
            callLogic()
        } else {
            pendingCallInitiation = callLogic
        }
    }

    /**
     * Принять входящий групповой звонок.
     * Вызывается когда пользователь нажимает "Принять" в уведомлении.
     */
    fun joinGroupCall(groupCallData: GroupCallIncomingData) {
        val joinLogic: () -> Unit = {
            setupCallAudio(isVideoCall = groupCallData.callType == "video")
            viewModelScope.launch {
                try {
                    val iceServers = fetchIceServersFromApi()
                    if (iceServers != null) {
                        groupWebRTCManager.setIceServers(iceServers)
                    }

                    val audioOnly = groupCallData.callType != "video"
                    groupWebRTCManager.setupLocalMedia(audioOnly = audioOnly)

                    val localStream = groupWebRTCManager.getLocalMediaStream()
                    localStream?.let { localStreamAdded.postValue(it) }

                    currentGroupRoomName = groupCallData.roomName
                    currentGroupId = groupCallData.groupId
                    currentGroupMaxParticipants = groupCallData.maxParticipants
                    isGroupCallInitiator = false

                    // Присоединиться к комнате
                    joinGroupCallRoom(groupCallData.roomName, groupCallData.groupId)

                    incomingGroupCall.postValue(null)
                    groupCallStarted.postValue(true)
                } catch (e: Exception) {
                    callError.postValue(e.message)
                    Log.e("CallsViewModel", "Error joining group call", e)
                }
            }
        }

        if (socketConnected.value == true) {
            joinLogic()
        } else {
            pendingCallInitiation = joinLogic
        }
    }

    private fun joinGroupCallRoom(roomName: String, groupId: Int) {
        val event = JSONObject().apply {
            put("roomName", roomName)
            put("userId", getUserId())
            put("groupId", groupId)
        }
        socketManager.emit("group_call:join", event)
        Log.d("CallsViewModel", "📍 Joined group call room: $roomName")
    }

    /**
     * Участник покидает групповой звонок (остальные продолжают).
     */
    fun leaveGroupCall() {
        val roomName = currentGroupRoomName ?: return
        val event = JSONObject().apply {
            put("roomName", roomName)
            put("userId", getUserId())
        }
        socketManager.emit("group_call:leave", event)

        cleanupGroupCall()
        groupCallEnded.postValue(true)
        Log.d("CallsViewModel", "Left group call room: $roomName")
    }

    /**
     * Инициатор завершает весь групповой звонок для всех.
     */
    fun endGroupCallForAll() {
        val roomName = currentGroupRoomName ?: run {
            leaveGroupCall()
            return
        }
        val event = JSONObject().apply {
            put("roomName", roomName)
            put("userId", getUserId())
        }
        socketManager.emit("group_call:end", event)

        cleanupGroupCall()
        groupCallEnded.postValue(true)
        Log.d("CallsViewModel", "Ended group call for all: $roomName")
    }

    private fun cleanupGroupCall() {
        groupWebRTCManager.close()
        groupParticipantInfoMap.clear()
        groupCallParticipants.postValue(emptyList())
        currentGroupRoomName = null
        currentGroupId = 0
        isGroupCallInitiator = false
        releaseCallAudio()
    }

    fun toggleGroupAudio(enabled: Boolean) {
        groupWebRTCManager.setAudioEnabled(enabled)
        val roomName = currentGroupRoomName ?: return
        val event = JSONObject().apply {
            put("roomName", roomName)
            put("userId", getUserId())
            put("audio", enabled)
            put("video", groupWebRTCManager.isVideoEnabled())
        }
        socketManager.emit("call:toggle_media", event)
    }

    fun toggleGroupVideo(enabled: Boolean) {
        groupWebRTCManager.setVideoEnabled(enabled)
        // Re-post local stream so the UI recomposes and picks up the video track
        groupWebRTCManager.getLocalMediaStream()?.let { localStreamAdded.postValue(it) }
        val roomName = currentGroupRoomName ?: return
        val event = JSONObject().apply {
            put("roomName", roomName)
            put("userId", getUserId())
            put("audio", groupWebRTCManager.isAudioEnabled())
            put("video", enabled)
        }
        socketManager.emit("call:toggle_media", event)
    }

    fun getGroupLocalStream(): MediaStream? = groupWebRTCManager.getLocalMediaStream()

    fun isGroupCallInitiator(): Boolean = isGroupCallInitiator

    fun getCurrentGroupMaxParticipants(): Int = currentGroupMaxParticipants

    /**
     * Прийняти вхідний вызов
     *
     * ✅ ВИПРАВЛЕНО: Тепер правильно обробляє випадок коли Socket ще не підключений
     * і отримує ICE сервери ПЕРЕД створенням PeerConnection
     */
    // Guard against acceptCall() being called multiple times for the same room
    @Volatile private var acceptedRoomName: String? = null

    fun acceptCall(callData: CallData) {
        Log.d("CallsViewModel", "📞 acceptCall() called for room: ${callData.roomName}")
        pendingIncomingRooms.remove(callData.roomName)

        // ✅ Prevent double-accept: if we already accepted this room, skip
        if (acceptedRoomName == callData.roomName) {
            Log.w("CallsViewModel", "⚠️ Already accepted room ${callData.roomName}, ignoring duplicate acceptCall()")
            return
        }
        acceptedRoomName = callData.roomName

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
        pendingIncomingRooms.remove(roomName)
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

        callFullyEstablished = false
        acceptedRoomName = null
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

            // Acquire PARTIAL_WAKE_LOCK so CPU/mic stay alive when screen turns off
            val pm = getApplication<Application>().getSystemService(Context.POWER_SERVICE) as PowerManager
            callWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WorldMates:CallWakeLock")
            callWakeLock?.acquire(4 * 60 * 60 * 1000L) // max 4 h

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
            // Release wake lock
            callWakeLock?.let { if (it.isHeld) it.release() }
            callWakeLock = null

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

    // ─── Call Reactions ──────────────────────────────────────────────────────

    /** Incoming reaction from the other party (emoji + sender name) */
    val incomingReaction = MutableLiveData<Pair<String, String>?>()

    /** Chat messages during call: list of (senderName, text) */
    val callChatMessages = MutableLiveData<List<Pair<String, String>>>(emptyList())

    /**
     * Send a reaction during a call via Socket.IO
     */
    fun sendCallReaction(emoji: String) {
        val roomName = currentCallData?.roomName ?: currentGroupRoomName ?: return
        val data = JSONObject().apply {
            put("roomName", roomName)
            put("userId", getUserId())
            put("userName", getUserName())
            put("emoji", emoji)
        }
        socketManager.emit("call:reaction", data)
        Log.d("CallsViewModel", "Sent call reaction: $emoji to room $roomName")
        // Show own reaction locally
        incomingReaction.postValue(Pair(emoji, getUserName()))
    }

    /**
     * Send a chat message during a call via Socket.IO
     */
    fun sendCallChatMessage(text: String) {
        val roomName = currentCallData?.roomName ?: currentGroupRoomName ?: return
        val userName = getUserName()
        val data = JSONObject().apply {
            put("roomName", roomName)
            put("userId", getUserId())
            put("userName", userName)
            put("text", text)
        }
        socketManager.emit("call:chat_message", data)
        // Add to local list
        val current = callChatMessages.value ?: emptyList()
        callChatMessages.postValue(current + Pair(userName, text))
    }

    /**
     * 📹 Отримати поточну якість відео
     */
    fun getVideoQuality(): com.worldmates.messenger.network.VideoQuality {
        return webRTCManager.getVideoQuality()
    }

    /** StateFlow: адаптивно змінюється при зміні якості мережі */
    val adaptiveVideoQuality = webRTCManager.adaptiveBitrateQuality

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

    // ─── Screen sharing ───────────────────────────────────────────────────────

    /** LiveData: чи активна трансляція екрану */
    val isScreenSharing = androidx.lifecycle.MutableLiveData(false)

    fun setScreenSharingState(active: Boolean) {
        isScreenSharing.postValue(active)
    }

    fun addScreenTrack(screenTrack: org.webrtc.VideoTrack) {
        webRTCManager.addScreenTrack(screenTrack)
    }

    fun removeScreenTrack() {
        webRTCManager.removeScreenTrack()
    }

    /** Надіслати socket-подію про початок/зупинку трансляції. */
    fun emitScreenSharingEvent(started: Boolean) {
        try {
            val event = if (started) "screen:start" else "screen:stop"
            val payload = org.json.JSONObject().apply {
                put("user_id", getUserId())
            }
            socketManager.emit(event, payload)
            Log.d("CallsViewModel", "Screen sharing event: $event")
        } catch (e: Exception) {
            Log.e("CallsViewModel", "Failed to emit screen sharing event", e)
        }
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

        // ✅ Налаштувати listeners для call events (тільки один раз — Socket.IO зберігає listeners між reconnect)
        if (!callListenersSetUp) {
            callListenersSetUp = true
            setupCallSocketListeners()
        }

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

            if (currentCallData?.roomName == roomName
                || acceptedRoomName == roomName
                || !pendingIncomingRooms.add(roomName)) {
                Log.d("CallsViewModel", "⚠️ Ignoring duplicate incoming call for room: $roomName (currentCall=${currentCallData?.roomName}, accepted=$acceptedRoomName)")
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
                pendingIncomingRooms.remove(roomName)
                return
            }

            incomingCall.postValue(callData)

            Log.d("CallsViewModel", "📞 Incoming call from ${callData.fromName}")

            // Launch IncomingCallActivity only when MessageNotificationService is NOT running.
            // Service handles the launch in both foreground (no notification) and background
            // (with full-screen notification). ViewModel is a fallback for when service was
            // killed by battery optimization.
            if (!com.worldmates.messenger.services.MessageNotificationService.isRunning) {
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
                Log.d("CallsViewModel", "📞 Launched IncomingCallActivity (service not running)")
            } else {
                Log.d("CallsViewModel", "📞 Service is running — it will handle the call UI")
            }

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
     * Uses Socket.IO acknowledgments for synchronous request-response.
     * Falls back to locally-computed HMAC TURN credentials if server is unavailable.
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

            // Fallback: compute HMAC TURN credentials locally using coturn static-auth-secret
            Log.w("CallsViewModel", "⚠️ Using HMAC TURN fallback credentials")
            buildHmacTurnServers()
        } catch (e: Exception) {
            Log.e("CallsViewModel", "❌ Error fetching ICE servers via Socket.IO — using HMAC fallback", e)
            buildHmacTurnServers()
        }
    }

    /**
     * Compute coturn time-limited credentials from the static-auth-secret (HMAC-SHA1).
     * This matches the coturn `use-auth-secret` / `lt-cred-mech` configuration on worldmates.club.
     * Format: username = "<expiry_unix>:<userId>",  password = Base64(HMAC-SHA1(username, secret))
     */
    private fun buildHmacTurnServers(): List<PeerConnection.IceServer> {
        return try {
            val expiry = System.currentTimeMillis() / 1000 + 86400   // valid 24 h
            val username = "$expiry:${getUserId()}"
            val mac = Mac.getInstance("HmacSHA1")
            mac.init(SecretKeySpec(TURN_SECRET.toByteArray(Charsets.UTF_8), "HmacSHA1"))
            val credential = Base64.encodeToString(
                mac.doFinal(username.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP
            )
            Log.d("CallsViewModel", "🔑 HMAC TURN creds built for user ${getUserId()}")
            listOf(
                PeerConnection.IceServer.builder("stun:$TURN_HOST_1:3478").createIceServer(),
                PeerConnection.IceServer.builder("stun:$TURN_HOST_2:3478").createIceServer(),
                PeerConnection.IceServer.builder("stun:$TURN_HOST_3:3478").createIceServer(),
                PeerConnection.IceServer.builder(listOf(
                    "turn:$TURN_HOST_1:3478?transport=udp",
                    "turn:$TURN_HOST_1:3478?transport=tcp"
                )).setUsername(username).setPassword(credential).createIceServer(),
                PeerConnection.IceServer.builder(listOf(
                    "turn:$TURN_HOST_2:3478?transport=udp",
                    "turn:$TURN_HOST_2:3478?transport=tcp"
                )).setUsername(username).setPassword(credential).createIceServer(),
                PeerConnection.IceServer.builder(listOf(
                    "turn:$TURN_HOST_3:3478?transport=udp",
                    "turn:$TURN_HOST_3:3478?transport=tcp"
                )).setUsername(username).setPassword(credential).createIceServer(),
                // TURNS TLS — по домену (SSL сертификат привязан к домену, не к IP)
                PeerConnection.IceServer.builder("turns:$TURNS_DOMAIN:5349?transport=tcp")
                    .setUsername(username).setPassword(credential).createIceServer()
            )
        } catch (e: Exception) {
            Log.e("CallsViewModel", "❌ Failed to build HMAC TURN servers", e)
            listOf(PeerConnection.IceServer.builder("stun:$TURN_HOST_1:3478").createIceServer())
        }
    }

    /**
     * 🎥 Отримати локальний медіа стрім
     */
    fun getLocalStream(): MediaStream? {
        return webRTCManager.getLocalMediaStream()
    }

    // ─── Video Filter API ─────────────────────────────────────────────────────

    /**
     * Застосовує відеофільтр [filter] до поточного відеопотоку.
     * Зміна відбувається миттєво — наступний кадр вже буде з фільтром.
     */
    fun applyVideoFilter(filter: VideoFilterType) {
        webRTCManager.setVideoFilter(filter)
        _activeVideoFilter.value = filter
        Log.d("CallsViewModel", "🎨 Video filter: $filter")
    }

    /**
     * Встановлює інтенсивність поточного фільтру (0.0 – 1.0).
     */
    fun setFilterIntensity(intensity: Float) {
        webRTCManager.videoFilterManager.intensity = intensity.coerceIn(0f, 1f)
    }

    // ─── Virtual Background API ───────────────────────────────────────────────

    /**
     * Застосовує режим віртуального фону [mode].
     * Для [VirtualBgMode.CUSTOM_IMAGE] спочатку завантажте зображення через [loadCustomBackground].
     */
    fun applyVirtualBackground(mode: VirtualBgMode) {
        webRTCManager.setVirtualBackground(mode)
        _activeVirtualBg.value = mode
        Log.d("CallsViewModel", "🖼️ Virtual background: $mode")
    }

    /**
     * Завантажує кастомне фонове зображення з [uri] і переключає режим на CUSTOM_IMAGE.
     */
    fun loadCustomBackground(uri: Uri) {
        webRTCManager.virtualBgManager.loadCustomBackground(uri)
        applyVirtualBackground(VirtualBgMode.CUSTOM_IMAGE)
    }

    // ─── Call Transfer API ────────────────────────────────────────────────────

    /**
     * Ініціалізує [CallTransferManager] після підключення Socket.IO.
     * Викликати один раз при встановленні сокету.
     */
    fun initCallTransferManager() {
        val socket = socketManager.getSocket() ?: return
        if (_callTransferManager != null) return

        val manager = CallTransferManager(socket)
        manager.onTransferCompleted = {
            // Ініціатор завершує свій дзвінок після успішної передачі
            viewModelScope.launch(Dispatchers.Main) {
                endCall()
            }
        }
        manager.onTransferFailed = { reason ->
            viewModelScope.launch(Dispatchers.Main) {
                callError.value = "transfer_failed:$reason"
            }
        }
        manager.registerListeners()
        _callTransferManager = manager
        Log.d("CallsViewModel", "✅ CallTransferManager initialized")
    }

    /**
     * Ініціює передачу поточного дзвінка [target]-у.
     */
    fun initiateCallTransfer(target: TransferTarget) {
        val roomName = currentCallData?.roomName ?: return
        // callType зберігається як рядок "video"/"audio" у CallData
        val callType = currentCallData?.callType ?: "video"
        // getUserId() вже існує в класі (повертає Int); конвертуємо у Long для CallTransferManager
        val fromUserId = getUserId().toLong()
        _callTransferManager?.initiateTransfer(target, roomName, callType, fromUserId)
    }

    // ─── Conference Optimization API ──────────────────────────────────────────

    /**
     * Змінює режим пропускної здатності для групового дзвінка.
     * Відразу застосовує нові bitrate до всіх PeerConnections.
     */
    fun setConferenceBandwidthMode(mode: GroupWebRTCManager.ConferenceBandwidthMode) {
        groupWebRTCManager.bandwidthMode = mode
        _conferenceBandwidthMode.value = mode
        groupWebRTCManager.optimizeBitrateForParticipantCount()
        Log.d("CallsViewModel", "📊 Conference bandwidth mode: $mode")
    }

    /**
     * Повідомляє менеджер про зміну видимих учасників (прокрутка grid-у).
     * [visibleUserIds] — множина userId, які зараз відображаються на екрані.
     */
    fun updateVisibleConferenceParticipants(visibleUserIds: Set<Long>) {
        groupWebRTCManager.updateVisibleParticipants(visibleUserIds)
    }

    /**
     * Встановлює домінуючого спікера (отримується від сервера або локально).
     */
    fun setDominantSpeaker(userId: Long?) {
        groupWebRTCManager.setDominantSpeaker(userId)
    }

    override fun onCleared() {
        super.onCleared()
        _callTransferManager?.unregisterListeners()
        _callTransferManager?.reset()
        _callTransferManager = null
        webRTCManager.close()
        groupWebRTCManager.close()
        socketManager.disconnect()
    }
}

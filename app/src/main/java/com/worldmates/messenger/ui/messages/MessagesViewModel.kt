package com.worldmates.messenger.ui.messages

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.R
import com.worldmates.messenger.data.Constants
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.data.model.MediaAutoDeleteOption
import com.worldmates.messenger.data.model.Message
import com.worldmates.messenger.data.model.MessageReaction
import com.worldmates.messenger.data.model.ReactionGroup
import com.worldmates.messenger.data.model.UserPresenceStatus
import com.worldmates.messenger.network.FileManager
import com.worldmates.messenger.network.MediaUploader
import com.worldmates.messenger.network.MediaLoadingManager
import com.worldmates.messenger.network.NetworkQualityMonitor
import com.worldmates.messenger.network.NodeRetrofitClient
import com.worldmates.messenger.network.SocketManager
import com.worldmates.messenger.utils.DecryptionUtility
import com.worldmates.messenger.utils.signal.SignalEncryptionService
import com.worldmates.messenger.utils.signal.SignalGroupEncryptionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.worldmates.messenger.ui.messages.selection.ForwardRecipient
import com.worldmates.messenger.data.repository.DraftRepository
import com.worldmates.messenger.data.repository.LiveLocationManager
import com.worldmates.messenger.data.repository.LocationRepository
import com.worldmates.messenger.data.local.AppDatabase
import com.worldmates.messenger.data.local.entity.CachedMessage
import com.worldmates.messenger.data.local.entity.Draft
import kotlinx.coroutines.isActive
import org.json.JSONObject
import java.io.File

class MessagesViewModel(application: Application) :
    AndroidViewModel(application), SocketManager.ExtendedSocketListener {

    private val context = application
    private val nodeApi = NodeRetrofitClient.api
    private val groupApi = NodeRetrofitClient.groupApi
    private val db by lazy { AppDatabase.getInstance(application) }

    companion object {
        private const val TAG = "MessagesViewModel"
        private const val DRAFT_AUTO_SAVE_DELAY = 5000L // 5 секунд
    }

    /** Signal Protocol E2EE service — private chats (Double Ratchet). */
    private val signalService: SignalEncryptionService by lazy {
        SignalEncryptionService.getInstance(getApplication(), nodeApi)
    }

    /** Signal Sender Key E2EE service — group chats. */
    private val signalGroupService: SignalGroupEncryptionService by lazy {
        SignalGroupEncryptionService.getInstance(getApplication(), nodeApi, signalService)
    }

    init {
        Log.d(TAG, "🚀 MessagesViewModel створено!")
        Log.d(TAG, "Access Token: ${UserSession.accessToken?.take(10)}...")
        Log.d(TAG, "User ID: ${UserSession.userId}")
        // Ensure Signal public keys are registered with the server (runs once per install)
        viewModelScope.launch {
            signalService.ensureRegistered()
        }
    }

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    // 💫 Messages currently playing the Thanos disintegration animation
    private val _deletingMessages = MutableStateFlow<Set<Long>>(emptySet())
    val deletingMessages: StateFlow<Set<Long>> = _deletingMessages.asStateFlow()

    // 🗑️ IDs permanently deleted this session — prevents polling from re-adding them
    private val permanentlyDeletedIds = mutableSetOf<Long>()

    /**
     * Thread-safe messages setter that always strips [permanentlyDeletedIds].
     * Use this instead of `_messages.value = ...` whenever setting a fetched list.
     */
    private fun setMessagesSafe(list: List<Message>) {
        val filtered = if (permanentlyDeletedIds.isEmpty()) list
                       else list.filter { it.id !in permanentlyDeletedIds }
        _messages.value = filtered
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _uploadProgress = MutableStateFlow(0)
    val uploadProgress: StateFlow<Int> = _uploadProgress

    // Combined presence/activity status for the chat header
    private val _presenceStatus = MutableStateFlow<UserPresenceStatus>(UserPresenceStatus.Offline)
    val presenceStatus: StateFlow<UserPresenceStatus> = _presenceStatus

    // Internal flags used to restore the base status after a transient action ends
    private var presenceIsOnline = false
    private var presenceLastSeenTs = 0L

    // Auto-reset jobs for typing indicators (cancels stale "typing" state if stop-event is missed)
    private var typingAutoResetJob: Job? = null
    private var groupTypingAutoResetJob: Job? = null

    private fun basePresenceStatus(): UserPresenceStatus = when {
        presenceIsOnline -> UserPresenceStatus.Online
        presenceLastSeenTs > 0L -> UserPresenceStatus.LastSeen(presenceLastSeenTs)
        else -> UserPresenceStatus.Offline
    }

    private val _forwardContacts = MutableStateFlow<List<ForwardRecipient>>(emptyList())
    val forwardContacts: StateFlow<List<ForwardRecipient>> = _forwardContacts

    private val _forwardGroups = MutableStateFlow<List<ForwardRecipient>>(emptyList())
    val forwardGroups: StateFlow<List<ForwardRecipient>> = _forwardGroups

    // ==================== GROUPS ====================
    private val _currentGroup = MutableStateFlow<com.worldmates.messenger.data.model.Group?>(null)
    val currentGroup: StateFlow<com.worldmates.messenger.data.model.Group?> = _currentGroup
    // ==================== END GROUPS ====================

    // ==================== PRIVATE CHAT PIN ====================
    // Supports up to 5 pinned messages (backend limit MAX_PINS=5)
    private val _pinnedPrivateMessages = MutableStateFlow<List<Message>>(emptyList())
    val pinnedPrivateMessages: StateFlow<List<Message>> = _pinnedPrivateMessages
    // Single-pin convenience (first pinned message) — used by existing MessagesScreen banner
    val pinnedPrivateMessage: StateFlow<Message?> get() =
        object : StateFlow<Message?> {
            override val value get() = _pinnedPrivateMessages.value.firstOrNull()
            override val replayCache get() = listOf(value)
            override suspend fun collect(collector: kotlinx.coroutines.flow.FlowCollector<Message?>) =
                _pinnedPrivateMessages.collect { collector.emit(it.firstOrNull()) }
        }
    // ==================== END PRIVATE CHAT PIN ====================

    // ==================== PRIVATE CHAT MUTE ====================
    private val _isMutedPrivate = MutableStateFlow(false)
    val isMutedPrivate: StateFlow<Boolean> = _isMutedPrivate
    // ==================== END PRIVATE CHAT MUTE ====================

    // ==================== MEDIA AUTO-DELETE ====================
    private val _mediaAutoDeleteOption = MutableStateFlow(MediaAutoDeleteOption.NEVER)
    val mediaAutoDeleteOption: StateFlow<MediaAutoDeleteOption> = _mediaAutoDeleteOption
    // ==================== END MEDIA AUTO-DELETE ====================

    // ==================== LOAD MORE (PAGINATION) ====================
    private val _canLoadMore = MutableStateFlow(true)
    val canLoadMore: StateFlow<Boolean> = _canLoadMore
    private var isLoadingMore = false
    // ==================== END LOAD MORE ====================

    // ==================== SEARCH ====================
    private val _searchResults = MutableStateFlow<List<Message>>(emptyList())
    val searchResults: StateFlow<List<Message>> = _searchResults

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _searchTotalCount = MutableStateFlow(0)
    val searchTotalCount: StateFlow<Int> = _searchTotalCount

    private val _currentSearchIndex = MutableStateFlow(0)
    val currentSearchIndex: StateFlow<Int> = _currentSearchIndex

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching
    // ==================== END SEARCH ====================

    // ==================== DRAFTS ====================
    private val draftRepository = DraftRepository.getInstance(context)

    private val _currentDraft = MutableStateFlow<String>("")
    val currentDraft: StateFlow<String> = _currentDraft

    private val _isDraftSaving = MutableStateFlow(false)
    val isDraftSaving: StateFlow<Boolean> = _isDraftSaving

    private var draftAutoSaveJob: Job? = null
    // ==================== END DRAFTS ====================

    // ==================== ADAPTIVE TRANSPORT ====================
    private val _connectionQuality = MutableStateFlow(
        NetworkQualityMonitor.ConnectionQuality.GOOD
    )
    val connectionQuality: StateFlow<NetworkQualityMonitor.ConnectionQuality> = _connectionQuality

    // MediaLoadingManager для прогресивного завантаження медіа
    private val mediaLoader by lazy {
        MediaLoadingManager(context)
    }

    private var qualityMonitorJob: Job? = null
    // ==================== END ADAPTIVE TRANSPORT ====================

    /**
     * In-memory plaintext cache for messages sent this session.
     * Keyed by server message ID. Populated immediately after a successful send
     * (alongside the Signal DB cache) so that a concurrent fetchMessages() call
     * triggered by a socket event doesn't show "encrypted_message" for our own
     * just-sent message during the brief window before the Signal DB cache is
     * confirmed written.  Cleared only when the ViewModel is destroyed.
     */
    private val sentPlaintextCache = java.util.concurrent.ConcurrentHashMap<Long, String>()

    private var recipientId: Long = 0
    private var groupId: Long = 0
    private var topicId: Long = 0 // 📁 Topic/Subgroup ID for topic-based filtering
    private var isBusinessChat: Boolean = false
    private var socketManager: SocketManager? = null
    private var mediaUploader: MediaUploader? = null
    private var fileManager: FileManager? = null
    private var messagePollingJob: Job? = null

    // ── Live Location ─────────────────────────────────────────────────────────
    private var liveLocationManager: LiveLocationManager? = null
    /** Позиції інших учасників, які зараз діляться геолокацією: userId → LatLng. */
    val liveLocations: StateFlow<Map<Long, com.google.android.gms.maps.model.LatLng>>
        get() = liveLocationManager?.liveLocations ?: MutableStateFlow(emptyMap())
    val isSharingLocation: StateFlow<Boolean>
        get() = liveLocationManager?.isSharingLocation ?: MutableStateFlow(false)

    // 🎥 Публічні getters для відеодзвінків
    fun getRecipientId(): Long = recipientId
    fun getGroupId(): Long = groupId
    fun getTopicId(): Long = topicId

    fun initialize(recipientId: Long, isBusinessChat: Boolean = false) {
        Log.d(TAG, "🔧 initialize() for user $recipientId isBusinessChat=$isBusinessChat")
        this.recipientId = recipientId
        this.isBusinessChat = isBusinessChat
        this.groupId = 0
        this.topicId = 0
        fetchMessages()
        setupSocket()
        startMessagePolling()
        loadDraft()
        markSeen()
        fetchRecipientStatus()  // initialise header status bar without waiting for socket events
        loadMuteStatusViaNode(viewModelScope, nodeApi, recipientId) { isMuted ->
            _isMutedPrivate.value = isMuted
        }
        loadMediaAutoDeleteSettingViaNode(viewModelScope, nodeApi, recipientId) { option ->
            _mediaAutoDeleteOption.value = option
        }
        // Log Signal session status for debug
        viewModelScope.launch {
            val keyStore = com.worldmates.messenger.utils.signal.SignalKeyStore(getApplication())
            val hasSession  = keyStore.hasSession(recipientId)
            val isRegistered = keyStore.isRegistered()
            val opkCount    = keyStore.opkCount()
            val ikPub       = keyStore.getIdentityPublicKeyBase64()?.take(8) ?: "none"
            Log.i(TAG, "🔑 [Signal] user=$recipientId session=$hasSession " +
                "registered=$isRegistered opk_pool=$opkCount ik=${ikPub}...")
        }
        Log.d(TAG, "✅ initialize() done for user $recipientId")
    }

    fun initializeGroup(groupId: Long, topicId: Long = 0) {
        this.groupId = groupId
        this.recipientId = 0
        this.topicId = topicId
        fetchGroupDetails(groupId)
        fetchGroupMessages()
        setupSocket()
        startMessagePolling()
        loadDraft()
        if (topicId != 0L) {
            Log.d("MessagesViewModel", "Ініціалізація для групи $groupId, topic $topicId")
        } else {
            Log.d("MessagesViewModel", "Ініціалізація для групи $groupId")
        }
    }

    /**
     * 📌 Отримати деталі групи (включаючи закріплене повідомлення)
     */
    private fun fetchGroupDetails(groupId: Long) {
        viewModelScope.launch {
            try {
                val response = groupApi.getGroupDetails(groupId)

                if (response.apiStatus == 200 && response.group != null) {
                    _currentGroup.value = response.group
                    Log.d(TAG, "📌 Group details loaded: ${response.group.name}, pinned: ${response.group.pinnedMessage != null}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error fetching group details", e)
            }
        }
    }

    /**
     * Завантажує історію повідомлень для особистого чату
     */
    fun fetchMessages(beforeMessageId: Long = 0) {
        if (UserSession.accessToken == null || recipientId == 0L) {
            _error.value = "Помилка: не авторизовано або невірний ID"
            return
        }

        _isLoading.value = true

        viewModelScope.launch {
            try {
                val response = nodeApi.getMessages(
                    recipientId    = recipientId,
                    limit          = Constants.MESSAGES_PAGE_SIZE,
                    beforeMessageId = beforeMessageId,
                    isBusinessChat = if (isBusinessChat) 1 else 0
                )

                if (response.apiStatus == 200 && response.messages != null) {
                    val decryptedMessages = response.messages.decryptAll()

                    val currentMessages = _messages.value.toMutableList()
                    currentMessages.addAll(decryptedMessages)
                    // Сортируем по времени (старые сверху, новые внизу)
                    setMessagesSafe(currentMessages.distinctBy { it.id }.sortedBy { it.timeStamp })

                    _error.value = null
                    Log.d("MessagesViewModel", "Завантажено ${decryptedMessages.size} повідомлень (Node.js)")
                } else {
                    _error.value = response.errorMessage ?: "Помилка завантаження повідомлень"
                    Log.e("MessagesViewModel", "Node.js Error: ${response.apiStatus}")
                }

                _isLoading.value = false
            } catch (e: Exception) {
                _error.value = "Помилка: ${e.localizedMessage}"
                _isLoading.value = false
                Log.e("MessagesViewModel", "Помилка завантаження повідомлень", e)
            }
        }
    }

    /**
     * Loads older messages (pagination) — triggered when user scrolls to the top.
     * Uses /api/node/chat/loadmore with the oldest message id as cursor.
     */
    fun loadMore() {
        if (recipientId == 0L || isLoadingMore || !_canLoadMore.value) return

        val oldestId = _messages.value.minByOrNull { it.timeStamp }?.id ?: return

        isLoadingMore = true
        viewModelScope.launch {
            try {
                val response = nodeApi.loadMore(
                    recipientId    = recipientId,
                    beforeMessageId = oldestId,
                    limit          = 30,
                    isBusinessChat = if (isBusinessChat) 1 else 0
                )
                if (response.apiStatus == 200 && response.messages != null) {
                    val older = response.messages.decryptAll()
                    if (older.isEmpty()) {
                        _canLoadMore.value = false
                        Log.d(TAG, "📜 loadMore: no more messages")
                    } else {
                        setMessagesSafe((_messages.value + older).distinctBy { it.id }.sortedBy { it.timeStamp })
                        if (older.size < 30) _canLoadMore.value = false
                        Log.d(TAG, "📜 loadMore: +${older.size} older messages")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ loadMore exception", e)
            } finally {
                isLoadingMore = false
            }
        }
    }

    /**
     * Завантажує повідомлення групи
     */
    private fun fetchGroupMessages(beforeMessageId: Long = 0) {
        if (UserSession.accessToken == null || groupId == 0L) {
            _error.value = "Помилка: не авторизовано"
            return
        }

        _isLoading.value = true

        viewModelScope.launch {
            try {
                // Застосовуємо pending SenderKey distributions перед завантаженням
                // (щоб нові повідомлення вже могли бути розшифровані)
                signalGroupService.fetchAndApplyPendingDistributions(groupId)

                // Node.js: POST /api/node/group/messages/get
                // (замість PHP — Node.js повертає cipher_version + signal_header)
                val response = groupApi.getGroupMessages(
                    groupId         = groupId,
                    limit           = Constants.MESSAGES_PAGE_SIZE,
                    beforeMessageId = beforeMessageId
                )

                if (response.apiStatus == 200 && response.messages != null) {
                    val decryptedMessages = response.messages!!.decryptAll()

                    val currentMessages = _messages.value.toMutableList()
                    currentMessages.addAll(decryptedMessages)
                    setMessagesSafe(currentMessages.distinctBy { it.id }.sortedBy { it.timeStamp })

                    _error.value = null
                    Log.d(TAG, "Завантажено ${decryptedMessages.size} повідомлень групи $groupId")
                } else {
                    _error.value = response.errorMessage ?: "Помилка завантаження повідомлень"
                }

                _isLoading.value = false
            } catch (e: Exception) {
                _error.value = "Помилка: ${e.localizedMessage}"
                _isLoading.value = false
                Log.e(TAG, "Помилка завантаження повідомлень групи", e)
            }
        }
    }

    /**
     * Надсилает текстовое сообщение
     */
    fun sendMessage(text: String, replyToMessage: Message? = null) {
        if (UserSession.accessToken == null || (recipientId == 0L && groupId == 0L) || text.isBlank()) {
            _error.value = "Не можна надіслати порожнє повідомлення"
            return
        }

        val replyToId   = replyToMessage?.id
        val replyToText = replyToMessage?.decryptedText?.takeIf { it.isNotBlank() }
        val replyToName = replyToMessage?.senderName?.takeIf { it.isNotBlank() }

        _isLoading.value = true

        viewModelScope.launch {
            try {
                // ── Private chat → Node.js (with Signal Double Ratchet E2EE) ──────
                if (groupId == 0L) {
                    // Try Signal encryption first (cipher_version=3)
                    val signalPayload = signalService.encryptForSend(recipientId, text)

                    val resp = if (signalPayload != null) {
                        Log.d(TAG, "📤 [Signal] Sending E2EE msg to user $recipientId " +
                            "(header=${signalPayload.signalHeader.take(40)}...)")
                        nodeApi.sendMessage(
                            recipientId   = recipientId,
                            text          = signalPayload.ciphertext,
                            iv            = signalPayload.iv,
                            tag           = signalPayload.tag,
                            signalHeader  = signalPayload.signalHeader,
                            cipherVersion = SignalEncryptionService.CIPHER_VERSION_SIGNAL,
                            replyId       = replyToId,
                            replyToText   = replyToText,
                            replyToName   = replyToName
                        )
                    } else {
                        // Fallback: server-side AES-256-GCM (cipher_version=2)
                        Log.w(TAG, "⚠️ [Signal] Encryption failed, falling back to GCM for user $recipientId")
                        nodeApi.sendMessage(
                            recipientId = recipientId,
                            text        = text,
                            replyId     = replyToId,
                            replyToText = replyToText,
                            replyToName = replyToName
                        )
                    }

                    if (resp.apiStatus == 200) {
                        // Decrypt the server echo and add to local list.
                        // For Signal (cipher_version=3) we are the sender — we cannot decrypt
                        // our own outgoing ciphertext (it was encrypted for the recipient).
                        // Use the known plaintext directly instead of calling decryptMessageFully.
                        val rawMsg  = resp.messageData
                        val newMsg  = when {
                            rawMsg == null       -> null
                            signalPayload != null -> {
                                // Cache our own plaintext so it can be shown on reload
                                // without attempting to re-decrypt (own ciphertext is
                                // encrypted for the recipient, not for us).
                                signalService.cacheDecryptedMessage(rawMsg.id, text)
                                // Also store in the in-memory sent cache so concurrent
                                // socket-triggered fetchMessages() calls can find it
                                // immediately without waiting for DB write confirmation.
                                sentPlaintextCache[rawMsg.id] = text
                                rawMsg.copy(decryptedText = text)
                            }
                            else                  -> decryptMessageFully(rawMsg)
                        }

                        if (newMsg != null) {
                            // Upsert: replace any existing entry with the same id (e.g. an
                            // "encrypted" placeholder written by a stale socket echo) with the
                            // correct plaintext version that we have here from the HTTP response.
                            _messages.update { curr ->
                                if (curr.any { it.id == newMsg.id }) {
                                    curr.map { if (it.id == newMsg.id) newMsg else it }
                                } else {
                                    (curr + newMsg).sortedBy { it.timeStamp }
                                }
                            }
                        } else {
                            _messages.value = emptyList()
                            fetchMessages()
                        }
                        _error.value = null
                        deleteDraft()
                        Log.d(TAG, "✅ Message sent (E2EE=${signalPayload != null})")
                    } else {
                        _error.value = resp.errorMessage ?: "Не вдалося надіслати повідомлення"
                        Log.e(TAG, "Node.js send error: ${resp.errorMessage}")
                    }
                    _isLoading.value = false
                    return@launch
                }

                // ── Group chat → Node.js (з Signal Sender Key E2EE) ──────────────
                // Завантажуємо учасників групи для SenderKey distribution
                val members = runCatching {
                    groupApi.getGroupMembers(groupId = groupId).members.orEmpty()
                }.getOrDefault(emptyList())

                val myUserId = UserSession.userId ?: 0L

                // Намагаємося зашифрувати через Signal Sender Key (cipher_version=3)
                val groupSignalPayload = signalGroupService.encryptForGroup(
                    groupId  = groupId,
                    plaintext = text,
                    members  = members,
                    myUserId = myUserId
                )

                val groupResp = if (groupSignalPayload != null) {
                    Log.d(TAG, "📤 [Signal/Group] Sending E2EE msg to group $groupId (counter in header)")
                    groupApi.sendGroupMessage(
                        groupId       = groupId,
                        text          = groupSignalPayload.ciphertext,
                        replyId       = replyToId ?: 0L,
                        replyToText   = replyToText,
                        replyToName   = replyToName,
                        iv            = groupSignalPayload.iv,
                        tag           = groupSignalPayload.tag,
                        signalHeader  = groupSignalPayload.signalHeader,
                        cipherVersion = SignalGroupEncryptionService.CIPHER_VERSION_SIGNAL
                    )
                } else {
                    // Fallback: сервер шифрує AES-256-GCM (cipher_version=2)
                    Log.w(TAG, "⚠️ [Signal/Group] Шифрування не вдалося, відправляємо без Signal для групи $groupId")
                    groupApi.sendGroupMessage(
                        groupId     = groupId,
                        text        = text,
                        replyId     = replyToId ?: 0L,
                        replyToText = replyToText,
                        replyToName = replyToName
                    )
                }

                if (groupResp.apiStatus == 200) {
                    val rawMsg = groupResp.messageData
                    val newMsg = when {
                        rawMsg == null            -> null
                        groupSignalPayload != null -> {
                            // Кешуємо відкритий текст — власне повідомлення не можна розшифрувати
                            signalGroupService.cachePlaintext(rawMsg.id, text)
                            rawMsg.copy(decryptedText = text)
                        }
                        else -> decryptMessageFully(rawMsg)
                    }

                    if (newMsg != null) {
                        val curr = _messages.value
                        if (!curr.any { it.id == newMsg.id }) {
                            _messages.value = (curr + newMsg).sortedBy { it.timeStamp }
                        }
                    } else {
                        fetchGroupMessages()
                    }

                    // Socket.IO для real-time доставки іншим учасникам
                    socketManager?.sendGroupMessage(groupId, text)

                    _error.value = null
                    deleteDraft()
                    Log.d(TAG, "✅ Групове повідомлення надіслано (E2EE=${groupSignalPayload != null})")
                } else {
                    _error.value = groupResp.errorMessage ?: "Не вдалося надіслати повідомлення"
                    Log.e(TAG, "Group send error: ${groupResp.errorMessage}")
                }

                _isLoading.value = false
            } catch (e: Exception) {
                // Network unavailable or timeout → queue the message locally
                val isNetworkError = e is java.io.IOException ||
                    e is java.net.UnknownHostException ||
                    e is java.net.SocketTimeoutException ||
                    e.cause is java.io.IOException
                if (isNetworkError && UserSession.userId != null) {
                    queueMessageLocally(text)
                    _error.value = getApplication<android.app.Application>().getString(
                        com.worldmates.messenger.R.string.message_queued_offline
                    )
                    Log.w(TAG, "Network error — message queued for later delivery: ${e.message}")
                } else {
                    _error.value = "Помилка: ${e.localizedMessage}"
                    Log.e(TAG, "Помилка надсилання повідомлення", e)
                }
                _isLoading.value = false
            }
        }
    }

    /**
     * Saves a text message to Room as a local pending entry (isSynced=false).
     * It will be shown in the chat with a "pending" indicator and sent when connectivity returns.
     */
    private suspend fun queueMessageLocally(text: String) {
        val localId  = -(System.currentTimeMillis())   // negative = local-only
        val myId     = UserSession.userId ?: return
        val chatId   = if (groupId != 0L) groupId else recipientId
        val chatType = if (groupId != 0L) CachedMessage.CHAT_TYPE_GROUP else CachedMessage.CHAT_TYPE_USER
        val pending = CachedMessage(
            id            = localId,
            chatId        = chatId,
            chatType      = chatType,
            fromId        = myId,
            toId          = if (groupId != 0L) 0L else recipientId,
            groupId       = if (groupId != 0L) groupId else null,
            encryptedText = text,       // store plaintext; will be encrypted on send
            iv            = null,
            tag           = null,
            cipherVersion = null,
            decryptedText = text,
            timestamp     = System.currentTimeMillis(),
            isSynced      = false
        )
        db.messageDao().insertMessage(pending)
        // Show immediately in UI with pending flag
        val pendingMsg = Message(
            id            = localId,
            fromId        = myId,
            toId          = if (groupId != 0L) 0L else recipientId,
            groupId       = if (groupId != 0L) groupId else null,
            encryptedText = text,
            timeStamp     = System.currentTimeMillis(),
            decryptedText = text,
            isLocalPending = true
        )
        val curr = _messages.value
        if (!curr.any { it.id == localId }) {
            _messages.value = (curr + pendingMsg).sortedBy { it.timeStamp }
        }
        Log.d(TAG, "Message queued locally (id=$localId) for later delivery")
    }

    /**
     * Attempts to send all locally-queued (isSynced=false) messages for the current chat.
     * Called automatically when the socket reconnects.
     */
    private fun drainOfflineQueue() {
        if (UserSession.accessToken == null) return
        val chatId   = if (groupId != 0L) groupId else recipientId
        val chatType = if (groupId != 0L) CachedMessage.CHAT_TYPE_GROUP else CachedMessage.CHAT_TYPE_USER
        if (chatId == 0L) return
        viewModelScope.launch {
            val pending = db.messageDao().getUnsyncedMessages()
                .filter { it.chatId == chatId && it.chatType == chatType }
            if (pending.isEmpty()) return@launch
            Log.i(TAG, "Draining ${pending.size} offline-queued messages")
            for (queued in pending) {
                val text = queued.decryptedText ?: queued.encryptedText ?: continue
                try {
                    if (groupId == 0L) {
                        // ── Private chat ──────────────────────────────────────
                        val signalPayload = signalService.encryptForSend(recipientId, text)
                        val resp = if (signalPayload != null) {
                            nodeApi.sendMessage(
                                recipientId    = recipientId,
                                text           = signalPayload.ciphertext,
                                iv             = signalPayload.iv,
                                tag            = signalPayload.tag,
                                signalHeader   = signalPayload.signalHeader,
                                cipherVersion  = SignalEncryptionService.CIPHER_VERSION_SIGNAL,
                                isBusinessChat = if (isBusinessChat) 1 else 0
                            )
                        } else {
                            nodeApi.sendMessage(
                                recipientId    = recipientId,
                                text           = text,
                                isBusinessChat = if (isBusinessChat) 1 else 0
                            )
                        }
                        if (resp.apiStatus == 200) {
                            db.messageDao().hardDeleteMessage(queued.id)
                            _messages.value = _messages.value.filter { it.id != queued.id }
                            val rawMsg = resp.messageData
                            if (rawMsg != null) {
                                val newMsg = rawMsg.copy(decryptedText = text)
                                val curr = _messages.value
                                if (!curr.any { it.id == newMsg.id }) {
                                    _messages.value = (curr + newMsg).sortedBy { it.timeStamp }
                                }
                            }
                            Log.d(TAG, "Offline-queued message delivered (local=${queued.id}, server=${resp.messageData?.id})")
                        } else {
                            Log.w(TAG, "Failed to deliver queued message ${queued.id}: ${resp.errorMessage}")
                        }
                    } else {
                        // ── Group chat ────────────────────────────────────────
                        val members = runCatching {
                            groupApi.getGroupMembers(groupId = groupId).members.orEmpty()
                        }.getOrDefault(emptyList())
                        val myUserId = UserSession.userId ?: 0L
                        val groupPayload = signalGroupService.encryptForGroup(groupId, text, members, myUserId)
                        val resp = if (groupPayload != null) {
                            groupApi.sendGroupMessage(
                                groupId       = groupId,
                                text          = groupPayload.ciphertext,
                                iv            = groupPayload.iv,
                                tag           = groupPayload.tag,
                                signalHeader  = groupPayload.signalHeader,
                                cipherVersion = SignalGroupEncryptionService.CIPHER_VERSION_SIGNAL
                            )
                        } else {
                            groupApi.sendGroupMessage(groupId = groupId, text = text)
                        }
                        if (resp.apiStatus == 200) {
                            db.messageDao().hardDeleteMessage(queued.id)
                            _messages.value = _messages.value.filter { it.id != queued.id }
                            val rawMsg = resp.messageData
                            if (rawMsg != null) {
                                val newMsg = rawMsg.copy(decryptedText = text)
                                val curr = _messages.value
                                if (!curr.any { it.id == newMsg.id }) {
                                    _messages.value = (curr + newMsg).sortedBy { it.timeStamp }
                                }
                            }
                            Log.d(TAG, "Offline-queued group message delivered (local=${queued.id}, server=${resp.messageData?.id})")
                        } else {
                            Log.w(TAG, "Failed to deliver queued group message ${queued.id}: ${resp.errorMessage}")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not deliver queued message ${queued.id}: ${e.message}")
                    // Leave in queue for next reconnect
                }
            }
        }
    }

    /**
     * Редагує повідомлення
     */
    fun editMessage(messageId: Long, newText: String) {
        if (UserSession.accessToken == null || newText.isBlank()) {
            _error.value = "Не можна зберегти порожнє повідомлення"
            return
        }

        _isLoading.value = true

        viewModelScope.launch {
            try {
                // Private chat → Node.js
                if (groupId == 0L && recipientId != 0L) {
                    val resp = nodeApi.editMessage(messageId, newText)
                    if (resp.apiStatus == 200) {
                        val currentMessages = _messages.value.toMutableList()
                        val index = currentMessages.indexOfFirst { it.id == messageId }
                        if (index != -1) {
                            currentMessages[index] = currentMessages[index].copy(
                                encryptedText = newText,
                                decryptedText = newText
                            )
                            _messages.value = currentMessages
                        }
                        _error.value = null
                        Log.d("MessagesViewModel", "Повідомлення відредаговано (Node.js): $messageId")
                    } else {
                        _error.value = resp.errorMessage ?: "Не вдалося відредагувати повідомлення"
                    }
                    _isLoading.value = false
                    return@launch
                }

                // Group → Node.js
                if (groupId > 0L) {
                    val resp = groupApi.editGroupMessage(messageId, newText)
                    if (resp.apiStatus == 200) {
                        val current = _messages.value.toMutableList()
                        val idx = current.indexOfFirst { it.id == messageId }
                        if (idx != -1) {
                            current[idx] = current[idx].copy(
                                encryptedText = newText,
                                decryptedText = newText
                            )
                            _messages.value = current
                        }
                        _error.value = null
                        Log.d("MessagesViewModel", "Повідомлення відредаговано у групі (Node.js): $messageId")
                    } else {
                        _error.value = resp.errorMessage ?: "Не вдалося відредагувати повідомлення"
                    }
                    _isLoading.value = false
                    return@launch
                }

            } catch (e: Exception) {
                _error.value = "Помилка: ${e.localizedMessage}"
                _isLoading.value = false
                Log.e("MessagesViewModel", "Помилка редагування повідомлення", e)
            }
        }
    }

    /**
     * Видаляє повідомлення
     * deleteType: "just_me" — лише для себе, "everyone" — для обох учасників
     */
    fun deleteMessage(messageId: Long, deleteType: String = "just_me") {
        if (UserSession.accessToken == null) {
            _error.value = "Помилка: не авторизовано"
            return
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) {
            try {
                // ① Start Thanos animation immediately (optimistic UX)
                _deletingMessages.update { it + messageId }
                permanentlyDeletedIds.add(messageId)

                // Private chat → Node.js
                if (groupId == 0L && recipientId != 0L) {
                    val resp = nodeApi.deleteMessage(messageId, deleteType)
                    if (resp.apiStatus == 200) {
                        // ② Wait for animation to finish, then remove from list
                        kotlinx.coroutines.delay(750L)
                        _messages.update { list -> list.filter { it.id != messageId } }
                        _error.value = null
                        Log.d("MessagesViewModel", "Повідомлення видалено (Node.js, $deleteType): $messageId")
                    } else {
                        // Rollback animation on failure
                        _deletingMessages.update { it - messageId }
                        permanentlyDeletedIds.remove(messageId)
                        _error.value = resp.errorMessage ?: "Не вдалося видалити повідомлення"
                    }
                    _deletingMessages.update { it - messageId }
                    return@launch
                }

                // Group → Node.js
                if (groupId > 0L) {
                    val resp = groupApi.deleteGroupMessage(messageId)
                    if (resp.apiStatus == 200) {
                        _messages.value = _messages.value.filter { it.id != messageId }
                        _error.value = null
                        Log.d("MessagesViewModel", "Повідомлення видалено у групі (Node.js): $messageId")
                    } else {
                        _error.value = resp.errorMessage ?: "Не вдалося видалити повідомлення"
                    }
                    _isLoading.value = false
                    return@launch
                }

            } catch (e: Exception) {
                _deletingMessages.update { it - messageId }
                permanentlyDeletedIds.remove(messageId)
                _error.value = "Помилка: ${e.localizedMessage}"
                Log.e("MessagesViewModel", "Помилка видалення повідомлення", e)
            }
        }
    }

    // ==================== РЕАКЦІЇ ====================

    /**
     * Додає або видаляє реакцію на повідомлення (toggle)
     */
    fun toggleReaction(messageId: Long, emoji: String) {
        if (UserSession.accessToken == null) {
            _error.value = "Помилка: не авторизовано"
            return
        }

        viewModelScope.launch {
            try {
                // Private chat → Node.js (toggle handled server-side)
                // The server will emit message_reaction socket event to both parties,
                // which will update the UI via onMessageReaction callback.
                if (groupId == 0L && recipientId != 0L) {
                    val resp = nodeApi.reactToMessage(messageId, emoji)
                    if (resp.apiStatus == 200) {
                        // Update local state optimistically based on server response
                        val action = resp.action ?: "added"
                        val finalReaction = resp.reaction ?: emoji
                        updateLocalReaction(messageId, UserSession.userId, finalReaction, action)
                        Log.d("MessagesViewModel", "Реакцію оновлено (Node.js): $action $finalReaction")
                    } else {
                        _error.value = resp.errorMessage ?: "Не вдалося оновити реакцію"
                    }
                    return@launch
                }

                // Group path — Node.js (same toggle endpoint as private chats)
                val resp = nodeApi.reactToMessage(messageId, emoji)
                if (resp.apiStatus == 200) {
                    val action = resp.action ?: "added"
                    val finalReaction = resp.reaction ?: emoji
                    updateLocalReaction(messageId, UserSession.userId, finalReaction, action)
                    Log.d("MessagesViewModel", "Реакцію оновлено (Node.js group): $action $finalReaction")
                } else {
                    _error.value = resp.errorMessage ?: "Не вдалося оновити реакцію"
                    Log.e("MessagesViewModel", "Reaction Error: ${resp.errorMessage}")
                }
            } catch (e: Exception) {
                _error.value = "Помилка: ${e.localizedMessage}"
                Log.e("MessagesViewModel", "Помилка оновлення реакції", e)
            }
        }
    }

    /**
     * Групує реакції по емоджі для відображення під повідомленням
     */
    fun getReactionGroups(reactions: List<MessageReaction>): List<ReactionGroup> {
        return reactions.groupBy { it.reaction }
            .map { (emoji, reactionList) ->
                ReactionGroup(
                    emoji = emoji,
                    count = reactionList.size,
                    userIds = reactionList.map { it.userId },
                    hasMyReaction = reactionList.any { it.userId == UserSession.userId }
                )
            }
    }

    // ==================== СТІКЕРИ ====================

    /**
     * Надсилає стікер
     */
    fun sendSticker(stickerUrl: String) {
        if (stickerUrl.isBlank() || (recipientId == 0L && groupId == 0L)) {
            _error.value = "Помилка: не авторизовано"
            return
        }

        _isLoading.value = true

        viewModelScope.launch {
            try {
                val (apiStatus, errorMessage) = if (groupId != 0L) {
                    val r = groupApi.sendGroupMessage(groupId = groupId, text = "", stickers = stickerUrl)
                    r.apiStatus to r.errorMessage
                } else {
                    val r = nodeApi.sendMessage(recipientId = recipientId, text = "", stickers = stickerUrl, isBusinessChat = if (isBusinessChat) 1 else 0)
                    r.apiStatus to r.errorMessage
                }

                if (apiStatus == 200) {
                    if (groupId != 0L) fetchGroupMessages() else fetchMessages()
                    _error.value = null
                    Log.d("MessagesViewModel", "Стікер надіслано via Node.js")
                } else {
                    _error.value = errorMessage ?: getApplication<Application>().getString(R.string.error_send_sticker)
                    Log.e("MessagesViewModel", "Send Sticker Error: $errorMessage")
                }
            } catch (e: Exception) {
                _error.value = "Помилка: ${e.localizedMessage}"
                Log.e("MessagesViewModel", "Помилка надсилання стікера", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 🎬 Надсилає GIF
     * Для груп — залишаємо PHP. Для приватних чатів — Node.js (stickers field).
     */
    fun sendGif(gifUrl: String) {
        if (UserSession.accessToken == null || (recipientId == 0L && groupId == 0L)) {
            _error.value = "Помилка: не авторизовано"
            return
        }

        if (groupId != 0L) {
            viewModelScope.launch {
                try {
                    val resp = groupApi.sendGroupMessage(groupId = groupId, text = gifUrl)
                    if (resp.apiStatus == 200) {
                        fetchGroupMessages()
                    } else {
                        _error.value = resp.errorMessage ?: "Не вдалося надіслати GIF"
                    }
                } catch (e: Exception) {
                    _error.value = "Помилка: ${e.localizedMessage}"
                }
            }
            return
        }

        // Приватний чат — Node.js: GIF URL → stickers field
        sendGifViaNode(
            scope       = viewModelScope,
            api         = nodeApi,
            recipientId = recipientId,
            gifUrl      = gifUrl,
            isLoading   = _isLoading,
            error       = _error,
            onSuccess   = { fetchMessages() }
        )
    }

    /**
     * 📍 Надсилає геолокацію
     * Для груп — PHP (текст). Для приватних чатів — Node.js (lat/lng fields, type=map).
     */
    fun sendLocation(locationData: com.worldmates.messenger.data.repository.LocationData) {
        if (UserSession.accessToken == null || (recipientId == 0L && groupId == 0L)) {
            _error.value = "Помилка: не авторизовано"
            return
        }

        if (groupId != 0L) {
            viewModelScope.launch {
                try {
                    val locationText = "📍 ${locationData.address}\n${locationData.latLng.latitude},${locationData.latLng.longitude}"
                    groupApi.sendGroupMessage(groupId = groupId, text = locationText)
                    fetchGroupMessages()
                } catch (e: Exception) {
                    _error.value = "Помилка: ${e.localizedMessage}"
                }
            }
            return
        }

        // Приватний чат — Node.js: нативні поля lat/lng (type = map)
        sendLocationViaNode(
            scope       = viewModelScope,
            api         = nodeApi,
            recipientId = recipientId,
            lat         = locationData.latLng.latitude.toString(),
            lng         = locationData.latLng.longitude.toString(),
            address     = locationData.address,
            isLoading   = _isLoading,
            error       = _error,
            onSuccess   = { fetchMessages() }
        )
    }

    /**
     * Отправка контакта (vCard)
     * Для груп — PHP. Для приватних чатів — Node.js (contact field, type_two=contact).
     */
    fun sendContact(contact: com.worldmates.messenger.data.model.Contact) {
        if (UserSession.accessToken == null || (recipientId == 0L && groupId == 0L)) {
            _error.value = "Помилка: не авторизовано"
            return
        }

        val vCardString = contact.toVCard()

        if (groupId != 0L) {
            viewModelScope.launch {
                try {
                    groupApi.sendGroupMessage(groupId = groupId, text = "📇 VCARD\n$vCardString")
                    fetchGroupMessages()
                } catch (e: Exception) {
                    _error.value = "Помилка: ${e.localizedMessage}"
                }
            }
            return
        }

        // Приватний чат — Node.js: contact field (type_two=contact on server)
        sendContactViaNode(
            scope       = viewModelScope,
            api         = nodeApi,
            recipientId = recipientId,
            vCard       = vCardString,
            isLoading   = _isLoading,
            error       = _error,
            onSuccess   = { fetchMessages() }
        )
    }

    // ==================== DRAFT METHODS ====================

    /**
     * 📝 Загрузить черновик при открытии чата
     */
    fun loadDraft() {
        val chatId = if (groupId != 0L) groupId else recipientId
        if (chatId == 0L) return

        viewModelScope.launch {
            try {
                val draft = draftRepository.getDraft(chatId)
                if (draft != null) {
                    _currentDraft.value = draft.text
                    Log.d(TAG, "✅ Draft loaded: ${draft.text.take(50)}...")
                } else {
                    _currentDraft.value = ""
                    Log.d(TAG, "📭 No draft found for chat $chatId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error loading draft", e)
            }
        }
    }

    /**
     * 📝 Обновить текст черновика и запустить автосохранение
     */
    fun updateDraftText(text: String) {
        _currentDraft.value = text

        // Отменяем предыдущую задачу автосохранения
        draftAutoSaveJob?.cancel()

        // Запускаем новую задачу с задержкой 5 секунд
        draftAutoSaveJob = viewModelScope.launch {
            delay(DRAFT_AUTO_SAVE_DELAY)
            saveDraft(text)
        }
    }

    /**
     * 📝 Сохранить черновик в БД
     */
    private suspend fun saveDraft(text: String) {
        val chatId = if (groupId != 0L) groupId else recipientId
        if (chatId == 0L) return

        _isDraftSaving.value = true

        try {
            val chatType = if (groupId != 0L)
                Draft.CHAT_TYPE_GROUP
            else
                Draft.CHAT_TYPE_USER

            draftRepository.saveDraft(
                chatId = chatId,
                text = text,
                chatType = chatType
            )

            Log.d(TAG, "💾 Draft auto-saved for chat $chatId")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // Coroutine cancelled (e.g. message sent) — not an error
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving draft", e)
        } finally {
            _isDraftSaving.value = false
        }
    }

    /**
     * 📝 Удалить черновик (при отправке сообщения)
     */
    fun deleteDraft() {
        val chatId = if (groupId != 0L) groupId else recipientId
        if (chatId == 0L) return

        // Отменяем автосохранение
        draftAutoSaveJob?.cancel()

        viewModelScope.launch {
            try {
                draftRepository.deleteDraft(chatId)
                _currentDraft.value = ""
                Log.d(TAG, "🗑️ Draft deleted for chat $chatId")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error deleting draft", e)
            }
        }
    }

    // ==================== END DRAFT METHODS ====================

    /**
     * Загружает и отправляет медиа-файл
     */
    fun uploadAndSendMedia(file: File, mediaType: String) {
        if (UserSession.accessToken == null || (recipientId == 0L && groupId == 0L)) {
            _error.value = "Помилка: не авторизовано"
            return
        }

        if (!file.exists()) {
            _error.value = "Файл не знайдено"
            return
        }

        _isLoading.value = true

        viewModelScope.launch {
            try {
                if (mediaUploader == null) {
                    mediaUploader = MediaUploader(context)
                }

                val result = mediaUploader!!.uploadMedia(
                    accessToken = UserSession.accessToken!!,
                    mediaType = mediaType,
                    filePath = file.absolutePath,
                    recipientId = recipientId.takeIf { it != 0L },
                    groupId = groupId.takeIf { it != 0L },
                    isPremium = UserSession.isProActive,
                    onProgress = { progress ->
                        _uploadProgress.value = progress
                    }
                )

                when (result) {
                    is MediaUploader.UploadResult.Success -> {
                        _uploadProgress.value = 0
                        _error.value = null
                        Log.d("MessagesViewModel", "Медіа завантажено: ${result.url}")

                        // Node.js send-media endpoint already handles Socket.IO broadcast,
                        // so no separate notify-media call is needed.

                        // Refresh message list
                        if (groupId != 0L) {
                            fetchGroupMessages()
                        } else {
                            fetchMessages()
                        }

                        // Чистимо файл
                        if (file.exists()) {
                            file.delete()
                        }
                    }
                    is MediaUploader.UploadResult.Error -> {
                        _error.value = result.message
                        _uploadProgress.value = 0
                        Log.e("MessagesViewModel", "Помилка завантаження: ${result.message}")
                    }
                    is MediaUploader.UploadResult.Progress -> {
                        _uploadProgress.value = result.percent
                    }
                }

                _isLoading.value = false
            } catch (e: Exception) {
                _error.value = "Помилка: ${e.localizedMessage}"
                _isLoading.value = false
                _uploadProgress.value = 0
                Log.e("MessagesViewModel", "Помилка завантаження медіа", e)
            }
        }
    }


    /**
     * Налаштовує Socket.IO для получения сообщений в реальном времени
     * + Адаптивний моніторинг якості з'єднання
     */
    private fun setupSocket() {
        Log.d(TAG, "🔌 setupSocket() викликано")
        try {
            // Створюємо SocketManager з context для NetworkQualityMonitor
            socketManager = SocketManager(this, context)
            Log.d(TAG, "✅ SocketManager створено з адаптивним моніторингом")

            // Ініціалізуємо Live Location Manager
            liveLocationManager = LiveLocationManager(
                locationRepo  = LocationRepository.getInstance(context),
                socketManager = socketManager!!
            )

            socketManager?.connect()
            Log.d(TAG, "✅ Socket.IO connect() викликано")

            // Запускаємо моніторинг якості з'єднання для UI
            startQualityMonitoring()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Помилка Socket.IO", e)
            e.printStackTrace()
        }
    }

    /**
     * Моніторинг якості з'єднання для оновлення UI
     */
    private fun startQualityMonitoring() {
        qualityMonitorJob?.cancel()
        qualityMonitorJob = viewModelScope.launch {
            while (true) {
                try {
                    val quality = socketManager?.getConnectionQuality()
                        ?: NetworkQualityMonitor.ConnectionQuality.OFFLINE

                    if (_connectionQuality.value != quality) {
                        _connectionQuality.value = quality
                        Log.d(TAG, "📊 Connection quality changed: $quality")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error monitoring quality", e)
                }

                delay(5000) // Перевіряємо кожні 5 секунд
            }
        }
    }

    /** Group messages (polls, regular text, media) come through this socket event */
    override fun onGroupMessage(messageJson: JSONObject) {
        onNewMessage(messageJson)
    }

    override fun onNewMessage(messageJson: JSONObject) {
        // Run in a coroutine so we can call suspend functions (Signal decryption)
        viewModelScope.launch {
            try {
                Log.d(TAG, "📨 [Socket.IO] Incoming message: ${messageJson.toString().take(120)}")

                // ── Skip own Signal echoes ────────────────────────────────────────────
                // The server emits `new_message { self: true }` back to the sender after
                // storing an outgoing Signal (cipher_version=3) message.  The payload
                // contains the ciphertext that was encrypted FOR THE RECIPIENT — the
                // sender cannot decrypt it, and the plaintext cache is always empty at
                // this point because the socket event arrives over WebSocket BEFORE the
                // HTTP response from sendMessage() comes back (server emits first, then
                // writes the REST response).  sendMessage() handles adding the message
                // with the known plaintext, so we just skip the echo here.
                val isSelfSignalEcho = messageJson.optBoolean("self", false) &&
                    messageJson.optInt("cipher_version", 0) == SignalEncryptionService.CIPHER_VERSION_SIGNAL
                if (isSelfSignalEcho) {
                    Log.d(TAG, "⏭️ [Socket.IO] Skipping own Signal echo " +
                        "(msg id=${messageJson.optLong("id")} — plaintext handled by sendMessage())")
                    return@launch
                }

                val timestamp     = messageJson.getLong("time")
                val encryptedText = messageJson.optString("text", null)
                val mediaUrl      = messageJson.optString("media", null)?.takeIf { it.isNotEmpty() }
                val iv            = messageJson.optString("iv",  null)?.takeIf { it.isNotEmpty() }
                val tag           = messageJson.optString("tag", null)?.takeIf { it.isNotEmpty() }
                val cipherVersion = if (messageJson.has("cipher_version"))
                    messageJson.getInt("cipher_version") else null
                val signalHeader  = messageJson.optString("signal_header", null)
                    ?.takeIf { it.isNotEmpty() }

                Log.d(TAG, "📨 [Socket.IO] cipher_v=$cipherVersion" +
                    " iv=${iv?.take(8)} signal=${signalHeader?.take(30)}")

                val stickers = messageJson.optString("stickers", null)?.takeIf { it.isNotEmpty() }
                val typeTwo  = messageJson.optString("type_two", null)?.takeIf { it.isNotEmpty() }

                // Build raw Message object with encrypted payload
                val rawMessage = Message(
                    id            = messageJson.getLong("id"),
                    fromId        = messageJson.getLong("from_id"),
                    toId          = messageJson.getLong("to_id"),
                    groupId       = messageJson.optLong("group_id", 0).takeIf { it != 0L },
                    encryptedText = encryptedText,
                    timeStamp     = timestamp,
                    mediaUrl      = mediaUrl,
                    type          = messageJson.optString("type", Constants.MESSAGE_TYPE_TEXT),
                    senderName    = messageJson.optString("sender_name", null),
                    senderAvatar  = messageJson.optString("sender_avatar", null),
                    iv            = iv,
                    tag           = tag,
                    cipherVersion = cipherVersion,
                    signalHeader  = signalHeader,
                    stickers      = stickers,
                    typeTwo       = typeTwo
                )

                // Decrypt (suspend — handles Signal v3 and legacy GCM/ECB)
                val message = decryptMessageFully(rawMessage)

                // Check relevance — must match chat type (business vs personal) to prevent
                // cross-thread routing that would corrupt the Signal Double Ratchet chain state.
                val msgIsBusinessChat = messageJson.optInt("is_business_chat", 0) == 1
                val isRelevant = if (this@MessagesViewModel.groupId != 0L) {
                    message.groupId == this@MessagesViewModel.groupId
                } else {
                    msgIsBusinessChat == isBusinessChat &&
                    ((message.fromId == recipientId && message.toId == UserSession.userId) ||
                    (message.fromId == UserSession.userId && message.toId == recipientId))
                }

                if (isRelevant) {
                    val currentMessages = _messages.value.toMutableList()
                    currentMessages.add(message)
                    _messages.value = currentMessages.distinctBy { it.id }.sortedBy { it.timeStamp }
                    Log.d(TAG, "✅ [Socket.IO] Added msg ${message.id} " +
                        "(E2EE=${cipherVersion == 3}): \"${message.decryptedText?.take(40)}\"")

                    if (message.fromId == recipientId) markSeen()
                } else {
                    Log.d(TAG, "ℹ️ [Socket.IO] msg ${message.id} not relevant to current chat")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ [Socket.IO] Error processing incoming message", e)
            }
        }
    }

    override fun onSocketConnected() {
        Log.i("MessagesViewModel", "Socket підключено успішно")
        _error.value = null
        // Register with server that this private chat is open, so on_user_loggedin events
        // are delivered regardless of follower relationship.
        if (recipientId > 0L) {
            val lastId = _messages.value.lastOrNull()?.id ?: 0L
            socketManager?.openChat(recipientId, lastId)
        }
        // Drain any messages that were queued while offline
        drainOfflineQueue()
    }

    override fun onSocketDisconnected() {
        Log.w("MessagesViewModel", "Socket відключено")
        _error.value = getApplication<android.app.Application>().getString(
            com.worldmates.messenger.R.string.connection_lost
        )
    }

    override fun onSocketError(error: String) {
        Log.e("MessagesViewModel", "Помилка Socket: $error")
        _error.value = error
    }

    override fun onTypingStatus(userId: Long?, isTyping: Boolean) {
        // Private chat: check userId == recipientId
        if (groupId == 0L && userId == recipientId) {
            typingAutoResetJob?.cancel()
            if (isTyping) {
                presenceIsOnline = true
                _presenceStatus.value = UserPresenceStatus.Typing
                // Auto-reset after 6s in case the stop-event is lost on the network
                typingAutoResetJob = viewModelScope.launch {
                    delay(6_000)
                    if (_presenceStatus.value is UserPresenceStatus.Typing) {
                        _presenceStatus.value = basePresenceStatus()
                    }
                }
            } else {
                _presenceStatus.value = basePresenceStatus()
            }
            Log.d("MessagesViewModel", "Користувач $userId ${if (isTyping) "набирає" else "зупинив набір"}")
        }
    }

    override fun onGroupTyping(groupId: Long, userId: Long, isTyping: Boolean) {
        if (this.groupId != groupId) return
        if (userId == UserSession.userId) return  // ignore own typing
        groupTypingAutoResetJob?.cancel()
        if (isTyping) {
            val memberName = _currentGroup.value?.members?.find { it.userId == userId }?.username ?: "Хтось"
            _presenceStatus.value = UserPresenceStatus.GroupTyping(memberName)
            // Auto-reset after 6s in case the stop-event is lost on the network
            groupTypingAutoResetJob = viewModelScope.launch {
                delay(6_000)
                if (_presenceStatus.value is UserPresenceStatus.GroupTyping) {
                    _presenceStatus.value = UserPresenceStatus.Online
                }
            }
        } else {
            _presenceStatus.value = UserPresenceStatus.Online
        }
        Log.d(TAG, "Group $groupId: user $userId ${if (isTyping) "typing" else "stopped"}")
    }

    override fun onUserOnline(userId: Long) {
        if (userId == recipientId) {
            presenceIsOnline = true
            // Only update if not in a transient action state
            val cur = _presenceStatus.value
            if (cur is UserPresenceStatus.Offline || cur is UserPresenceStatus.LastSeen) {
                _presenceStatus.value = UserPresenceStatus.Online
            }
            Log.d("MessagesViewModel", "✅ Користувач $userId з'явився онлайн")
        }
    }

    override fun onUserOffline(userId: Long) {
        if (userId == recipientId) {
            presenceIsOnline = false
            // Only update if not in a transient action state (typing etc.)
            val cur = _presenceStatus.value
            if (cur is UserPresenceStatus.Online) {
                _presenceStatus.value = basePresenceStatus()
            }
            Log.d("MessagesViewModel", "❌ Користувач $userId з'явився офлайн")
        }
    }

    override fun onRecordingStatus(userId: Long?, isRecording: Boolean) {
        if (userId == recipientId) {
            _presenceStatus.value = if (isRecording) UserPresenceStatus.RecordingVoice else basePresenceStatus()
            if (isRecording) presenceIsOnline = true
            Log.d(TAG, "Користувач $userId recording voice: $isRecording")
        }
    }

    override fun onLastSeen(userId: Long, lastSeen: Long) {
        if (userId == recipientId && lastSeen > 0) {
            presenceLastSeenTs = lastSeen
            // Only switch to LastSeen if currently offline
            if (_presenceStatus.value is UserPresenceStatus.Offline) {
                _presenceStatus.value = UserPresenceStatus.LastSeen(lastSeen)
            }
            Log.d(TAG, "Last seen for $userId: $lastSeen")
        }
    }

    /**
     * Recipient [userId] has read all our messages (server "lastseen" bulk event).
     * Marks every outgoing message in this chat as isRead=true in the in-memory list.
     */
    override fun onChatSeen(userId: Long, seenAt: Long) {
        if (userId != recipientId) return
        val readAtMs = if (seenAt > 0) seenAt * 1000L else System.currentTimeMillis()
        val myId = UserSession.userId
        _messages.update { list ->
            list.map { msg ->
                if (msg.fromId == myId && !msg.isRead) msg.copy(isRead = true, readAt = readAtMs)
                else msg
            }
        }
        Log.d(TAG, "onChatSeen: marked messages as read for recipient $userId seenAt=$seenAt")
    }

    /**
     * Fetches the recipient's current online/last-seen status from the REST API and
     * immediately updates the header bar.  Called on chat open so the status dot is
     * correct from the first frame — without waiting for socket events, which only
     * fire when a user connects/disconnects.
     */
    private fun fetchRecipientStatus() {
        if (recipientId == 0L) return
        viewModelScope.launch {
            try {
                val resp = nodeApi.getUserStatus(recipientId)
                if (resp.apiStatus == 200) {
                    presenceIsOnline    = resp.online
                    if (resp.lastSeen > 0) presenceLastSeenTs = resp.lastSeen
                    // Only update the UI if no transient state (typing etc.) is active
                    val cur = _presenceStatus.value
                    if (cur is UserPresenceStatus.Offline || cur is UserPresenceStatus.LastSeen) {
                        _presenceStatus.value = basePresenceStatus()
                    }
                    Log.d(TAG, "Recipient $recipientId status: online=${resp.online} last_seen=${resp.lastSeen}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "fetchRecipientStatus failed: ${e.message}")
            }
        }
    }

    override fun onUserAction(userId: Long?, groupId: Long?, action: String) {
        val isRelevant = if (this.groupId > 0L) {
            groupId == this.groupId && userId != UserSession.userId
        } else {
            userId == recipientId
        }
        if (!isRelevant) return

        val newStatus: UserPresenceStatus = when (action) {
            Constants.USER_ACTION_RECORDING        -> UserPresenceStatus.RecordingVoice
            Constants.USER_ACTION_RECORDING_VIDEO  -> UserPresenceStatus.RecordingVideo
            Constants.USER_ACTION_LISTENING        -> UserPresenceStatus.ListeningAudio
            Constants.USER_ACTION_VIEWING          -> UserPresenceStatus.ViewingMedia
            Constants.USER_ACTION_CHOOSING_STICKER -> UserPresenceStatus.ChoosingSticker
            else                                   -> basePresenceStatus()
        }
        _presenceStatus.value = newStatus
        Log.d(TAG, "User action '$action' from user $userId")
    }

    // ── Live Location callbacks (from SocketManager.ExtendedSocketListener) ────

    override fun onLiveLocationUpdate(fromId: Long, lat: Double, lng: Double, accuracy: Float) {
        liveLocationManager?.onIncomingUpdate(fromId, lat, lng)
    }

    override fun onLiveLocationStarted(fromId: Long) {
        Log.d(TAG, "📍 User $fromId started sharing location")
    }

    override fun onLiveLocationStopped(fromId: Long) {
        liveLocationManager?.onRemoteStop(fromId)
        Log.d(TAG, "🛑 User $fromId stopped sharing location")
    }

    // ── E2EE: Signal identity key change (device change) ────────────────────

    /**
     * Called when a contact registered a new Signal identity key (logged in on new device).
     * Their old DR session is now invalid — delete it so the next message triggers fresh X3DH.
     */
    override fun onSignalIdentityChanged(userId: Long) {
        viewModelScope.launch {
            val keyStore = com.worldmates.messenger.utils.signal.SignalKeyStore(getApplication())
            keyStore.deleteSession(userId)
            Log.i(TAG, "🔑 [Signal] DR session cleared for user=$userId (identity key changed on new device)")
            // If this is the current private chat, we'll re-establish X3DH on next send automatically.
        }
    }

    // ── Group E2EE: member join / leave ──────────────────────────────────────

    /**
     * A new member joined the current group.
     * Force re-distribution of our SenderKey so the new member can decrypt future messages.
     */
    override fun onGroupMemberJoined(groupId: Long, userId: Long) {
        if (this.groupId != groupId) return
        viewModelScope.launch {
            // Invalidate the "distributed" flag — encryptForGroup will re-distribute on next send
            signalGroupService.invalidateMyKey(groupId)
            Log.i(TAG, "🔑 [Signal/Group] SenderKey invalidated for group=$groupId (new member userId=$userId)")
        }
    }

    /**
     * A member left the current group.
     * Remove their SenderKey locally and tell the server to invalidate it.
     */
    override fun onGroupMemberLeft(groupId: Long, userId: Long) {
        if (this.groupId != groupId) return
        signalGroupService.invalidateMemberKey(groupId, userId)
        Log.i(TAG, "🔑 [Signal/Group] SenderKey of user=$userId removed for group=$groupId (member left)")
        viewModelScope.launch {
            runCatching {
                nodeApi.invalidateGroupSenderKey(groupId = groupId, senderId = userId)
            }.onFailure {
                Log.w(TAG, "invalidateGroupSenderKey API call failed: ${it.message}")
            }
        }
        // Force re-distribution on next send (new chain key won't include the removed member)
        signalGroupService.invalidateMyKey(groupId)
    }

    // ── Live Location public API ─────────────────────────────────────────────

    /**
     * Почати ділитись геолокацією з поточним співрозмовником або групою.
     * Потрібен дозвіл ACCESS_FINE_LOCATION.
     */
    fun startLiveLocationSharing() {
        val manager = liveLocationManager ?: return
        val (toId, isGroup) = if (groupId != 0L) Pair(groupId, true) else Pair(recipientId, false)
        if (toId == 0L) return
        manager.startSharing(toId, isGroup)
        Log.d(TAG, "📍 startLiveLocationSharing → ${if (isGroup) "group" else "user"}=$toId")
    }

    /**
     * Зупинити передачу геолокації.
     */
    fun stopLiveLocationSharing() {
        val manager = liveLocationManager ?: return
        val (toId, isGroup) = if (groupId != 0L) Pair(groupId, true) else Pair(recipientId, false)
        if (toId == 0L) return
        manager.stopSharing(toId, isGroup)
    }

    // ────────────────────────────────────────────────────────────────────────

    override fun onMessageEdited(messageId: Long, newText: String, iv: String?, tag: String?, cipherVersion: String?) {
        val current = _messages.value.toMutableList()
        val idx = current.indexOfFirst { it.id == messageId }
        if (idx != -1) {
            val msg = current[idx]
            // Use new encryption metadata from socket event if provided; fall back to existing
            val effectiveIv = iv ?: msg.iv
            val effectiveTag = tag ?: msg.tag
            // cipherVersion from socket is String (e.g. "2"); Message model needs Int?
            val effectiveCipherVersion = cipherVersion?.toIntOrNull() ?: msg.cipherVersion
            val decrypted = DecryptionUtility.decryptMessageOrOriginal(
                text = newText,
                timestamp = msg.timeStamp,
                iv = effectiveIv,
                tag = effectiveTag,
                cipherVersion = effectiveCipherVersion
            )
            current[idx] = msg.copy(
                encryptedText = newText,
                decryptedText = decrypted,
                iv = effectiveIv,
                tag = effectiveTag,
                cipherVersion = effectiveCipherVersion
            )
            _messages.value = current
            Log.d(TAG, "Socket: повідомлення $messageId відредаговано")
        }
    }

    override fun onMessageDeleted(messageId: Long, deleteType: String) {
        // Skip if the sender already triggered the animation locally
        if (_deletingMessages.value.contains(messageId)) {
            Log.d(TAG, "Socket: message $messageId already deleting, skip duplicate event")
            return
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) {
            permanentlyDeletedIds.add(messageId)
            // ① Play Thanos animation
            _deletingMessages.update { it + messageId }
            // ② Wait for animation, then remove atomically
            kotlinx.coroutines.delay(750L)
            _messages.update { list -> list.filter { it.id != messageId } }
            _deletingMessages.update { it - messageId }
            Log.d(TAG, "Socket: повідомлення $messageId видалено ($deleteType)")
        }
    }

    override fun onMessageReaction(messageId: Long, userId: Long, reaction: String, action: String) {
        updateLocalReaction(messageId, userId, reaction, action)
        Log.d(TAG, "Socket: реакція '$reaction' ($action) на повідомлення $messageId від $userId")
    }

    override fun onGroupHistoryCleared(groupId: Long) {
        if (this.groupId == groupId) {
            _messages.value = emptyList()
            Log.d(TAG, "Socket: group $groupId history cleared for all")
        }
    }

    override fun onPrivateHistoryCleared(fromId: Long, recipientId: Long) {
        val myId = UserSession.userId ?: return
        // Applies to this conversation if we're one of the two participants
        val isOurChat = (this.recipientId == fromId || this.recipientId == recipientId) &&
                        (myId == fromId || myId == recipientId)
        if (isOurChat) {
            permanentlyDeletedIds.addAll(_messages.value.map { it.id })
            _messages.value = emptyList()
            Log.d(TAG, "Socket: private history cleared by $fromId (chat with $recipientId)")
        }
    }

    override fun onMessagePinned(messageId: Long, isPinned: Boolean, chatId: Long) {
        if (recipientId != 0L && (chatId == recipientId || chatId == UserSession.userId)) {
            val current = _pinnedPrivateMessages.value.toMutableList()
            if (isPinned) {
                val msg = _messages.value.find { it.id == messageId }
                if (msg != null && current.none { it.id == messageId }) {
                    current.add(0, msg)
                    _pinnedPrivateMessages.value = current.take(if (UserSession.isProActive) 15 else 5)
                }
            } else {
                _pinnedPrivateMessages.value = current.filter { it.id != messageId }
            }
            Log.d(TAG, "Socket: повідомлення $messageId ${if (isPinned) "закріплено" else "відкріплено"}")
        }
    }

    /**
     * Оновлює реакції конкретного повідомлення в локальному списку.
     * action: "added" | "updated" | "removed"
     */
    private fun updateLocalReaction(messageId: Long, userId: Long, reaction: String, action: String) {
        val current = _messages.value.toMutableList()
        val idx = current.indexOfFirst { it.id == messageId }
        if (idx != -1) {
            val msg = current[idx]
            val reactions = msg.reactions?.toMutableList() ?: mutableListOf()
            when (action) {
                "removed" -> reactions.removeAll { it.userId == userId }
                else -> {
                    reactions.removeAll { it.userId == userId }
                    reactions.add(MessageReaction(id = null, messageId = messageId, userId = userId, reaction = reaction))
                }
            }
            current[idx] = msg.copy(reactions = reactions)
            _messages.value = current
        }
    }

    /**
     * Надсилає статус "набирає текст" через REST API Node.js
     * REST API (не Socket.IO) використовується тому що auth через header access-token,
     * а не session hash — так простіше та надійніше.
     */
    fun sendTypingStatus(isTyping: Boolean) {
        if (socketManager?.canSendTypingIndicators() == false) return
        viewModelScope.launch {
            try {
                if (groupId > 0L) {
                    groupApi.sendGroupTyping(groupId, isTyping)
                    Log.d(TAG, "Group typing status sent: $isTyping → group $groupId")
                } else if (recipientId != 0L) {
                    nodeApi.sendTyping(recipientId, if (isTyping) "true" else "false")
                    Log.d(TAG, "Typing status sent via REST: $isTyping → recipient $recipientId")
                }
            } catch (e: Exception) {
                Log.w(TAG, "sendTypingStatus failed: ${e.message}")
            }
        }
    }

    /**
     * Надсилає статус "записує голосове"
     */
    fun sendRecordingStatus() {
        if (groupId > 0L) {
            sendUserAction(Constants.USER_ACTION_RECORDING)
        } else if (recipientId != 0L) {
            socketManager?.sendRecordingStatus(recipientId)
            Log.d(TAG, "Recording status emitted for recipient $recipientId")
        }
    }

    /**
     * Sends a user action status (listening, viewing, choosing_sticker, recording_video, etc.)
     * to the current chat recipient or group.
     */
    fun sendUserAction(action: String) {
        viewModelScope.launch {
            try {
                if (groupId > 0L) {
                    groupApi.sendGroupUserAction(groupId, action)
                    Log.d(TAG, "Group user action '$action' sent for group $groupId")
                } else if (recipientId != 0L) {
                    nodeApi.sendUserAction(recipientId, action)
                    Log.d(TAG, "User action '$action' sent for recipient $recipientId")
                }
            } catch (e: Exception) {
                Log.w(TAG, "sendUserAction failed: ${e.message}")
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    /**
     * Полностью дешифрует сообщение: текст и URL медиа.
     * Также пытается извлечь URL медиа из текста сообщения.
     * Поддерживает AES-GCM (v2) и обратную совместимость с AES-ECB (v1).
     */
    /**
     * Decrypt a single message.
     *
     * cipher_version=1 (ECB) or 2 (GCM) — uses DecryptionUtility (synchronous).
     * cipher_version=3 (Signal Double Ratchet) — uses SignalEncryptionService (suspend).
     *
     * All cipher paths run on [Dispatchers.Default] to keep the Main thread free.
     */
    private suspend fun decryptMessageFully(msg: Message): Message =
        withContext(Dispatchers.Default) {
            when (msg.cipherVersion) {
                3 -> {
                    // cipher_version=3: Signal E2EE
                    // Розрізняємо групові та приватні повідомлення по groupId
                    if (msg.groupId != null && msg.groupId!! > 0L) {
                        decryptGroupSignalMessage(msg)   // Signal Sender Key
                    } else {
                        decryptSignalMessage(msg)         // Signal Double Ratchet
                    }
                }
                else -> decryptLegacyMessage(msg)
            }
        }

    /** Decrypt AES-128-ECB (v1) or AES-256-GCM (v2) message. */
    private fun decryptLegacyMessage(msg: Message): Message {
        val decryptedText = DecryptionUtility.decryptMessageOrOriginal(
            text          = msg.encryptedText,
            timestamp     = msg.timeStamp,
            iv            = msg.iv,
            tag           = msg.tag,
            cipherVersion = msg.cipherVersion
        )
        val decryptedMediaUrl = DecryptionUtility.decryptMediaUrl(
            mediaUrl      = msg.mediaUrl,
            timestamp     = msg.timeStamp,
            iv            = msg.iv,
            tag           = msg.tag,
            cipherVersion = msg.cipherVersion
        )
        val finalMediaUrl = decryptedMediaUrl
            ?: DecryptionUtility.extractMediaUrlFromText(decryptedText)
        return msg.copy(decryptedText = decryptedText, decryptedMediaUrl = finalMediaUrl)
    }

    /**
     * Decrypt a Signal Sender Key (cipher_version=3) GROUP message.
     *
     * Використовує SignalGroupEncryptionService з SenderKey відправника.
     * Якщо SenderKey не знайдено → намагається завантажити distributions з сервера.
     */
    private suspend fun decryptGroupSignalMessage(msg: Message): Message {
        val msgId    = msg.id
        val groupId  = msg.groupId ?: 0L
        val senderId = msg.fromId

        // Перевіряємо кеш — власні відправлені повідомлення кешуються в plaintext
        val cached = signalGroupService.getCachedPlaintext(msgId)
        if (cached != null) {
            Log.d(TAG, "📱 [Signal/Group] msg $msgId від user $senderId — з кешу")
            return msg.copy(decryptedText = cached)
        }

        val header     = msg.signalHeader
        val ciphertext = msg.encryptedText
        val iv         = msg.iv
        val tag        = msg.tag

        if (header == null || ciphertext.isNullOrEmpty() || iv == null || tag == null) {
            Log.w(TAG, "⚠️ [Signal/Group] msg $msgId missing fields")
            return msg.copy(decryptedText = getApplication<Application>().getString(R.string.encrypted_message))
        }

        // Якщо SenderKey не знайдено — пробуємо завантажити distributions
        if (!signalGroupService.hasSenderKey(groupId, senderId)) {
            Log.d(TAG, "🔄 [Signal/Group] SenderKey від user=$senderId не знайдено, завантажуємо distributions...")
            signalGroupService.fetchAndApplyPendingDistributions(groupId)
        }

        val plainText = signalGroupService.decryptGroupMessage(
            groupId       = groupId,
            senderId      = senderId,
            ciphertextB64 = ciphertext,
            ivB64         = iv,
            tagB64        = tag,
            headerJson    = header
        )

        return if (plainText != null) {
            Log.d(TAG, "🔓 [Signal/Group] msg $msgId OK: \"${plainText.take(40)}\"")
            signalGroupService.cachePlaintext(msgId, plainText)
            msg.copy(decryptedText = plainText)
        } else {
            Log.e(TAG, "❌ [Signal/Group] msg $msgId — розшифровка не вдалася (SenderKey відсутній або лічильник розійшовся)")
            msg.copy(decryptedText = getApplication<Application>().getString(R.string.encrypted_message))
        }
    }

    /** Decrypt a Signal Double Ratchet (cipher_version=3) message. */
    private suspend fun decryptSignalMessage(msg: Message): Message {
        // Double Ratchet keys are one-time use: once a message is decrypted the
        // session state advances and the same ciphertext cannot be decrypted again.
        // Check the persistent plaintext cache first so re-opening a conversation
        // does not trigger a second (failing) decryption attempt.
        val cached = signalService.getCachedDecryptedMessage(msg.id)
        if (cached != null) {
            Log.d(TAG, "📱 [Signal] msg ${msg.id} from cache")
            return msg.copy(decryptedText = cached)
        }

        val signalHeader = msg.signalHeader
        val ciphertext   = msg.encryptedText
        val iv           = msg.iv
        val tag          = msg.tag

        if (signalHeader == null || ciphertext.isNullOrEmpty() || iv == null || tag == null) {
            Log.w(TAG, "⚠️ [Signal] msg ${msg.id} missing fields: " +
                "header=${signalHeader != null} iv=${iv != null} tag=${tag != null}")
            return msg.copy(decryptedText = getApplication<Application>().getString(R.string.encrypted_message))
        }

        val senderId  = msg.fromId
        val myUserId  = UserSession.userId ?: 0L

        // Own sent messages are encrypted for the recipient — we cannot decrypt our own
        // ciphertext. The plaintext is cached in sendMessage() right after sending, so a
        // cache hit above is the only way to recover it. If the cache is empty (Room DB
        // wiped or message sent before caching was implemented), the plaintext is gone.
        // Never call decryptIncoming() with senderId == ourself: there is no self-session,
        // and doing so would just pollute the logs with spurious auth-failure errors.
        if (senderId == myUserId) {
            // First try the in-memory cache populated right after each successful send.
            // This covers the race where a socket-triggered fetchMessages() fires before
            // the Signal DB cache write (line below) has been confirmed.
            val memCached = sentPlaintextCache[msg.id]
            if (memCached != null) {
                Log.d(TAG, "📬 [Signal] msg ${msg.id} from in-memory sent cache")
                return msg.copy(decryptedText = memCached)
            }
            // Fall back to Room DB — survives ViewModel recreation (e.g. leaving and re-entering chat)
            val dbCached = signalService.getCachedDecryptedMessage(msg.id)
            if (dbCached != null) {
                Log.d(TAG, "📬 [Signal] msg ${msg.id} from Room DB cache")
                sentPlaintextCache[msg.id] = dbCached  // warm the in-memory cache
                return msg.copy(decryptedText = dbCached)
            }
            Log.w(TAG, "⚠️ [Signal] msg ${msg.id} is own message not in cache — plaintext unavailable")
            return msg.copy(decryptedText = getApplication<Application>().getString(R.string.encrypted_message))
        }

        Log.d(TAG, "🔐 [Signal] Decrypting msg ${msg.id} from user $senderId " +
            "header=${signalHeader.take(40)}...")

        val plainText = signalService.decryptIncoming(
            senderId         = senderId,
            msgId            = msg.id,
            ciphertextB64    = ciphertext,
            ivB64            = iv,
            tagB64           = tag,
            signalHeaderJson = signalHeader
        )

        return if (plainText != null) {
            Log.d(TAG, "🔓 [Signal] msg ${msg.id} OK: \"${plainText.take(40)}\"")
            // Note: cacheDecryptedMessage is now called inside decryptIncoming (within the
            // per-sender lock) to prevent a second concurrent call from re-decrypting.
            msg.copy(decryptedText = plainText)
        } else {
            Log.e(TAG, "❌ [Signal] msg ${msg.id} auth FAILED — possible session mismatch")
            msg.copy(decryptedText = getApplication<Application>().getString(R.string.encrypted_message))
        }
    }

    /**
     * Decrypt a list of messages sequentially.
     * Must be called from a coroutine context (suspend).
     *
     * Signal Double Ratchet requires messages to be decrypted in chronological
     * order (ascending ID). The server typically returns messages in DESC order,
     * so we sort ascending before decryption to ensure the X3DH init message
     * (which contains "ik"/"ek" fields) is always processed before the DR-only
     * messages that depend on the session it establishes.
     * After decryption we restore the original server order.
     */
    private suspend fun List<Message>.decryptAll(): List<Message> {
        val ascendingOrder = sortedBy { it.id }
        val decryptedById = HashMap<Long, Message>(size)
        for (msg in ascendingOrder) decryptedById[msg.id] = decryptMessageFully(msg)
        return map { decryptedById[it.id] ?: it }
    }

    /**
     * 📤 Завантажує список контактів для пересилання
     */
    fun loadForwardContacts() {
        if (UserSession.accessToken == null) {
            Log.e("MessagesViewModel", "Не авторизовано")
            return
        }

        viewModelScope.launch {
            try {
                val response = nodeApi.getChats(limit = 100)

                if (response.apiStatus == 200) {
                    val contacts = response.data?.map { chat ->
                        ForwardRecipient(
                            id = chat.userId,
                            name = chat.username ?: "Користувач",
                            avatarUrl = chat.avatarUrl ?: "",
                            isGroup = false
                        )
                    } ?: emptyList()

                    _forwardContacts.value = contacts
                    Log.d("MessagesViewModel", "Завантажено ${contacts.size} контактів для пересилання")
                } else {
                    Log.e("MessagesViewModel", "Помилка завантаження контактів: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                Log.e("MessagesViewModel", "Помилка завантаження контактів", e)
            }
        }
    }

    /**
     * 📤 Завантажує список груп для пересилання
     */
    fun loadForwardGroups() {
        if (UserSession.accessToken == null) {
            Log.e("MessagesViewModel", "Не авторизовано")
            return
        }

        viewModelScope.launch {
            try {
                val response = groupApi.getGroups(limit = 100)

                if (response.apiStatus == 200) {
                    // Конвертуємо групи в ForwardRecipient
                    val groups = response.groups?.map { group ->
                        ForwardRecipient(
                            id = group.id,
                            name = group.name,
                            avatarUrl = group.avatarUrl,
                            isGroup = true
                        )
                    } ?: emptyList()

                    _forwardGroups.value = groups
                    Log.d("MessagesViewModel", "Завантажено ${groups.size} груп для пересилання")
                } else {
                    Log.e("MessagesViewModel", "Помилка завантаження груп: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                Log.e("MessagesViewModel", "Помилка завантаження груп", e)
            }
        }
    }

    /**
     * 📤 Пересилає повідомлення до вибраних отримувачів.
     * Приватні чати — Node.js /api/node/chat/forward (сервер розшифровує/перешифровує текст).
     * Групи — PHP sendGroupMessage.
     */
    fun forwardMessages(messageIds: Set<Long>, recipientIds: List<Long>) {
        if (UserSession.accessToken == null) {
            Log.e("MessagesViewModel", "Не авторизовано")
            return
        }

        // Split recipients into groups vs private users
        val groupIds   = recipientIds.filter { id -> _forwardGroups.value.any { it.id == id } }
        val privateIds = recipientIds.filter { id -> !_forwardGroups.value.any { it.id == id } }

        // Groups — PHP
        if (groupIds.isNotEmpty()) {
            viewModelScope.launch {
                try {
                    messageIds.forEach { messageId ->
                        val message = _messages.value.find { it.id == messageId }
                        if (message != null) {
                            groupIds.forEach { gId ->
                                groupApi.sendGroupMessage(groupId = gId, text = message.decryptedText ?: "")
                                Log.d("MessagesViewModel", "Forwarded msg $messageId to group $gId via Node.js")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MessagesViewModel", "Forward to group error", e)
                }
            }
        }

        // Private chats — Node.js (decrypts+re-encrypts on server side)
        if (privateIds.isNotEmpty()) {
            forwardPrivateMsgsViaNode(
                scope        = viewModelScope,
                api          = nodeApi,
                messageIds   = messageIds,
                recipientIds = privateIds,
                onResult     = { allOk ->
                    if (!allOk) Log.w("MessagesViewModel", "Some private forwards failed")
                }
            )
        }
    }

    /**
     * 📌 Закріпити повідомлення в групі
     */
    fun pinGroupMessage(
        messageId: Long,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (UserSession.accessToken == null || groupId == 0L) {
            onError("Не авторизовано або це не група")
            return
        }

        viewModelScope.launch {
            try {
                val response = groupApi.pinGroupMessage(groupId = groupId, messageId = messageId)

                if (response.apiStatus == 200) {
                    fetchGroupDetails(groupId)
                    onSuccess()
                    Log.d(TAG, "📌 Message $messageId pinned in group $groupId")
                } else {
                    val errorMsg = response.errorMessage ?: "Не вдалося закріпити повідомлення"
                    onError(errorMsg)
                    Log.e(TAG, "❌ Failed to pin message: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                val errorMsg = "Помилка: ${e.localizedMessage}"
                onError(errorMsg)
                Log.e(TAG, "❌ Error pinning message", e)
            }
        }
    }

    /**
     * 📌 Відкріпити повідомлення в групі
     */
    fun unpinGroupMessage(
        messageId: Long? = null,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (UserSession.accessToken == null || groupId == 0L) {
            onError("Не авторизовано або це не група")
            return
        }

        viewModelScope.launch {
            try {
                val response = groupApi.unpinGroupMessage(groupId = groupId, messageId = messageId)

                if (response.apiStatus == 200) {
                    fetchGroupDetails(groupId)
                    onSuccess()
                    Log.d(TAG, "📌 Message $messageId unpinned in group $groupId")
                } else {
                    val errorMsg = response.errorMessage ?: "Не вдалося відкріпити повідомлення"
                    onError(errorMsg)
                    Log.e(TAG, "❌ Failed to unpin message: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                val errorMsg = "Помилка: ${e.localizedMessage}"
                onError(errorMsg)
                Log.e(TAG, "❌ Error unpinning message", e)
            }
        }
    }

    /**
     * 🔕 Вимкнути сповіщення для групи
     */
    fun muteGroup(
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (UserSession.accessToken == null || groupId == 0L) {
            onError("Не авторизовано або це не група")
            return
        }

        viewModelScope.launch {
            try {
                val response = groupApi.muteGroup(groupId = groupId)

                if (response.apiStatus == 200) {
                    fetchGroupDetails(groupId)
                    onSuccess()
                    Log.d(TAG, "🔕 Group $groupId muted")
                } else {
                    val errorMsg = response.errorMessage ?: "Не вдалося вимкнути сповіщення"
                    onError(errorMsg)
                    Log.e(TAG, "❌ Failed to mute group: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                val errorMsg = "Помилка: ${e.localizedMessage}"
                onError(errorMsg)
                Log.e(TAG, "❌ Error muting group", e)
            }
        }
    }

    /**
     * 🔔 Увімкнути сповіщення для групи
     */
    fun unmuteGroup(
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (UserSession.accessToken == null || groupId == 0L) {
            onError("Не авторизовано або це не група")
            return
        }

        viewModelScope.launch {
            try {
                val response = groupApi.unmuteGroup(groupId = groupId)

                if (response.apiStatus == 200) {
                    fetchGroupDetails(groupId)
                    onSuccess()
                    Log.d(TAG, "🔔 Group $groupId unmuted")
                } else {
                    val errorMsg = response.errorMessage ?: "Не вдалося увімкнути сповіщення"
                    onError(errorMsg)
                    Log.e(TAG, "❌ Failed to unmute group: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                val errorMsg = "Помилка: ${e.localizedMessage}"
                onError(errorMsg)
                Log.e(TAG, "❌ Error unmuting group", e)
            }
        }
    }

    /**
     * ✏️ Edit message text locally only — no server call, not visible to other party.
     */
    fun editMessageLocally(messageId: Long, newText: String) {
        if (newText.isBlank()) return
        val current = _messages.value.toMutableList()
        val idx = current.indexOfFirst { it.id == messageId }
        if (idx != -1) {
            current[idx] = current[idx].copy(
                decryptedText = newText,
                encryptedText = newText
            )
            _messages.value = current
            Log.d(TAG, "✏️ Message $messageId edited locally only")
        }
    }

    /**
     * 🔕 Вимкнути сповіщення для приватного чату (Node.js)
     */
    fun mutePrivateChat(
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        mutePrivateChatViaNode(
            scope     = viewModelScope,
            api       = nodeApi,
            chatId    = recipientId,
            mute      = true,
            isLoading = _isLoading,
            error     = _error,
            onSuccess = { isMuted ->
                _isMutedPrivate.value = isMuted
                onSuccess()
            }
        )
    }

    /**
     * 🔔 Увімкнути сповіщення для приватного чату (Node.js)
     */
    fun unmutePrivateChat(
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        mutePrivateChatViaNode(
            scope     = viewModelScope,
            api       = nodeApi,
            chatId    = recipientId,
            mute      = false,
            isLoading = _isLoading,
            error     = _error,
            onSuccess = { isMuted ->
                _isMutedPrivate.value = isMuted
                onSuccess()
            }
        )
    }

    /**
     * 🗑️ Save media auto-delete setting for the current private chat (Node.js)
     */
    fun saveMediaAutoDeleteSetting(
        option: MediaAutoDeleteOption,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        saveMediaAutoDeleteSettingViaNode(
            scope     = viewModelScope,
            api       = nodeApi,
            chatId    = recipientId,
            option    = option,
            isLoading = _isLoading,
            error     = _error,
            onSuccess = { saved ->
                _mediaAutoDeleteOption.value = saved
                onSuccess()
            }
        )
    }

    /**
     * 🔍 Поиск сообщений в группе
     */
    fun searchGroupMessages(query: String) {
        if (UserSession.accessToken == null || groupId == 0L) {
            Log.e(TAG, "Cannot search: not authorized or not in group")
            return
        }

        if (query.length < 2) {
            // Очищаем результаты поиска
            _searchResults.value = emptyList()
            _searchQuery.value = ""
            _searchTotalCount.value = 0
            _currentSearchIndex.value = 0
            return
        }

        _isSearching.value = true
        _searchQuery.value = query

        viewModelScope.launch {
            try {
                val response = groupApi.searchGroupMessages(
                    groupId = groupId,
                    query = query,
                    limit = 100
                )

                if (response.apiStatus == 200) {
                    val messages = response.messages ?: emptyList()
                    _searchResults.value = messages
                    _searchTotalCount.value = response.count ?: 0
                    _currentSearchIndex.value = if (messages.isNotEmpty()) 0 else -1
                    Log.d(TAG, "🔍 Search completed: found ${response.count} results for '$query'")
                } else {
                    Log.e(TAG, "❌ Search failed: ${response.errorMessage}")
                    _searchResults.value = emptyList()
                    _searchTotalCount.value = 0
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error searching messages", e)
                _searchResults.value = emptyList()
                _searchTotalCount.value = 0
            } finally {
                _isSearching.value = false
            }
        }
    }

    /**
     * 🔍 Пошук повідомлень у приватному чаті (Node.js)
     */
    fun searchPrivateMessages(query: String) {
        if (recipientId == 0L || groupId != 0L) return

        if (query.length < 2) {
            _searchResults.value = emptyList()
            _searchQuery.value = ""
            _searchTotalCount.value = 0
            _currentSearchIndex.value = 0
            return
        }

        _isSearching.value = true
        _searchQuery.value = query

        viewModelScope.launch {
            try {
                val response = nodeApi.searchMessages(
                    recipientId = recipientId,
                    query = query,
                    limit = 100
                )
                if (response.apiStatus == 200) {
                    val messages = response.messages?.decryptAll() ?: emptyList()
                    _searchResults.value = messages
                    _searchTotalCount.value = messages.size
                    _currentSearchIndex.value = if (messages.isNotEmpty()) 0 else -1
                    Log.d(TAG, "🔍 Private search: ${messages.size} results for '$query'")
                } else {
                    _searchResults.value = emptyList()
                    _searchTotalCount.value = 0
                    Log.e(TAG, "Private search failed: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                _searchResults.value = emptyList()
                _searchTotalCount.value = 0
                Log.e(TAG, "searchPrivateMessages error", e)
            } finally {
                _isSearching.value = false
            }
        }
    }

    /**
     * 📌 Закріпити / відкріпити повідомлення в приватному чаті
     */
    fun pinPrivateMessage(messageId: Long, pin: Boolean = true) {
        if (recipientId == 0L || groupId != 0L) return

        viewModelScope.launch {
            try {
                val resp = nodeApi.pinMessage(
                    messageId = messageId,
                    chatId = recipientId,
                    pin = if (pin) "yes" else "no"
                )
                if (resp.apiStatus == 200) {
                    val current = _pinnedPrivateMessages.value.toMutableList()
                    if (pin) {
                        val msg = _messages.value.find { it.id == messageId }
                        if (msg != null && current.none { it.id == messageId }) {
                            current.add(0, msg)
                            _pinnedPrivateMessages.value = current.take(if (UserSession.isProActive) 15 else 5)
                        }
                    } else {
                        _pinnedPrivateMessages.value = current.filter { it.id != messageId }
                    }
                    Log.d(TAG, "📌 Message $messageId ${if (pin) "pinned" else "unpinned"} in private chat")
                } else {
                    _error.value = resp.errorMessage ?: "Не вдалося закріпити повідомлення"
                }
            } catch (e: Exception) {
                _error.value = "Помилка: ${e.localizedMessage}"
                Log.e(TAG, "pinPrivateMessage error", e)
            }
        }
    }

    /**
     * 🔍 Перейти к следующему результату поиска
     */
    fun nextSearchResult() {
        val results = _searchResults.value
        if (results.isEmpty()) return

        val currentIndex = _currentSearchIndex.value
        val nextIndex = (currentIndex + 1) % results.size
        _currentSearchIndex.value = nextIndex
        Log.d(TAG, "🔍 Next result: ${nextIndex + 1} of ${results.size}")
    }

    /**
     * 🔍 Перейти к предыдущему результату поиска
     */
    fun previousSearchResult() {
        val results = _searchResults.value
        if (results.isEmpty()) return

        val currentIndex = _currentSearchIndex.value
        val prevIndex = if (currentIndex > 0) currentIndex - 1 else results.size - 1
        _currentSearchIndex.value = prevIndex
        Log.d(TAG, "🔍 Previous result: ${prevIndex + 1} of ${results.size}")
    }

    /**
     * 🔍 Очистить результаты поиска
     */
    fun clearSearch() {
        _searchResults.value = emptyList()
        _searchQuery.value = ""
        _searchTotalCount.value = 0
        _currentSearchIndex.value = 0
        _isSearching.value = false
        Log.d(TAG, "🔍 Search cleared")
    }

    /**
     * 🔍 Установить поисковый запрос
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        Log.d(TAG, "🔍 Search query set to: $query")
    }

    // ==================== MEDIA LOADING ====================

    /**
     * 📥 Завантажити превью (thumbnail) для медіа-повідомлення
     * Викликається автоматично при скролі до повідомлення з медіа
     */
    fun loadMessageThumbnail(message: Message) {
        if (message.mediaUrl.isNullOrEmpty()) {
            Log.d(TAG, "⚠️ Message ${message.id} has no media URL")
            return
        }

        // Перевіряємо чи можна завантажувати медіа
        if (!socketManager?.canAutoLoadMedia()!!) {
            Log.d(TAG, "⚠️ Auto-loading disabled due to connection quality")
            return
        }

        viewModelScope.launch {
            try {
                val progressFlow = mediaLoader.loadThumbnail(
                    messageId = message.id,
                    thumbnailUrl = message.mediaUrl,
                    priority = 5
                )

                progressFlow.collect { state ->
                    when (state.state) {
                        MediaLoadingManager.LoadingState.THUMB_LOADED -> {
                            Log.d(TAG, "✅ Thumbnail loaded for message ${message.id}")
                            // UI автоматично оновиться через StateFlow
                        }
                        MediaLoadingManager.LoadingState.ERROR -> {
                            Log.e(TAG, "❌ Failed to load thumbnail: ${state.error}")
                        }
                        else -> {
                            // Loading...
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading thumbnail", e)
            }
        }
    }

    /**
     * 📥 Завантажити повне медіа (при кліку користувача)
     */
    fun loadFullMedia(message: Message) {
        if (message.mediaUrl.isNullOrEmpty()) {
            Log.d(TAG, "⚠️ Message ${message.id} has no media URL")
            return
        }

        viewModelScope.launch {
            try {
                val progressFlow = mediaLoader.loadFullMedia(
                    messageId = message.id,
                    mediaUrl = message.mediaUrl,
                    priority = 10 // Вищий пріоритет для повного медіа
                )

                progressFlow.collect { state ->
                    when (state.state) {
                        MediaLoadingManager.LoadingState.LOADING_FULL -> {
                            Log.d(TAG, "📥 Loading full media: ${state.progress}%")
                        }
                        MediaLoadingManager.LoadingState.FULL_LOADED -> {
                            Log.d(TAG, "✅ Full media loaded for message ${message.id}")
                            // UI автоматично оновиться
                        }
                        MediaLoadingManager.LoadingState.ERROR -> {
                            Log.e(TAG, "❌ Failed to load full media: ${state.error}")
                            _error.value = "Не вдалося завантажити медіа"
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading full media", e)
                _error.value = "Помилка завантаження: ${e.message}"
            }
        }
    }

    /**
     * Чи можна завантажувати медіа автоматично?
     * Залежить від якості з'єднання
     */
    fun shouldAutoLoadMedia(): Boolean {
        return socketManager?.canAutoLoadMedia() ?: true
    }

    /**
     * Отримати опис якості з'єднання для відображення в UI
     */
    fun getQualityDescription(): String {
        return socketManager?.getQualityDescription() ?: "🔴 Немає з'єднання"
    }

    // ==================== END MEDIA LOADING ====================

    // ==================== CHAT ACTIONS ====================

    /**
     * 🗑️ Очистити історію чату
     */
    fun clearChatHistory(
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (UserSession.accessToken == null) {
            onError("Не авторизовано")
            return
        }

        viewModelScope.launch {
            try {
                if (groupId != 0L) {
                    // Group → Node.js: "for me" = clear-self, "for all" = clear-all (admin only)
                    val forAll = UserSession.userId?.let { uid ->
                        // we don't know admin status here, try clear-all first; fallback to self
                        false // callers should pass deleteType to distinguish
                    } ?: false
                    val resp = groupApi.clearGroupHistorySelf(groupId)
                    if (resp.apiStatus == 200) {
                        _messages.value = emptyList()
                        onSuccess()
                        Log.d(TAG, "🗑️ Group history cleared for self (Node.js)")
                    } else {
                        onError(resp.errorMessage ?: "Не вдалося очистити історію")
                    }
                    return@launch
                }
                // Private chat → Node.js
                val response = nodeApi.clearHistory(recipientId = recipientId)
                if (response.apiStatus == 200) {
                    _messages.value = emptyList()
                    onSuccess()
                    Log.d(TAG, "🗑️ Chat history cleared (Node.js)")
                } else {
                    onError(response.errorMessage ?: "Не вдалося очистити історію")
                }
            } catch (e: Exception) {
                onError("Помилка: ${e.localizedMessage}")
                Log.e(TAG, "❌ Error clearing chat history", e)
            }
        }
    }

    /**
     * 🗑️ Очистити всю переписку у приватному чаті для обох сторін.
     * Сервер позначає повідомлення deleted_one=1 + deleted_two=1 та шле
     * socket-подію private_history_cleared другому учаснику.
     */
    fun clearPrivateChatHistoryForAll(
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (recipientId == 0L || groupId != 0L) return
        viewModelScope.launch {
            try {
                val response = nodeApi.clearHistory(recipientId = recipientId, clearType = "everyone")
                if (response.apiStatus == 200) {
                    permanentlyDeletedIds.addAll(_messages.value.map { it.id })
                    _messages.value = emptyList()
                    onSuccess()
                    Log.d(TAG, "🗑️ Private chat history cleared for both parties")
                } else {
                    onError(response.errorMessage ?: "Не вдалося очистити історію")
                }
            } catch (e: Exception) {
                onError("Помилка: ${e.localizedMessage}")
                Log.e(TAG, "❌ Error clearing private chat history for all", e)
            }
        }
    }

    /**
     * 🗑️ Очистити історію групи для всіх (тільки адміни)
     */
    fun clearGroupHistoryForAll(
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (groupId == 0L) return
        viewModelScope.launch {
            try {
                val resp = groupApi.clearGroupHistoryAdmin(groupId)
                if (resp.apiStatus == 200) {
                    _messages.value = emptyList()
                    onSuccess()
                    Log.d(TAG, "🗑️ Group history cleared for all (Node.js)")
                } else {
                    onError(resp.errorMessage ?: "Не вдалося очистити історію для всіх")
                }
            } catch (e: Exception) {
                onError("Помилка: ${e.localizedMessage}")
            }
        }
    }

    /**
     * 🚫 Заблокувати користувача
     */
    fun blockUser(
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (UserSession.accessToken == null || recipientId == 0L) {
            onError("Не авторизовано або невірний користувач")
            return
        }

        viewModelScope.launch {
            try {
                val response = NodeRetrofitClient.profileApi.blockUser(recipientId)

                if (response.apiStatus == 200) {
                    onSuccess()
                    Log.d(TAG, "🚫 User $recipientId blocked")
                } else {
                    val errorMsg = response.errorMessage ?: "Не вдалося заблокувати користувача"
                    onError(errorMsg)
                    Log.e(TAG, "❌ Failed to block user: $errorMsg")
                }
            } catch (e: Exception) {
                val errorMsg = "Помилка: ${e.localizedMessage}"
                onError(errorMsg)
                Log.e(TAG, "❌ Error blocking user", e)
            }
        }
    }

    // ==================== END CHAT ACTIONS ====================

    // ==================== TEXT FORMATTING ====================

    /**
     * Застосовує форматування до тексту
     * Обгортає весь текст у вказані маркери форматування
     *
     * @param text Текст для форматування
     * @param prefix Маркер на початку (наприклад, "**" для жирного)
     * @param suffix Маркер в кінці (наприклад, "**" для жирного)
     * @return Відформатований текст
     */
    fun applyFormatting(text: String, prefix: String, suffix: String): String {
        return if (text.isNotEmpty()) {
            "$prefix$text$suffix"
        } else {
            "$prefix$suffix" // Повертаємо порожні маркери, щоб користувач міг друкувати між ними
        }
    }

    // ==================== END TEXT FORMATTING ====================

    /**
     * Періодичне оновлення повідомлень (fallback якщо Socket.IO не працює)
     */
    private fun startMessagePolling() {
        messagePollingJob?.cancel()
        messagePollingJob = viewModelScope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(5000) // Кожні 5 секунд
                refreshLatestMessages()
            }
        }
        Log.d(TAG, "🔄 Polling повідомлень запущено (кожні 5с)")
    }

    /**
     * Оновлює останні повідомлення з сервера (легкий запит)
     */
    private fun refreshLatestMessages() {
        if (UserSession.accessToken == null) return

        viewModelScope.launch {
            try {
                if (recipientId != 0L && groupId == 0L) {
                    // ── Private chat → Node.js ────────────────────────────────
                    val response = nodeApi.getMessages(
                        recipientId    = recipientId,
                        limit          = 15,
                        beforeMessageId = 0,
                        isBusinessChat = if (isBusinessChat) 1 else 0
                    )
                    if (response.apiStatus == 200 && response.messages != null) {
                        val newMessages = response.messages.decryptAll()
                        val currentIds = _messages.value.map { it.id }.toSet()
                        val trulyNew = newMessages.filter { it.id !in currentIds }
                        if (trulyNew.isNotEmpty()) {
                            setMessagesSafe((_messages.value + trulyNew).distinctBy { it.id }.sortedBy { it.timeStamp })
                            Log.d(TAG, "🔄 Polling (Node.js): +${trulyNew.size} нових")
                        }
                        // Detect messages deleted "for everyone" that weren't removed via socket.
                        // Any local message whose timestamp falls within the server's response window
                        // but is absent from the response was deleted on the server side.
                        if (newMessages.isNotEmpty()) {
                            val oldestServerTs = newMessages.minOf { it.timeStamp }
                            val serverIds = newMessages.map { it.id }.toSet()
                            val likelyDeleted = _messages.value.filter { local ->
                                local.timeStamp >= oldestServerTs &&
                                local.id !in serverIds &&
                                !local.isLocalPending &&
                                local.id !in permanentlyDeletedIds
                            }
                            if (likelyDeleted.isNotEmpty()) {
                                val deletedIds = likelyDeleted.map { it.id }.toSet()
                                withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                                    permanentlyDeletedIds.addAll(deletedIds)
                                    _messages.update { list -> list.filter { it.id !in deletedIds } }
                                }
                                Log.d(TAG, "🔄 Polling: виявлено ${likelyDeleted.size} видалених (для всіх)")
                            }
                        }
                    }
                    return@launch
                }

                // ── Group chat → Node.js ──────────────────────────────────────
                if (groupId == 0L) return@launch
                val response = groupApi.getGroupMessages(groupId = groupId, limit = 15)
                if (response.apiStatus == 200 && response.messages != null) {
                    val newMessages = response.messages!!.decryptAll()
                    val currentIds = _messages.value.map { it.id }.toSet()
                    val trulyNew = newMessages.filter { it.id !in currentIds }
                    if (trulyNew.isNotEmpty()) {
                        _messages.value = (_messages.value + trulyNew).distinctBy { it.id }.sortedBy { it.timeStamp }
                        Log.d(TAG, "🔄 Polling: додано ${trulyNew.size} нових повідомлень")
                    }
                    // Same deletion-detection fallback for group chats.
                    if (newMessages.isNotEmpty()) {
                        val oldestServerTs = newMessages.minOf { it.timeStamp }
                        val serverIds = newMessages.map { it.id }.toSet()
                        val likelyDeleted = _messages.value.filter { local ->
                            local.timeStamp >= oldestServerTs &&
                            local.id !in serverIds &&
                            !local.isLocalPending &&
                            local.id !in permanentlyDeletedIds
                        }
                        if (likelyDeleted.isNotEmpty()) {
                            val deletedIds = likelyDeleted.map { it.id }.toSet()
                            withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                                permanentlyDeletedIds.addAll(deletedIds)
                                _messages.update { list -> list.filter { it.id !in deletedIds } }
                            }
                            Log.d(TAG, "🔄 Polling (group): виявлено ${likelyDeleted.size} видалених")
                        }
                    }
                }
            } catch (e: Exception) {
                // Тихо ігноруємо помилки polling - не турбуємо користувача
                Log.w(TAG, "Polling error: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()

        // Зупиняємо polling
        messagePollingJob?.cancel()

        // Notify server the chat is no longer open (so status events stop for this chat)
        if (recipientId > 0L) socketManager?.closeChat(recipientId)

        // Зупиняємо Socket.IO
        socketManager?.disconnect()

        // Зупиняємо моніторинг якості
        qualityMonitorJob?.cancel()

        // Зупиняємо автозбереження чернетки
        draftAutoSaveJob?.cancel()

        // Зупиняємо Live Location
        liveLocationManager?.destroy()
        liveLocationManager = null

        // Очищуємо MediaLoader
        mediaLoader.cleanup()

        Log.d(TAG, "🧹 ViewModel очищена")
    }

    // ── Mark messages as seen ─────────────────────────────────────────────

    /**
     * Позначає всі повідомлення від співрозмовника як прочитані на сервері.
     * Викликається при відкритті чату та при отриманні нових повідомлень.
     */
    fun markSeen() {
        if (recipientId == 0L) return
        viewModelScope.launch {
            try {
                nodeApi.markSeen(recipientId)
                Log.d(TAG, "Marked messages as seen from user $recipientId")
            } catch (e: Exception) {
                Log.e(TAG, "markSeen error", e)
            }
        }
    }

    // ==================== POLLS ====================

    fun voteOnGroupPoll(message: Message, optionId: Long) {
        val pollId = message.stickers?.let {
            runCatching {
                com.google.gson.Gson().fromJson(it, com.worldmates.messenger.data.model.GroupPollData::class.java).id
            }.getOrNull()
        } ?: return

        viewModelScope.launch {
            try {
                val response = groupApi.votePoll(
                    groupId   = groupId,
                    pollId    = pollId,
                    optionIds = listOf(optionId)
                )
                if (response.apiStatus == 200 && response.poll != null) {
                    val updatedPollJson = com.google.gson.Gson().toJson(response.poll)
                    _messages.value = _messages.value.map { msg ->
                        if (msg.id == message.id) msg.copy(stickers = updatedPollJson) else msg
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "voteOnGroupPoll error: ${e.localizedMessage}")
            }
        }
    }
}

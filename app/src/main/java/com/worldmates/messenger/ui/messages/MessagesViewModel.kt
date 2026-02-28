package com.worldmates.messenger.ui.messages

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.data.Constants
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.data.model.Message
import com.worldmates.messenger.data.model.MessageReaction
import com.worldmates.messenger.data.model.ReactionGroup
import com.worldmates.messenger.network.FileManager
import com.worldmates.messenger.network.MediaUploader
import com.worldmates.messenger.network.MediaLoadingManager
import com.worldmates.messenger.network.NetworkQualityMonitor
import com.worldmates.messenger.network.NodeRetrofitClient
import com.worldmates.messenger.network.RetrofitClient
import com.worldmates.messenger.network.SocketManager
import com.worldmates.messenger.utils.DecryptionUtility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.worldmates.messenger.ui.messages.selection.ForwardRecipient
import com.worldmates.messenger.data.repository.DraftRepository
import com.worldmates.messenger.data.local.entity.Draft
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.json.JSONObject
import java.io.File

class MessagesViewModel(application: Application) :
    AndroidViewModel(application), SocketManager.ExtendedSocketListener {

    private val context = application
    private val nodeApi = NodeRetrofitClient.api
    private val groupApi = NodeRetrofitClient.groupApi

    companion object {
        private const val TAG = "MessagesViewModel"
        private const val DRAFT_AUTO_SAVE_DELAY = 5000L // 5 секунд
    }

    init {
        Log.d(TAG, "🚀 MessagesViewModel створено!")
        Log.d(TAG, "Access Token: ${UserSession.accessToken?.take(10)}...")
        Log.d(TAG, "User ID: ${UserSession.userId}")
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

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping

    // true = співрозмовник зараз записує голосове повідомлення
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _recipientOnlineStatus = MutableStateFlow(false)
    val recipientOnlineStatus: StateFlow<Boolean> = _recipientOnlineStatus

    private val _forwardContacts = MutableStateFlow<List<ForwardRecipient>>(emptyList())
    val forwardContacts: StateFlow<List<ForwardRecipient>> = _forwardContacts

    private val _forwardGroups = MutableStateFlow<List<ForwardRecipient>>(emptyList())
    val forwardGroups: StateFlow<List<ForwardRecipient>> = _forwardGroups

    // ==================== GROUPS ====================
    private val _currentGroup = MutableStateFlow<com.worldmates.messenger.data.model.Group?>(null)
    val currentGroup: StateFlow<com.worldmates.messenger.data.model.Group?> = _currentGroup
    // ==================== END GROUPS ====================

    // ==================== PRIVATE CHAT PIN ====================
    private val _pinnedPrivateMessage = MutableStateFlow<Message?>(null)
    val pinnedPrivateMessage: StateFlow<Message?> = _pinnedPrivateMessage
    // ==================== END PRIVATE CHAT PIN ====================

    // ==================== PRIVATE CHAT MUTE ====================
    private val _isMutedPrivate = MutableStateFlow(false)
    val isMutedPrivate: StateFlow<Boolean> = _isMutedPrivate
    // ==================== END PRIVATE CHAT MUTE ====================

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

    private var recipientId: Long = 0
    private var groupId: Long = 0
    private var topicId: Long = 0 // 📁 Topic/Subgroup ID for topic-based filtering
    private var socketManager: SocketManager? = null
    private var mediaUploader: MediaUploader? = null
    private var fileManager: FileManager? = null
    private var messagePollingJob: Job? = null

    // 🎥 Публічні getters для відеодзвінків
    fun getRecipientId(): Long = recipientId
    fun getGroupId(): Long = groupId
    fun getTopicId(): Long = topicId

    fun initialize(recipientId: Long) {
        Log.d("MessagesViewModel", "🔧 initialize() викликано для користувача $recipientId")
        this.recipientId = recipientId
        this.groupId = 0
        this.topicId = 0
        fetchMessages()
        setupSocket()
        startMessagePolling()
        loadDraft()
        markSeen() // Позначаємо повідомлення як прочитані при відкритті чату
        loadMuteStatusViaNode(viewModelScope, nodeApi, recipientId) { isMuted ->
            _isMutedPrivate.value = isMuted
        }
        Log.d("MessagesViewModel", "✅ Ініціалізація завершена для користувача $recipientId")
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
                val response = RetrofitClient.apiService.getGroupDetails(
                    accessToken = UserSession.accessToken!!,
                    groupId = groupId
                )

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
                    recipientId = recipientId,
                    limit = Constants.MESSAGES_PAGE_SIZE,
                    beforeMessageId = beforeMessageId
                )

                if (response.apiStatus == 200 && response.messages != null) {
                    val decryptedMessages = response.messages.map { msg ->
                        decryptMessageFully(msg)
                    }

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
                    recipientId     = recipientId,
                    beforeMessageId = oldestId,
                    limit           = 30
                )
                if (response.apiStatus == 200 && response.messages != null) {
                    val older = response.messages.map { decryptMessageFully(it) }
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
                // Використовуємо API для групових повідомлень (з опціональною фільтрацією по топіку)
                val response = RetrofitClient.apiService.getGroupMessages(
                    accessToken = UserSession.accessToken!!,
                    groupId = groupId,
                    topicId = topicId, // Фільтруємо по топіку якщо вказано
                    limit = Constants.MESSAGES_PAGE_SIZE,
                    beforeMessageId = beforeMessageId
                )

                if (response.apiStatus == 200 && response.messages != null) {
                    val decryptedMessages = response.messages!!.map { msg ->
                        decryptMessageFully(msg)
                    }

                    val currentMessages = _messages.value.toMutableList()
                    currentMessages.addAll(decryptedMessages)
                    // Сортируем по времени (старые сверху, новые внизу)
                    setMessagesSafe(currentMessages.distinctBy { it.id }.sortedBy { it.timeStamp })

                    _error.value = null
                    if (topicId != 0L) {
                        Log.d("MessagesViewModel", "Завантажено ${decryptedMessages.size} повідомлень топіку $topicId")
                    } else {
                        Log.d("MessagesViewModel", "Завантажено ${decryptedMessages.size} повідомлень групи")
                    }
                } else {
                    _error.value = response.errorMessage ?: "Помилка завантаження повідомлень"
                }

                _isLoading.value = false
            } catch (e: Exception) {
                _error.value = "Помилка: ${e.localizedMessage}"
                _isLoading.value = false
                Log.e("MessagesViewModel", "Помилка завантаження повідомлень групи", e)
            }
        }
    }

    /**
     * Надсилает текстовое сообщение
     */
    fun sendMessage(text: String, replyToId: Long? = null) {
        if (UserSession.accessToken == null || (recipientId == 0L && groupId == 0L) || text.isBlank()) {
            _error.value = "Не можна надіслати порожнє повідомлення"
            return
        }

        _isLoading.value = true

        viewModelScope.launch {
            try {
                // ── Private chat → Node.js ────────────────────────────────────────
                if (groupId == 0L) {
                    val resp = nodeApi.sendMessage(
                        recipientId = recipientId,
                        text = text,
                        replyId = replyToId
                    )
                    if (resp.apiStatus == 200) {
                        val newMsg = resp.messageData?.let { decryptMessageFully(it) }
                        if (newMsg != null) {
                            val curr = _messages.value
                            if (!curr.any { it.id == newMsg.id }) {
                                _messages.value = (curr + newMsg).sortedBy { it.timeStamp }
                            }
                        } else {
                            _messages.value = emptyList()
                            fetchMessages()
                        }
                        _error.value = null
                        deleteDraft()
                        Log.d("MessagesViewModel", "Повідомлення надіслано через Node.js")
                    } else {
                        _error.value = resp.errorMessage ?: "Не вдалося надіслати повідомлення"
                        Log.e("MessagesViewModel", "Node.js send error: ${resp.errorMessage}")
                    }
                    _isLoading.value = false
                    return@launch
                }

                // ── Group chat → PHP ──────────────────────────────────────────────
                val messageHashId = System.currentTimeMillis().toString()

                val response = if (groupId != 0L) {
                    // Використовуємо API для відправки в групу (з опціональним топіком)
                    RetrofitClient.apiService.sendGroupMessage(
                        accessToken = UserSession.accessToken!!,
                        groupId = groupId,
                        topicId = topicId, // Якщо є топік, повідомлення буде прив'язане до нього
                        text = text,
                        replyToId = replyToId
                    )
                } else {
                    RetrofitClient.apiService.sendMessage(
                        accessToken = UserSession.accessToken!!,
                        recipientId = recipientId,
                        text = text,
                        messageHashId = messageHashId,
                        replyToId = replyToId
                    )
                }

                Log.d("MessagesViewModel", "API Response: status=${response.apiStatus}, messages=${response.messages?.size}, message=${response.message}, allMessages=${response.allMessages?.size}, errors=${response.errors}")

                if (response.apiStatus == 200) {
                    // Если API вернул сообщения, добавляем их в список
                    val receivedMessages = response.allMessages
                    Log.d("MessagesViewModel", "receivedMessages: $receivedMessages")
                    if (receivedMessages != null && receivedMessages.isNotEmpty()) {
                        val decryptedMessages = receivedMessages.map { msg ->
                            decryptMessageFully(msg)
                        }

                        val currentMessages = _messages.value.toMutableList()
                        currentMessages.addAll(decryptedMessages)
                        // Сортируем по времени (старые сверху, новые внизу)
                        setMessagesSafe(currentMessages.distinctBy { it.id }.sortedBy { it.timeStamp })
                        Log.d("MessagesViewModel", "Додано ${decryptedMessages.size} нових повідомлень")
                    } else {
                        // Если API не вернул сообщения, перезагружаем весь список
                        Log.d("MessagesViewModel", "API не повернув повідомлення, перезавантажуємо список")
                        if (groupId != 0L) {
                            fetchGroupMessages()
                        } else {
                            fetchMessages()
                        }
                    }

                    // КРИТИЧНО: Эмитим Socket.IO событие для real-time доставки
                    if (groupId != 0L) {
                        socketManager?.sendGroupMessage(groupId, text)
                        Log.d("MessagesViewModel", "Socket.IO: Відправлено групове повідомлення")
                    } else {
                        socketManager?.sendMessage(recipientId, text)
                        Log.d("MessagesViewModel", "Socket.IO: Відправлено приватне повідомлення")
                    }

                    _error.value = null
                    deleteDraft() // Удаляем черновик после успешной отправки
                    Log.d("MessagesViewModel", "Повідомлення надіслано")
                } else {
                    _error.value = response.errors?.errorText ?: response.errorMessage ?: "Не вдалося надіслати повідомлення"
                    Log.e("MessagesViewModel", "Send Error: ${response.errors?.errorText ?: response.errorMessage}")
                }

                _isLoading.value = false
            } catch (e: Exception) {
                _error.value = "Помилка: ${e.localizedMessage}"
                _isLoading.value = false
                Log.e("MessagesViewModel", "Помилка надсилання повідомлення", e)
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

                // PHP fallback (legacy)
                val response = RetrofitClient.apiService.editMessage(
                    accessToken = UserSession.accessToken!!,
                    messageId = messageId,
                    newText = newText
                )
                if (response.apiStatus == 200) {
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
                } else {
                    _error.value = response.errors?.errorText ?: "Не вдалося відредагувати повідомлення"
                }
                _isLoading.value = false
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

                // PHP fallback (legacy)
                val response = RetrofitClient.apiService.deleteMessage(
                    accessToken = UserSession.accessToken!!,
                    messageId = messageId
                )
                if (response.apiStatus == 200) {
                    _messages.value = _messages.value.filter { it.id != messageId }
                    _error.value = null
                    Log.d("MessagesViewModel", "Повідомлення видалено: $messageId")
                } else {
                    _deletingMessages.update { it - messageId }
                    permanentlyDeletedIds.remove(messageId)
                    _error.value = response.errors?.errorText ?: "Не вдалося видалити повідомлення"
                }
                _isLoading.value = false
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

                // Group/PHP path
                // Перевіряємо, чи вже є реакція від поточного користувача
                val message = _messages.value.find { it.id == messageId }
                val existingReactions = message?.reactions ?: emptyList()
                val hasMyReaction = existingReactions.any {
                    it.userId == UserSession.userId && it.reaction == emoji
                }

                val response = if (hasMyReaction) {
                    // Видаляємо реакцію
                    RetrofitClient.apiService.removeReaction(
                        accessToken = UserSession.accessToken!!,
                        messageId = messageId,
                        reaction = emoji
                    )
                } else {
                    // Додаємо реакцію
                    RetrofitClient.apiService.addReaction(
                        accessToken = UserSession.accessToken!!,
                        messageId = messageId,
                        reaction = emoji
                    )
                }

                if (response.apiStatus == 200) {
                    // Оновлюємо реакції для повідомлення
                    fetchReactionsForMessage(messageId)
                    Log.d("MessagesViewModel", "Реакцію ${if (hasMyReaction) "видалено" else "додано"}")
                } else {
                    _error.value = response.errorMessage ?: "Не вдалося оновити реакцію"
                    Log.e("MessagesViewModel", "Reaction Error: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                _error.value = "Помилка: ${e.localizedMessage}"
                Log.e("MessagesViewModel", "Помилка оновлення реакції", e)
            }
        }
    }

    /**
     * Завантажує реакції для конкретного повідомлення
     */
    private suspend fun fetchReactionsForMessage(messageId: Long) {
        try {
            val response = RetrofitClient.apiService.getReactions(
                accessToken = UserSession.accessToken!!,
                messageId = messageId
            )

            if (response.apiStatus == 200 && response.reactions != null) {
                // Оновлюємо список повідомлень з новими реакціями
                val currentMessages = _messages.value.toMutableList()
                val messageIndex = currentMessages.indexOfFirst { it.id == messageId }

                if (messageIndex != -1) {
                    val updatedMessage = currentMessages[messageIndex].copy(
                        reactions = response.reactions
                    )
                    currentMessages[messageIndex] = updatedMessage
                    _messages.value = currentMessages
                }
            }
        } catch (e: Exception) {
            Log.e("MessagesViewModel", "Помилка завантаження реакцій", e)
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
    fun sendSticker(stickerId: Long) {
        if (UserSession.accessToken == null || (recipientId == 0L && groupId == 0L)) {
            _error.value = "Помилка: не авторизовано"
            return
        }

        _isLoading.value = true

        viewModelScope.launch {
            try {
                val messageHashId = java.util.UUID.randomUUID().toString()

                val response = RetrofitClient.apiService.sendSticker(
                    accessToken = UserSession.accessToken!!,
                    recipientId = recipientId.takeIf { it != 0L },
                    groupId = groupId.takeIf { it != 0L },
                    stickerId = stickerId,
                    messageHashId = messageHashId
                )

                if (response.apiStatus == 200) {
                    // Перезавантажуємо повідомлення
                    if (groupId != 0L) {
                        fetchGroupMessages()
                    } else {
                        fetchMessages()
                    }

                    _error.value = null
                    Log.d("MessagesViewModel", "Стікер надіслано")
                } else {
                    _error.value = response.errors?.errorText ?: response.errorMessage ?: "Не вдалося надіслати стікер"
                    Log.e("MessagesViewModel", "Send Sticker Error: ${response.errors?.errorText ?: response.errorMessage}")
                }

                _isLoading.value = false
            } catch (e: Exception) {
                _error.value = "Помилка: ${e.localizedMessage}"
                _isLoading.value = false
                Log.e("MessagesViewModel", "Помилка надсилання стікера", e)
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
            // Групи — залишаємо на PHP (групи поки не мігровані на Node.js)
            viewModelScope.launch {
                try {
                    RetrofitClient.apiService.sendMessage(
                        accessToken = UserSession.accessToken!!,
                        recipientId = recipientId,
                        text = gifUrl,
                        messageHashId = java.util.UUID.randomUUID().toString(),
                        replyToId = null
                    )
                    fetchGroupMessages()
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
            // Групи — PHP
            viewModelScope.launch {
                try {
                    val locationText = "📍 ${locationData.address}\n${locationData.latLng.latitude},${locationData.latLng.longitude}"
                    RetrofitClient.apiService.sendGroupMessage(
                        accessToken = UserSession.accessToken!!,
                        groupId = groupId,
                        text = locationText,
                        replyToId = null
                    )
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
            // Групи — PHP
            viewModelScope.launch {
                try {
                    RetrofitClient.apiService.sendGroupMessage(
                        accessToken = UserSession.accessToken!!,
                        groupId = groupId,
                        text = "📇 VCARD\n$vCardString",
                        replyToId = null
                    )
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
                    isPremium = false,
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

    override fun onNewMessage(messageJson: JSONObject) {
        try {
            Log.d("MessagesViewModel", "📨 Отримано Socket.IO повідомлення: $messageJson")

            val timestamp = messageJson.getLong("time")
            val encryptedText = messageJson.optString("text", null)
            val mediaUrl = messageJson.optString("media", null)

            // Поддержка AES-GCM (v2) - новые поля
            val iv = messageJson.optString("iv", null)?.takeIf { it.isNotEmpty() }
            val tag = messageJson.optString("tag", null)?.takeIf { it.isNotEmpty() }
            val cipherVersion = if (messageJson.has("cipher_version")) {
                messageJson.getInt("cipher_version")
            } else null

            // Дешифруем текст с поддержкой GCM
            val decryptedText = DecryptionUtility.decryptMessageOrOriginal(
                text = encryptedText,
                timestamp = timestamp,
                iv = iv,
                tag = tag,
                cipherVersion = cipherVersion
            )

            // Дешифруем URL медиа с поддержкой GCM
            val decryptedMediaUrl = DecryptionUtility.decryptMediaUrl(
                mediaUrl = mediaUrl,
                timestamp = timestamp,
                iv = iv,
                tag = tag,
                cipherVersion = cipherVersion
            )

            // Пытаемся извлечь URL медиа из текста, если mediaUrl пуст
            val finalMediaUrl = decryptedMediaUrl
                ?: DecryptionUtility.extractMediaUrlFromText(decryptedText)

            val message = Message(
                id = messageJson.getLong("id"),
                fromId = messageJson.getLong("from_id"),
                toId = messageJson.getLong("to_id"),
                groupId = messageJson.optLong("group_id", 0).takeIf { it != 0L },
                encryptedText = encryptedText,
                timeStamp = timestamp,
                mediaUrl = mediaUrl,
                type = messageJson.optString("type", Constants.MESSAGE_TYPE_TEXT),
                senderName = messageJson.optString("sender_name", null),
                senderAvatar = messageJson.optString("sender_avatar", null),
                // Поля для AES-GCM (v2)
                iv = iv,
                tag = tag,
                cipherVersion = cipherVersion,
                // Дешифрованные данные
                decryptedText = decryptedText,
                decryptedMediaUrl = finalMediaUrl
            )

            // Проверяем, принадлежит ли сообщение текущему диалогу
            val isRelevant = if (groupId != 0L) {
                message.groupId == groupId
            } else {
                (message.fromId == recipientId && message.toId == UserSession.userId) ||
                        (message.fromId == UserSession.userId && message.toId == recipientId)
            }

            if (isRelevant) {
                val currentMessages = _messages.value.toMutableList()
                currentMessages.add(message)
                // Сортируем по времени (старые сверху, новые внизу)
                _messages.value = currentMessages.distinctBy { it.id }.sortedBy { it.timeStamp }
                Log.d("MessagesViewModel", "Додано нове повідомлення від Socket.IO: ${message.decryptedText}")

                // Відразу позначаємо як прочитане — чат відкритий
                if (message.fromId == recipientId) {
                    markSeen()
                }
            }
        } catch (e: Exception) {
            Log.e("MessagesViewModel", "Помилка обробки повідомлення", e)
        }
    }

    override fun onSocketConnected() {
        Log.i("MessagesViewModel", "Socket підключено успішно")
        _error.value = null
    }

    override fun onSocketDisconnected() {
        Log.w("MessagesViewModel", "Socket відключено")
        _error.value = "Втрачено з'єднання з сервером"
    }

    override fun onSocketError(error: String) {
        Log.e("MessagesViewModel", "Помилка Socket: $error")
        _error.value = error
    }

    override fun onTypingStatus(userId: Long?, isTyping: Boolean) {
        if (userId == recipientId) {
            _isTyping.value = isTyping
            // ВАЖНО: Если пользователь печатает, значит он онлайн!
            if (isTyping) {
                _recipientOnlineStatus.value = true
            }
            Log.d("MessagesViewModel", "Користувач $userId ${if (isTyping) "набирає" else "зупинив набір"}")
        }
    }

    override fun onUserOnline(userId: Long) {
        if (userId == recipientId) {
            _recipientOnlineStatus.value = true
            Log.d("MessagesViewModel", "✅ Користувач $userId з'явився онлайн")
        }
    }

    override fun onUserOffline(userId: Long) {
        if (userId == recipientId) {
            // ВАЖНО: Не сбрасываем статус, если пользователь печатает
            if (!_isTyping.value) {
                _recipientOnlineStatus.value = false
                Log.d("MessagesViewModel", "❌ Користувач $userId з'явився офлайн")
            } else {
                Log.d("MessagesViewModel", "⚠️ Ігноруємо offline для $userId (друкує)")
            }
        }
    }

    override fun onRecordingStatus(userId: Long?, isRecording: Boolean) {
        if (userId == recipientId) {
            _isRecording.value = isRecording
            if (isRecording) _recipientOnlineStatus.value = true
            Log.d(TAG, "Користувач $userId recording: $isRecording")
        }
    }

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

    override fun onMessagePinned(messageId: Long, isPinned: Boolean, chatId: Long) {
        if (recipientId != 0L && (chatId == recipientId || chatId == UserSession.userId)) {
            if (isPinned) {
                val pinnedMsg = _messages.value.find { it.id == messageId }
                _pinnedPrivateMessage.value = pinnedMsg
            } else {
                if (_pinnedPrivateMessage.value?.id == messageId) {
                    _pinnedPrivateMessage.value = null
                }
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
        if (recipientId == 0L || groupId != 0L) return
        if (socketManager?.canSendTypingIndicators() == false) return

        viewModelScope.launch {
            try {
                nodeApi.sendTyping(recipientId, if (isTyping) "true" else "false")
                Log.d(TAG, "Typing status sent via REST: $isTyping → recipient $recipientId")
            } catch (e: Exception) {
                Log.w(TAG, "sendTypingStatus failed: ${e.message}")
            }
        }
    }

    /**
     * Надсилає статус "записує голосове" через Socket.IO
     * Сервер RecordingController очікує session hash у полі user_id
     */
    fun sendRecordingStatus() {
        if (recipientId == 0L || groupId != 0L) return
        socketManager?.sendRecordingStatus(recipientId)
        Log.d(TAG, "Recording status emitted for recipient $recipientId")
    }

    fun clearError() {
        _error.value = null
    }

    /**
     * Полностью дешифрует сообщение: текст и URL медиа.
     * Также пытается извлечь URL медиа из текста сообщения.
     * Поддерживает AES-GCM (v2) и обратную совместимость с AES-ECB (v1).
     */
    private fun decryptMessageFully(msg: Message): Message {
        // Дешифруем текст с поддержкой GCM
        val decryptedText = DecryptionUtility.decryptMessageOrOriginal(
            text = msg.encryptedText,
            timestamp = msg.timeStamp,
            iv = msg.iv,
            tag = msg.tag,
            cipherVersion = msg.cipherVersion
        )

        // Дешифруем URL медиа с поддержкой GCM
        val decryptedMediaUrl = DecryptionUtility.decryptMediaUrl(
            mediaUrl = msg.mediaUrl,
            timestamp = msg.timeStamp,
            iv = msg.iv,
            tag = msg.tag,
            cipherVersion = msg.cipherVersion
        )

        // Пытаемся извлечь URL медиа из текста, если mediaUrl пуст
        val finalMediaUrl = decryptedMediaUrl
            ?: DecryptionUtility.extractMediaUrlFromText(decryptedText)

        return msg.copy(
            decryptedText = decryptedText,
            decryptedMediaUrl = finalMediaUrl
        )
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
                val response = RetrofitClient.apiService.getChats(
                    accessToken = UserSession.accessToken!!,
                    dataType = "users", // Тільки користувачі
                    limit = 100
                )

                if (response.apiStatus == 200) {
                    // Конвертуємо чати в ForwardRecipient
                    val contacts = response.chats?.map { chat ->
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
                val response = RetrofitClient.apiService.getGroups(
                    accessToken = UserSession.accessToken!!,
                    type = "get_list",
                    limit = 100
                )

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
                                RetrofitClient.apiService.sendGroupMessage(
                                    accessToken = UserSession.accessToken!!,
                                    type = "send_message",
                                    groupId = gId,
                                    text = message.decryptedText ?: ""
                                )
                                Log.d("MessagesViewModel", "Forwarded msg $messageId to group $gId via PHP")
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
                val response = RetrofitClient.apiService.pinGroupMessage(
                    accessToken = UserSession.accessToken!!,
                    groupId = groupId,
                    messageId = messageId
                )

                if (response.apiStatus == 200) {
                    // Оновлюємо дані групи
                    fetchGroupDetails(groupId)
                    onSuccess()
                    Log.d(TAG, "📌 Message $messageId pinned in group $groupId")
                } else {
                    val errorMsg = response.message ?: "Не вдалося закріпити повідомлення"
                    onError(errorMsg)
                    Log.e(TAG, "❌ Failed to pin message: ${response.message}")
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
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (UserSession.accessToken == null || groupId == 0L) {
            onError("Не авторизовано або це не група")
            return
        }

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.unpinGroupMessage(
                    accessToken = UserSession.accessToken!!,
                    groupId = groupId
                )

                if (response.apiStatus == 200) {
                    // Оновлюємо дані групи
                    fetchGroupDetails(groupId)
                    onSuccess()
                    Log.d(TAG, "📌 Message unpinned in group $groupId")
                } else {
                    val errorMsg = response.message ?: "Не вдалося відкріпити повідомлення"
                    onError(errorMsg)
                    Log.e(TAG, "❌ Failed to unpin message: ${response.message}")
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
                val response = RetrofitClient.apiService.muteGroup(
                    accessToken = UserSession.accessToken!!,
                    groupId = groupId
                )

                if (response.apiStatus == 200) {
                    // Оновлюємо дані групи
                    fetchGroupDetails(groupId)
                    onSuccess()
                    Log.d(TAG, "🔕 Group $groupId muted")
                } else {
                    val errorMsg = response.message ?: "Не вдалося вимкнути сповіщення"
                    onError(errorMsg)
                    Log.e(TAG, "❌ Failed to mute group: ${response.message}")
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
                val response = RetrofitClient.apiService.unmuteGroup(
                    accessToken = UserSession.accessToken!!,
                    groupId = groupId
                )

                if (response.apiStatus == 200) {
                    // Оновлюємо дані групи
                    fetchGroupDetails(groupId)
                    onSuccess()
                    Log.d(TAG, "🔔 Group $groupId unmuted")
                } else {
                    val errorMsg = response.message ?: "Не вдалося увімкнути сповіщення"
                    onError(errorMsg)
                    Log.e(TAG, "❌ Failed to unmute group: ${response.message}")
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
                val response = RetrofitClient.apiService.searchGroupMessages(
                    accessToken = UserSession.accessToken!!,
                    groupId = groupId,
                    query = query,
                    limit = 100
                )

                if (response.apiStatus == 200) {
                    val messages = response.messages ?: emptyList()
                    _searchResults.value = messages
                    _searchTotalCount.value = response.totalCount
                    _currentSearchIndex.value = if (messages.isNotEmpty()) 0 else -1
                    Log.d(TAG, "🔍 Search completed: found ${response.totalCount} results for '$query'")
                } else {
                    Log.e(TAG, "❌ Search failed: ${response.message}")
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
                    val messages = response.messages?.map { decryptMessageFully(it) } ?: emptyList()
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
                    if (pin) {
                        val pinnedMsg = _messages.value.find { it.id == messageId }
                        _pinnedPrivateMessage.value = pinnedMsg
                    } else {
                        if (_pinnedPrivateMessage.value?.id == messageId) {
                            _pinnedPrivateMessage.value = null
                        }
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
                // Private chat → PHP
                val response = RetrofitClient.apiService.clearChatHistory(
                    accessToken = UserSession.accessToken!!,
                    userId = recipientId
                )
                if (response.apiStatus == 200) {
                    _messages.value = emptyList()
                    onSuccess()
                    Log.d(TAG, "🗑️ Chat history cleared")
                } else {
                    onError(response.message ?: "Не вдалося очистити історію")
                }
            } catch (e: Exception) {
                onError("Помилка: ${e.localizedMessage}")
                Log.e(TAG, "❌ Error clearing chat history", e)
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
                val response = RetrofitClient.apiService.blockUser(
                    accessToken = UserSession.accessToken!!,
                    userId = recipientId
                )

                if (response.apiStatus == 200) {
                    onSuccess()
                    Log.d(TAG, "🚫 User $recipientId blocked")
                } else {
                    val errorMsg = response.message ?: "Не вдалося заблокувати користувача"
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
                        recipientId = recipientId,
                        limit = 15,
                        beforeMessageId = 0
                    )
                    if (response.apiStatus == 200 && response.messages != null) {
                        val newMessages = response.messages.map { msg -> decryptMessageFully(msg) }
                        val currentIds = _messages.value.map { it.id }.toSet()
                        val trulyNew = newMessages.filter { it.id !in currentIds }
                        if (trulyNew.isNotEmpty()) {
                            setMessagesSafe((_messages.value + trulyNew).distinctBy { it.id }.sortedBy { it.timeStamp })
                            Log.d(TAG, "🔄 Polling (Node.js): +${trulyNew.size} нових")
                        }
                    }
                    return@launch
                }

                // ── Group chat → PHP ──────────────────────────────────────────
                if (groupId == 0L) return@launch
                val response = RetrofitClient.apiService.getGroupMessages(
                    accessToken = UserSession.accessToken!!,
                    groupId = groupId,
                    topicId = topicId,
                    limit = 15,
                    beforeMessageId = 0
                )
                if (response.apiStatus == 200 && response.messages != null) {
                    val newMessages = response.messages!!.map { msg -> decryptMessageFully(msg) }
                    val currentIds = _messages.value.map { it.id }.toSet()
                    val trulyNew = newMessages.filter { it.id !in currentIds }
                    if (trulyNew.isNotEmpty()) {
                        _messages.value = (_messages.value + trulyNew).distinctBy { it.id }.sortedBy { it.timeStamp }
                        Log.d(TAG, "🔄 Polling: додано ${trulyNew.size} нових повідомлень")
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

        // Зупиняємо Socket.IO
        socketManager?.disconnect()

        // Зупиняємо моніторинг якості
        qualityMonitorJob?.cancel()

        // Зупиняємо автозбереження чернетки
        draftAutoSaveJob?.cancel()

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
}
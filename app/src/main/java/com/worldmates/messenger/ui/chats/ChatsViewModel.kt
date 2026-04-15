package com.worldmates.messenger.ui.chats

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.data.model.Chat
import com.worldmates.messenger.network.NodeRetrofitClient
import com.worldmates.messenger.network.SocketManager
import com.worldmates.messenger.R
import com.worldmates.messenger.utils.DecryptionUtility
import com.worldmates.messenger.utils.signal.SignalKeyStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class ChatsViewModel(private val context: Context) : ViewModel(), SocketManager.SocketListener {

    private val _chatList = MutableStateFlow<List<Chat>>(emptyList())
    val chatList: StateFlow<List<Chat>> = _chatList

    private val _businessChatList = MutableStateFlow<List<Chat>>(emptyList())
    val businessChatList: StateFlow<List<Chat>> = _businessChatList

    private val _isLoadingBusiness = MutableStateFlow(false)
    val isLoadingBusiness: StateFlow<Boolean> = _isLoadingBusiness

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _needsRelogin = MutableStateFlow(false)
    val needsRelogin: StateFlow<Boolean> = _needsRelogin

    // ─── Пагінація ────────────────────────────────────────────────────────────
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    private val _hasMoreChats = MutableStateFlow(true)
    val hasMoreChats: StateFlow<Boolean> = _hasMoreChats

    private val PAGE_SIZE   = 50
    private var chatsOffset = 0

    // ─── Приховані чати (server-synced) ──────────────────────────────────────
    private val _hiddenChats = MutableStateFlow<List<Chat>>(emptyList())
    val hiddenChats: StateFlow<List<Chat>> = _hiddenChats

    private val _hiddenChatsCount = MutableStateFlow(0)
    val hiddenChatsCount: StateFlow<Int> = _hiddenChatsCount

    private var socketManager: SocketManager? = null
    private var authErrorCount = 0

    init {
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            fetchChats()
            fetchHiddenChatsCount()
        }
        setupSocket()
    }

    /**
     * Завантажує перші [PAGE_SIZE] чатів з сервера (скидає пагінацію).
     */
    fun fetchChats() {
        if (UserSession.accessToken == null) {
            _error.value = context.getString(R.string.geo_not_authenticated)
            Log.e("ChatsViewModel", "Access token is null!")
            return
        }

        Log.d("ChatsViewModel", "Початок завантаження чатів...")

        chatsOffset = 0
        _hasMoreChats.value = true
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                val response = NodeRetrofitClient.api.getChats(
                    limit  = PAGE_SIZE,
                    offset = 0
                )

                Log.d("ChatsViewModel", "API Response Status: ${response.apiStatus}")
                Log.d("ChatsViewModel", "Chats count: ${response.data?.size ?: 0}")

                if (response.apiStatus == 200) {
                    authErrorCount = 0
                    if (response.data != null && response.data.isNotEmpty()) {
                        Log.d("ChatsViewModel", "Отримано ${response.data.size} особистих чатів (Node.js)")

                        val decryptedChats = response.data
                            .filter { !it.isGroup }
                            .map { chat ->
                            Log.d("ChatsViewModel", "✅ Особистий чат: ${chat.username}, last_msg: ${chat.lastMessage?.encryptedText}")

                            val lastMessage = chat.lastMessage?.let { msg ->
                                val encryptedText = msg.encryptedText ?: ""

                                val decryptedText = when {
                                    msg.cipherVersion == 3 -> {
                                        try {
                                            SignalKeyStore(context).getCachedDecryptedMessage(msg.id)
                                        } catch (e: Exception) {
                                            Log.w("ChatsViewModel", "SignalKeyStore unavailable", e)
                                            null
                                        } ?: context.getString(R.string.last_message_e2ee)
                                    }
                                    encryptedText.isNotEmpty() -> {
                                        val result = DecryptionUtility.decryptMessageOrOriginal(
                                            text = encryptedText,
                                            timestamp = msg.timeStamp,
                                            iv = msg.iv,
                                            tag = msg.tag,
                                            cipherVersion = msg.cipherVersion
                                        )
                                        if (result == encryptedText) {
                                            val looksEncrypted = encryptedText.length >= 12 &&
                                                encryptedText.length % 4 == 0 &&
                                                encryptedText.all { c ->
                                                    c.code < 128 && (c.isLetterOrDigit() || c == '+' || c == '/' || c == '=')
                                                }
                                            if (looksEncrypted)
                                                context.getString(R.string.last_message_encrypted)
                                            else
                                                result
                                        } else result
                                    }
                                    else -> ""
                                }

                                val displayText = convertMediaUrlToLabel(decryptedText)
                                msg.copy(
                                    encryptedText = msg.encryptedText,
                                    decryptedText = displayText
                                )
                            }
                            chat.copy(lastMessage = lastMessage)
                        }

                        _chatList.value = decryptedChats
                        chatsOffset = decryptedChats.size
                        _hasMoreChats.value = response.data.size >= PAGE_SIZE
                        _error.value = null
                        Log.d("ChatsViewModel", "✅ Завантажено ${decryptedChats.size} чатів успішно (Node.js)")
                    } else {
                        Log.w("ChatsViewModel", "⚠️ Node.js повернуло 200, але чатів немає")
                        _chatList.value = emptyList()
                        _hasMoreChats.value = false
                        _error.value = null
                    }
                } else if (response.apiStatus == 401 || response.apiStatus == 403) {
                    authErrorCount++
                    Log.e("ChatsViewModel", "❌ Auth error #$authErrorCount (status=${response.apiStatus})")
                    if (authErrorCount >= 3) {
                        Log.e("ChatsViewModel", "❌ 3 consecutive auth errors — forcing re-login")
                        UserSession.clearSession()
                        _needsRelogin.value = true
                        _error.value = context.getString(R.string.error_session_expired)
                    } else {
                        _error.value = response.errorMessage ?: context.getString(R.string.error_auth_retry)
                    }
                } else {
                    authErrorCount = 0
                    val errorMsg = response.errorMessage ?: context.getString(R.string.unknown_error)
                    _error.value = errorMsg
                    Log.e("ChatsViewModel", "❌ Помилка Node.js API: ${response.apiStatus} - $errorMsg")
                }

                _isLoading.value = false
            } catch (e: com.google.gson.JsonSyntaxException) {
                _error.value = context.getString(R.string.error_parse_response)
                _isLoading.value = false
                Log.e("ChatsViewModel", "❌ JSON parse error", e)
            } catch (e: java.net.ConnectException) {
                _error.value = context.getString(R.string.error_connect_server)
                _isLoading.value = false
                Log.e("ChatsViewModel", "❌ Connection error", e)
            } catch (e: Exception) {
                _error.value = context.getString(R.string.error_with_message, e.localizedMessage ?: "")
                _isLoading.value = false
                Log.e("ChatsViewModel", "❌ Помилка завантаження чатів", e)
            }
        }
    }

    /**
     * Дозавантажує наступну сторінку чатів (infinite scroll).
     */
    fun loadMoreChats() {
        if (_isLoadingMore.value || !_hasMoreChats.value) return
        if (UserSession.accessToken == null) return

        _isLoadingMore.value = true
        Log.d("ChatsViewModel", "📄 loadMoreChats: offset=$chatsOffset")

        viewModelScope.launch {
            try {
                val response = NodeRetrofitClient.api.getChats(
                    limit  = PAGE_SIZE,
                    offset = chatsOffset
                )

                if (response.apiStatus == 200 && !response.data.isNullOrEmpty()) {
                    val newDecrypted = response.data
                        .filter { !it.isGroup }
                        .map { chat ->
                            val lastMessage = chat.lastMessage?.let { msg ->
                                val displayText = convertMediaUrlToLabel(
                                    DecryptionUtility.decryptMessageOrOriginal(
                                        text          = msg.encryptedText ?: "",
                                        timestamp     = msg.timeStamp,
                                        iv            = msg.iv,
                                        tag           = msg.tag,
                                        cipherVersion = msg.cipherVersion
                                    )
                                )
                                msg.copy(decryptedText = displayText)
                            }
                            chat.copy(lastMessage = lastMessage)
                        }

                    val existingIds = _chatList.value.map { it.id }.toSet()
                    val unique      = newDecrypted.filter { it.id !in existingIds }
                    _chatList.value = _chatList.value + unique
                    chatsOffset    += response.data.size
                    _hasMoreChats.value = response.data.size >= PAGE_SIZE
                    Log.d("ChatsViewModel", "📄 loadMoreChats: +${unique.size} чатів, offset=$chatsOffset (Node.js)")
                } else {
                    _hasMoreChats.value = false
                }
            } catch (e: Exception) {
                Log.e("ChatsViewModel", "❌ loadMoreChats error", e)
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    // ─── Архів (server + local sync) ─────────────────────────────────────────

    /**
     * Архівує чат: відправляє запит на сервер і оновлює локальний стан.
     */
    fun archiveChat(userId: Long) {
        viewModelScope.launch {
            try {
                val response = NodeRetrofitClient.api.archiveChat(chatId = userId, archive = "yes")
                if (response.apiStatus == 200) {
                    ChatOrganizationManager.archiveChat(userId)
                    // Видаляємо зі списку (сервер більше не поверне його без show_archived=true)
                    _chatList.value = _chatList.value.filter { it.userId != userId }
                    Log.d("ChatsViewModel", "✅ Чат $userId архівовано")
                }
            } catch (e: Exception) {
                Log.e("ChatsViewModel", "❌ archiveChat error", e)
                // Fallback: оновлюємо тільки локально
                ChatOrganizationManager.archiveChat(userId)
                _chatList.value = _chatList.value.filter { it.userId != userId }
            }
        }
    }

    /**
     * Розархівує чат: відправляє запит на сервер і оновлює локальний стан.
     */
    fun unarchiveChat(userId: Long) {
        viewModelScope.launch {
            try {
                val response = NodeRetrofitClient.api.archiveChat(chatId = userId, archive = "no")
                if (response.apiStatus == 200) {
                    ChatOrganizationManager.unarchiveChat(userId)
                    fetchChats()
                    Log.d("ChatsViewModel", "✅ Чат $userId розархівовано")
                }
            } catch (e: Exception) {
                Log.e("ChatsViewModel", "❌ unarchiveChat error", e)
                ChatOrganizationManager.unarchiveChat(userId)
                fetchChats()
            }
        }
    }

    // ─── Приховані чати (server-persisted) ───────────────────────────────────

    /**
     * Завантажує приховані чати з сервера (show_hidden=true).
     */
    fun fetchHiddenChats() {
        if (UserSession.accessToken == null) return
        viewModelScope.launch {
            try {
                val response = NodeRetrofitClient.api.getChats(
                    limit        = 100,
                    offset       = 0,
                    showHidden   = "true"
                )
                if (response.apiStatus == 200) {
                    val decrypted = (response.data ?: emptyList())
                        .filter { !it.isGroup }
                        .map { chat ->
                            val lastMessage = chat.lastMessage?.let { msg ->
                                val displayText = convertMediaUrlToLabel(
                                    DecryptionUtility.decryptMessageOrOriginal(
                                        text          = msg.encryptedText ?: "",
                                        timestamp     = msg.timeStamp,
                                        iv            = msg.iv,
                                        tag           = msg.tag,
                                        cipherVersion = msg.cipherVersion
                                    )
                                )
                                msg.copy(decryptedText = displayText)
                            }
                            chat.copy(lastMessage = lastMessage)
                        }
                    _hiddenChats.value = decrypted
                    _hiddenChatsCount.value = decrypted.size
                    Log.d("ChatsViewModel", "✅ Завантажено ${decrypted.size} прихованих чатів")
                }
            } catch (e: Exception) {
                Log.e("ChatsViewModel", "❌ fetchHiddenChats error", e)
            }
        }
    }

    /**
     * Отримує тільки кількість прихованих чатів (для бейджа вкладки).
     */
    fun fetchHiddenChatsCount() {
        if (UserSession.accessToken == null) return
        viewModelScope.launch {
            try {
                val response = NodeRetrofitClient.api.getHiddenChatsCount()
                if (response.apiStatus == 200) {
                    _hiddenChatsCount.value = response.count
                }
            } catch (e: Exception) {
                Log.e("ChatsViewModel", "❌ fetchHiddenChatsCount error", e)
            }
        }
    }

    /**
     * Ховає чат (server-persisted): переміщує в "приховані".
     */
    fun hideChat(userId: Long) {
        viewModelScope.launch {
            try {
                val response = NodeRetrofitClient.api.hideChat(chatId = userId, hidden = "yes")
                if (response.apiStatus == 200) {
                    // Видаляємо з основного списку
                    _chatList.value = _chatList.value.filter { it.userId != userId }
                    _hiddenChatsCount.value = _hiddenChatsCount.value + 1
                    Log.d("ChatsViewModel", "✅ Чат $userId приховано")
                }
            } catch (e: Exception) {
                Log.e("ChatsViewModel", "❌ hideChat error", e)
                // Fallback: приховуємо локально
                _chatList.value = _chatList.value.filter { it.userId != userId }
            }
        }
    }

    /**
     * Показує раніше прихований чат.
     */
    fun unhideChat(userId: Long) {
        viewModelScope.launch {
            try {
                val response = NodeRetrofitClient.api.hideChat(chatId = userId, hidden = "no")
                if (response.apiStatus == 200) {
                    _hiddenChats.value = _hiddenChats.value.filter { it.userId != userId }
                    _hiddenChatsCount.value = maxOf(0, _hiddenChatsCount.value - 1)
                    fetchChats()
                    Log.d("ChatsViewModel", "✅ Чат $userId показано знову")
                }
            } catch (e: Exception) {
                Log.e("ChatsViewModel", "❌ unhideChat error", e)
                _hiddenChats.value = _hiddenChats.value.filter { it.userId != userId }
                fetchChats()
            }
        }
    }

    // ─── Видалення чату (3 варіанти) ─────────────────────────────────────────

    /**
     * Видалити чат тільки для себе (переписка залишається у співрозмовника).
     */
    fun deleteChatForMe(userId: Long, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val response = NodeRetrofitClient.api.deleteConversation(
                    userId     = userId,
                    deleteType = "me"
                )
                if (response.apiStatus == 200) {
                    _chatList.value = _chatList.value.filter { it.userId != userId }
                    onSuccess()
                    Log.d("ChatsViewModel", "✅ Чат $userId видалено для мене")
                }
            } catch (e: Exception) {
                Log.e("ChatsViewModel", "❌ deleteChatForMe error", e)
            }
        }
    }

    /**
     * Видалити чат і очистити переписку для обох сторін.
     */
    fun deleteChatForEveryone(userId: Long, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                // Очищаємо історію для обох
                NodeRetrofitClient.api.clearHistory(
                    recipientId = userId,
                    clearType   = "everyone"
                )
                // Видаляємо запис розмови для себе
                val response = NodeRetrofitClient.api.deleteConversation(
                    userId     = userId,
                    deleteType = "me"
                )
                if (response.apiStatus == 200) {
                    _chatList.value = _chatList.value.filter { it.userId != userId }
                    onSuccess()
                    Log.d("ChatsViewModel", "✅ Чат $userId видалено і очищено для обох")
                }
            } catch (e: Exception) {
                Log.e("ChatsViewModel", "❌ deleteChatForEveryone error", e)
            }
        }
    }

    /**
     * Видалити чат і заблокувати користувача.
     */
    fun deleteChatAndBlock(userId: Long, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                // Видаляємо чат
                NodeRetrofitClient.api.deleteConversation(
                    userId     = userId,
                    deleteType = "me"
                )
                // Блокуємо користувача
                NodeRetrofitClient.profileApi.blockUser(userId)

                _chatList.value = _chatList.value.filter { it.userId != userId }
                onSuccess()
                Log.d("ChatsViewModel", "✅ Чат $userId видалено і користувача заблоковано")
            } catch (e: Exception) {
                Log.e("ChatsViewModel", "❌ deleteChatAndBlock error", e)
            }
        }
    }

    // ─── Socket ───────────────────────────────────────────────────────────────

    private fun setupSocket() {
        try {
            socketManager = SocketManager(this, context)
            socketManager?.connect()
            Log.d("ChatsViewModel", "Socket.IO налаштований")
        } catch (e: Exception) {
            Log.e("ChatsViewModel", "Помилка налаштування Socket.IO", e)
        }
    }

    fun fetchBusinessChats() {
        if (UserSession.accessToken == null) return
        _isLoadingBusiness.value = true
        viewModelScope.launch {
            try {
                val response = NodeRetrofitClient.api.getBusinessInbox(limit = 50, offset = 0)
                if (response.apiStatus == 200) {
                    _businessChatList.value = response.data ?: emptyList()
                    Log.d("ChatsViewModel", "Бізнес inbox завантажено: ${_businessChatList.value.size}")
                } else {
                    Log.e("ChatsViewModel", "fetchBusinessChats error: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                Log.e("ChatsViewModel", "fetchBusinessChats exception", e)
            } finally {
                _isLoadingBusiness.value = false
            }
        }
    }

    override fun onNewMessage(messageJson: JSONObject) {
        Log.d("ChatsViewModel", "Нове повідомлення отримано")
        val isBusinessMsg = messageJson.optInt("is_business_chat", 0) == 1
        if (isBusinessMsg) {
            fetchBusinessChats()
        } else {
            fetchChats()
        }
    }

    override fun onSocketConnected() {
        Log.i("ChatsViewModel", "Socket підключено успішно!")
        _error.value = null
    }

    override fun onSocketDisconnected() {
        Log.w("ChatsViewModel", "Socket відключено")
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            if (socketManager != null) {
                try {
                    socketManager?.connect()
                    Log.d("ChatsViewModel", "Спроба переподключення Socket...")
                } catch (e: Exception) {
                    Log.e("ChatsViewModel", "Помилка переподключення Socket", e)
                }
            }
        }
    }

    override fun onSocketError(error: String) {
        Log.e("ChatsViewModel", "Помилка Socket: $error")
        if (!error.contains("xhr poll error", ignoreCase = true) &&
            !error.contains("timeout", ignoreCase = true)) {
            _error.value = error
        }
    }

    fun clearError() {
        _error.value = null
    }

    private fun convertMediaUrlToLabel(text: String): String {
        if (!text.startsWith("http://") && !text.startsWith("https://")) {
            return text
        }

        val lowerText = text.lowercase()

        return when {
            lowerText.contains("/upload/photos/") ||
            lowerText.matches(Regex(".*\\.(jpg|jpeg|png|gif|webp|bmp)$")) -> "📷 Зображення"

            lowerText.contains("/upload/videos/") ||
            lowerText.matches(Regex(".*\\.(mp4|webm|mov|avi|mkv)$")) -> "🎬 Відео"

            lowerText.contains("/upload/sounds/") ||
            lowerText.matches(Regex(".*\\.(mp3|wav|ogg|m4a|aac)$")) -> "🎵 Аудіо"

            lowerText.matches(Regex(".*\\.gif$")) -> "🎞️ GIF"

            lowerText.contains("/upload/files/") ||
            lowerText.matches(Regex(".*\\.(pdf|doc|docx|xls|xlsx|zip|rar)$")) -> "📎 Файл"

            else -> text
        }
    }

    override fun onCleared() {
        super.onCleared()
        socketManager?.disconnect()
        Log.d("ChatsViewModel", "ViewModel очищена")
    }
}

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
    private var chatsOffset = 0   // зміщення для наступного запиту

    private val hiddenChatIds = mutableSetOf<Long>()
    private var socketManager: SocketManager? = null
    private var authErrorCount = 0

    init {
        // Добавляем задержку перед первым запросом
        // чтобы токен успел активироваться на сервере
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000) // 2 секунды задержка
            fetchChats()
        }
        setupSocket()
    }

    /**
     * Завантажує перші [PAGE_SIZE] чатів з сервера (скидає пагінацію).
     */
    fun fetchChats() {
        if (UserSession.accessToken == null) {
            _error.value = "Користувач не авторизований"
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
                    authErrorCount = 0  // Reset on success
                    if (response.data != null && response.data.isNotEmpty()) {
                        Log.d("ChatsViewModel", "Отримано ${response.data.size} особистих чатів (Node.js)")

                        // Дешифруємо останнє повідомлення у кожному чаті
                        val decryptedChats = response.data
                            .filter { !it.isGroup && !hiddenChatIds.contains(it.userId) }
                            .map { chat ->
                            Log.d("ChatsViewModel", "✅ Особистий чат: ${chat.username}, last_msg: ${chat.lastMessage?.encryptedText}")

                            val lastMessage = chat.lastMessage?.let { msg ->
                                // Перевіряємо чи є текст повідомлення
                                val encryptedText = msg.encryptedText ?: ""

                                // cipher_version=3 is Signal Double Ratchet — keys are one-time
                                // use, so we never attempt live decryption here.  Read from the
                                // persistent plaintext cache written by MessagesViewModel instead.
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
                                        // Only show placeholder when the text actually looks like
                                        // ciphertext (Base64-structured, min length).  Plain text
                                        // messages also satisfy result == encryptedText (unchanged),
                                        // so we must distinguish them from real ciphertext.
                                        if (result == encryptedText) {
                                            // Real Base64 uses only 7-bit ASCII chars.
                                            // Unicode-styled text (FULLWIDTH, SMALL_CAPS, etc.)
                                            // contains non-ASCII letters that also pass
                                            // isLetterOrDigit() — add c.code < 128 guard.
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
                                    else -> "" // Порожнє повідомлення (можливо медіа без тексту)
                                }

                                Log.d("ChatsViewModel", "🔐 Дешифрування для ${chat.username}:")
                                Log.d("ChatsViewModel", "   Зашифровано: $encryptedText")
                                Log.d("ChatsViewModel", "   Timestamp: ${msg.timeStamp}")
                                Log.d("ChatsViewModel", "   Cipher version: ${msg.cipherVersion ?: "ECB (v1)"}")
                                Log.d("ChatsViewModel", "   Has IV/TAG: ${msg.iv != null}/${msg.tag != null}")
                                Log.d("ChatsViewModel", "   Дешифровано: $decryptedText")

                                // Конвертуємо URL медіа в зрозумілі мітки
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
                        _error.value = "Сесія застаріла. Будь ласка, увійдіть знову"
                    } else {
                        _error.value = response.errorMessage ?: "Помилка авторизації. Повторна спроба..."
                    }
                } else {
                    authErrorCount = 0
                    val errorMsg = response.errorMessage ?: "Невідома помилка (${response.apiStatus})"
                    _error.value = errorMsg
                    Log.e("ChatsViewModel", "❌ Помилка Node.js API: ${response.apiStatus} - $errorMsg")
                }

                _isLoading.value = false
            } catch (e: com.google.gson.JsonSyntaxException) {
                val errorMsg = "Помилка парсингу відповіді від сервера"
                _error.value = errorMsg
                _isLoading.value = false
                Log.e("ChatsViewModel", "❌ $errorMsg", e)
            } catch (e: java.net.ConnectException) {
                val errorMsg = "Не вдалося з'єднатися з сервером"
                _error.value = errorMsg
                _isLoading.value = false
                Log.e("ChatsViewModel", "❌ $errorMsg", e)
            } catch (e: Exception) {
                val errorMsg = "Помилка: ${e.localizedMessage}"
                _error.value = errorMsg
                _isLoading.value = false
                Log.e("ChatsViewModel", "❌ Помилка завантаження чатів", e)
            }
        }
    }

    /**
     * Дозавантажує наступну сторінку чатів (infinite scroll).
     * Безпечно викликати кілька разів — повторний запит ігнорується.
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
                        .filter { !it.isGroup && !hiddenChatIds.contains(it.userId) }
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
                    Log.d("ChatsViewModel", "📄 loadMoreChats: більше чатів немає")
                }
            } catch (e: Exception) {
                Log.e("ChatsViewModel", "❌ loadMoreChats error", e)
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    /**
     * Налаштовує Socket.IO для отримання чатів у реальному часі
     */
    private fun setupSocket() {
        try {
            socketManager = SocketManager(this, context)
            socketManager?.connect()
            Log.d("ChatsViewModel", "Socket.IO налаштований")
        } catch (e: Exception) {
            Log.e("ChatsViewModel", "Помилка налаштування Socket.IO", e)
        }
    }

    /**
     * Завантажує бізнес-чати (вхідні від клієнтів до власника бізнес-профілю).
     */
    fun fetchBusinessChats() {
        if (UserSession.accessToken == null) return
        _isLoadingBusiness.value = true
        viewModelScope.launch {
            try {
                // getBusinessInbox() = owner's view: returns clients who messaged this business
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

    /**
     * Callback для нових повідомлень
     */
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
        // Не показуємо помилку користувачу, оскільки Socket.IO автоматично спробує переподключитися
        // _error.value = "Втрачено з'єднання"

        // Спробуємо переподключитися через 2 секунди, якщо автоматичне переподключення не спрацює
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
        // Не показуємо помилку Socket користувачу, якщо це тимчасова помилка з'єднання
        if (!error.contains("xhr poll error", ignoreCase = true) &&
            !error.contains("timeout", ignoreCase = true)) {
            _error.value = error
        }
    }

    /**
     * Видаляє помилку при закритті
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Конвертує URL медіа в зрозумілі мітки для відображення в списку чатів
     */
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

    /**
     * Приховує чат локально (не видаляє на сервері)
     * @param userId ID користувача, чий чат треба приховати
     */
    fun hideChat(userId: Long) {
        hiddenChatIds.add(userId)
        // Оновлюємо список чатів, виключаючи прихований
        _chatList.value = _chatList.value.filter { it.userId != userId }
        Log.d("ChatsViewModel", "Чат з користувачем $userId приховано")
    }

    /**
     * Показує раніше прихований чат
     * @param userId ID користувача, чий чат треба показати
     */
    fun unhideChat(userId: Long) {
        hiddenChatIds.remove(userId)
        // Перезавантажуємо чати для відображення прихованого
        fetchChats()
        Log.d("ChatsViewModel", "Чат з користувачем $userId показано знову")
    }

    override fun onCleared() {
        super.onCleared()
        socketManager?.disconnect()
        Log.d("ChatsViewModel", "ViewModel очищена")
    }
}
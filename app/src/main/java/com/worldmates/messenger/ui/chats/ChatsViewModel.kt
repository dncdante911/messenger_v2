package com.worldmates.messenger.ui.chats

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.data.model.Chat
import com.worldmates.messenger.network.RetrofitClient
import com.worldmates.messenger.network.SocketManager
import com.worldmates.messenger.utils.DecryptionUtility
import com.worldmates.messenger.utils.signal.SignalKeyStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class ChatsViewModel(private val context: Context) : ViewModel(), SocketManager.SocketListener {

    private val _chatList = MutableStateFlow<List<Chat>>(emptyList())
    val chatList: StateFlow<List<Chat>> = _chatList

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _needsRelogin = MutableStateFlow(false)
    val needsRelogin: StateFlow<Boolean> = _needsRelogin

    private val hiddenChatIds = mutableSetOf<Long>()
    private var socketManager: SocketManager? = null
    private var authErrorCount = 0 // Счетчик ошибок авторизации

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
     * Завантажує список чатів з сервера
     */
    fun fetchChats() {
        if (UserSession.accessToken == null) {
            _error.value = "Користувач не авторизований"
            Log.e("ChatsViewModel", "Access token is null!")
            return
        }

        Log.d("ChatsViewModel", "Початок завантаження чатів...")
        Log.d("ChatsViewModel", "Access token: ${UserSession.accessToken}")
        Log.d("ChatsViewModel", "User ID: ${UserSession.userId}")

        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                // Виклик API для отримання ТІЛЬКИ особистих чатів (не груп!)
                val response = RetrofitClient.apiService.getChats(
                    accessToken = UserSession.accessToken!!,
                    limit = 50,
                    dataType = "users", // ТІЛЬКИ особисті чати, без груп
                    setOnline = 1
                )

                Log.d("ChatsViewModel", "API Response Status: ${response.apiStatus}")
                Log.d("ChatsViewModel", "Chats count: ${response.chats?.size ?: 0}")
                Log.d("ChatsViewModel", "Error code: ${response.errorCode}")
                Log.d("ChatsViewModel", "Error message: ${response.errorMessage}")

                if (response.apiStatus == 200) {
                    authErrorCount = 0  // Reset on success
                    if (response.chats != null && response.chats.isNotEmpty()) {
                        Log.d("ChatsViewModel", "Отримано ${response.chats.size} особистих чатів")

                        // Дешифруємо останнє повідомлення у кожному чаті
                        // API вже повернуло тільки особисті чати (dataType="users")
                        // Залишаємо подвійний захист: виключаємо групи та приховані чати
                        val decryptedChats = response.chats
                            .filter { !it.isGroup && !hiddenChatIds.contains(it.userId) } // Подвійний захист
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
                                        SignalKeyStore(context).getCachedDecryptedMessage(msg.id)
                                            ?: "🔐 E2EE"
                                    }
                                    encryptedText.isNotEmpty() -> {
                                        DecryptionUtility.decryptMessageOrOriginal(
                                            text = encryptedText,
                                            timestamp = msg.timeStamp,
                                            iv = msg.iv,
                                            tag = msg.tag,
                                            cipherVersion = msg.cipherVersion
                                        )
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
                        _error.value = null
                        Log.d("ChatsViewModel", "✅ Завантажено ${decryptedChats.size} чатів успішно")
                    } else {
                        Log.w("ChatsViewModel", "⚠️ API повернуло 200, але чатів немає")
                        _chatList.value = emptyList()
                        _error.value = null // Не помилка, просто порожньо
                    }
                } else if (response.apiStatus == 404) {
                    // WoWonder повертає 404 коли чатів просто немає — це нормально, не помилка авторизації
                    authErrorCount = 0
                    _chatList.value = emptyList()
                    _error.value = null
                    Log.d("ChatsViewModel", "ℹ️ Чатів ще немає (api_status=404 = no data)")
                } else {
                    // Auth errors: api_status 0 = "Access is not allowed" (WoWonder),
                    // 401/403 = invalid/expired token.
                    // WoWonder НІКОЛИ не повертає 404 для auth-помилок — він повертає 0 або 400.
                    // 404 тут означає "немає даних" і вже оброблено вище.
                    if (response.apiStatus == 0 || response.apiStatus == 401 || response.apiStatus == 403) {
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
                        Log.e("ChatsViewModel", "❌ Помилка API: ${response.apiStatus} - $errorMsg")
                    }
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
     * Callback для нових повідомлень
     */
    override fun onNewMessage(messageJson: JSONObject) {
        Log.d("ChatsViewModel", "Нове повідомлення отримано")
        // Оновлюємо список чатів при отриманні нового повідомлення
        fetchChats()
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
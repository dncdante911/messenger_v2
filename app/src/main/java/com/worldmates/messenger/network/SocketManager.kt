package com.worldmates.messenger.network

import android.content.Context
import android.util.Log
import com.worldmates.messenger.data.Constants
import com.worldmates.messenger.data.UserSession
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 🔄 Адаптивний менеджер для Socket.IO з автоматичною оптимізацією
 *
 * Особливості:
 * - Моніторинг якості з'єднання в real-time
 * - Адаптивна затримка reconnect (швидше при хорошому з'єднанні)
 * - Компресія payload при поганому з'єднанні
 * - Автоматичне відключення непотрібних features на слабкому з'єднанні
 */
class SocketManager(
    private val listener: SocketListener,
    private val context: Context? = null
) {

    companion object {
        private const val TAG = "SocketManager"
    }

    private var socket: Socket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 📡 Моніторинг якості з'єднання
    private var networkMonitor: NetworkQualityMonitor? = null
    private var currentQuality: NetworkQualityMonitor.ConnectionQuality =
        NetworkQualityMonitor.ConnectionQuality.GOOD

    interface SocketListener {
        fun onNewMessage(messageJson: JSONObject)
        fun onSocketConnected()
        fun onSocketDisconnected()
        fun onSocketError(error: String)
    }

    fun connect() {
        Log.d(TAG, "🔌 connect() викликано")

        if (UserSession.accessToken == null) {
            Log.e(TAG, "❌ Access token is NULL! Cannot connect to Socket.IO")
            listener.onSocketError("No access token")
            return
        }

        if (socket?.connected() == true) {
            Log.d(TAG, "⚠️ Socket вже підключений, пропускаємо")
            return
        }

        // Ініціалізуємо моніторинг якості з'єднання
        if (context != null && networkMonitor == null) {
            networkMonitor = NetworkQualityMonitor(context)
            startQualityMonitoring()
        }

        Log.d(TAG, "✅ Access token: ${UserSession.accessToken?.take(10)}...")
        Log.d(TAG, "✅ User ID: ${UserSession.userId}")
        Log.d(TAG, "✅ Socket URL: ${Constants.SOCKET_URL}")

        try {
            // 📡 Адаптивні опції Socket.IO в залежності від якості з'єднання
            val opts = IO.Options()
            opts.forceNew = false
            opts.reconnection = true
            opts.reconnectionAttempts = Int.MAX_VALUE

            // 🔄 Адаптивна затримка reconnect
            when (currentQuality) {
                NetworkQualityMonitor.ConnectionQuality.EXCELLENT -> {
                    opts.reconnectionDelay = 500  // Швидке перепідключення
                    opts.reconnectionDelayMax = 2000
                    opts.timeout = 10000
                }
                NetworkQualityMonitor.ConnectionQuality.GOOD -> {
                    opts.reconnectionDelay = 1000 // Нормальне
                    opts.reconnectionDelayMax = 5000
                    opts.timeout = 15000
                }
                NetworkQualityMonitor.ConnectionQuality.POOR -> {
                    opts.reconnectionDelay = 2000 // Повільне
                    opts.reconnectionDelayMax = 10000
                    opts.timeout = 30000
                }
                NetworkQualityMonitor.ConnectionQuality.OFFLINE -> {
                    opts.reconnectionDelay = 5000 // Дуже повільне
                    opts.reconnectionDelayMax = 20000
                    opts.timeout = 60000
                }
            }

            // Пріоритет WebSocket, але fallback на polling при поганому з'єднанні
            opts.transports = if (currentQuality == NetworkQualityMonitor.ConnectionQuality.POOR) {
                arrayOf("polling", "websocket") // Polling спочатку при поганому з'єднанні
            } else {
                arrayOf("websocket", "polling") // WebSocket спочатку при хорошому
            }

            opts.query = "access_token=${UserSession.accessToken}&user_id=${UserSession.userId}"

            socket = IO.socket(Constants.SOCKET_URL, opts)

            // 1. Обработка подключения
            socket?.on(Socket.EVENT_CONNECT) {
                Log.d("SocketManager", "Socket Connected! ID: ${socket?.id()}")
                // Отправляем событие аутентификации для привязки сокета к пользователю
                authenticateSocket()
                listener.onSocketConnected()
            }

            // 2. Обработка отключения
            socket?.on(Socket.EVENT_DISCONNECT) {
                Log.d("SocketManager", "Socket Disconnected")
                listener.onSocketDisconnected()
            }

            // 3. Обработка переподключения
            socket?.on("reconnect") {
                Log.d("SocketManager", "Socket Reconnected")
                authenticateSocket()
                listener.onSocketConnected()
            }

            // 4. Обработка попытки переподключения
            socket?.on("reconnecting") { args ->
                val attempt = if (args.isNotEmpty()) args[0].toString() else "?"
                Log.d("SocketManager", "Reconnection Attempt #$attempt")
            }

            // 6. Обработка ошибок
            socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                val error = if (args.isNotEmpty()) args[0].toString() else "Unknown error"
                Log.e("SocketManager", "Connection Error: $error")
                listener.onSocketError("Connection Error: $error")
            }

            // 7. Получение нового личного сообщения (основное событие от сервера)
            socket?.on(Constants.SOCKET_EVENT_PRIVATE_MESSAGE) { args ->
                Log.d("SocketManager", "📨 private_message event received with ${args.size} args")
                if (args.isNotEmpty()) {
                    Log.d("SocketManager", "Args[0] type: ${args[0]?.javaClass?.simpleName}")
                    if (args[0] is JSONObject) {
                        val messageData = args[0] as JSONObject
                        Log.d("SocketManager", "✅ private_message JSON: ${messageData.toString()}")
                        listener.onNewMessage(messageData)
                    } else {
                        Log.w("SocketManager", "⚠️ private_message args[0] не є JSONObject: ${args[0]}")
                    }
                } else {
                    Log.w("SocketManager", "⚠️ private_message отримано без аргументів")
                }
            }

            // 8. Получение нового сообщения (для обратной совместимости)
            socket?.on(Constants.SOCKET_EVENT_NEW_MESSAGE) { args ->
                Log.d("SocketManager", "📨 new_message event received with ${args.size} args")
                if (args.isNotEmpty() && args[0] is JSONObject) {
                    Log.d("SocketManager", "✅ new_message JSON: ${args[0]}")
                    listener.onNewMessage(args[0] as JSONObject)
                }
            }

            // 8a. ДОДАТКОВО: Слухаємо всі можливі події повідомлень
            socket?.on("private_message_page") { args ->
                Log.d("SocketManager", "📨 private_message_page received with ${args.size} args")
                if (args.isNotEmpty() && args[0] is JSONObject) {
                    listener.onNewMessage(args[0] as JSONObject)
                }
            }

            socket?.on("page_message") { args ->
                Log.d("SocketManager", "📨 page_message received with ${args.size} args")
                if (args.isNotEmpty() && args[0] is JSONObject) {
                    listener.onNewMessage(args[0] as JSONObject)
                }
            }

            // 8. Обробка індикатора набору тексту
            // REST API path: емітить {from_id, to_id} без is_typing
            // Socket.IO TypingController: емітить {sender_id, is_typing:200/300, ...}
            socket?.on(Constants.SOCKET_EVENT_TYPING) { args ->
                if (args.isNotEmpty() && args[0] is JSONObject) {
                    val data = args[0] as? org.json.JSONObject
                    val senderId = data?.let {
                        it.optLong("sender_id", 0L).takeIf { id -> id > 0L }
                            ?: it.optLong("from_id", 0L)
                    }
                    val isTypingCode = data?.optInt("is_typing", -1) ?: -1
                    // REST API path не надсилає is_typing → сама подія означає "почав набирати"
                    val isTyping = if (isTypingCode >= 0) isTypingCode == 200 else true
                    Log.d("SocketManager", "User $senderId is typing: $isTyping (code: $isTypingCode)")
                    if (listener is ExtendedSocketListener) {
                        listener.onTypingStatus(senderId, isTyping)
                    }
                }
            }

            // 8a. Обробка зупинки набору (REST API path → typing_done подія)
            socket?.on(Constants.SOCKET_EVENT_TYPING_DONE) { args ->
                if (args.isNotEmpty() && args[0] is JSONObject) {
                    val data = args[0] as? org.json.JSONObject
                    val senderId = data?.let {
                        it.optLong("sender_id", 0L).takeIf { id -> id > 0L }
                            ?: it.optLong("from_id", 0L)
                    }
                    Log.d("SocketManager", "User $senderId stopped typing")
                    if (listener is ExtendedSocketListener) {
                        listener.onTypingStatus(senderId, false)
                    }
                }
            }

            // 8b. Обробка запису голосового повідомлення
            // is_recording: 200 = розпочав запис, 300 = завершив
            socket?.on("recording") { args ->
                if (args.isNotEmpty() && args[0] is JSONObject) {
                    val data = args[0] as? org.json.JSONObject
                    val senderId = data?.let {
                        it.optLong("sender_id", 0L).takeIf { id -> id > 0L }
                            ?: it.optLong("from_id", 0L)
                    }
                    val isRecordingCode = data?.optInt("is_recording", 200) ?: 200
                    val isRecording = isRecordingCode == 200
                    Log.d("SocketManager", "User $senderId recording: $isRecording")
                    if (listener is ExtendedSocketListener) {
                        listener.onRecordingStatus(senderId, isRecording)
                    }
                }
            }

            // 9. Обработка "последний раз в сети"
            socket?.on(Constants.SOCKET_EVENT_LAST_SEEN) { args ->
                if (args.isNotEmpty() && args[0] is JSONObject) {
                    val data = args[0] as JSONObject
                    val userId = data.optLong("user_id", 0)
                    val lastSeen = data.optLong("last_seen", 0)
                    Log.d("SocketManager", "User $userId last seen: $lastSeen")
                    if (listener is ExtendedSocketListener) {
                        listener.onLastSeen(userId, lastSeen)
                    }
                }
            }

            // 10. Обработка прочтения сообщения
            socket?.on(Constants.SOCKET_EVENT_MESSAGE_SEEN) { args ->
                if (args.isNotEmpty() && args[0] is JSONObject) {
                    val data = args[0] as JSONObject
                    val messageId = data.optLong("message_id", 0)
                    val userId = data.optLong("user_id", 0)
                    Log.d("SocketManager", "Message $messageId seen by user $userId")
                    if (listener is ExtendedSocketListener) {
                        listener.onMessageSeen(messageId, userId)
                    }
                }
            }

            // 11. Обработка группового сообщения
            socket?.on(Constants.SOCKET_EVENT_GROUP_MESSAGE) { args ->
                if (args.isNotEmpty() && args[0] is JSONObject) {
                    val data = args[0] as JSONObject
                    Log.d("SocketManager", "Group message received")
                    if (listener is ExtendedSocketListener) {
                        listener.onGroupMessage(data)
                    }
                }
            }

            // 14. КРИТИЧНО: Обработка события "user_status_change" от WoWonder сервера
            // WoWonder отправляет HTML, нужно парсить онлайн пользователей из разметки
            socket?.on("user_status_change") { args ->
                Log.d("SocketManager", "Received user_status_change event with ${args.size} args")
                if (args.isNotEmpty() && args[0] is JSONObject) {
                    val data = args[0] as JSONObject

                    // WoWonder отправляет HTML в полях online_users и offline_users
                    val onlineUsersHtml = data.optString("online_users", "")
                    val offlineUsersHtml = data.optString("offline_users", "")

                    // Парсим онлайн пользователей из HTML
                    parseOnlineUsers(onlineUsersHtml, true)
                    parseOnlineUsers(offlineUsersHtml, false)
                }
            }

            // 15. ДОПОЛНИТЕЛЬНО: Слушаем событие с конкретным пользователем
            socket?.on("on_user_loggedin") { args ->
                Log.d("SocketManager", "Received on_user_loggedin with ${args.size} args")
                if (args.isNotEmpty()) {
                    try {
                        val userId = when (val arg = args[0]) {
                            is Number -> arg.toLong()
                            is String -> arg.toLongOrNull() ?: 0
                            is JSONObject -> arg.optLong("user_id", 0)
                            else -> 0
                        }
                        if (userId > 0) {
                            Log.d("SocketManager", "✅ User $userId logged in")
                            if (listener is ExtendedSocketListener) {
                                listener.onUserOnline(userId)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("SocketManager", "Error parsing on_user_loggedin", e)
                    }
                }
            }

            socket?.on("on_user_loggedoff") { args ->
                Log.d("SocketManager", "Received on_user_loggedoff with ${args.size} args")
                if (args.isNotEmpty()) {
                    try {
                        val userId = when (val arg = args[0]) {
                            is Number -> arg.toLong()
                            is String -> arg.toLongOrNull() ?: 0
                            is JSONObject -> arg.optLong("user_id", 0)
                            else -> 0
                        }
                        if (userId > 0) {
                            Log.d("SocketManager", "❌ User $userId logged off")
                            if (listener is ExtendedSocketListener) {
                                listener.onUserOffline(userId)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("SocketManager", "Error parsing on_user_loggedoff", e)
                    }
                }
            }

            // 16. Обробка редагування повідомлення в реальному часі
            // Payload: {message_id, text (encrypted), iv, tag, cipher_version, ...}
            socket?.on("message_edited") { args ->
                if (args.isNotEmpty() && args[0] is JSONObject) {
                    val data = args[0] as? org.json.JSONObject
                    val messageId = data?.optLong("message_id", 0L) ?: 0L
                    val newText = data?.optString("text", "") ?: ""
                    val iv = data?.optString("iv", null)
                    val tag = data?.optString("tag", null)
                    val cipherVersion = data?.optString("cipher_version", null)
                    Log.d("SocketManager", "Message $messageId edited via socket")
                    if (messageId > 0 && listener is ExtendedSocketListener) {
                        listener.onMessageEdited(messageId, newText, iv, tag, cipherVersion)
                    }
                }
            }

            // 17. Обробка видалення повідомлення в реальному часі
            socket?.on("message_deleted") { args ->
                if (args.isNotEmpty() && args[0] is JSONObject) {
                    val data = args[0] as? org.json.JSONObject
                    val messageId = data?.optLong("message_id", 0L) ?: 0L
                    val deleteType = data?.optString("delete_type", "just_me") ?: "just_me"
                    Log.d("SocketManager", "Message $messageId deleted ($deleteType) via socket")
                    if (messageId > 0 && listener is ExtendedSocketListener) {
                        listener.onMessageDeleted(messageId, deleteType)
                    }
                }
            }

            // 18a. Групове редагування повідомлення (group_message_edited)
            // Payload: {message_id, group_id, text, iv, tag, cipher_version, time, edited}
            socket?.on("group_message_edited") { args ->
                if (args.isNotEmpty() && args[0] is JSONObject) {
                    val data = args[0] as? org.json.JSONObject
                    val messageId     = data?.optLong("message_id", 0L) ?: 0L
                    val newText       = data?.optString("text", "") ?: ""
                    val iv            = data?.optString("iv", null)
                    val tag           = data?.optString("tag", null)
                    val cipherVersion = data?.optString("cipher_version", null)
                    Log.d("SocketManager", "Group message $messageId edited via socket")
                    if (messageId > 0L && listener is ExtendedSocketListener) {
                        listener.onMessageEdited(messageId, newText, iv, tag, cipherVersion)
                    }
                }
            }

            // 18b. Групове видалення повідомлення (group_message_deleted)
            // Payload: {message_id, group_id}
            socket?.on("group_message_deleted") { args ->
                if (args.isNotEmpty() && args[0] is JSONObject) {
                    val data      = args[0] as? org.json.JSONObject
                    val messageId = data?.optLong("message_id", 0L) ?: 0L
                    Log.d("SocketManager", "Group message $messageId deleted via socket")
                    if (messageId > 0L && listener is ExtendedSocketListener) {
                        listener.onMessageDeleted(messageId, "everyone")
                    }
                }
            }

            // 18c. Очищення історії групи для всіх (group_history_cleared)
            // Payload: {group_id}
            socket?.on("group_history_cleared") { args ->
                if (args.isNotEmpty() && args[0] is JSONObject) {
                    val data    = args[0] as? org.json.JSONObject
                    val groupId = data?.optLong("group_id", 0L) ?: 0L
                    Log.d("SocketManager", "Group $groupId history cleared via socket")
                    if (groupId > 0L && listener is ExtendedSocketListener) {
                        listener.onGroupHistoryCleared(groupId)
                    }
                }
            }

            // 18. Обробка реакції на повідомлення в реальному часі
            socket?.on("message_reaction") { args ->
                if (args.isNotEmpty() && args[0] is JSONObject) {
                    val data = args[0] as? org.json.JSONObject
                    val messageId = data?.optLong("message_id", 0L) ?: 0L
                    val userId = data?.optLong("user_id", 0L) ?: 0L
                    val reaction = data?.optString("reaction", "") ?: ""
                    val action = data?.optString("action", "added") ?: "added"
                    Log.d("SocketManager", "Reaction '$reaction' ($action) on message $messageId by user $userId")
                    if (messageId > 0 && listener is ExtendedSocketListener) {
                        listener.onMessageReaction(messageId, userId, reaction, action)
                    }
                }
            }

            // 19. Обробка закріплення/відкріплення повідомлення в реальному часі
            socket?.on("message_pinned") { args ->
                if (args.isNotEmpty() && args[0] is JSONObject) {
                    val data = args[0] as? org.json.JSONObject
                    val messageId = data?.optLong("message_id", 0L) ?: 0L
                    val pin = data?.optString("pin", "yes") ?: "yes"
                    val chatId = data?.optLong("chat_id", 0L) ?: 0L
                    Log.d("SocketManager", "Message $messageId pin=$pin chatId=$chatId via socket")
                    if (listener is ExtendedSocketListener) {
                        listener.onMessagePinned(messageId, pin == "yes", chatId)
                    }
                }
            }

            socket?.connect()

        } catch (e: Exception) {
            e.printStackTrace()
            listener.onSocketError("Socket Connection Exception: ${e.message}")
        }
    }

    private fun authenticateSocket() {
        // Проверяем токен и отправляем данные для "привязки" сокета на Node.js
        // Сервер ожидает событие "join" с session hash в поле user_id
        if (socket?.connected() == true && UserSession.accessToken != null) {
            val authData = JSONObject().apply {
                // user_id должен быть session hash (access_token), а НЕ числовой ID
                put("user_id", UserSession.accessToken)
                // Опционально: можно добавить массивы открытых чатов
                // put("recipient_ids", JSONArray())
                // put("recipient_group_ids", JSONArray())
            }
            socket?.emit(Constants.SOCKET_EVENT_AUTH, authData)
            Log.d("SocketManager", "Sent 'join' event with session hash: ${UserSession.accessToken?.take(10)}...")
        }
    }

    fun sendMessage(recipientId: Long, text: String) {
        if (socket?.connected() == true && UserSession.accessToken != null) {
            val messagePayload = JSONObject().apply {
                // Сервер ожидает именно эти поля (см. PrivateMessageController.js)
                put("msg", text)  // НЕ "text"!
                put("from_id", UserSession.userId)  // НЕ "user_id"!
                put("to_id", recipientId)  // НЕ "recipient_id"!
                // TODO: Добавить поля для медиа, стикеров и т.д.
                // mediaId, mediaFilename, record, message_reply_id, story_id, lng, lat, contact, color, isSticker
            }
            socket?.emit(Constants.SOCKET_EVENT_SEND_MESSAGE, messagePayload)
            Log.d("SocketManager", "Emitted private_message to user $recipientId: $text")
        } else {
            // Fallback: Если Socket не подключен, можно использовать REST API для отправки (send-message.php)
            Log.w("SocketManager", "Socket not connected. Message not sent via socket.")
        }
    }

    fun disconnect() {
        Log.d(TAG, "🔌 Disconnecting Socket.IO and cleaning up")
        socket?.disconnect()
        networkMonitor?.stopMonitoring()
        scope.cancel()
    }

    /**
     * Emit raw event to server (for channels, stories, etc.)
     */
    fun emitRaw(event: String, data: JSONObject) {
        if (socket?.connected() == true) {
            socket?.emit(event, data)
        }
    }

    // ==================== АДАПТИВНА ЧАСТИНА ====================

    /**
     * Запустити моніторинг якості з'єднання
     */
    private fun startQualityMonitoring() {
        scope.launch {
            networkMonitor?.connectionState?.collectLatest { state ->
                currentQuality = state.quality

                Log.i(TAG, "📊 Connection quality changed: ${state.quality}")
                Log.i(TAG, "   ├─ Latency: ${state.latencyMs}ms")
                Log.i(TAG, "   ├─ Bandwidth: ${state.bandwidthKbps} Kbps")
                Log.i(TAG, "   ├─ Metered: ${state.isMetered}")
                Log.i(TAG, "   └─ Media mode: ${state.mediaLoadMode}")

                // При значній зміні якості - переконнектимось з новими параметрами
                if (socket?.connected() == true) {
                    when (state.quality) {
                        NetworkQualityMonitor.ConnectionQuality.POOR -> {
                            Log.w(TAG, "⚠️ Poor connection detected. Optimizing Socket.IO...")
                            // При поганому з'єднанні можна зменшити частоту ping/pong
                        }
                        NetworkQualityMonitor.ConnectionQuality.EXCELLENT -> {
                            Log.i(TAG, "✅ Excellent connection. Full features enabled.")
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    /**
     * Отримати поточну якість з'єднання
     */
    fun getConnectionQuality(): NetworkQualityMonitor.ConnectionQuality {
        return currentQuality
    }

    /**
     * Чи можна відправляти typing indicators?
     * При поганому з'єднанні - краще не відправляти (економія)
     */
    fun canSendTypingIndicators(): Boolean {
        return currentQuality != NetworkQualityMonitor.ConnectionQuality.POOR &&
                currentQuality != NetworkQualityMonitor.ConnectionQuality.OFFLINE
    }

    /**
     * Чи можна завантажувати медіа автоматично?
     */
    fun canAutoLoadMedia(): Boolean {
        return networkMonitor?.canLoadMedia() ?: true
    }

    /**
     * Отримати опис якості з'єднання для UI
     */
    fun getQualityDescription(): String {
        return when (currentQuality) {
            NetworkQualityMonitor.ConnectionQuality.EXCELLENT -> "🟢 Відмінне з'єднання"
            NetworkQualityMonitor.ConnectionQuality.GOOD -> "🟡 Добре з'єднання"
            NetworkQualityMonitor.ConnectionQuality.POOR -> "🟠 Погане з'єднання"
            NetworkQualityMonitor.ConnectionQuality.OFFLINE -> "🔴 Немає з'єднання"
        }
    }

    // ==================== КІНЕЦЬ АДАПТИВНОЇ ЧАСТИНИ ====================

    /**
     * Универсальный метод для отправки произвольных событий через Socket.IO
     * Используется для WebRTC сигнализации и других кастомных событий
     */
    fun emit(event: String, data: Any) {
        if (socket?.connected() == true) {
            socket?.emit(event, data)
            Log.d(TAG, "✅ Emitted event: $event")
            Log.d(TAG, "   Data: ${data.toString().take(200)}")  // Перші 200 символів
        } else {
            Log.e(TAG, "❌ Cannot emit event '$event': Socket not connected!")
            Log.e(TAG, "   Socket state: connected=${socket?.connected()}, socket=${socket != null}")
        }
    }

    /**
     * 🔌 Підписатись на Socket.IO подію
     * Використовується для WebRTC call events
     */
    fun on(event: String, listener: (Array<Any>) -> Unit): io.socket.emitter.Emitter.Listener {
        val emitterListener = io.socket.emitter.Emitter.Listener { args ->
            listener(args)
        }
        socket?.on(event, emitterListener)
        Log.d(TAG, "Subscribed to event: $event")
        return emitterListener
    }

    /**
     * 🔌 Відписатись від Socket.IO події
     */
    fun off(event: String, listener: io.socket.emitter.Emitter.Listener? = null) {
        if (listener != null) {
            socket?.off(event, listener)
        } else {
            socket?.off(event)
        }
        Log.d(TAG, "Unsubscribed from event: $event")
    }

    /**
     * 🧊 Request ICE servers from server via Socket.IO
     * Uses Socket.IO acknowledgments for synchronous response
     */
    suspend fun requestIceServers(userId: Int): JSONObject? = withTimeoutOrNull(2000) {
        suspendCancellableCoroutine { continuation ->
            if (socket?.connected() != true) {
                Log.e(TAG, "❌ Cannot request ICE servers: Socket not connected")
                continuation.resume(null) {}
                return@suspendCancellableCoroutine
            }

            try {
                val requestData = JSONObject().apply {
                    put("userId", userId)
                }

                Log.d(TAG, "🧊 Requesting ICE servers for user $userId via Socket.IO...")

                // Create Ack callback with proper Socket.IO interface
                val ackCallback = io.socket.client.Ack { args ->
                    try {
                        if (args.isNotEmpty()) {
                            val response = args[0] as? JSONObject
                            if (response?.optBoolean("success") == true) {
                                Log.d(TAG, "✅ ICE servers received via Socket.IO")
                                continuation.resume(response) {}
                            } else {
                                Log.e(TAG, "❌ ICE servers request failed: ${response?.optString("error")}")
                                continuation.resume(null) {}
                            }
                        } else {
                            Log.e(TAG, "❌ ICE servers response empty")
                            continuation.resume(null) {}
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error processing ICE servers response", e)
                        continuation.resume(null) {}
                    }
                }

                // Emit with acknowledgment
                socket?.emit("ice:request", requestData, ackCallback)

                // Cleanup on cancellation
                continuation.invokeOnCancellation {
                    Log.w(TAG, "⚠️ ICE servers request cancelled")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error requesting ICE servers", e)
                continuation.resume(null) {}
            }
        }
    }

    /**
     * Відправляє індикатор "печатає" (тільки при хорошому з'єднанні)
     */
    fun sendTyping(recipientId: Long, isTyping: Boolean) {
        // При поганому з'єднанні не відправляємо typing indicators (економія)
        if (!canSendTypingIndicators()) {
            Log.d(TAG, "⚠️ Skipping typing indicator due to poor connection")
            return
        }

        if (socket?.connected() == true && UserSession.accessToken != null) {
            val typingPayload = JSONObject().apply {
                put("access_token", UserSession.accessToken)
                put("user_id", UserSession.userId)
                put("recipient_id", recipientId)
                put("is_typing", isTyping)
            }
            socket?.emit(Constants.SOCKET_EVENT_TYPING, typingPayload)
        }
    }

    /**
     * Надсилає індикатор запису голосового повідомлення через Socket.IO
     * user_id має бути session hash (access_token) для коректного розпізнавання на сервері
     */
    fun sendRecordingStatus(recipientId: Long) {
        if (!canSendTypingIndicators()) return
        if (socket?.connected() == true && UserSession.accessToken != null) {
            val payload = JSONObject().apply {
                put("user_id", UserSession.accessToken)   // session hash, not numeric ID
                put("recipient_id", recipientId.toString())
            }
            socket?.emit("recording", payload)
            Log.d(TAG, "Emitted 'recording' for recipient $recipientId")
        }
    }

    /**
     * Отправляет подтверждение прочтения сообщения
     */
    fun sendMessageSeen(messageId: Long, senderId: Long) {
        if (socket?.connected() == true && UserSession.accessToken != null) {
            val seenPayload = JSONObject().apply {
                put("access_token", UserSession.accessToken)
                put("user_id", UserSession.userId)
                put("message_id", messageId)
                put("sender_id", senderId)
            }
            socket?.emit(Constants.SOCKET_EVENT_MESSAGE_SEEN, seenPayload)
        }
    }

    /**
     * Отправляет групповое сообщение
     */
    fun sendGroupMessage(groupId: Long, text: String) {
        if (socket?.connected() == true && UserSession.accessToken != null) {
            val messagePayload = JSONObject().apply {
                // Сервер ожидает именно эти поля (см. GroupMessageController.js)
                put("msg", text)  // НЕ "text"!
                put("from_id", UserSession.userId)  // НЕ "user_id"!
                put("group_id", groupId)
                // TODO: mediaId, message_reply_id, color, isSticker
            }
            socket?.emit(Constants.SOCKET_EVENT_GROUP_MESSAGE, messagePayload)
            Log.d("SocketManager", "Emitted group_message to group $groupId: $text")
        }
    }

    /**
     * Парсит HTML разметку с онлайн/оффлайн пользователями от WoWonder
     */
    private fun parseOnlineUsers(html: String, isOnline: Boolean) {
        if (html.isEmpty()) return

        try {
            // WoWonder использует id="online_XXX" где XXX - это user_id
            val pattern = """id="online_(\d+)"""".toRegex()
            val matches = pattern.findAll(html)

            matches.forEach { match ->
                val userId = match.groupValues[1].toLongOrNull()
                if (userId != null && userId > 0) {
                    Log.d("SocketManager", "Parsed user $userId as ${if (isOnline) "ONLINE ✅" else "OFFLINE ❌"}")
                    if (listener is ExtendedSocketListener) {
                        if (isOnline) {
                            listener.onUserOnline(userId)
                        } else {
                            listener.onUserOffline(userId)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SocketManager", "Error parsing online users HTML", e)
        }
    }

    // ==================== КАНАЛИ - SOCKET.IO ====================

    /**
     * Підписатися на оновлення каналу
     */
    fun subscribeToChannel(channelId: Long) {
        if (socket?.connected() == true && UserSession.userId != null) {
            val data = JSONObject().apply {
                put("channelId", channelId)
                put("userId", UserSession.userId)
            }
            socket?.emit("channel:subscribe", data)
            Log.d(TAG, "📢 Subscribed to channel $channelId")
        }
    }

    /**
     * Відписатися від оновлень каналу
     */
    fun unsubscribeFromChannel(channelId: Long) {
        if (socket?.connected() == true && UserSession.userId != null) {
            val data = JSONObject().apply {
                put("channelId", channelId)
                put("userId", UserSession.userId)
            }
            socket?.emit("channel:unsubscribe", data)
            Log.d(TAG, "📢 Unsubscribed from channel $channelId")
        }
    }

    /**
     * Слухати нові пости в каналі
     */
    fun onChannelPostCreated(callback: (JSONObject) -> Unit) {
        socket?.on("channel:post_created") { args ->
            if (args.isNotEmpty() && args[0] is JSONObject) {
                val data = args[0] as JSONObject
                callback(data)
                Log.d(TAG, "📝 New channel post received")
            }
        }
    }

    /**
     * Слухати оновлення постів
     */
    fun onChannelPostUpdated(callback: (JSONObject) -> Unit) {
        socket?.on("channel:post_updated") { args ->
            if (args.isNotEmpty() && args[0] is JSONObject) {
                val data = args[0] as JSONObject
                callback(data)
                Log.d(TAG, "✏️ Channel post updated")
            }
        }
    }

    /**
     * Слухати видалення постів
     */
    fun onChannelPostDeleted(callback: (JSONObject) -> Unit) {
        socket?.on("channel:post_deleted") { args ->
            if (args.isNotEmpty() && args[0] is JSONObject) {
                val data = args[0] as JSONObject
                callback(data)
                Log.d(TAG, "🗑️ Channel post deleted")
            }
        }
    }

    /**
     * Слухати нові коментарі
     */
    fun onChannelCommentAdded(callback: (JSONObject) -> Unit) {
        socket?.on("channel:comment_added") { args ->
            if (args.isNotEmpty() && args[0] is JSONObject) {
                val data = args[0] as JSONObject
                callback(data)
                Log.d(TAG, "💬 New channel comment")
            }
        }
    }

    /**
     * Відправити typing в каналі (коментарі)
     */
    fun sendChannelTyping(channelId: Long, postId: Long, isTyping: Boolean) {
        if (!canSendTypingIndicators()) return

        if (socket?.connected() == true && UserSession.userId != null) {
            val data = JSONObject().apply {
                put("channelId", channelId)
                put("postId", postId)
                put("userId", UserSession.userId)
                put("isTyping", isTyping)
            }
            socket?.emit("channel:typing", data)
        }
    }

    // ==================== STORIES - SOCKET.IO ====================

    /**
     * Підписатися на stories друзів
     */
    fun subscribeToStories(friendIds: List<Long>) {
        if (socket?.connected() == true && UserSession.userId != null) {
            val data = JSONObject().apply {
                put("userId", UserSession.userId)
                put("friendIds", org.json.JSONArray(friendIds))
            }
            socket?.emit("story:subscribe", data)
            Log.d(TAG, "📸 Subscribed to ${friendIds.size} friends' stories")
        }
    }

    /**
     * Відписатися від stories
     */
    fun unsubscribeFromStories(friendIds: List<Long>) {
        if (socket?.connected() == true && UserSession.userId != null) {
            val data = JSONObject().apply {
                put("userId", UserSession.userId)
                put("friendIds", org.json.JSONArray(friendIds))
            }
            socket?.emit("story:unsubscribe", data)
            Log.d(TAG, "📸 Unsubscribed from stories")
        }
    }

    /**
     * Слухати нові stories
     */
    fun onStoryCreated(callback: (JSONObject) -> Unit) {
        socket?.on("story:created") { args ->
            if (args.isNotEmpty() && args[0] is JSONObject) {
                val data = args[0] as JSONObject
                callback(data)
                Log.d(TAG, "📸 New story created")
            }
        }
    }

    /**
     * Слухати видалення stories
     */
    fun onStoryDeleted(callback: (JSONObject) -> Unit) {
        socket?.on("story:deleted") { args ->
            if (args.isNotEmpty() && args[0] is JSONObject) {
                val data = args[0] as JSONObject
                callback(data)
                Log.d(TAG, "🗑️ Story deleted")
            }
        }
    }

    /**
     * Повідомити про перегляд story
     */
    fun sendStoryView(storyId: Long, storyOwnerId: Long) {
        if (socket?.connected() == true && UserSession.userId != null) {
            val data = JSONObject().apply {
                put("storyId", storyId)
                put("userId", UserSession.userId)
                put("storyOwnerId", storyOwnerId)
            }
            socket?.emit("story:view", data)
            Log.d(TAG, "👁️ Story view sent")
        }
    }

    /**
     * Слухати нові коментарі до stories
     */
    fun onStoryCommentAdded(callback: (JSONObject) -> Unit) {
        socket?.on("story:comment_added") { args ->
            if (args.isNotEmpty() && args[0] is JSONObject) {
                val data = args[0] as JSONObject
                callback(data)
                Log.d(TAG, "💬 New story comment")
            }
        }
    }

    /**
     * Відправити typing в story (коментарі)
     */
    fun sendStoryTyping(storyId: Long, storyOwnerId: Long, isTyping: Boolean) {
        if (!canSendTypingIndicators()) return

        if (socket?.connected() == true && UserSession.userId != null) {
            val data = JSONObject().apply {
                put("storyId", storyId)
                put("userId", UserSession.userId)
                put("storyOwnerId", storyOwnerId)
                put("isTyping", isTyping)
            }
            socket?.emit("story:typing", data)
        }
    }

    // ==================== КІНЕЦЬ КАНАЛІВ ТА STORIES ====================

    /**
     * Расширенный интерфейс для дополнительных событий
     */
    interface ExtendedSocketListener : SocketListener {
        fun onTypingStatus(userId: Long?, isTyping: Boolean) {}
        fun onRecordingStatus(userId: Long?, isRecording: Boolean) {}
        fun onLastSeen(userId: Long, lastSeen: Long) {}
        fun onMessageSeen(messageId: Long, userId: Long) {}
        fun onGroupMessage(messageJson: JSONObject) {}
        fun onUserOnline(userId: Long) {}
        fun onUserOffline(userId: Long) {}
        fun onMessageEdited(messageId: Long, newText: String, iv: String? = null, tag: String? = null, cipherVersion: String? = null) {}
        fun onMessageDeleted(messageId: Long, deleteType: String) {}
        fun onMessageReaction(messageId: Long, userId: Long, reaction: String, action: String) {}
        fun onMessagePinned(messageId: Long, isPinned: Boolean, chatId: Long) {}
        fun onGroupHistoryCleared(groupId: Long) {}
    }
}

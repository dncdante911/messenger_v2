package com.worldmates.messenger.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.worldmates.messenger.R
import com.worldmates.messenger.data.Constants
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.ui.messages.MessagesActivity
import com.worldmates.messenger.utils.DecryptionUtility
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

/**
 * Фоновий сервіс, який слухає Socket.IO і показує push-сповіщення
 * для нових повідомлень від особистих чатів, груп і каналів.
 *
 * Не використовує Firebase — повністю self-hosted через Node.js.
 * Запускається як ForegroundService після успішного входу.
 */
class MessageNotificationService : Service() {

    companion object {
        private const val TAG = "MsgNotifService"

        // Канал для особистих повідомлень / груп / каналів (HIGH priority + sound)
        const val CHANNEL_MESSAGES_ID   = "wm_messages"
        const val CHANNEL_MESSAGES_NAME = "Повідомлення"

        // Канал для постійного фонового сповіщення самого сервісу (LOW, тихий)
        private const val CHANNEL_SERVICE_ID   = "wm_service"
        private const val CHANNEL_SERVICE_NAME = "WorldMates — фоновий сервіс"

        // ID постійного (foreground) сповіщення сервісу
        private const val FOREGROUND_NOTIF_ID = 9001

        var isRunning = false
            private set

        // Які чати зараз відкриті — для них сповіщення не показуємо
        @Volatile var activeRecipientId: Long = 0   // приватний чат
        @Volatile var activeGroupId: Long = 0        // груповий чат
        @Volatile var activeChannelId: Long = 0      // канал

        fun start(context: Context) {
            if (isRunning) return
            val intent = Intent(context, MessageNotificationService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MessageNotificationService::class.java))
        }
    }

    private var socket: Socket? = null

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannels()
        startForeground(FOREGROUND_NOTIF_ID, buildServiceNotification())
        connectSocket()
        Log.d(TAG, "MessageNotificationService started")
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        socket?.off()
        socket?.disconnect()
        socket = null
        Log.d(TAG, "MessageNotificationService stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    // ------------------------------------------------------------------
    // Socket connection
    // ------------------------------------------------------------------

    private fun connectSocket() {
        val token = UserSession.accessToken
        if (token.isNullOrEmpty()) {
            Log.e(TAG, "No access token — stopping service")
            stopSelf()
            return
        }

        try {
            val opts = IO.Options().apply {
                forceNew            = false
                reconnection        = true
                reconnectionAttempts = Int.MAX_VALUE
                reconnectionDelay   = 2_000
                reconnectionDelayMax = 15_000
                timeout             = 20_000
                transports          = arrayOf("websocket", "polling")
                query               = "access_token=$token&user_id=${UserSession.userId}"
            }

            socket = IO.socket(Constants.SOCKET_URL, opts)

            socket?.on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "Notification socket connected")
                socket?.emit(Constants.SOCKET_EVENT_AUTH, JSONObject().apply {
                    put("user_id", token)
                })
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                Log.d(TAG, "Notification socket disconnected")
            }

            socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                Log.w(TAG, "Socket error: ${args.firstOrNull()}")
            }

            // Private chat messages (two possible event names for compatibility)
            socket?.on(Constants.SOCKET_EVENT_PRIVATE_MESSAGE) { args ->
                (args.firstOrNull() as? JSONObject)?.let {
                    handleMessage(it, type = ChatType.PRIVATE)
                }
            }
            socket?.on(Constants.SOCKET_EVENT_NEW_MESSAGE) { args ->
                (args.firstOrNull() as? JSONObject)?.let {
                    handleMessage(it, type = ChatType.PRIVATE)
                }
            }

            // Group messages
            socket?.on(Constants.SOCKET_EVENT_GROUP_MESSAGE) { args ->
                (args.firstOrNull() as? JSONObject)?.let {
                    handleMessage(it, type = ChatType.GROUP)
                }
            }

            // Channel posts
            socket?.on(Constants.SOCKET_EVENT_CHANNEL_MESSAGE) { args ->
                (args.firstOrNull() as? JSONObject)?.let {
                    handleMessage(it, type = ChatType.CHANNEL)
                }
            }

            socket?.connect()
        } catch (e: Exception) {
            Log.e(TAG, "Socket connection error", e)
        }
    }

    // ------------------------------------------------------------------
    // Message handling
    // ------------------------------------------------------------------

    private enum class ChatType { PRIVATE, GROUP, CHANNEL }

    private fun handleMessage(data: JSONObject, type: ChatType) {
        try {
            val senderId   = data.optLong("from_id", data.optLong("sender_id", 0))
            val groupId    = data.optLong("group_id", 0)
            val channelId  = data.optLong("channel_id", 0)

            // --- Витягуємо ім'я відправника ---
            // Сервер може надсилати ім'я у різних форматах:
            // 1. Новий Node.js формат: user_data: { name, username, first_name, last_name }
            // 2. Старий WoWonder формат: username: "First Last"
            // 3. Прямі поля: sender_name, from_name
            val senderName = extractSenderName(data)

            // --- Витягуємо та дешифруємо текст повідомлення ---
            val rawText = data.optString("msg",
                          data.optString("text",
                          data.optString("message", data.optString("content", ""))))

            val timestamp     = data.optLong("time", data.optLong("time_api", 0))
            val iv            = data.optString("iv", null)
            val tag           = data.optString("tag", null)
            val cipherVersion = if (data.has("cipher_version")) data.optInt("cipher_version", 1) else null

            // Дешифруємо текст (або показуємо fallback)
            val displayText = decryptNotificationText(rawText, timestamp, iv, tag, cipherVersion, data)

            // Skip our own messages
            if (senderId == UserSession.userId) return

            // Skip if user is already in the relevant chat
            when (type) {
                ChatType.PRIVATE  -> if (senderId  == activeRecipientId && activeRecipientId > 0) return
                ChatType.GROUP    -> if (groupId   == activeGroupId   && activeGroupId   > 0) return
                ChatType.CHANNEL  -> if (channelId == activeChannelId && activeChannelId > 0) return
            }

            // Skip empty messages
            if (displayText.isBlank()) return

            val (title, notifId) = when (type) {
                ChatType.PRIVATE -> Pair(senderName, senderId.toInt())
                ChatType.GROUP   -> {
                    val groupName = data.optString("group_name", "Група")
                    Pair("$senderName @ $groupName", (groupId + 100_000L).toInt())
                }
                ChatType.CHANNEL -> {
                    val channelName = data.optString("channel_name", "Канал")
                    Pair(channelName, (channelId + 200_000L).toInt())
                }
            }

            showMessageNotification(
                notifId    = notifId,
                title      = title,
                text       = displayText,
                senderId   = senderId,
                senderName = senderName,
                groupId    = groupId,
                channelId  = channelId,
                type       = type
            )

            Log.d(TAG, "[$type] notification → $title: ${displayText.take(40)}")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing message for notification", e)
        }
    }

    /**
     * Витягує ім'я відправника з JSON, враховуючи різні формати сервера:
     * - user_data.name / user_data.first_name + last_name / user_data.username
     * - username (старий WoWonder формат)
     * - sender_name / from_name (прямі поля)
     */
    private fun extractSenderName(data: JSONObject): String {
        // 1. Новий формат: user_data (вкладений JSON об'єкт)
        val userData = data.optJSONObject("user_data")
        if (userData != null) {
            // name — повне ім'я (найнадійніше)
            val name = userData.optString("name", "").trim()
            if (name.isNotEmpty()) return name

            // first_name + last_name
            val firstName = userData.optString("first_name", "").trim()
            val lastName  = userData.optString("last_name", "").trim()
            if (firstName.isNotEmpty()) {
                return if (lastName.isNotEmpty()) "$firstName $lastName" else firstName
            }

            // username як fallback
            val username = userData.optString("username", "").trim()
            if (username.isNotEmpty()) return username
        }

        // 2. Старий формат: username на верхньому рівні
        val topUsername = data.optString("username", "").trim()
        if (topUsername.isNotEmpty()) return topUsername

        // 3. Прямі поля
        val senderName = data.optString("sender_name", "").trim()
        if (senderName.isNotEmpty()) return senderName

        val fromName = data.optString("from_name", "").trim()
        if (fromName.isNotEmpty()) return fromName

        return "WorldMates"
    }

    /**
     * Дешифрує текст повідомлення для сповіщення.
     * Якщо дешифрування не вдається — показує тип медіа або загальний текст.
     */
    private fun decryptNotificationText(
        rawText: String,
        timestamp: Long,
        iv: String?,
        tag: String?,
        cipherVersion: Int?,
        data: JSONObject
    ): String {
        if (rawText.isBlank()) {
            // Немає тексту — перевіряємо чи є медіа
            val media = data.optString("media", "")
            val msgType = data.optString("type", "")
            return when {
                msgType.contains("image", ignoreCase = true) || media.contains("/photos/") -> "\uD83D\uDCF7 Фото"
                msgType.contains("video", ignoreCase = true) || media.contains("/videos/") -> "\uD83C\uDFA5 Відео"
                msgType.contains("audio", ignoreCase = true) || msgType.contains("voice", ignoreCase = true) || media.contains("/sounds/") -> "\uD83C\uDFB5 Аудіо"
                msgType.contains("file", ignoreCase = true) || media.contains("/files/") -> "\uD83D\uDCCE Файл"
                msgType.contains("sticker", ignoreCase = true) -> "\uD83C\uDFAD Стікер"
                media.isNotEmpty() -> "\uD83D\uDCCE Медіа"
                else -> "Нове повідомлення"
            }
        }

        // Якщо є iv та tag — пробуємо дешифрувати (AES-256-GCM або ECB)
        if (timestamp > 0) {
            val decrypted = DecryptionUtility.decryptMessageOrOriginal(
                text = rawText,
                timestamp = timestamp,
                iv = iv,
                tag = tag,
                cipherVersion = cipherVersion
            )
            // Якщо дешифрований текст не порожній та не виглядає як Base64 gibberish
            if (decrypted.isNotEmpty() && isReadableText(decrypted)) {
                return decrypted
            }
        }

        // Якщо rawText виглядає як звичайний текст (не зашифрований) — показуємо
        if (isReadableText(rawText)) {
            return rawText
        }

        // Fallback — не показуємо зашифрований текст
        return "Нове повідомлення"
    }

    /**
     * Перевіряє чи текст виглядає як читабельний (а не Base64/зашифрований).
     */
    private fun isReadableText(text: String): Boolean {
        if (text.isBlank()) return false
        // Base64 зазвичай містить тільки A-Z, a-z, 0-9, +, /, =
        // Якщо текст довгий і не містить пробілів/пунктуації — скоріше за все зашифрований
        val trimmed = text.trim()
        if (trimmed.length > 20 && !trimmed.contains(" ") && !trimmed.contains("\n")) {
            val base64Ratio = trimmed.count { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }.toFloat() / trimmed.length
            if (base64Ratio > 0.95f) return false
        }
        return true
    }

    // ------------------------------------------------------------------
    // Notification builders
    // ------------------------------------------------------------------

    private fun showMessageNotification(
        notifId: Int,
        title: String,
        text: String,
        senderId: Long,
        senderName: String,
        groupId: Long,
        channelId: Long,
        type: ChatType
    ) {
        val intent = Intent(this, MessagesActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            when (type) {
                ChatType.PRIVATE -> {
                    putExtra("recipient_id", senderId)
                    putExtra("recipient_name", senderName)
                }
                ChatType.GROUP -> {
                    putExtra("group_id", groupId)
                    putExtra("is_group", true)
                }
                ChatType.CHANNEL -> {
                    putExtra("channel_id", channelId)
                    putExtra("is_channel", true)
                }
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // MessagingStyle makes the notification look like a chat bubble on Android 7+
        val sender = Person.Builder().setName(title).build()
        val style  = NotificationCompat.MessagingStyle(sender)
            .addMessage(text, System.currentTimeMillis(), sender)

        val notification = NotificationCompat.Builder(this, CHANNEL_MESSAGES_ID)
            .setSmallIcon(R.drawable.ic_notification_message)
            .setStyle(style)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 200, 80, 200))
            .setLights(0xFF0084FF.toInt(), 300, 1000)
            .build()

        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(notifId, notification)
    }

    private fun buildServiceNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_SERVICE_ID)
            .setSmallIcon(R.drawable.ic_notification_service)
            .setContentTitle("WorldMates")
            .setContentText("Очікуємо нові повідомлення…")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    // ------------------------------------------------------------------
    // Notification channels
    // ------------------------------------------------------------------

    private fun createNotificationChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // HIGH — messages with sound + vibration
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val msgChannel = NotificationChannel(
            CHANNEL_MESSAGES_ID,
            CHANNEL_MESSAGES_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Нові повідомлення, групи та канали WorldMates"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 200, 80, 200)
            enableLights(true)
            lightColor = 0xFF0084FF.toInt()
            setSound(
                soundUri,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        nm.createNotificationChannel(msgChannel)

        // MIN — silent persistent service notification
        val svcChannel = NotificationChannel(
            CHANNEL_SERVICE_ID,
            CHANNEL_SERVICE_NAME,
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Фоновий сервіс WorldMates (не відключайте)"
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannel(svcChannel)
    }
}

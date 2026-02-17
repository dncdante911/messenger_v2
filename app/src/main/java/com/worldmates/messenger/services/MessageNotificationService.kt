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
import com.worldmates.messenger.data.Constants
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.ui.messages.MessagesActivity
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

/**
 * Foreground Service that listens to Socket.IO for new messages
 * and shows notifications when the app is in background.
 *
 * This provides push-like notifications without Firebase.
 */
class MessageNotificationService : Service() {

    companion object {
        private const val TAG = "MsgNotifService"
        private const val CHANNEL_ID = "wm_message_notifications"
        private const val CHANNEL_NAME = "Повідомлення"
        private const val FOREGROUND_NOTIFICATION_ID = 9001
        private const val SERVICE_CHANNEL_ID = "wm_service_channel"
        private const val SERVICE_CHANNEL_NAME = "WorldMates Service"

        var isRunning = false
            private set

        // Track which chat is currently open so we don't show notifications for it
        var activeRecipientId: Long = 0
        var activeGroupId: Long = 0

        fun start(context: Context) {
            if (isRunning) return
            val intent = Intent(context, MessageNotificationService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MessageNotificationService::class.java)
            context.stopService(intent)
        }
    }

    private var socket: Socket? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannels()
        startForeground(FOREGROUND_NOTIFICATION_ID, buildForegroundNotification())
        connectSocket()
        Log.d(TAG, "MessageNotificationService started")
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        socket?.disconnect()
        socket?.off()
        socket = null
        Log.d(TAG, "MessageNotificationService stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun connectSocket() {
        val accessToken = UserSession.accessToken
        if (accessToken.isNullOrEmpty()) {
            Log.e(TAG, "No access token, cannot connect")
            stopSelf()
            return
        }

        try {
            val opts = IO.Options().apply {
                forceNew = false
                reconnection = true
                reconnectionAttempts = Int.MAX_VALUE
                reconnectionDelay = 2000
                reconnectionDelayMax = 10000
                timeout = 20000
                transports = arrayOf("websocket", "polling")
                query = "access_token=$accessToken&user_id=${UserSession.userId}"
            }

            socket = IO.socket(Constants.SOCKET_URL, opts)

            socket?.on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "Notification socket connected")
                // Authenticate
                val authData = JSONObject().apply {
                    put("user_id", accessToken)
                }
                socket?.emit(Constants.SOCKET_EVENT_AUTH, authData)
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                Log.d(TAG, "Notification socket disconnected")
            }

            // Listen for private messages
            socket?.on(Constants.SOCKET_EVENT_PRIVATE_MESSAGE) { args ->
                if (args.isNotEmpty() && args[0] is JSONObject) {
                    handleIncomingMessage(args[0] as JSONObject, isGroup = false)
                }
            }

            socket?.on(Constants.SOCKET_EVENT_NEW_MESSAGE) { args ->
                if (args.isNotEmpty() && args[0] is JSONObject) {
                    handleIncomingMessage(args[0] as JSONObject, isGroup = false)
                }
            }

            // Listen for group messages
            socket?.on(Constants.SOCKET_EVENT_GROUP_MESSAGE) { args ->
                if (args.isNotEmpty() && args[0] is JSONObject) {
                    handleIncomingMessage(args[0] as JSONObject, isGroup = true)
                }
            }

            socket?.connect()
        } catch (e: Exception) {
            Log.e(TAG, "Socket connection error", e)
        }
    }

    private fun handleIncomingMessage(data: JSONObject, isGroup: Boolean) {
        try {
            val senderId = data.optLong("from_id", data.optLong("sender_id", 0))
            val senderName = data.optString("sender_name",
                data.optString("from_name", "WorldMates"))
            val text = data.optString("msg",
                data.optString("text",
                    data.optString("message", "")))
            val groupId = data.optLong("group_id", 0)

            // Don't show notification for our own messages
            if (senderId == UserSession.userId) return

            // Don't show notification if the user is viewing this exact chat
            if (!isGroup && senderId == activeRecipientId && activeRecipientId > 0) return
            if (isGroup && groupId == activeGroupId && activeGroupId > 0) return

            // Don't notify for empty messages
            if (text.isBlank()) return

            val title = if (isGroup) {
                val groupName = data.optString("group_name", "Група")
                "$senderName @ $groupName"
            } else {
                senderName
            }

            showMessageNotification(
                title = title,
                message = text,
                senderId = senderId,
                senderName = senderName,
                groupId = groupId,
                isGroup = isGroup
            )

            Log.d(TAG, "Notification shown: $title: ${text.take(30)}")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing message for notification", e)
        }
    }

    private fun showMessageNotification(
        title: String,
        message: String,
        senderId: Long,
        senderName: String,
        groupId: Long,
        isGroup: Boolean
    ) {
        val intent = Intent(this, MessagesActivity::class.java).apply {
            if (isGroup) {
                putExtra("group_id", groupId)
            } else {
                putExtra("recipient_id", senderId)
                putExtra("recipient_name", senderName)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val notificationId = if (isGroup) (groupId + 100000).toInt() else senderId.toInt()

        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 250, 100, 250))
            .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notificationId, notification)
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setContentTitle("WorldMates")
            .setContentText("Очікуємо нові повідомлення")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Message notifications channel (high priority with sound)
        val messageChannel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Нові повідомлення від WorldMates"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 100, 250)
            enableLights(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        nm.createNotificationChannel(messageChannel)

        // Service channel (low priority, silent)
        val serviceChannel = NotificationChannel(
            SERVICE_CHANNEL_ID,
            SERVICE_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Фонова служба WorldMates"
            setSound(null, null)
        }
        nm.createNotificationChannel(serviceChannel)
    }
}

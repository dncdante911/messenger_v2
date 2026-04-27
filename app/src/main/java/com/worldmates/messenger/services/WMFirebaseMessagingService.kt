package com.worldmates.messenger.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.worldmates.messenger.R
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.network.NodeRetrofitClient
import com.worldmates.messenger.ui.calls.IncomingCallActivity
import com.worldmates.messenger.ui.messages.MessagesActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * FCM fallback service — wakes the app when the Socket.IO ForegroundService
 * is killed by the OS (aggressive battery saving on MIUI, OxygenOS, etc.).
 *
 * Delivery strategy:
 *  • App alive (service running) → Socket.IO handles everything, FCM is ignored.
 *  • App killed                 → FCM fires this service, shows a notification,
 *                                  and restarts MessageNotificationService on tap.
 *
 * SETUP REQUIRED (one-time, per developer):
 *  1. Go to https://console.firebase.google.com
 *  2. Create a project (or reuse an existing one).
 *  3. Add Android app: package name = com.worldmates.messenger
 *  4. Download google-services.json → place it in app/ directory.
 *  5. On the server side: download the Firebase Admin SDK service account key
 *     (Project Settings → Service Accounts → Generate new private key)
 *     and put it at api-server-files/nodejs/firebase-service-account.json
 *
 * Server FCM payload for calls should include:
 *   type, from_name, from_id, from_avatar, room_name, call_type
 */
class WMFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "WMFcmService"
        const val CHANNEL_LIVESTREAM_ID    = "wm_livestream"
        private const val LIVESTREAM_NOTIF_ID_BASE = 70000L

        /**
         * Fetch the current FCM token and register it with the WM server.
         * Call this after a successful login.
         */
        fun registerToken(context: Context) {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "Fetching FCM token failed: ${task.exception?.message}")
                    return@addOnCompleteListener
                }
                val token = task.result ?: return@addOnCompleteListener
                Log.d(TAG, "FCM token obtained")
                sendTokenToServer(context, token)
            }
        }

        private fun sendTokenToServer(context: Context, token: String) {
            if (UserSession.accessToken == null) return
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    NodeRetrofitClient.api.registerFcmToken(fcmToken = token)
                    Log.d(TAG, "FCM token registered with server")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to register FCM token: ${e.message}")
                }
            }
        }
    }

    /** Called when Firebase issues a new token (first launch or token refresh). */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed")
        sendTokenToServer(applicationContext, token)
    }

    /**
     * Called when a FCM message arrives while the app is in the foreground,
     * OR when a *data-only* message arrives regardless of app state.
     *
     * Notification messages sent by the server while the app is in the
     * background are handled automatically by the FCM SDK (system tray).
     * We only need to handle data messages or foreground cases here.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "FCM message received: ${message.data}")

        val data        = message.data
        val type        = data["type"] ?: "message"
        val fromName    = data["from_name"]  ?: data["title"]  ?: getString(R.string.app_name)
        val body        = data["body"]       ?: message.notification?.body ?: getString(R.string.notifications_on)
        val fromId      = data["from_id"]?.toLongOrNull() ?: 0L
        val recipientId = data["chat_id"]?.toLongOrNull() ?: 0L

        when (type) {
            "call" -> {
                val roomName   = data["room_name"]   ?: data["roomName"]   ?: ""
                val callType   = data["call_type"]   ?: data["callType"]   ?: "audio"
                val fromAvatar = data["from_avatar"] ?: data["fromAvatar"] ?: ""
                val sdpOffer   = data["sdp_offer"]   ?: data["sdpOffer"]

                // Always restart the service so the socket reconnects for WebRTC signaling.
                // If the server re-sends call:incoming on call:register, that will handle it.
                restartNotificationService()

                // If the Socket.IO service is already connected, skip — it handles the call.
                if (MessageNotificationService.isConnected) {
                    Log.d(TAG, "Socket.IO connected — skipping FCM call notification")
                    return
                }

                // App was killed — show the call notification immediately from FCM data so
                // the user sees the ring screen without waiting for socket reconnection.
                if (fromName.isNotEmpty() && fromId > 0) {
                    Log.d(TAG, "📞 Showing call notification from FCM: $fromName, room=$roomName")
                    ensureCallNotificationChannel()
                    showCallNotificationFromFcm(
                        fromName   = fromName,
                        fromId     = fromId.toInt(),
                        fromAvatar = fromAvatar,
                        callType   = callType,
                        roomName   = roomName,
                        sdpOffer   = sdpOffer
                    )
                }
            }
            "channel_live" -> {
                val channelId = data["channel_id"]?.toLongOrNull() ?: 0L
                val streamId  = data["stream_id"]?.toLongOrNull()  ?: 0L
                Log.d(TAG, "🔴 FCM channel_live: channel=$channelId from=$fromName")
                if (channelId > 0) {
                    ensureLivestreamNotificationChannel()
                    showLivestreamNotification(channelId, streamId, fromName)
                }
            }
            else -> {
                // If Socket.IO service is alive and connected, it already showed a notification.
                if (MessageNotificationService.isConnected) {
                    Log.d(TAG, "Socket.IO connected — skipping FCM message notification")
                    return
                }
                showMessageNotification(fromName, body, fromId, recipientId)
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Ensure the call notification channel exists.
     * MessageNotificationService normally creates it, but may not have run yet.
     */
    private fun ensureCallNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(MessageNotificationService.CHANNEL_CALLS_ID) != null) return

        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val audioAttr = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val channel = NotificationChannel(
            MessageNotificationService.CHANNEL_CALLS_ID,
            MessageNotificationService.CHANNEL_CALLS_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setSound(ringtoneUri, audioAttr)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 1000, 500, 1000)
        }
        nm.createNotificationChannel(channel)
    }

    /**
     * Show a full-screen incoming call notification from FCM payload data.
     * Used when the Socket.IO service is killed and can't receive call:incoming.
     */
    private fun showCallNotificationFromFcm(
        fromName:   String,
        fromId:     Int,
        fromAvatar: String,
        callType:   String,
        roomName:   String,
        sdpOffer:   String?
    ) {
        val callIntent = IncomingCallActivity.createIntent(
            context    = this,
            fromId     = fromId,
            fromName   = fromName,
            fromAvatar = fromAvatar,
            callType   = callType,
            roomName   = roomName,
            sdpOffer   = sdpOffer
        )
        val fullScreenPi = PendingIntent.getActivity(
            this, fromId, callIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val callTypeText = if (callType == "video")
            getString(R.string.video_call) else getString(R.string.incoming_call)

        val notification = NotificationCompat.Builder(this, MessageNotificationService.CHANNEL_CALLS_ID)
            .setSmallIcon(R.drawable.ic_notification_service)
            .setContentTitle(fromName)
            .setContentText(callTypeText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPi, true)
            .setAutoCancel(true)
            .setOngoing(true)
            .build()

        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(MessageNotificationService.CALL_NOTIF_ID, notification)
    }

    private fun showMessageNotification(
        senderName: String,
        body: String,
        fromId: Long,
        recipientId: Long,
    ) {
        val intent = Intent(this, MessagesActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("recipient_id", if (recipientId != 0L) recipientId else fromId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            fromId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, MessageNotificationService.CHANNEL_MESSAGES_ID)
            .setSmallIcon(R.drawable.ic_notification_message)
            .setContentTitle(senderName)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(fromId.toInt(), notification)

        // Also restart the background service so future messages come via Socket.IO
        restartNotificationService()
    }

    private fun ensureLivestreamNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_LIVESTREAM_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_LIVESTREAM_ID,
            getString(R.string.notif_channel_live_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
            setSound(
                android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .build()
            )
        }
        nm.createNotificationChannel(channel)
    }

    private fun showLivestreamNotification(channelId: Long, streamId: Long, hostName: String) {
        val intent = Intent(this, com.worldmates.messenger.ui.channels.ChannelDetailsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("channel_id", channelId)
            if (streamId > 0) putExtra("stream_id", streamId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            (channelId + 90000L).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val body = getString(R.string.notif_channel_live_body, hostName)
        val notification = NotificationCompat.Builder(this, CHANNEL_LIVESTREAM_ID)
            .setSmallIcon(R.drawable.ic_notification_service)
            .setContentTitle(hostName)
            .setContentText(body)
            .setSubText(getString(R.string.notif_channel_live_tap_hint))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify((LIVESTREAM_NOTIF_ID_BASE + channelId).toInt(), notification)
    }

    private fun restartNotificationService() {
        if (UserSession.accessToken == null) return
        val intent = Intent(this, MessageNotificationService::class.java)
        try {
            startForegroundService(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not restart MessageNotificationService: ${e.message}")
        }
    }
}

package com.worldmates.messenger.services

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.worldmates.messenger.R
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.network.NodeRetrofitClient
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
 */
class WMFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "WMFcmService"

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

        // If Socket.IO service is alive and connected, it already showed a notification.
        if (MessageNotificationService.isConnected) {
            Log.d(TAG, "Socket.IO connected — skipping FCM notification")
            return
        }

        val data        = message.data
        val type        = data["type"] ?: "message"
        val fromName    = data["from_name"]  ?: data["title"]  ?: getString(R.string.app_name)
        val body        = data["body"]       ?: message.notification?.body ?: getString(R.string.notifications_on)
        val fromId      = data["from_id"]?.toLongOrNull() ?: 0L
        val recipientId = data["chat_id"]?.toLongOrNull() ?: 0L

        when (type) {
            "call" -> {
                // Incoming call while app is killed — the calls subsystem handles ringing.
                // We just ensure the service restarts so WebRTC can connect.
                restartNotificationService()
            }
            else -> {
                showMessageNotification(fromName, body, fromId, recipientId)
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

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

package com.worldmates.messenger.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.worldmates.messenger.R
import com.worldmates.messenger.ui.messages.MessagesActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
        private const val CHANNEL_ID = "worldmates_messages"
        private const val CHANNEL_NAME = "WorldMates Messages"
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "Message received from: ${remoteMessage.from}")

        // Отримуємо дані з повідомлення
        val title = remoteMessage.data["title"] ?: "WorldMates"
        val body = remoteMessage.data["body"] ?: ""
        val senderId = remoteMessage.data["sender_id"]?.toLongOrNull() ?: 0L
        val senderName = remoteMessage.data["sender_name"] ?: "Unknown"

        // Показуємо сповіщення
        showNotification(
            title = senderName,
            message = body,
            senderId = senderId,
            senderName = senderName
        )
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "New FCM token: $token")
        // Надішліть token на сервер для зберігання
        sendTokenToServer(token)
    }

    private fun showNotification(
        title: String,
        message: String,
        senderId: Long,
        senderName: String
    ) {
        createNotificationChannel()

        val intent = Intent(this, MessagesActivity::class.java).apply {
            putExtra("recipient_id", senderId)
            putExtra("recipient_name", senderName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            senderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(senderId.toInt(), notification)
    }

    private fun createNotificationChannel() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Повідомлення від WorldMates"
            enableVibration(true)
            enableLights(true)
        }

        notificationManager.createNotificationChannel(channel)
    }

    private fun sendTokenToServer(token: String) {
        val accessToken = com.worldmates.messenger.data.UserSession.accessToken
        if (accessToken.isNullOrEmpty()) {
            Log.w(TAG, "Cannot send FCM token: no access token")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = com.worldmates.messenger.network.RetrofitClient.apiService.updateFcmToken(
                    accessToken = accessToken,
                    fcmToken = token
                )
                if (response.apiStatus == 200) {
                    Log.d(TAG, "FCM token sent to server successfully")
                } else {
                    Log.w(TAG, "FCM token upload failed: ${response.errorMessage}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending FCM token: ${e.message}")
            }
        }
    }
}
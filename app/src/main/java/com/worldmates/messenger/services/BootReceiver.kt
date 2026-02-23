package com.worldmates.messenger.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.worldmates.messenger.data.UserSession

/**
 * BroadcastReceiver, який запускає MessageNotificationService
 * після перезавантаження пристрою або оновлення додатку.
 *
 * Працює без Firebase — забезпечує що Socket.IO сервіс
 * завжди перезапускається після ребуту.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON") {

            Log.d(TAG, "Boot/update received: $action")

            if (UserSession.isLoggedIn) {
                Log.d(TAG, "User is logged in — starting notification service")
                MessageNotificationService.start(context)
                NotificationKeepAliveManager.ensureServiceAlive(context)
            } else {
                Log.d(TAG, "User not logged in — skipping service start")
            }
        }
    }
}

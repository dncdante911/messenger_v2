package com.worldmates.messenger.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.worldmates.messenger.data.UserSession

/**
 * BroadcastReceiver для перезапуску MessageNotificationService
 * через AlarmManager. Спрацьовує якщо сервіс був вбитий системою
 * або користувачем (свайп з "Недавніх").
 */
class ServiceRestartReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SvcRestartReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, "Restart alarm fired, isRunning=${MessageNotificationService.isRunning}")

        if (!UserSession.isLoggedIn) {
            Log.d(TAG, "User not logged in — skipping restart")
            return
        }

        if (!MessageNotificationService.isRunning) {
            Log.d(TAG, "Service is NOT running — restarting")
            MessageNotificationService.start(context)
        } else {
            Log.d(TAG, "Service is already running — no action needed")
        }
    }
}

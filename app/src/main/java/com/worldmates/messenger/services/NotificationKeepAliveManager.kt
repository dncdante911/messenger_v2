package com.worldmates.messenger.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.worldmates.messenger.data.UserSession

/**
 * Менеджер, який координує всі механізми виживання MessageNotificationService.
 *
 * Без Firebase для доставки повідомлень при вбитому додатку використовуються:
 * 1. ForegroundService з START_STICKY — ОС перезапускає при вбивстві
 * 2. onTaskRemoved() — перезапуск при свайпі з "Недавніх"
 * 3. AlarmManager — страховочний таймер кожні 5 хвилин
 * 4. BootReceiver — запуск після перезавантаження пристрою
 * 5. WorkManager (ServiceWatchdogWorker) — перевірка кожні 15 хвилин
 * 6. Запит на вимкнення оптимізації батареї — щоб система не вбивала сервіс
 */
object NotificationKeepAliveManager {

    private const val TAG = "KeepAliveManager"

    /**
     * Ініціалізувати всі механізми виживання.
     * Викликається після успішного логіну та при старті додатку.
     */
    fun initialize(context: Context) {
        if (!UserSession.isLoggedIn) {
            Log.d(TAG, "User not logged in — skipping initialization")
            return
        }

        Log.d(TAG, "Initializing keep-alive mechanisms")

        // 1. Запустити ForegroundService
        MessageNotificationService.start(context)

        // 2. Запланувати WorkManager watchdog
        ServiceWatchdogWorker.schedule(context)

        Log.d(TAG, "All keep-alive mechanisms initialized")
    }

    /**
     * Зупинити всі механізми (при логауті).
     */
    fun shutdown(context: Context) {
        Log.d(TAG, "Shutting down all keep-alive mechanisms")

        MessageNotificationService.stop(context)
        ServiceWatchdogWorker.cancel(context)
    }

    /**
     * Перевірити та перезапустити сервіс якщо потрібно.
     * Викликається з BootReceiver та ServiceRestartReceiver.
     */
    fun ensureServiceAlive(context: Context) {
        if (!UserSession.isLoggedIn) return

        if (!MessageNotificationService.isRunning) {
            Log.w(TAG, "Service not running — starting")
            MessageNotificationService.start(context)
        }

        // Переконатися що watchdog запланований
        ServiceWatchdogWorker.schedule(context)
    }

    /**
     * Перевіряє чи додаток виключений з оптимізації батареї.
     */
    fun isBatteryOptimizationDisabled(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Відкриває системні налаштування для вимкнення оптимізації батареї.
     * Повертає Intent який потрібно запустити з Activity.
     */
    fun getBatteryOptimizationIntent(context: Context): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }
}

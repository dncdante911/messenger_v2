package com.worldmates.messenger.services

import android.content.Context
import android.util.Log
import androidx.work.*
import com.worldmates.messenger.data.UserSession
import java.util.concurrent.TimeUnit

/**
 * WorkManager Worker — "сторожовий пес" для MessageNotificationService.
 *
 * Виконується кожні 15 хвилин (мінімальний інтервал WorkManager).
 * Перевіряє чи сервіс живий і перезапускає його якщо потрібно.
 *
 * Це safety net на випадок якщо:
 * - AlarmManager не спрацював (Doze restrictions)
 * - Система вбила сервіс і START_STICKY не допоміг
 * - Пристрій тривалий час був у глибокому сні
 */
class ServiceWatchdogWorker(
    appContext: Context,
    params: WorkerParameters
) : Worker(appContext, params) {

    companion object {
        private const val TAG = "SvcWatchdog"
        private const val WORK_NAME = "wm_service_watchdog"

        /**
         * Запланувати періодичну перевірку сервісу.
         * Викликається при логіні та при старті додатку.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<ServiceWatchdogWorker>(
                15, TimeUnit.MINUTES  // мінімальний інтервал WorkManager
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )

            Log.d(TAG, "Watchdog scheduled (every 15 min)")
        }

        /**
         * Скасувати watchdog (при логауті).
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Watchdog cancelled")
        }
    }

    override fun doWork(): Result {
        Log.d(TAG, "Watchdog check: isLoggedIn=${UserSession.isLoggedIn}, isRunning=${MessageNotificationService.isRunning}")

        if (!UserSession.isLoggedIn) {
            Log.d(TAG, "User not logged in — stopping watchdog cycle")
            return Result.success()
        }

        if (!MessageNotificationService.isRunning) {
            Log.w(TAG, "Service is NOT running — restarting!")
            try {
                MessageNotificationService.start(applicationContext)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restart service", e)
                return Result.retry()
            }
        } else {
            Log.d(TAG, "Service is alive — all good")
        }

        return Result.success()
    }
}

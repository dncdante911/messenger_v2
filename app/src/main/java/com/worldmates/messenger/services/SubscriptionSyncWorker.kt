package com.worldmates.messenger.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.worldmates.messenger.R
import com.worldmates.messenger.data.AccountManager
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.network.RetrofitClient
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * SubscriptionSyncWorker — фоновий воркер синхронізації статусу підписки.
 *
 * Запускається раз на 24 години (або при старті додатку).
 * Викликає getUserData() та оновлює UserSession.updateProStatus().
 * Якщо підписка завершилась — показує gentle notification.
 */
class SubscriptionSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val token = UserSession.accessToken ?: return Result.success()
        return try {
            val api = RetrofitClient.getApi()
            val response = api.getUserData(accessToken = token)
            if (response.apiStatus == 200) {
                val user = response.userData ?: return Result.success()
                val wasProBefore = UserSession.isPro > 0
                val expiresMs = parseExpiresAt(user.proExpiresAt)
                UserSession.updateProStatus(user.isPro, user.proType, expiresMs)
                // Оновлюємо статус у Room для поточного акаунту
                AccountManager.refreshCurrentAccountProStatus(user.isPro)
                // Підписка щойно завершилась — сповіщаємо
                if (wasProBefore && user.isPro == 0) {
                    showExpiredNotification()
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun parseExpiresAt(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        return try {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(raw)?.time ?: 0L
        } catch (_: Exception) { 0L }
    }

    private fun showExpiredNotification() {
        val channelId = "subscription_channel"
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Підписка",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_notification_message)
            .setContentTitle("WorldMates PRO")
            .setContentText(applicationContext.getString(R.string.premium_expired))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val WORK_NAME = "subscription_sync"
        private const val NOTIFICATION_ID = 7777

        /**
         * Зареєструвати або оновити воркер.
         * Використовуй ExistingPeriodicWorkPolicy.KEEP щоб не перезапускати щоразу.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SubscriptionSyncWorker>(
                repeatInterval = 24,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /** Одноразова негайна синхронізація (при старті додатку або поверненні з оплати). */
        fun runOnce(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SubscriptionSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }
}

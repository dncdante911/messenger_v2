package com.worldmates.messenger.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.worldmates.messenger.R
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.network.NodeRetrofitClient
import java.util.concurrent.TimeUnit

/**
 * SubscriptionSyncWorker — фоновий воркер синхронізації статусу підписки.
 *
 * Запускається раз на 6 годин (і одноразово після повернення з оплати).
 * Звертається до Node.js /api/node/subscription/status і оновлює UserSession.
 * Якщо підписка завершилась — показує сповіщення.
 */
class SubscriptionSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        if (!UserSession.isLoggedIn) return Result.success()

        return try {
            val api      = NodeRetrofitClient.subscriptionApi
            val response = api.getStatus()

            if (response.apiStatus == 200) {
                val wasProBefore = UserSession.isPro > 0
                UserSession.updateProStatus(
                    isPro        = response.isPro,
                    proType      = response.proType,
                    proExpiresAt = response.proTime * 1000L   // server returns Unix seconds
                )
                // Підписка щойно закінчилась — повідомляємо
                if (wasProBefore && response.isPro == 0) {
                    showExpiredNotification()
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun showExpiredNotification() {
        val channelId = "subscription_channel"
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Підписка", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_notification_message)
            .setContentTitle("WallyMates PRO")
            .setContentText(applicationContext.getString(R.string.premium_expired))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val WORK_NAME      = "subscription_sync"
        private const val NOTIFICATION_ID = 7777

        /** Зареєструвати або оновити воркер (раз на 6 годин). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SubscriptionSyncWorker>(
                repeatInterval         = 6,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /** Одноразова негайна синхронізація (при старті або після оплати). */
        fun runOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<SubscriptionSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}

package com.worldmates.messenger.services

import android.content.Context
import androidx.work.*
import com.worldmates.messenger.data.local.AppDatabase
import java.util.concurrent.TimeUnit

/**
 * SecretChatCleanupWorker - WorkManager воркер для видалення прострочених секретних повідомлень.
 *
 * Запускається кожні 15 хвилин у фоновому режимі.
 * Видаляє повідомлення, у яких destroyAt <= поточного часу.
 *
 * Реєстрація: WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(...)
 * Викликається з WMApplication або ChatsActivity.
 */
class SecretChatCleanupWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val db = AppDatabase.getInstance(applicationContext)
            val nowMs = System.currentTimeMillis()
            db.messageDao().deleteExpiredSecretMessages(nowMs)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "secret_chat_cleanup"

        /**
         * Зареєструвати або оновити фоновий воркер очищення.
         * Використовуй ExistingPeriodicWorkPolicy.KEEP, щоб не перезапускати кожного разу.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SecretChatCleanupWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(false)
                        .build()
                )
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /**
         * Запустити очищення негайно (одноразово) - наприклад, при відкритті чату.
         */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SecretChatCleanupWorker>().build()
            WorkManager.getInstance(context.applicationContext).enqueue(request)
        }
    }
}

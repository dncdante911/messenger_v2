package com.worldmates.messenger.services

import android.content.Context
import androidx.work.*
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.data.backup.CloudBackupManager
import com.worldmates.messenger.data.model.CloudBackupSettings
import java.util.concurrent.TimeUnit

/**
 * BackupWorker — фоновый воркер автоматического резервного копирования.
 *
 * Запускается по расписанию (ежедневно / еженедельно / ежемесячно).
 * Создаёт бэкап через CloudBackupManager и загружает его в облако.
 */
class BackupWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        if (!UserSession.isLoggedIn) return Result.success()

        return try {
            val manager = CloudBackupManager.getInstance(applicationContext)
            val result = manager.createBackup(uploadToCloud = true)
            if (result.isSuccess) Result.success() else Result.retry()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "WorldMates_AutoBackup"

        /**
         * Запланировать периодический автобэкап согласно частоте.
         * Если frequency == NEVER — отменяет задачу.
         */
        fun schedule(context: Context, frequency: CloudBackupSettings.BackupFrequency) {
            if (frequency == CloudBackupSettings.BackupFrequency.NEVER || frequency.hours <= 0) {
                cancel(context)
                return
            }

            val request = PeriodicWorkRequestBuilder<BackupWorker>(
                repeatInterval = frequency.hours.toLong(),
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        /** Отменить автобэкап. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}

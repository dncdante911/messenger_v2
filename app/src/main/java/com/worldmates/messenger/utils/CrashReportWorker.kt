package com.worldmates.messenger.utils

import android.content.Context
import android.util.Log
import androidx.work.*
import com.worldmates.messenger.network.NodeRetrofitClient
import java.io.File
import java.util.concurrent.TimeUnit

class CrashReportWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG       = "CrashReportWorker"
        private const val CRASH_DIR = "crash_reports"
        private const val WORK_NAME = "crash_report_send"

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<CrashReportWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }

    override suspend fun doWork(): Result {
        val crashDir = File(applicationContext.filesDir, CRASH_DIR)
        if (!crashDir.exists()) return Result.success()

        val files = crashDir.listFiles { f -> f.name.endsWith(".log") }
            ?.takeIf { it.isNotEmpty() }
            ?: return Result.success()

        Log.d(TAG, "Sending ${files.size} pending crash report(s)")
        var anyFailed = false

        for (file in files) {
            if (!trySend(file)) anyFailed = true
        }

        return if (anyFailed) Result.retry() else Result.success()
    }

    private suspend fun trySend(file: File): Boolean {
        return try {
            val content  = file.readText(Charsets.UTF_8)
            val response = NodeRetrofitClient.api.sendCrashReport(
                report   = content,
                filename = file.name,
                secret   = CrashReporter.CRASH_SECRET
            )
            if (response.apiStatus == 200) {
                file.delete()
                Log.d(TAG, "Sent & deleted: ${file.name}")
                true
            } else {
                Log.w(TAG, "Server returned ${response.apiStatus} for ${file.name}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send ${file.name}: ${e.message}")
            false
        }
    }
}

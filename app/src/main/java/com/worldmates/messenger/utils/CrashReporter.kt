package com.worldmates.messenger.utils

import android.content.Context
import android.os.Build
import android.util.Log
import com.worldmates.messenger.BuildConfig
import com.worldmates.messenger.data.UserSession
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Перехоплює краші застосунку, зберігає звіт у файл і при наступному
 * запуску відправляє на сервер.
 *
 * Використання:
 *   1. CrashReporter.install(context)  — у WMApplication.onCreate()
 *   2. CrashReporter.sendPendingReports() — там же, відразу після install()
 */
object CrashReporter : Thread.UncaughtExceptionHandler {

    private const val TAG             = "CrashReporter"
    private const val CRASH_DIR       = "crash_reports"
    private const val MAX_REPORT_BYTES = 200_000   // 200 KB — stay under Express body limit
    internal const val CRASH_SECRET   = "wm_crash_rpt_2025"

    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private lateinit var appContext: Context

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Встановлює власний UncaughtExceptionHandler.
     * Викликати якомога раніше — у Application.onCreate().
     */
    fun install(context: Context) {
        appContext = context.applicationContext
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
        Log.d(TAG, "CrashReporter installed")
    }

    /**
     * Schedules a WorkManager job to deliver all pending crash reports.
     * WorkManager guarantees execution once the device is online, with
     * automatic retry — even if the app process is killed before the
     * first attempt completes.
     */
    fun sendPendingReports() {
        val crashDir = File(appContext.filesDir, CRASH_DIR)
        if (!crashDir.exists()) return
        if (crashDir.listFiles { f -> f.name.endsWith(".log") }.isNullOrEmpty()) return
        CrashReportWorker.schedule(appContext)
    }

    // ─── UncaughtExceptionHandler ────────────────────────────────────────────

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            saveCrashToFile(thread, throwable)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save crash report", e)
        }
        // Передаємо оригінальному обробнику → Android покаже стандартний діалог краша
        defaultHandler?.uncaughtException(thread, throwable)
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private fun saveCrashToFile(thread: Thread, throwable: Throwable) {
        val now     = System.currentTimeMillis()
        val dateFmt = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date(now))
        val userId  = runCatching { UserSession.userId }.getOrDefault(0L)

        val report = buildString {
            appendLine("=== WallyMates Crash Report ===")
            appendLine("Time:        $dateFmt")
            appendLine("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android:     ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device:      ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Thread:      ${thread.name}")
            appendLine("User ID:     ${if (userId > 0) userId else "not logged in"}")
            appendLine()
            appendLine("=== Stack Trace ===")
            appendLine(throwable.stackTraceToString())

            // Вся ланцюжок причин (до 5 рівнів щоб не зациклитися)
            var cause = throwable.cause
            var depth = 0
            while (cause != null && depth < 5) {
                appendLine()
                appendLine("=== Caused by ===")
                appendLine(cause.stackTraceToString())
                cause = cause.cause
                depth++
            }
        }

        val crashDir = File(appContext.filesDir, CRASH_DIR)
        crashDir.mkdirs()

        val bytes = report.toByteArray(Charsets.UTF_8)
        val finalReport = if (bytes.size > MAX_REPORT_BYTES) {
            // Truncate from the end to stay under the server body limit
            String(bytes, 0, MAX_REPORT_BYTES, Charsets.UTF_8) +
                "\n\n[TRUNCATED — original size: ${bytes.size} bytes]"
        } else {
            report
        }

        val filename = "crash_${userId}_$dateFmt.log"
        File(crashDir, filename).writeText(finalReport, Charsets.UTF_8)
        Log.d(TAG, "Crash report saved: $filename (${finalReport.length} chars)")
    }
}

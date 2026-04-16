package com.worldmates.messenger.utils

import android.content.Context
import android.os.Build
import android.util.Log
import com.worldmates.messenger.BuildConfig
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.network.NodeRetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

    private const val TAG          = "CrashReporter"
    private const val CRASH_DIR    = "crash_reports"
    // Простий секрет щоб не спамили ендпоінт зовні
    internal const val CRASH_SECRET = "wm_crash_rpt_2025"

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
     * Перевіряє наявність збережених звітів і відправляє їх на сервер
     * у фоновому потоці. Видаляє файл після успішної відправки.
     */
    fun sendPendingReports() {
        val crashDir = File(appContext.filesDir, CRASH_DIR)
        if (!crashDir.exists()) return
        val files = crashDir.listFiles { f -> f.name.endsWith(".log") }
            ?.takeIf { it.isNotEmpty() } ?: return

        Log.d(TAG, "Found ${files.size} pending crash report(s)")

        CoroutineScope(Dispatchers.IO).launch {
            for (file in files) {
                trySendReport(file)
            }
        }
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
            appendLine("=== WorldMates Crash Report ===")
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

        val filename = "crash_${userId}_$dateFmt.log"
        File(crashDir, filename).writeText(report, Charsets.UTF_8)
        Log.d(TAG, "Crash report saved: $filename")
    }

    private suspend fun trySendReport(file: File) {
        try {
            val content  = file.readText(Charsets.UTF_8)
            val response = NodeRetrofitClient.api.sendCrashReport(
                report   = content,
                filename = file.name,
                secret   = CRASH_SECRET
            )
            if (response.apiStatus == 200) {
                file.delete()
                Log.d(TAG, "Crash report sent & deleted: ${file.name}")
            } else {
                Log.w(TAG, "Server returned ${response.apiStatus} for ${file.name}")
            }
        } catch (e: Exception) {
            // Не вдалося відправити — залишимо файл, спробуємо при наступному запуску
            Log.e(TAG, "Failed to send ${file.name}: ${e.message}")
        }
    }
}

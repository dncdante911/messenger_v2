package com.worldmates.messenger.data

import android.content.Context
import android.content.SharedPreferences
import com.worldmates.messenger.R
import com.worldmates.messenger.WMApplication

/**
 * SecretChatManager - менеджер секретних чатів з самознищенням.
 *
 * Варіанти таймерів (як в Telegram): OFF, 1s, 5s, 30s, 1m, 5m, 1h, 1d, 1w
 * Таймер зберігається per-account, per-chat у SharedPreferences.
 * Видалення: WorkManager кожні 15 хв (SecretChatCleanupWorker) + Node.js auto-cleanup.
 */
object SecretChatManager {

    // Варіанти таймеру (в секундах). 0L = вимкнено.
    val TIMER_OPTIONS: List<Long> = listOf(
        0L,
        1L,
        5L,
        30L,
        60L,
        300L,
        3_600L,
        86_400L,
        604_800L
    )

    /** Повна локалізована назва таймеру (використовує системний Context для i18n). */
    fun timerLabel(seconds: Long): String {
        val ctx = WMApplication.instance
        return when (seconds) {
            0L       -> ctx.getString(R.string.timer_off)
            1L       -> ctx.getString(R.string.timer_1s)
            5L       -> ctx.getString(R.string.timer_5s)
            30L      -> ctx.getString(R.string.timer_30s)
            60L      -> ctx.getString(R.string.timer_1m)
            300L     -> ctx.getString(R.string.timer_5m)
            3_600L   -> ctx.getString(R.string.timer_1h)
            86_400L  -> ctx.getString(R.string.timer_1d)
            604_800L -> ctx.getString(R.string.timer_1w)
            else     -> ctx.getString(R.string.timer_custom_s, seconds.toInt())
        }
    }

    /** Скорочена локалізована назва для бейджа в хедері чату. */
    fun timerLabelShort(seconds: Long): String {
        val ctx = WMApplication.instance
        return when (seconds) {
            0L       -> ctx.getString(R.string.timer_off_short)
            1L       -> ctx.getString(R.string.timer_1s_short)
            5L       -> ctx.getString(R.string.timer_5s_short)
            30L      -> ctx.getString(R.string.timer_30s_short)
            60L      -> ctx.getString(R.string.timer_1m_short)
            300L     -> ctx.getString(R.string.timer_5m_short)
            3_600L   -> ctx.getString(R.string.timer_1h_short)
            86_400L  -> ctx.getString(R.string.timer_1d_short)
            604_800L -> ctx.getString(R.string.timer_1w_short)
            else     -> ctx.getString(R.string.timer_custom_s, seconds.toInt())
        }
    }

    private fun prefs(): SharedPreferences =
        WMApplication.instance.getSharedPreferences(
            "secret_chat_timers_${UserSession.userId}",
            Context.MODE_PRIVATE
        )

    fun getTimer(chatId: Long, chatType: String): Long =
        prefs().getLong("${chatType}_$chatId", 0L)

    fun setTimer(chatId: Long, chatType: String, seconds: Long) {
        prefs().edit().putLong("${chatType}_$chatId", seconds).apply()
    }

    fun isSecretChat(chatId: Long, chatType: String): Boolean =
        getTimer(chatId, chatType) > 0L

    fun computeDestroyAt(chatId: Long, chatType: String): Long? {
        val seconds = getTimer(chatId, chatType)
        if (seconds <= 0L) return null
        return System.currentTimeMillis() + seconds * 1_000L
    }

    fun clearTimer(chatId: Long, chatType: String) {
        prefs().edit().remove("${chatType}_$chatId").apply()
    }
}

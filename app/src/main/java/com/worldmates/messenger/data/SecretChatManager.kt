package com.worldmates.messenger.data

import android.content.Context
import android.content.SharedPreferences
import com.worldmates.messenger.WMApplication

/**
 * SecretChatManager - менеджер секретних чатів з самознищенням.
 *
 * Функціонал (аналог Telegram):
 * - Встановлення таймеру самознищення для кожного чату окремо
 * - Таймер визначає, через скільки секунд повідомлення буде видалено
 * - Таймер зберігається локально в SharedPreferences (per-account, per-chat)
 * - Видалення відбувається через SecretChatCleanupWorker (WorkManager, кожні 15 хв)
 *   + одразу після закриття чату (in MessagesViewModel)
 *
 * Варіанти таймерів (як в Telegram):
 *   OFF, 1s, 5s, 30s, 1m, 5m, 1h, 1d, 1w
 */
object SecretChatManager {

    // Варіанти таймеру (в секундах). 0L = вимкнено.
    val TIMER_OPTIONS: List<Long> = listOf(
        0L,          // Вимкнено
        1L,          // 1 секунда
        5L,          // 5 секунд
        30L,         // 30 секунд
        60L,         // 1 хвилина
        300L,        // 5 хвилин
        3_600L,      // 1 година
        86_400L,     // 1 день
        604_800L     // 1 тиждень
    )

    fun timerLabel(seconds: Long): String = when (seconds) {
        0L -> "Вимкнено"
        1L -> "1 секунда"
        5L -> "5 секунд"
        30L -> "30 секунд"
        60L -> "1 хвилина"
        300L -> "5 хвилин"
        3_600L -> "1 година"
        86_400L -> "1 день"
        604_800L -> "1 тиждень"
        else -> "${seconds}с"
    }

    fun timerLabelShort(seconds: Long): String = when (seconds) {
        0L -> "Вимк."
        1L -> "1с"
        5L -> "5с"
        30L -> "30с"
        60L -> "1хв"
        300L -> "5хв"
        3_600L -> "1г"
        86_400L -> "1д"
        604_800L -> "1т"
        else -> "${seconds}с"
    }

    private fun prefs(): SharedPreferences =
        WMApplication.instance.getSharedPreferences(
            "secret_chat_timers_${UserSession.userId}",
            Context.MODE_PRIVATE
        )

    /**
     * Отримати поточний таймер для чату (у секундах, 0 = вимкнено).
     */
    fun getTimer(chatId: Long, chatType: String): Long {
        return prefs().getLong("${chatType}_$chatId", 0L)
    }

    /**
     * Встановити таймер для чату.
     * @param seconds 0 = вимкнено, > 0 = кількість секунд до видалення
     */
    fun setTimer(chatId: Long, chatType: String, seconds: Long) {
        prefs().edit().putLong("${chatType}_$chatId", seconds).apply()
    }

    /**
     * Чи є активний таймер для цього чату.
     */
    fun isSecretChat(chatId: Long, chatType: String): Boolean {
        return getTimer(chatId, chatType) > 0L
    }

    /**
     * Обчислити час знищення нового повідомлення.
     * @return Unix-timestamp у мілісекундах або null якщо таймер вимкнено
     */
    fun computeDestroyAt(chatId: Long, chatType: String): Long? {
        val seconds = getTimer(chatId, chatType)
        if (seconds <= 0L) return null
        return System.currentTimeMillis() + seconds * 1_000L
    }

    /**
     * Очистити таймер при виході з секретного режиму.
     */
    fun clearTimer(chatId: Long, chatType: String) {
        prefs().edit().remove("${chatType}_$chatId").apply()
    }
}

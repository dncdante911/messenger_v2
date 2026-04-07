package com.worldmates.messenger.utils

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory кеш транскрипцій голосових повідомлень.
 *
 * Зберігає результати Whisper-розпізнавання на час сесії:
 * при прокручуванні листа назад транскрипція вже є в кеші — зайвий API-запит не виконується.
 *
 * Використання:
 *   VoiceTranscriptCache[message.id] = "текст"
 *   val cached = VoiceTranscriptCache[message.id]
 */
object VoiceTranscriptCache {

    // messageId → транскрипт (thread-safe)
    private val cache = ConcurrentHashMap<Long, String>()

    operator fun get(messageId: Long): String? = cache[messageId]

    operator fun set(messageId: Long, transcript: String) {
        cache[messageId] = transcript
    }

    fun contains(messageId: Long): Boolean = cache.containsKey(messageId)

    /** Очистити кеш (наприклад, при виході з акаунту) */
    fun clear() = cache.clear()
}

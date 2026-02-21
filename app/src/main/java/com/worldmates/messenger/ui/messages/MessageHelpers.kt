package com.worldmates.messenger.ui.messages

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

fun formatTime(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp * 1000))
}

/**
 * Определяет тип медиа по URL или явному типу сообщения.
 * Если message.type указан явно (не "text"), используем его.
 * Иначе определяем по расширению файла или пути в URL.
 */
fun detectMediaType(url: String?, messageType: String?): String? {
    // Если URL пустой, используем тип сообщения
    if (url.isNullOrEmpty()) {
        Log.d("detectMediaType", "URL пустий, тип повідомлення: $messageType")
        return if (messageType?.isNotEmpty() == true && messageType != "text") messageType else "text"
    }

    val lowerUrl = url.lowercase()
    Log.d("detectMediaType", "Аналіз URL: $lowerUrl, тип повідомлення: $messageType")

    // Спочатку перевіряємо за шляхом (найнадійніше)
    val typeByPath = when {
        lowerUrl.contains("/upload/photos/") || lowerUrl.contains("/upload/images/") -> "image"
        lowerUrl.contains("/upload/videos/") -> "video"
        lowerUrl.contains("/upload/sounds/") || lowerUrl.contains("/upload/audio/") -> "audio"
        lowerUrl.contains("/upload/files/") -> "file"
        else -> null
    }

    if (typeByPath != null) {
        Log.d("detectMediaType", "Визначено за шляхом: $typeByPath")
        return typeByPath
    }

    // Потім перевіряємо за розширенням
    val typeByExtension = when {
        // Анімовані стікери
        lowerUrl.endsWith(".json") || lowerUrl.endsWith(".lottie") ||
                lowerUrl.endsWith(".tgs") || lowerUrl.startsWith("lottie://") ||
                lowerUrl.contains("/stickers/") -> "sticker"

        // Изображения
        lowerUrl.endsWith(".jpg") || lowerUrl.endsWith(".jpeg") ||
                lowerUrl.endsWith(".png") || lowerUrl.endsWith(".gif") ||
                lowerUrl.endsWith(".webp") || lowerUrl.endsWith(".bmp") -> "image"

        // Видео
        lowerUrl.endsWith(".mp4") || lowerUrl.endsWith(".webm") ||
                lowerUrl.endsWith(".mov") || lowerUrl.endsWith(".avi") ||
                lowerUrl.endsWith(".mkv") || lowerUrl.endsWith(".3gp") -> "video"

        // Аудио/Голос
        lowerUrl.endsWith(".mp3") || lowerUrl.endsWith(".wav") ||
                lowerUrl.endsWith(".ogg") || lowerUrl.endsWith(".m4a") ||
                lowerUrl.endsWith(".aac") || lowerUrl.endsWith(".opus") -> "audio"

        // Файлы
        lowerUrl.endsWith(".pdf") || lowerUrl.endsWith(".doc") ||
                lowerUrl.endsWith(".docx") || lowerUrl.endsWith(".xls") ||
                lowerUrl.endsWith(".xlsx") || lowerUrl.endsWith(".zip") ||
                lowerUrl.endsWith(".rar") || lowerUrl.endsWith(".txt") -> "file"

        else -> null
    }

    if (typeByExtension != null) {
        Log.d("detectMediaType", "Визначено за розширенням: $typeByExtension")
        return typeByExtension
    }

    // Якщо нічого не знайшли, використовуємо messageType
    if (messageType?.isNotEmpty() == true && messageType != "text") {
        Log.d("detectMediaType", "Використовую тип повідомлення: $messageType")
        return messageType
    }

    Log.d("detectMediaType", "Не вдалося визначити тип, повертаю 'text'")
    return "text"
}

/**
 * Извлекает URL медиа-файла из текста сообщения.
 * Возвращает URL если он найден, иначе null.
 */
fun extractMediaUrlFromText(text: String): String? {
    val trimmed = text.trim()

    // Проверяем, является ли весь текст URL
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        val lowerText = trimmed.lowercase()
        if (lowerText.contains("/upload/photos/") ||
            lowerText.contains("/upload/videos/") ||
            lowerText.contains("/upload/sounds/") ||
            lowerText.contains("/upload/files/") ||
            lowerText.matches(Regex(".*\\.(jpg|jpeg|png|gif|webp|mp4|webm|mov|mp3|wav|ogg|pdf|doc|docx)$"))) {
            return trimmed
        }
    }

    // Пытаемся найти URL медиа внутри текста
    val urlPattern = "(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)".toRegex()
    val match = urlPattern.find(trimmed)

    return match?.value?.let { url ->
        val lowerUrl = url.lowercase()
        if (lowerUrl.contains("/upload/photos/") ||
            lowerUrl.contains("/upload/videos/") ||
            lowerUrl.contains("/upload/sounds/") ||
            lowerUrl.contains("/upload/files/") ||
            lowerUrl.matches(Regex(".*\\.(jpg|jpeg|png|gif|webp|mp4|webm|mov|mp3|wav|ogg|pdf|doc|docx)$"))) {
            url
        } else {
            null
        }
    }
}

/**
 * Проверяет, является ли текст только URL медиа-файла.
 * Если да, не нужно показывать текст отдельно (покажем только медиа).
 */
fun isOnlyMediaUrl(text: String): Boolean {
    val trimmed = text.trim()

    // Если текст не похож на URL, это не чистый URL
    if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
        return false
    }

    // Проверяем, содержит ли URL только медиа-ресурс без дополнительного текста
    val lowerText = trimmed.lowercase()
    val isMediaUrl = lowerText.contains("/upload/photos/") ||
            lowerText.contains("/upload/videos/") ||
            lowerText.contains("/upload/sounds/") ||
            lowerText.contains("/upload/files/") ||
            lowerText.endsWith(".jpg") ||
            lowerText.endsWith(".jpeg") ||
            lowerText.endsWith(".png") ||
            lowerText.endsWith(".gif") ||
            lowerText.endsWith(".mp4") ||
            lowerText.endsWith(".mp3") ||
            lowerText.endsWith(".webm")

    // Если это URL медиа и нет дополнительного текста после URL
    return isMediaUrl && !trimmed.contains(" ") && !trimmed.contains("\n")
}

/**
 * Перевірка чи URL вказує на зображення
 */
fun isImageUrl(url: String): Boolean {
    val imageExtensions = listOf(".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp")
    val lowerUrl = url.lowercase()
    return imageExtensions.any { lowerUrl.contains(it) } ||
            lowerUrl.contains("image") ||
            lowerUrl.contains("/img/") ||
            lowerUrl.contains("/images/")
}

/**
 * 📳 Вібрація при активації режиму вибору
 */
fun performSelectionVibration(context: Context) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Короткий подвійний імпульс: 50ms → пауза 30ms → 50ms
                val timings = longArrayOf(0, 50, 30, 50)
                val amplitudes = intArrayOf(0, 150, 0, 200)
                it.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(100) // Проста вібрація 100ms для старих версій
            }
        }
    } catch (e: Exception) {
        Log.e("MessagesScreen", "Помилка вібрації: ${e.message}")
    }
}

fun formatAudioTime(millis: Long): String {
    if (millis <= 0) return "0:00"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

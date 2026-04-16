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
 * Determines the media type from a URL or explicit message type.
 *
 * Node.js server prefixes type with position ("left_gif", "right_sticker") —
 * those prefixes are stripped before comparison.
 */
fun detectMediaType(url: String?, messageType: String?): String? {
    // Strip position prefix added by Node.js (e.g. "left_gif" → "gif")
    val cleanType = messageType?.lowercase()
        ?.removePrefix("left_")?.removePrefix("right_")
        ?.takeIf { it.isNotEmpty() }

    // When URL is absent fall back to the message type
    if (url.isNullOrEmpty()) {
        Log.d("detectMediaType", "URL пустий, тип повідомлення: $messageType → clean: $cleanType")
        return when {
            cleanType == "gif" || cleanType == "sticker" -> "sticker"
            cleanType != null && cleanType != "text" -> cleanType
            else -> "text"
        }
    }

    val lowerUrl = url.lowercase()
    Log.d("detectMediaType", "Аналіз URL: $lowerUrl, тип повідомлення: $messageType")

    // Explicit gif/sticker type always wins over extension detection
    if (cleanType == "gif" || cleanType == "sticker") {
        Log.d("detectMediaType", "Явний тип gif/sticker → sticker")
        return "sticker"
    }

    // Path-based detection (most reliable for uploaded files)
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

    // Extension-based detection
    val typeByExtension = when {
        // Animated stickers & GIF — all rendered via AnimatedStickerView
        lowerUrl.endsWith(".json") || lowerUrl.endsWith(".lottie") ||
                lowerUrl.endsWith(".tgs") || lowerUrl.startsWith("lottie://") ||
                lowerUrl.endsWith(".gif") ||
                lowerUrl.contains("/stickers/") -> "sticker"

        // Static images
        lowerUrl.endsWith(".jpg") || lowerUrl.endsWith(".jpeg") ||
                lowerUrl.endsWith(".png") ||
                lowerUrl.endsWith(".webp") || lowerUrl.endsWith(".bmp") -> "image"

        // Video
        lowerUrl.endsWith(".mp4") || lowerUrl.endsWith(".webm") ||
                lowerUrl.endsWith(".mov") || lowerUrl.endsWith(".avi") ||
                lowerUrl.endsWith(".mkv") || lowerUrl.endsWith(".3gp") -> "video"

        // Audio / Voice
        lowerUrl.endsWith(".mp3") || lowerUrl.endsWith(".wav") ||
                lowerUrl.endsWith(".ogg") || lowerUrl.endsWith(".m4a") ||
                lowerUrl.endsWith(".aac") || lowerUrl.endsWith(".opus") -> "audio"

        // Files
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

    // Last resort: use clean message type
    if (cleanType != null && cleanType != "text") {
        Log.d("detectMediaType", "Використовую тип повідомлення: $cleanType")
        return cleanType
    }

    Log.d("detectMediaType", "Не вдалося визначити тип, повертаю 'text'")
    return "text"
}

/**
 * Extracts a media URL from message text.
 * Returns the URL if found, otherwise null.
 */
fun extractMediaUrlFromText(text: String): String? {
    val trimmed = text.trim()

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
 * Returns true when the text is purely a media URL (no visible caption needed).
 */
fun isOnlyMediaUrl(text: String): Boolean {
    val trimmed = text.trim()

    if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
        return false
    }

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

    return isMediaUrl && !trimmed.contains(" ") && !trimmed.contains("\n")
}

/**
 * Returns true when the URL points to an image.
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
 * 📳 Vibration when entering selection mode
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
                val timings = longArrayOf(0, 50, 30, 50)
                val amplitudes = intArrayOf(0, 150, 0, 200)
                it.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(100)
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

/**
 * Returns the last-message preview text for the chat list.
 */
fun getLastMessagePreview(message: com.worldmates.messenger.data.model.Message): String {
    val text = message.decryptedText ?: message.encryptedText

    val mediaUrl = message.decryptedMediaUrl ?: message.mediaUrl
    // Also consider stickers field (GIF/sticker URL stored there)
    val stickerUrl = message.stickers?.takeIf { it.startsWith("http") }
    val effectiveMediaUrl = when {
        !mediaUrl.isNullOrEmpty() -> mediaUrl
        stickerUrl != null -> stickerUrl
        !text.isNullOrEmpty() -> extractMediaUrlFromText(text)
        else -> null
    }
    val mediaType = when {
        stickerUrl != null && effectiveMediaUrl == stickerUrl -> "sticker"
        else -> detectMediaType(effectiveMediaUrl, message.type)
    }

    if (!text.isNullOrEmpty() && !isOnlyMediaUrl(text)) {
        val prefix = when (mediaType) {
            "image" -> "\uD83D\uDCF7 "  // 📷
            "video" -> "\uD83C\uDFA5 "  // 🎥
            "audio" -> "\uD83C\uDFB5 "  // 🎵
            "voice" -> "\uD83C\uDF99 "  // 🎙
            "file" -> "\uD83D\uDCCE "   // 📎
            "sticker" -> "\uD83C\uDFAD " // 🎭
            else -> ""
        }
        return if (prefix.isNotEmpty() && effectiveMediaUrl != null) "$prefix$text" else text
    }

    return when (mediaType) {
        "image" -> "\uD83D\uDCF7 Фото"
        "video" -> "\uD83C\uDFA5 Відео"
        "audio" -> "\uD83C\uDFB5 Аудіо"
        "voice" -> "\uD83C\uDF99 Голосове повідомлення"
        "file" -> "\uD83D\uDCCE Файл"
        "sticker" -> "\uD83C\uDFAD Стікер"
        "location" -> "\uD83D\uDCCD Локація"
        "call" -> "\uD83D\uDCDE Дзвінок"
        else -> text ?: ""
    }
}

/**
 * Audio track info: artist and title.
 */
data class AudioTrackInfo(
    val title: String,
    val artist: String,
    val extension: String
)

/**
 * Extracts track info from a URL or filename.
 * Parses "Artist - Title.ext" from the filename.
 */
fun extractAudioTrackInfo(mediaUrl: String?, originalFileName: String? = null): AudioTrackInfo {
    if (mediaUrl.isNullOrEmpty()) {
        return AudioTrackInfo(title = "Unknown Track", artist = "", extension = "")
    }

    val rawFileName = if (!originalFileName.isNullOrBlank()) {
        originalFileName
    } else {
        mediaUrl.substringAfterLast("/").substringBefore("?")
    }

    val decodedName = try {
        java.net.URLDecoder.decode(rawFileName, "UTF-8")
    } catch (e: Exception) {
        rawFileName
    }

    val extension = if (decodedName.contains(".")) decodedName.substringAfterLast(".") else ""
    val nameWithoutExt = if (extension.isNotEmpty()) decodedName.substringBeforeLast(".") else decodedName

    val separators = listOf(" - ", " — ", " – ")
    for (sep in separators) {
        if (nameWithoutExt.contains(sep)) {
            val parts = nameWithoutExt.split(sep, limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                return AudioTrackInfo(
                    title = parts[1].trim(),
                    artist = parts[0].trim(),
                    extension = extension
                )
            }
        }
    }

    val isHashOrEncrypted = nameWithoutExt.matches(Regex("[a-f0-9]{8,}[-_]?[a-f0-9]*")) ||
        nameWithoutExt.matches(Regex("encrypted_\\w+_\\d+_[a-f0-9]+"))
    return if (!isHashOrEncrypted && nameWithoutExt.length > 2) {
        AudioTrackInfo(title = nameWithoutExt.trim(), artist = "", extension = extension)
    } else {
        AudioTrackInfo(title = "Unknown Track", artist = "", extension = extension)
    }
}

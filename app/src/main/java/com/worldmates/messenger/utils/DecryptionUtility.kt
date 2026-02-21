package com.worldmates.messenger.utils

import android.util.Base64
import android.util.Log
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Утилита для дешифрования сообщений Wowonder/Worldmates.
 *
 * Поддерживает два режима шифрования:
 * - v1 (ECB): Старый режим - openssl_encrypt($text, "AES-128-ECB", $time)
 * - v2 (GCM): Новый режим - AES-256-GCM с authentication tag и динамическим IV
 *
 * Где $time - это Unix-метка времени (timestamp) сообщения.
 */
object DecryptionUtility {

    private const val TAG = "DecryptionUtility"

    // Константы для AES-128-ECB (старая версия)
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION_ECB = "AES/ECB/PKCS5Padding"

    // Версии шифрования
    private const val CIPHER_VERSION_ECB = 1
    private const val CIPHER_VERSION_GCM = 2

    /**
     * Универсальная функция дешифрования с автоопределением версии.
     * Пытается сначала дешифровать как GCM (v2), затем как ECB (v1).
     *
     * @param encryptedText Зашифрованный текст в формате Base64
     * @param timestamp Unix-метка времени сообщения (используется как ключ)
     * @param iv Base64 IV (только для GCM), может быть null
     * @param tag Base64 authentication tag (только для GCM), может быть null
     * @param cipherVersion Версия шифрования (1=ECB, 2=GCM), может быть null
     * @return Дешифрованный текст или null в случае ошибки
     */
    fun decryptMessage(
        encryptedText: String,
        timestamp: Long,
        iv: String? = null,
        tag: String? = null,
        cipherVersion: Int? = null
    ): String? {
        // Определяем версию шифрования
        val version = when {
            cipherVersion != null -> cipherVersion
            iv != null && tag != null -> CIPHER_VERSION_GCM
            else -> CIPHER_VERSION_ECB
        }

        return when (version) {
            CIPHER_VERSION_GCM -> {
                if (iv != null && tag != null) {
                    decryptMessageGCM(encryptedText, timestamp, iv, tag)
                } else {
                    Log.e(TAG, "GCM decryption requested but IV or TAG is missing")
                    null
                }
            }
            CIPHER_VERSION_ECB -> {
                decryptMessageECB(encryptedText, timestamp)
            }
            else -> {
                Log.e(TAG, "Unknown cipher version: $version")
                null
            }
        }
    }

    /**
     * Дешифрует строку Base64, зашифрованную AES-128-ECB (старый метод).
     *
     * @param encryptedText Зашифрованный текст в формате Base64
     * @param timestamp Unix-метка времени сообщения (используется как ключ)
     * @return Дешифрованный текст или null в случае ошибки
     */
    private fun decryptMessageECB(encryptedText: String, timestamp: Long): String? {
        // Создаем 16-байтовый ключ (128 бит) из метки времени
        // PHP's openssl_encrypt конвертирует число в строку и дополняет нулевыми байтами
        val timestampString = timestamp.toString()
        val timestampBytes = timestampString.toByteArray(Charsets.UTF_8)

        val keyBytes = ByteArray(16)
        timestampBytes.copyInto(keyBytes, 0, 0, minOf(timestampBytes.size, 16))

        Log.d(TAG, "ECB Decryption: timestamp=$timestamp")

        try {
            val encryptedBytes = Base64.decode(encryptedText, Base64.DEFAULT)
            if (encryptedBytes.size % 16 != 0) {
                Log.d(TAG, "ECB skip: length ${encryptedBytes.size} is not a multiple of 16 (not ECB-encrypted)")
                return null
            }

            val keySpec = SecretKeySpec(keyBytes, ALGORITHM)
            val cipher = Cipher.getInstance(TRANSFORMATION_ECB)
            cipher.init(Cipher.DECRYPT_MODE, keySpec)

            val decryptedBytes = cipher.doFinal(encryptedBytes)

            val result = String(decryptedBytes, Charsets.UTF_8).trim()
            Log.d(TAG, "ECB decryption successful")
            return result
        } catch (e: Exception) {
            Log.e(TAG, "ECB decryption failed", e)
            return null
        }
    }

    /**
     * Дешифрует строку Base64, зашифрованную AES-256-GCM (новый метод).
     *
     * @param encryptedText Зашифрованный текст в формате Base64
     * @param timestamp Unix-метка времени сообщения (используется как ключ)
     * @param iv Base64 Initialization Vector
     * @param tag Base64 Authentication Tag
     * @return Дешифрованный текст или null в случае ошибки
     */
    private fun decryptMessageGCM(
        encryptedText: String,
        timestamp: Long,
        iv: String,
        tag: String
    ): String? {
        try {
            val encryptedData = EncryptionUtility.EncryptedData(
                encryptedText = encryptedText,
                iv = iv,
                tag = tag,
                cipherVersion = CIPHER_VERSION_GCM
            )

            val decrypted = EncryptionUtility.decryptMessage(encryptedData, timestamp)

            if (decrypted != null) {
                Log.d(TAG, "GCM decryption successful")
            } else {
                Log.e(TAG, "GCM decryption failed (authentication failed or wrong key)")
            }

            return decrypted
        } catch (e: Exception) {
            Log.e(TAG, "GCM decryption error", e)
            return null
        }
    }

    /**
     * Проверяет, является ли строка валидным Base64.
     */
    private fun isBase64(text: String): Boolean {
        return try {
            // Base64 строки обычно содержат только A-Z, a-z, 0-9, +, /, =
            val base64Pattern = "^[A-Za-z0-9+/]+=*$".toRegex()
            base64Pattern.matches(text) && text.length % 4 == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Пытается расшифровать сообщение. Если не получается - возвращает исходный текст.
     * Это нужно для поддержки как зашифрованных (с веб-версии), так и незашифрованных сообщений.
     *
     * @param text Текст сообщения (зашифрованный или нет)
     * @param timestamp Unix-метка времени сообщения
     * @param iv Base64 IV (только для GCM), может быть null
     * @param tag Base64 authentication tag (только для GCM), может быть null
     * @param cipherVersion Версия шифрования (1=ECB, 2=GCM), может быть null
     * @return Расшифрованный текст или исходный текст, если расшифровка не удалась
     */
    fun decryptMessageOrOriginal(
        text: String?,
        timestamp: Long,
        iv: String? = null,
        tag: String? = null,
        cipherVersion: Int? = null
    ): String {
        // Проверяем, null или пустая ли строка
        if (text.isNullOrEmpty()) {
            Log.d(TAG, "Текст null или пустой, возвращаю пустую строку")
            return ""
        }

        // Проверяем, похоже ли это на Base64 строку
        if (!isBase64(text)) {
            Log.d(TAG, "Текст не похож на Base64, возвращаю как есть")
            return text
        }

        Log.d(TAG, "Попытка расшифровки: version=${cipherVersion ?: "auto"}, hasIV=${iv != null}, hasTag=${tag != null}")

        // Пытаемся расшифровать
        val decrypted = decryptMessage(text, timestamp, iv, tag, cipherVersion)

        // Если расшифровка успешна и результат не пустой, возвращаем его
        return if (!decrypted.isNullOrEmpty()) {
            Log.d(TAG, "Успешно расшифровано")
            decrypted
        } else {
            // Иначе возвращаем исходный текст (возможно он не был зашифрован)
            Log.d(TAG, "Не удалось расшифровать, возвращаю исходный текст")
            text
        }
    }

    /**
     * Дешифрует URL медиа-файла, если он зашифрован.
     * URL может быть:
     * - Обычным URL (http://, https://) - возвращается как есть
     * - Base64 зашифрованным URL - дешифруется
     * - Относительным путём к файлу - возвращается как есть
     *
     * @param mediaUrl URL или зашифрованная строка
     * @param timestamp Unix-метка времени сообщения
     * @param iv Base64 IV (только для GCM), может быть null
     * @param tag Base64 authentication tag (только для GCM), может быть null
     * @param cipherVersion Версия шифрования (1=ECB, 2=GCM), может быть null
     * @return Дешифрованный URL или исходный URL
     */
    fun decryptMediaUrl(
        mediaUrl: String?,
        timestamp: Long,
        iv: String? = null,
        tag: String? = null,
        cipherVersion: Int? = null
    ): String? {
        if (mediaUrl.isNullOrEmpty()) return mediaUrl

        // Если это обычный URL - возвращаем как есть
        if (mediaUrl.startsWith("http://") || mediaUrl.startsWith("https://")) {
            Log.d(TAG, "URL вже розшифрований: $mediaUrl")
            return mediaUrl
        }

        // Если это путь к файлу на сервере (начинается с /)
        if (mediaUrl.startsWith("/")) {
            Log.d(TAG, "Це шлях до файлу: $mediaUrl")
            return mediaUrl
        }

        // Проверяем, похоже ли это на Base64
        if (!isBase64(mediaUrl)) {
            Log.d(TAG, "Media URL не Base64, повертаю як є")
            return mediaUrl
        }

        Log.d(TAG, "Спроба розшифровки media URL")

        // Пытаемся расшифровать
        val decrypted = decryptMessage(mediaUrl, timestamp, iv, tag, cipherVersion)

        return if (!decrypted.isNullOrEmpty()) {
            Log.d(TAG, "Media URL розшифровано")
            decrypted
        } else {
            Log.d(TAG, "Не вдалося розшифрувати media URL, повертаю оригінал")
            mediaUrl
        }
    }

    /**
     * Проверяет, содержит ли текст зашифрованный URL внутри.
     * Иногда URL медиа-файла может быть внутри зашифрованного текста сообщения.
     *
     * @param decryptedText Расшифрованный текст сообщения
     * @return URL, если найден, иначе null
     */
    fun extractMediaUrlFromText(decryptedText: String?): String? {
        if (decryptedText.isNullOrEmpty()) return null

        // Ищем URL в тексте
        val urlPattern = "(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)".toRegex()
        val match = urlPattern.find(decryptedText)

        return match?.value?.let { url ->
            // Проверяем, является ли это URL медиа-файла
            if (url.contains("/upload/photos/") ||
                url.contains("/upload/videos/") ||
                url.contains("/upload/files/") ||
                url.contains("/upload/sounds/") ||
                url.contains(".jpg") ||
                url.contains(".jpeg") ||
                url.contains(".png") ||
                url.contains(".gif") ||
                url.contains(".mp4") ||
                url.contains(".mp3") ||
                url.contains(".wav") ||
                url.contains(".webm")) {
                Log.d("DecryptionUtility", "Знайдено URL медіа у тексті: $url")
                url
            } else {
                null
            }
        }
    }
}
package com.worldmates.messenger.utils

import android.content.Context
import android.util.Base64
import android.util.Log
import com.worldmates.messenger.data.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Утиліта для роботи з зашифрованими медіа файлами (відео, фото, аудіо)
 *
 * Підтримує AES-256-GCM дешифрування для файлів з префіксом "encrypted_file_"
 */
object EncryptedMediaHandler {

    private const val TAG = "EncryptedMediaHandler"
    private const val AES_KEY_SIZE = 256 / 8 // 32 bytes для AES-256
    private const val GCM_TAG_LENGTH = 128 // 128 біт (16 байт)
    private const val GCM_IV_LENGTH = 12 // 12 байт для GCM

    /**
     * Перевіряє чи файл зашифрований
     */
    fun isEncryptedFile(mediaUrl: String?): Boolean {
        return mediaUrl?.contains("encrypted_file_") == true
    }

    /**
     * Формує повний URL до медіа файлу на сервері
     */
    fun getFullMediaUrl(mediaUrl: String?, type: String? = "video"): String? {
        if (mediaUrl.isNullOrEmpty()) return null

        // Якщо вже повний URL - повертаємо як є
        if (mediaUrl.startsWith("http://") || mediaUrl.startsWith("https://")) {
            return mediaUrl
        }

        // Якщо починається з / - це шлях на сервері
        if (mediaUrl.startsWith("/")) {
            return "${Constants.MEDIA_BASE_URL.removeSuffix("/")}$mediaUrl"
        }

        // Інакше формуємо URL в залежності від типу
        val folder = when (type?.lowercase()) {
            "video" -> "upload/videos"
            "image", "photo" -> "upload/photos"
            "audio", "voice" -> "upload/sounds"
            "file" -> "upload/files"
            else -> "upload"
        }

        return "${Constants.MEDIA_BASE_URL}$folder/$mediaUrl"
    }

    /**
     * Завантажує і дешифровує зашифрований медіа файл
     *
     * @param mediaUrl URL або ім'я зашифрованого файлу
     * @param timestamp Unix timestamp для генерації ключа
     * @param iv Base64 Initialization Vector
     * @param tag Base64 Authentication Tag
     * @param type Тип медіа (video, image, audio)
     * @param context Контекст додатку
     * @return URL до тимчасового дешифрованого файлу або null при помилці
     */
    suspend fun decryptMediaFile(
        mediaUrl: String?,
        timestamp: Long,
        iv: String?,
        tag: String?,
        type: String?,
        context: Context
    ): String? = withContext(Dispatchers.IO) {
        try {
            if (mediaUrl.isNullOrEmpty()) {
                Log.w(TAG, "mediaUrl порожній")
                return@withContext null
            }

            // Якщо файл НЕ зашифрований - повертаємо повний URL
            if (!isEncryptedFile(mediaUrl)) {
                Log.d(TAG, "Файл НЕ зашифрований, повертаємо повний URL")
                return@withContext getFullMediaUrl(mediaUrl, type)
            }

            // Перевіряємо наявність IV і TAG
            if (iv.isNullOrEmpty() || tag.isNullOrEmpty()) {
                Log.w(TAG, "⚠️ Відсутній IV або TAG - файл може бути не зашифрований або використовує старе шифрування")
                // Повертаємо повний URL без дешифрування
                return@withContext getFullMediaUrl(mediaUrl, type)
            }

            Log.d(TAG, "🔐 Початок дешифрування медіа файлу: $mediaUrl")

            // 1. Формуємо повний URL до зашифрованого файлу
            val fullUrl = getFullMediaUrl(mediaUrl, type)
            if (fullUrl == null) {
                Log.e(TAG, "Не вдалося сформувати URL")
                return@withContext null
            }

            Log.d(TAG, "📥 Завантаження з: $fullUrl")

            // 2. Завантажуємо зашифрований файл
            val encryptedData = downloadFile(fullUrl)
            if (encryptedData == null) {
                Log.e(TAG, "❌ Не вдалося завантажити файл")
                return@withContext null
            }

            Log.d(TAG, "✅ Завантажено ${encryptedData.size} байт")

            // 3. Дешифруємо файл
            val decryptedData = decryptData(encryptedData, timestamp, iv, tag)
            if (decryptedData == null) {
                Log.e(TAG, "❌ Не вдалося дешифрувати")
                return@withContext null
            }

            Log.d(TAG, "✅ Дешифровано ${decryptedData.size} байт")

            // 4. Зберігаємо в тимчасову директорію
            val tempFile = saveTempFile(context, decryptedData, mediaUrl, type)
            if (tempFile == null) {
                Log.e(TAG, "❌ Не вдалося зберегти файл")
                return@withContext null
            }

            val tempFilePath = tempFile.absolutePath
            Log.d(TAG, "💾 Збережено в: $tempFilePath")

            return@withContext tempFilePath

        } catch (e: Exception) {
            Log.e(TAG, "❌ Помилка дешифрування медіа", e)
            return@withContext null
        }
    }

    /**
     * Завантажує файл з URL
     */
    private suspend fun downloadFile(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection()
            connection.connectTimeout = 30000 // 30 секунд
            connection.readTimeout = 30000
            connection.getInputStream().use { it.readBytes() }
        } catch (e: Exception) {
            Log.e(TAG, "Помилка завантаження файлу", e)
            null
        }
    }

    /**
     * Дешифровує дані AES-256-GCM
     */
    private fun decryptData(
        encryptedData: ByteArray,
        timestamp: Long,
        ivBase64: String,
        tagBase64: String
    ): ByteArray? {
        return try {
            // Декодуємо IV і TAG з Base64
            val iv = Base64.decode(ivBase64, Base64.DEFAULT)
            val tag = Base64.decode(tagBase64, Base64.DEFAULT)

            // Генеруємо ключ з timestamp (32 байти для AES-256)
            val key = generateKey(timestamp)

            // Об'єднуємо зашифровані дані + TAG (для GCM режиму)
            val cipherTextWithTag = encryptedData + tag

            // Ініціалізуємо Cipher для дешифрування
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            val keySpec = SecretKeySpec(key, "AES")

            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

            // Дешифруємо
            cipher.doFinal(cipherTextWithTag)

        } catch (e: Exception) {
            Log.e(TAG, "Помилка дешифрування даних", e)
            null
        }
    }

    /**
     * Генерує 32-байтовий ключ з timestamp для AES-256
     */
    private fun generateKey(timestamp: Long): ByteArray {
        // Використовуємо timestamp як базу для ключа
        val timestampString = timestamp.toString()
        val keyBytes = ByteArray(AES_KEY_SIZE)

        // Заповнюємо ключ (32 байти)
        val timestampBytes = timestampString.toByteArray(Charsets.UTF_8)
        timestampBytes.copyInto(keyBytes, 0, 0, minOf(timestampBytes.size, AES_KEY_SIZE))

        // Якщо timestamp коротший за 32 байти, доповнюємо нулями
        // (вже заповнено нулями при створенні ByteArray)

        return keyBytes
    }

    /**
     * Зберігає дешифрований файл у тимчасову директорію
     */
    private fun saveTempFile(
        context: Context,
        data: ByteArray,
        originalFileName: String,
        type: String?
    ): File? {
        return try {
            // Створюємо директорію для тимчасових файлів
            val tempDir = File(context.cacheDir, "decrypted_media")
            if (!tempDir.exists()) {
                tempDir.mkdirs()
            }

            // Генеруємо ім'я файлу
            val extension = when (type?.lowercase()) {
                "video" -> ".mp4"
                "image", "photo" -> ".jpg"
                "audio", "voice" -> ".mp3"
                else -> ".tmp"
            }

            // Видаляємо префікс "encrypted_file_" з імені
            val cleanFileName = originalFileName.removePrefix("encrypted_file_")
                .substringBefore(".")

            val tempFile = File(tempDir, "$cleanFileName$extension")

            // Зберігаємо дані
            FileOutputStream(tempFile).use { fos ->
                fos.write(data)
            }

            tempFile

        } catch (e: Exception) {
            Log.e(TAG, "Помилка збереження файлу", e)
            null
        }
    }

    /**
     * Очищає старі тимчасові файли (старші 1 години)
     */
    fun cleanupOldTempFiles(context: Context) {
        try {
            val tempDir = File(context.cacheDir, "decrypted_media")
            if (tempDir.exists()) {
                val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)
                tempDir.listFiles()?.forEach { file ->
                    if (file.lastModified() < oneHourAgo) {
                        file.delete()
                        Log.d(TAG, "Видалено старий файл: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Помилка очищення тимчасових файлів", e)
        }
    }
}

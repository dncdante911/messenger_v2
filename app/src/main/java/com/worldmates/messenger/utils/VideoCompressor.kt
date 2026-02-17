package com.worldmates.messenger.utils

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.worldmates.messenger.data.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Утилита для сжатия видео
 *
 * ВАЖНО: Для полноценного сжатия рекомендуется использовать FFmpeg-kit.
 * Эта версия предоставляет базовую функциональность с проверкой размера.
 *
 * Для добавления FFmpeg-kit в build.gradle:
 * implementation 'com.arthenica:ffmpeg-kit-min:5.1'
 */
class VideoCompressor(private val context: Context) {

    sealed class CompressionResult {
        data class Success(val compressedFile: File, val originalSize: Long, val compressedSize: Long) : CompressionResult()
        data class NoCompressionNeeded(val file: File, val size: Long) : CompressionResult()
        data class Error(val message: String) : CompressionResult()
    }

    companion object {
        private const val TAG = "VideoCompressor"
        private const val TARGET_BITRATE = 2_000_000 // 2 Mbps для сжатого видео
    }

    /**
     * Проверяет нужно ли сжимать видео и возвращает путь к файлу
     */
    suspend fun compressIfNeeded(
        videoUri: Uri,
        maxSize: Long = Constants.MAX_VIDEO_SIZE,
        onProgress: ((Int) -> Unit)? = null
    ): CompressionResult = withContext(Dispatchers.IO) {
        try {
            val inputFile = getFileFromUri(videoUri)
            if (inputFile == null) {
                return@withContext CompressionResult.Error("Не вдалося отримати файл з URI")
            }

            val fileSize = inputFile.length()
            Log.d(TAG, "Розмір відео: ${fileSize / 1024 / 1024}MB, макс: ${maxSize / 1024 / 1024}MB")

            // Если размер меньше максимального - сжатие не нужно
            if (fileSize <= maxSize) {
                Log.d(TAG, "Стиснення не потрібне")
                return@withContext CompressionResult.NoCompressionNeeded(inputFile, fileSize)
            }

            // Получаем метаданные видео
            val metadata = getVideoMetadata(inputFile)
            Log.d(TAG, "Метадані відео: duration=${metadata.duration}ms, bitrate=${metadata.bitrate}")

            // TODO: Реализация сжатия через FFmpeg-kit
            // Пока возвращаем ошибку с информацией
            Log.w(TAG, "Відео занадто велике (${fileSize / 1024 / 1024}MB). Потрібне стиснення через FFmpeg")

            // Временное решение: можно отправить как есть с предупреждением
            // или вернуть ошибку
            CompressionResult.Error(
                "Відео занадто велике (${fileSize / 1024 / 1024}MB). " +
                "Максимум ${maxSize / 1024 / 1024}MB. " +
                "Стиснення буде додано в наступній версії."
            )

        } catch (e: Exception) {
            Log.e(TAG, "Помилка стиснення: ${e.message}", e)
            CompressionResult.Error("Помилка стиснення: ${e.message}")
        }
    }

    /**
     * Сжимает видео используя FFmpeg (требует библиотеку ffmpeg-kit)
     *
     * Пример команды FFmpeg для сжатия:
     * ffmpeg -i input.mp4 -c:v libx264 -preset medium -b:v 2M -c:a aac -b:a 128k output.mp4
     */
    private suspend fun compressVideoWithFFmpeg(
        inputFile: File,
        targetBitrate: Int = TARGET_BITRATE,
        onProgress: ((Int) -> Unit)? = null
    ): CompressionResult = withContext(Dispatchers.IO) {
        // TODO: Реализация с FFmpeg-kit
        /*
        val outputFile = File(context.cacheDir, "compressed_${System.currentTimeMillis()}.mp4")

        val command = arrayOf(
            "-i", inputFile.absolutePath,
            "-c:v", "libx264",
            "-preset", "medium",
            "-b:v", "${targetBitrate}",
            "-c:a", "aac",
            "-b:a", "128k",
            "-y", // перезаписать выходной файл
            outputFile.absolutePath
        )

        val session = FFmpegKit.execute(command.joinToString(" "))

        return@withContext when (session.returnCode.isValueSuccess) {
            true -> {
                val originalSize = inputFile.length()
                val compressedSize = outputFile.length()
                Log.d(TAG, "Стиснення успішне: $originalSize -> $compressedSize")
                CompressionResult.Success(outputFile, originalSize, compressedSize)
            }
            false -> {
                CompressionResult.Error("FFmpeg помилка: ${session.failStackTrace}")
            }
        }
        */

        CompressionResult.Error("FFmpeg не інтегровано. Додайте залежність ffmpeg-kit")
    }

    private fun getFileFromUri(uri: Uri): File? {
        return try {
            val path = uri.path ?: return null
            File(path)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting file from URI: ${e.message}")
            null
        }
    }

    private data class VideoMetadata(
        val duration: Long,
        val width: Int,
        val height: Int,
        val bitrate: Int
    )

    private fun getVideoMetadata(file: File): VideoMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)

            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0

            VideoMetadata(duration, width, height, bitrate)
        } finally {
            retriever.release()
        }
    }
}

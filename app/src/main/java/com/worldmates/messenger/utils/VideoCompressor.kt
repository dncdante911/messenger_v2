package com.worldmates.messenger.utils

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.worldmates.messenger.data.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Утиліта для стиснення відео перед відправкою.
 *
 * Залежність (вже додана в build.gradle):
 *   implementation 'com.arthenica:ffmpeg-kit-min:6.0-2'
 *
 * Стратегія:
 *  - Файли менше MAX_VIDEO_SIZE відправляються без змін.
 *  - Більші файли стискаються через FFmpegKit:
 *    libx264 + AAC, 2 Мбіт/с відео, 128 кбіт/с аудіо.
 *  - Вихідний файл кешується в context.cacheDir і видаляється
 *    після відправки (відповідальність виклику).
 */
class VideoCompressor(private val context: Context) {

    sealed class CompressionResult {
        /** Стиснення виконано успішно. */
        data class Success(
            val compressedFile: File,
            val originalSize: Long,
            val compressedSize: Long
        ) : CompressionResult()

        /** Файл менше ліміту — стиснення не потрібне. */
        data class NoCompressionNeeded(val file: File, val size: Long) : CompressionResult()

        /** Помилка стиснення або отримання файлу. */
        data class Error(val message: String) : CompressionResult()
    }

    companion object {
        private const val TAG = "VideoCompressor"
        private const val TARGET_VIDEO_BITRATE = 2_000_000   // 2 Мбіт/с
        private const val TARGET_AUDIO_BITRATE = "128k"
        private const val VIDEO_CODEC         = "libx264"
        private const val AUDIO_CODEC         = "aac"
        private const val PRESET              = "fast"       // баланс швидкість/якість
    }

    /**
     * Перевіряє, чи потрібне стиснення, і за необхідності стискає.
     *
     * @param videoUri   URI вихідного відео
     * @param maxSize    максимально допустимий розмір у байтах
     * @param onProgress колбек прогресу 0‥100 (виконується в основному потоці)
     */
    suspend fun compressIfNeeded(
        videoUri: Uri,
        maxSize: Long = Constants.MAX_VIDEO_SIZE,
        onProgress: ((Int) -> Unit)? = null
    ): CompressionResult = withContext(Dispatchers.IO) {
        try {
            val inputFile = resolveFileFromUri(videoUri)
                ?: return@withContext CompressionResult.Error("Не вдалося отримати файл із URI")

            val fileSize = inputFile.length()
            Log.d(TAG, "Розмір відео: ${fileSize / 1_048_576} МБ, ліміт: ${maxSize / 1_048_576} МБ")

            if (fileSize <= maxSize) {
                Log.d(TAG, "Стиснення не потрібне")
                return@withContext CompressionResult.NoCompressionNeeded(inputFile, fileSize)
            }

            val metadata = extractMetadata(inputFile)
            Log.d(TAG, "Метадані: duration=${metadata.duration}мс, bitrate=${metadata.bitrate}")

            compressVideoWithFFmpeg(inputFile, metadata, onProgress)

        } catch (e: Exception) {
            Log.e(TAG, "Помилка стиснення: ${e.message}", e)
            CompressionResult.Error("Помилка стиснення: ${e.message}")
        }
    }

    // ─── FFmpeg ───────────────────────────────────────────────────────────────

    private suspend fun compressVideoWithFFmpeg(
        inputFile: File,
        metadata: VideoMetadata,
        onProgress: ((Int) -> Unit)?
    ): CompressionResult = suspendCancellableCoroutine { cont ->

        val outputFile = File(
            context.cacheDir,
            "compressed_${System.currentTimeMillis()}.mp4"
        )

        // Розраховуємо цільовий scale: якщо ширина > 1280 — зменшуємо
        val scaleFilter = if (metadata.width > 1280) {
            "-vf scale=1280:-2"
        } else {
            ""
        }

        // Будуємо команду FFmpeg
        val command = buildString {
            append("-i \"${inputFile.absolutePath}\" ")
            append("-c:v $VIDEO_CODEC ")
            append("-preset $PRESET ")
            append("-b:v $TARGET_VIDEO_BITRATE ")
            if (scaleFilter.isNotEmpty()) append("$scaleFilter ")
            append("-c:a $AUDIO_CODEC ")
            append("-b:a $TARGET_AUDIO_BITRATE ")
            append("-movflags +faststart ")   // веб-сумісність (MP4 stream)
            append("-y ")                     // перезаписати якщо існує
            append("\"${outputFile.absolutePath}\"")
        }

        Log.d(TAG, "FFmpeg команда: $command")

        val session = FFmpegKit.executeAsync(
            command,
            { completedSession ->
                // Колбек завершення
                when {
                    ReturnCode.isSuccess(completedSession.returnCode) -> {
                        val originalSize   = inputFile.length()
                        val compressedSize = outputFile.length()
                        val ratio = if (originalSize > 0)
                            100 - (compressedSize * 100L / originalSize) else 0L
                        Log.d(
                            TAG,
                            "Стиснення успішне: ${originalSize / 1_048_576} МБ → " +
                            "${compressedSize / 1_048_576} МБ (−$ratio%)"
                        )
                        if (!cont.isCompleted)
                            cont.resume(CompressionResult.Success(outputFile, originalSize, compressedSize))
                    }
                    ReturnCode.isCancel(completedSession.returnCode) -> {
                        outputFile.delete()
                        if (!cont.isCompleted)
                            cont.resume(CompressionResult.Error("Стиснення скасовано"))
                    }
                    else -> {
                        outputFile.delete()
                        val log = completedSession.failStackTrace ?: "невідома помилка"
                        Log.e(TAG, "FFmpeg помилка: $log")
                        if (!cont.isCompleted)
                            cont.resume(CompressionResult.Error("FFmpeg помилка: $log"))
                    }
                }
            },
            null, // log callback — null (пишеться в LogCat автоматично)
            { statistics ->
                // Колбек прогресу
                if (onProgress != null && metadata.duration > 0) {
                    val timeMs = statistics.time.toLong()
                    val progress = ((timeMs * 100L) / metadata.duration).coerceIn(0L, 100L).toInt()
                    onProgress(progress)
                }
            }
        )

        // Якщо корутина скасована — зупиняємо FFmpeg
        cont.invokeOnCancellation {
            session.cancel()
            outputFile.delete()
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Отримує File з URI. Підтримує file:// шляхи та content:// через ContentResolver.
     */
    private fun resolveFileFromUri(uri: Uri): File? {
        // Прямий file:// шлях
        if (uri.scheme == "file") {
            val path = uri.path ?: return null
            val f = File(path)
            return if (f.exists()) f else null
        }

        // content:// — копіюємо в кеш
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val tempFile = File(context.cacheDir, "video_input_${System.currentTimeMillis()}.mp4")
            tempFile.outputStream().use { out -> inputStream.copyTo(out) }
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Помилка отримання файлу з content URI: ${e.message}")
            null
        }
    }

    private data class VideoMetadata(
        val duration: Long,
        val width:    Int,
        val height:   Int,
        val bitrate:  Int
    )

    private fun extractMetadata(file: File): VideoMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            VideoMetadata(
                duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                width    = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                height   = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
                bitrate  = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
            )
        } finally {
            retriever.release()
        }
    }
}

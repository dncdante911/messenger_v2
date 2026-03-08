package com.worldmates.messenger.utils

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import com.worldmates.messenger.data.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * Стискає відео перед відправкою, використовуючи вбудований Android MediaCodec.
 *
 * Стратегія:
 *  - Файли менше MAX_VIDEO_SIZE відправляються без змін.
 *  - Більші файли перекодуються: H.264 + пасивне копіювання аудіо.
 *  - Роздільна здатність обмежується MAX_WIDTH=1280 зі збереженням пропорцій.
 *  - Бітрейт відео розраховується так, щоб вихідний файл вмістився в maxSize.
 */
class VideoCompressor(private val context: Context) {

    sealed class CompressionResult {
        data class Success(
            val compressedFile: File,
            val originalSize: Long,
            val compressedSize: Long
        ) : CompressionResult()

        data class NoCompressionNeeded(val file: File, val size: Long) : CompressionResult()

        data class Error(val message: String) : CompressionResult()
    }

    private data class VideoMetadata(
        val duration: Long,  // мс
        val width: Int,
        val height: Int,
        val bitrate: Int
    )

    companion object {
        private const val TAG              = "VideoCompressor"
        private const val TIMEOUT_US       = 10_000L
        private const val MAX_WIDTH        = 1280
        private const val MIN_VIDEO_BITRATE = 500_000   // 500 кбіт/с
        private const val MAX_VIDEO_BITRATE = 2_000_000 // 2 Мбіт/с
        private const val AUDIO_BITRATE    = 128_000    // резерв для аудіо
        private const val FRAME_RATE       = 30
        private const val I_FRAME_INTERVAL = 2
    }

    // ─────────────────────────────────────────────────────────────────────────

    suspend fun compressIfNeeded(
        videoUri: Uri,
        maxSize: Long = Constants.MAX_VIDEO_SIZE,
        onProgress: ((Int) -> Unit)? = null
    ): CompressionResult = withContext(Dispatchers.IO) {
        try {
            val inputFile = resolveFileFromUri(videoUri)
                ?: return@withContext CompressionResult.Error("Не вдалося отримати файл із URI")

            val fileSize = inputFile.length()
            Log.d(TAG, "Вхідний файл: ${fileSize / 1_048_576} МБ, ліміт: ${maxSize / 1_048_576} МБ")

            if (fileSize <= maxSize) {
                Log.d(TAG, "Стиснення не потрібне")
                return@withContext CompressionResult.NoCompressionNeeded(inputFile, fileSize)
            }

            val meta = extractMetadata(inputFile)
            val durationSec = meta.duration / 1000.0
            if (durationSec <= 0) {
                return@withContext CompressionResult.Error("Не вдалося визначити тривалість відео")
            }

            // Цільовий бітрейт відео: влізти в maxSize мінус резерв під аудіо
            val targetBitrate = ((maxSize * 8L / durationSec).toLong() - AUDIO_BITRATE)
                .coerceIn(MIN_VIDEO_BITRATE.toLong(), MAX_VIDEO_BITRATE.toLong())
                .toInt()

            val (tW, tH) = scaleDimensions(meta.width, meta.height, MAX_WIDTH)
            Log.d(TAG, "Ціль: ${tW}x${tH} @ ${targetBitrate / 1000}kbps")

            val outputFile = File(context.cacheDir, "compressed_${System.currentTimeMillis()}.mp4")

            ensureActive()
            transcode(inputFile, outputFile, tW, tH, targetBitrate, meta.duration, onProgress)

            if (!outputFile.exists() || outputFile.length() == 0L) {
                return@withContext CompressionResult.Error("Вихідний файл порожній або не існує")
            }

            val compressed = outputFile.length()
            val ratio = 100 - compressed * 100L / fileSize
            Log.d(TAG, "Готово: ${fileSize / 1_048_576}МБ → ${compressed / 1_048_576}МБ (−$ratio%)")

            CompressionResult.Success(outputFile, fileSize, compressed)

        } catch (e: Exception) {
            Log.e(TAG, "Помилка стиснення", e)
            CompressionResult.Error("Помилка стиснення: ${e.message}")
        }
    }

    // ─── Транскодування ───────────────────────────────────────────────────────

    private fun transcode(
        inputFile: File,
        outputFile: File,
        targetWidth: Int,
        targetHeight: Int,
        targetBitrate: Int,
        durationMs: Long,
        onProgress: ((Int) -> Unit)?
    ) {
        val extractor = MediaExtractor()
        var encoder: MediaCodec? = null
        var decoder: MediaCodec? = null
        var muxer: MediaMuxer? = null

        try {
            extractor.setDataSource(inputFile.absolutePath)

            // Знаходимо відео- та аудіодоріжки
            var videoTrack = -1
            var audioTrack = -1
            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
                when {
                    mime.startsWith("video/") && videoTrack < 0 -> videoTrack = i
                    mime.startsWith("audio/") && audioTrack < 0 -> audioTrack = i
                }
            }
            if (videoTrack < 0) throw IllegalStateException("Відеодоріжку не знайдено")

            val videoInputFmt = extractor.getTrackFormat(videoTrack)
            val videoMime = videoInputFmt.getString(MediaFormat.KEY_MIME)!!

            // Кодувальник H.264 з Surface-входом
            val encoderFmt = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC, targetWidth, targetHeight
            ).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE,      targetBitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE,    FRAME_RATE)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
            }
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder.configure(encoderFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val encoderSurface = encoder.createInputSurface()
            encoder.start()

            // Декодувальник → виводить кадри на Surface кодувальника
            decoder = MediaCodec.createDecoderByType(videoMime)
            decoder.configure(videoInputFmt, encoderSurface, null, 0)
            decoder.start()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            extractor.selectTrack(videoTrack)

            val bufInfo = MediaCodec.BufferInfo()
            var muxVideoTrack = -1
            var muxAudioTrack = -1
            var muxerStarted  = false
            var audioAddedToMuxer = false
            var decInputDone  = false
            var decOutputDone = false
            var encOutputDone = false

            // ── Основний цикл decode→encode ──────────────────────────────────
            while (!encOutputDone) {

                // 1. Подаємо дані у декодувальник
                if (!decInputDone) {
                    val idx = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (idx >= 0) {
                        val buf = decoder.getInputBuffer(idx)!!
                        val n = extractor.readSampleData(buf, 0)
                        if (n < 0) {
                            decoder.queueInputBuffer(idx, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            decInputDone = true
                        } else {
                            decoder.queueInputBuffer(idx, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                // 2. Декодувальник → Surface кодувальника
                if (!decOutputDone) {
                    val idx = decoder.dequeueOutputBuffer(bufInfo, TIMEOUT_US)
                    if (idx >= 0) {
                        val render = bufInfo.size > 0
                        decoder.releaseOutputBuffer(idx, render)
                        if (bufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            decOutputDone = true
                            encoder.signalEndOfInputStream()
                        }
                    }
                }

                // 3. Кодувальник → мукслер
                val eIdx = encoder.dequeueOutputBuffer(bufInfo, TIMEOUT_US)
                when {
                    eIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        muxVideoTrack = muxer.addTrack(encoder.outputFormat)
                        if (audioTrack >= 0 && !audioAddedToMuxer) {
                            muxAudioTrack = muxer.addTrack(extractor.getTrackFormat(audioTrack))
                            audioAddedToMuxer = true
                        }
                        muxer.start()
                        muxerStarted = true
                    }
                    eIdx >= 0 -> {
                        val encoded = encoder.getOutputBuffer(eIdx)!!
                        val isConfig = bufInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (!isConfig && muxerStarted) {
                            muxer.writeSampleData(muxVideoTrack, encoded, bufInfo)
                            if (onProgress != null && durationMs > 0) {
                                val pct = (bufInfo.presentationTimeUs / 1000L * 100L / durationMs)
                                    .coerceIn(0L, 99L).toInt()
                                onProgress(pct)
                            }
                        }
                        encoder.releaseOutputBuffer(eIdx, false)
                        if (bufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            encOutputDone = true
                        }
                    }
                }
            }

            // ── Копіюємо аудіодоріжку без перекодування ─────────────────────
            if (audioTrack >= 0 && muxerStarted && muxAudioTrack >= 0) {
                extractor.unselectTrack(videoTrack)
                extractor.selectTrack(audioTrack)
                extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

                val audioBuf = ByteBuffer.allocate(256 * 1024)
                val audioBufInfo = MediaCodec.BufferInfo()
                while (true) {
                    audioBuf.clear()
                    val n = extractor.readSampleData(audioBuf, 0)
                    if (n < 0) break
                    audioBufInfo.offset = 0
                    audioBufInfo.size   = n
                    audioBufInfo.presentationTimeUs = extractor.sampleTime
                    audioBufInfo.flags  =
                        if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                            MediaCodec.BUFFER_FLAG_SYNC_FRAME else 0
                    muxer.writeSampleData(muxAudioTrack, audioBuf, audioBufInfo)
                    extractor.advance()
                }
            }

            onProgress?.invoke(100)

        } finally {
            runCatching { decoder?.stop(); decoder?.release() }
            runCatching { encoder?.stop(); encoder?.release() }
            runCatching { muxer?.stop(); muxer?.release() }
            runCatching { extractor.release() }
        }
    }

    // ─── Допоміжні функції ────────────────────────────────────────────────────

    /** Масштабує розміри зі збереженням пропорцій, ширина ≤ maxW, парні значення. */
    private fun scaleDimensions(w: Int, h: Int, maxW: Int): Pair<Int, Int> {
        if (w <= 0 || h <= 0) return Pair(maxW, maxW * 9 / 16)  // fallback 16:9
        if (w <= maxW) return Pair(makeEven(w), makeEven(h))
        val scale = maxW.toFloat() / w
        return Pair(maxW, makeEven((h * scale).toInt()))
    }

    private fun makeEven(v: Int) = if (v % 2 == 0) v else v + 1

    /** Копіює content:// URI в кеш; повертає File або null при помилці. */
    private fun resolveFileFromUri(uri: Uri): File? {
        if (uri.scheme == "file") {
            val f = File(uri.path ?: return null)
            return if (f.exists()) f else null
        }
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            val tmp = File(context.cacheDir, "video_input_${System.currentTimeMillis()}.mp4")
            tmp.outputStream().use { input.copyTo(it) }
            tmp
        } catch (e: Exception) {
            Log.e(TAG, "resolveFileFromUri помилка: ${e.message}")
            null
        }
    }

    private data class VideoMetadataInternal(
        val duration: Long,
        val width: Int,
        val height: Int,
        val bitrate: Int
    )

    private fun extractMetadata(file: File): VideoMetadataInternal {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(file.absolutePath)
            VideoMetadataInternal(
                duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                width    = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                height   = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
                bitrate  = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
            )
        } finally {
            r.release()
        }
    }
}

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
 * Video compression using Android MediaCodec — Telegram-like quality tiers.
 *
 * Quality tiers (mirrors server-side TIERS in video-compressor.js):
 *   VIDEO_MESSAGE – 480p, 1.5 Mbps – short in-chat clips recorded with the camera
 *   COMPRESSED    – 720p, 2 Mbps   – "Send as video" user choice (default)
 *   HIGH_QUALITY  – 1080p, 4 Mbps  – "Good quality" user choice
 *   ORIGINAL      – no compression – "Send as file" (skip this class entirely)
 *
 * Compression threshold: files above VIDEO_COMPRESSION_THRESHOLD are compressed.
 * Android pre-compresses before upload so the server only stores small files.
 */
class VideoCompressor(private val context: Context) {

    // ─── Quality Presets ──────────────────────────────────────────────────────

    enum class Quality(
        val serverParam: String,   // sent to server as ?quality= param
        val maxWidth: Int,         // max long edge in pixels
        val maxVideoBitrate: Int,  // ceiling bitrate (bps)
        val minVideoBitrate: Int,  // floor bitrate (bps)
    ) {
        /** For round video messages / short camera clips. Target ≤ 15 MB. */
        VIDEO_MESSAGE(
            serverParam     = "video_message",
            maxWidth        = 640,
            maxVideoBitrate = 1_500_000,
            minVideoBitrate = 300_000,
        ),
        /** "Send as video" — good balance of size and quality. */
        COMPRESSED(
            serverParam     = "compressed",
            maxWidth        = 1280,
            maxVideoBitrate = 2_000_000,
            minVideoBitrate = 500_000,
        ),
        /** "Good quality" — near-original, only reduces resolution if > 1080p. */
        HIGH_QUALITY(
            serverParam     = "high",
            maxWidth        = 1920,
            maxVideoBitrate = 4_000_000,
            minVideoBitrate = 1_000_000,
        ),
    }

    sealed class CompressionResult {
        data class Success(
            val compressedFile: File,
            val originalSize: Long,
            val compressedSize: Long,
            val quality: Quality,
        ) : CompressionResult()

        data class NoCompressionNeeded(val file: File, val size: Long) : CompressionResult()

        data class Error(val message: String) : CompressionResult()
    }

    companion object {
        private const val TAG             = "VideoCompressor"
        private const val TIMEOUT_US      = 10_000L
        private const val AUDIO_BITRATE   = 128_000   // reserved for audio track (bps)
        private const val FRAME_RATE      = 30
        private const val I_FRAME_INTERVAL = 2
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Compress [videoUri] if its size exceeds [threshold].
     *
     * @param videoUri  Content or file URI to the video
     * @param quality   Which quality preset to use
     * @param threshold Compress only if file is larger than this (bytes).
     *                  Defaults to [Constants.VIDEO_COMPRESSION_THRESHOLD].
     * @param onProgress Progress callback (0-100)
     */
    suspend fun compressIfNeeded(
        videoUri: Uri,
        quality: Quality = Quality.COMPRESSED,
        threshold: Long = Constants.VIDEO_COMPRESSION_THRESHOLD,
        onProgress: ((Int) -> Unit)? = null
    ): CompressionResult = withContext(Dispatchers.IO) {
        try {
            val inputFile = resolveFileFromUri(videoUri)
                ?: return@withContext CompressionResult.Error("Не вдалося отримати файл із URI")

            val fileSize = inputFile.length()
            Log.d(TAG, "Input: ${fileSize / 1_048_576} MB, threshold: ${threshold / 1_048_576} MB, quality: $quality")

            if (fileSize <= threshold) {
                Log.d(TAG, "No compression needed (below threshold)")
                return@withContext CompressionResult.NoCompressionNeeded(inputFile, fileSize)
            }

            val meta = extractMetadata(inputFile)
            val durationSec = meta.duration / 1000.0
            if (durationSec <= 0) {
                return@withContext CompressionResult.Error("Не вдалося визначити тривалість відео")
            }

            // Target bitrate: fit within a size budget scaled by quality ceiling
            // Budget = input_size × quality_ratio, then convert to bitrate
            val targetBitrate = calculateTargetBitrate(fileSize, durationSec, quality)
            val (tW, tH) = scaleDimensions(meta.width, meta.height, quality.maxWidth)
            Log.d(TAG, "Target: ${tW}x${tH} @ ${targetBitrate / 1000} kbps (${quality.name})")

            val outputFile = File(context.cacheDir, "compressed_${quality.name}_${System.currentTimeMillis()}.mp4")

            ensureActive()
            transcode(inputFile, outputFile, tW, tH, targetBitrate, meta.duration, onProgress)

            if (!outputFile.exists() || outputFile.length() == 0L) {
                return@withContext CompressionResult.Error("Вихідний файл порожній або не існує")
            }

            val compressed = outputFile.length()
            val ratio = 100 - compressed * 100L / fileSize
            Log.d(TAG, "Done: ${fileSize / 1_048_576} MB → ${compressed / 1_048_576} MB (−$ratio%) [${quality.name}]")

            CompressionResult.Success(outputFile, fileSize, compressed, quality)

        } catch (e: Exception) {
            Log.e(TAG, "Compression error", e)
            CompressionResult.Error("Помилка стиснення: ${e.message}")
        }
    }

    // ─── Bitrate calculation ──────────────────────────────────────────────────

    private fun calculateTargetBitrate(fileSizeBytes: Long, durationSec: Double, quality: Quality): Int {
        // Calculate bitrate that would produce a file at the quality ceiling size
        // We use the existing file size but cap to the quality's max bitrate
        val rawBitrate = ((fileSizeBytes * 8L / durationSec).toLong() - AUDIO_BITRATE)
        return rawBitrate
            .coerceIn(quality.minVideoBitrate.toLong(), quality.maxVideoBitrate.toLong())
            .toInt()
    }

    // ─── Transcoding ──────────────────────────────────────────────────────────

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

            val encoderFmt = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC, targetWidth, targetHeight
            ).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE,         targetBitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE,       FRAME_RATE)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
            }
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder.configure(encoderFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val encoderSurface = encoder.createInputSurface()
            encoder.start()

            decoder = MediaCodec.createDecoderByType(videoMime)
            decoder.configure(videoInputFmt, encoderSurface, null, 0)
            decoder.start()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            extractor.selectTrack(videoTrack)

            val bufInfo = MediaCodec.BufferInfo()
            var muxVideoTrack    = -1
            var muxAudioTrack    = -1
            var muxerStarted     = false
            var audioAddedToMuxer = false
            var decInputDone     = false
            var decOutputDone    = false
            var encOutputDone    = false

            while (!encOutputDone) {

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

            // Copy audio track without re-encoding
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

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Scale dimensions preserving aspect ratio; long edge ≤ maxW; values are even. */
    private fun scaleDimensions(w: Int, h: Int, maxW: Int): Pair<Int, Int> {
        if (w <= 0 || h <= 0) return Pair(maxW, maxW * 9 / 16)
        if (w <= maxW && h <= maxW) return Pair(makeEven(w), makeEven(h))
        val scale = maxW.toFloat() / maxOf(w, h)
        return Pair(makeEven((w * scale).toInt()), makeEven((h * scale).toInt()))
    }

    private fun makeEven(v: Int) = if (v % 2 == 0) v else v + 1

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
            Log.e(TAG, "resolveFileFromUri error: ${e.message}")
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

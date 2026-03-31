package com.worldmates.messenger.network

import android.media.*
import android.util.Log
import kotlinx.coroutines.*
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Records a WebRTC video+audio stream to an MP4 file on disk.
 *
 * Usage (host):
 *   recorder.startRecording(outputFile, width = 1280, height = 720)
 *   videoTrack.addSink(recorder)
 *   // ... stream runs ...
 *   videoTrack.removeSink(recorder)
 *   val durationSeconds = recorder.stopRecording()
 *
 * Thread safety:
 *   [onFrame] is called from the WebRTC capture thread.
 *   Audio encoding runs on [audioScope].
 *   All [MediaMuxer] writes are serialised via [muxerLock].
 */
class WebRTCStreamRecorder : VideoSink {

    companion object {
        private const val TAG = "StreamRecorder"
        private const val VIDEO_MIME  = "video/avc"
        private const val AUDIO_MIME  = "audio/mp4a-latm"
        private const val SAMPLE_RATE = 44100
        private const val TIMEOUT_US  = 10_000L
    }

    // ── State ──────────────────────────────────────────────────────────────────
    @Volatile private var isRecording   = false
    @Volatile private var muxerStarted  = false
    @Volatile private var videoReady    = false
    @Volatile private var audioReady    = false

    private val startTimeUs = AtomicLong(0)

    private var muxer        : MediaMuxer?  = null
    private var videoEncoder : MediaCodec?  = null
    private var audioEncoder : MediaCodec?  = null
    private var audioRecord  : AudioRecord? = null

    private var videoTrackIndex = -1
    private var audioTrackIndex = -1

    // Reused NV12 buffer to avoid per-frame allocation
    private var nv12Buffer: ByteArray? = null

    private val muxerLock  = Object()
    private val audioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var audioJob: Job? = null

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    fun startRecording(file: File, width: Int, height: Int) {
        if (isRecording) return
        try {
            muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            startTimeUs.set(0)
            videoTrackIndex = -1
            audioTrackIndex = -1
            videoReady      = false
            audioReady      = false
            muxerStarted    = false
            nv12Buffer      = ByteArray(width * height * 3 / 2)

            setupVideoEncoder(width, height)
            setupAudioEncoderAndRecord()

            isRecording = true
            Log.d(TAG, "Recording started → ${file.absolutePath}  ${width}x${height}")
        } catch (e: Exception) {
            Log.e(TAG, "startRecording error", e)
            releaseSilently()
        }
    }

    /**
     * Stops recording and returns the recorded duration in seconds.
     * Call this AFTER removing this recorder as a VideoSink from the VideoTrack.
     */
    fun stopRecording(): Long {
        if (!isRecording) return 0
        isRecording = false

        val durationSec = if (startTimeUs.get() > 0)
            (System.nanoTime() / 1000 - startTimeUs.get()) / 1_000_000
        else 0L

        // Stop audio capture first
        audioJob?.cancel()
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null

        // Signal EOS and drain audio encoder
        signalEncoderEos(audioEncoder)
        drainEncoder(audioEncoder, isVideo = false, endOfStream = true)

        // Drain video encoder (frames may still be queued)
        drainEncoder(videoEncoder, isVideo = true, endOfStream = false)

        synchronized(muxerLock) {
            if (muxerStarted) {
                try { muxer?.stop() } catch (e: Exception) { Log.w(TAG, "muxer stop", e) }
            }
            try { muxer?.release() } catch (_: Exception) {}
            muxer = null
            muxerStarted = false
        }

        videoEncoder?.release(); videoEncoder = null
        audioEncoder?.release(); audioEncoder = null
        audioScope.coroutineContext.cancelChildren()

        Log.d(TAG, "Recording stopped. Duration ≈ ${durationSec}s")
        return durationSec
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VideoSink
    // ─────────────────────────────────────────────────────────────────────────

    override fun onFrame(frame: VideoFrame) {
        if (!isRecording) return
        frame.retain()
        try {
            val i420 = frame.buffer.toI420() ?: return
            try {
                encodeVideoFrame(i420)
            } finally {
                i420.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "onFrame error", e)
        } finally {
            frame.release()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Video encoding
    // ─────────────────────────────────────────────────────────────────────────

    private fun encodeVideoFrame(i420: VideoFrame.I420Buffer) {
        val encoder = videoEncoder ?: return
        val w = i420.width
        val h = i420.height

        val inputIdx = encoder.dequeueInputBuffer(TIMEOUT_US)
        if (inputIdx < 0) return

        val inputBuf = encoder.getInputBuffer(inputIdx) ?: return
        inputBuf.clear()

        // Record start timestamp
        val nowUs = System.nanoTime() / 1000
        if (startTimeUs.compareAndSet(0, nowUs)) { /* first frame */ }
        val ptsUs = nowUs - startTimeUs.get()

        // Copy I420 → NV12 into pre-allocated buffer
        val nv12 = ensureNv12Buffer(w, h)
        copyI420ToNv12(i420, nv12, w, h)
        inputBuf.put(nv12, 0, w * h * 3 / 2)

        encoder.queueInputBuffer(inputIdx, 0, w * h * 3 / 2, ptsUs, 0)
        drainEncoder(encoder, isVideo = true, endOfStream = false)
    }

    private fun ensureNv12Buffer(w: Int, h: Int): ByteArray {
        val needed = w * h * 3 / 2
        val buf = nv12Buffer
        return if (buf != null && buf.size >= needed) buf
        else { nv12Buffer = ByteArray(needed); nv12Buffer!! }
    }

    private fun copyI420ToNv12(i420: VideoFrame.I420Buffer, out: ByteArray, w: Int, h: Int) {
        val yBuf = i420.dataY
        val uBuf = i420.dataU
        val vBuf = i420.dataV
        val yStride = i420.strideY
        val uStride = i420.strideU
        val vStride = i420.strideV

        // Y plane
        var pos = 0
        for (row in 0 until h) {
            val rowOff = row * yStride
            for (col in 0 until w) out[pos++] = yBuf.get(rowOff + col)
        }

        // UV plane (interleaved NV12: U0 V0 U1 V1 ...)
        val uvH = h / 2
        val uvW = w / 2
        for (row in 0 until uvH) {
            val uOff = row * uStride
            val vOff = row * vStride
            for (col in 0 until uvW) {
                out[pos++] = uBuf.get(uOff + col)
                out[pos++] = vBuf.get(vOff + col)
            }
        }
    }

    private fun setupVideoEncoder(width: Int, height: Int) {
        val fmt = MediaFormat.createVideoFormat(VIDEO_MIME, width, height).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, 1_500_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
        }
        videoEncoder = MediaCodec.createEncoderByType(VIDEO_MIME).also {
            it.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            it.start()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Audio encoding
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupAudioEncoderAndRecord() {
        val fmt = MediaFormat.createAudioFormat(AUDIO_MIME, SAMPLE_RATE, 1).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, 64_000)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        audioEncoder = MediaCodec.createEncoderByType(AUDIO_MIME).also {
            it.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            it.start()
        }

        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf * 4, 8192)
        ).also { it.startRecording() }

        audioJob = audioScope.launch {
            val pcm = ByteArray(4096)
            while (isRecording) {
                val read = audioRecord?.read(pcm, 0, pcm.size) ?: break
                if (read > 0) encodeAudio(pcm, read)
            }
        }
    }

    private fun encodeAudio(pcm: ByteArray, length: Int) {
        val encoder = audioEncoder ?: return
        val idx = encoder.dequeueInputBuffer(TIMEOUT_US)
        if (idx < 0) return
        val buf = encoder.getInputBuffer(idx) ?: return
        buf.clear()
        buf.put(pcm, 0, length)
        val nowUs = System.nanoTime() / 1000
        startTimeUs.compareAndSet(0, nowUs)
        encoder.queueInputBuffer(idx, 0, length, nowUs - startTimeUs.get(), 0)
        drainEncoder(encoder, isVideo = false, endOfStream = false)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Drain / mux
    // ─────────────────────────────────────────────────────────────────────────

    private fun drainEncoder(encoder: MediaCodec?, isVideo: Boolean, endOfStream: Boolean) {
        encoder ?: return
        val info = MediaCodec.BufferInfo()
        while (true) {
            val outIdx = encoder.dequeueOutputBuffer(info, TIMEOUT_US)
            when {
                outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val fmt = encoder.outputFormat
                    synchronized(muxerLock) {
                        if (isVideo) {
                            videoTrackIndex = muxer?.addTrack(fmt) ?: -1
                            videoReady = true
                        } else {
                            audioTrackIndex = muxer?.addTrack(fmt) ?: -1
                            audioReady = true
                        }
                        maybeStartMuxerLocked()
                    }
                }
                outIdx >= 0 -> {
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        encoder.releaseOutputBuffer(outIdx, false)
                        continue
                    }
                    synchronized(muxerLock) {
                        if (muxerStarted) {
                            val trackIdx = if (isVideo) videoTrackIndex else audioTrackIndex
                            if (trackIdx >= 0 && info.size > 0) {
                                val outBuf = encoder.getOutputBuffer(outIdx)
                                if (outBuf != null) {
                                    outBuf.position(info.offset)
                                    outBuf.limit(info.offset + info.size)
                                    try { muxer?.writeSampleData(trackIdx, outBuf, info) }
                                    catch (e: Exception) { Log.w(TAG, "writeSampleData error", e) }
                                }
                            }
                        }
                    }
                    encoder.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
            if (endOfStream && outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) break
        }
    }

    private fun maybeStartMuxerLocked() {
        if (!muxerStarted && videoReady && audioReady) {
            muxer?.start()
            muxerStarted = true
            Log.d(TAG, "Muxer started")
        }
    }

    private fun signalEncoderEos(encoder: MediaCodec?) {
        encoder ?: return
        try {
            val idx = encoder.dequeueInputBuffer(TIMEOUT_US)
            if (idx >= 0) {
                encoder.queueInputBuffer(idx, 0, 0,
                    System.nanoTime() / 1000, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
        } catch (_: Exception) {}
    }

    private fun releaseSilently() {
        try { audioRecord?.release() } catch (_: Exception) {}
        try { videoEncoder?.release() } catch (_: Exception) {}
        try { audioEncoder?.release() } catch (_: Exception) {}
        try { muxer?.release() } catch (_: Exception) {}
        audioRecord = null; videoEncoder = null; audioEncoder = null; muxer = null
    }
}

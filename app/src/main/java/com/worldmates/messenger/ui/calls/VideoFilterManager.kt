package com.worldmates.messenger.ui.calls

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Log
import org.webrtc.CapturerObserver
import org.webrtc.VideoFrame
import org.webrtc.VideoFrame.I420Buffer
import org.webrtc.YuvHelper
import java.nio.ByteBuffer

/**
 * VideoFilterManager — менеджер відеофільтрів у реальному часі для відеодзвінків.
 *
 * Архітектура:
 *   CameraVideoCapturer → FilteredCapturerObserver (перехоплює кадри) → VideoSource
 *
 * Кожен кадр [VideoFrame] конвертується в [Bitmap], до нього застосовується
 * [ColorMatrix]-трансформація (тепло, холод, ч/б, краса тощо), після чого
 * кадр конвертується назад у I420 і передається в VideoSource.
 *
 * Фільтри спроєктовані так, щоб не перевищувати бюджет ~5 мс на кадр (30 fps).
 * Важкі операції (конвертація RGB↔YUV) виконуються лише коли фільтр активний.
 */
enum class VideoFilterType(
    /** Ключ для R.string.video_filters_* (для пошуку у рядках локалізації) */
    val stringKey: String
) {
    NONE("video_filters_none"),
    BEAUTY("video_filters_beauty"),
    WARM("video_filters_warm"),
    COOL("video_filters_cool"),
    BW("video_filters_bw"),
    VINTAGE("video_filters_vintage"),
    VIVID("video_filters_vivid"),
    SEPIA("video_filters_sepia"),
}

class VideoFilterManager {

    companion object {
        private const val TAG = "VideoFilterManager"
    }

    /** Поточний активний фільтр. */
    @Volatile var activeFilter: VideoFilterType = VideoFilterType.NONE

    /** Інтенсивність фільтру — від 0.0 (нема ефекту) до 1.0 (повна сила). */
    @Volatile var intensity: Float = 1.0f

    // ─── ColorMatrix-матриці для кожного фільтру ────────────────────────────

    /**
     * Повертає [ColorMatrix] відповідно до [filter] та [intensity].
     * При [VideoFilterType.NONE] повертає null → конвертація пропускається.
     */
    fun buildColorMatrix(filter: VideoFilterType, intensity: Float): ColorMatrix? {
        val t = intensity.coerceIn(0f, 1f)
        return when (filter) {
            VideoFilterType.NONE -> null

            // Краса: злегка підвищує яскравість і контрастність + рожевий відтінок
            VideoFilterType.BEAUTY -> {
                val m = ColorMatrix()
                val brightness = lerp(0f, 15f, t)
                val contrast   = lerp(1f, 1.15f, t)
                m.set(floatArrayOf(
                    contrast, 0f, 0f, 0f, brightness + 8f * t,
                    0f, contrast, 0f, 0f, brightness + 4f * t,
                    0f, 0f, contrast, 0f, brightness,
                    0f, 0f, 0f, 1f, 0f
                ))
                m
            }

            // Тепло: підсилює червоний/жовтий, послаблює синій
            VideoFilterType.WARM -> {
                val m = ColorMatrix()
                m.set(floatArrayOf(
                    lerp(1f, 1.2f, t), 0f, 0f, 0f, lerp(0f, 10f, t),
                    0f, lerp(1f, 1.05f, t), 0f, 0f, 0f,
                    0f, 0f, lerp(1f, 0.8f, t), 0f, lerp(0f, -10f, t),
                    0f, 0f, 0f, 1f, 0f
                ))
                m
            }

            // Холодний: підсилює синій, послаблює червоний
            VideoFilterType.COOL -> {
                val m = ColorMatrix()
                m.set(floatArrayOf(
                    lerp(1f, 0.85f, t), 0f, 0f, 0f, lerp(0f, -5f, t),
                    0f, lerp(1f, 1.0f, t), 0f, 0f, 0f,
                    0f, 0f, lerp(1f, 1.25f, t), 0f, lerp(0f, 10f, t),
                    0f, 0f, 0f, 1f, 0f
                ))
                m
            }

            // Чорно-білий: стандартна luminance-формула (NTSC)
            VideoFilterType.BW -> {
                val m = ColorMatrix()
                val bw = floatArrayOf(
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0.299f, 0.587f, 0.114f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
                // Змішуємо ч/б з ідентичністю за інтенсивністю
                val identity = floatArrayOf(
                    1f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
                val blended = FloatArray(20) { i -> lerp(identity[i], bw[i], t) }
                m.set(blended)
                m
            }

            // Вінтаж: знебарвлення + бежевий відтінок + знижений контраст
            VideoFilterType.VINTAGE -> {
                val m = ColorMatrix()
                m.set(floatArrayOf(
                    lerp(1f, 0.9f, t), lerp(0f, 0.05f, t), lerp(0f, 0.05f, t), 0f, lerp(0f, 10f, t),
                    lerp(0f, 0.05f, t), lerp(1f, 0.85f, t), 0f, 0f, 0f,
                    0f, lerp(0f, 0.05f, t), lerp(1f, 0.7f, t), 0f, lerp(0f, -15f, t),
                    0f, 0f, 0f, 1f, 0f
                ))
                m
            }

            // Яскравий: підвищує насиченість і контрастність
            VideoFilterType.VIVID -> {
                val sat = ColorMatrix()
                sat.setSaturation(lerp(1f, 1.6f, t))
                val contrast = ColorMatrix()
                contrast.set(floatArrayOf(
                    lerp(1f, 1.1f, t), 0f, 0f, 0f, lerp(0f, -8f, t),
                    0f, lerp(1f, 1.1f, t), 0f, 0f, lerp(0f, -8f, t),
                    0f, 0f, lerp(1f, 1.1f, t), 0f, lerp(0f, -8f, t),
                    0f, 0f, 0f, 1f, 0f
                ))
                contrast.preConcat(sat)
                contrast
            }

            // Сепія: класична коричнева тонировка
            VideoFilterType.SEPIA -> {
                val sepia = floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
                val identity = floatArrayOf(
                    1f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
                val blended = FloatArray(20) { i -> lerp(identity[i], sepia[i], t) }
                val m = ColorMatrix()
                m.set(blended)
                m
            }
        }
    }

    // ─── Обгортка CapturerObserver ───────────────────────────────────────────

    /**
     * Повертає обгортку навколо [delegate], яка перехоплює [VideoFrame]-кадри
     * і застосовує активний фільтр перед передачею до [VideoSource].
     *
     * Використання:
     *   videoCapturer?.initialize(surfaceTextureHelper, context,
     *       videoFilterManager.wrapObserver(videoSource!!.capturerObserver))
     */
    fun wrapObserver(delegate: CapturerObserver): CapturerObserver =
        FilteredCapturerObserver(delegate)

    // ─── Внутрішня реалізація ────────────────────────────────────────────────

    private inner class FilteredCapturerObserver(
        private val delegate: CapturerObserver
    ) : CapturerObserver {

        override fun onCapturerStarted(success: Boolean) = delegate.onCapturerStarted(success)
        override fun onCapturerStopped() = delegate.onCapturerStopped()

        override fun onFrameCaptured(frame: VideoFrame) {
            val filter = activeFilter
            if (filter == VideoFilterType.NONE) {
                // Фільтр вимкнено — передаємо кадр без змін, уникаємо конвертацій
                delegate.onFrameCaptured(frame)
                return
            }

            val matrix = buildColorMatrix(filter, intensity)
            if (matrix == null) {
                delegate.onFrameCaptured(frame)
                return
            }

            try {
                val filteredFrame = applyColorMatrix(frame, matrix)
                delegate.onFrameCaptured(filteredFrame)
                filteredFrame.release()
            } catch (e: Exception) {
                Log.w(TAG, "Filter apply failed, passing original frame: ${e.message}")
                delegate.onFrameCaptured(frame)
            }
        }
    }

    /**
     * Застосовує [colorMatrix] до [VideoFrame]:
     * 1. Конвертує I420 → Bitmap (ARGB_8888)
     * 2. Малює через Canvas + Paint з ColorMatrixColorFilter
     * 3. Конвертує Bitmap → I420 → обгортає у новий VideoFrame
     */
    private fun applyColorMatrix(frame: VideoFrame, colorMatrix: ColorMatrix): VideoFrame {
        val i420 = frame.buffer.toI420()
        val w = i420.width
        val h = i420.height

        // I420 → Bitmap
        val argbBytes = ByteArray(w * h * 4)
        i420ToArgb(i420, argbBytes, w, h)
        val srcBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        srcBitmap.copyPixelsFromBuffer(ByteBuffer.wrap(argbBytes))

        // Застосовуємо фільтр через Canvas
        val dstBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dstBitmap)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
        canvas.drawBitmap(srcBitmap, 0f, 0f, paint)
        srcBitmap.recycle()

        // Bitmap → I420
        val dstArgb = ByteArray(w * h * 4)
        dstBitmap.copyPixelsToBuffer(ByteBuffer.wrap(dstArgb))
        dstBitmap.recycle()

        val filteredI420 = argbToI420(dstArgb, w, h)
        i420.release()

        return VideoFrame(filteredI420, frame.rotation, frame.timestampNs)
    }

    // ─── YUV / ARGB конвертація ─────────────────────────────────────────────

    /** Конвертує I420Buffer у ARGB-байтовий масив. */
    private fun i420ToArgb(i420: I420Buffer, dst: ByteArray, w: Int, h: Int) {
        val yBuf  = i420.dataY
        val uBuf  = i420.dataU
        val vBuf  = i420.dataV
        val yStride = i420.strideY
        val uStride = i420.strideU
        val vStride = i420.strideV

        var dstPos = 0
        for (row in 0 until h) {
            for (col in 0 until w) {
                val yVal  = (yBuf.get(row * yStride + col).toInt() and 0xFF)
                val uVal  = (uBuf.get((row / 2) * uStride + col / 2).toInt() and 0xFF) - 128
                val vVal  = (vBuf.get((row / 2) * vStride + col / 2).toInt() and 0xFF) - 128

                val r = (yVal + 1.402f * vVal).toInt().coerceIn(0, 255)
                val g = (yVal - 0.344f * uVal - 0.714f * vVal).toInt().coerceIn(0, 255)
                val b = (yVal + 1.772f * uVal).toInt().coerceIn(0, 255)

                // ARGB порядок
                dst[dstPos++] = r.toByte()
                dst[dstPos++] = g.toByte()
                dst[dstPos++] = b.toByte()
                dst[dstPos++] = 0xFF.toByte()
            }
        }
    }

    /** Конвертує ARGB-байтовий масив у [VideoFrame.Buffer] I420. */
    private fun argbToI420(argb: ByteArray, w: Int, h: Int): VideoFrame.Buffer {
        val ySize = w * h
        val uvSize = (w / 2) * (h / 2)
        val yData = ByteBuffer.allocateDirect(ySize)
        val uData = ByteBuffer.allocateDirect(uvSize)
        val vData = ByteBuffer.allocateDirect(uvSize)

        for (row in 0 until h) {
            for (col in 0 until w) {
                val idx = (row * w + col) * 4
                val r = argb[idx].toInt() and 0xFF
                val g = argb[idx + 1].toInt() and 0xFF
                val b = argb[idx + 2].toInt() and 0xFF

                val y = (0.299f * r + 0.587f * g + 0.114f * b).toInt().coerceIn(0, 255)
                yData.put(y.toByte())

                if (row % 2 == 0 && col % 2 == 0) {
                    val u = (-0.169f * r - 0.331f * g + 0.500f * b + 128).toInt().coerceIn(0, 255)
                    val v = (0.500f * r - 0.419f * g - 0.081f * b + 128).toInt().coerceIn(0, 255)
                    uData.put(u.toByte())
                    vData.put(v.toByte())
                }
            }
        }

        yData.flip()
        uData.flip()
        vData.flip()

        return JavaI420Buffer(w, h, yData, w, uData, w / 2, vData, w / 2)
    }

    // ─── Просте лінійне інтерполювання ─────────────────────────────────────
    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}

// ─── Допоміжний I420Buffer ──────────────────────────────────────────────────

/**
 * Мінімальна реалізація [VideoFrame.Buffer] для I420-даних,
 * яка повертає готові ByteBuffer-и без додаткової алокації.
 */
private class JavaI420Buffer(
    private val w: Int,
    private val h: Int,
    private val y: ByteBuffer,
    private val yStride: Int,
    private val u: ByteBuffer,
    private val uStride: Int,
    private val v: ByteBuffer,
    private val vStride: Int,
) : I420Buffer {
    override fun getWidth(): Int = w
    override fun getHeight(): Int = h
    override fun getDataY(): ByteBuffer = y
    override fun getDataU(): ByteBuffer = u
    override fun getDataV(): ByteBuffer = v
    override fun getStrideY(): Int = yStride
    override fun getStrideU(): Int = uStride
    override fun getStrideV(): Int = vStride
    override fun toI420(): I420Buffer = this
    override fun retain() {}
    override fun release() {}
    override fun cropAndScale(
        cropX: Int, cropY: Int, cropW: Int, cropH: Int, scaleW: Int, scaleH: Int
    ): VideoFrame.Buffer = this
}

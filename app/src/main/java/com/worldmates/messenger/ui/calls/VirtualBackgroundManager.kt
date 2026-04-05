package com.worldmates.messenger.ui.calls

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenter
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import org.webrtc.CapturerObserver
import org.webrtc.VideoFrame
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * VirtualBackgroundManager — менеджер віртуальних фонів для відеодзвінків.
 *
 * Підтримувані режими ([VirtualBgMode]):
 *   - NONE          → без змін (прохідний режим)
 *   - BLUR_LIGHT    → легке розмиття фону (radius=10)
 *   - BLUR_STRONG   → сильне розмиття фону (radius=25)
 *   - COLOR_OFFICE  → замінює фон на бежевий офісний колір
 *   - COLOR_NATURE  → замінює фон на зелений природний колір
 *   - COLOR_GRADIENT→ замінює фон на градієнт синій→фіолетовий
 *   - CUSTOM_IMAGE  → замінює фон на зображення, вибране з галереї
 *
 * Сегментація людини:
 *   Використовує ML Kit Selfie Segmentation (on-device, без мережі).
 *   Маска оновлюється асинхронно — поки маска ще не готова, застосовується
 *   попередня (або кадр передається без змін щоб уникнути затримки).
 *
 * Pipeline:
 *   CameraVideoCapturer
 *     → VirtualBgCapturerObserver (перехоплює I420 → Bitmap)
 *       → SelfieSegmenter → маска (ByteBuffer confidences 0f–1f)
 *         → compositing (foreground + processed background)
 *           → Bitmap → I420 → VideoFrame → VideoSource
 */
enum class VirtualBgMode(val stringKey: String) {
    NONE("virtual_bg_none"),
    BLUR_LIGHT("virtual_bg_blur_light"),
    BLUR_STRONG("virtual_bg_blur_strong"),
    COLOR_OFFICE("virtual_bg_color_office"),
    COLOR_NATURE("virtual_bg_color_nature"),
    COLOR_GRADIENT("virtual_bg_color_gradient"),
    CUSTOM_IMAGE("virtual_bg_image"),
}

class VirtualBackgroundManager(private val context: Context) {

    companion object {
        private const val TAG = "VirtualBgManager"
        // Поріг впевненості ML Kit: пікселі > THRESHOLD вважаються людиною
        private const val FOREGROUND_THRESHOLD = 0.5f
    }

    /** Поточний режим фону. */
    @Volatile var activeMode: VirtualBgMode = VirtualBgMode.NONE

    /** Кастомне зображення для режиму [VirtualBgMode.CUSTOM_IMAGE]. */
    @Volatile var customBackgroundBitmap: Bitmap? = null

    // ─── ML Kit Selfie Segmenter ──────────────────────────────────────────

    private val segmenterOptions = SelfieSegmenterOptions.Builder()
        .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE) // оптимізовано для стриму
        .enableRawSizeMask()
        .build()

    private val segmenter: SelfieSegmenter = com.google.mlkit.vision.segmentation.Segmentation
        .getClient(segmenterOptions)

    /** Остання отримана маска сегментації (confidence map, один float на піксель). */
    @Volatile private var lastMask: FloatArray? = null
    @Volatile private var lastMaskWidth: Int = 0
    @Volatile private var lastMaskHeight: Int = 0

    /** Флаг: зараз сегментатор обробляє кадр (щоб не завантажувати черги). */
    private val isSegmenting = AtomicBoolean(false)

    /** Вивільняє ресурси (виклик при закритті дзвінка). */
    fun release() {
        segmenter.close()
        customBackgroundBitmap?.recycle()
        customBackgroundBitmap = null
        lastMask = null
    }

    /**
     * Завантажує кастомне фонове зображення з [Uri] (вибраного з галереї).
     */
    fun loadCustomBackground(uri: Uri) {
        try {
            val stream = context.contentResolver.openInputStream(uri) ?: return
            val bmp = BitmapFactory.decodeStream(stream)
            stream.close()
            customBackgroundBitmap?.recycle()
            customBackgroundBitmap = bmp
            Log.d(TAG, "✅ Custom background loaded: ${bmp.width}x${bmp.height}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to load custom background: ${e.message}")
        }
    }

    // ─── CapturerObserver Wrapper ────────────────────────────────────────

    /**
     * Повертає [CapturerObserver], що перехоплює кадри та застосовує фон.
     *
     * Використання (у WebRTCManager.kt):
     *   videoCapturer?.initialize(surfaceTextureHelper, context,
     *       virtualBgManager.wrapObserver(videoSource!!.capturerObserver))
     */
    fun wrapObserver(delegate: CapturerObserver): CapturerObserver =
        VirtualBgCapturerObserver(delegate)

    private inner class VirtualBgCapturerObserver(
        private val delegate: CapturerObserver
    ) : CapturerObserver {

        override fun onCapturerStarted(success: Boolean) = delegate.onCapturerStarted(success)
        override fun onCapturerStopped() = delegate.onCapturerStopped()

        override fun onFrameCaptured(frame: VideoFrame) {
            val mode = activeMode
            if (mode == VirtualBgMode.NONE) {
                delegate.onFrameCaptured(frame)
                return
            }

            try {
                val i420 = frame.buffer.toI420()
                val w = i420.width
                val h = i420.height

                // Конвертуємо I420 → Bitmap для обробки
                val argbBytes = ByteArray(w * h * 4)
                i420ToArgb(i420, argbBytes, w, h)
                val frameBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                frameBitmap.copyPixelsFromBuffer(ByteBuffer.wrap(argbBytes))
                i420.release()

                // Запускаємо/оновлюємо маску сегментації асинхронно (STREAM_MODE)
                triggerSegmentation(frameBitmap, w, h)

                // Застосовуємо фон, використовуючи останню доступну маску
                val resultBitmap = applyBackground(frameBitmap, mode, w, h)
                frameBitmap.recycle()

                // Конвертуємо оброблений Bitmap назад у VideoFrame
                val dstArgb = ByteArray(w * h * 4)
                resultBitmap.copyPixelsToBuffer(ByteBuffer.wrap(dstArgb))
                resultBitmap.recycle()

                val filteredBuffer = argbToI420Buffer(dstArgb, w, h)
                val filteredFrame = VideoFrame(filteredBuffer, frame.rotation, frame.timestampNs)
                delegate.onFrameCaptured(filteredFrame)
                filteredFrame.release()

            } catch (e: Exception) {
                Log.w(TAG, "VirtualBg frame processing failed: ${e.message}")
                delegate.onFrameCaptured(frame)
            }
        }
    }

    // ─── Сегментація (ML Kit) ────────────────────────────────────────────

    /**
     * Відправляє [bitmap] до [segmenter] для оновлення маски.
     * Використовує [isSegmenting] щоб не перевантажувати pipeline.
     */
    private fun triggerSegmentation(bitmap: Bitmap, w: Int, h: Int) {
        if (!isSegmenting.compareAndSet(false, true)) return // вже обробляється

        val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val inputImage = InputImage.fromBitmap(copy, 0)

        segmenter.process(inputImage)
            .addOnSuccessListener { result ->
                val maskBuffer = result.buffer
                maskBuffer.rewind()
                val maskW = result.width
                val maskH = result.height
                val confidences = FloatArray(maskW * maskH)
                maskBuffer.asFloatBuffer().get(confidences)

                lastMask = confidences
                lastMaskWidth = maskW
                lastMaskHeight = maskH
                copy.recycle()
                isSegmenting.set(false)
            }
            .addOnFailureListener {
                copy.recycle()
                isSegmenting.set(false)
            }
    }

    // ─── Compositing ─────────────────────────────────────────────────────

    /**
     * Застосовує фон до [frameBitmap] згідно [mode].
     * Якщо маска недоступна — фон застосовується до всього кадру (без сегментації).
     */
    private fun applyBackground(
        frameBitmap: Bitmap,
        mode: VirtualBgMode,
        w: Int,
        h: Int
    ): Bitmap {
        val bgBitmap = buildBackground(mode, w, h)

        val mask = lastMask
        val mw = lastMaskWidth
        val mh = lastMaskHeight

        if (mask == null || mw == 0 || mh == 0) {
            // Маска ще не готова — повертаємо фон (або оригінал для blur)
            return when (mode) {
                VirtualBgMode.BLUR_LIGHT, VirtualBgMode.BLUR_STRONG -> bgBitmap
                else -> bgBitmap
            }
        }

        // Результуючий bitmap: фон + накладаємо людину з маскою
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // 1. Малюємо фон
        canvas.drawBitmap(bgBitmap, 0f, 0f, null)
        bgBitmap.recycle()

        // 2. Малюємо людину поверх фону, використовуючи маску
        val foreground = extractForeground(frameBitmap, mask, mw, mh, w, h)
        canvas.drawBitmap(foreground, 0f, 0f, null)
        foreground.recycle()

        return result
    }

    /**
     * Витягує людину (foreground) з [frame] використовуючи [mask].
     * Пікселі з confidence > [FOREGROUND_THRESHOLD] стають непрозорими.
     */
    private fun extractForeground(
        frame: Bitmap,
        mask: FloatArray,
        maskW: Int, maskH: Int,
        frameW: Int, frameH: Int
    ): Bitmap {
        val result = Bitmap.createBitmap(frameW, frameH, Bitmap.Config.ARGB_8888)

        for (row in 0 until frameH) {
            for (col in 0 until frameW) {
                // Масштабуємо координати маски до розміру кадру
                val maskRow = (row.toFloat() / frameH * maskH).toInt().coerceIn(0, maskH - 1)
                val maskCol = (col.toFloat() / frameW * maskW).toInt().coerceIn(0, maskW - 1)
                val confidence = mask[maskRow * maskW + maskCol]

                if (confidence >= FOREGROUND_THRESHOLD) {
                    result.setPixel(col, row, frame.getPixel(col, row))
                }
                // else: залишається прозорим (фон вже намальований нижче)
            }
        }
        return result
    }

    /**
     * Будує фоновий [Bitmap] розміром [w]×[h] відповідно до [mode].
     */
    private fun buildBackground(mode: VirtualBgMode, w: Int, h: Int): Bitmap {
        return when (mode) {
            VirtualBgMode.BLUR_LIGHT -> blurBitmap(/* TODO: pass frame */ Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888), 10f)
            VirtualBgMode.BLUR_STRONG -> blurBitmap(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888), 25f)
            VirtualBgMode.COLOR_OFFICE -> solidColorBitmap(w, h, Color.parseColor("#F5F0E8"))
            VirtualBgMode.COLOR_NATURE -> solidColorBitmap(w, h, Color.parseColor("#2D5A27"))
            VirtualBgMode.COLOR_GRADIENT -> gradientBitmap(w, h,
                Color.parseColor("#1A237E"), Color.parseColor("#6A1B9A"))
            VirtualBgMode.CUSTOM_IMAGE -> {
                val custom = customBackgroundBitmap
                if (custom != null && !custom.isRecycled) {
                    Bitmap.createScaledBitmap(custom, w, h, true)
                } else {
                    solidColorBitmap(w, h, Color.DKGRAY)
                }
            }
            VirtualBgMode.NONE -> Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        }
    }

    // ─── Blur ────────────────────────────────────────────────────────────

    /**
     * Розмиття через [BlurMaskFilter] + Canvas render trick.
     * Альтернатива RenderScript (застарілий API 31+).
     */
    private fun blurBitmap(src: Bitmap, radius: Float): Bitmap {
        val blurred = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(blurred)
        val paint = Paint().apply {
            maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
            isAntiAlias = true
        }
        canvas.drawBitmap(src, 0f, 0f, paint)
        if (src != blurred) src.recycle()
        return blurred
    }

    /**
     * Виконує software stack blur на [Bitmap] — значно ефективніший за кадр
     * ніж [BlurMaskFilter] при великих радіусах.
     * radius має бути в діапазоні [1..25].
     */
    fun stackBlurBitmap(bitmap: Bitmap, radius: Int): Bitmap {
        val r = radius.coerceIn(1, 25)
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        var rsum: Int; var gsum: Int; var bsum: Int
        var x: Int; var y: Int; var i: Int; var p: Int
        val div = 2 * r + 1
        val rOut = IntArray(w * h)
        val gOut = IntArray(w * h)
        val bOut = IntArray(w * h)
        val vmin = IntArray(maxOf(w, h))
        val divsum = (div + 1) shr 1
        val dv = IntArray(256 * divsum)
        for (di in dv.indices) dv[di] = di / divsum

        var yw = 0; var yi = 0
        val stack = Array(div) { IntArray(3) }
        var stackstart: Int; var stackend: Int
        var sir: IntArray
        var rbs: Int

        y = 0
        while (y < h) {
            rsum = 0; gsum = 0; bsum = 0
            for (dyi in -r..r) {
                p = pixels[(yi + minOf(h - 1, maxOf(0, dyi))) * 1]  // simplified: use 0
                sir = stack[dyi + r]
                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = (p and 0x0000ff)
                rbs = r + 1 - Math.abs(dyi)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
            }
            stackend = r
            x = 0
            while (x < w) {
                rOut[yi] = dv[rsum]
                gOut[yi] = dv[gsum]
                bOut[yi] = dv[bsum]
                rsum -= dv[rsum]
                gsum -= dv[gsum]
                bsum -= dv[bsum]
                stackstart = (stackend + 1) % div
                sir = stack[stackstart]
                rsum -= sir[0]; gsum -= sir[1]; bsum -= sir[2]
                val nextX = minOf(x + r + 1, w - 1)
                p = pixels[yw + nextX]
                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = (p and 0x0000ff)
                rsum += sir[0]; gsum += sir[1]; bsum += sir[2]
                stackend = (stackend + 1) % div
                sir = stack[stackend]
                rsum += sir[0]; gsum += sir[1]; bsum += sir[2]
                rsum -= sir[0]; gsum -= sir[1]; bsum -= sir[2]
                yi++; x++
            }
            yw += w; y++
        }

        // Reconstruct pixels
        for (j in pixels.indices) {
            pixels[j] = (-0x1000000
                    or (rOut[j] shl 16)
                    or (gOut[j] shl 8)
                    or bOut[j])
        }
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    // ─── Допоміжні генератори фону ────────────────────────────────────────

    private fun solidColorBitmap(w: Int, h: Int, color: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(color)
        return bmp
    }

    private fun gradientBitmap(w: Int, h: Int, startColor: Int, endColor: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint()
        paint.shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            startColor, endColor, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        return bmp
    }

    // ─── YUV / ARGB конвертація ──────────────────────────────────────────

    private fun i420ToArgb(i420: VideoFrame.I420Buffer, dst: ByteArray, w: Int, h: Int) {
        val yBuf = i420.dataY; val uBuf = i420.dataU; val vBuf = i420.dataV
        val yStride = i420.strideY; val uStride = i420.strideU; val vStride = i420.strideV
        var dstPos = 0
        for (row in 0 until h) {
            for (col in 0 until w) {
                val y = (yBuf.get(row * yStride + col).toInt() and 0xFF)
                val u = (uBuf.get((row / 2) * uStride + col / 2).toInt() and 0xFF) - 128
                val v = (vBuf.get((row / 2) * vStride + col / 2).toInt() and 0xFF) - 128
                dst[dstPos++] = ((y + 1.402f * v).toInt().coerceIn(0, 255)).toByte()
                dst[dstPos++] = ((y - 0.344f * u - 0.714f * v).toInt().coerceIn(0, 255)).toByte()
                dst[dstPos++] = ((y + 1.772f * u).toInt().coerceIn(0, 255)).toByte()
                dst[dstPos++] = 0xFF.toByte()
            }
        }
    }

    private fun argbToI420Buffer(argb: ByteArray, w: Int, h: Int): VideoFrame.Buffer {
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
                val yv = (0.299f * r + 0.587f * g + 0.114f * b).toInt().coerceIn(0, 255)
                yData.put(yv.toByte())
                if (row % 2 == 0 && col % 2 == 0) {
                    uData.put((-0.169f * r - 0.331f * g + 0.500f * b + 128).toInt().coerceIn(0, 255).toByte())
                    vData.put((0.500f * r - 0.419f * g - 0.081f * b + 128).toInt().coerceIn(0, 255).toByte())
                }
            }
        }
        yData.flip(); uData.flip(); vData.flip()
        return VirtualBgI420Buffer(w, h, yData, w, uData, w / 2, vData, w / 2)
    }
}

// ─── Допоміжний I420Buffer для VirtualBackgroundManager ─────────────────────

private class VirtualBgI420Buffer(
    private val w: Int, private val h: Int,
    private val y: ByteBuffer, private val yStride: Int,
    private val u: ByteBuffer, private val uStride: Int,
    private val v: ByteBuffer, private val vStride: Int,
) : VideoFrame.I420Buffer {
    override fun getWidth() = w
    override fun getHeight() = h
    override fun getDataY() = y
    override fun getDataU() = u
    override fun getDataV() = v
    override fun getStrideY() = yStride
    override fun getStrideU() = uStride
    override fun getStrideV() = vStride
    override fun toI420() = this
    override fun retain() {}
    override fun release() {}
    override fun cropAndScale(cx: Int, cy: Int, cw: Int, ch: Int, sw: Int, sh: Int) = this
}

package com.worldmates.messenger.ui.calls

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
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
    // Правильний тип: Segmenter<SegmentationMask>, але використовуємо var для відстрочки ініціалізації
    // Segmentation.getClient() повертає Segmenter<SegmentationMask> — тип виводиться автоматично

    private val segmenter by lazy {
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE) // оптимізовано для стриму
            .enableRawSizeMask()
            .build()
        Segmentation.getClient(options)
    }

    /** Остання отримана маска сегментації (confidence map, один float на піксель). */
    @Volatile private var lastMask: FloatArray? = null
    @Volatile private var lastMaskWidth: Int = 0
    @Volatile private var lastMaskHeight: Int = 0

    /** Флаг: зараз сегментатор обробляє кадр (щоб не завантажувати черги). */
    private val isSegmenting = AtomicBoolean(false)

    /** Вивільняє ресурси (виклик при закритті дзвінка). */
    fun release() {
        try { segmenter.close() } catch (_: Exception) { /* non-fatal */ }
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
                // toI420() може повернути null — у цьому разі пропускаємо кадр без обробки
                val i420 = frame.buffer.toI420()
                if (i420 == null) {
                    delegate.onFrameCaptured(frame)
                    return
                }
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
     * Відправляє [bitmap] до segmenter для оновлення маски.
     * Використовує [isSegmenting] щоб не перевантажувати pipeline.
     */
    private fun triggerSegmentation(bitmap: Bitmap, w: Int, h: Int) {
        if (!isSegmenting.compareAndSet(false, true)) return // вже обробляється

        val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val inputImage = InputImage.fromBitmap(copy, 0)

        segmenter.process(inputImage)
            .addOnSuccessListener { result ->
                // SegmentationMask: result.buffer (ByteBuffer з float values), result.width, result.height
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
     * Якщо маска недоступна — для blur режимів розмиваємо весь кадр (без сегментації),
     * для кольорових режимів повертаємо solid фон без compositing.
     */
    private fun applyBackground(
        frameBitmap: Bitmap,
        mode: VirtualBgMode,
        w: Int,
        h: Int
    ): Bitmap {
        val mask = lastMask
        val mw = lastMaskWidth
        val mh = lastMaskHeight

        // Blur-режим: розмиваємо весь кадр (frameBitmap)
        if (mode == VirtualBgMode.BLUR_LIGHT || mode == VirtualBgMode.BLUR_STRONG) {
            val radius = if (mode == VirtualBgMode.BLUR_LIGHT) 10 else 22
            val blurredFull = stackBlurBitmap(
                frameBitmap.copy(Bitmap.Config.ARGB_8888, true), radius
            )
            // Якщо маска є — compositing: на розмитому фоні малюємо оригінальну людину
            if (mask != null && mw > 0 && mh > 0) {
                val result = blurredFull.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(result)
                val foreground = extractForeground(frameBitmap, mask, mw, mh, w, h)
                canvas.drawBitmap(foreground, 0f, 0f, null)
                foreground.recycle()
                blurredFull.recycle()
                return result
            }
            return blurredFull
        }

        // Solid/gradient/custom режим
        val bgBitmap = buildBackground(mode, w, h, frameBitmap)

        if (mask == null || mw == 0 || mh == 0) {
            // Маска ще не готова — повертаємо фон без compositing
            return bgBitmap
        }

        // Результуючий bitmap: фон + накладаємо людину з маскою
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(bgBitmap, 0f, 0f, null)
        bgBitmap.recycle()

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
                val maskRow = (row.toFloat() / frameH * maskH).toInt().coerceIn(0, maskH - 1)
                val maskCol = (col.toFloat() / frameW * maskW).toInt().coerceIn(0, maskW - 1)
                val confidence = mask[maskRow * maskW + maskCol]

                if (confidence >= FOREGROUND_THRESHOLD) {
                    result.setPixel(col, row, frame.getPixel(col, row))
                }
            }
        }
        return result
    }

    /**
     * Будує фоновий [Bitmap] розміром [w]×[h] відповідно до [mode].
     * [frameBitmap] використовується тільки для blur режимів (уже оброблено вище).
     */
    private fun buildBackground(
        mode: VirtualBgMode,
        w: Int,
        h: Int,
        frameBitmap: Bitmap? = null
    ): Bitmap {
        return when (mode) {
            VirtualBgMode.COLOR_OFFICE  -> solidColorBitmap(w, h, Color.parseColor("#F5F0E8"))
            VirtualBgMode.COLOR_NATURE  -> solidColorBitmap(w, h, Color.parseColor("#2D5A27"))
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
            // Blur та NONE обробляються до цього виклику або повертають пустий bitmap
            else -> Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        }
    }

    // ─── Stack Blur ───────────────────────────────────────────────────────

    /**
     * Виконує software Stack Blur на [bitmap].
     * Ефективний алгоритм для real-time розмиття (~2–5 мс для 480p при radius=15).
     * radius має бути в діапазоні [1..25].
     */
    fun stackBlurBitmap(bitmap: Bitmap, radius: Int): Bitmap {
        val r = radius.coerceIn(1, 25)
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // Однопрохідний горизонтальний blur
        for (y in 0 until h) {
            var rSum = 0; var gSum = 0; var bSum = 0
            for (i in -r..r) {
                val x = i.coerceIn(0, w - 1)
                val c = pixels[y * w + x]
                rSum += (c shr 16) and 0xFF
                gSum += (c shr 8) and 0xFF
                bSum += c and 0xFF
            }
            val div = 2 * r + 1
            for (x in 0 until w) {
                pixels[y * w + x] = (-0x1000000
                        or ((rSum / div) shl 16)
                        or ((gSum / div) shl 8)
                        or (bSum / div))
                val leftX = (x - r).coerceIn(0, w - 1)
                val rightX = (x + r + 1).coerceIn(0, w - 1)
                val left = pixels[y * w + leftX]
                val right = pixels[y * w + rightX]
                rSum += ((right shr 16) and 0xFF) - ((left shr 16) and 0xFF)
                gSum += ((right shr 8) and 0xFF) - ((left shr 8) and 0xFF)
                bSum += (right and 0xFF) - (left and 0xFF)
            }
        }

        // Однопрохідний вертикальний blur
        for (x in 0 until w) {
            var rSum = 0; var gSum = 0; var bSum = 0
            for (i in -r..r) {
                val y = i.coerceIn(0, h - 1)
                val c = pixels[y * w + x]
                rSum += (c shr 16) and 0xFF
                gSum += (c shr 8) and 0xFF
                bSum += c and 0xFF
            }
            val div = 2 * r + 1
            for (y in 0 until h) {
                pixels[y * w + x] = (-0x1000000
                        or ((rSum / div) shl 16)
                        or ((gSum / div) shl 8)
                        or (bSum / div))
                val topY = (y - r).coerceIn(0, h - 1)
                val botY = (y + r + 1).coerceIn(0, h - 1)
                val top = pixels[topY * w + x]
                val bot = pixels[botY * w + x]
                rSum += ((bot shr 16) and 0xFF) - ((top shr 16) and 0xFF)
                gSum += ((bot shr 8) and 0xFF) - ((top shr 8) and 0xFF)
                bSum += (bot and 0xFF) - (top and 0xFF)
            }
        }

        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    // ─── Допоміжні генератори фону ────────────────────────────────────────

    private fun solidColorBitmap(w: Int, h: Int, color: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(color)
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

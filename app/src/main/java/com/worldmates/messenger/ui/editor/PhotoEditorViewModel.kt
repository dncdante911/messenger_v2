package com.worldmates.messenger.ui.editor

import android.content.Context
import android.graphics.*
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

class PhotoEditorViewModel : ViewModel() {

    private val TAG = "PhotoEditorVM"
    private val MAX_DIM = 1920 // max image dimension on load

    // ── Image state ───────────────────────────────────────────────────────────
    private val _originalBitmap  = MutableStateFlow<Bitmap?>(null)
    private val _editedBitmap    = MutableStateFlow<Bitmap?>(null)
    val editedBitmap: StateFlow<Bitmap?> = _editedBitmap.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    // ── Tool state ────────────────────────────────────────────────────────────
    private val _currentTool = MutableStateFlow(EditorTool.DRAW)
    val currentTool: StateFlow<EditorTool> = _currentTool.asStateFlow()

    // ── Drawing state (normalized 0-1 coords) ─────────────────────────────────
    private val _drawingPaths = MutableStateFlow<List<DrawPath>>(emptyList())
    val drawingPaths: StateFlow<List<DrawPath>> = _drawingPaths.asStateFlow()

    private val _selectedColor = MutableStateFlow(Color.Red)
    val selectedColor: StateFlow<Color> = _selectedColor.asStateFlow()

    private val _brushSize = MutableStateFlow(10f)
    val brushSize: StateFlow<Float> = _brushSize.asStateFlow()

    // ── Text / sticker state ──────────────────────────────────────────────────
    private val _textElements = MutableStateFlow<List<TextElement>>(emptyList())
    val textElements: StateFlow<List<TextElement>> = _textElements.asStateFlow()

    // ── Filter state ──────────────────────────────────────────────────────────
    private val _selectedFilter = MutableStateFlow(PhotoFilter.NONE)
    val selectedFilter: StateFlow<PhotoFilter> = _selectedFilter.asStateFlow()

    // ── Adjustment state ──────────────────────────────────────────────────────
    private val _brightness  = MutableStateFlow(0f)
    private val _contrast    = MutableStateFlow(1f)
    private val _saturation  = MutableStateFlow(1f)
    private val _warmth      = MutableStateFlow(0f) // -1 cool … +1 warm
    val brightness:  StateFlow<Float> = _brightness.asStateFlow()
    val contrast:    StateFlow<Float> = _contrast.asStateFlow()
    val saturation:  StateFlow<Float> = _saturation.asStateFlow()
    val warmth:      StateFlow<Float> = _warmth.asStateFlow()

    // ── Rotation state ────────────────────────────────────────────────────────
    private val _rotationAngle = MutableStateFlow(0)
    val rotationAngle: StateFlow<Int> = _rotationAngle.asStateFlow()

    // ── Crop state ────────────────────────────────────────────────────────────
    private val _cropRect = MutableStateFlow(CropRect(0f, 0f, 1f, 1f))
    val cropRect: StateFlow<CropRect> = _cropRect.asStateFlow()

    // ── Undo / Redo stacks ────────────────────────────────────────────────────
    private val undoStack = mutableListOf<EditorState>()
    private val redoStack = mutableListOf<EditorState>()

    // Tracks if undo was already saved during current ADJUST session
    private var adjustUndoSaved = false
    private var adjustJob: Job? = null

    // ── Image loading ─────────────────────────────────────────────────────────

    fun loadImage(imageUrl: String, context: Context) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val raw = if (imageUrl.startsWith("http")) {
                        val conn = URL(imageUrl).openConnection()
                        conn.connectTimeout = 10_000
                        conn.readTimeout    = 15_000
                        conn.getInputStream().use { BitmapFactory.decodeStream(it) }
                    } else {
                        BitmapFactory.decodeFile(imageUrl)
                    }
                    raw?.let { scaleToBounds(it, MAX_DIM) }
                }
                if (bitmap != null) {
                    _originalBitmap.value = bitmap
                    _editedBitmap.value   = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                    Log.d(TAG, "Loaded ${bitmap.width}×${bitmap.height}")
                } else {
                    Log.e(TAG, "Failed to decode bitmap from $imageUrl")
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadImage error: ${e.message}", e)
            } finally {
                _isProcessing.value = false
            }
        }
    }

    // Scale bitmap so its longest dimension ≤ maxDim; recycles input if scaled
    private fun scaleToBounds(bmp: Bitmap, maxDim: Int): Bitmap {
        if (bmp.width <= maxDim && bmp.height <= maxDim) return bmp
        val scale = maxDim.toFloat() / maxOf(bmp.width, bmp.height)
        val scaled = Bitmap.createScaledBitmap(
            bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true
        )
        bmp.recycle()
        return scaled
    }

    // ── Tool selection ────────────────────────────────────────────────────────

    fun selectTool(tool: EditorTool) {
        if (tool == EditorTool.ADJUST) adjustUndoSaved = false
        _currentTool.value = tool
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    fun addDrawPathFromPoints(
        canvasPoints: List<Offset>,
        canvasSize:   IntSize,
        color:        Color,
        strokeWidth:  Float
    ) {
        if (canvasPoints.size < 2 || canvasSize.width == 0 || canvasSize.height == 0) return
        saveStateForUndo()
        val normalized = canvasPoints.map { pt ->
            Offset(pt.x / canvasSize.width, pt.y / canvasSize.height)
        }
        _drawingPaths.value = _drawingPaths.value + DrawPath(normalized, color, strokeWidth)
    }

    fun setDrawColor(color: Color) { _selectedColor.value = color }
    fun setBrushSize(size: Float)  { _brushSize.value = size }

    // ── Text / stickers ───────────────────────────────────────────────────────

    fun addText(text: String, color: Color, size: Float) {
        saveStateForUndo()
        _textElements.value = _textElements.value + TextElement(text, color, size, 0.5f, 0.45f)
    }

    fun addSticker(sticker: String) {
        saveStateForUndo()
        _textElements.value = _textElements.value + TextElement(sticker, Color.White, 48f, 0.5f, 0.45f)
    }

    fun moveTextElement(index: Int, newX: Float, newY: Float) {
        val list = _textElements.value.toMutableList()
        if (index in list.indices) {
            list[index] = list[index].copy(
                x = newX.coerceIn(0.02f, 0.98f),
                y = newY.coerceIn(0.02f, 0.98f)
            )
            _textElements.value = list
        }
    }

    // Save undo snapshot once at the start of a text drag
    fun beginMoveTextElement() { saveStateForUndo() }

    fun deleteTextElement(index: Int) {
        saveStateForUndo()
        _textElements.value = _textElements.value.toMutableList().also { it.removeAt(index) }
    }

    // ── Filters ───────────────────────────────────────────────────────────────

    fun applyFilter(filter: PhotoFilter) {
        saveStateForUndo()
        _selectedFilter.value = filter
        viewModelScope.launch {
            _isProcessing.value = true
            val original = _originalBitmap.value ?: run { _isProcessing.value = false; return@launch }
            val result = withContext(Dispatchers.Default) {
                val base = original.copy(Bitmap.Config.ARGB_8888, true)
                when (filter) {
                    PhotoFilter.NONE      -> base
                    PhotoFilter.GRAYSCALE -> applyGrayscale(base)
                    PhotoFilter.SEPIA     -> applySepia(base)
                    PhotoFilter.INVERT    -> applyInvert(base)
                    PhotoFilter.BLUR      -> applyBlurFast(base, radius = 10)
                    PhotoFilter.SHARPEN   -> applySharpen(base)
                    PhotoFilter.WARM      -> applyColorMatrix(base, warmColorMatrix(0.6f))
                    PhotoFilter.COOL      -> applyColorMatrix(base, warmColorMatrix(-0.6f))
                    PhotoFilter.VIVID     -> applyVivid(base)
                }
            }
            _editedBitmap.value = result
            _isProcessing.value = false
        }
    }

    // ── Adjustments (debounced) ───────────────────────────────────────────────

    fun setBrightness(value: Float) { _brightness.value = value;  scheduleAdjustments() }
    fun setContrast  (value: Float) { _contrast.value   = value;  scheduleAdjustments() }
    fun setSaturation(value: Float) { _saturation.value = value;  scheduleAdjustments() }
    fun setWarmth    (value: Float) { _warmth.value     = value;  scheduleAdjustments() }

    private fun scheduleAdjustments() {
        if (!adjustUndoSaved) { saveStateForUndo(); adjustUndoSaved = true }
        adjustJob?.cancel()
        adjustJob = viewModelScope.launch {
            delay(80) // 80 ms debounce
            withContext(Dispatchers.Default) { applyAdjustmentsInternal() }
        }
    }

    private fun applyAdjustmentsInternal() {
        val original = _originalBitmap.value ?: return
        val cm = ColorMatrix()

        // Brightness
        val b = _brightness.value * 255
        cm.postConcat(ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, b,
            0f, 1f, 0f, 0f, b,
            0f, 0f, 1f, 0f, b,
            0f, 0f, 0f, 1f, 0f
        )))

        // Contrast
        val c = _contrast.value
        val t = (1f - c) / 2f * 255
        cm.postConcat(ColorMatrix(floatArrayOf(
            c, 0f, 0f, 0f, t,
            0f, c, 0f, 0f, t,
            0f, 0f, c, 0f, t,
            0f, 0f, 0f, 1f, 0f
        )))

        // Saturation
        cm.postConcat(ColorMatrix().apply { setSaturation(_saturation.value) })

        // Warmth
        val w = _warmth.value
        if (w != 0f) cm.postConcat(warmColorMatrix(w))

        val result = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
        Canvas(result).drawBitmap(original, 0f, 0f, Paint().apply {
            colorFilter = ColorMatrixColorFilter(cm)
        })
        _editedBitmap.value = result
    }

    private fun warmColorMatrix(strength: Float) = ColorMatrix(floatArrayOf(
        1f + strength * 0.3f,  0f,                   0f,                    0f, 0f,
        0f,                    1f + strength * 0.04f, 0f,                    0f, 0f,
        0f,                    0f,                    1f - strength * 0.25f, 0f, 0f,
        0f,                    0f,                    0f,                    1f, 0f
    ))

    // ── Rotate / Flip ─────────────────────────────────────────────────────────

    fun rotate(degrees: Int) {
        saveStateForUndo()
        _rotationAngle.value = (_rotationAngle.value + degrees) % 360
        viewModelScope.launch {
            _isProcessing.value = true
            val current = _editedBitmap.value ?: run { _isProcessing.value = false; return@launch }
            val result = withContext(Dispatchers.Default) {
                val m = Matrix().apply { postRotate(degrees.toFloat()) }
                Bitmap.createBitmap(current, 0, 0, current.width, current.height, m, true)
            }
            _editedBitmap.value = result
            _isProcessing.value = false
        }
    }

    fun flip(horizontal: Boolean) {
        saveStateForUndo()
        viewModelScope.launch {
            _isProcessing.value = true
            val current = _editedBitmap.value ?: run { _isProcessing.value = false; return@launch }
            val result = withContext(Dispatchers.Default) {
                val m = Matrix().apply {
                    val cx = current.width  / 2f
                    val cy = current.height / 2f
                    if (horizontal) postScale(-1f, 1f, cx, cy)
                    else            postScale(1f, -1f, cx, cy)
                }
                Bitmap.createBitmap(current, 0, 0, current.width, current.height, m, true)
            }
            _editedBitmap.value = result
            _isProcessing.value = false
        }
    }

    // ── Crop ──────────────────────────────────────────────────────────────────

    fun resetCrop() { _cropRect.value = CropRect(0f, 0f, 1f, 1f) }

    fun updateCropRect(left: Float, top: Float, right: Float, bottom: Float) {
        val min = 0.05f
        _cropRect.value = CropRect(
            left   = left.coerceIn(0f, (right  - min).coerceAtLeast(0f)),
            top    = top.coerceIn(0f,  (bottom - min).coerceAtLeast(0f)),
            right  = right.coerceIn((left   + min).coerceAtMost(1f), 1f),
            bottom = bottom.coerceIn((top + min).coerceAtMost(1f), 1f)
        )
    }

    fun applyCrop() {
        saveStateForUndo()
        viewModelScope.launch {
            _isProcessing.value = true
            val bitmap = _editedBitmap.value ?: run { _isProcessing.value = false; return@launch }
            val rect   = _cropRect.value
            val result = withContext(Dispatchers.Default) {
                val x = (rect.left  * bitmap.width ).toInt().coerceIn(0, bitmap.width  - 1)
                val y = (rect.top   * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
                val w = ((rect.right  - rect.left) * bitmap.width ).toInt().coerceIn(1, bitmap.width  - x)
                val h = ((rect.bottom - rect.top)  * bitmap.height).toInt().coerceIn(1, bitmap.height - y)
                Bitmap.createBitmap(bitmap, x, y, w, h)
            }
            _editedBitmap.value  = result
            _cropRect.value      = CropRect(0f, 0f, 1f, 1f)
            _isProcessing.value  = false
        }
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    /**
     * Apply all canvas drawings and text to the bitmap, then save to cache.
     * Runs on IO thread; caller must be in a coroutine context.
     */
    suspend fun applyAndSaveImage(context: Context): File? = withContext(Dispatchers.Default) {
        try {
            val bitmap = _editedBitmap.value ?: return@withContext null
            val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(result)

            _drawingPaths.value.forEach { dp ->
                if (dp.points.size >= 2) {
                    val paint = Paint().apply {
                        color       = dp.color.toArgb()
                        strokeWidth = dp.strokeWidth * result.width / 400f
                        style       = Paint.Style.STROKE
                        strokeCap   = Paint.Cap.ROUND
                        strokeJoin  = Paint.Join.ROUND
                        isAntiAlias = true
                    }
                    val path = android.graphics.Path()
                    path.moveTo(dp.points[0].x * result.width, dp.points[0].y * result.height)
                    for (i in 1 until dp.points.size) {
                        path.lineTo(dp.points[i].x * result.width, dp.points[i].y * result.height)
                    }
                    canvas.drawPath(path, paint)
                }
            }

            _textElements.value.forEach { el ->
                val paint = Paint().apply {
                    color       = el.color.toArgb()
                    textSize    = el.size * result.width / 10f
                    isAntiAlias = true
                    typeface    = Typeface.DEFAULT_BOLD
                    setShadowLayer(4f, 1f, 1f, android.graphics.Color.argb(120, 0, 0, 0))
                }
                canvas.drawText(el.text, el.x * result.width, el.y * result.height, paint)
            }

            withContext(Dispatchers.IO) {
                val file = File(context.cacheDir, "edited_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { result.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                file
            }
        } catch (e: Exception) {
            Log.e(TAG, "applyAndSaveImage: ${e.message}", e)
            null
        }
    }

    suspend fun saveOriginalImage(context: Context): File? = withContext(Dispatchers.IO) {
        try {
            val bitmap = _originalBitmap.value ?: return@withContext null
            val file   = File(context.cacheDir, "original_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            file
        } catch (e: Exception) {
            Log.e(TAG, "saveOriginalImage: ${e.message}", e)
            null
        }
    }

    // Keep the old sync API for compatibility, but route through the async version
    fun saveImage(context: Context): File? {
        // Blocking call — only use from background thread
        return try {
            val bitmap = _editedBitmap.value ?: return null
            val file   = File(context.cacheDir, "edited_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            file
        } catch (e: Exception) {
            Log.e(TAG, "saveImage: ${e.message}", e)
            null
        }
    }

    // ── Undo / Redo ───────────────────────────────────────────────────────────

    fun undo() {
        if (undoStack.isNotEmpty()) {
            redoStack.add(captureCurrentState())
            restoreState(undoStack.removeAt(undoStack.lastIndex))
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            undoStack.add(captureCurrentState())
            restoreState(redoStack.removeAt(redoStack.lastIndex))
        }
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    private fun saveStateForUndo() {
        undoStack.add(captureCurrentState())
        redoStack.clear()
        if (undoStack.size > 10) undoStack.removeAt(0)
    }

    private fun captureCurrentState() = EditorState(
        bitmap        = _editedBitmap.value?.copy(Bitmap.Config.ARGB_8888, true),
        drawingPaths  = _drawingPaths.value.toList(),
        textElements  = _textElements.value.toList(),
        filter        = _selectedFilter.value,
        brightness    = _brightness.value,
        contrast      = _contrast.value,
        saturation    = _saturation.value,
        warmth        = _warmth.value,
        rotationAngle = _rotationAngle.value,
        cropRect      = _cropRect.value
    )

    private fun restoreState(state: EditorState) {
        _editedBitmap.value   = state.bitmap?.copy(Bitmap.Config.ARGB_8888, true)
        _drawingPaths.value   = state.drawingPaths
        _textElements.value   = state.textElements
        _selectedFilter.value = state.filter
        _brightness.value     = state.brightness
        _contrast.value       = state.contrast
        _saturation.value     = state.saturation
        _warmth.value         = state.warmth
        _rotationAngle.value  = state.rotationAngle
        _cropRect.value       = state.cropRect
    }

    // ── Filter implementations ────────────────────────────────────────────────

    private fun applyColorMatrix(bitmap: Bitmap, cm: ColorMatrix): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(result).drawBitmap(bitmap, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(cm) })
        bitmap.recycle()
        return result
    }

    private fun applyGrayscale(bitmap: Bitmap) =
        applyColorMatrix(bitmap, ColorMatrix().apply { setSaturation(0f) })

    private fun applySepia(bitmap: Bitmap) =
        applyColorMatrix(bitmap, ColorMatrix(floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f,     0f,     0f,     1f, 0f
        )))

    private fun applyInvert(bitmap: Bitmap) =
        applyColorMatrix(bitmap, ColorMatrix(floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
             0f,-1f, 0f, 0f, 255f,
             0f, 0f,-1f, 0f, 255f,
             0f, 0f, 0f, 1f,   0f
        )))

    // Fast blur via scale-down trick — O(n) instead of O(n·r²), negligible quality loss
    private fun applyBlurFast(bitmap: Bitmap, radius: Int): Bitmap {
        val factor = (radius / 2).coerceIn(2, 8)
        val w = (bitmap.width  / factor).coerceAtLeast(1)
        val h = (bitmap.height / factor).coerceAtLeast(1)
        val small  = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val result = Bitmap.createScaledBitmap(small, bitmap.width, bitmap.height, true)
        small.recycle()
        bitmap.recycle()
        return result
    }

    private fun applySharpen(bitmap: Bitmap): Bitmap {
        val result  = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val width   = bitmap.width
        val height  = bitmap.height
        val pixels  = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val out = IntArray(width * height)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val c = pixels[y * width + x]
                val t = pixels[(y - 1) * width + x]
                val b = pixels[(y + 1) * width + x]
                val l = pixels[y * width + (x - 1)]
                val r = pixels[y * width + (x + 1)]
                fun ch(s: Int) = (s shr 16 and 0xFF) to (s shr 8 and 0xFF) to (s and 0xFF)
                val rr = (5 * (c shr 16 and 0xFF) - (t shr 16 and 0xFF) - (b shr 16 and 0xFF) - (l shr 16 and 0xFF) - (r shr 16 and 0xFF)).coerceIn(0, 255)
                val gg = (5 * (c shr 8  and 0xFF) - (t shr 8  and 0xFF) - (b shr 8  and 0xFF) - (l shr 8  and 0xFF) - (r shr 8  and 0xFF)).coerceIn(0, 255)
                val bb = (5 * (c and 0xFF)        - (t and 0xFF)        - (b and 0xFF)        - (l and 0xFF)        - (r and 0xFF)).coerceIn(0, 255)
                out[y * width + x] = (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
            }
        }
        result.setPixels(out, 0, width, 0, 0, width, height)
        bitmap.recycle()
        return result
    }

    // Vivid: boost saturation + slight contrast
    private fun applyVivid(bitmap: Bitmap): Bitmap {
        val cm = ColorMatrix()
        cm.setSaturation(1.8f)
        val c = 1.15f; val t = (1f - c) / 2f * 255
        cm.postConcat(ColorMatrix(floatArrayOf(
            c, 0f, 0f, 0f, t,  0f, c, 0f, 0f, t,  0f, 0f, c, 0f, t,  0f, 0f, 0f, 1f, 0f
        )))
        return applyColorMatrix(bitmap, cm)
    }
}

// ── Data classes ──────────────────────────────────────────────────────────────

data class EditorState(
    val bitmap:        Bitmap?,
    val drawingPaths:  List<DrawPath>,
    val textElements:  List<TextElement>,
    val filter:        PhotoFilter,
    val brightness:    Float,
    val contrast:      Float,
    val saturation:    Float,
    val warmth:        Float,
    val rotationAngle: Int,
    val cropRect:      CropRect
)

data class DrawPath(
    val points:      List<Offset>,
    val color:       Color,
    val strokeWidth: Float
)

data class TextElement(
    val text:  String,
    val color: Color,
    val size:  Float,
    val x:     Float,  // normalized 0-1
    val y:     Float   // normalized 0-1
)

data class CropRect(
    val left:   Float,
    val top:    Float,
    val right:  Float,
    val bottom: Float
)

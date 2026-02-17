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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

/**
 * PHOTO EDITOR VIEWMODEL
 *
 * Manages photo editing state and operations:
 * - Image loading and manipulation
 * - Drawing paths with normalized coordinates
 * - Text elements
 * - Filters and adjustments
 * - Undo/Redo stack
 * - Save with multiple options
 */
class PhotoEditorViewModel : ViewModel() {
    private val TAG = "PhotoEditorViewModel"

    // Image state
    private val _originalBitmap = MutableStateFlow<Bitmap?>(null)
    private val _editedBitmap = MutableStateFlow<Bitmap?>(null)
    val editedBitmap: StateFlow<Bitmap?> = _editedBitmap.asStateFlow()

    // Tool state
    private val _currentTool = MutableStateFlow(EditorTool.DRAW)
    val currentTool: StateFlow<EditorTool> = _currentTool.asStateFlow()

    // Drawing state - uses normalized points (0-1) for resolution independence
    private val _drawingPaths = MutableStateFlow<List<DrawPath>>(emptyList())
    val drawingPaths: StateFlow<List<DrawPath>> = _drawingPaths.asStateFlow()

    private val _selectedColor = MutableStateFlow(Color.Red)
    val selectedColor: StateFlow<Color> = _selectedColor.asStateFlow()

    private val _brushSize = MutableStateFlow(10f)
    val brushSize: StateFlow<Float> = _brushSize.asStateFlow()

    // Text state
    private val _textElements = MutableStateFlow<List<TextElement>>(emptyList())
    val textElements: StateFlow<List<TextElement>> = _textElements.asStateFlow()

    // Filter state
    private val _selectedFilter = MutableStateFlow(PhotoFilter.NONE)
    val selectedFilter: StateFlow<PhotoFilter> = _selectedFilter.asStateFlow()

    // Adjustment state
    private val _brightness = MutableStateFlow(0f)
    val brightness: StateFlow<Float> = _brightness.asStateFlow()

    private val _contrast = MutableStateFlow(1f)
    val contrast: StateFlow<Float> = _contrast.asStateFlow()

    private val _saturation = MutableStateFlow(1f)
    val saturation: StateFlow<Float> = _saturation.asStateFlow()

    // Rotation state
    private val _rotationAngle = MutableStateFlow(0)
    val rotationAngle: StateFlow<Int> = _rotationAngle.asStateFlow()

    // Undo/Redo stacks
    private val undoStack = mutableListOf<EditorState>()
    private val redoStack = mutableListOf<EditorState>()

    /**
     * Load image from URL or file path
     */
    fun loadImage(imageUrl: String, context: Context) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Loading image: $imageUrl")

                val bitmap = withContext(Dispatchers.IO) {
                    if (imageUrl.startsWith("http")) {
                        val url = URL(imageUrl)
                        BitmapFactory.decodeStream(url.openStream())
                    } else {
                        BitmapFactory.decodeFile(imageUrl)
                    }
                }

                if (bitmap != null) {
                    _originalBitmap.value = bitmap
                    _editedBitmap.value = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                    Log.d(TAG, "Image loaded: ${bitmap.width}x${bitmap.height}")
                } else {
                    Log.e(TAG, "Failed to load bitmap")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading image: ${e.message}", e)
            }
        }
    }

    fun selectTool(tool: EditorTool) {
        _currentTool.value = tool
    }

    /**
     * Add drawing path from canvas points (normalized to 0-1 range)
     */
    fun addDrawPathFromPoints(
        canvasPoints: List<Offset>,
        canvasSize: IntSize,
        color: Color,
        strokeWidth: Float
    ) {
        if (canvasPoints.size < 2 || canvasSize.width == 0 || canvasSize.height == 0) return

        saveStateForUndo()

        // Normalize points to 0-1 range
        val normalizedPoints = canvasPoints.map { point ->
            Offset(
                point.x / canvasSize.width.toFloat(),
                point.y / canvasSize.height.toFloat()
            )
        }

        val drawPath = DrawPath(normalizedPoints, color, strokeWidth)
        _drawingPaths.value = _drawingPaths.value + drawPath
        Log.d(TAG, "Added draw path with ${normalizedPoints.size} points")
    }

    /**
     * Apply all drawings and text to the bitmap (for saving)
     */
    fun applyAllDrawingsTobitmap() {
        val bitmap = _editedBitmap.value ?: return
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        // Draw all paths
        _drawingPaths.value.forEach { drawPath ->
            val points = drawPath.points
            if (points.size >= 2) {
                val paint = Paint().apply {
                    this.color = drawPath.color.toArgb()
                    this.strokeWidth = drawPath.strokeWidth * result.width / 400f // Scale to bitmap
                    this.style = Paint.Style.STROKE
                    this.strokeCap = Paint.Cap.ROUND
                    this.strokeJoin = Paint.Join.ROUND
                    this.isAntiAlias = true
                }

                val path = android.graphics.Path()
                path.moveTo(points[0].x * result.width, points[0].y * result.height)
                for (i in 1 until points.size) {
                    path.lineTo(points[i].x * result.width, points[i].y * result.height)
                }
                canvas.drawPath(path, paint)
            }
        }

        // Draw all text elements
        _textElements.value.forEach { element ->
            val paint = Paint().apply {
                this.color = element.color.toArgb()
                this.textSize = element.size * result.width / 10f
                this.isAntiAlias = true
                this.typeface = Typeface.DEFAULT_BOLD
            }
            canvas.drawText(element.text, element.x * result.width, element.y * result.height, paint)
        }

        _editedBitmap.value = result
        Log.d(TAG, "Applied all drawings to bitmap")
    }

    @Deprecated("Use addDrawPathFromPoints instead")
    fun addDrawPath(path: Path) {
        // Legacy - not used with new point-based system
    }

    fun setDrawColor(color: Color) {
        _selectedColor.value = color
    }

    fun setBrushSize(size: Float) {
        _brushSize.value = size
    }

    fun addText(text: String, color: Color, size: Float) {
        saveStateForUndo()
        val textElement = TextElement(text, color, size, 0.5f, 0.5f)
        _textElements.value = _textElements.value + textElement
    }

    fun addTextAt(text: String, x: Float, y: Float) {
        saveStateForUndo()
        val textElement = TextElement(text, _selectedColor.value, 24f, x, y)
        _textElements.value = _textElements.value + textElement
    }

    fun addSticker(sticker: String) {
        saveStateForUndo()
        val textElement = TextElement(sticker, Color.White, 48f, 0.5f, 0.5f)
        _textElements.value = _textElements.value + textElement
        Log.d(TAG, "Added sticker: $sticker")
    }

    fun applyFilter(filter: PhotoFilter) {
        saveStateForUndo()
        _selectedFilter.value = filter

        val original = _originalBitmap.value ?: return
        val filtered = original.copy(Bitmap.Config.ARGB_8888, true)

        _editedBitmap.value = when (filter) {
            PhotoFilter.NONE -> filtered
            PhotoFilter.GRAYSCALE -> applyGrayscale(filtered)
            PhotoFilter.SEPIA -> applySepia(filtered)
            PhotoFilter.INVERT -> applyInvert(filtered)
            PhotoFilter.BLUR -> applyBlur(filtered)
            PhotoFilter.SHARPEN -> applySharpen(filtered)
        }

        Log.d(TAG, "Applied filter: ${filter.displayName}")
    }

    fun setBrightness(value: Float) {
        _brightness.value = value
        applyAdjustments()
    }

    fun setContrast(value: Float) {
        _contrast.value = value
        applyAdjustments()
    }

    fun setSaturation(value: Float) {
        _saturation.value = value
        applyAdjustments()
    }

    fun rotate(degrees: Int) {
        saveStateForUndo()
        _rotationAngle.value = (_rotationAngle.value + degrees) % 360

        val current = _editedBitmap.value ?: return
        val matrix = Matrix().apply {
            postRotate(degrees.toFloat())
        }

        val rotated = Bitmap.createBitmap(
            current, 0, 0, current.width, current.height, matrix, true
        )

        _editedBitmap.value = rotated
        Log.d(TAG, "Rotated ${degrees} (total: ${_rotationAngle.value})")
    }

    fun applyCrop() {
        saveStateForUndo()
        Log.d(TAG, "Crop not yet implemented")
    }

    /**
     * Save edited image to cache
     */
    fun saveImage(context: Context): File? {
        try {
            val bitmap = _editedBitmap.value ?: return null

            val outputDir = context.cacheDir
            val outputFile = File(outputDir, "edited_${System.currentTimeMillis()}.jpg")

            FileOutputStream(outputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }

            Log.d(TAG, "Image saved: ${outputFile.absolutePath}")
            return outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Error saving image: ${e.message}", e)
            return null
        }
    }

    /**
     * Save original (unedited) image to cache
     */
    fun saveOriginalImage(context: Context): File? {
        try {
            val bitmap = _originalBitmap.value ?: return null

            val outputDir = context.cacheDir
            val outputFile = File(outputDir, "original_${System.currentTimeMillis()}.jpg")

            FileOutputStream(outputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }

            Log.d(TAG, "Original image saved: ${outputFile.absolutePath}")
            return outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Error saving original: ${e.message}", e)
            return null
        }
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val currentState = captureCurrentState()
            redoStack.add(currentState)

            val previousState = undoStack.removeAt(undoStack.lastIndex)
            restoreState(previousState)
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val currentState = captureCurrentState()
            undoStack.add(currentState)

            val nextState = redoStack.removeAt(redoStack.lastIndex)
            restoreState(nextState)
        }
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    // Private helpers

    private fun saveStateForUndo() {
        val state = captureCurrentState()
        undoStack.add(state)
        redoStack.clear()
        if (undoStack.size > 20) {
            undoStack.removeAt(0)
        }
    }

    private fun captureCurrentState(): EditorState {
        val bitmap = _editedBitmap.value?.copy(Bitmap.Config.ARGB_8888, true)
        return EditorState(
            bitmap = bitmap,
            drawingPaths = _drawingPaths.value.toList(),
            textElements = _textElements.value.toList(),
            filter = _selectedFilter.value,
            brightness = _brightness.value,
            contrast = _contrast.value,
            saturation = _saturation.value,
            rotationAngle = _rotationAngle.value
        )
    }

    private fun restoreState(state: EditorState) {
        _editedBitmap.value = state.bitmap?.copy(Bitmap.Config.ARGB_8888, true)
        _drawingPaths.value = state.drawingPaths
        _textElements.value = state.textElements
        _selectedFilter.value = state.filter
        _brightness.value = state.brightness
        _contrast.value = state.contrast
        _saturation.value = state.saturation
        _rotationAngle.value = state.rotationAngle
    }

    private fun applyAdjustments() {
        val original = _originalBitmap.value ?: return
        val adjusted = original.copy(Bitmap.Config.ARGB_8888, true)

        val colorMatrix = ColorMatrix().apply {
            val b = _brightness.value * 255
            postConcat(ColorMatrix().apply {
                set(floatArrayOf(
                    1f, 0f, 0f, 0f, b,
                    0f, 1f, 0f, 0f, b,
                    0f, 0f, 1f, 0f, b,
                    0f, 0f, 0f, 1f, 0f
                ))
            })

            val c = _contrast.value
            val t = (1f - c) / 2f * 255
            postConcat(ColorMatrix().apply {
                set(floatArrayOf(
                    c, 0f, 0f, 0f, t,
                    0f, c, 0f, 0f, t,
                    0f, 0f, c, 0f, t,
                    0f, 0f, 0f, 1f, 0f
                ))
            })

            setSaturation(_saturation.value)
        }

        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }

        val result = Bitmap.createBitmap(adjusted.width, adjusted.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(adjusted, 0f, 0f, paint)

        _editedBitmap.value = result
    }

    // Filter implementations

    private fun applyGrayscale(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val colorMatrix = ColorMatrix().apply { setSaturation(0f) }
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(colorMatrix) }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    private fun applySepia(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val colorMatrix = ColorMatrix().apply {
            set(floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(colorMatrix) }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    private fun applyInvert(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val colorMatrix = ColorMatrix().apply {
            set(floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(colorMatrix) }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    private fun applyBlur(bitmap: Bitmap): Bitmap {
        // Simple averaging blur (no RenderScript needed)
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val radius = 3
        val resultPixels = IntArray(width * height)
        for (y in radius until height - radius) {
            for (x in radius until width - radius) {
                var r = 0; var g = 0; var b = 0; var count = 0
                for (dy in -radius..radius) {
                    for (dx in -radius..radius) {
                        val pixel = pixels[(y + dy) * width + (x + dx)]
                        r += (pixel shr 16) and 0xFF
                        g += (pixel shr 8) and 0xFF
                        b += pixel and 0xFF
                        count++
                    }
                }
                resultPixels[y * width + x] = (0xFF shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
            }
        }
        result.setPixels(resultPixels, 0, width, 0, 0, width, height)
        return result
    }

    private fun applySharpen(bitmap: Bitmap): Bitmap {
        // Sharpen using unsharp mask principle
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val resultPixels = IntArray(width * height)
        // Sharpen kernel: center=5, adjacent=-1
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val center = pixels[y * width + x]
                val top = pixels[(y - 1) * width + x]
                val bottom = pixels[(y + 1) * width + x]
                val left = pixels[y * width + (x - 1)]
                val right = pixels[y * width + (x + 1)]

                val r = (5 * ((center shr 16) and 0xFF) - ((top shr 16) and 0xFF) - ((bottom shr 16) and 0xFF) - ((left shr 16) and 0xFF) - ((right shr 16) and 0xFF)).coerceIn(0, 255)
                val g = (5 * ((center shr 8) and 0xFF) - ((top shr 8) and 0xFF) - ((bottom shr 8) and 0xFF) - ((left shr 8) and 0xFF) - ((right shr 8) and 0xFF)).coerceIn(0, 255)
                val b2 = (5 * (center and 0xFF) - (top and 0xFF) - (bottom and 0xFF) - (left and 0xFF) - (right and 0xFF)).coerceIn(0, 255)
                resultPixels[y * width + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b2
            }
        }
        result.setPixels(resultPixels, 0, width, 0, 0, width, height)
        return result
    }
}

/**
 * Editor State (for undo/redo)
 */
data class EditorState(
    val bitmap: Bitmap?,
    val drawingPaths: List<DrawPath>,
    val textElements: List<TextElement>,
    val filter: PhotoFilter,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val rotationAngle: Int
)

/**
 * Drawing Path - uses normalized points (0-1)
 */
data class DrawPath(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float
)

/**
 * Text Element
 */
data class TextElement(
    val text: String,
    val color: Color,
    val size: Float,
    val x: Float,  // Normalized 0-1
    val y: Float   // Normalized 0-1
)

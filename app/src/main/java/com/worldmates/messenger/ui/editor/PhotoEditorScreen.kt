package com.worldmates.messenger.ui.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.annotation.StringRes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.worldmates.messenger.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import java.io.File
import java.io.FileOutputStream

/**
 * PHOTO EDITOR SCREEN
 *
 * Полнофункциональный редактор фото с:
 * - Рисование (draw) - реально працює
 * - Фильтры (filters)
 * - Яркость/Контраст/Насыщенность
 * - Поворот (rotate)
 * - Текст (text)
 * - Стикеры (stickers)
 * - Зберігання з діалогом вибору
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoEditorScreen(
    imageUrl: String,
    onDismiss: () -> Unit,
    onSave: (File) -> Unit,
    viewModel: PhotoEditorViewModel = viewModel()
) {
    val context = LocalContext.current
    val currentTool by viewModel.currentTool.collectAsState()
    val brightness by viewModel.brightness.collectAsState()
    val contrast by viewModel.contrast.collectAsState()
    val saturation by viewModel.saturation.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val rotationAngle by viewModel.rotationAngle.collectAsState()
    val drawingPaths by viewModel.drawingPaths.collectAsState()
    val textElements by viewModel.textElements.collectAsState()
    val selectedColor by viewModel.selectedColor.collectAsState()
    val brushSize by viewModel.brushSize.collectAsState()

    var showSaveDialog by remember { mutableStateOf(false) }

    LaunchedEffect(imageUrl) {
        viewModel.loadImage(imageUrl, context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.photo_editor), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, stringResource(R.string.close_cd))
                    }
                },
                actions = {
                    // Undo button
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = viewModel.canUndo()
                    ) {
                        Icon(Icons.Default.Undo, stringResource(R.string.cancel))
                    }

                    // Redo button
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = viewModel.canRedo()
                    ) {
                        Icon(Icons.Default.Redo, stringResource(R.string.save))
                    }

                    // Save button
                    IconButton(onClick = { showSaveDialog = true }) {
                        Icon(Icons.Default.Check, stringResource(R.string.save))
                    }
                }
            )
        },
        bottomBar = {
            Column {
                // Tool-specific controls
                when (currentTool) {
                    EditorTool.DRAW -> DrawControls(
                        selectedColor = selectedColor,
                        brushSize = brushSize,
                        isErasing = selectedColor == eraserColor,
                        onColorChange = { viewModel.setDrawColor(it) },
                        onBrushSizeChange = { viewModel.setBrushSize(it) },
                        onToggleEraser = {
                            val newColor = if (selectedColor == eraserColor) Color.Red else eraserColor
                            viewModel.setDrawColor(newColor)
                        }
                    )
                    EditorTool.FILTER -> FilterControls(
                        selectedFilter = selectedFilter,
                        onFilterChange = { viewModel.applyFilter(it) }
                    )
                    EditorTool.ADJUST -> AdjustmentControls(
                        brightness = brightness,
                        contrast = contrast,
                        saturation = saturation,
                        onBrightnessChange = { viewModel.setBrightness(it) },
                        onContrastChange = { viewModel.setContrast(it) },
                        onSaturationChange = { viewModel.setSaturation(it) }
                    )
                    EditorTool.ROTATE -> RotateControls(
                        rotationAngle = rotationAngle,
                        onRotate = { viewModel.rotate(it) }
                    )
                    EditorTool.TEXT -> TextControls(
                        onAddText = { text, color, size ->
                            viewModel.addText(text, color, size)
                        }
                    )
                    EditorTool.STICKER -> StickerControls(
                        onAddSticker = { sticker ->
                            viewModel.addSticker(sticker)
                        }
                    )
                    EditorTool.CROP -> CropControls(
                        onCrop = { viewModel.applyCrop() },
                        onReset = { viewModel.resetCrop() }
                    )
                }

                HorizontalDivider()

                // Tool selector
                ToolSelector(
                    currentTool = currentTool,
                    onToolSelected = { viewModel.selectTool(it) }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black)
        ) {
            // Canvas for editing
            PhotoEditorCanvas(
                viewModel = viewModel,
                currentTool = currentTool,
                drawingPaths = drawingPaths,
                textElements = textElements,
                selectedColor = selectedColor,
                brushSize = brushSize
            )
        }
    }

    // Save dialog with 3 options
    if (showSaveDialog) {
        SaveDialog(
            onSaveModified = {
                showSaveDialog = false
                viewModel.applyAllDrawingsTobitmap()
                val savedFile = viewModel.saveImage(context)
                if (savedFile != null) {
                    onSave(savedFile)
                } else {
                    android.widget.Toast.makeText(context, context.getString(R.string.save_error), android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            onSaveBoth = {
                showSaveDialog = false
                // Save original first
                val originalFile = viewModel.saveOriginalImage(context)
                // Apply drawings and save modified
                viewModel.applyAllDrawingsTobitmap()
                val modifiedFile = viewModel.saveImage(context)
                if (modifiedFile != null) {
                    android.widget.Toast.makeText(context, context.getString(R.string.save_both_success), android.widget.Toast.LENGTH_SHORT).show()
                    onSave(modifiedFile)
                }
            },
            onDontSave = {
                showSaveDialog = false
                onDismiss()
            },
            onDismiss = { showSaveDialog = false }
        )
    }
}

/**
 * Save dialog with 3 options
 */
@Composable
private fun SaveDialog(
    onSaveModified: () -> Unit,
    onSaveBoth: () -> Unit,
    onDontSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Save, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.save_photo_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.save_choose_option))

                // Save modified only
                Surface(
                    onClick = onSaveModified,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text(stringResource(R.string.save_modified), fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.save_modified_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Save both
                Surface(
                    onClick = onSaveBoth,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.FileCopy, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column {
                            Text(stringResource(R.string.save_both), fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.save_both_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDontSave) {
                Text(stringResource(R.string.dont_save))
            }
        }
    )
}

// Which part of the crop rect is being dragged
private enum class CropDragHandle {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
    LEFT_EDGE, RIGHT_EDGE, TOP_EDGE, BOTTOM_EDGE,
    MOVE
}

/**
 * Photo Editor Canvas with real drawing support
 */
@Composable
private fun PhotoEditorCanvas(
    viewModel: PhotoEditorViewModel,
    currentTool: EditorTool,
    drawingPaths: List<DrawPath>,
    textElements: List<TextElement>,
    selectedColor: Color,
    brushSize: Float
) {
    val editedBitmap by viewModel.editedBitmap.collectAsState()
    val cropRect by viewModel.cropRect.collectAsState()

    // Current path being drawn
    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var cropDragHandle by remember { mutableStateOf<CropDragHandle?>(null) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        editedBitmap?.let { bitmap ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasSize = it }
            ) {
                // Display base image
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Edited photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                // Drawing overlay Canvas
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(currentTool, selectedColor, brushSize) {
                            when (currentTool) {
                                EditorTool.DRAW -> detectDragGestures(
                                    onDragStart = { offset ->
                                        currentPoints = listOf(offset)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        currentPoints = currentPoints + change.position
                                    },
                                    onDragEnd = {
                                        if (currentPoints.size >= 2) {
                                            viewModel.addDrawPathFromPoints(
                                                currentPoints,
                                                canvasSize,
                                                selectedColor,
                                                brushSize
                                            )
                                        }
                                        currentPoints = emptyList()
                                    },
                                    onDragCancel = {
                                        currentPoints = emptyList()
                                    }
                                )
                                EditorTool.CROP -> detectDragGestures(
                                    onDragStart = { offset ->
                                        val w = canvasSize.width.toFloat()
                                        val h = canvasSize.height.toFloat()
                                        if (w == 0f || h == 0f) return@detectDragGestures
                                        val r = viewModel.cropRect.value
                                        val l = r.left * w;  val t = r.top * h
                                        val ri = r.right * w; val b = r.bottom * h
                                        val tolerance = 48f
                                        fun near(a: Float, v: Float) = kotlin.math.abs(a - v) < tolerance
                                        cropDragHandle = when {
                                            near(offset.x, l)  && near(offset.y, t)  -> CropDragHandle.TOP_LEFT
                                            near(offset.x, ri) && near(offset.y, t)  -> CropDragHandle.TOP_RIGHT
                                            near(offset.x, l)  && near(offset.y, b)  -> CropDragHandle.BOTTOM_LEFT
                                            near(offset.x, ri) && near(offset.y, b)  -> CropDragHandle.BOTTOM_RIGHT
                                            near(offset.x, l)                        -> CropDragHandle.LEFT_EDGE
                                            near(offset.x, ri)                       -> CropDragHandle.RIGHT_EDGE
                                            near(offset.y, t)                        -> CropDragHandle.TOP_EDGE
                                            near(offset.y, b)                        -> CropDragHandle.BOTTOM_EDGE
                                            else                                     -> CropDragHandle.MOVE
                                        }
                                    },
                                    onDrag = { change, delta ->
                                        change.consume()
                                        val w = canvasSize.width.toFloat()
                                        val h = canvasSize.height.toFloat()
                                        if (w == 0f || h == 0f) return@detectDragGestures
                                        val dx = delta.x / w
                                        val dy = delta.y / h
                                        val r = viewModel.cropRect.value
                                        when (cropDragHandle) {
                                            CropDragHandle.TOP_LEFT     -> viewModel.updateCropRect(r.left + dx, r.top + dy, r.right, r.bottom)
                                            CropDragHandle.TOP_RIGHT    -> viewModel.updateCropRect(r.left, r.top + dy, r.right + dx, r.bottom)
                                            CropDragHandle.BOTTOM_LEFT  -> viewModel.updateCropRect(r.left + dx, r.top, r.right, r.bottom + dy)
                                            CropDragHandle.BOTTOM_RIGHT -> viewModel.updateCropRect(r.left, r.top, r.right + dx, r.bottom + dy)
                                            CropDragHandle.LEFT_EDGE    -> viewModel.updateCropRect(r.left + dx, r.top, r.right, r.bottom)
                                            CropDragHandle.RIGHT_EDGE   -> viewModel.updateCropRect(r.left, r.top, r.right + dx, r.bottom)
                                            CropDragHandle.TOP_EDGE     -> viewModel.updateCropRect(r.left, r.top + dy, r.right, r.bottom)
                                            CropDragHandle.BOTTOM_EDGE  -> viewModel.updateCropRect(r.left, r.top, r.right, r.bottom + dy)
                                            CropDragHandle.MOVE         -> viewModel.updateCropRect(r.left + dx, r.top + dy, r.right + dx, r.bottom + dy)
                                            null                        -> {}
                                        }
                                    },
                                    onDragEnd   = { cropDragHandle = null },
                                    onDragCancel = { cropDragHandle = null }
                                )
                                else -> {}
                            }
                        }
                ) {
                    // Draw saved paths
                    drawingPaths.forEach { drawPath ->
                        val path = Path()
                        val points = drawPath.points
                        if (points.size >= 2) {
                            val scaleX = size.width
                            val scaleY = size.height
                            path.moveTo(points[0].x * scaleX, points[0].y * scaleY)
                            for (i in 1 until points.size) {
                                path.lineTo(points[i].x * scaleX, points[i].y * scaleY)
                            }
                            val isEraser = drawPath.color == eraserColor
                            drawPath(
                                path = path,
                                color = if (isEraser) Color.Transparent else drawPath.color,
                                style = Stroke(
                                    width = drawPath.strokeWidth,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                ),
                                blendMode = if (isEraser) BlendMode.Clear else BlendMode.SrcOver
                            )
                        }
                    }

                    // Draw current active path
                    if (currentPoints.size >= 2) {
                        val activePath = Path()
                        activePath.moveTo(currentPoints[0].x, currentPoints[0].y)
                        for (i in 1 until currentPoints.size) {
                            activePath.lineTo(currentPoints[i].x, currentPoints[i].y)
                        }
                        drawPath(
                            path = activePath,
                            color = selectedColor,
                            style = Stroke(
                                width = brushSize,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }

                    // Draw text elements
                    textElements.forEach { element ->
                        drawContext.canvas.nativeCanvas.apply {
                            val paint = android.graphics.Paint().apply {
                                color = element.color.toArgb()
                                textSize = element.size * 3f
                                isAntiAlias = true
                                typeface = android.graphics.Typeface.DEFAULT_BOLD
                            }
                            drawText(
                                element.text,
                                element.x * size.width,
                                element.y * size.height,
                                paint
                            )
                        }
                    }

                    // Crop overlay — drawn on top when CROP tool is active
                    if (currentTool == EditorTool.CROP) {
                        val cr = cropRect
                        val cLeft   = cr.left   * size.width
                        val cTop    = cr.top    * size.height
                        val cRight  = cr.right  * size.width
                        val cBottom = cr.bottom * size.height
                        val cW = cRight - cLeft
                        val cH = cBottom - cTop
                        val dimColor = Color.Black.copy(alpha = 0.55f)

                        // Dim outside crop rect (4 rectangles)
                        drawRect(dimColor, topLeft = Offset(0f, 0f),     size = androidx.compose.ui.geometry.Size(size.width, cTop))
                        drawRect(dimColor, topLeft = Offset(0f, cBottom), size = androidx.compose.ui.geometry.Size(size.width, size.height - cBottom))
                        drawRect(dimColor, topLeft = Offset(0f, cTop),    size = androidx.compose.ui.geometry.Size(cLeft, cH))
                        drawRect(dimColor, topLeft = Offset(cRight, cTop), size = androidx.compose.ui.geometry.Size(size.width - cRight, cH))

                        // Crop border
                        drawRect(
                            color = Color.White,
                            topLeft = Offset(cLeft, cTop),
                            size = androidx.compose.ui.geometry.Size(cW, cH),
                            style = Stroke(width = 2f)
                        )

                        // Rule-of-thirds grid lines
                        val gridAlpha = Color.White.copy(alpha = 0.4f)
                        drawLine(gridAlpha, Offset(cLeft + cW / 3, cTop), Offset(cLeft + cW / 3, cBottom), 1f)
                        drawLine(gridAlpha, Offset(cLeft + 2 * cW / 3, cTop), Offset(cLeft + 2 * cW / 3, cBottom), 1f)
                        drawLine(gridAlpha, Offset(cLeft, cTop + cH / 3), Offset(cRight, cTop + cH / 3), 1f)
                        drawLine(gridAlpha, Offset(cLeft, cTop + 2 * cH / 3), Offset(cRight, cTop + 2 * cH / 3), 1f)

                        // Corner handles
                        val hs = 28f  // handle length in px
                        val hw = 4f   // handle stroke width
                        val handleColor = Color.White
                        // Top-left
                        drawLine(handleColor, Offset(cLeft, cTop), Offset(cLeft + hs, cTop), hw)
                        drawLine(handleColor, Offset(cLeft, cTop), Offset(cLeft, cTop + hs), hw)
                        // Top-right
                        drawLine(handleColor, Offset(cRight, cTop), Offset(cRight - hs, cTop), hw)
                        drawLine(handleColor, Offset(cRight, cTop), Offset(cRight, cTop + hs), hw)
                        // Bottom-left
                        drawLine(handleColor, Offset(cLeft, cBottom), Offset(cLeft + hs, cBottom), hw)
                        drawLine(handleColor, Offset(cLeft, cBottom), Offset(cLeft, cBottom - hs), hw)
                        // Bottom-right
                        drawLine(handleColor, Offset(cRight, cBottom), Offset(cRight - hs, cBottom), hw)
                        drawLine(handleColor, Offset(cRight, cBottom), Offset(cRight, cBottom - hs), hw)
                    }
                }
            }
        } ?: run {
            // Loading state
            CircularProgressIndicator(color = Color.White)
        }
    }
}

/**
 * Tool Selector
 */
@Composable
private fun ToolSelector(
    currentTool: EditorTool,
    onToolSelected: (EditorTool) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        items(EditorTool.values()) { tool ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .clickable { onToolSelected(tool) }
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (currentTool == tool) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = tool.icon,
                            contentDescription = stringResource(tool.labelRes),
                            tint = if (currentTool == tool) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(tool.labelRes),
                    fontSize = 11.sp,
                    color = if (currentTool == tool) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}

/**
 * Draw Controls — color picker + brush size + eraser toggle
 */
@Composable
private fun DrawControls(
    selectedColor: Color,
    brushSize: Float,
    isErasing: Boolean,
    onColorChange: (Color) -> Unit,
    onBrushSizeChange: (Float) -> Unit,
    onToggleEraser: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Кисть:", fontWeight = FontWeight.Bold, modifier = Modifier.width(52.dp))

            // Color swatches
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(drawingColors) { color ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (!isErasing && color == selectedColor) 3.dp else 1.dp,
                                color = if (!isErasing && color == selectedColor) Color.White else Color.Gray,
                                shape = CircleShape
                            )
                            .clickable { onColorChange(color) }
                    )
                }
            }

            // Eraser toggle button
            IconButton(
                onClick = onToggleEraser,
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = if (isErasing) MaterialTheme.colorScheme.errorContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    )
            ) {
                Icon(
                    Icons.Default.AutoFixOff,
                    contentDescription = "Ластик",
                    tint = if (isErasing) MaterialTheme.colorScheme.error
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Brush size slider
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(if (isErasing) "Ластик:" else "Розмір:", fontSize = 13.sp, modifier = Modifier.width(52.dp))
            Slider(
                value = brushSize,
                onValueChange = onBrushSizeChange,
                valueRange = 2f..60f,
                modifier = Modifier.weight(1f)
            )
            Text("${brushSize.toInt()}px", fontSize = 13.sp, modifier = Modifier.width(42.dp))
        }
    }
}

/**
 * Filter Controls
 */
@Composable
private fun FilterControls(
    selectedFilter: PhotoFilter,
    onFilterChange: (PhotoFilter) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(PhotoFilter.values()) { filter ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onFilterChange(filter) }
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selectedFilter == filter) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                        .border(
                            width = if (selectedFilter == filter) 2.dp else 0.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = filter.icon,
                        fontSize = 24.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(filter.labelRes),
                    fontSize = 11.sp,
                    color = if (selectedFilter == filter) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}

/**
 * Adjustment Controls
 */
@Composable
private fun AdjustmentControls(
    brightness: Float,
    contrast: Float,
    saturation: Float,
    onBrightnessChange: (Float) -> Unit,
    onContrastChange: (Float) -> Unit,
    onSaturationChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Text("Налаштування зображення", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Яскравість:", fontSize = 14.sp, modifier = Modifier.width(100.dp))
            Slider(
                value = brightness,
                onValueChange = onBrightnessChange,
                valueRange = -1f..1f,
                modifier = Modifier.weight(1f)
            )
            Text("${(brightness * 100).toInt()}%", fontSize = 12.sp, modifier = Modifier.width(50.dp))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Контраст:", fontSize = 14.sp, modifier = Modifier.width(100.dp))
            Slider(
                value = contrast,
                onValueChange = onContrastChange,
                valueRange = 0f..2f,
                modifier = Modifier.weight(1f)
            )
            Text("${(contrast * 100).toInt()}%", fontSize = 12.sp, modifier = Modifier.width(50.dp))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Насиченість:", fontSize = 14.sp, modifier = Modifier.width(100.dp))
            Slider(
                value = saturation,
                onValueChange = onSaturationChange,
                valueRange = 0f..2f,
                modifier = Modifier.weight(1f)
            )
            Text("${(saturation * 100).toInt()}%", fontSize = 12.sp, modifier = Modifier.width(50.dp))
        }
    }
}

/**
 * Rotate Controls
 */
@Composable
private fun RotateControls(
    rotationAngle: Int,
    onRotate: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Button(onClick = { onRotate(-90) }) {
            Icon(Icons.Default.RotateLeft, "Вліво 90")
            Spacer(modifier = Modifier.width(4.dp))
            Text("90 вліво")
        }

        Button(onClick = { onRotate(180) }) {
            Text("180")
        }

        Button(onClick = { onRotate(90) }) {
            Icon(Icons.Default.RotateRight, "Вправо 90")
            Spacer(modifier = Modifier.width(4.dp))
            Text("90 вправо")
        }
    }
}

/**
 * Text Controls
 */
@Composable
private fun TextControls(
    onAddText: (String, Color, Float) -> Unit
) {
    var textInput by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(Color.White) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Text("Додати текст", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = textInput,
            onValueChange = { textInput = it },
            placeholder = { Text("Введіть текст...") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(textColors) { color ->
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (color == selectedColor) 3.dp else 1.dp,
                            color = if (color == selectedColor) Color.White else Color.Gray,
                            shape = CircleShape
                        )
                        .clickable { selectedColor = color }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                if (textInput.isNotBlank()) {
                    onAddText(textInput, selectedColor, 24f)
                    textInput = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Додати текст")
        }
    }
}

/**
 * Sticker Controls
 */
@Composable
private fun StickerControls(
    onAddSticker: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(availableStickers) { sticker ->
            Text(
                text = sticker,
                fontSize = 40.sp,
                modifier = Modifier
                    .size(60.dp)
                    .clickable { onAddSticker(sticker) }
                    .wrapContentSize(Alignment.Center)
            )
        }
    }
}

/**
 * Crop Controls
 */
@Composable
private fun CropControls(
    onCrop: () -> Unit,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        OutlinedButton(onClick = onReset) {
            Icon(Icons.Default.Refresh, stringResource(R.string.crop_reset_cd), modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.crop_reset))
        }

        Button(onClick = onCrop) {
            Icon(Icons.Default.Crop, stringResource(R.string.crop_apply_cd))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.crop_apply))
        }
    }
}

// Special sentinel color for the eraser tool (detected in canvas via identity check)
internal val eraserColor = Color(0x00000001)   // fully transparent, unique alpha

// Color palette — expanded for more creative options
private val drawingColors = listOf(
    Color.Black,
    Color.White,
    Color(0xFFE53935), // red
    Color(0xFFFF7043), // deep orange
    Color(0xFFFFA726), // orange
    Color(0xFFFFEE58), // yellow
    Color(0xFF66BB6A), // green
    Color(0xFF26C6DA), // cyan
    Color(0xFF1E88E5), // blue
    Color(0xFF5E35B1), // purple
    Color(0xFFEC407A), // pink
    Color(0xFF8D6E63), // brown
    Color(0xFF78909C), // blue-grey
    Color(0xFFBDBDBD), // light grey
)

private val textColors = listOf(
    Color.White, Color.Black, Color.Red, Color.Blue,
    Color.Green, Color.Yellow
)

// Available stickers
private val availableStickers = listOf(
    "😀", "😂", "😍", "😎", "🥳", "😢", "😡", "🤔",
    "👍", "👎", "❤\uFE0F", "💔", "🔥", "⭐", "🎉", "🎈"
)

/**
 * Editor Tools
 */
enum class EditorTool(
    @StringRes val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    DRAW(R.string.editor_tool_draw, Icons.Default.Brush),
    FILTER(R.string.editor_tool_filter, Icons.Default.FilterVintage),
    ADJUST(R.string.editor_tool_adjust, Icons.Default.Tune),
    ROTATE(R.string.editor_tool_rotate, Icons.Default.RotateRight),
    TEXT(R.string.editor_tool_text, Icons.Default.TextFields),
    STICKER(R.string.editor_tool_sticker, Icons.Default.EmojiEmotions),
    CROP(R.string.editor_tool_crop, Icons.Default.Crop)
}

/**
 * Photo Filters
 */
enum class PhotoFilter(
    @StringRes val labelRes: Int,
    val icon: String
) {
    NONE(R.string.photo_filter_none, "🖼\uFE0F"),
    GRAYSCALE(R.string.photo_filter_grayscale, "⚫"),
    SEPIA(R.string.photo_filter_sepia, "🟤"),
    INVERT(R.string.photo_filter_invert, "🔄"),
    BLUR(R.string.photo_filter_blur, "🌫\uFE0F"),
    SHARPEN(R.string.photo_filter_sharpen, "🔪")
}

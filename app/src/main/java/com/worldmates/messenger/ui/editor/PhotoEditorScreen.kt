package com.worldmates.messenger.ui.editor

import android.widget.Toast
import androidx.annotation.StringRes
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worldmates.messenger.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoEditorScreen(
    imageUrl: String,
    onDismiss: () -> Unit,
    onSave: (java.io.File) -> Unit,
    viewModel: PhotoEditorViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val currentTool    by viewModel.currentTool.collectAsState()
    val brightness     by viewModel.brightness.collectAsState()
    val contrast       by viewModel.contrast.collectAsState()
    val saturation     by viewModel.saturation.collectAsState()
    val warmth         by viewModel.warmth.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val rotationAngle  by viewModel.rotationAngle.collectAsState()
    val drawingPaths   by viewModel.drawingPaths.collectAsState()
    val textElements   by viewModel.textElements.collectAsState()
    val selectedColor  by viewModel.selectedColor.collectAsState()
    val brushSize      by viewModel.brushSize.collectAsState()
    val isProcessing   by viewModel.isProcessing.collectAsState()

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
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = viewModel.canUndo()
                    ) {
                        Icon(Icons.Default.Undo, stringResource(R.string.editor_undo))
                    }
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = viewModel.canRedo()
                    ) {
                        Icon(Icons.Default.Redo, stringResource(R.string.editor_redo))
                    }
                    IconButton(onClick = { showSaveDialog = true }) {
                        Icon(Icons.Default.Check, stringResource(R.string.save))
                    }
                }
            )
        },
        bottomBar = {
            Column {
                when (currentTool) {
                    EditorTool.DRAW   -> DrawControls(
                        selectedColor   = selectedColor,
                        brushSize       = brushSize,
                        isErasing       = selectedColor == eraserColor,
                        onColorChange   = { viewModel.setDrawColor(it) },
                        onBrushSizeChange = { viewModel.setBrushSize(it) },
                        onToggleEraser  = {
                            val newColor = if (selectedColor == eraserColor) Color.Red else eraserColor
                            viewModel.setDrawColor(newColor)
                        }
                    )
                    EditorTool.FILTER -> FilterControls(
                        selectedFilter  = selectedFilter,
                        onFilterChange  = { viewModel.applyFilter(it) }
                    )
                    EditorTool.ADJUST -> AdjustmentControls(
                        brightness      = brightness,
                        contrast        = contrast,
                        saturation      = saturation,
                        warmth          = warmth,
                        onBrightnessChange  = { viewModel.setBrightness(it) },
                        onContrastChange    = { viewModel.setContrast(it) },
                        onSaturationChange  = { viewModel.setSaturation(it) },
                        onWarmthChange      = { viewModel.setWarmth(it) }
                    )
                    EditorTool.ROTATE -> RotateControls(
                        rotationAngle  = rotationAngle,
                        onRotate       = { viewModel.rotate(it) },
                        onFlip         = { viewModel.flip(it) }
                    )
                    EditorTool.TEXT   -> TextControls(
                        onAddText = { text, color, size -> viewModel.addText(text, color, size) }
                    )
                    EditorTool.STICKER -> StickerControls(
                        onAddSticker = { viewModel.addSticker(it) }
                    )
                    EditorTool.CROP   -> CropControls(
                        onCrop  = { viewModel.applyCrop() },
                        onReset = { viewModel.resetCrop() }
                    )
                }

                HorizontalDivider()

                ToolSelector(
                    currentTool    = currentTool,
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
            PhotoEditorCanvas(
                viewModel     = viewModel,
                currentTool   = currentTool,
                drawingPaths  = drawingPaths,
                textElements  = textElements,
                selectedColor = selectedColor,
                brushSize     = brushSize
            )

            // Loading overlay
            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }
    }

    if (showSaveDialog) {
        SaveDialog(
            onSaveModified = {
                showSaveDialog = false
                coroutineScope.launch {
                    val savedFile = viewModel.applyAndSaveImage(context)
                    if (savedFile != null) {
                        onSave(savedFile)
                    } else {
                        Toast.makeText(context, context.getString(R.string.save_error), Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onSaveBoth = {
                showSaveDialog = false
                coroutineScope.launch {
                    viewModel.saveOriginalImage(context)
                    val modifiedFile = viewModel.applyAndSaveImage(context)
                    if (modifiedFile != null) {
                        Toast.makeText(context, context.getString(R.string.save_both_success), Toast.LENGTH_SHORT).show()
                        onSave(modifiedFile)
                    }
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

// ── Save dialog ───────────────────────────────────────────────────────────────

@Composable
private fun SaveDialog(
    onSaveModified: () -> Unit,
    onSaveBoth:     () -> Unit,
    onDontSave:     () -> Unit,
    onDismiss:      () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Default.Save, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.save_photo_title)) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.save_choose_option))

                Surface(
                    onClick = onSaveModified,
                    shape   = RoundedCornerShape(12.dp),
                    color   = MaterialTheme.colorScheme.primaryContainer,
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
                            Text(
                                stringResource(R.string.save_modified_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Surface(
                    onClick  = onSaveBoth,
                    shape    = RoundedCornerShape(12.dp),
                    color    = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                            Text(
                                stringResource(R.string.save_both_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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

// ── Crop drag handle ──────────────────────────────────────────────────────────

private enum class CropDragHandle {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
    LEFT_EDGE, RIGHT_EDGE, TOP_EDGE, BOTTOM_EDGE,
    MOVE
}

// ── Canvas ────────────────────────────────────────────────────────────────────

@Composable
private fun PhotoEditorCanvas(
    viewModel:     PhotoEditorViewModel,
    currentTool:   EditorTool,
    drawingPaths:  List<DrawPath>,
    textElements:  List<TextElement>,
    selectedColor: Color,
    brushSize:     Float
) {
    val editedBitmap by viewModel.editedBitmap.collectAsState()
    val cropRect     by viewModel.cropRect.collectAsState()

    var currentPoints    by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize       by remember { mutableStateOf(IntSize.Zero) }
    var cropDragHandle   by remember { mutableStateOf<CropDragHandle?>(null) }
    var draggingTextIdx  by remember { mutableStateOf<Int?>(null) }

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
                Image(
                    bitmap       = bitmap.asImageBitmap(),
                    contentDescription = "Edited photo",
                    modifier     = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(currentTool, selectedColor, brushSize) {
                            when (currentTool) {

                                EditorTool.DRAW -> detectDragGestures(
                                    onDragStart = { offset -> currentPoints = listOf(offset) },
                                    onDrag      = { change, _ ->
                                        change.consume()
                                        currentPoints = currentPoints + change.position
                                    },
                                    onDragEnd   = {
                                        if (currentPoints.size >= 2) {
                                            viewModel.addDrawPathFromPoints(
                                                currentPoints, canvasSize, selectedColor, brushSize
                                            )
                                        }
                                        currentPoints = emptyList()
                                    },
                                    onDragCancel = { currentPoints = emptyList() }
                                )

                                EditorTool.TEXT, EditorTool.STICKER -> detectDragGestures(
                                    onDragStart = { offset ->
                                        if (canvasSize.width == 0 || canvasSize.height == 0) return@detectDragGestures
                                        val nx = offset.x / canvasSize.width
                                        val ny = offset.y / canvasSize.height
                                        val tolerance = 0.12f
                                        val idx = textElements.indexOfFirst { el ->
                                            kotlin.math.abs(el.x - nx) < tolerance &&
                                            kotlin.math.abs(el.y - ny) < tolerance
                                        }
                                        if (idx >= 0) {
                                            draggingTextIdx = idx
                                            viewModel.beginMoveTextElement()
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val idx = draggingTextIdx ?: return@detectDragGestures
                                        if (canvasSize.width == 0 || canvasSize.height == 0) return@detectDragGestures
                                        val nx = change.position.x / canvasSize.width
                                        val ny = change.position.y / canvasSize.height
                                        viewModel.moveTextElement(idx, nx, ny)
                                    },
                                    onDragEnd    = { draggingTextIdx = null },
                                    onDragCancel = { draggingTextIdx = null }
                                )

                                EditorTool.CROP -> detectDragGestures(
                                    onDragStart = { offset ->
                                        val w = canvasSize.width.toFloat()
                                        val h = canvasSize.height.toFloat()
                                        if (w == 0f || h == 0f) return@detectDragGestures
                                        val r = viewModel.cropRect.value
                                        val l  = r.left   * w;  val t  = r.top    * h
                                        val ri = r.right  * w;  val b  = r.bottom * h
                                        val tol = 48f
                                        fun near(a: Float, v: Float) = kotlin.math.abs(a - v) < tol
                                        cropDragHandle = when {
                                            near(offset.x, l)  && near(offset.y, t) -> CropDragHandle.TOP_LEFT
                                            near(offset.x, ri) && near(offset.y, t) -> CropDragHandle.TOP_RIGHT
                                            near(offset.x, l)  && near(offset.y, b) -> CropDragHandle.BOTTOM_LEFT
                                            near(offset.x, ri) && near(offset.y, b) -> CropDragHandle.BOTTOM_RIGHT
                                            near(offset.x, l)                       -> CropDragHandle.LEFT_EDGE
                                            near(offset.x, ri)                      -> CropDragHandle.RIGHT_EDGE
                                            near(offset.y, t)                       -> CropDragHandle.TOP_EDGE
                                            near(offset.y, b)                       -> CropDragHandle.BOTTOM_EDGE
                                            else                                    -> CropDragHandle.MOVE
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
                                            null -> {}
                                        }
                                    },
                                    onDragEnd    = { cropDragHandle = null },
                                    onDragCancel = { cropDragHandle = null }
                                )

                                else -> {}
                            }
                        }
                ) {
                    // Saved drawing paths
                    drawingPaths.forEach { drawPath ->
                        val path = Path()
                        val pts  = drawPath.points
                        if (pts.size >= 2) {
                            path.moveTo(pts[0].x * size.width, pts[0].y * size.height)
                            for (i in 1 until pts.size) {
                                path.lineTo(pts[i].x * size.width, pts[i].y * size.height)
                            }
                            val isEraser = drawPath.color == eraserColor
                            drawPath(
                                path      = path,
                                color     = if (isEraser) Color.Transparent else drawPath.color,
                                style     = Stroke(width = drawPath.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
                                blendMode = if (isEraser) BlendMode.Clear else BlendMode.SrcOver
                            )
                        }
                    }

                    // Active stroke being drawn
                    if (currentPoints.size >= 2) {
                        val activePath = Path()
                        activePath.moveTo(currentPoints[0].x, currentPoints[0].y)
                        for (i in 1 until currentPoints.size) {
                            activePath.lineTo(currentPoints[i].x, currentPoints[i].y)
                        }
                        drawPath(
                            path  = activePath,
                            color = selectedColor,
                            style = Stroke(width = brushSize, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }

                    // Text / sticker elements
                    textElements.forEachIndexed { idx, element ->
                        drawContext.canvas.nativeCanvas.apply {
                            val paint = android.graphics.Paint().apply {
                                color     = element.color.toArgb()
                                textSize  = element.size * 3f
                                isAntiAlias = true
                                typeface  = android.graphics.Typeface.DEFAULT_BOLD
                                setShadowLayer(3f, 1f, 1f, android.graphics.Color.argb(100, 0, 0, 0))
                            }
                            // Drag indicator: subtle ring around active element
                            if (idx == draggingTextIdx) {
                                val cx = element.x * size.width
                                val cy = element.y * size.height
                                val ringPaint = android.graphics.Paint().apply {
                                    color   = android.graphics.Color.WHITE
                                    style   = android.graphics.Paint.Style.STROKE
                                    strokeWidth = 3f
                                    isAntiAlias = true
                                }
                                drawCircle(cx, cy - paint.textSize * 0.3f, paint.textSize * 0.9f, ringPaint)
                            }
                            drawText(
                                element.text,
                                element.x * size.width,
                                element.y * size.height,
                                paint
                            )
                        }
                    }

                    // Crop overlay
                    if (currentTool == EditorTool.CROP) {
                        val cr      = cropRect
                        val cLeft   = cr.left   * size.width
                        val cTop    = cr.top    * size.height
                        val cRight  = cr.right  * size.width
                        val cBottom = cr.bottom * size.height
                        val cW = cRight - cLeft
                        val cH = cBottom - cTop
                        val dim = Color.Black.copy(alpha = 0.55f)

                        drawRect(dim, topLeft = Offset(0f, 0f),     size = androidx.compose.ui.geometry.Size(size.width, cTop))
                        drawRect(dim, topLeft = Offset(0f, cBottom), size = androidx.compose.ui.geometry.Size(size.width, size.height - cBottom))
                        drawRect(dim, topLeft = Offset(0f, cTop),    size = androidx.compose.ui.geometry.Size(cLeft, cH))
                        drawRect(dim, topLeft = Offset(cRight, cTop), size = androidx.compose.ui.geometry.Size(size.width - cRight, cH))

                        drawRect(
                            color    = Color.White,
                            topLeft  = Offset(cLeft, cTop),
                            size     = androidx.compose.ui.geometry.Size(cW, cH),
                            style    = Stroke(width = 2f)
                        )

                        val grid = Color.White.copy(alpha = 0.4f)
                        drawLine(grid, Offset(cLeft + cW / 3, cTop),     Offset(cLeft + cW / 3, cBottom), 1f)
                        drawLine(grid, Offset(cLeft + 2 * cW / 3, cTop), Offset(cLeft + 2 * cW / 3, cBottom), 1f)
                        drawLine(grid, Offset(cLeft, cTop + cH / 3),     Offset(cRight, cTop + cH / 3), 1f)
                        drawLine(grid, Offset(cLeft, cTop + 2 * cH / 3), Offset(cRight, cTop + 2 * cH / 3), 1f)

                        val hs = 28f; val hw = 4f; val wh = Color.White
                        drawLine(wh, Offset(cLeft, cTop),       Offset(cLeft + hs, cTop), hw)
                        drawLine(wh, Offset(cLeft, cTop),       Offset(cLeft, cTop + hs), hw)
                        drawLine(wh, Offset(cRight, cTop),      Offset(cRight - hs, cTop), hw)
                        drawLine(wh, Offset(cRight, cTop),      Offset(cRight, cTop + hs), hw)
                        drawLine(wh, Offset(cLeft, cBottom),    Offset(cLeft + hs, cBottom), hw)
                        drawLine(wh, Offset(cLeft, cBottom),    Offset(cLeft, cBottom - hs), hw)
                        drawLine(wh, Offset(cRight, cBottom),   Offset(cRight - hs, cBottom), hw)
                        drawLine(wh, Offset(cRight, cBottom),   Offset(cRight, cBottom - hs), hw)
                    }
                }
            }
        } ?: CircularProgressIndicator(color = Color.White)
    }
}

// ── Tool Selector ─────────────────────────────────────────────────────────────

@Composable
private fun ToolSelector(
    currentTool:    EditorTool,
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
                    shape    = CircleShape,
                    color    = if (currentTool == tool) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector      = tool.icon,
                            contentDescription = stringResource(tool.labelRes),
                            tint = if (currentTool == tool) MaterialTheme.colorScheme.onPrimary
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text     = stringResource(tool.labelRes),
                    fontSize = 11.sp,
                    color    = if (currentTool == tool) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// ── Draw Controls ─────────────────────────────────────────────────────────────

@Composable
private fun DrawControls(
    selectedColor:    Color,
    brushSize:        Float,
    isErasing:        Boolean,
    onColorChange:    (Color) -> Unit,
    onBrushSizeChange:(Float) -> Unit,
    onToggleEraser:   () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment   = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Кисть:", fontWeight = FontWeight.Bold, modifier = Modifier.width(52.dp))
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
                                width  = if (!isErasing && color == selectedColor) 3.dp else 1.dp,
                                color  = if (!isErasing && color == selectedColor) Color.White else Color.Gray,
                                shape  = CircleShape
                            )
                            .clickable { onColorChange(color) }
                    )
                }
            }
            IconButton(
                onClick  = onToggleEraser,
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
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment   = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(if (isErasing) "Ластик:" else "Розмір:", fontSize = 13.sp, modifier = Modifier.width(52.dp))
            Slider(
                value        = brushSize,
                onValueChange = onBrushSizeChange,
                valueRange   = 2f..60f,
                modifier     = Modifier.weight(1f)
            )
            Text("${brushSize.toInt()}px", fontSize = 13.sp, modifier = Modifier.width(42.dp))
        }
    }
}

// ── Filter Controls ───────────────────────────────────────────────────────────

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
                            if (selectedFilter == filter) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .border(
                            width = if (selectedFilter == filter) 2.dp else 0.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = filter.icon, fontSize = 24.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text     = stringResource(filter.labelRes),
                    fontSize = 11.sp,
                    color    = if (selectedFilter == filter) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// ── Adjustment Controls ───────────────────────────────────────────────────────

@Composable
private fun AdjustmentControls(
    brightness:         Float,
    contrast:           Float,
    saturation:         Float,
    warmth:             Float,
    onBrightnessChange: (Float) -> Unit,
    onContrastChange:   (Float) -> Unit,
    onSaturationChange: (Float) -> Unit,
    onWarmthChange:     (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text("Налаштування зображення", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))

        AdjustRow("Яскравість:", "${(brightness * 100).toInt()}%", brightness, -1f..1f, onBrightnessChange)
        AdjustRow("Контраст:",   "${(contrast   * 100).toInt()}%", contrast,   0f..2f,  onContrastChange)
        AdjustRow("Насиченість:","${(saturation * 100).toInt()}%", saturation, 0f..2f,  onSaturationChange)
        AdjustRow(
            label    = "Теплота:",
            display  = if (warmth >= 0f) "+${(warmth * 100).toInt()}%" else "${(warmth * 100).toInt()}%",
            value    = warmth,
            range    = -1f..1f,
            onChange = onWarmthChange
        )
    }
}

@Composable
private fun AdjustRow(
    label:    String,
    display:  String,
    value:    Float,
    range:    ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Row(
        verticalAlignment   = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Text(label, fontSize = 13.sp, modifier = Modifier.width(96.dp))
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f))
        Text(display, fontSize = 12.sp, modifier = Modifier.width(50.dp))
    }
}

// ── Rotate Controls ───────────────────────────────────────────────────────────

@Composable
private fun RotateControls(
    rotationAngle: Int,
    onRotate:      (Int) -> Unit,
    onFlip:        (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Rotation row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = { onRotate(-90) }) {
                Icon(Icons.Default.RotateLeft, null)
                Spacer(Modifier.width(4.dp))
                Text("90°")
            }
            Button(onClick = { onRotate(180) }) {
                Text("180°")
            }
            Button(onClick = { onRotate(90) }) {
                Icon(Icons.Default.RotateRight, null)
                Spacer(Modifier.width(4.dp))
                Text("90°")
            }
        }

        Spacer(Modifier.height(8.dp))

        // Flip row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(onClick = { onFlip(true) }) {
                Icon(Icons.Default.Flip, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.flip_horizontal))
            }
            OutlinedButton(onClick = { onFlip(false) }) {
                Icon(
                    Icons.Default.Flip,
                    null,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = 90f }
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.flip_vertical))
            }
        }
    }
}

// ── Text Controls ─────────────────────────────────────────────────────────────

@Composable
private fun TextControls(
    onAddText: (String, Color, Float) -> Unit
) {
    var textInput     by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(Color.White) }
    var fontSize      by remember { mutableStateOf(24f) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Text("Додати текст", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value         = textInput,
            onValueChange = { textInput = it },
            placeholder   = { Text("Введіть текст...") },
            modifier      = Modifier.fillMaxWidth(),
            singleLine    = true
        )

        Spacer(Modifier.height(8.dp))

        // Color row + font size slider side by side
        Row(
            verticalAlignment   = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(textColors) { color ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (color == selectedColor) 3.dp else 1.dp,
                                color = if (color == selectedColor) MaterialTheme.colorScheme.primary else Color.Gray,
                                shape = CircleShape
                            )
                            .clickable { selectedColor = color }
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // Font size slider
        Row(
            verticalAlignment   = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Розмір:", fontSize = 13.sp, modifier = Modifier.width(56.dp))
            Slider(
                value        = fontSize,
                onValueChange = { fontSize = it },
                valueRange   = 12f..72f,
                modifier     = Modifier.weight(1f)
            )
            Text("${fontSize.toInt()}sp", fontSize = 12.sp, modifier = Modifier.width(42.dp))
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick  = {
                if (textInput.isNotBlank()) {
                    onAddText(textInput, selectedColor, fontSize)
                    textInput = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Додати")
        }
    }
}

// ── Sticker Controls ──────────────────────────────────────────────────────────

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
                text     = sticker,
                fontSize = 40.sp,
                modifier = Modifier
                    .size(56.dp)
                    .clickable { onAddSticker(sticker) }
                    .wrapContentSize(Alignment.Center)
            )
        }
    }
}

// ── Crop Controls ─────────────────────────────────────────────────────────────

@Composable
private fun CropControls(
    onCrop:  () -> Unit,
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
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.crop_reset))
        }
        Button(onClick = onCrop) {
            Icon(Icons.Default.Crop, null)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.crop_apply))
        }
    }
}

// ── Constants ─────────────────────────────────────────────────────────────────

internal val eraserColor = Color(0x00000001)

private val drawingColors = listOf(
    Color.Black, Color.White,
    Color(0xFFE53935), Color(0xFFFF7043), Color(0xFFFFA726), Color(0xFFFFEE58),
    Color(0xFF66BB6A), Color(0xFF26C6DA), Color(0xFF1E88E5), Color(0xFF5E35B1),
    Color(0xFFEC407A), Color(0xFF8D6E63), Color(0xFF78909C), Color(0xFFBDBDBD)
)

private val textColors = listOf(
    Color.White, Color.Black,
    Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF66BB6A),
    Color(0xFFFFEE58), Color(0xFFEC407A), Color(0xFFFF7043)
)

private val availableStickers = listOf(
    "😀", "😂", "😍", "😎", "🥳", "😢", "😡", "🤔",
    "😇", "🤩", "😏", "🥺", "😤", "🤯", "🥴", "😴",
    "👍", "👎", "❤️", "💔", "🔥", "⭐", "🎉", "🎈",
    "💯", "✨", "💥", "🌈", "🎶", "🏆", "👑", "💎",
    "🐱", "🐶", "🦊", "🐸", "🦋", "🌸", "🍕", "☕"
)

// ── Enums ─────────────────────────────────────────────────────────────────────

enum class EditorTool(
    @StringRes val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    DRAW   (R.string.editor_tool_draw,    Icons.Default.Brush),
    FILTER (R.string.editor_tool_filter,  Icons.Default.FilterVintage),
    ADJUST (R.string.editor_tool_adjust,  Icons.Default.Tune),
    ROTATE (R.string.editor_tool_rotate,  Icons.Default.RotateRight),
    TEXT   (R.string.editor_tool_text,    Icons.Default.TextFields),
    STICKER(R.string.editor_tool_sticker, Icons.Default.EmojiEmotions),
    CROP   (R.string.editor_tool_crop,    Icons.Default.Crop)
}

enum class PhotoFilter(
    @StringRes val labelRes: Int,
    val icon: String
) {
    NONE     (R.string.photo_filter_none,      "🖼️"),
    GRAYSCALE(R.string.photo_filter_grayscale, "⚫"),
    SEPIA    (R.string.photo_filter_sepia,     "🟤"),
    WARM     (R.string.photo_filter_warm,      "🌅"),
    COOL     (R.string.photo_filter_cool,      "❄️"),
    VIVID    (R.string.photo_filter_vivid,     "🌈"),
    INVERT   (R.string.photo_filter_invert,    "🔄"),
    BLUR     (R.string.photo_filter_blur,      "🌫️"),
    SHARPEN  (R.string.photo_filter_sharpen,   "🔪")
}

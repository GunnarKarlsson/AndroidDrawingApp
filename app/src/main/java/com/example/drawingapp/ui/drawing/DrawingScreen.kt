package com.example.drawingapp.ui.drawing

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.LifecycleOwner
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.drawingapp.R
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.offset
import com.skydoves.colorpickerview.ActionMode
import com.skydoves.colorpickerview.ColorPickerView
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import com.skydoves.colorpickerview.sliders.BrightnessSlideBar
import com.example.drawingapp.data.DrawTool
import com.example.drawingapp.data.LayerMeta
import com.example.drawingapp.data.Stroke
import com.example.drawingapp.data.StrokeCapStyle
import com.example.drawingapp.data.StrokeData
import com.example.drawingapp.tools.DrawingContext
import com.example.drawingapp.tools.DrawingTool
import com.example.drawingapp.tools.StrokeIntent
import com.example.drawingapp.tools.TapIntent
import com.example.drawingapp.tools.ToolRegistry
import com.example.drawingapp.tools.ToolSettings
import com.example.drawingapp.tools.createDefaultToolRegistry
import com.example.drawingapp.util.ColorUtil
import com.example.drawingapp.util.getExportDirectoryPath
import java.io.ByteArrayOutputStream


private const val MIN_STROKE_SEGMENT_PX = 0.5f
private val STROKE_SIZE_RANGE = 1f..64f
private const val DEFAULT_STROKE_SIZE_PX = 20f // ~30% into 1..64
private val DEFAULT_COLOR = Color.Black

private val HEADER_BACKGROUND = Color(0xFF565563)
private val HEADER_ICON_COLOR = Color(0xFFE1D8D5)
private val HEADER_ICON_SIZE = 29.dp // 20% larger than default 24.dp

/** Represents one undoable action for the unified undo stack. */
private sealed class UndoEntry {
    data class Stroke(val layerIndex: Int, val stroke: com.example.drawingapp.data.Stroke? = null) : UndoEntry()
    data class Fill(val layerIndex: Int, val bitmapBeforeFill: Bitmap) : UndoEntry()
}

private val DESATURATED_PRESETS = listOf(0f, 30f, 60f, 90f, 120f, 150f, 180f, 210f, 240f, 270f, 300f, 330f).map { hue ->
    ColorUtil.colorFromHsv(hue, 0.55f, 0.9f)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun DrawingScreen(
    pageId: String,
    onLoadLayers: (String) -> List<Bitmap?>,
    onLoadLayerMetas: (String) -> List<LayerMeta>?,
    onSaveLayers: (String, List<Bitmap>, Int, List<LayerMeta>?, Int) -> Unit,
    onLoadBackgroundColor: (String) -> Int,
    onSaveBackgroundColor: (String, Int) -> Unit,
    onExport: (Bitmap) -> Unit,
    initialStrokeSizePx: Float = DEFAULT_STROKE_SIZE_PX,
    onSaveStrokeSizePx: (Float) -> Unit = {},
    initialStrokeColor: Color = DEFAULT_COLOR,
    onConfirmStrokeColor: (Color) -> Unit = {},
    initialStrokeCap: StrokeCapStyle = StrokeCapStyle.ROUND,
    onSaveStrokeCap: (StrokeCapStyle) -> Unit = {},
    initialCurveSmoothingEnabled: Boolean = false,
    onSaveCurveSmoothing: (Boolean) -> Unit = {},
    initialCurveClosingEnabled: Boolean = false,
    onSaveCurveClosing: (Boolean) -> Unit = {},
    loadFavoriteColors: () -> List<Int> = { listOf(Color.Black.toArgb(), Color.White.toArgb()) },
    saveFavoriteColors: (List<Int>) -> Unit = {},
    onHomeClick: () -> Unit = {},
    onExitPage: (String, Int) -> Unit = { _, _ -> },
    initialCurrentLayerIndex: Int = 0,
    modifier: Modifier = Modifier
) {
    val layerStates = remember(pageId) { mutableStateListOf<LayerState>() }
    var currentLayerIndex by remember(pageId) { mutableStateOf(initialCurrentLayerIndex) }
    DisposableEffect(pageId) {
        onDispose { onExitPage(pageId, currentLayerIndex) }
    }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var selectedTool by remember { mutableStateOf(DrawTool.Pen) }
    val toolRegistry = remember { createDefaultToolRegistry() }
    val currentTool: DrawingTool = remember(selectedTool, toolRegistry) {
        toolRegistry.getToolByEnum(selectedTool) ?: toolRegistry.getToolByEnum(DrawTool.Pen)!!
    }
    var selectedColor by remember(initialStrokeColor) { mutableStateOf(initialStrokeColor) }
    var showColorPickerModal by remember { mutableStateOf(false) }
    var pendingColor by remember { mutableStateOf(selectedColor) }
    val favoriteColorsArgb = remember { mutableStateListOf<Int>() }
    var favoriteToDeleteArgb by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(showColorPickerModal) {
        if (showColorPickerModal) {
            favoriteColorsArgb.clear()
            favoriteColorsArgb.addAll(loadFavoriteColors())
        }
    }
    var strokeSizePx by remember(initialStrokeSizePx) {
        mutableStateOf(initialStrokeSizePx.coerceIn(STROKE_SIZE_RANGE))
    }
    var strokeCapStyle by remember(initialStrokeCap) { mutableStateOf(initialStrokeCap) }
    var curveSmoothingEnabled by remember(initialCurveSmoothingEnabled) { mutableStateOf(initialCurveSmoothingEnabled) }
    var curveClosingEnabled by remember(initialCurveClosingEnabled) { mutableStateOf(initialCurveClosingEnabled) }
    var backgroundColor by remember(pageId) { mutableStateOf(0xFFFFFFFF.toInt()) }
    var initialized by remember(pageId) { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showLayerManagerDialog by remember { mutableStateOf(false) }
    var showToolSelectionModal by remember { mutableStateOf(false) }
    var drawingViewRef by remember { mutableStateOf<DrawingView?>(null) }
    var canvasRefreshTrigger by remember { mutableStateOf(0) }
    val undoStack = remember(pageId) { mutableStateListOf<UndoEntry>() }
    val redoStack = remember(pageId) { mutableStateListOf<UndoEntry>() }
    val strokeWidth = strokeSizePx
    val toolSettings = ToolSettings(
        color = selectedColor,
        strokeWidth = strokeSizePx,
        strokeCapStyle = strokeCapStyle,
        smoothingEnabled = curveSmoothingEnabled,
        closingEnabled = curveClosingEnabled
    )
    val strokeColor = currentTool.transformColor(selectedColor, toolSettings)

    fun saveAllLayers() {
        if (layerStates.isNotEmpty()) {
            val layerMetas = layerStates.map { layer ->
                LayerMeta(
                    hasFill = layer.hasFill,
                    strokes = layer.strokes.map { StrokeData.fromStroke(it) },
                    isHidden = layer.isHidden
                )
            }
            onSaveLayers(pageId, layerStates.map { it.bitmap }, backgroundColor, layerMetas, currentLayerIndex)
        }
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val entry = undoStack.removeAt(undoStack.lastIndex)
        when (entry) {
            is UndoEntry.Stroke -> {
                if (entry.layerIndex !in layerStates.indices) return
                val layer = layerStates[entry.layerIndex]
                if (layer.strokes.isEmpty()) return
                val removedStroke = layer.strokes.removeAt(layer.strokes.lastIndex)
                layer.bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
                layer.strokes.forEach { stroke -> drawStrokeOnBitmap(layer.bitmap, stroke) }
                // Add to redo stack with the removed stroke
                redoStack.add(UndoEntry.Stroke(entry.layerIndex, removedStroke))
            }
            is UndoEntry.Fill -> {
                if (entry.layerIndex in layerStates.indices) {
                    val layer = layerStates[entry.layerIndex]
                    val currentBitmap = Bitmap.createBitmap(layer.bitmap.width, layer.bitmap.height, Bitmap.Config.ARGB_8888)
                    android.graphics.Canvas(currentBitmap).drawBitmap(layer.bitmap, 0f, 0f, null)
                    layer.bitmap = entry.bitmapBeforeFill
                    // Add to redo stack with the current state before fill
                    redoStack.add(UndoEntry.Fill(entry.layerIndex, currentBitmap))
                }
            }
        }
        saveAllLayers()
        canvasRefreshTrigger++
    }
    
    fun redo() {
        if (redoStack.isEmpty()) return
        val entry = redoStack.removeAt(redoStack.lastIndex)
        when (entry) {
            is UndoEntry.Stroke -> {
                if (entry.layerIndex !in layerStates.indices) return
                val layer = layerStates[entry.layerIndex]
                val strokeToRestore = entry.stroke
                if (strokeToRestore != null) {
                    layer.strokes.add(strokeToRestore)
                    drawStrokeOnBitmap(layer.bitmap, strokeToRestore)
                    // Add back to undo stack (without stroke data, since undo will remove it)
                    undoStack.add(UndoEntry.Stroke(entry.layerIndex))
                }
            }
            is UndoEntry.Fill -> {
                if (entry.layerIndex in layerStates.indices) {
                    val layer = layerStates[entry.layerIndex]
                    val currentBitmap = Bitmap.createBitmap(layer.bitmap.width, layer.bitmap.height, Bitmap.Config.ARGB_8888)
                    android.graphics.Canvas(currentBitmap).drawBitmap(layer.bitmap, 0f, 0f, null)
                    layer.bitmap = entry.bitmapBeforeFill
                    // Add back to undo stack
                    undoStack.add(UndoEntry.Fill(entry.layerIndex, currentBitmap))
                }
            }
        }
        saveAllLayers()
        canvasRefreshTrigger++
    }

    fun addLayer() {
        if (canvasSize.width <= 0 || canvasSize.height <= 0) return
        val newBitmap = Bitmap.createBitmap(canvasSize.width, canvasSize.height, Bitmap.Config.ARGB_8888)
        newBitmap.eraseColor(android.graphics.Color.TRANSPARENT)
        layerStates.add(LayerState(bitmap = newBitmap))
        currentLayerIndex = layerStates.lastIndex
        saveAllLayers()
    }

    fun deleteLayer(index: Int) {
        if (layerStates.size <= 1) return
        layerStates.removeAt(index)
        if (currentLayerIndex >= layerStates.size) currentLayerIndex = layerStates.lastIndex
        else if (currentLayerIndex > index) currentLayerIndex--
        saveAllLayers()
    }

    fun compositeLayers(): Bitmap? {
        if (layerStates.isEmpty() || canvasSize.width <= 0 || canvasSize.height <= 0) return null
        val out = Bitmap.createBitmap(canvasSize.width, canvasSize.height, Bitmap.Config.ARGB_8888)
        out.eraseColor(backgroundColor)
        val canvas = android.graphics.Canvas(out)
        layerStates.forEach { layer ->
            if (!layer.isTransparent() && !layer.isHidden) {
                canvas.drawBitmap(layer.bitmap, 0f, 0f, null)
            }
        }
        return out
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = HEADER_BACKGROUND,
                    titleContentColor = HEADER_ICON_COLOR,
                    actionIconContentColor = HEADER_ICON_COLOR
                ),
                title = { },
                actions = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onHomeClick) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_home),
                                contentDescription = "Home",
                                tint = HEADER_ICON_COLOR,
                                modifier = Modifier.size(HEADER_ICON_SIZE)
                            )
                        }
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(
                                    onClick = { showToolSelectionModal = true }
                                ) {
                                    Icon(
                                        painter = painterResource(id = currentTool.iconRes),
                                        contentDescription = currentTool.displayName,
                                        tint = HEADER_ICON_COLOR,
                                        modifier = Modifier.size(HEADER_ICON_SIZE)
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(
                                    onClick = {
                                        strokeCapStyle = if (strokeCapStyle == StrokeCapStyle.ROUND) StrokeCapStyle.BUTT else StrokeCapStyle.ROUND
                                        onSaveStrokeCap(strokeCapStyle)
                                    }
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            id = if (strokeCapStyle == StrokeCapStyle.ROUND) R.drawable.ic_tip_round else R.drawable.ic_tip_square
                                        ),
                                        contentDescription = if (strokeCapStyle == StrokeCapStyle.ROUND) "Round tip" else "Square tip",
                                        tint = HEADER_ICON_COLOR,
                                        modifier = Modifier.size(HEADER_ICON_SIZE)
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(HEADER_ICON_SIZE)
                                        .clickable {
                                            pendingColor = selectedColor
                                            showColorPickerModal = true
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(selectedColor, CircleShape)
                                    )
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_color_circle),
                                        contentDescription = "Stroke color",
                                        tint = HEADER_ICON_COLOR,
                                        modifier = Modifier.size(HEADER_ICON_SIZE)
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(onClick = { undo() }) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_undo),
                                        contentDescription = "Undo",
                                        tint = HEADER_ICON_COLOR,
                                        modifier = Modifier.size(HEADER_ICON_SIZE)
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(onClick = { redo() }) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_redo),
                                        contentDescription = "Redo",
                                        tint = HEADER_ICON_COLOR,
                                        modifier = Modifier.size(HEADER_ICON_SIZE)
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(onClick = { showLayerManagerDialog = true }) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_layers),
                                        contentDescription = "Layers",
                                        tint = HEADER_ICON_COLOR,
                                        modifier = Modifier.size(HEADER_ICON_SIZE)
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(onClick = {
                                    compositeLayers()?.let { bmp ->
                                        exportBitmap = bmp
                                        showExportDialog = true
                                    }
                                }) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_export),
                                        contentDescription = "Export",
                                        tint = HEADER_ICON_COLOR,
                                        modifier = Modifier.size(HEADER_ICON_SIZE)
                                    )
                                }
                            }
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HEADER_BACKGROUND)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PreviewDot(
                    strokeWidth = strokeWidth,
                    strokeColor = strokeColor,
                    tool = selectedTool,
                    containerBackground = HEADER_BACKGROUND,
                    borderColor = HEADER_ICON_COLOR
                )
                Slider(
                    value = strokeSizePx,
                    onValueChange = {
                        strokeSizePx = it
                        onSaveStrokeSizePx(it)
                    },
                    valueRange = STROKE_SIZE_RANGE,
                    steps = 62,
                    enabled = true,
                    colors = SliderDefaults.colors(
                        thumbColor = HEADER_ICON_COLOR,
                        activeTrackColor = HEADER_ICON_COLOR,
                        inactiveTrackColor = HEADER_ICON_COLOR.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        curveClosingEnabled = !curveClosingEnabled
                        onSaveCurveClosing(curveClosingEnabled)
                    }
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (curveClosingEnabled) R.drawable.ic_close_on else R.drawable.ic_close_off
                        ),
                        contentDescription = if (curveClosingEnabled) "Curve closing on" else "Curve closing off",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(HEADER_ICON_SIZE)
                    )
                }
                IconButton(
                    onClick = {
                        curveSmoothingEnabled = !curveSmoothingEnabled
                        onSaveCurveSmoothing(curveSmoothingEnabled)
                    }
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (curveSmoothingEnabled) R.drawable.ic_smooth_on else R.drawable.ic_smooth_off
                        ),
                        contentDescription = if (curveSmoothingEnabled) "Curve smoothing on" else "Curve smoothing off",
                        tint = HEADER_ICON_COLOR,
                        modifier = Modifier.size(HEADER_ICON_SIZE)
                    )
                }
            }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AndroidView(
                    factory = { DrawingView(it) },
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { size ->
                            if (size.width > 0 && size.height > 0 && (canvasSize != size)) {
                                canvasSize = size
                                if (!initialized) {
                                    initialized = true
                                    val loaded = onLoadLayers(pageId)
                                    val layerMetas = onLoadLayerMetas(pageId)
                                    backgroundColor = onLoadBackgroundColor(pageId)
                                    if (loaded.isEmpty()) {
                                        val bmp = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
                                        bmp.eraseColor(android.graphics.Color.TRANSPARENT)
                                        layerStates.add(LayerState(bitmap = bmp))
                                    } else {
                                        loaded.forEachIndexed { index, lb ->
                                            val bmp = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
                                            bmp.eraseColor(android.graphics.Color.TRANSPARENT)
                                            lb?.let {
                                                android.graphics.Canvas(bmp).drawBitmap(
                                                    it,
                                                    Rect(0, 0, it.width, it.height),
                                                    Rect(0, 0, size.width, size.height),
                                                    null
                                                )
                                            } ?: run { bmp.eraseColor(android.graphics.Color.TRANSPARENT) }
                                            val meta = layerMetas?.getOrNull(index)
                                            val strokes = meta?.strokes?.map { it.toStroke() }?.toMutableList() ?: mutableListOf()
                                            val hasFill = meta?.hasFill ?: true
                                            val isHidden = meta?.isHidden ?: false
                                            layerStates.add(
                                                LayerState(
                                                    bitmap = bmp,
                                                    strokes = strokes,
                                                    hasFill = hasFill,
                                                    isHidden = isHidden
                                                )
                                            )
                                        }
                                    }
                                    currentLayerIndex = currentLayerIndex.coerceIn(0, (layerStates.size - 1).coerceAtLeast(0))
                                }
                            }
                        },
                    update = { view ->
                        drawingViewRef = view
                        view.layers = layerStates.mapIndexed { index, layerState ->
                            RenderLayer(
                                bitmap = layerState.bitmap,
                                isHidden = layerState.isHidden,
                                isTransparent = layerState.isTransparent(),
                                index = index
                            )
                        }
                        view.currentLayerIndex = currentLayerIndex
                        view.canvasBackgroundColor = backgroundColor
                        view.isPanning = currentTool.affectsViewInteraction()
                        view.strokePreviewColor = strokeColor.toArgb()
                        view.strokePreviewWidth = strokeWidth
                        view.strokePreviewCapRound = (strokeCapStyle == StrokeCapStyle.ROUND)
                        view.strokePreviewIsEraser = (currentTool.drawTool == DrawTool.Eraser)
                        view.enableDotPreview = currentTool.supportsContinuousDrawing()
                        view.onStrokeDrawn = { points, _ ->
                            if (currentLayerIndex in layerStates.indices && points.size > 1) {
                                val context = DrawingContext(currentLayerIndex, canvasSize, view.scale)
                                val intent = currentTool.createAction(points, toolSettings, context)
                                if (intent != null) {
                                    val stroke = Stroke(
                                        points = intent.points,
                                        color = intent.color,
                                        strokeWidth = intent.strokeWidth,
                                        tool = intent.drawTool,
                                        strokeCapStyle = intent.strokeCapStyle,
                                        closed = intent.closed
                                    )
                                    val layer = layerStates[currentLayerIndex]
                                    layer.strokes.add(stroke)
                                    drawStrokeOnBitmap(layer.bitmap, stroke)
                                    redoStack.clear()
                                    undoStack.add(UndoEntry.Stroke(currentLayerIndex))
                                    saveAllLayers()
                                    canvasRefreshTrigger++
                                }
                            }
                        }
                        view.onTap = { bx, by ->
                            val context = DrawingContext(currentLayerIndex, canvasSize, view.scale)
                            val intent = currentTool.handleTap(bx, by, toolSettings, context)
                            when (intent) {
                                is TapIntent.Eyedropper -> compositeLayers()?.let { composite ->
                                    val x = bx.toInt()
                                    val y = by.toInt()
                                    selectedColor = ColorUtil.getPixelColor(composite, x, y)
                                    onConfirmStrokeColor(selectedColor)
                                    selectedTool = DrawTool.Pen
                                }
                                is TapIntent.Fill -> {
                                    if (currentLayerIndex in layerStates.indices) {
                                        val layer = layerStates[currentLayerIndex]
                                        val fillColorArgb = selectedColor.toArgb()
                                        val bitmapCopy = Bitmap.createBitmap(
                                            layer.bitmap.width,
                                            layer.bitmap.height,
                                            Bitmap.Config.ARGB_8888
                                        )
                                        android.graphics.Canvas(bitmapCopy).drawBitmap(layer.bitmap, 0f, 0f, null)
                                        val newBitmap = Bitmap.createBitmap(
                                            layer.bitmap.width,
                                            layer.bitmap.height,
                                            Bitmap.Config.ARGB_8888
                                        )
                                        android.graphics.Canvas(newBitmap).drawBitmap(layer.bitmap, 0f, 0f, null)
                                        ColorUtil.floodFill(
                                            newBitmap,
                                            intent.x.toInt(),
                                            intent.y.toInt(),
                                            fillColorArgb,
                                            tolerance = 18f
                                        )
                                        layer.bitmap = newBitmap
                                        layer.hasFill = true
                                        redoStack.clear()
                                        undoStack.add(UndoEntry.Fill(currentLayerIndex, bitmapCopy))
                                        saveAllLayers()
                                        canvasRefreshTrigger++
                                    }
                                }
                                is TapIntent.DrawDot -> {
                                    if (currentLayerIndex in layerStates.indices) {
                                        val layer = layerStates[currentLayerIndex]
                                        val bitmapBefore = Bitmap.createBitmap(layer.bitmap)
                                        val canvas = android.graphics.Canvas(layer.bitmap)
                                        val paint = Paint().apply {
                                            isAntiAlias = true
                                            color = intent.color.toArgb()
                                            style = Paint.Style.FILL
                                            if (intent.isEraser) {
                                                xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
                                            }
                                        }
                                        if (strokeCapStyle == StrokeCapStyle.ROUND) {
                                            canvas.drawCircle(intent.x, intent.y, intent.dotRadiusBitmap, paint)
                                        } else {
                                            canvas.drawRect(
                                                intent.x - intent.dotRadiusBitmap,
                                                intent.y - intent.dotRadiusBitmap,
                                                intent.x + intent.dotRadiusBitmap,
                                                intent.y + intent.dotRadiusBitmap,
                                                paint
                                            )
                                        }
                                        undoStack.add(UndoEntry.Fill(currentLayerIndex, bitmapBefore))
                                        redoStack.clear()
                                        saveAllLayers()
                                        canvasRefreshTrigger++
                                    }
                                }
                                null -> { }
                            }
                        }
                    }
                )
            // Zoom controls at bottom left
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { drawingViewRef?.takeIf { it.scale > 1f }?.zoomOut() }) {
                    Text(
                        text = "−",
                        color = HEADER_ICON_COLOR,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.size(HEADER_ICON_SIZE)
                    )
                }
                IconButton(onClick = { drawingViewRef?.zoomIn() }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Zoom in",
                        tint = HEADER_ICON_COLOR,
                        modifier = Modifier.size(HEADER_ICON_SIZE)
                    )
                }
            }
        }
        }
    }
    if (showExportDialog && exportBitmap != null) {
        val context = LocalContext.current
        val bmp = exportBitmap!!
        val fileSizeBytes = ByteArrayOutputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.size()
        }
        val fileSizeStr = if (fileSizeBytes >= 1024 * 1024) {
            "%.1f MB".format(fileSizeBytes / (1024.0 * 1024.0))
        } else {
            "%.1f KB".format(fileSizeBytes / 1024.0)
        }
        val savePath = getExportDirectoryPath(context)
        AlertDialog(
            onDismissRequest = {
                showExportDialog = false
                exportBitmap = null
            },
            containerColor = HEADER_BACKGROUND,
            titleContentColor = HEADER_ICON_COLOR,
            textContentColor = HEADER_ICON_COLOR,
            shape = RoundedCornerShape(0.dp),
            title = { Text("Export drawing", color = HEADER_ICON_COLOR) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Dimensions: ${bmp.width} × ${bmp.height}", color = HEADER_ICON_COLOR)
                    Text(text = "File size: ~$fileSizeStr", color = HEADER_ICON_COLOR)
                    Text(text = "Save location: $savePath", color = HEADER_ICON_COLOR)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onExport(bmp)
                    showExportDialog = false
                    exportBitmap = null
                }) { Text("Export", color = HEADER_ICON_COLOR) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExportDialog = false
                    exportBitmap = null
                }) { Text("Cancel", color = HEADER_ICON_COLOR) }
            }
        )
    }
    if (showColorPickerModal) {
        val context = LocalContext.current
        val lifecycleOwner = (context as? Activity) as? LifecycleOwner
        AlertDialog(
            onDismissRequest = {
                showColorPickerModal = false
                favoriteToDeleteArgb = null
            },
            containerColor = HEADER_BACKGROUND,
            titleContentColor = HEADER_ICON_COLOR,
            textContentColor = HEADER_ICON_COLOR,
            shape = RoundedCornerShape(0.dp),
            title = null,
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text("Favorites", style = MaterialTheme.typography.titleSmall, color = HEADER_ICON_COLOR)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        favoriteColorsArgb.forEach { argb ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color(argb), CircleShape)
                                    .border(
                                        2.dp,
                                        if (argb == pendingColor.toArgb()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        CircleShape
                                    )
                                    .combinedClickable(
                                        onClick = { pendingColor = Color(argb) },
                                        onLongClick = { favoriteToDeleteArgb = argb }
                                    )
                            )
                        }
                    }
                    Text("Presets", style = MaterialTheme.typography.titleSmall, color = HEADER_ICON_COLOR)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        DESATURATED_PRESETS.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(color, CircleShape)
                                    .border(
                                        2.dp,
                                        if (color.toArgb() == pendingColor.toArgb()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        CircleShape
                                    )
                                    .clickable { pendingColor = color }
                            )
                        }
                    }
                    AndroidView(
                        factory = {
                            val density = it.resources.displayMetrics.density
                            val wheelSizePx = (280 * density).toInt()
                            val sliderHeightPx = (48 * density).toInt()
                            val column = LinearLayout(it).apply {
                                orientation = LinearLayout.VERTICAL
                            }
                            val brightnessBar = BrightnessSlideBar(it).apply {
                                val whiteLine = GradientDrawable().apply {
                                    setColor(android.graphics.Color.WHITE)
                                    setShape(GradientDrawable.RECTANGLE)
                                    setSize((4 * density).toInt(), sliderHeightPx)
                                }
                                setSelectorDrawable(whiteLine)
                            }
                            val picker = ColorPickerView.Builder(it)
                                .setInitialColor(pendingColor.toArgb())
                                .setColorListener(ColorEnvelopeListener { envelope, _ ->
                                    val newColor = Color(envelope.getColor())
                                    pendingColor = newColor
                                    brightnessBar.post {
                                        brightnessBar.setSelectorPosition(ColorUtil.colorToHsvValue(newColor))
                                    }
                                })
                                .setActionMode(ActionMode.LAST)
                                .apply { lifecycleOwner?.let { setLifecycleOwner(it) } }
                                .build()
                            picker.attachBrightnessSlider(brightnessBar)
                            column.addView(picker, ViewGroup.LayoutParams(wheelSizePx, wheelSizePx))
                            val sliderParams = android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, sliderHeightPx).apply {
                                topMargin = (8 * density).toInt()
                            }
                            column.addView(brightnessBar, sliderParams)
                            column
                        },
                        modifier = Modifier.size(280.dp, 320.dp),
                        update = { root ->
                            val color = pendingColor
                            val column = root as? ViewGroup
                            if (column != null) {
                                val picker = column.getChildAt(0) as? ColorPickerView
                                val brightnessBar = column.getChildAt(1) as? BrightnessSlideBar
                                root.post {
                                    if (picker != null && picker.width > 0 && picker.height > 0) {
                                        picker.setHsvPaletteDrawable()
                                        picker.selectByHsvColor(color.toArgb())
                                    }
                                    if (brightnessBar != null && brightnessBar.width > 0) {
                                        brightnessBar.setSelectorPosition(ColorUtil.colorToHsvValue(color))
                                    }
                                }
                            }
                        }
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Selection", style = MaterialTheme.typography.titleSmall, color = HEADER_ICON_COLOR)
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(pendingColor, CircleShape)
                                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        )
                    }
                    TextButton(
                        modifier = Modifier.border(1.dp, Color.White, RoundedCornerShape(4.dp)),
                        onClick = {
                            val argb = pendingColor.toArgb()
                            if (argb !in favoriteColorsArgb) {
                                favoriteColorsArgb.add(argb)
                                saveFavoriteColors(favoriteColorsArgb.toList())
                            }
                        }
                    ) { Text("Add to favorites", color = HEADER_ICON_COLOR) }
                }
            },
            confirmButton = {
                TextButton(
                    modifier = Modifier.border(1.dp, Color.White, RoundedCornerShape(4.dp)),
                    onClick = {
                        selectedColor = pendingColor
                        onConfirmStrokeColor(pendingColor)
                        showColorPickerModal = false
                        favoriteToDeleteArgb = null
                    }
                ) { Text("Confirm", color = HEADER_ICON_COLOR) }
            },
            dismissButton = {
                TextButton(
                    modifier = Modifier.border(1.dp, Color.White, RoundedCornerShape(4.dp)),
                    onClick = {
                        showColorPickerModal = false
                        favoriteToDeleteArgb = null
                    }
                ) { Text("Cancel", color = HEADER_ICON_COLOR) }
            }
        )
    }
    favoriteToDeleteArgb?.let { argb ->
        AlertDialog(
            onDismissRequest = { favoriteToDeleteArgb = null },
            containerColor = HEADER_BACKGROUND,
            titleContentColor = HEADER_ICON_COLOR,
            textContentColor = HEADER_ICON_COLOR,
            title = { Text("Remove from favorites?", color = HEADER_ICON_COLOR) },
            confirmButton = {
                TextButton(onClick = {
                    favoriteColorsArgb.remove(argb)
                    saveFavoriteColors(favoriteColorsArgb.toList())
                    favoriteToDeleteArgb = null
                }) { Text("Delete", color = HEADER_ICON_COLOR) }
            },
            dismissButton = {
                TextButton(onClick = { favoriteToDeleteArgb = null }) { Text("Cancel", color = HEADER_ICON_COLOR) }
            }
        )
    }

    if (showLayerManagerDialog) {
        LayerManagerDialog(
            layerStates = layerStates,
            currentLayerIndex = currentLayerIndex,
            onLayerSelected = { index -> currentLayerIndex = index },
            onAddLayer = { addLayer() },
            onDeleteLayer = { index -> deleteLayer(index) },
            onToggleLayerHidden = { index ->
                if (index in layerStates.indices) {
                    val layer = layerStates[index]
                    layerStates[index] = layer.copy(isHidden = !layer.isHidden)
                    saveAllLayers()
                }
            },
            onDismiss = { showLayerManagerDialog = false },
            backgroundColor = backgroundColor
        )
    }
    
    if (showToolSelectionModal) {
        ToolSelectionDialog(
            toolRegistry = toolRegistry,
            selectedTool = selectedTool,
            onToolSelected = { tool ->
                selectedTool = tool
                showToolSelectionModal = false
            },
            onDismiss = { showToolSelectionModal = false }
        )
    }
}

@Composable
private fun PreviewDot(
    strokeWidth: Float,
    strokeColor: Color,
    tool: DrawTool,
    containerBackground: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .background(containerBackground, CircleShape)
            .border(1.dp, borderColor, CircleShape)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (tool == DrawTool.Eraser) {
                // For eraser, show a circle with white interior and border to indicate size
                val radius = strokeWidth / 2f
                // Draw white filled circle
                drawCircle(
                    color = Color.White,
                    radius = radius,
                    center = center
                )
                // Draw border circle to show the size
                drawCircle(
                    color = borderColor,
                    radius = radius,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )
            } else {
                drawCircle(
                    color = strokeColor,
                    radius = strokeWidth / 2f,
                    center = center
                )
            }
        }
    }
}

private fun drawStrokeOnBitmap(bitmap: Bitmap?, stroke: Stroke) {
    val bmp = bitmap ?: return
    val canvas = android.graphics.Canvas(bmp)
    val paint = Paint().apply {
        color = stroke.color.toArgb()
        style = Paint.Style.STROKE
        strokeWidth = stroke.strokeWidth
        isAntiAlias = true
        strokeJoin = if (stroke.strokeCapStyle == StrokeCapStyle.ROUND) Paint.Join.ROUND else Paint.Join.BEVEL
        strokeCap = if (stroke.strokeCapStyle == StrokeCapStyle.ROUND) Paint.Cap.ROUND else Paint.Cap.BUTT
        // For eraser, use CLEAR blend mode to erase pixels
        if (stroke.tool == DrawTool.Eraser) {
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
        }
    }
    val path = android.graphics.Path()
    var lastX = 0f
    var lastY = 0f
    stroke.points.forEachIndexed { index, offset ->
        if (index == 0) {
            path.moveTo(offset.x, offset.y)
            lastX = offset.x
            lastY = offset.y
        } else {
            val dx = offset.x - lastX
            val dy = offset.y - lastY
            if (hypot(dx, dy) >= MIN_STROKE_SEGMENT_PX) {
                path.lineTo(offset.x, offset.y)
                lastX = offset.x
                lastY = offset.y
            }
        }
    }
    if (stroke.closed && stroke.points.isNotEmpty()) {
        val start = stroke.points.first()
        if (stroke.strokeCapStyle == StrokeCapStyle.BUTT || stroke.points.size < 3) {
            path.lineTo(start.x, start.y)
        } else {
            val end = stroke.points.last()
            val prev = stroke.points[stroke.points.size - 2]
            val controlX = end.x + (end.x - prev.x) * 0.75f
            val controlY = end.y + (end.y - prev.y) * 0.75f
            path.quadTo(controlX, controlY, start.x, start.y)
        }
        path.close()
    }
    canvas.drawPath(path, paint)
}


private fun generateLayerThumbnail(bitmap: Bitmap, size: Int = 80): Bitmap {
    if (bitmap.width <= 0 || bitmap.height <= 0) {
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    }
    val scale = size.toFloat() / maxOf(bitmap.width, bitmap.height).coerceAtLeast(1)
    val scaledWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
    val scaledHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
}

private fun hasTransparency(bitmap: Bitmap): Boolean {
    if (!bitmap.hasAlpha()) return false
    // Sample pixels to check for transparency
    // Check corners and center to avoid checking every pixel
    val samplePoints = listOf(
        0 to 0,
        bitmap.width - 1 to 0,
        0 to bitmap.height - 1,
        bitmap.width - 1 to bitmap.height - 1,
        bitmap.width / 2 to bitmap.height / 2
    )
    return samplePoints.any { (x, y) ->
        if (x < 0 || x >= bitmap.width || y < 0 || y >= bitmap.height) return@any false
        val pixel = bitmap.getPixel(x, y)
        android.graphics.Color.alpha(pixel) < 255
    }
}

@Composable
private fun TransparencyCheckerboard(
    modifier: Modifier = Modifier,
    squareSize: Int = 8
) {
    Canvas(modifier = modifier) {
        val squareSizePx = squareSize.dp.toPx()
        val lightGray = Color(0xFFE0E0E0)
        val white = Color.White
        
        val horizontalSquares = (size.width / squareSizePx).toInt() + 1
        val verticalSquares = (size.height / squareSizePx).toInt() + 1
        
        for (y in 0 until verticalSquares) {
            for (x in 0 until horizontalSquares) {
                val color = if ((x + y) % 2 == 0) white else lightGray
                drawRect(
                    color = color,
                    topLeft = Offset(x * squareSizePx, y * squareSizePx),
                    size = androidx.compose.ui.geometry.Size(squareSizePx, squareSizePx)
                )
            }
        }
    }
}

@Composable
private fun LayerManagerDialog(
    layerStates: List<LayerState>,
    currentLayerIndex: Int,
    onLayerSelected: (Int) -> Unit,
    onAddLayer: () -> Unit,
    onDeleteLayer: (Int) -> Unit,
    onToggleLayerHidden: (Int) -> Unit,
    onDismiss: () -> Unit,
    backgroundColor: Int
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HEADER_BACKGROUND,
        titleContentColor = HEADER_ICON_COLOR,
        textContentColor = HEADER_ICON_COLOR,
        shape = RoundedCornerShape(0.dp),
        title = { Text("Layers", color = HEADER_ICON_COLOR) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                // Add Layer button at top
                TextButton(
                    onClick = {
                        onAddLayer()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Layer", tint = HEADER_ICON_COLOR)
                    Text("Add Layer", modifier = Modifier.padding(start = 8.dp), color = HEADER_ICON_COLOR)
                }
                
                // Layer list (newest first)
                val reversedLayersWithIndices = layerStates.mapIndexed { index, layer -> index to layer }.reversed()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = reversedLayersWithIndices,
                        key = { it.first }
                    ) { (originalIndex, layer) ->
                        val isSelected = originalIndex == currentLayerIndex
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    HEADER_ICON_COLOR,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onLayerSelected(originalIndex) }
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Check icon/checkbox
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = HEADER_ICON_COLOR,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Box(modifier = Modifier.size(24.dp))
                            }
                            
                            // Thumbnail preview
                            val thumbnail = remember(originalIndex, layer.bitmap) {
                                generateLayerThumbnail(layer.bitmap, 64)
                            }
                            // First layer (index 0) always uses background color, layers 1+ check for transparency
                            val isFirstLayer = originalIndex == 0
                            val hasTransparentBg = remember(originalIndex, layer.bitmap) {
                                !isFirstLayer && hasTransparency(layer.bitmap)
                            }
                            val bgColor = if (isFirstLayer) {
                                // Use background color for first layer, default to white if invalid
                                if (backgroundColor != 0) Color(backgroundColor) else Color.White
                            } else {
                                // For other layers, use transparent if layer has transparency
                                if (hasTransparentBg) Color.Transparent else Color(backgroundColor)
                            }
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(
                                        bgColor,
                                        RoundedCornerShape(4.dp)
                                    )
                            ) {
                                if (hasTransparentBg) {
                                    TransparencyCheckerboard(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(4.dp))
                                    )
                                }
                                // Only show thumbnail image if layer is not hidden
                                if (!layer.isHidden) {
                                    Image(
                                        bitmap = thumbnail.asImageBitmap(),
                                        contentDescription = "Layer ${originalIndex + 1} preview",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }
                            
                            // Layer label
                            Text(
                                text = "#${originalIndex + 1}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = HEADER_ICON_COLOR,
                                modifier = Modifier.weight(1f)
                            )
                            
                            // Visibility toggle (persisted per-layer; no other behavior yet)
                            IconButton(
                                onClick = { onToggleLayerHidden(originalIndex) }
                            ) {
                                Icon(
                                    painter = painterResource(
                                        id = if (layer.isHidden) R.drawable.ic_visibility_off else R.drawable.ic_visibility
                                    ),
                                    contentDescription = if (layer.isHidden) "Show layer" else "Hide layer",
                                    tint = HEADER_ICON_COLOR,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Delete icon (only show if more than one layer)
                            if (layerStates.size > 1) {
                                IconButton(
                                    onClick = { onDeleteLayer(originalIndex) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete layer",
                                        tint = HEADER_ICON_COLOR,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = HEADER_ICON_COLOR) }
        }
    )
}

@Composable
private fun ToolSelectionDialog(
    toolRegistry: ToolRegistry,
    selectedTool: DrawTool,
    onToolSelected: (DrawTool) -> Unit,
    onDismiss: () -> Unit
) {
    val allTools = toolRegistry.getAllTools()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HEADER_BACKGROUND,
        titleContentColor = HEADER_ICON_COLOR,
        textContentColor = HEADER_ICON_COLOR,
        shape = RoundedCornerShape(0.dp),
        title = { Text("Select Tool", color = HEADER_ICON_COLOR) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                allTools.forEach { tool ->
                    val isSelected = tool.drawTool == selectedTool
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isSelected) {
                                    Modifier.border(
                                        1.dp,
                                        HEADER_ICON_COLOR,
                                        RoundedCornerShape(8.dp)
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .clickable { onToolSelected(tool.drawTool) }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = tool.iconRes),
                            contentDescription = tool.displayName,
                            tint = HEADER_ICON_COLOR,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = tool.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                            color = HEADER_ICON_COLOR
                        )
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = HEADER_ICON_COLOR,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Box(modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

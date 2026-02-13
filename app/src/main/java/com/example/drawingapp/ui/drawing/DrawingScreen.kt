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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.skydoves.colorpickerview.ActionMode
import com.skydoves.colorpickerview.ColorPickerView
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import com.skydoves.colorpickerview.sliders.BrightnessSlideBar
import com.example.drawingapp.data.DrawTool
import com.example.drawingapp.data.Stroke
import com.example.drawingapp.data.StrokeCapStyle
import com.example.drawingapp.util.getExportDirectoryPath
import java.io.ByteArrayOutputStream

private const val PENCIL_ALPHA = 0.75f
private val STROKE_SIZE_RANGE = 1f..64f
private const val DEFAULT_STROKE_SIZE_PX = 20f // ~30% into 1..64
private val DEFAULT_COLOR = Color.Black

private val HEADER_BACKGROUND = Color(0xFF565563)
private val HEADER_ICON_COLOR = Color(0xFFE1D8D5)
private val HEADER_ICON_SIZE = 29.dp // 20% larger than default 24.dp

/** Represents one undoable action for the unified undo stack. */
private sealed class UndoEntry {
    data class Stroke(val layerIndex: Int) : UndoEntry()
    data class Fill(val layerIndex: Int, val bitmapBeforeFill: Bitmap) : UndoEntry()
}

@Composable
private fun getToolIconRes(tool: DrawTool): Int {
    return when (tool) {
        DrawTool.Pen -> R.drawable.ic_pen
        DrawTool.Pencil -> R.drawable.ic_pencil
        DrawTool.MarkerPen -> R.drawable.ic_marker
        DrawTool.Eraser -> R.drawable.ic_eraser
        DrawTool.Fill -> R.drawable.ic_fill
    }
}

@Composable
private fun getToolDisplayName(tool: DrawTool): String {
    return when (tool) {
        DrawTool.Pen -> "Pen"
        DrawTool.Pencil -> "Pencil"
        DrawTool.MarkerPen -> "Marker Pen"
        DrawTool.Eraser -> "Eraser"
        DrawTool.Fill -> "Fill"
    }
}

private fun colorFromHsv(hue: Float, saturation: Float, value: Float): Color {
    val h = hue.coerceIn(0f, 360f) / 60f
    val s = saturation.coerceIn(0f, 1f)
    val v = value.coerceIn(0f, 1f)
    val c = v * s
    val x = c * (1 - abs(h % 2f - 1f))
    val m = v - c
    val (r, g, b) = when {
        h < 1 -> Triple(c, x, 0f)
        h < 2 -> Triple(x, c, 0f)
        h < 3 -> Triple(0f, c, x)
        h < 4 -> Triple(0f, x, c)
        h < 5 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        red = (r + m).coerceIn(0f, 1f),
        green = (g + m).coerceIn(0f, 1f),
        blue = (b + m).coerceIn(0f, 1f),
        alpha = 1f
    )
}

private val DESATURATED_PRESETS = listOf(0f, 30f, 60f, 90f, 120f, 150f, 180f, 210f, 240f, 270f, 300f, 330f).map { hue ->
    colorFromHsv(hue, 0.55f, 0.9f)
}

/** Returns HSV value (0f..1f) for the given color, for brightness slider position. */
private fun colorToHsvValue(color: androidx.compose.ui.graphics.Color): Float {
    val argb = color.toArgb()
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        android.graphics.Color.red(argb),
        android.graphics.Color.green(argb),
        android.graphics.Color.blue(argb),
        hsv
    )
    return hsv[2]
}

private val BG_COLOR_PALETTE = listOf(
    Color.White,
    Color(0xFFE53935),
    Color(0xFF43A047),
    Color(0xFF1E88E5),
    Color(0xFFFB8C00),
    Color(0xFF8E24AA)
)

@Composable
private fun StrokeCapToggleButton(
    currentCap: StrokeCapStyle,
    onClick: () -> Unit,
    iconColor: Color = MaterialTheme.colorScheme.onSurface,
    buttonSizeDp: androidx.compose.ui.unit.Dp = 32.dp,
    modifier: Modifier = Modifier
) {
    val strokeCap = if (currentCap == StrokeCapStyle.ROUND) StrokeCap.Round else StrokeCap.Butt
    Box(
        modifier = modifier
            .size(buttonSizeDp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Circle (arc open at bottom) containing the line tip — same size as color picker circle
        Canvas(Modifier.size(buttonSizeDp)) {
            val arcStroke = 2.5.dp.toPx()
            val lineWidth = 6.dp.toPx()
            val inset = 2.dp.toPx()
            val rect = androidx.compose.ui.geometry.Rect(inset, inset, size.width - inset, size.height - inset)
            // Full circle (360°) except small gap at bottom: start at 100° (just after bottom) and sweep 340° to end at 80° (just before bottom)
            // This draws: 100° → 180° (left) → 270° (top) → 360°/0° (right) → 80°, leaving only ~20° gap at bottom
            drawArc(
                color = iconColor,
                topLeft = rect.topLeft,
                size = rect.size,
                startAngle = 100f,
                sweepAngle = 340f,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = arcStroke)
            )
            val centerX = size.width / 2f
            val path = Path().apply {
                moveTo(centerX, inset + lineWidth)
                lineTo(centerX, size.height - inset)
            }
            drawPath(
                path = path,
                color = iconColor,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = lineWidth, cap = strokeCap)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawingScreen(
    pageId: String,
    onLoadLayers: (String) -> List<Bitmap?>,
    onSaveLayers: (String, List<Bitmap>, Int) -> Unit,
    onLoadBackgroundColor: (String) -> Int,
    onSaveBackgroundColor: (String, Int) -> Unit,
    onExport: (Bitmap) -> Unit,
    initialStrokeSizePx: Float = DEFAULT_STROKE_SIZE_PX,
    onSaveStrokeSizePx: (Float) -> Unit = {},
    initialStrokeColor: Color = DEFAULT_COLOR,
    onConfirmStrokeColor: (Color) -> Unit = {},
    initialStrokeCap: StrokeCapStyle = StrokeCapStyle.ROUND,
    onSaveStrokeCap: (StrokeCapStyle) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val layerStates = remember(pageId) { mutableStateListOf<LayerState>() }
    var currentLayerIndex by remember(pageId) { mutableStateOf(0) }
    var currentStrokePoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var selectedTool by remember { mutableStateOf(DrawTool.Pen) }
    var selectedColor by remember(initialStrokeColor) { mutableStateOf(initialStrokeColor) }
    var showColorPickerModal by remember { mutableStateOf(false) }
    var pendingColor by remember { mutableStateOf(selectedColor) }
    var strokeSizePx by remember(initialStrokeSizePx) {
        mutableStateOf(initialStrokeSizePx.coerceIn(STROKE_SIZE_RANGE))
    }
    var strokeCapStyle by remember(initialStrokeCap) { mutableStateOf(initialStrokeCap) }
    var backgroundColor by remember(pageId) { mutableStateOf(0xFFFFFFFF.toInt()) }
    var showBgColorPickerModal by remember { mutableStateOf(false) }
    var pendingBgColor by remember { mutableStateOf(Color(backgroundColor)) }
    var initialized by remember(pageId) { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showLayerManagerDialog by remember { mutableStateOf(false) }
    var showToolSelectionModal by remember { mutableStateOf(false) }
    var canvasRefreshTrigger by remember { mutableStateOf(0) }
    val undoStack = remember(pageId) { mutableStateListOf<UndoEntry>() }

    val strokeWidth = strokeSizePx
    val strokeColor = when (selectedTool) {
        DrawTool.Pen -> selectedColor
        DrawTool.Pencil -> selectedColor.copy(alpha = PENCIL_ALPHA)
        DrawTool.MarkerPen -> selectedColor.copy(alpha = 0.6f) // Semi-transparent for marker effect
        DrawTool.Eraser -> Color.Transparent // Eraser uses transparent color
        DrawTool.Fill -> selectedColor // Fill uses selected color for the fill
    }

    fun saveAllLayers() {
        if (layerStates.isNotEmpty()) {
            onSaveLayers(pageId, layerStates.map { it.bitmap }, backgroundColor)
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
                layer.strokes.removeAt(layer.strokes.lastIndex)
                layer.bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
                layer.strokes.forEach { stroke -> drawStrokeOnBitmap(layer.bitmap, stroke) }
            }
            is UndoEntry.Fill -> {
                if (entry.layerIndex in layerStates.indices) {
                    layerStates[entry.layerIndex].bitmap = entry.bitmapBeforeFill
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
            canvas.drawBitmap(layer.bitmap, 0f, 0f, null)
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
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { showToolSelectionModal = true }
                        ) {
                            Icon(
                                painter = painterResource(id = getToolIconRes(selectedTool)),
                                contentDescription = getToolDisplayName(selectedTool),
                                tint = HEADER_ICON_COLOR,
                                modifier = Modifier.size(HEADER_ICON_SIZE)
                            )
                        }
                        StrokeCapToggleButton(
                            currentCap = strokeCapStyle,
                            onClick = {
                                strokeCapStyle = if (strokeCapStyle == StrokeCapStyle.ROUND) StrokeCapStyle.BUTT else StrokeCapStyle.ROUND
                                onSaveStrokeCap(strokeCapStyle)
                            },
                            iconColor = HEADER_ICON_COLOR,
                            buttonSizeDp = 38.dp
                        )
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(selectedColor, CircleShape)
                                .border(2.dp, HEADER_ICON_COLOR, CircleShape)
                                .clickable {
                                    pendingColor = selectedColor
                                    showColorPickerModal = true
                                }
                        )
                        Text("Bg", color = HEADER_ICON_COLOR, modifier = Modifier.padding(horizontal = 4.dp))
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(Color(backgroundColor), CircleShape)
                                .border(2.dp, HEADER_ICON_COLOR, CircleShape)
                                .clickable {
                                    pendingBgColor = Color(backgroundColor)
                                    showBgColorPickerModal = true
                                }
                        )
                        IconButton(onClick = { undo() }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_undo),
                                contentDescription = "Undo",
                                tint = HEADER_ICON_COLOR,
                                modifier = Modifier.size(HEADER_ICON_SIZE)
                            )
                        }
                        IconButton(onClick = { showLayerManagerDialog = true }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_layers),
                                contentDescription = "Layers",
                                tint = HEADER_ICON_COLOR,
                                modifier = Modifier.size(HEADER_ICON_SIZE)
                            )
                        }
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
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { size ->
                        if (size.width > 0 && size.height > 0 && (canvasSize != size)) {
                            canvasSize = size
                            if (!initialized) {
                                initialized = true
                                val loaded = onLoadLayers(pageId)
                                backgroundColor = onLoadBackgroundColor(pageId)
                                if (loaded.isEmpty()) {
                                    val bmp = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
                                    bmp.eraseColor(android.graphics.Color.TRANSPARENT)
                                    layerStates.add(LayerState(bitmap = bmp))
                                } else {
                                    loaded.forEach { lb ->
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
                                        layerStates.add(LayerState(bitmap = bmp))
                                    }
                                }
                                if (currentLayerIndex >= layerStates.size) currentLayerIndex = 0
                            }
                        }
                    }
                    .pointerInput(selectedTool, selectedColor, currentLayerIndex, strokeWidth) {
                        if (selectedTool == DrawTool.Fill) {
                            detectTapGestures(
                                onTap = { offset ->
                                    if (currentLayerIndex in layerStates.indices) {
                                        val layer = layerStates[currentLayerIndex]
                                        val fillColorArgb = selectedColor.toArgb()
                                        val bitmapCopy = Bitmap.createBitmap(
                                            layer.bitmap.width,
                                            layer.bitmap.height,
                                            Bitmap.Config.ARGB_8888
                                        )
                                        android.graphics.Canvas(bitmapCopy).drawBitmap(layer.bitmap, 0f, 0f, null)
                                        floodFill(
                                            layer.bitmap,
                                            offset.x.toInt(),
                                            offset.y.toInt(),
                                            fillColorArgb
                                        )
                                        undoStack.add(UndoEntry.Fill(currentLayerIndex, bitmapCopy))
                                        saveAllLayers()
                                        canvasRefreshTrigger++
                                    }
                                }
                            )
                        } else {
                            detectDragGestures(
                                onDragStart = { currentStrokePoints = listOf(it) },
                                onDrag = { change, _ -> currentStrokePoints = currentStrokePoints + change.position },
                                onDragEnd = {
                                    if (currentLayerIndex in layerStates.indices && currentStrokePoints.size > 1) {
                                        val stroke = Stroke(
                                            points = currentStrokePoints,
                                            color = strokeColor,
                                            strokeWidth = strokeWidth,
                                            tool = selectedTool,
                                            strokeCapStyle = strokeCapStyle
                                        )
                                        val layer = layerStates[currentLayerIndex]
                                        layer.strokes.add(stroke)
                                        drawStrokeOnBitmap(layer.bitmap, stroke)
                                        undoStack.add(UndoEntry.Stroke(currentLayerIndex))
                                        saveAllLayers()
                                    }
                                    currentStrokePoints = emptyList()
                                }
                            )
                        }
                    }
            ) {
                if (layerStates.isNotEmpty()) {
                    key(canvasRefreshTrigger) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawRect(Color(backgroundColor))
                        // Draw layers below the current layer
                        for (i in 0 until currentLayerIndex) {
                            if (i in layerStates.indices) {
                                drawImage(layerStates[i].bitmap.asImageBitmap(), topLeft = Offset.Zero)
                            }
                        }
                        // Draw the current layer bitmap (existing strokes on this layer)
                        if (currentLayerIndex in layerStates.indices) {
                            drawImage(layerStates[currentLayerIndex].bitmap.asImageBitmap(), topLeft = Offset.Zero)
                        }
                        // Draw current stroke preview (if drawing) - appears above current layer but below layers above
                        if (currentStrokePoints.isNotEmpty()) {
                            val path = Path().apply {
                                currentStrokePoints.forEachIndexed { i, o ->
                                    if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
                                }
                            }
                            if (selectedTool == DrawTool.Eraser) {
                                // For eraser, show a red dashed line preview
                                drawPath(
                                    path = path,
                                    color = Color.Red.copy(alpha = 0.5f),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = strokeWidth,
                                        cap = if (strokeCapStyle == StrokeCapStyle.ROUND) StrokeCap.Round else StrokeCap.Butt,
                                        join = if (strokeCapStyle == StrokeCapStyle.ROUND) StrokeJoin.Round else StrokeJoin.Bevel,
                                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                                    )
                                )
                            } else {
                                drawPath(
                                    path = path,
                                    color = strokeColor,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = strokeWidth,
                                        cap = if (strokeCapStyle == StrokeCapStyle.ROUND) StrokeCap.Round else StrokeCap.Butt,
                                        join = if (strokeCapStyle == StrokeCapStyle.ROUND) StrokeJoin.Round else StrokeJoin.Bevel
                                    )
                                )
                            }
                        }
                        // Draw layers above the current layer
                        for (i in (currentLayerIndex + 1) until layerStates.size) {
                            drawImage(layerStates[i].bitmap.asImageBitmap(), topLeft = Offset.Zero)
                        }
                    }
                    }
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
            title = { Text("Export drawing") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Dimensions: ${bmp.width} × ${bmp.height}")
                    Text(text = "File size: ~$fileSizeStr")
                    Text(text = "Save location: $savePath")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onExport(bmp)
                    showExportDialog = false
                    exportBitmap = null
                }) { Text("Export") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExportDialog = false
                    exportBitmap = null
                }) { Text("Cancel") }
            }
        )
    }
    if (showColorPickerModal) {
        val context = LocalContext.current
        val lifecycleOwner = (context as? Activity) as? LifecycleOwner
        AlertDialog(
            onDismissRequest = { showColorPickerModal = false },
            title = { Text("Stroke color") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Presets", style = MaterialTheme.typography.titleSmall)
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
                            val picker = ColorPickerView.Builder(it)
                                .setInitialColor(pendingColor.toArgb())
                                .setColorListener(ColorEnvelopeListener { envelope, _ ->
                                    pendingColor = Color(envelope.getColor())
                                })
                                .setActionMode(ActionMode.LAST)
                                .apply { lifecycleOwner?.let { setLifecycleOwner(it) } }
                                .build()
                            val brightnessBar = BrightnessSlideBar(it).apply {
                                val whiteLine = GradientDrawable().apply {
                                    setColor(android.graphics.Color.WHITE)
                                    setShape(GradientDrawable.RECTANGLE)
                                    setSize((4 * density).toInt(), sliderHeightPx)
                                }
                                setSelectorDrawable(whiteLine)
                            }
                            picker.attachBrightnessSlider(brightnessBar)
                            column.addView(picker, ViewGroup.LayoutParams(wheelSizePx, wheelSizePx))
                            column.addView(brightnessBar, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, sliderHeightPx))
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
                                        brightnessBar.setSelectorPosition(colorToHsvValue(color))
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
                        Text("Selection", style = MaterialTheme.typography.titleSmall)
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(pendingColor, CircleShape)
                                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedColor = pendingColor
                    onConfirmStrokeColor(pendingColor)
                    showColorPickerModal = false
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { showColorPickerModal = false }) { Text("Cancel") }
            }
        )
    }

    if (showLayerManagerDialog) {
        LayerManagerDialog(
            layerStates = layerStates,
            currentLayerIndex = currentLayerIndex,
            onLayerSelected = { index -> currentLayerIndex = index },
            onAddLayer = { addLayer() },
            onDismiss = { showLayerManagerDialog = false },
            backgroundColor = backgroundColor
        )
    }
    
    if (showToolSelectionModal) {
        ToolSelectionDialog(
            selectedTool = selectedTool,
            onToolSelected = { tool ->
                selectedTool = tool
                showToolSelectionModal = false
            },
            onDismiss = { showToolSelectionModal = false }
        )
    }
    
    if (showBgColorPickerModal) {
        val context = LocalContext.current
        val lifecycleOwner = (context as? Activity) as? LifecycleOwner
        AlertDialog(
            onDismissRequest = { showBgColorPickerModal = false },
            title = { Text("Background color") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Presets", style = MaterialTheme.typography.titleSmall)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        BG_COLOR_PALETTE.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(color, CircleShape)
                                    .border(
                                        2.dp,
                                        if (color.toArgb() == pendingBgColor.toArgb()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        CircleShape
                                    )
                                    .clickable { pendingBgColor = color }
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
                            val picker = ColorPickerView.Builder(it)
                                .setInitialColor(pendingBgColor.toArgb())
                                .setColorListener(ColorEnvelopeListener { envelope, _ ->
                                    pendingBgColor = Color(envelope.getColor())
                                })
                                .setActionMode(ActionMode.LAST)
                                .apply { lifecycleOwner?.let { setLifecycleOwner(it) } }
                                .build()
                            val brightnessBar = BrightnessSlideBar(it).apply {
                                val whiteLine = GradientDrawable().apply {
                                    setColor(android.graphics.Color.WHITE)
                                    setShape(GradientDrawable.RECTANGLE)
                                    setSize((4 * density).toInt(), sliderHeightPx)
                                }
                                setSelectorDrawable(whiteLine)
                            }
                            picker.attachBrightnessSlider(brightnessBar)
                            column.addView(picker, ViewGroup.LayoutParams(wheelSizePx, wheelSizePx))
                            column.addView(brightnessBar, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, sliderHeightPx))
                            column
                        },
                        modifier = Modifier.size(280.dp, 320.dp),
                        update = { root ->
                            val color = pendingBgColor
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
                                        brightnessBar.setSelectorPosition(colorToHsvValue(color))
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
                        Text("Selection", style = MaterialTheme.typography.titleSmall)
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(pendingBgColor, CircleShape)
                                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    backgroundColor = pendingBgColor.toArgb()
                    onSaveBackgroundColor(pageId, backgroundColor)
                    showBgColorPickerModal = false
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { showBgColorPickerModal = false }) { Text("Cancel") }
            }
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
    stroke.points.forEachIndexed { index, offset ->
        if (index == 0) path.moveTo(offset.x, offset.y)
        else path.lineTo(offset.x, offset.y)
    }
    canvas.drawPath(path, paint)
}

/** Flood-fill connected pixels of the same color with the fill color. Uses 4-connectivity. */
private fun floodFill(bitmap: Bitmap, startX: Int, startY: Int, fillColorArgb: Int) {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= 0 || h <= 0) return
    val x0 = startX.coerceIn(0, w - 1)
    val y0 = startY.coerceIn(0, h - 1)
    val targetColor = bitmap.getPixel(x0, y0)
    if (targetColor == fillColorArgb) return
    val queue = ArrayDeque<Pair<Int, Int>>()
    queue.add(x0 to y0)
    bitmap.setPixel(x0, y0, fillColorArgb)
    val neighbors = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
    while (queue.isNotEmpty()) {
        val (x, y) = queue.removeFirst()
        for ((dx, dy) in neighbors) {
            val nx = x + dx
            val ny = y + dy
            if (nx in 0 until w && ny in 0 until h && bitmap.getPixel(nx, ny) == targetColor) {
                bitmap.setPixel(nx, ny, fillColorArgb)
                queue.add(nx to ny)
            }
        }
    }
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
    onDismiss: () -> Unit,
    backgroundColor: Int
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Layers") },
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
                    Icon(Icons.Default.Add, contentDescription = "Add Layer")
                    Text("Add Layer", modifier = Modifier.padding(start = 8.dp))
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
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
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
                                    tint = MaterialTheme.colorScheme.primary,
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
                                Image(
                                    bitmap = thumbnail.asImageBitmap(),
                                    contentDescription = "Layer ${originalIndex + 1} preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            
                            // Layer label
                            Text(
                                text = "Layer ${originalIndex + 1}",
                                style = MaterialTheme.typography.bodyLarge
                            )
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

@Composable
private fun ToolSelectionDialog(
    selectedTool: DrawTool,
    onToolSelected: (DrawTool) -> Unit,
    onDismiss: () -> Unit
) {
    val allTools = listOf(DrawTool.Pen, DrawTool.Pencil, DrawTool.MarkerPen, DrawTool.Eraser, DrawTool.Fill)
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Tool") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                allTools.forEach { tool ->
                    val isSelected = tool == selectedTool
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onToolSelected(tool) }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Tool icon
                        Icon(
                            painter = painterResource(id = getToolIconRes(tool)),
                            contentDescription = getToolDisplayName(tool),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                        
                        // Tool name
                        Text(
                            text = getToolDisplayName(tool),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        
                        // Checkmark
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
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

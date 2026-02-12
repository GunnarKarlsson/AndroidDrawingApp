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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
private fun StrokeCapButton(
    cap: StrokeCapStyle,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strokeCap = if (cap == StrokeCapStyle.ROUND) StrokeCap.Round else StrokeCap.Butt
    val lineColor = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .size(40.dp)
            .padding(4.dp)
            .then(
                if (selected) Modifier.background(
                    MaterialTheme.colorScheme.primaryContainer,
                    RoundedCornerShape(8.dp)
                ) else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(24.dp)) {
            val lineWidth = 3.dp.toPx()
            val half = size.minDimension / 2f
            val path = Path().apply {
                moveTo(half, 2.dp.toPx())
                lineTo(half, size.maxDimension - 2.dp.toPx())
            }
            drawPath(
                path = path,
                color = lineColor,
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

    val strokeWidth = strokeSizePx
    val strokeColor = when (selectedTool) {
        DrawTool.Pen -> selectedColor
        DrawTool.Pencil -> selectedColor.copy(alpha = PENCIL_ALPHA)
    }

    fun saveAllLayers() {
        if (layerStates.isNotEmpty()) {
            onSaveLayers(pageId, layerStates.map { it.bitmap }, backgroundColor)
        }
    }

    fun undo() {
        if (currentLayerIndex !in layerStates.indices) return
        val layer = layerStates[currentLayerIndex]
        if (layer.strokes.isEmpty()) return
        layer.strokes.removeAt(layer.strokes.lastIndex)
        layer.bitmap.eraseColor(android.graphics.Color.WHITE)
        layer.strokes.forEach { stroke -> drawStrokeOnBitmap(layer.bitmap, stroke) }
        saveAllLayers()
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
                title = { Text("Drawing") },
                actions = {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(horizontal = 0.dp)) {
                            SegmentedButton(
                                selected = selectedTool == DrawTool.Pen,
                                onClick = { selectedTool = DrawTool.Pen },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                label = { Text("Pen") }
                            )
                            SegmentedButton(
                                selected = selectedTool == DrawTool.Pencil,
                                onClick = { selectedTool = DrawTool.Pencil },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                label = { Text("Pencil") }
                            )
                        }
                        StrokeCapButton(
                            cap = StrokeCapStyle.ROUND,
                            selected = strokeCapStyle == StrokeCapStyle.ROUND,
                            onClick = {
                                strokeCapStyle = StrokeCapStyle.ROUND
                                onSaveStrokeCap(StrokeCapStyle.ROUND)
                            }
                        )
                        StrokeCapButton(
                            cap = StrokeCapStyle.BUTT,
                            selected = strokeCapStyle == StrokeCapStyle.BUTT,
                            onClick = {
                                strokeCapStyle = StrokeCapStyle.BUTT
                                onSaveStrokeCap(StrokeCapStyle.BUTT)
                            }
                        )
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(selectedColor, CircleShape)
                                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable {
                                    pendingColor = selectedColor
                                    showColorPickerModal = true
                                }
                        )
                        Text("Bg", modifier = Modifier.padding(horizontal = 4.dp))
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color(backgroundColor), CircleShape)
                                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable {
                                    pendingBgColor = Color(backgroundColor)
                                    showBgColorPickerModal = true
                                }
                        )
                        TextButton(onClick = { undo() }) { Text("Undo") }
                        TextButton(onClick = {
                            compositeLayers()?.let { bmp ->
                                exportBitmap = bmp
                                showExportDialog = true
                            }
                        }) { Text("Export") }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PreviewDot(strokeWidth = strokeWidth, strokeColor = strokeColor)
                Slider(
                    value = strokeSizePx,
                    onValueChange = {
                        strokeSizePx = it
                        onSaveStrokeSizePx(it)
                    },
                    valueRange = STROKE_SIZE_RANGE,
                    steps = 62,
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
                        detectDragGestures(
                            onDragStart = { currentStrokePoints = listOf(it) },
                            onDrag = { change, _ -> currentStrokePoints = currentStrokePoints + change.position },
                            onDragEnd = {
                                if (currentLayerIndex in layerStates.indices && currentStrokePoints.size > 1) {
                                    val                                     stroke = Stroke(
                                        points = currentStrokePoints,
                                        color = strokeColor,
                                        strokeWidth = strokeWidth,
                                        tool = selectedTool,
                                        strokeCapStyle = strokeCapStyle
                                    )
                                    val layer = layerStates[currentLayerIndex]
                                    layer.strokes.add(stroke)
                                    drawStrokeOnBitmap(layer.bitmap, stroke)
                                    saveAllLayers()
                                }
                                currentStrokePoints = emptyList()
                            }
                        )
                    }
            ) {
                if (layerStates.isNotEmpty()) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawRect(Color(backgroundColor))
                        layerStates.forEach { layer ->
                            drawImage(layer.bitmap.asImageBitmap(), topLeft = Offset.Zero)
                        }
                        if (currentStrokePoints.isNotEmpty()) {
                            val path = Path().apply {
                                currentStrokePoints.forEachIndexed { i, o ->
                                    if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
                                }
                            }
                            drawPath(
                                path = path,
                                color = strokeColor,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = strokeWidth,
                                    cap = if (strokeCapStyle == StrokeCapStyle.ROUND) StrokeCap.Round else StrokeCap.Butt
                                )
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f))
                    .horizontalScroll(rememberScrollState())
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                layerStates.forEachIndexed { index, _ ->
                    val selected = index == currentLayerIndex
                    TextButton(
                        onClick = { currentLayerIndex = index },
                        modifier = Modifier.background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                    ) {
                        Text("Layer ${index + 1}")
                    }
                    if (layerStates.size > 1) {
                        TextButton(onClick = { deleteLayer(index) }) { Text("×", style = MaterialTheme.typography.titleMedium) }
                    }
                }
                TextButton(onClick = { addLayer() }) { Text("+ Layer") }
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
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .background(MaterialTheme.colorScheme.surface, CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = strokeColor,
                radius = strokeWidth / 2f,
                center = center
            )
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
        strokeJoin = Paint.Join.ROUND
        strokeCap = if (stroke.strokeCapStyle == StrokeCapStyle.ROUND) Paint.Cap.ROUND else Paint.Cap.BUTT
    }
    val path = android.graphics.Path()
    stroke.points.forEachIndexed { index, offset ->
        if (index == 0) path.moveTo(offset.x, offset.y)
        else path.lineTo(offset.x, offset.y)
    }
    canvas.drawPath(path, paint)
}

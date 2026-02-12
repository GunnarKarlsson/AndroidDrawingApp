package com.example.drawingapp.ui.drawing

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.drawingapp.data.DrawTool
import com.example.drawingapp.data.Stroke

private const val PENCIL_ALPHA = 0.75f
private const val DEFAULT_STROKE_SIZE_PX = 4f
private val STROKE_SIZE_RANGE = 1f..24f
private val DEFAULT_COLOR = Color.Black

private val COLOR_PALETTE = listOf(
    Color.Black,
    Color(0xFFE53935),
    Color(0xFF43A047),
    Color(0xFF1E88E5),
    Color(0xFFFB8C00),
    Color(0xFF8E24AA)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawingScreen(
    pageId: String,
    onLoadLayers: (String) -> List<Bitmap?>,
    onSaveLayers: (String, List<Bitmap>, Int) -> Unit,
    onLoadBackgroundColor: (String) -> Int,
    onSaveBackgroundColor: (String, Int) -> Unit,
    onExport: (Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    val layerStates = remember(pageId) { mutableStateListOf<LayerState>() }
    var currentLayerIndex by remember(pageId) { mutableStateOf(0) }
    var currentStrokePoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var selectedTool by remember { mutableStateOf(DrawTool.Pen) }
    var selectedColor by remember { mutableStateOf(DEFAULT_COLOR) }
    var strokeSizePx by remember { mutableStateOf(DEFAULT_STROKE_SIZE_PX) }
    var backgroundColor by remember(pageId) { mutableStateOf(0xFFFFFFFF.toInt()) }
    var initialized by remember(pageId) { mutableStateOf(false) }

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
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(horizontal = 8.dp)) {
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
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        COLOR_PALETTE.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(color, CircleShape)
                                    .border(
                                        width = if (color == selectedColor) 2.dp else 0.dp,
                                        color = MaterialTheme.colorScheme.outline,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColor = color }
                            )
                        }
                    }
                    Text("Bg:", modifier = Modifier.padding(horizontal = 4.dp))
                    COLOR_PALETTE.forEach { color ->
                        val isBg = color.toArgb() == backgroundColor
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(color, CircleShape)
                                .border(
                                    width = if (isBg) 2.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                                .clickable {
                                    backgroundColor = color.toArgb()
                                    onSaveBackgroundColor(pageId, color.toArgb())
                                }
                        )
                    }
                    TextButton(onClick = { undo() }) { Text("Undo") }
                    TextButton(onClick = { compositeLayers()?.let { onExport(it) } }) { Text("Export") }
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
                    onValueChange = { strokeSizePx = it },
                    valueRange = STROKE_SIZE_RANGE,
                    steps = 22,
                    modifier = Modifier.widthIn(min = 80.dp, max = 160.dp)
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
                                    val stroke = Stroke(
                                        points = currentStrokePoints,
                                        color = strokeColor,
                                        strokeWidth = strokeWidth,
                                        tool = selectedTool
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
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
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
        strokeCap = Paint.Cap.ROUND
    }
    val path = android.graphics.Path()
    stroke.points.forEachIndexed { index, offset ->
        if (index == 0) path.moveTo(offset.x, offset.y)
        else path.lineTo(offset.x, offset.y)
    }
    canvas.drawPath(path, paint)
}

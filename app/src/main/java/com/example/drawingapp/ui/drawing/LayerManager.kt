package com.example.drawingapp.ui.drawing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.IntSize
import com.example.drawingapp.data.LayerMeta

/**
 * Owns the list of layers and current layer index. Exposes addLayer, deleteLayer,
 * compositeLayers, loadLayers, and layer visibility. Used by DrawingScreen as the
 * single source of truth for layer state.
 */
class LayerManager {

    private val layers = mutableStateListOf<LayerState>()
    private val _currentLayerIndex = mutableStateOf(0)
    private var canvasSize: IntSize = IntSize.Zero

    /** Observable list of layers; mutations (add/remove/replace) trigger recomposition. */
    fun getLayers(): List<LayerState> = layers

    var currentLayerIndex: Int
        get() = _currentLayerIndex.value
        set(value) {
            _currentLayerIndex.value = value
        }

    /**
     * Load layers from persisted bitmaps and metas, or create one empty layer.
     * Sets canvas size and current layer index. Call once when canvas size is first known.
     */
    fun loadLayers(
        size: IntSize,
        loadedBitmaps: List<Bitmap?>,
        layerMetas: List<LayerMeta>?,
        initialCurrentLayerIndex: Int
    ) {
        canvasSize = size
        layers.clear()
        if (loadedBitmaps.isEmpty()) {
            val bmp = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(android.graphics.Color.TRANSPARENT)
            layers.add(LayerState(bitmap = bmp, hasFill = true))
        } else {
            loadedBitmaps.forEachIndexed { index, lb ->
                val bmp = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.TRANSPARENT)
                lb?.let {
                    Canvas(bmp).drawBitmap(
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
                layers.add(
                    LayerState(
                        bitmap = bmp,
                        strokes = strokes,
                        hasFill = hasFill,
                        isHidden = isHidden
                    )
                )
            }
        }
        currentLayerIndex = initialCurrentLayerIndex.coerceIn(0, (layers.size - 1).coerceAtLeast(0))
    }

    fun addLayer() {
        if (canvasSize.width <= 0 || canvasSize.height <= 0) return
        val newBitmap = Bitmap.createBitmap(canvasSize.width, canvasSize.height, Bitmap.Config.ARGB_8888)
        newBitmap.eraseColor(android.graphics.Color.TRANSPARENT)
        layers.add(LayerState(bitmap = newBitmap, hasFill = true))
        currentLayerIndex = layers.lastIndex
    }

    fun deleteLayer(index: Int): Boolean {
        if (layers.size <= 1) return false
        layers.removeAt(index)
        if (currentLayerIndex >= layers.size) currentLayerIndex = layers.lastIndex
        else if (currentLayerIndex > index) currentLayerIndex--
        return true
    }

    fun compositeLayers(backgroundColor: Int): Bitmap? {
        if (layers.isEmpty() || canvasSize.width <= 0 || canvasSize.height <= 0) return null
        val out = Bitmap.createBitmap(canvasSize.width, canvasSize.height, Bitmap.Config.ARGB_8888)
        out.eraseColor(backgroundColor)
        val canvas = Canvas(out)
        layers.forEach { layer ->
            if (!layer.isTransparent() && !layer.isHidden) {
                canvas.drawBitmap(layer.bitmap, 0f, 0f, null)
            }
        }
        return out
    }

    fun toggleLayerHidden(index: Int) {
        if (index !in layers.indices) return
        val layer = layers[index]
        layers[index] = layer.copy(isHidden = !layer.isHidden)
    }
}

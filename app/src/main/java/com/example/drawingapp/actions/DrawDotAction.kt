package com.example.drawingapp.actions

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.Color
import com.example.drawingapp.data.StrokeCapStyle
import com.example.drawingapp.rendering.Renderer
import com.example.drawingapp.ui.drawing.LayerState

/**
 * Command that draws a single dot (tap without drag). Undo restores the bitmap before the dot.
 */
class DrawDotAction(
    override val layerIndex: Int,
    val x: Float,
    val y: Float,
    val dotRadiusBitmap: Float,
    val color: Color,
    val isEraser: Boolean,
    val strokeCapStyle: StrokeCapStyle
) : DrawingAction {

    override fun execute(layer: LayerState, renderer: Renderer): DrawingActionResult {
        val previousBitmap = Bitmap.createBitmap(layer.bitmap.width, layer.bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(previousBitmap).drawBitmap(layer.bitmap, 0f, 0f, null)
        renderer.render(layer.bitmap, this)
        return DrawingActionResult.BitmapChanged(previousBitmap)
    }

    override fun undo(layer: LayerState, previousState: DrawingActionResult, renderer: Renderer) {
        when (previousState) {
            is DrawingActionResult.BitmapChanged -> {
                layer.bitmap = previousState.previousBitmap
            }
            is DrawingActionResult.StrokeAdded,
            is DrawingActionResult.NoOp -> { }
        }
    }
}

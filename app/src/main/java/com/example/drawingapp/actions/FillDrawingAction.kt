package com.example.drawingapp.actions

import android.graphics.Bitmap
import android.graphics.Canvas
import com.example.drawingapp.rendering.Renderer
import com.example.drawingapp.ui.drawing.LayerState

/**
 * Command that flood-fills at (x,y). Undo restores the bitmap before fill.
 */
class FillDrawingAction(
    override val layerIndex: Int,
    val x: Float,
    val y: Float,
    val fillColorArgb: Int,
    val tolerance: Float
) : DrawingAction {

    override fun execute(layer: LayerState, renderer: Renderer): DrawingActionResult {
        val w = layer.bitmap.width
        val h = layer.bitmap.height
        val previousBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(previousBitmap).drawBitmap(layer.bitmap, 0f, 0f, null)
        renderer.render(layer.bitmap, this)
        layer.hasFill = true
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

package com.example.drawingapp.actions

import android.graphics.Bitmap
import android.graphics.Canvas
import com.example.drawingapp.ui.drawing.LayerState
import com.example.drawingapp.util.ColorUtil

/**
 * Command that flood-fills at (x,y). Undo restores the bitmap before fill.
 */
class FillDrawingAction(
    override val layerIndex: Int,
    private val x: Float,
    private val y: Float,
    private val fillColorArgb: Int,
    private val tolerance: Float
) : DrawingAction {

    override fun execute(layer: LayerState): DrawingActionResult {
        val w = layer.bitmap.width
        val h = layer.bitmap.height
        val previousBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(previousBitmap).drawBitmap(layer.bitmap, 0f, 0f, null)

        val newBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(newBitmap).drawBitmap(layer.bitmap, 0f, 0f, null)
        ColorUtil.floodFill(newBitmap, x.toInt(), y.toInt(), fillColorArgb, tolerance)

        layer.bitmap = newBitmap
        layer.hasFill = true
        return DrawingActionResult.BitmapChanged(previousBitmap)
    }

    override fun undo(layer: LayerState, previousState: DrawingActionResult) {
        when (previousState) {
            is DrawingActionResult.BitmapChanged -> {
                layer.bitmap = previousState.previousBitmap
            }
            is DrawingActionResult.StrokeAdded -> { }
        }
    }
}

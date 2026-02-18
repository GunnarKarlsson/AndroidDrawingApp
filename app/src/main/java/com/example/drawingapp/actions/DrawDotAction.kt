package com.example.drawingapp.actions

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.drawingapp.data.StrokeCapStyle
import com.example.drawingapp.ui.drawing.LayerState

/**
 * Command that draws a single dot (tap without drag). Undo restores the bitmap before the dot.
 */
class DrawDotAction(
    override val layerIndex: Int,
    private val x: Float,
    private val y: Float,
    private val dotRadiusBitmap: Float,
    private val color: Color,
    private val isEraser: Boolean,
    private val strokeCapStyle: StrokeCapStyle
) : DrawingAction {

    override fun execute(layer: LayerState): DrawingActionResult {
        val previousBitmap = Bitmap.createBitmap(layer.bitmap.width, layer.bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(previousBitmap).drawBitmap(layer.bitmap, 0f, 0f, null)

        val colorArgb = color.toArgb()
        val canvas = Canvas(layer.bitmap)
        val paint = Paint().apply {
            isAntiAlias = true
            color = colorArgb
            style = Paint.Style.FILL
            if (isEraser) {
                xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
            }
        }
        if (strokeCapStyle == StrokeCapStyle.ROUND) {
            canvas.drawCircle(x, y, dotRadiusBitmap, paint)
        } else {
            canvas.drawRect(
                x - dotRadiusBitmap,
                y - dotRadiusBitmap,
                x + dotRadiusBitmap,
                y + dotRadiusBitmap,
                paint
            )
        }
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

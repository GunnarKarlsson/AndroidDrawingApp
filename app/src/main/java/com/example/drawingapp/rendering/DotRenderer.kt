package com.example.drawingapp.rendering

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.toArgb
import com.example.drawingapp.actions.DrawDotAction
import com.example.drawingapp.actions.DrawingAction
import com.example.drawingapp.data.StrokeCapStyle

/**
 * Renders a single dot (tap) onto a bitmap. Handles [DrawDotAction].
 */
object DotRenderer : Renderer {

    override fun render(bitmap: Bitmap, action: DrawingAction): Bitmap? {
        if (action !is DrawDotAction) return null
        val canvas = Canvas(bitmap)
        val colorArgb = action.color.toArgb()
        val paint = Paint().apply {
            isAntiAlias = true
            color = colorArgb
            style = Paint.Style.FILL
            if (action.isEraser) {
                xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
            }
        }
        if (action.strokeCapStyle == StrokeCapStyle.ROUND) {
            canvas.drawCircle(action.x, action.y, action.dotRadiusBitmap, paint)
        } else {
            canvas.drawRect(
                action.x - action.dotRadiusBitmap,
                action.y - action.dotRadiusBitmap,
                action.x + action.dotRadiusBitmap,
                action.y + action.dotRadiusBitmap,
                paint
            )
        }
        return null
    }
}

package com.example.drawingapp.rendering

import android.graphics.Bitmap
import android.graphics.Canvas
import com.example.drawingapp.actions.DrawingAction
import com.example.drawingapp.actions.FillDrawingAction
import com.example.drawingapp.util.ColorUtil

/**
 * Renders a flood-fill into the given bitmap in place (same behavior as stroke/dot).
 * Uses a temp buffer for the fill algorithm, then draws the result back onto the input bitmap.
 */
object FillRenderer : Renderer {

    override fun render(bitmap: Bitmap, action: DrawingAction): Bitmap? {
        if (action !is FillDrawingAction) return null
        val w = bitmap.width
        val h = bitmap.height
        val temp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(temp).drawBitmap(bitmap, 0f, 0f, null)
        ColorUtil.floodFill(
            temp,
            action.x.toInt(),
            action.y.toInt(),
            action.fillColorArgb,
            action.tolerance
        )
        Canvas(bitmap).drawBitmap(temp, 0f, 0f, null)
        if (!temp.isRecycled) temp.recycle()
        return null
    }
}

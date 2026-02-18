package com.example.drawingapp.rendering

import android.graphics.Bitmap
import android.graphics.Canvas
import com.example.drawingapp.actions.DrawingAction
import com.example.drawingapp.actions.FillDrawingAction
import com.example.drawingapp.util.ColorUtil

/**
 * Renders a flood-fill onto a copy of the bitmap. Handles [FillDrawingAction].
 * Returns the new bitmap so the action can set layer.bitmap = result.
 */
object FillRenderer : Renderer {

    override fun render(bitmap: Bitmap, action: DrawingAction): Bitmap? {
        if (action !is FillDrawingAction) return null
        val w = bitmap.width
        val h = bitmap.height
        val newBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(newBitmap).drawBitmap(bitmap, 0f, 0f, null)
        ColorUtil.floodFill(
            newBitmap,
            action.x.toInt(),
            action.y.toInt(),
            action.fillColorArgb,
            action.tolerance
        )
        return newBitmap
    }
}

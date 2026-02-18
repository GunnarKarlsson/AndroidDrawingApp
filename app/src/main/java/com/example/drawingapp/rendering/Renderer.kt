package com.example.drawingapp.rendering

import android.graphics.Bitmap
import com.example.drawingapp.actions.DrawingAction

/**
 * Single entry point for all bitmap drawing. Implementations draw the given action onto the bitmap.
 *
 * @return null if the bitmap was modified in place (stroke, dot).
 *         A new [Bitmap] when the result must replace the layer bitmap (e.g. fill);
 *         the caller must set layer.bitmap = result.
 */
interface Renderer {
    fun render(bitmap: Bitmap, action: DrawingAction): Bitmap?
}

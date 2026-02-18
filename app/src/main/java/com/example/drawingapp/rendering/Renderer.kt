package com.example.drawingapp.rendering

import android.graphics.Bitmap
import com.example.drawingapp.actions.DrawingAction

/**
 * Single entry point for all bitmap drawing. Implementations draw the given action onto the bitmap.
 *
 * @return null if the bitmap was modified in place (stroke, dot, fill).
 *         A new [Bitmap] only when the caller must replace the layer bitmap (currently unused).
 */
interface Renderer {
    fun render(bitmap: Bitmap, action: DrawingAction): Bitmap?
}

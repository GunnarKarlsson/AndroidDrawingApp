package com.example.drawingapp.rendering

import android.graphics.Bitmap
import com.example.drawingapp.actions.DrawDotAction
import com.example.drawingapp.actions.DrawingAction
import com.example.drawingapp.actions.FillDrawingAction
import com.example.drawingapp.actions.RedrawStrokesAction
import com.example.drawingapp.actions.StrokeDrawingAction
import com.example.drawingapp.util.StrokeRenderer

/**
 * Dispatches [Renderer.render] to the appropriate implementation by action type.
 * This is the single [Renderer] instance used by the app.
 */
class CompositeRenderer : Renderer {

    override fun render(bitmap: Bitmap, action: DrawingAction): Bitmap? {
        return when (action) {
            is StrokeDrawingAction,
            is RedrawStrokesAction -> StrokeRenderer.render(bitmap, action)
            is DrawDotAction -> DotRenderer.render(bitmap, action)
            is FillDrawingAction -> FillRenderer.render(bitmap, action)
            else -> null
        }
    }
}

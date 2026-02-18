package com.example.drawingapp.actions

import android.graphics.Bitmap
import com.example.drawingapp.data.Stroke

/**
 * Result of executing a DrawingAction; passed to undo() to restore state.
 */
sealed class DrawingActionResult {
    data class StrokeAdded(val stroke: Stroke) : DrawingActionResult()
    data class BitmapChanged(val previousBitmap: Bitmap) : DrawingActionResult()
    /** Used when the action only triggers rendering (e.g. RedrawStrokesAction); not stored on undo stack. */
    data object NoOp : DrawingActionResult()
}

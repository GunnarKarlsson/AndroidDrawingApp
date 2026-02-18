package com.example.drawingapp.tools

import androidx.compose.ui.graphics.Color

/**
 * What the tool wants to do on tap. Returned by DrawingTool.handleTap.
 * The screen executes the corresponding bitmap/UI logic.
 */
sealed class TapIntent {
    /** Sample color from composite and switch to Pen. */
    data object Eyedropper : TapIntent()

    /** Flood fill at (x, y). */
    data class Fill(val x: Float, val y: Float) : TapIntent()

    /** Draw a single dot (tap-without-drag). */
    data class DrawDot(
        val x: Float,
        val y: Float,
        val dotRadiusBitmap: Float,
        val color: Color,
        val isEraser: Boolean
    ) : TapIntent()
}

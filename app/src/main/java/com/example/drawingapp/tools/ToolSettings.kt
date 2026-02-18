package com.example.drawingapp.tools

import androidx.compose.ui.graphics.Color
import com.example.drawingapp.data.StrokeCapStyle

/**
 * Current tool settings (color, size, cap, smoothing/closing).
 * Built in the screen from UI state when calling tool methods.
 */
data class ToolSettings(
    val color: Color,
    val strokeWidth: Float,
    val strokeCapStyle: StrokeCapStyle,
    val smoothingEnabled: Boolean,
    val closingEnabled: Boolean
)

object ToolConstants {
    const val CLOSE_THRESHOLD_PX = 50f
}

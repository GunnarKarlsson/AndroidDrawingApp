package com.example.drawingapp.data

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/** Line ending style: rounded (default) or square (butt). */
enum class StrokeCapStyle {
    ROUND,
    BUTT
}

data class Stroke(
    val points: List<Offset>,
    val color: androidx.compose.ui.graphics.Color,
    val strokeWidth: Float,
    val tool: DrawTool = DrawTool.Pen,
    val strokeCapStyle: StrokeCapStyle = StrokeCapStyle.ROUND,
    val closed: Boolean = false
)

enum class DrawTool {
    Pen,
    Pencil,
    MarkerPen,
    Eraser,
    Fill,
    Eyedropper,
    Pan,
    OilPaint
}

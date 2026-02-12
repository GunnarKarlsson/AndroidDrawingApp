package com.example.drawingapp.data

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

data class Stroke(
    val points: List<Offset>,
    val color: androidx.compose.ui.graphics.Color,
    val strokeWidth: Float,
    val tool: DrawTool = DrawTool.Pen
)

enum class DrawTool {
    Pen,
    Pencil
}

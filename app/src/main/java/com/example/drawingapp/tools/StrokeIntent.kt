package com.example.drawingapp.tools

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.drawingapp.data.DrawTool
import com.example.drawingapp.data.StrokeCapStyle

/**
 * Describes a stroke to apply. Returned by DrawingTool.createAction.
 * The screen creates Stroke from this and calls drawStrokeOnBitmap.
 */
data class StrokeIntent(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float,
    val strokeCapStyle: StrokeCapStyle,
    val closed: Boolean,
    val drawTool: DrawTool
)

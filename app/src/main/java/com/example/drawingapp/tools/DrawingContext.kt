package com.example.drawingapp.tools

import androidx.compose.ui.unit.IntSize

/**
 * Context passed to tool methods: current layer, canvas size, and view scale.
 */
data class DrawingContext(
    val currentLayerIndex: Int,
    val canvasSize: IntSize,
    val scale: Float
)

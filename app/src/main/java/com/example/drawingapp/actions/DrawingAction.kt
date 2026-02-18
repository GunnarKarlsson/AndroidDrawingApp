package com.example.drawingapp.actions

import com.example.drawingapp.rendering.Renderer
import com.example.drawingapp.ui.drawing.LayerState

/**
 * Command for a single drawing operation. Execute applies the change;
 * undo restores the layer using the captured result.
 * All bitmap drawing is performed via [Renderer]; actions do not use Canvas/Paint directly.
 */
interface DrawingAction {
    val layerIndex: Int

    fun execute(layer: LayerState, renderer: Renderer): DrawingActionResult
    fun undo(layer: LayerState, previousState: DrawingActionResult, renderer: Renderer)
}

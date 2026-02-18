package com.example.drawingapp.actions

import com.example.drawingapp.ui.drawing.LayerState

/**
 * Command for a single drawing operation. Execute applies the change;
 * undo restores the layer using the captured result.
 */
interface DrawingAction {
    val layerIndex: Int

    fun execute(layer: LayerState): DrawingActionResult
    fun undo(layer: LayerState, previousState: DrawingActionResult)
}

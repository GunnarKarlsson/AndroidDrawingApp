package com.example.drawingapp.actions

import com.example.drawingapp.data.Stroke
import com.example.drawingapp.rendering.Renderer
import com.example.drawingapp.ui.drawing.LayerState

/**
 * Internal action used to redraw all strokes onto a layer bitmap (e.g. when undoing a stroke).
 * Not pushed onto the undo stack; only passed to [Renderer.render] to perform the redraw.
 */
data class RedrawStrokesAction(
    override val layerIndex: Int,
    val strokes: List<Stroke>
) : DrawingAction {

    override fun execute(layer: LayerState, renderer: Renderer): DrawingActionResult {
        renderer.render(layer.bitmap, this)
        return DrawingActionResult.NoOp
    }

    override fun undo(layer: LayerState, previousState: DrawingActionResult, renderer: Renderer) {
        // Never called; this action is not on the undo stack.
    }
}

package com.example.drawingapp.actions

import com.example.drawingapp.rendering.Renderer
import com.example.drawingapp.ui.drawing.LayerState

/**
 * Holds undo/redo stacks and executes or reverts DrawingActions.
 * Does not hold layers; receives them on each call. All bitmap drawing is delegated to [renderer].
 */
class DrawingEngine(private val renderer: Renderer) {

    private val undoStack = mutableListOf<Pair<DrawingAction, DrawingActionResult>>()
    private val redoStack = mutableListOf<Pair<DrawingAction, DrawingActionResult>>()

    fun executeAction(action: DrawingAction, layers: List<LayerState>) {
        val layer = layers.getOrNull(action.layerIndex) ?: return
        val result = action.execute(layer, renderer)
        undoStack.add(action to result)
        redoStack.clear()
    }

    fun undo(layers: List<LayerState>) {
        if (undoStack.isEmpty()) return
        val (action, result) = undoStack.removeAt(undoStack.lastIndex)
        val layer = layers.getOrNull(action.layerIndex) ?: return
        action.undo(layer, result, renderer)
        redoStack.add(action to result)
    }

    fun redo(layers: List<LayerState>) {
        if (redoStack.isEmpty()) return
        val (action, _) = redoStack.removeAt(redoStack.lastIndex)
        executeAction(action, layers)
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()
}

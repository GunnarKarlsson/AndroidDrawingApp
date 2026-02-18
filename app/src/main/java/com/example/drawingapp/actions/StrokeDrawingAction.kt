package com.example.drawingapp.actions

import com.example.drawingapp.data.Stroke
import com.example.drawingapp.tools.StrokeIntent
import com.example.drawingapp.ui.drawing.LayerState
import com.example.drawingapp.util.StrokeRenderer

/**
 * Command that adds a stroke to a layer. Undo removes the stroke and redraws the rest.
 */
class StrokeDrawingAction(
    override val layerIndex: Int,
    private val intent: StrokeIntent
) : DrawingAction {

    override fun execute(layer: LayerState): DrawingActionResult {
        val stroke = Stroke(
            points = intent.points,
            color = intent.color,
            strokeWidth = intent.strokeWidth,
            tool = intent.drawTool,
            strokeCapStyle = intent.strokeCapStyle,
            closed = intent.closed
        )
        StrokeRenderer.render(layer.bitmap, stroke)
        layer.strokes.add(stroke)
        return DrawingActionResult.StrokeAdded(stroke)
    }

    override fun undo(layer: LayerState, previousState: DrawingActionResult) {
        when (previousState) {
            is DrawingActionResult.StrokeAdded -> {
                layer.strokes.remove(previousState.stroke)
                layer.bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
                layer.strokes.forEach { stroke ->
                    StrokeRenderer.render(layer.bitmap, stroke)
                }
            }
            is DrawingActionResult.BitmapChanged -> { }
        }
    }
}

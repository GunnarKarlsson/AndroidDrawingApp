package com.example.drawingapp.actions

import com.example.drawingapp.data.Stroke
import com.example.drawingapp.rendering.Renderer
import com.example.drawingapp.tools.StrokeIntent
import com.example.drawingapp.ui.drawing.LayerState

/**
 * Command that adds a stroke to a layer. Undo removes the stroke and redraws the rest.
 */
class StrokeDrawingAction(
    override val layerIndex: Int,
    private val intent: StrokeIntent
) : DrawingAction {

    /** Builds the stroke from the intent; used by StrokeRenderer to draw. */
    fun buildStroke(): Stroke = Stroke(
        points = intent.points,
        color = intent.color,
        strokeWidth = intent.strokeWidth,
        tool = intent.drawTool,
        strokeCapStyle = intent.strokeCapStyle,
        closed = intent.closed
    )

    override fun execute(layer: LayerState, renderer: Renderer): DrawingActionResult {
        val stroke = buildStroke()
        renderer.render(layer.bitmap, this)
        layer.strokes.add(stroke)
        return DrawingActionResult.StrokeAdded(stroke)
    }

    override fun undo(layer: LayerState, previousState: DrawingActionResult, renderer: Renderer) {
        when (previousState) {
            is DrawingActionResult.StrokeAdded -> {
                layer.strokes.remove(previousState.stroke)
                renderer.render(layer.bitmap, RedrawStrokesAction(layerIndex, layer.strokes))
            }
            is DrawingActionResult.BitmapChanged,
            is DrawingActionResult.NoOp -> { }
        }
    }
}

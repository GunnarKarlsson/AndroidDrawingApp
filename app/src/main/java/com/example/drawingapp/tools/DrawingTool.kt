package com.example.drawingapp.tools

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.drawingapp.data.DrawTool

/**
 * Strategy for a drawing tool. Encapsulates color transform, stroke/tap intents,
 * and view behavior (pan, dot preview, eraser preview).
 */
interface DrawingTool {
    val id: String
    val displayName: String
    val iconRes: Int
    /** Used for Stroke.tool and persistence (LayerMeta/StrokeData). */
    val drawTool: DrawTool

    fun transformColor(baseColor: Color, settings: ToolSettings): Color

    /**
     * Build a stroke descriptor from touch points, or null for non-stroke tools.
     */
    fun createAction(
        points: List<Offset>,
        settings: ToolSettings,
        context: DrawingContext
    ): StrokeIntent?

    /**
     * Build tap intent (eyedropper, fill, draw dot), or null for no-op (e.g. Pan).
     */
    fun handleTap(
        x: Float,
        y: Float,
        settings: ToolSettings,
        context: DrawingContext
    ): TapIntent?

    /** True for Pen, Pencil, MarkerPen, Eraser (enables stroke preview and dot-on-tap). */
    fun supportsContinuousDrawing(): Boolean

    /** True for Pan (enables pan/zoom instead of draw). */
    fun affectsViewInteraction(): Boolean
}

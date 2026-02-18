package com.example.drawingapp.tools

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.drawingapp.R
import com.example.drawingapp.data.DrawTool

class EyedropperTool : DrawingTool {
    override val id: String = "eyedropper"
    override val displayName: String = "Eyedropper"
    override val iconRes: Int = R.drawable.ic_eyedropper
    override val drawTool: DrawTool = DrawTool.Eyedropper

    override fun transformColor(baseColor: Color, settings: ToolSettings): Color = baseColor

    override fun createAction(
        points: List<Offset>,
        settings: ToolSettings,
        context: DrawingContext
    ): StrokeIntent? = null

    override fun handleTap(
        x: Float,
        y: Float,
        settings: ToolSettings,
        context: DrawingContext
    ): TapIntent? = TapIntent.Eyedropper

    override fun supportsContinuousDrawing(): Boolean = false
    override fun affectsViewInteraction(): Boolean = false
}

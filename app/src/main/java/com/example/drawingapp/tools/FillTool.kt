package com.example.drawingapp.tools

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.drawingapp.R
import com.example.drawingapp.data.DrawTool

class FillTool : DrawingTool {
    override val id: String = "fill"
    override val displayName: String = "Fill"
    override val iconRes: Int = R.drawable.ic_fill
    override val drawTool: DrawTool = DrawTool.Fill

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
    ): TapIntent? = TapIntent.Fill(x = x, y = y)

    override fun supportsContinuousDrawing(): Boolean = false
    override fun affectsViewInteraction(): Boolean = false
}

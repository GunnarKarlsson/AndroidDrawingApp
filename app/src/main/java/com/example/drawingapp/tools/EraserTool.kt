package com.example.drawingapp.tools

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.drawingapp.R
import com.example.drawingapp.data.DrawTool
import com.example.drawingapp.util.shouldAutoClose
import com.example.drawingapp.util.smoothStrokePoints

class EraserTool : DrawingTool {
    override val id: String = "eraser"
    override val displayName: String = "Eraser"
    override val iconRes: Int = R.drawable.ic_eraser
    override val drawTool: DrawTool = DrawTool.Eraser

    override fun transformColor(baseColor: Color, settings: ToolSettings): Color = Color.Transparent

    override fun createAction(
        points: List<Offset>,
        settings: ToolSettings,
        context: DrawingContext
    ): StrokeIntent? {
        if (points.size < 2) return null
        val pointsToUse = if (settings.smoothingEnabled) smoothStrokePoints(points) else points
        val closed = settings.closingEnabled && shouldAutoClose(pointsToUse, ToolConstants.CLOSE_THRESHOLD_PX)
        return StrokeIntent(
            points = pointsToUse,
            color = Color.Transparent,
            strokeWidth = settings.strokeWidth / context.scale,
            strokeCapStyle = settings.strokeCapStyle,
            closed = closed,
            drawTool = drawTool
        )
    }

    override fun handleTap(
        x: Float,
        y: Float,
        settings: ToolSettings,
        context: DrawingContext
    ): TapIntent? {
        val dotRadiusBitmap = (settings.strokeWidth / context.scale / 2f).coerceAtLeast(1.5f)
        return TapIntent.DrawDot(
            x = x,
            y = y,
            dotRadiusBitmap = dotRadiusBitmap,
            color = Color.Transparent,
            isEraser = true
        )
    }

    override fun supportsContinuousDrawing(): Boolean = true
    override fun affectsViewInteraction(): Boolean = false
}

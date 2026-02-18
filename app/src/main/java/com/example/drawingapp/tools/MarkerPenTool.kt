package com.example.drawingapp.tools

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.drawingapp.R
import com.example.drawingapp.data.DrawTool
import com.example.drawingapp.util.shouldAutoClose
import com.example.drawingapp.util.smoothStrokePoints

private const val MARKER_ALPHA = 0.6f

class MarkerPenTool : DrawingTool {
    override val id: String = "markerpen"
    override val displayName: String = "Marker Pen"
    override val iconRes: Int = R.drawable.ic_marker
    override val drawTool: DrawTool = DrawTool.MarkerPen

    override fun transformColor(baseColor: Color, settings: ToolSettings): Color =
        baseColor.copy(alpha = MARKER_ALPHA)

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
            color = transformColor(settings.color, settings),
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
            color = transformColor(settings.color, settings),
            isEraser = false
        )
    }

    override fun supportsContinuousDrawing(): Boolean = true
    override fun affectsViewInteraction(): Boolean = false
}

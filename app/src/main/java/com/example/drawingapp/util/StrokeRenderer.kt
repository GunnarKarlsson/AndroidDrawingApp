package com.example.drawingapp.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.toArgb
import com.example.drawingapp.data.DrawTool
import com.example.drawingapp.data.Stroke
import com.example.drawingapp.data.StrokeCapStyle
import kotlin.math.hypot

private const val MIN_STROKE_SEGMENT_PX = 0.5f

/**
 * Renders a single Stroke onto a Bitmap. Used by StrokeDrawingAction and for
 * redrawing the layer bitmap when undoing a stroke.
 */
object StrokeRenderer {

    fun render(bitmap: Bitmap?, stroke: Stroke) {
        val bmp = bitmap ?: return
        val canvas = Canvas(bmp)
        val paint = Paint().apply {
            color = stroke.color.toArgb()
            style = Paint.Style.STROKE
            strokeWidth = stroke.strokeWidth
            isAntiAlias = true
            strokeJoin = if (stroke.strokeCapStyle == StrokeCapStyle.ROUND) Paint.Join.ROUND else Paint.Join.BEVEL
            strokeCap = if (stroke.strokeCapStyle == StrokeCapStyle.ROUND) Paint.Cap.ROUND else Paint.Cap.BUTT
            if (stroke.tool == DrawTool.Eraser) {
                xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
            }
        }
        val path = Path()
        var lastX = 0f
        var lastY = 0f
        stroke.points.forEachIndexed { index, offset ->
            if (index == 0) {
                path.moveTo(offset.x, offset.y)
                lastX = offset.x
                lastY = offset.y
            } else {
                val dx = offset.x - lastX
                val dy = offset.y - lastY
                if (hypot(dx, dy) >= MIN_STROKE_SEGMENT_PX) {
                    path.lineTo(offset.x, offset.y)
                    lastX = offset.x
                    lastY = offset.y
                }
            }
        }
        if (stroke.closed && stroke.points.isNotEmpty()) {
            val start = stroke.points.first()
            if (stroke.strokeCapStyle == StrokeCapStyle.BUTT || stroke.points.size < 3) {
                path.lineTo(start.x, start.y)
            } else {
                val end = stroke.points.last()
                val prev = stroke.points[stroke.points.size - 2]
                val controlX = end.x + (end.x - prev.x) * 0.75f
                val controlY = end.y + (end.y - prev.y) * 0.75f
                path.quadTo(controlX, controlY, start.x, start.y)
            }
            path.close()
        }
        canvas.drawPath(path, paint)
    }
}

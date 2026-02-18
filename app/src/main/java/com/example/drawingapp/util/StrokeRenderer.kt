package com.example.drawingapp.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.toArgb
import com.example.drawingapp.actions.DrawingAction
import com.example.drawingapp.actions.RedrawStrokesAction
import com.example.drawingapp.actions.StrokeDrawingAction
import com.example.drawingapp.data.DrawTool
import com.example.drawingapp.data.Stroke
import com.example.drawingapp.data.StrokeCapStyle
import com.example.drawingapp.rendering.Renderer
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.random.Random

private const val MIN_STROKE_SEGMENT_PX = 0.5f

/**
 * Renders strokes onto a bitmap. Implements [Renderer] for [StrokeDrawingAction]
 * and [RedrawStrokesAction] (undo redraw). All path/paint logic is in [renderStroke].
 */
object StrokeRenderer : Renderer {

    override fun render(bitmap: Bitmap, action: DrawingAction): Bitmap? {
        when (action) {
            is StrokeDrawingAction -> {
                val stroke = action.buildStroke()
                renderStroke(bitmap, stroke)
                return null
            }
            is RedrawStrokesAction -> {
                bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
                action.strokes.forEach { renderStroke(bitmap, it) }
                return null
            }
            else -> return null
        }
    }

    /**
     * Renders a single stroke onto the bitmap. Used for both drawing a new stroke
     * and redrawing the layer when undoing.
     */
    fun renderStroke(bitmap: Bitmap?, stroke: Stroke) {
        val bmp = bitmap ?: return
        if (!bmp.isMutable) return
        val canvas = Canvas(bmp)
        when (stroke.tool) {
            DrawTool.OilPaint -> renderOilPaintStroke(canvas, stroke, bmp)
            else -> renderNormalStroke(canvas, stroke)
        }
    }

    private fun renderNormalStroke(canvas: Canvas, stroke: Stroke) {
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

    private fun renderOilPaintStroke(canvas: Canvas, stroke: Stroke, targetBitmap: Bitmap) {
        if (stroke.points.size < 2) return

        val brushRadius = (stroke.strokeWidth / 2f).toInt().coerceAtLeast(4)
        val half = brushRadius
        val smearStrength = 0.5f
        val minCoverageToRefresh = 0.15f
        val decayLengthPx = 80f
        
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            style = Paint.Style.FILL
        }
        val tmpBufferSize = (brushRadius * 2 + 1).let { it * it }
        val tmpBuffer = IntArray(tmpBufferSize)
        val strokeColorArgb = stroke.color.toArgb()
        
        // Trailing state: carried color and strength
        var carriedColorArgb = strokeColorArgb
        var carryStrength = 0f
        var lastDabX = Float.NaN
        var lastDabY = Float.NaN

        stroke.points.windowed(size = 2, step = 1).forEach { (prev, curr) ->
            val dx = curr.x - prev.x
            val dy = curr.y - prev.y
            val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()

            if (dist < 1f) return@forEach

            val steps = max(1, (dist / (brushRadius * 0.7f)).toInt())

            for (i in 0..steps) {
                val t = i.toFloat() / steps
                val x = (prev.x + dx * t).toInt()
                val y = (prev.y + dy * t).toInt()

                val jitterX = x + Random.nextInt(-2, 3)
                val jitterY = y + Random.nextInt(-2, 3)

                val left = (jitterX - half).coerceIn(0, targetBitmap.width - 1)
                val top = (jitterY - half).coerceIn(0, targetBitmap.height - 1)
                val w = (brushRadius * 2 + 1).coerceAtMost(targetBitmap.width - left)
                val h = (brushRadius * 2 + 1).coerceAtMost(targetBitmap.height - top)

                targetBitmap.getPixels(tmpBuffer, 0, w, left, top, w, h)

                var r = 0L
                var g = 0L
                var b = 0L
                var count = 0
                val pixelCount = w * h
                for (i in 0 until pixelCount) {
                    val px = tmpBuffer[i]
                    if (android.graphics.Color.alpha(px) > 30) {
                        r += android.graphics.Color.red(px)
                        g += android.graphics.Color.green(px)
                        b += android.graphics.Color.blue(px)
                        count++
                    }
                }

                val picked = if (count > 0) {
                    android.graphics.Color.argb(
                        255,
                        (r / count).toInt().coerceIn(0, 255),
                        (g / count).toInt().coerceIn(0, 255),
                        (b / count).toInt().coerceIn(0, 255)
                    )
                } else {
                    strokeColorArgb
                }

                // Compute coverage (non-transparent pixel ratio)
                val coverage = if (pixelCount > 0) count.toFloat() / pixelCount else 0f
                
                // Refresh carried color if we're over painted pixels, otherwise decay
                if (coverage >= minCoverageToRefresh) {
                    carriedColorArgb = picked
                    carryStrength = 1.0f
                } else {
                    // Distance-based exponential decay
                    if (!lastDabX.isNaN() && !lastDabY.isNaN()) {
                        val dabDistPx = hypot(jitterX - lastDabX, jitterY - lastDabY)
                        carryStrength *= exp(-dabDistPx / decayLengthPx).toFloat()
                    } else {
                        carryStrength = 0f
                    }
                }
                
                // Blend picked color with carried color based on carryStrength
                val baseFromCanvas = blendArgb(picked, carriedColorArgb, carryStrength)
                
                // Then blend with stroke color (existing oil paint behavior)
                val finalArgb = blendArgb(baseFromCanvas, strokeColorArgb, smearStrength)
                paint.color = finalArgb
                paint.alpha = (180..240).random()

                canvas.drawCircle(jitterX.toFloat(), jitterY.toFloat(), brushRadius.toFloat(), paint)
                
                // Update last dab position for distance calculation
                lastDabX = jitterX.toFloat()
                lastDabY = jitterY.toFloat()
            }
        }
    }

    private fun blendArgb(c1: Int, c2: Int, ratioTowardC2: Float): Int {
        val ir = (1 - ratioTowardC2).coerceIn(0f, 1f)
        val r = (android.graphics.Color.red(c1) * ir + android.graphics.Color.red(c2) * ratioTowardC2).toInt()
        val g = (android.graphics.Color.green(c1) * ir + android.graphics.Color.green(c2) * ratioTowardC2).toInt()
        val b = (android.graphics.Color.blue(c1) * ir + android.graphics.Color.blue(c2) * ratioTowardC2).toInt()
        return android.graphics.Color.argb(255, r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }
}

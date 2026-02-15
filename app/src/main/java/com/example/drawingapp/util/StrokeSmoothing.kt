package com.example.drawingapp.util

import android.graphics.PointF
import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Smooths raw stroke points using moving average and Catmull-Rom spline interpolation.
 * Call when curve smoothing is enabled, after touch-up.
 *
 * @param rawPoints List of points collected from touch events
 * @param smoothingWindow Size for initial averaging (higher = smoother but less accurate)
 * @param curveTension Catmull-Rom tension (0.5f = standard Catmull-Rom)
 * @param segmentsPerSegment How many intermediate points per original segment
 * @return Smoothed list of points ready for drawing
 */
fun smoothStrokePoints(
    rawPoints: List<Offset>,
    smoothingWindow: Int = 5,
    curveTension: Float = 0.5f,
    segmentsPerSegment: Int = 8
): List<Offset> {
    if (rawPoints.size < 2) return rawPoints
    val points = rawPoints.map { PointF(it.x, it.y) }
    val filtered = averageSmoothing(points, smoothingWindow)
    val smoothed = catmullRomSpline(filtered, tension = curveTension, segmentsPerSegment = segmentsPerSegment)
    return smoothed.map { Offset(it.x, it.y) }
}

/** Approximate total length of the point sequence. */
fun pathLength(points: List<Offset>): Float {
    if (points.size < 2) return 0f
    var length = 0f
    for (i in 1 until points.size) {
        val dx = points[i].x - points[i - 1].x
        val dy = points[i].y - points[i - 1].y
        length += hypot(dx, dy)
    }
    return length
}

/**
 * Returns true if the stroke should be auto-closed: enough points, path long enough to avoid tiny loops,
 * and start/end within closeThresholdPx.
 */
fun shouldAutoClose(
    points: List<Offset>,
    closeThresholdPx: Float,
    minPathLengthPx: Float = 60f
): Boolean {
    if (points.size < 3) return false
    if (pathLength(points) <= minPathLengthPx) return false
    val start = points.first()
    val end = points.last()
    val dist = hypot(end.x - start.x, end.y - start.y)
    return dist <= closeThresholdPx
}

/**
 * Simple moving average to reduce noise/shakiness. Edge points are kept closer to original.
 */
private fun averageSmoothing(points: List<PointF>, window: Int): List<PointF> {
    if (window < 2 || points.size < window) return points.toList()
    val result = mutableListOf<PointF>()
    val half = window / 2
    for (i in points.indices) {
        var sumX = 0f
        var sumY = 0f
        var count = 0
        for (j in max(0, i - half)..min(points.lastIndex, i + half)) {
            sumX += points[j].x
            sumY += points[j].y
            count++
        }
        result.add(PointF(sumX / count, sumY / count))
    }
    return result
}

/**
 * Catmull-Rom spline interpolation for smooth curves. Produces more points than input.
 */
private fun catmullRomSpline(
    points: List<PointF>,
    tension: Float = 0.5f,
    segmentsPerSegment: Int = 8
): List<PointF> {
    if (points.size < 2) return points
    val result = mutableListOf<PointF>()
    val extended = mutableListOf<PointF>()
    extended.add(points.first())
    extended.addAll(points)
    extended.add(points.last())
    for (i in 1 until extended.lastIndex) {
        val p0 = extended[i - 1]
        val p1 = extended[i]
        val p2 = extended[i + 1]
        val p3 = extended[(i + 2).coerceAtMost(extended.lastIndex)]
        for (t in 0..segmentsPerSegment) {
            val tNorm = t.toFloat() / segmentsPerSegment
            val x = catmullRom(p0.x, p1.x, p2.x, p3.x, tNorm, tension)
            val y = catmullRom(p0.y, p1.y, p2.y, p3.y, tNorm, tension)
            result.add(PointF(x, y))
        }
    }
    return result
}

private fun catmullRom(p0: Float, p1: Float, p2: Float, p3: Float, t: Float, tension: Float): Float {
    val t2 = t * t
    val t3 = t2 * t
    return 0.5f * (
        (2 * p1) +
            (-p0 + p2) * t +
            (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 +
            (-p0 + 3 * p1 - 3 * p2 + p3) * t3 * tension
        )
}

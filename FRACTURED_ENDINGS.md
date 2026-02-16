Fix for Fractured Square Endings with Auto-Curve-ClosingThe specific issue you described—"fractured" endings for lines with square caps (BUTT) when auto-closing is enabled—stems from the closure logic in drawStrokeOnBitmap (in the truncated part of DrawingScreen.kt). Here's the breakdown:Root CauseWhen stroke.closed is true (set in onStrokeDrawn if shouldAutoClose returns true), the path adds a quadTo (quadratic Bézier curve) from the last point back to the first, using an extrapolated control point: controlX = end.x + (end.x - prev.x) * 1.5f (same for Y).This is intended to create a "smooth" closure, but the extrapolation factor of 1.5f often overshoots, creating a small loop or bulge at the joint.

With square caps (Paint.Cap.BUTT) and bevel joins (Paint.Join.BEVEL), the flat-cut ends don't blend well with this curved closure:BUTT caps cut perpendicular to the line direction without extension, so any curvature at the joint exposes misalignments.
BEVEL joins create sharp angles, which can look "fractured" (jagged or disconnected) if the quad curve doesn't perfectly align the directions at start/end.

If the stroke has few points or is nearly straight, the quadTo can make the closure look like a small hook or break, especially visible at thicker widths.
Note: This doesn't affect ROUND caps as much because ROUND adds semicircles that mask the joint.

Proposed FixSimplify Closure for Square Caps: Use a straight lineTo back to start instead of quadTo when using square caps. This avoids curvature artifacts while still closing the shape.
Adjust Control Point for Round Caps: Reduce the extrapolation factor (e.g., to 0.5f–1.0f) to prevent overshoot even for round caps.
Add Path.close(): After adding the closure segment, call path.close() to ensure the path is properly closed (though for stroking it's optional, it helps consistency).
Edge Case Handling: If points.size < 3, always use lineTo (your code already does for <2, but extend to avoid quad on short strokes).

Update drawStrokeOnBitmap like this (assuming it's in DrawingScreen.kt or a utils file):kotlin

private fun drawStrokeOnBitmap(bitmap: Bitmap, stroke: Stroke) {
    val canvas = android.graphics.Canvas(bitmap)
    val paint = Paint().apply {
        style = Paint.Style.STROKE
        isAntiAlias = true
        color = stroke.color.toArgb()
        strokeWidth = stroke.strokeWidth
        strokeCap = if (stroke.strokeCapStyle == StrokeCapStyle.ROUND) Paint.Cap.ROUND else Paint.Cap.BUTT
        strokeJoin = if (stroke.strokeCapStyle == StrokeCapStyle.ROUND) Paint.Join.ROUND else Paint.Join.BEVEL
        // Add alpha blending for tools like Pencil/Marker if needed (from stroke.tool)
        if (stroke.tool == DrawTool.Pencil) alpha = (255 * PENCIL_ALPHA).toInt()
        else if (stroke.tool == DrawTool.MarkerPen) alpha = (255 * 0.6f).toInt()
        // For eraser: PorterDuffXfermode(PorterDuff.Mode.CLEAR) if stroke.color == Transparent
        if (stroke.tool == DrawTool.Eraser) xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
    }
    val path = Path()
    if (stroke.points.isNotEmpty()) {
        path.moveTo(stroke.points[0].x, stroke.points[0].y)
        for (i in 1 until stroke.points.size) {
            path.lineTo(stroke.points[i].x, stroke.points[i].y)
        }
    }
    if (stroke.closed && stroke.points.isNotEmpty()) {
        val start = stroke.points.first()
        if (stroke.strokeCapStyle == StrokeCapStyle.BUTT || stroke.points.size < 3) {
            // Simple straight closure for square caps or short strokes
            path.lineTo(start.x, start.y)
        } else {
            // Smoother quad for round caps, with reduced extrapolation to avoid overshoot
            val end = stroke.points.last()
            val prev = stroke.points[stroke.points.size - 2]
            val controlX = end.x + (end.x - prev.x) * 0.75f  // Reduced from 1.5f
            val controlY = end.y + (end.y - prev.y) * 0.75f
            path.quadTo(controlX, controlY, start.x, start.y)
        }
        path.close()  // Ensure closed path
    }
    canvas.drawPath(path, paint)
}

Why this solves it: Straight line closure for BUTT ensures flat alignment without curves. Reduced factor prevents bulging for ROUND.
Testing Tip: Draw a near-closed shape with square caps and enable closing. Check at different thicknesses/zooms. If still fractured, try Join.MITER for square (it extends sharp corners better).

Also, ensure shouldAutoClose (in util.kt?) is accurate—e.g., it should check Euclidean distance < threshold.


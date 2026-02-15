1. Feature: Curve closing

when on, if the curve/line end is near the curve/line start, the curve is automatically closed into a closed curve.
if off, no such behavior.

Icon will be shows to the left of the curve-smooth icon.
On icon: cicle with dot on its line
Off icon: cirlce with gap

2. Implementation

Implementing **curve closing** as a separate, toggleable feature is straightforward. It works independently of smoothing: 

- If closing is **off** → just use the processed path as-is (open stroke).
- If closing is **on** → check if start and end points are close enough → if yes, connect them smoothly.

The connection can be:
- A straight `lineTo` (simple and fast, common in many apps).
- A smooth curve continuation (more natural for smoothed strokes).

Below are two clean ways to add this to your existing pipeline. Both assume you already have the `processStroke` function from before (or a similar raw → filtered → smoothed points flow).

### Option 1: Simple straight-line close (recommended for most cases)
This is what most drawing apps do when auto-closing.

Add a parameter `enableClosing: Boolean` and modify the final path building step.

```kotlin
fun processStroke(
    rawPoints: List<PointF>,
    smoothingWindow: Int = 5,
    curveTension: Float = 0.5f,
    taperLengthFraction: Float = 0.15f,
    maxWidth: Float = 12f,
    closeThresholdPx: Float = 20f,
    enableClosing: Boolean = false   // ← new toggle
): Path {
    if (rawPoints.size < 2) return Path()

    val filtered = averageSmoothing(rawPoints, smoothingWindow)
    val smoothedPoints = catmullRomSpline(filtered, tension = curveTension, segmentsPerSegment = 8)

    val path = Path()
    applyTaperedStroke(path, smoothedPoints, taperLengthFraction, maxWidth)

    // Optional auto-close – only if toggled on
    if (enableClosing && shouldAutoClose(smoothedPoints, closeThresholdPx)) {
        // Simple straight close (most common)
        path.lineTo(smoothedPoints.first().x, smoothedPoints.first().y)
        // Or use path.close() if you don't mind it forcing a straight line anyway
        // path.close()
    }

    return path
}

// Reuse your existing shouldAutoClose
private fun shouldAutoClose(points: List<PointF>, threshold: Float): Boolean {
    if (points.size < 3) return false
    val start = points.first()
    val end = points.last()
    val dist = hypot(end.x - start.x, end.y - start.y)
    return dist <= threshold
}
```

### Option 2: Smoother curved close (better visual match when smoothing is on)
Instead of a sharp straight line, continue the spline curve toward the start point. This looks more natural for freehand closed shapes (circles, ovals, etc.).

Replace the closing part with this logic:

```kotlin
if (enableClosing && shouldAutoClose(smoothedPoints, closeThresholdPx)) {
    val start = smoothedPoints.first()
    val end = smoothedPoints.last()

    // Use the last few points to estimate tangent direction at end
    val tangentPoints = if (smoothedPoints.size >= 4) smoothedPoints.takeLast(4) else smoothedPoints

    // Approximate a cubic or quadratic continuation toward start
    // Simple approach: quadratic Bézier using last point + control ≈ direction + start
    val prev = tangentPoints[tangentPoints.size - 2]
    val controlX = end.x + (end.x - prev.x) * 1.5f   // extrapolate direction
    val controlY = end.y + (end.y - prev.y) * 1.5f

    path.quadTo(controlX, controlY, start.x, start.y)
    // Alternative: cubic Bezier (needs two controls)
    // val control1X = end.x + (end.x - prev.x) * 0.8f
    // val control1Y = end.y + (end.y - prev.y) * 0.8f
    // val control2X = start.x + (start.x - smoothedPoints[1].x) * 0.3f
    // val control2Y = start.y + (start.y - smoothedPoints[1].y) * 0.3f
    // path.cubicTo(control1X, control1Y, control2X, control2Y, start.x, start.y)
}
```

### Full updated usage example (in your View)

```kotlin
class DrawingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var closingEnabled = true  // ← user toggle (e.g. from settings or button)

    // ... onTouchEvent same as before ...

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // ...
        MotionEvent.ACTION_UP -> {
            if (rawPoints.size >= 2) {
                val processed = processStroke(
                    rawPoints,
                    smoothingWindow = 5,
                    taperLengthFraction = 0.12f,
                    maxWidth = 16f,
                    closeThresholdPx = 18f,
                    enableClosing = closingEnabled   // ← pass the toggle
                )
                finishedPaths.add(processed)
            }
            rawPoints.clear()
            invalidate()
        }
        // ...
    }

    // Add a public method to toggle from UI
    fun setCurveClosingEnabled(enabled: Boolean) {
        closingEnabled = enabled
    }
}
```

### Quick Tips
- **Threshold tuning**: 15–30 px works well on phones/tablets. You can make it relative: `threshold = averageStrokeWidth * 2.5f` or `totalLength * 0.05f`.
- **Avoid closing very small loops**: Add `if (calculatePathLength(smoothedPoints) > 60f)` before checking distance.
- **Filling closed shapes**: If you want filled regions when closed, change `paint.style = Paint.Style.FILL_AND_STROKE` or add a separate fill path.
- **Live preview**: If you want to preview closing during drawing (while finger is down), you can run a lighter version of the pipeline on `ACTION_MOVE` and conditionally add the close if distance < threshold.

This keeps closing completely decoupled from smoothing — perfect as a separate feature. Let me know if you'd like the curved close version expanded or integrated with variable-width tapering!
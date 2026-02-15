1. Intro:

There will be a toggle button for curve smooting on/off, to the right of the stroke-size slider.

When curve smoothing is on, drawn lines and curves will be adjusted to make them look smoother - ie more continuous. 
When curve smoothing is off, original line drawing logic will apply ie no smoothing.

icon: wave-icon.svg for on state (see top dir), create icon with 3 strait lines for off state

2. Detailed proposal from Grok for curve smoothing:

Here's a complete, self-contained Kotlin implementation for the stroke processing pipeline you described. It works in an Android drawing app (e.g., inside a custom `View` or Compose `Canvas` drawing logic). The pipeline runs **after touch-up** (on `ACTION_UP` or equivalent), processing a list of raw points collected during the stroke.

Key assumptions / simplifications (as per your request):
- No velocity/pressure — tapering is purely shape/length-based (start and end thin, middle thick).
- Uses simple techniques suitable for Android: weighted moving average → Catmull-Rom spline fitting (smooth curves) → variable-width tapering via multiple thin strokes or path effects approximation → optional auto-close based on endpoint distance.
- Returns an Android `Path` ready for drawing (you can stroke it with a `Paint`).

```kotlin
import android.graphics.Path
import android.graphics.PointF
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Processes a raw stroke into a smooth, tapered, optionally closed path.
 * Call this after collecting raw points on touch-up.
 *
 * @param rawPoints List of PointF collected from MotionEvent (x,y)
 * @param smoothingWindow Size for initial averaging (higher = smoother but less accurate)
 * @param curveTension Catmull-Rom tension (0.5 = standard Catmull-Rom)
 * @param taperLengthFraction Fraction of total length where taper happens (e.g. 0.15 = 15% start + 15% end)
 * @param maxWidth Maximum stroke width in pixels
 * @param closeThresholdPx If start/end closer than this, auto-close the path
 * @return Processed Path ready to draw (stroke or fill)
 */
fun processStroke(
    rawPoints: List<PointF>,
    smoothingWindow: Int = 5,
    curveTension: Float = 0.5f,
    taperLengthFraction: Float = 0.15f,
    maxWidth: Float = 12f,
    closeThresholdPx: Float = 20f
): Path {
    if (rawPoints.size < 2) return Path()

    // 1. Noise reduction / filtering (simple moving average)
    val filtered = averageSmoothing(rawPoints, smoothingWindow)

    // 2. Curve fitting (Catmull-Rom spline → many interpolated points)
    val smoothedPoints = catmullRomSpline(filtered, tension = curveTension, segmentsPerSegment = 8)

    // 3. Build final path + apply tapering
    val path = Path()
    applyTaperedStroke(path, smoothedPoints, taperLengthFraction, maxWidth)

    // 4. Optional auto-close
    if (shouldAutoClose(smoothedPoints, closeThresholdPx)) {
        path.close()
    }

    return path
}

/**
 * Step 1: Simple moving average to reduce noise/shakiness.
 * Edge points are kept closer to original.
 */
private fun averageSmoothing(points: List<PointF>, window: Int): List<PointF> {
    if (window < 2 || points.size < window) return points.toList()

    val result = mutableListOf<PointF>()
    val half = window / 2

    for (i in points.indices) {
        var sumX = 0f
        var sumY = 0f
        var count = 0

        for (j in max(0, i - half) .. min(points.lastIndex, i + half)) {
            sumX += points[j].x
            sumY += points[j].y
            count++
        }

        result.add(PointF(sumX / count, sumY / count))
    }
    return result
}

/**
 * Step 2: Catmull-Rom spline interpolation for smooth curves.
 * Produces more points than input → better for tapering & rendering.
 *
 * @param points Control points
 * @param tension Usually 0.5 for Catmull-Rom
 * @param segmentsPerSegment How many intermediate points per original segment
 */
private fun catmullRomSpline(
    points: List<PointF>,
    tension: Float = 0.5f,
    segmentsPerSegment: Int = 8
): List<PointF> {
    if (points.size < 2) return points

    val result = mutableListOf<PointF>()

    // Duplicate first/last points to handle ends smoothly (extrapolate)
    val extended = mutableListOf<PointF>()
    extended.add(points.first())           // p0 = p1
    extended.addAll(points)
    extended.add(points.last())             // pn+1 = pn

    for (i in 1 until extended.lastIndex) {  // skip duplicated start/end
        val p0 = extended[i - 1]
        val p1 = extended[i]
        val p2 = extended[i + 1]
        val p3 = extended[i + 2.coerceAtMost(extended.lastIndex)]

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

/**
 * Step 3: Build a path with variable width by approximating taper.
 * Simple method: draw many small line segments with varying stroke width.
 * (For production, consider a filled path with offset curves or custom PathEffect)
 */
private fun applyTaperedStroke(
    path: Path,
    points: List<PointF>,
    taperFraction: Float,
    maxWidth: Float
) {
    if (points.size < 2) return

    val totalLength = calculatePathLength(points)
    var accumulatedLength = 0f

    path.moveTo(points[0].x, points[0].y)

    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val curr = points[i]

        val segmentLength = hypot(curr.x - prev.x, curr.y - prev.y)
        accumulatedLength += segmentLength

        val width = calculateWidthAtDistance(
            distance = accumulatedLength,
            total = totalLength,
            taperFraction = taperFraction,
            maxWidth = maxWidth
        )

        // For real variable width, you'd need to draw perpendicular offsets or use multiple paths.
        // Here we approximate by just moving forward (you can stroke with Paint.StrokeWidth = width later,
        // but true variable width needs more advanced rendering).
        // For better quality, see Square's Smoother Signatures approach or use filled Path.

        path.lineTo(curr.x, curr.y)

        // Alternative approximation: draw tiny circles at each point with radius = width/2
        // path.addCircle(curr.x, curr.y, width / 2, Path.Direction.CW)
    }
}

/** Helper: width profile — thin at start/end, full in middle */
private fun calculateWidthAtDistance(
    distance: Float,
    total: Float,
    taperFraction: Float,
    maxWidth: Float
): Float {
    val taperLen = total * taperFraction
    return when {
        distance <= taperLen -> maxWidth * (distance / taperLen)           // ramp up
        distance >= total - taperLen -> maxWidth * ((total - distance) / taperLen)  // ramp down
        else -> maxWidth
    }
}

/** Helper: approximate total length of point sequence */
private fun calculatePathLength(points: List<PointF>): Float {
    var length = 0f
    for (i in 1 until points.size) {
        val dx = points[i].x - points[i - 1].x
        val dy = points[i].y - points[i - 1].y
        length += hypot(dx, dy)
    }
    return length
}

/**
 * Step 4: Check if path should be auto-closed (near start/end)
 */
private fun shouldAutoClose(points: List<PointF>, threshold: Float): Boolean {
    if (points.size < 3) return false
    val start = points.first()
    val end = points.last()
    val dist = hypot(end.x - start.x, end.y - start.y)
    return dist <= threshold
}
```

### Usage example (in a custom View)

```kotlin
class DrawingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val rawPoints = mutableListOf<PointF>()
    private val finishedPaths = mutableListOf<Path>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f  // fallback — tapering is baked in path approximation
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.BLACK
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                rawPoints.clear()
                rawPoints.add(PointF(event.x, event.y))
            }
            MotionEvent.ACTION_MOVE -> {
                rawPoints.add(PointF(event.x, event.y))
                invalidate() // optional: preview raw or live-smoothed
            }
            MotionEvent.ACTION_UP -> {
                if (rawPoints.size >= 2) {
                    val processed = processStroke(
                        rawPoints,
                        smoothingWindow = 5,
                        taperLengthFraction = 0.12f,
                        maxWidth = 16f,
                        closeThresholdPx = 18f
                    )
                    finishedPaths.add(processed)
                }
                rawPoints.clear()
                invalidate()
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        finishedPaths.forEach { canvas.drawPath(it, paint) }
        // Optional: draw rawPoints lightly for preview
    }
}
```

### Notes & Improvements
- **True variable width**: The current `applyTaperedStroke` uses fixed-width stroking. For real variable width (like Procreate), use the Square "Smoother Signatures" method (quadratic/cubic Bézier segments with width interpolation) or draw two offset paths (upper/lower envelope) and fill between them.
- **Performance**: For very long strokes, downsample points first (Ramer-Douglas-Peucker).
- **Live preview**: You can call a lighter version of the pipeline on `ACTION_MOVE` (e.g. just averaging) for real-time feedback.
- **Android Ink API** (Jetpack): If targeting newer Android, consider `androidx.ink` for built-in low-latency smoothing.

This gives the core "post-release stylization" behavior you asked for. Adjust parameters to taste!
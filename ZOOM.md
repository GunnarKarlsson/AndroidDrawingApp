To add zoom functionality via **Zoom +** and **Zoom -** buttons (not pinch-to-zoom) to the `DrawingView` from the previous example, we extend the existing panning system by introducing a **scale factor** and combining it with the translation (`panX`, `panY`) using a `Matrix`.

This is the most common, clean, and performant way for drawing apps that support both pan and zoom.

### Core Ideas for Button-based Zoom
- Use a single `scale` variable (e.g. 1.0f = 100%, 2.0f = 200%)
- Apply zoom around the **center of the viewport** (most intuitive for button zoom)
- Keep the bitmap fixed in memory — only change how it's rendered
- Use a `Matrix` to combine scale + translation
- When zooming, adjust `panX`/`panY` so the center stays roughly stable (prevents violent jumps)
- When drawing, convert screen → bitmap coordinates using the **inverse** transform

### Updated DrawingView – Key Additions

```kotlin
class DrawingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var bitmap: Bitmap? = null          // your drawing surface
    private val drawPaint = Paint().apply { /* your paint setup */ }

    // ── Transform state ────────────────────────────────────────
    private var scale = 1.0f                    // current zoom level
    private var panX = 0f
    private var panY = 0f

    // Reasonable limits
    private val MIN_SCALE = 0.25f
    private val MAX_SCALE = 8.0f
    private val ZOOM_STEP = 0.25f               // each button press

    private val matrix = Matrix()
    private val inverseMatrix = Matrix()        // for touch → bitmap conversion

    // ── Zoom buttons (you call these from your activity/fragment) ────────

    fun zoomIn() {
        zoomByFactor(1 + ZOOM_STEP, width / 2f, height / 2f)
    }

    fun zoomOut() {
        zoomByFactor(1 - ZOOM_STEP, width / 2f, height / 2f)
    }

    /**
     * Apply zoom factor around a specific screen point (here: usually viewport center)
     */
    private fun zoomByFactor(factor: Float, focusX: Float, focusY: Float) {
        val oldScale = scale
        scale *= factor
        scale = scale.coerceIn(MIN_SCALE, MAX_SCALE)

        if (scale == oldScale) return  // no change

        // Adjust pan so the point under focus stays roughly in the same screen position
        // New pan = old pan * newScale/oldScale - focus * (newScale/oldScale - 1)
        val scaleRatio = scale / oldScale

        panX = panX * scaleRatio - focusX * (scaleRatio - 1)
        panY = panY * scaleRatio - focusY * (scaleRatio - 1)

        // Optional: clamp translation so content doesn't move completely off-screen
        clampTranslation()

        invalidate()
    }

    private fun clampTranslation() {
        bitmap?.let { bmp ->
            val visibleW = width / scale
            val visibleH = height / scale

            // Don't allow panning past the edges too far
            panX = panX.coerceIn(-bmp.width + visibleW, 0f)
            panY = panY.coerceIn(-bmp.height + visibleH, 0f)
        }
    }

    // ── onDraw ───────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        bitmap?.let { bmp ->
            matrix.reset()
            matrix.postScale(scale, scale)
            matrix.postTranslate(panX, panY)

            // Optional: center initially if bitmap smaller than view
            // if (firstDraw) { centerContent(); firstDraw = false }

            canvas.drawBitmap(bmp, matrix, null)

            // ── Draw overlays, selection, temporary stroke preview, etc. ──
            // They should also be transformed with the same matrix if needed
        }
    }

    // ── Important: Map screen touches → bitmap coordinates when drawing ─────

    private fun screenToBitmap(x: Float, y: Float, outPoint: PointF) {
        inverseMatrix.reset()
        matrix.invert(inverseMatrix)
        inverseMatrix.mapPoints(floatArrayOf(x, y), floatArrayOf(outPoint.x, outPoint.y))
    }

    // Example usage in onTouchEvent (when in drawing mode)
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isPanningMode) {
            // your existing pan logic...
            // Remember: pan deltas should be divided by scale!
            // deltaX /= scale; deltaY /= scale; then panX += deltaX * scale (see note below)
        } else {
            // Drawing mode
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val bmpPoint = PointF()
                    screenToBitmap(event.x, event.y, bmpPoint)
                    // Now use bmpPoint.x, bmpPoint.y to draw on the bitmap
                }
            }
        }
        return true
    }

    // Helper to center content (call on bitmap change or first draw)
    fun centerContent() {
        bitmap?.let { bmp ->
            panX = (width - bmp.width * scale) / 2f
            panY = (height - bmp.height * scale) / 2f
            invalidate()
        }
    }
}
```

### Connecting Zoom Buttons (in Activity / Fragment)

```kotlin
class MainActivity : AppCompatActivity() {

    private lateinit var drawingView: DrawingView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawingView = findViewById(R.id.drawing_view)

        findViewById<Button>(R.id.btn_zoom_in).setOnClickListener {
            drawingView.zoomIn()
        }

        findViewById<Button>(R.id.btn_zoom_out).setOnClickListener {
            drawingView.zoomOut()
        }

        // Optional: reset zoom
        findViewById<Button>(R.id.btn_reset)?.setOnClickListener {
            drawingView.scale = 1f
            drawingView.centerContent()  // or panX=0f; panY=0f
        }
    }
}
```

### Important Notes & Best Practices

| Aspect                  | Recommendation                                                                 | Why |
|-------------------------|--------------------------------------------------------------------------------|-----|
| Zoom pivot point        | Usually viewport center (`width/2`, `height/2`)                                | Most natural for button zoom |
| Pan adjustment on zoom  | Yes — scale pan offsets & shift by focus delta                                 | Prevents content from jumping away |
| Panning sensitivity     | When dragging to pan: `panX += deltaX` (screen delta)                          | User drags screen pixels, not bitmap pixels |
| Drawing coordinates     | Always use `matrix.invert()` → `mapPoints()` to convert touch to bitmap coords | Essential for correct stroke placement |
| Clamping                | Optional but recommended — keep some content visible                           | Better UX |
| Performance             | `Matrix` + `drawBitmap(matrix)` is very fast on modern devices                | Preferred over scaling the bitmap itself |
| Optional extras         | Add double-tap to reset zoom, pinch-to-zoom later, zoom slider                | Common in drawing apps |

This gives smooth, centered button zoom that plays nicely with your existing panning logic. If you later want to add pinch-to-zoom, you can reuse almost the same code — just replace button calls with `ScaleGestureDetector` updates to `scale` and focus point.

Let me know if you want the pinch version too, or need help with fling momentum after panning, undo system integration, etc.
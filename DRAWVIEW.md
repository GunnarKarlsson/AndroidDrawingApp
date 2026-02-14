In a well-designed Android drawing app written in Kotlin (e.g., using a custom `View` subclass like `DrawingView` that handles touch events and drawing on a `Bitmap`), panning is typically implemented by keeping the underlying `Bitmap` fixed in memory while applying a translation offset to control how it's rendered on the screen. This approach is efficient because it avoids unnecessary bitmap manipulations (like shifting pixels within the bitmap itself, which would be costly for large images). Instead, the "view" of the bitmap—meaning the visible portion and its position within the screen viewport—is what's effectively moved via offsets.

I'll break this down step-by-step, including the core logic, touch handling, and rendering. This assumes a basic setup where:
- You have a mutable `Bitmap` (e.g., created with `Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)`) that serves as the drawing canvas.
- A custom `View` (e.g., `class DrawingView : View`) overrides `onDraw` to render the bitmap and `onTouchEvent` to handle gestures like panning.
- Initially, the bitmap matches the viewport (e.g., drawn at (0,0) with no scaling or offset).
- Panning is a two-finger or mode-activated gesture, but for simplicity, I'll describe it as a single-touch drag (common in drawing apps when not in draw mode).

### 1. **Key Concepts and State Variables**
   - **Bitmap**: This is your fixed drawing surface in memory. Its pixel data doesn't change during panning—panning only affects *where* it's drawn on the screen.
   - **Viewport**: The visible area of your `View` (e.g., the screen bounds of your `DrawingView`).
   - **Offsets (panX, panY)**: Floating-point variables (e.g., `private var panX = 0f; private var panY = 0f;`) that track the cumulative translation. These represent how much the bitmap's top-left corner has been shifted relative to the viewport's top-left (0,0).
   - **Touch Positions**: You track the start touch position (`startX, startY`) on `MotionEvent.ACTION_DOWN` and the current position on `MotionEvent.ACTION_MOVE`. The delta (difference) is used to update the offsets.
   - **Matrix (Optional)**: For more advanced handling (e.g., with zooming), use an `android.graphics.Matrix` to combine translation, scale, etc. But for pure panning, simple offsets suffice.
   - **Why not move the bitmap?** Shifting pixels in the bitmap (e.g., via `Canvas` drawing onto itself) is inefficient and destructive—it could lose data off the edges and require resizing the bitmap, which is bad for performance in a real-time app.

In essence, the bitmap stays "fixed" as a static image buffer, and you move the "camera" (the view's rendering offset) over it. This is similar to how map apps or image editors like Photoshop handle panning.

### 2. **Touch Event Handling for Panning**
In your custom `View`'s `onTouchEvent` method, detect panning gestures. Here's a simplified Kotlin example:

```kotlin
class DrawingView(context: Context) : View(context) {
    private var bitmap: Bitmap? = null  // Your drawing bitmap, initialized elsewhere
    private var panX = 0f
    private var panY = 0f
    private var startX = 0f  // Touch start for panning
    private var startY = 0f
    private var isPanning = false  // Flag to toggle panning mode (e.g., via button or gesture)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isPanning) {
            // Handle drawing mode instead (e.g., draw on bitmap)
            return super.onTouchEvent(event)
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Record touch start position (screen coords)
                startX = event.x
                startY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                // Calculate delta from start
                val deltaX = event.x - startX
                val deltaY = event.y - startY

                // Update offsets (this "moves" the view of the bitmap)
                panX += deltaX
                panY += deltaY

                // Clamp offsets if needed (e.g., prevent panning beyond bitmap edges)
                panX = panX.coerceIn(-bitmap!!.width.toFloat() + width.toFloat(), 0f)
                panY = panY.coerceIn(-bitmap!!.height.toFloat() + height.toFloat(), 0f)

                // Update touch start for next move (smooth dragging)
                startX = event.x
                startY = event.y

                // Invalidate to trigger redraw with new offsets
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                // End panning (optional cleanup)
            }
        }
        return true  // Consume the event
    }

    // ... onDraw implementation below
}
```

- **What happens here?**
  - On touch down: Capture the initial screen position (X,Y).
  - On move: Compute the delta from the start (or previous move) to the current touch-end position.
  - Update `panX` and `panY` by adding the delta. This accumulates the pan offset over multiple drags.
  - Optionally clamp the offsets so the bitmap doesn't pan completely off-screen (e.g., keep at least some part visible).
  - Call `invalidate()` to request a redraw, applying the new offsets.
  - Note: Screen touch positions are in view coordinates (relative to the `View`'s top-left). No need for global screen coords unless your view is nested.

If you're supporting multitouch (e.g., pinch-to-zoom with panning), use `ScaleGestureDetector` and `GestureDetector` together, but the pan logic remains similar—update offsets based on translation deltas.

### 3. **Rendering (onDraw) with Panning**
In `onDraw`, apply the offsets when drawing the bitmap. This makes the bitmap appear to move while keeping it fixed in memory:

```kotlin
override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    bitmap?.let {
        // Draw the bitmap with offsets (top-left shifted by panX, panY)
        canvas.drawBitmap(it, panX, panY, null)
        
        // If you have overlays (e.g., grid or temporary strokes), draw them here with the same offsets
    }
}
```

- **What happens when panned?**
  - Initially (`panX=0, panY=0`): Bitmap draws at (0,0), matching the viewport.
  - After panning: The bitmap's top-left is now at (panX, panY). If panX is negative (e.g., -100), the bitmap shifts right by 100 pixels (showing more of the left side). Positive panX shifts left.
  - The viewport clips anything outside its bounds automatically (via `Canvas` clipping).
  - For efficiency, if the bitmap is larger than the screen, you could use `drawBitmap` with a source rectangle to draw only the visible portion, but simple translation works for most cases.

Using a `Matrix` for more flexibility (e.g., with scale):

```kotlin
private val matrix = Matrix()

override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    bitmap?.let {
        matrix.reset()
        matrix.postTranslate(panX, panY)  // Apply pan (could add scale too)
        canvas.drawBitmap(it, matrix, null)
    }
}
```

### 4. **Handling Drawing Interactions with Panning**
When in drawing mode (not panning), touch events need to map screen coordinates back to bitmap coordinates:

- Screen touch (sx, sy) → Bitmap position = (sx - panX, sy - panY)
- Use this to draw on the bitmap (e.g., via a temporary `Canvas(bitmap)`).

Example for drawing a line:

```kotlin
// In onTouchEvent, for drawing mode
val bitmapX = event.x - panX
val bitmapY = event.y - panY
// Use bitmapX, bitmapY to set pixels or draw on bitmap's Canvas
```

This ensures strokes land in the correct spot on the bitmap, regardless of pan.

### 5. **Best Practices in a Well-Designed App**
- **Performance**: Use hardware acceleration (`android:hardwareAccelerated="true"` in manifest). Avoid resizing/recreating the bitmap during pan.
- **Edge Cases**: Handle bitmap sizes larger/smaller than viewport. Add bounds checking to prevent over-panning.
- **Undo/Redo**: Panning doesn't affect the bitmap, so it's non-destructive and doesn't need undo.
- **Alternatives**: For very large bitmaps, consider tiled rendering or libraries like `PhotoView` (for image viewing) or `Canvas` wrappers in apps like Infinite Painter. But for custom drawing, the offset approach is standard.
- **Testing**: Use `GestureDetector` for fling momentum if desired (smooth scrolling after lift).

This logic keeps things responsive and memory-efficient. If you share code snippets or specifics from your app, I can refine this further!
To add **two-finger pinch-to-zoom** to your `DrawingView` (while keeping the existing two-finger panning, single-finger drawing/panning, and the button-based zoom control), the cleanest and most reliable approach is to use Android's built-in **`ScaleGestureDetector`**. It handles pinch detection, focal point calculation, and span changes very well — exactly what you need to zoom toward the center of the two fingers.

This integrates nicely with your current multi-touch panning logic without major conflicts.

### Step 1: Add State Variables
Add these near your existing pan/scale/touch state variables:

```kotlin
private val scaleDetector: ScaleGestureDetector
private var isScaling: Boolean = false          // Track if pinch is active in current gesture
private var focusX: Float = 0f                  // Used temporarily during scaling
private var focusY: Float = 0f

// Optional but recommended: zoom limits
private val MIN_SCALE = 0.5f
private val MAX_SCALE = 8.0f   // adjust to taste
```

Initialize in the constructor (or init block):

```kotlin
init {
    scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isScaling = true
            focusX = detector.focusX
            focusY = detector.focusY
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val factor = detector.scaleFactor

            // Zoom toward the focal point (midpoint between fingers)
            // 1. Shift so focal point is at origin
            panX -= focusX
            panY -= focusY

            // 2. Apply scale
            val newScale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
            scale = newScale

            // 3. Shift back — but scaled
            panX += focusX
            panY += focusY

            // Optional: small pan adjustment during pinch to feel more natural
            // panX += (detector.focusX - focusX) * 0.1f   // tweak factor if desired
            // panY += (detector.focusY - focusY) * 0.1f

            focusX = detector.focusX    // update for next event
            focusY = detector.focusY

            clampPan()   // very important after scale changes!
            invalidate()
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isScaling = false
        }
    })
}
```

**Important note on the zoom math**: The code above implements **zoom-to-focal-point** correctly by temporarily translating the coordinate system.

### Step 2: Update `onTouchEvent` to Integrate Scaling
Pass **every** `MotionEvent` to the `scaleDetector` first — this is the official pattern.

Modify your `onTouchEvent` like this (building on your previous two-finger panning code):

```kotlin
override fun onTouchEvent(event: MotionEvent): Boolean {
    // Always feed events to scale detector first
    scaleDetector.onTouchEvent(event)

    val pointerCount = event.pointerCount
    val action = event.actionMasked

    when (action) {
        MotionEvent.ACTION_DOWN -> {
            // Your existing DOWN logic...
            isScaling = false
            isGesturePanning = false
            // ...
            return true
        }

        MotionEvent.ACTION_POINTER_DOWN -> {
            if (pointerCount >= 2) {
                isGesturePanning = true   // still good to mark for consistency

                // If we were drawing, cancel it
                if (!isPanning && dragPoints.isNotEmpty()) {
                    dragPoints.clear()
                    currentStrokePoints = emptyList()
                    isDrag = false
                }

                // Optional: if you want to pause single-finger pan during pinch start
            }
            return true
        }

        MotionEvent.ACTION_MOVE -> {
            if (isScaling) {
                // Scaling is already handled inside ScaleGestureDetector callbacks
                // We just need to prevent other branches from running
                return true
            }

            if (pointerCount > 1) {
                // Your existing multi-finger centroid panning logic
                // ...
                return true
            } else if (pointerCount == 1) {
                // Your existing single-finger pan or draw logic
                // But only if NOT scaling (which we already checked)
                // ...
            }
        }

        MotionEvent.ACTION_POINTER_UP -> {
            // Your existing logic for updating lastMoveX/Y when dropping to 1 finger
            // ...
            return true
        }

        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            // Your existing UP logic, but skip draw finalization if isScaling or isGesturePanning
            if (!isGesturePanning && !isPanning && !isScaling) {
                // finalize stroke or tap
            }
            // Clear states
            dragPoints.clear()
            currentStrokePoints = emptyList()
            isScaling = false
            return true
        }
    }

    return super.onTouchEvent(event)
}
```

### Step 3: Minor Adjustments

**A. Better clamping after zoom**

Your current `clampPan()` assumes `scale` is already applied. After zoom changes, call `clampPan()` — it should work as-is because it uses `bmpW * scale` etc.

You may want to make clamping more aggressive after zoom-out:

```kotlin
private fun clampPan() {
    val layers = layerBitmaps
    if (layers.isEmpty()) return
    val bmpW = layers.maxOfOrNull { it.width }?.toFloat() ?: 0f
    val bmpH = layers.maxOfOrNull { it.height }?.toFloat() ?: 0f
    val vw = width.toFloat()
    val vh = height.toFloat()

    val scaledW = bmpW * scale
    val scaledH = bmpH * scale

    val minPanX = (vw - scaledW).coerceAtMost(0f)
    val minPanY = (vh - scaledH).coerceAtMost(0f)

    panX = panX.coerceIn(minPanX, 0f)
    panY = panY.coerceIn(minPanY, 0f)
}
```

**B. Sync with button zoom**

When the user taps the +/- buttons, update `scale` and then:

```kotlin
scale = (scale * 1.2f).coerceIn(MIN_SCALE, MAX_SCALE)   // or whatever step
clampPan()
invalidate()
```

Optionally zoom toward view center:

```kotlin
val centerX = width / 2f
val centerY = height / 2f
panX = centerX - (centerX - panX) * (newScale / scale)
panY = centerY - (centerY - panY) * (newScale / scale)
scale = newScale
```

### Summary — Priority & Conflicts

| Gesture              | Fingers | Priority / Behavior                              |
|----------------------|---------|--------------------------------------------------|
| Pinch zoom           | 2+      | Highest — handled by `ScaleGestureDetector`      |
| Two-finger pan       | 2+      | Only if not scaling (rare conflict)              |
| Single-finger pan    | 1       | When `isPanning` or continued from multi         |
| Drawing              | 1       | Only pure single-finger non-pan gestures         |

This should feel natural (like Google Photos, maps apps). Test edge cases: pinch + drag, lift one finger mid-pinch, start drawing then pinch, etc.

If you run into jitter or want smoother zoom-to-focal during fast pinches, you can record the initial focus and only update `panX/Y` based on the delta from initial focus — but the above is usually good enough.

Let me know how it behaves or if you want to merge panning & zooming into one two-finger gesture more tightly!
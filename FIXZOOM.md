The main issues you're seeing (jittery panning, zoom and pan "breaking" each other) almost always come from **double-applying translation (pan) during two-finger gestures** — especially when pinch-zoom happens at the same time as (or very close to) finger movement that would normally trigger centroid-based panning.

In your current setup:
- `ScaleGestureDetector` calls `onScale()` many times per gesture (often 10–30+ times during a pinch).
- Each `onScale()` applies scale **and** tries to keep the focal point fixed using a pan adjustment.
- But **simultaneously**, your `ACTION_MOVE` branch (when `pointerCount > 1` and `!isScaling`) computes centroid delta → adds extra `panX += dx`, `panY += dy`.
- Even when `isScaling` **is** true, if the fingers are moving (translating) while pinching, the centroid logic can still run in some frames if timing/condition order is unlucky.
- Tiny fluctuations in `scaleFactor` (~1.001–1.003) from noisy touch input cause repeated small pan corrections → visible jitter/stutter.
- When lifting fingers (`POINTER_UP` / transition to 1 finger), mismatched `lastMoveX/Y` vs `lastAvgX/Y` vs focus position causes a sudden jump.

The fixed code I proposed earlier solves exactly these problems. Here's **why it works** and what each change addresses:

### 1. **Move all pan-during-zoom into `onScale()` — using focus delta**
   - In `onScale()`:
     ```kotlin
     val dx = currentFocusX - lastFocusX
     val dy = currentFocusY - lastFocusY
     panX += dx
     panY += dy
     ```
     → This captures **any translation of the pinch center itself** (i.e. the user is dragging while pinching). It's the natural way two-finger pan feels during zoom.
   - Then apply the scale correction around the **current** focus:
     ```kotlin
     panX = currentFocusX + (panX - currentFocusX) * scaleRatio
     panY = currentFocusY + (panY - currentFocusY) * scaleRatio
     ```
   - **Result**: Pan during pinch is smooth and correct (no separate centroid logic fighting it). No double-panning.

### 2. **Strongly prevent centroid panning during active scaling**
   - In `ACTION_MOVE`:
     ```kotlin
     if (isScaling) {
         return true  // ← do **nothing** else
     }
     ```
   - This blocks the old two-finger centroid code whenever `ScaleGestureDetector` is processing a real pinch. Only non-pinch two-finger movement (parallel drag) uses centroid — which is now rare / fallback.

### 3. **Raise jitter threshold for tiny scale changes**
   - `if (Math.abs(factor - 1f) > 0.005f)` (was probably 0.01 or lower)
   - Touch sensors are noisy → Android often reports scaleFactor = 1.002, 0.998, etc. even when fingers are almost still.
   - Ignoring changes < ~0.5% prevents hundreds of tiny pan corrections per second → much smoother zoom feel.

### 4. **Smooth handoff on `POINTER_UP` and `onScaleEnd`**
   - On `POINTER_UP`: recompute `lastAvgX/Y` from remaining pointers (correct average)
   - On `onScaleEnd`: set `lastAvgX/Y = detector.focusX/Y` and (if now 1 finger) also set `lastMoveX/Y` → single-finger pan continues without jump.

### 5. **Button zoom uses identical math**
   - `zoomByFactor` now matches pinch formula exactly → consistent behavior whether button or fingers.

### 6. **Improved `clampPan()` centers small content**
   - When zoomed out so content < screen, pan snaps to center → feels polished, prevents "stuck in corner".

### Summary: What got fixed

| Problem you saw                  | Root cause                                      | How the code fixes it                              |
|----------------------------------|-------------------------------------------------|----------------------------------------------------|
| Jittery pan during/after pinch   | Tiny scaleFactor noise → repeated micro-pans   | Higher threshold + move pan logic into onScale()   |
| Pan & zoom fight / break each other | Centroid pan runs **at same time** as focus pan | Block centroid when `isScaling`; use focus delta   |
| Jump when lifting finger mid-gesture | `lastMoveX/Y` desynced from focus/avg          | Update both on `POINTER_UP` & `onScaleEnd`         |
| Two-finger drag feels wrong during zoom | Separate pan code overrides zoom pan            | Unified pan handling inside `onScale()`            |

Test this version carefully with these gestures:
- Pure pinch (no translation) → smooth zoom to finger center
- Pinch + drag fingers together → zooms **and** pans smoothly
- Two-finger drag without pinch distance change → uses centroid (fallback)
- Pinch → lift one finger → remaining finger continues panning without jump
- Start drawing → add second finger → cancels draw, starts pan/zoom

If you still see minor issues (device-specific touch noise, very fast gestures), you can experiment with even higher threshold (0.01f) or add a small smoothing factor on the focus delta.

Let me know what specific remaining symptom you see (video description / exact gesture / device) if it isn't fully smooth yet.

```kotlin
// Step 1: Add/Adjust State Variables
// Add these if not present:
private var lastFocusX: Float = 0f  // Track previous focus for pan during zoom
private var lastFocusY: Float = 0f

// In init block, update ScaleGestureDetector listener:
scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        isScaling = true
        lastFocusX = detector.focusX
        lastFocusY = detector.focusY
        return true
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        val currentFocusX = detector.focusX
        val currentFocusY = detector.focusY

        // First, apply pan delta from focus movement (handles simultaneous pan + zoom)
        val dx = currentFocusX - lastFocusX
        val dy = currentFocusY - lastFocusY
        panX += dx
        panY += dy

        // Then, apply scale
        val oldScale = scale
        val factor = detector.scaleFactor
        if (Math.abs(factor - 1f) > 0.005f) {  // Increase threshold to 0.005 to reduce jitter from tiny changes
            scale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
            val scaleRatio = scale / oldScale

            // Adjust pan to keep current focus fixed during scale
            panX = currentFocusX + (panX - currentFocusX) * scaleRatio
            panY = currentFocusY + (panY - currentFocusY) * scaleRatio
        }

        // Update for next
        lastFocusX = currentFocusX
        lastFocusY = currentFocusY

        clampPan()
        invalidate()
        return true
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {
        isScaling = false
        // After end, update lastAvgX/Y or lastMoveX/Y if continuing to pan
        lastAvgX = detector.focusX
        lastAvgY = detector.focusY
        if (event.pointerCount == 1) {  // If dropped to one, set for single pan
            lastMoveX = lastAvgX
            lastMoveY = lastAvgY
        }
    }
})
```

```kotlin
// Step 2: Updated onTouchEvent
override fun onTouchEvent(event: MotionEvent): Boolean {
    scaleDetector.onTouchEvent(event)  // Always first

    val action = event.actionMasked
    val pointerCount = event.pointerCount

    when (action) {
        MotionEvent.ACTION_DOWN -> {
            touchStartX = event.x
            touchStartY = event.y
            lastMoveX = event.x
            lastMoveY = event.y
            dragPoints.clear()
            dragPoints.add(screenToBitmap(event.x, event.y))
            isDrag = false
            isGesturePanning = false
            isScaling = false  // Reset, but detector will set if applicable
            return true
        }
        MotionEvent.ACTION_POINTER_DOWN -> {
            if (pointerCount >= 2) {
                isGesturePanning = true
                // Cancel any ongoing draw
                if (!isPanning && dragPoints.isNotEmpty()) {
                    dragPoints.clear()
                    currentStrokePoints = emptyList()
                    isDrag = false
                }
                // Init avg for non-scaling multi-pan
                var sumX = 0f
                var sumY = 0f
                for (i in 0 until pointerCount) {
                    sumX += event.getX(i)
                    sumY += event.getY(i)
                }
                lastAvgX = sumX / pointerCount
                lastAvgY = sumY / pointerCount
            }
            return true
        }
        MotionEvent.ACTION_MOVE -> {
            if (isScaling) {
                // During scaling, pan is already handled in onScale via focus delta
                // No need for additional pan here; avoids double-panning jitter
                return true
            }

            if (pointerCount > 1) {
                // Non-scaling multi-finger pan (e.g., parallel movement)
                var sumX = 0f
                var sumY = 0f
                for (i in 0 until pointerCount) {
                    sumX += event.getX(i)
                    sumY += event.getY(i)
                }
                val avgX = sumX / pointerCount
                val avgY = sumY / pointerCount

                val dx = avgX - lastAvgX
                val dy = avgY - lastAvgY
                panX += dx
                panY += dy
                clampPan()
                invalidate()

                lastAvgX = avgX
                lastAvgY = avgY
                return true
            } else if (pointerCount == 1 && (isGesturePanning || isPanning)) {
                // Single-finger pan
                val dx = event.x - lastMoveX
                val dy = event.y - lastMoveY
                panX += dx
                panY += dy
                clampPan()
                invalidate()

                lastMoveX = event.x
                lastMoveY = event.y
                return true
            } else if (pointerCount == 1) {
                // Drawing
                val pt = screenToBitmap(event.x, event.y)
                dragPoints.add(pt)
                if (!isDrag) {
                    val dx = event.x - touchStartX
                    val dy = event.y - touchStartY
                    if (dx * dx + dy * dy > tapSlopPx * tapSlopPx) {
                        isDrag = true
                    }
                }
                currentStrokePoints = dragPoints.toList()
                return true
            }
        }
        MotionEvent.ACTION_POINTER_UP -> {
            // Update last positions for remaining pointers
            val remainingCount = pointerCount - 1  // Since includes lifting one
            if (remainingCount >= 1) {
                var sumX = 0f
                var sumY = 0f
                val upIndex = event.actionIndex
                for (i in 0 until pointerCount) {
                    if (i != upIndex) {
                        sumX += event.getX(i)
                        sumY += event.getY(i)
                    }
                }
                lastAvgX = sumX / remainingCount
                lastAvgY = sumY / remainingCount
                if (remainingCount == 1) {
                    // Find the remaining pointer index
                    val remainingIndex = if (upIndex == 0) 1 else 0  // For 2 fingers; generalize for more
                    lastMoveX = event.getX(remainingIndex)
                    lastMoveY = event.getY(remainingIndex)
                }
            }
            return true
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            if (!isGesturePanning && !isPanning && !isScaling) {
                if (isDrag && dragPoints.size >= 2) {
                    val widthInBitmap = strokePreviewWidth / scale
                    onStrokeDrawn?.invoke(dragPoints.toList(), widthInBitmap)
                } else if (!isDrag && dragPoints.isNotEmpty()) {
                    val first = dragPoints.first()
                    onTap?.invoke(first.x, first.y)
                }
            }
            dragPoints.clear()
            currentStrokePoints = emptyList()
            isScaling = false
            isGesturePanning = false  // Reset for next gesture
            return true
        }
    }
    return super.onTouchEvent(event)
}
```

```kotlin
// Step 3: Update Button Zoom (zoomByFactor)
private fun zoomByFactor(factor: Float, focusX: Float, focusY: Float) {
    val oldScale = scale
    scale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
    val scaleRatio = scale / oldScale

    // Same formula as pinch
    panX = focusX + (panX - focusX) * scaleRatio
    panY = focusY + (panY - focusY) * scaleRatio

    clampPan()
    invalidate()
}
```

```kotlin
// Step 4: Improved clampPan (handle over-zoom better)
private fun clampPan() {
    val layers = layerBitmaps
    if (layers.isEmpty()) return
    val bmpW = layers.maxOfOrNull { it.width }?.toFloat() ?: 0f
    val bmpH = layers.maxOfOrNull { it.height }?.toFloat() ?: 0f
    val vw = width.toFloat()
    val vh = height.toFloat()

    val scaledW = bmpW * scale
    val scaledH = bmpH * scale

    // Allow centering if scaled < view
    val minPanX = if (scaledW < vw) (vw - scaledW) / 2f else vw - scaledW
    val maxPanX = if (scaledW < vw) minPanX else 0f
    val minPanY = if (scaledH < vh) (vh - scaledH) / 2f else vh - scaledH
    val maxPanY = if (scaledH < vh) minPanY else 0f

    panX = panX.coerceIn(minPanX, maxPanX)
    panY = panY.coerceIn(minPanY, maxPanY)
}
```


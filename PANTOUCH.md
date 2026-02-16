To add two-finger panning (which can generalize to multi-finger panning for robustness) without interfering with drawing, while preserving the existing `isPanning` tool mode for single-finger panning, we'll make the following high-level changes:

- Introduce multi-finger panning that triggers whenever two or more fingers are detected during a gesture (from DOWN to UP). This will use the movement of the centroid (average position) of all fingers to update `panX` and `panY`.
- If a single-finger drawing stroke is in progress when a second finger touches down, cancel the stroke to avoid interference.
- Mark the entire gesture as a "panning gesture" if it ever involves multiple fingers. This ensures that if fingers are lifted (reducing back to one), the remaining finger continues panning (instead of switching to drawing), until the gesture ends on UP/CANCEL.
- Preserve single-finger behavior: If the gesture never involves multiple fingers, it follows the existing logic (pan if `isPanning` is true; draw otherwise).
- Handle transitions smoothly, e.g., update `lastMoveX`/`lastMoveY` when reducing from multi to single so deltas are correct.
- Use `event.actionMasked` for proper multi-touch action detection.
- Ignore gestures with more than one finger if already in single-finger panning mode? No, allow multi-finger to override for flexibility, as it's a common UX pattern.

This approach ensures drawing only happens in pure single-finger gestures when `!isPanning`. Multi-finger panning is always available and takes priority.

### Step 1: Add New State Variables
Add these near your existing touch/curve state (around lines 142-148):

```kotlin
private var isGesturePanning: Boolean = false  // True if this gesture ever had >1 finger (forces panning mode)
private var lastAvgX: Float = 0f  // Last average X for multi-finger pan
private var lastAvgY: Float = 0f  // Last average Y for multi-finger pan
```

### Step 2: Update `onTouchEvent`
Replace your existing `onTouchEvent` with this version. It integrates your current logic while adding multi-touch handling. Key changes:
- Use `event.actionMasked`.
- Add cases for `ACTION_POINTER_DOWN` and `ACTION_POINTER_UP`.
- In `ACTION_MOVE`, branch based on `pointerCount` and `isGesturePanning`.
- Cancel ongoing draws when multi-finger starts.
- Compute centroid for multi-finger panning.
- On pointer lift (to single), continue as pan if `isGesturePanning`.
- Reset states appropriately.

```kotlin
override fun onTouchEvent(event: MotionEvent): Boolean {
    val action = event.actionMasked
    when (action) {
        MotionEvent.ACTION_DOWN -> {
            val sx = event.x
            val sy = event.y
            touchStartX = sx
            touchStartY = sy
            lastMoveX = sx
            lastMoveY = sy
            dragPoints.clear()
            dragPoints.add(screenToBitmap(sx, sy))
            isDrag = false
            isGesturePanning = false  // Reset for new gesture
            return true
        }
        MotionEvent.ACTION_POINTER_DOWN -> {
            if (event.pointerCount > 1) {
                // Mark as panning gesture
                isGesturePanning = true

                // Cancel any ongoing single-finger draw (if not already in pan mode)
                if (!isPanning && dragPoints.isNotEmpty()) {
                    dragPoints.clear()
                    currentStrokePoints = emptyList()
                    isDrag = false
                }

                // Initialize average position for multi-pan
                var sumX = 0f
                var sumY = 0f
                for (i in 0 until event.pointerCount) {
                    sumX += event.getX(i)
                    sumY += event.getY(i)
                }
                lastAvgX = sumX / event.pointerCount
                lastAvgY = sumY / event.pointerCount
            }
            return true
        }
        MotionEvent.ACTION_MOVE -> {
            if (event.pointerCount > 1) {
                // Multi-finger pan: Move based on centroid delta
                var sumX = 0f
                var sumY = 0f
                for (i in 0 until event.pointerCount) {
                    sumX += event.getX(i)
                    sumY += event.getY(i)
                }
                val avgX = sumX / event.pointerCount
                val avgY = sumY / event.pointerCount

                val dx = avgX - lastAvgX
                val dy = avgY - lastAvgY
                panX += dx
                panY += dy
                clampPan()
                invalidate()

                lastAvgX = avgX
                lastAvgY = avgY
                return true
            } else if (event.pointerCount == 1) {
                lastMoveX = event.x
                lastMoveY = event.y

                if (isGesturePanning || isPanning) {
                    // Single-finger pan (either from tool or continued from multi)
                    val dx = event.x - lastMoveX  // Note: lastMoveX is updated after, so this uses previous
                    val dy = event.y - lastMoveY
                    panX += dx
                    panY += dy
                    clampPan()
                    invalidate()
                } else {
                    // Single-finger draw (only if gesture never had multi-fingers)
                    val pt = screenToBitmap(event.x, event.y)
                    dragPoints.add(pt)
                    if (!isDrag && (event.x - touchStartX).let { dx -> (event.y - touchStartY).let { dy -> dx * dx + dy * dy > tapSlopPx * tapSlopPx } }) {
                        isDrag = true
                    }
                    currentStrokePoints = dragPoints.toList()
                }
                return true
            }
            // For >1 not handled above, fall through (ignore extra fingers if needed)
        }
        MotionEvent.ACTION_POINTER_UP -> {
            if (event.pointerCount > 1) {  // During POINTER_UP, count includes the lifting finger
                // Transition to single: Find remaining active pointer and update lastMoveX/Y
                val upIndex = event.actionIndex
                val activeIndex = if (upIndex == 0) 1 else 0  // Assume 2 fingers; generalize if needed
                lastMoveX = event.getX(activeIndex)
                lastMoveY = event.getY(activeIndex)

                // Also update lastAvg to match, for smoothness if more pointers
                lastAvgX = lastMoveX
                lastAvgY = lastMoveY
            }
            return true
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            if (!isGesturePanning && !isPanning) {  // Only finalize draw if pure single-finger non-pan gesture
                if (isDrag && dragPoints.size >= 2) {
                    val widthInBitmap = strokePreviewWidth / scale
                    onStrokeDrawn?.invoke(dragPoints.toList(), widthInBitmap)
                } else if (!isDrag && dragPoints.isNotEmpty()) {
                    val first = dragPoints.first()
                    onTap?.invoke(first.x, first.y)
                }
            }
            // Always clear at end
            dragPoints.clear()
            currentStrokePoints = emptyList()
            return true
        }
    }
    return super.onTouchEvent(event)
}
```

### Notes and Testing Tips
- **Clamping and Redraw**: Multi-finger panning uses the same `clampPan()` and `invalidate()` as single-finger, so it integrates seamlessly.
- **Scale and Matrix**: No changes needed; panning updates `panX`/`panY` the same way.
- **Interference Prevention**: Drawing is canceled on multi-finger start, and won't resume in the same gesture even after lifting to one finger (it continues as pan instead).
- **Edge Cases**: 
  - Start with two fingers: Pans immediately.
  - Add third finger: Still pans using centroid (robust).
  - If `isPanning` is true, single-finger works as before; multi enhances it.
  - Taps (no drag): Still trigger `onTap` if pure single-finger.
- **Performance**: Centroid calculation is cheap; fine for mobile.
- Test on a device/emulator with multi-touch support. Log `pointerCount` and states if issues arise.
- If you have zoom (not shown in code), you could extend this for two-finger pinch-zoom similarly (track distance between fingers).

If this doesn't fit your exact setup or you encounter issues, provide more code context (e.g., full class or scale handling).
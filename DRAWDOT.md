Here’s the repeated and consolidated solution for implementing **dot drawing** with these two specific requirements:

1. The dot size **matches the current brush size** (strokePreviewWidth / scale)
2. There is **visual feedback** (a preview dot appears while the finger is still pressed down, before lifting)

### Step 1: Add state for preview dot in DrawingView

Add these new properties near your existing touch-related variables:

```kotlin
/** Temporary preview dot position in bitmap space (shown during press if not dragging) */
private var previewDotPosition: Offset? = null
    set(value) {
        if (field != value) {
            field = value
            invalidate()
        }
    }
```

### Step 2: Update onTouchEvent to handle preview dot

Modify the relevant parts of `onTouchEvent`:

```kotlin
override fun onTouchEvent(event: MotionEvent): Boolean {
    scaleDetector.onTouchEvent(event)
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
            isGesturePanning = false
            isScaling = false

            // NEW: Show preview dot immediately at touch position
            previewDotPosition = screenToBitmap(sx, sy)

            return true
        }

        MotionEvent.ACTION_MOVE -> {
            if (isScaling) return true

            if (event.pointerCount > 1) {
                // your existing multi-finger pan code...
                previewDotPosition = null  // hide dot during multi-touch
                // ...
            } else if (event.pointerCount == 1) {
                val currentPt = screenToBitmap(event.x, event.y)

                if (isGesturePanning || isPanning) {
                    // pan code...
                    previewDotPosition = null  // no dot during pan
                } else {
                    // Check if we've exceeded tap slop → becomes drag
                    val dx = event.x - touchStartX
                    val dy = event.y - touchStartY
                    if (!isDrag && (dx * dx + dy * dy > tapSlopPx * tapSlopPx)) {
                        isDrag = true
                        previewDotPosition = null  // hide preview dot once dragging starts
                    }

                    if (!isDrag) {
                        // Still in "potential dot" state → update preview position
                        previewDotPosition = currentPt
                    }

                    dragPoints.add(currentPt)
                    currentStrokePoints = dragPoints.toList()
                }
                return true
            }
        }

        MotionEvent.ACTION_POINTER_DOWN -> {
            // your existing code...
            previewDotPosition = null  // multi-touch cancels dot preview
        }

        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            // Hide preview dot
            previewDotPosition = null

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
            isGesturePanning = false
            return true
        }
    }
    return super.onTouchEvent(event)
}
```

### Step 3: Draw the preview dot in onDraw

Add this block **after** drawing the current stroke preview (so the dot appears above layers but below the stroke preview if dragging starts):

```kotlin
// Preview dot while finger is down (only if not dragging yet)
previewDotPosition?.let { pos ->
    val paint = Paint().apply {
        isAntiAlias = true
        color = strokePreviewColor
        style = Paint.Style.FILL
        alpha = if (strokePreviewIsEraser) 128 else 255  // semi-transparent for eraser preview
    }

    // Dot size matches current brush size (in screen space for consistent feel)
    val dotRadiusScreen = strokePreviewWidth / 2f

    val screenX = panX + scale * pos.x
    val screenY = panY + scale * pos.y

    canvas.drawCircle(screenX, screenY, dotRadiusScreen, paint)
}
```

### Step 4: Draw the actual dot on lift (in onTap callback)

In your `AndroidView` `update` block, inside `view.onTap = { bx, by -> ... }`:

```kotlin
DrawTool.Pen, DrawTool.Pencil, DrawTool.MarkerPen, DrawTool.Eraser -> {
    if (currentLayerIndex !in layerStates.indices) return@onTap
    val layer = layerStates[currentLayerIndex]

    // Dot diameter = current brush size in bitmap space
    val dotDiameterBitmap = strokeSizePx / scale
    val dotRadiusBitmap = dotDiameterBitmap / 2f.coerceAtLeast(1.5f)  // avoid tiny dots when zoomed out

    val dotColor = when (selectedTool) {
        DrawTool.Pen       -> selectedColor
        DrawTool.Pencil    -> selectedColor.copy(alpha = PENCIL_ALPHA)
        DrawTool.MarkerPen -> selectedColor.copy(alpha = 0.6f)
        DrawTool.Eraser    -> Color.Transparent
        else -> return@onTap
    }

    val canvas = android.graphics.Canvas(layer.bitmap)
    val paint = Paint().apply {
        isAntiAlias = true
        color = dotColor.toArgb()
        style = Paint.Style.FILL
        if (selectedTool == DrawTool.Eraser) {
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
        }
    }

    canvas.drawCircle(bx, by, dotRadiusBitmap, paint)

    // Undo support (snapshot before change)
    undoStack.add(UndoEntry.Fill(currentLayerIndex, Bitmap.createBitmap(layer.bitmap)))
    redoStack.clear()

    saveAllLayers()
    canvasRefreshTrigger++
}
```

### Summary – What you get

- **Finger down** → small filled circle preview appears immediately at touch point (size = current brush width in screen pixels)
- **If you drag** (beyond tapSlopPx) → preview dot disappears, normal stroke preview begins
- **If you lift without dragging** → final filled dot is drawn on the bitmap with exactly the same size as your current brush
- **Eraser** → preview is semi-transparent red (or whatever), final dot clears a circle of brush size
- **Zoom aware** → dot size stays visually consistent relative to brush

This gives the natural feel most drawing apps provide: tap = dot of brush size, drag = stroke.

Let me know if you want to adjust:
- Dot opacity during preview
- Minimum dot size
- Different shape (e.g. square for certain tools)
- Or if the preview feels too jumpy on some devices

----

Here are the most important **considerations** and **best practices** to make sure **dot drawing** (single tap / short press placing a dot) does **not interfere** with panning, zooming, drawing strokes, or any other gesture in your current setup.

### 1. Gesture Priority & Conflict Resolution (most important)

| Gesture              | Fingers | Priority / When it should win                  | How to prevent interference with dot |
|----------------------|---------|------------------------------------------------|---------------------------------------|
| Pinch zoom           | 2+      | Highest priority                               | Multi-touch → immediately cancel/hide preview dot |
| Two-finger pan       | 2+      | High (when not pinching)                       | Same: multi-touch cancels dot preview |
| Single-finger pan    | 1       | When `isPanning` or `isGesturePanning` true    | Explicitly check tool/mode before showing dot |
| Normal stroke        | 1       | When movement > tapSlopPx                      | Dot preview disappears as soon as drag starts |
| Dot (tap)            | 1       | Lowest — only when no movement + drawing tool  | Only commit on UP if !isDrag && !pan && !scale |

**Implementation checklist**:

- Never show or commit dot in pan mode (`isPanning == true`)
- Never show or commit dot after multi-touch has occurred (`isGesturePanning == true`)
- Never show or commit dot after scaling has been detected (`isScaling == true`)
- Hide preview dot the moment any drag is detected (`isDrag = true`)
- Hide preview dot on any `ACTION_POINTER_DOWN` (second finger appears)

Your current code already does most of this — just make sure these conditions are consistently applied in both preview and commit logic.

### 2. Tool / Mode Filtering (critical)

Only allow dots for **drawing tools**, never for:

- `DrawTool.Pan`
- `DrawTool.Eyedropper` (already handled)
- `DrawTool.Fill` (already handled — fill is different)

Example safe guard in `onTap`:

```kotlin
if (selectedTool !in listOf(DrawTool.Pen, DrawTool.Pencil, DrawTool.MarkerPen, DrawTool.Eraser)) {
    return@onTap
}
```

And in preview logic (inside `onDraw` or touch handling):

```kotlin
if (selectedTool == DrawTool.Pan || isPanning || isGesturePanning || isScaling) {
    previewDotPosition = null
    return
}
```

### 3. Timing & State Transitions

Common sources of interference:

| Situation                              | Problem that can occur                           | Prevention strategy                              |
|----------------------------------------|--------------------------------------------------|--------------------------------------------------|
| User starts pan → quickly taps         | Dot appears briefly or commits                   | Check `isPanning` / `isGesturePanning` every frame |
| User zooms → taps immediately after    | Dot commits at wrong scale/position              | Clear `previewDotPosition` on `onScaleEnd`       |
| Very fast tap (down → up < 50 ms)      | Sometimes counted as drag due to noise           | Lower tapSlopPx slightly (18–22 px) or add min press duration (~60 ms) |
| Tap while finger is still moving slightly (shaky hand) | Dot not placed, stroke starts instead            | Increase tapSlopPx tolerance to 28–32 px         |
| Second finger touches during long press| Dot preview stays, then disappears               | Force `previewDotPosition = null` in `POINTER_DOWN` |

### 4. Coordinate Space Consistency

- **Preview dot** is drawn in **screen space** (`panX + scale * pos.x`, radius = `strokePreviewWidth / 2f`)
- **Final dot** is drawn in **bitmap space** (radius = `strokeSizePx / scale / 2f`)

This is correct — but you must be very careful:

- Never use screen-space radius for final dot (would create inconsistently sized dots when zoomed)
- Never forget to divide by `scale` when calculating final radius

### 5. Undo & History Safety

- Dot is basically a tiny "fill" operation → treat it like a fill in undo stack (`UndoEntry.Fill`)
- Do **not** add dot as a `Stroke` unless you want it editable later
- Make sure snapshot is taken **before** drawing the circle

```kotlin
val bitmapBefore = Bitmap.createBitmap(layer.bitmap) // ← before drawCircle
canvas.drawCircle(bx, by, radius, paint)
undoStack.add(UndoEntry.Fill(currentLayerIndex, bitmapBefore))
```

### 6. Visual & UX Polish (to avoid feeling broken)

- Make preview dot slightly transparent (alpha 0.6–0.8) so user sees it's not final yet
- Use same anti-aliasing and cap/join logic as normal strokes
- For eraser: show preview as semi-transparent white or checkerboard circle (or keep red dashed)
- Add very short **debounce / min-press-time** (optional ~80–120 ms) to avoid accidental dots on fast movements

Example:

```kotlin
private var downTimeMs: Long = 0

// in ACTION_DOWN
downTimeMs = System.currentTimeMillis()

// in ACTION_UP
if (System.currentTimeMillis() - downTimeMs < 80) {
    // ignore very fast taps (accidental)
    return true
}
```

### Quick Summary — Must-Have Guards

In code order of importance:

1. `if (isScaling || isGesturePanning || isPanning) { previewDotPosition = null }`
2. `if (selectedTool == DrawTool.Pan) return` (in both preview & commit)
3. `previewDotPosition = null` on `ACTION_POINTER_DOWN`, on drag start, on scale end
4. Only commit dot if `!isDrag && dragPoints.size == 1` (or very small movement)
5. Use correct coordinate space & size for preview vs final dot

If you apply these five rules consistently, dots should feel completely non-intrusive and never conflict with panning, zooming, or normal drawing.

Let me know which of these you are most worried about — I can give more targeted code snippets.
To implement an **oil paint brush** with a **smearing / wet blending** effect in your current architecture, the key change is in **how the stroke is rendered** — not in touch collection, intents, or actions.

Your existing flow (touch → points → `StrokeIntent` → `StrokeDrawingAction` → `StrokeRenderer.renderStroke(bitmap, stroke)`) remains almost unchanged.

### Step 1: Update data model

Add the new tool enum value:

```kotlin
// Stroke.kt
enum class DrawTool {
    Pen, Pencil, MarkerPen, Eraser, Fill, Eyedropper, Pan,
    OilPaint     // ← add this
}
```

Optionally (recommended), add parameters that control the oil behavior. You can put them in `Stroke` (per-stroke) or in `ToolSettings` (global while tool is active). Example:

```kotlin
// Stroke.kt (add fields)
data class Stroke(
    // ... existing fields
    val oilSmearStrength: Float = 0.7f,     // 0.0 = almost no pickup, 1.0 = heavy smearing
    val oilPressureInfluence: Float = 0.0f, // if you later read pressure
    // ...
)
```

For the first version you can hard-code them or read from `ToolSettings`.

### Step 2: Create the tool class

```kotlin
// OilPaintTool.kt
package com.example.drawingapp.tools

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.drawingapp.data.DrawTool
import com.example.drawingapp.data.StrokeCapStyle
import com.example.drawingapp.util.smoothStrokePoints   // if you want smoothing

class OilPaintTool : DrawingTool {
    override val id: String = "oilpaint"
    override val displayName: String = "Oil Paint"
    override val iconRes: Int = R.drawable.ic_oil_paint   // add your icon
    override val drawTool: DrawTool = DrawTool.OilPaint

    override fun transformColor(baseColor: Color, settings: ToolSettings): Color = baseColor

    override fun createAction(
        points: List<Offset>,
        settings: ToolSettings,
        context: DrawingContext
    ): StrokeIntent? {
        if (points.size < 2) return null

        val pointsToUse = if (settings.smoothingEnabled) smoothStrokePoints(points) else points

        val closed = settings.closingEnabled && shouldAutoClose(pointsToUse, ToolConstants.CLOSE_THRESHOLD_PX)

        return StrokeIntent(
            points = pointsToUse,
            color = transformColor(settings.color, settings),
            strokeWidth = settings.strokeWidth / context.scale,
            strokeCapStyle = settings.strokeCapStyle,   // most oil brushes use ROUND
            closed = closed,
            drawTool = drawTool
            // If you added extra fields to StrokeIntent, set them here
        )
    }

    // supportsContinuousDrawing = true (default is fine)
}
```

Register it:

```kotlin
// ToolRegistry.kt
registry.register(OilPaintTool())
```

### Step 3: The important part — realistic smear in `StrokeRenderer`

The current `renderStroke` draws a simple path. For oil paint we need to **stamp small dabs along the path**, sampling existing color under each dab, blending it with the stroke color, and writing back.

This is the classic "impostor" oil/smudge brush used in many mobile painting apps.

```kotlin
// StrokeRenderer.kt — replace or extend the existing renderStroke function

import android.graphics.*
import kotlin.math.hypot
import kotlin.math.max
import kotlin.random.Random

fun renderStroke(bitmap: Bitmap?, stroke: Stroke) {
    val bmp = bitmap ?: return
    if (!bmp.isMutable) return  // safety

    val canvas = Canvas(bmp)

    when (stroke.tool) {
        DrawTool.OilPaint -> renderOilPaintStroke(canvas, stroke, bmp)
        else -> renderNormalStroke(canvas, stroke)   // ← move your current path drawing logic here
    }
}

private fun renderNormalStroke(canvas: Canvas, stroke: Stroke) {
    // Your existing path + paint code goes here (the one with drawPath)
    val paint = Paint().apply {
        color = stroke.color.toArgb()
        style = Paint.Style.STROKE
        strokeWidth = stroke.strokeWidth
        isAntiAlias = true
        strokeJoin = if (stroke.strokeCapStyle == StrokeCapStyle.ROUND) Paint.Join.ROUND else Paint.Join.BEVEL
        strokeCap = if (stroke.strokeCapStyle == StrokeCapStyle.ROUND) Paint.Cap.ROUND else Paint.Cap.BUTT
        // eraser xfermode etc.
    }
    val path = Path()
    // ... build path from stroke.points (your existing code)
    canvas.drawPath(path, paint)
}

private fun renderOilPaintStroke(canvas: Canvas, stroke: Stroke, targetBitmap: Bitmap) {
    if (stroke.points.size < 2) return

    val brushRadius = (stroke.strokeWidth / 2f).toInt().coerceAtLeast(4)
    val half = brushRadius
    val smearStrength = 0.68f          // tune: higher = more pickup of old color
    val addNewColorStrength = 0.40f    // how much of stroke.color is added

    val paint = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
        style = Paint.Style.FILL
    }

    val tmpBuffer = IntArray((brushRadius * 2 + 1).let { it * it })  // reuse if possible

    stroke.points.windowed(size = 2, step = 1).forEach { (prev, curr) ->
        val dx = curr.x - prev.x
        val dy = curr.y - prev.y
        val dist = hypot(dx, dy).toFloat()

        if (dist < 1f) return@forEach

        val steps = max(1, (dist / (brushRadius * 0.7f)).toInt())   // adaptive density

        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val x = (prev.x + dx * t).toInt()
            val y = (prev.y + dy * t).toInt()

            // Optional: add small random offset for organic feel
            val jitterX = x + Random.nextInt(-2, 3)
            val jitterY = y + Random.nextInt(-2, 3)

            // Sample rectangle under brush
            val left   = (jitterX - half).coerceIn(0, targetBitmap.width - 1)
            val top    = (jitterY - half).coerceIn(0, targetBitmap.height - 1)
            val w      = (brushRadius * 2 + 1).coerceAtMost(targetBitmap.width - left)
            val h      = (brushRadius * 2 + 1).coerceAtMost(targetBitmap.height - top)

            targetBitmap.getPixels(tmpBuffer, 0, w, left, top, w, h)

            // Compute average color under brush (simple mean)
            var r = 0L; var g = 0L; var b = 0L; var count = 0
            for (px in tmpBuffer) {
                if (android.graphics.Color.alpha(px) > 30) {
                    r += android.graphics.Color.red(px)
                    g += android.graphics.Color.green(px)
                    b += android.graphics.Color.blue(px)
                    count++
                }
            }

            val picked = if (count > 0) {
                android.graphics.Color.argb(
                    255,
                    (r / count).toInt().coerceIn(0,255),
                    (g / count).toInt().coerceIn(0,255),
                    (b / count).toInt().coerceIn(0,255)
                )
            } else {
                stroke.color.toArgb()
            }

            // Blend: picked * smearStrength + new color * (1 - smearStrength)
            val finalArgb = blendArgb(picked, stroke.color.toArgb(), smearStrength)

            paint.color = finalArgb

            // Optional: lower alpha for softer buildup
            paint.alpha = (180..240).random()

            // Stamp a soft circle (or load a brush texture bitmap and draw it)
            canvas.drawCircle(jitterX.toFloat(), jitterY.toFloat(), brushRadius.toFloat(), paint)
        }
    }
}

private fun blendArgb(c1: Int, c2: Int, ratioTowardC2: Float): Int {
    val ir = (1 - ratioTowardC2).coerceIn(0f,1f)
    val r = (android.graphics.Color.red(c1) * ir + android.graphics.Color.red(c2) * ratioTowardC2).toInt()
    val g = (android.graphics.Color.green(c1) * ir + android.graphics.Color.green(c2) * ratioTowardC2).toInt()
    val b = (android.graphics.Color.blue(c1) * ir + android.graphics.Color.blue(c2) * ratioTowardC2).toInt()
    return android.graphics.Color.argb(255, r.coerceIn(0,255), g.coerceIn(0,255), b.coerceIn(0,255))
}
```

### Quick tuning tips

- **Performance** — if slow: reduce steps (increase step divisor), sample fewer points (e.g. 9 fixed positions instead of full rect), or lower brushRadius.
- **Better look** — replace `drawCircle` with a soft round brush texture (pre-load `Bitmap` with alpha, use `canvas.drawBitmap(texture, matrix, paint)`).
- **More realism** — add directionality: rotate brush shape along (dx,dy), sample slightly behind finger position.
- **Pressure** — if you collect pressure from `MotionEvent`, scale `brushRadius` and `smearStrength`.

This gives a convincing wet oil smear: new strokes pick up and drag existing colors while depositing some new paint.

Start with hard-coded values, then expose `smearStrength`, `brushTexture`, etc. in settings.

If performance becomes an issue on old devices, consider limiting oil-paint strokes to lower resolution or use a temporary layer for each stroke. 

ICON:

<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 576 512"><!--!Font Awesome Free v7.2.0 by @fontawesome - https://fontawesome.com License - https://fontawesome.com/license/free Copyright 2026 Fonticons, Inc.--><path d="M480.5 10.3L259.1 158c-29.1 19.4-47.6 50.9-50.6 85.3 62.3 12.8 111.4 61.9 124.3 124.3 34.5-3 65.9-21.5 85.3-50.6L565.7 95.5c6.7-10.1 10.3-21.9 10.3-34.1 0-33.9-27.5-61.4-61.4-61.4-12.1 0-24 3.6-34.1 10.3zM288 400c0-61.9-50.1-112-112-112S64 338.1 64 400c0 3.9 .2 7.8 .6 11.6 1.8 17.5-10.2 36.4-27.8 36.4L32 448c-17.7 0-32 14.3-32 32s14.3 32 32 32l144 0c61.9 0 112-50.1 112-112z"/></svg>
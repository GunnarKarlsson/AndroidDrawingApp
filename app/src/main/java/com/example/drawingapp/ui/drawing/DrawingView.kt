package com.example.drawingapp.ui.drawing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.compose.ui.geometry.Offset

/**
 * Custom View that renders layer bitmaps with pan offset and handles touch for pan vs draw/tap.
 * Bitmap(s) stay fixed in memory; pan moves the "camera" via panX/panY.
 * All callbacks use bitmap-space coordinates (screen position transformed by inverse matrix).
 */
class DrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs) {

    /** Layer bitmaps to draw (order: index 0 = bottom). */
    var layerBitmaps: List<Bitmap> = emptyList()
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** For each layer, true if the layer should be skipped when transparent (no content). */
    var layerTransparent: List<Boolean> = emptyList()
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** Index of the current layer (drawn in order with others; used for stroke preview ordering). */
    var currentLayerIndex: Int = 0
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** Background color for the canvas (ARGB). Does not conflict with View.setBackgroundColor. */
    var canvasBackgroundColor: Int = 0xFFFFFFFF.toInt()
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var panX: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }
    var panY: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var isPanning: Boolean = false

    /** Current zoom level (1f = 100%). */
    var scale: Float = 1f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val MIN_SCALE = 0.25f
    private val MAX_SCALE = 8f
    private val ZOOM_STEP = 0.25f

    private val matrix = Matrix()
    private val inverseMatrix = Matrix()
    private val screenToBitmapSrc = floatArrayOf(0f, 0f)
    private val screenToBitmapDst = floatArrayOf(0f, 0f)

    /** In-progress stroke points in bitmap space; drawn as preview. Cleared on pointer UP. */
    var currentStrokePoints: List<Offset> = emptyList()
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** Preview stroke color (ARGB). */
    var strokePreviewColor: Int = 0xFF000000.toInt()
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }
    var strokePreviewWidth: Float = 20f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }
    var strokePreviewCapRound: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }
    var strokePreviewIsEraser: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** Called when a stroke is finished (points in bitmap space, strokeWidth in bitmap pixels so zoomed-in strokes are thinner). */
    var onStrokeDrawn: ((List<Offset>, strokeWidthBitmap: Float) -> Unit)? = null

    /** Called when a tap is detected (x, y in bitmap space). */
    var onTap: ((Float, Float) -> Unit)? = null

    private var touchStartX: Float = 0f
    private var touchStartY: Float = 0f
    private var lastMoveX: Float = 0f
    private var lastMoveY: Float = 0f
    private val dragPoints = mutableListOf<Offset>()
    private var isDrag: Boolean = false
    private val tapSlopPx: Float = 24f // treat as tap if movement under this

    private var isGesturePanning: Boolean = false
    private var lastAvgX: Float = 0f
    private var lastAvgY: Float = 0f

    private var isScaling: Boolean = false
    private var focusX: Float = 0f
    private var focusY: Float = 0f
    private val scaleDetector: ScaleGestureDetector =
        ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                isScaling = true
                focusX = detector.focusX
                focusY = detector.focusY
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val factor = detector.scaleFactor
                panX -= focusX
                panY -= focusY
                scale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
                panX += focusX
                panY += focusY
                focusX = detector.focusX
                focusY = detector.focusY
                clampPan()
                invalidate()
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                isScaling = false
            }
        })

    private val bgPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = false
    }
    private val strokePreviewPaint = Paint().apply {
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        canvas.save()
        canvas.clipRect(0f, 0f, w, h)

        // Background (full view, then content may be panned over it)
        bgPaint.color = canvasBackgroundColor
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        val layers = layerBitmaps
        val transparent = layerTransparent
        if (layers.isEmpty()) return

        matrix.reset()
        matrix.postScale(scale, scale)
        matrix.postTranslate(panX, panY)

        val n = layers.size
        val currentIdx = currentLayerIndex.coerceIn(0, n - 1)

        // Layers below current
        for (i in 0 until currentIdx) {
            if (i in layers.indices && (i >= transparent.size || !transparent[i])) {
                layers[i].let { bmp -> canvas.drawBitmap(bmp, matrix, null) }
            }
        }
        // Current layer bitmap
        if (currentIdx in layers.indices && (currentIdx >= transparent.size || !transparent[currentIdx])) {
            canvas.drawBitmap(layers[currentIdx], matrix, null)
        }
        // Current stroke preview in screen space (above current layer, below layers above)
        if (currentStrokePoints.isNotEmpty()) {
            path.rewind()
            currentStrokePoints.forEachIndexed { index, o ->
                val sx = panX + scale * o.x
                val sy = panY + scale * o.y
                if (index == 0) path.moveTo(sx, sy)
                else path.lineTo(sx, sy)
            }
            strokePreviewPaint.strokeWidth = strokePreviewWidth
            strokePreviewPaint.strokeCap = if (strokePreviewCapRound) Paint.Cap.ROUND else Paint.Cap.BUTT
            strokePreviewPaint.strokeJoin = if (strokePreviewCapRound) Paint.Join.ROUND else Paint.Join.BEVEL
            strokePreviewPaint.pathEffect = null
            if (strokePreviewIsEraser) {
                strokePreviewPaint.color = 0x80FF0000.toInt() // red, 50% alpha
                strokePreviewPaint.pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
            } else {
                strokePreviewPaint.color = strokePreviewColor
            }
            canvas.drawPath(path, strokePreviewPaint)
        }
        // Layers above current
        for (i in (currentIdx + 1) until n) {
            if (i in layers.indices && (i >= transparent.size || !transparent[i])) {
                layers[i].let { bmp -> canvas.drawBitmap(bmp, matrix, null) }
            }
        }

        canvas.restore()
    }

    /** Maps screen coordinates to bitmap-space coordinates using the inverse transform. */
    private fun screenToBitmap(sx: Float, sy: Float): Offset {
        matrix.reset()
        matrix.postScale(scale, scale)
        matrix.postTranslate(panX, panY)
        screenToBitmapSrc[0] = sx
        screenToBitmapSrc[1] = sy
        inverseMatrix.reset()
        matrix.invert(inverseMatrix)
        inverseMatrix.mapPoints(screenToBitmapDst, screenToBitmapSrc)
        return Offset(screenToBitmapDst[0], screenToBitmapDst[1])
    }

    fun zoomIn() {
        zoomByFactor(1f + ZOOM_STEP, width / 2f, height / 2f)
    }

    fun zoomOut() {
        zoomByFactor(1f - ZOOM_STEP, width / 2f, height / 2f)
    }

    private fun zoomByFactor(factor: Float, focusX: Float, focusY: Float) {
        val oldScale = scale
        scale *= factor
        scale = scale.coerceIn(MIN_SCALE, MAX_SCALE)
        if (scale == oldScale) return
        val scaleRatio = scale / oldScale
        panX = panX * scaleRatio - focusX * (scaleRatio - 1f)
        panY = panY * scaleRatio - focusY * (scaleRatio - 1f)
        clampPan()
        invalidate()
    }

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
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount > 1) {
                    isGesturePanning = true
                    if (!isPanning && dragPoints.isNotEmpty()) {
                        dragPoints.clear()
                        currentStrokePoints = emptyList()
                        isDrag = false
                    }
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
                if (isScaling) return true
                if (event.pointerCount > 1) {
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
                    if (isGesturePanning || isPanning) {
                        val dx = event.x - lastMoveX
                        val dy = event.y - lastMoveY
                        panX += dx
                        panY += dy
                        clampPan()
                        invalidate()
                        lastMoveX = event.x
                        lastMoveY = event.y
                    } else {
                        val pt = screenToBitmap(event.x, event.y)
                        dragPoints.add(pt)
                        if (!isDrag && (event.x - touchStartX).let { dx -> (event.y - touchStartY).let { dy -> dx * dx + dy * dy > tapSlopPx * tapSlopPx } }) {
                            isDrag = true
                        }
                        currentStrokePoints = dragPoints.toList()
                    }
                    return true
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount > 1) {
                    val upIndex = event.actionIndex
                    if (event.pointerCount == 2) {
                        val remainingIndex = if (upIndex == 0) 1 else 0
                        lastMoveX = event.getX(remainingIndex)
                        lastMoveY = event.getY(remainingIndex)
                        lastAvgX = lastMoveX
                        lastAvgY = lastMoveY
                    } else {
                        var sumX = 0f
                        var sumY = 0f
                        var count = 0
                        for (i in 0 until event.pointerCount) {
                            if (i != upIndex) {
                                sumX += event.getX(i)
                                sumY += event.getY(i)
                                count++
                            }
                        }
                        if (count > 0) {
                            lastAvgX = sumX / count
                            lastAvgY = sumY / count
                            lastMoveX = lastAvgX
                            lastMoveY = lastAvgY
                        }
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
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun clampPan() {
        val layers = layerBitmaps
        if (layers.isEmpty()) return
        val bmpW = layers.maxOfOrNull { it.width }?.toFloat() ?: 0f
        val bmpH = layers.maxOfOrNull { it.height }?.toFloat() ?: 0f
        val vw = width.toFloat()
        val vh = height.toFloat()
        val minPanX = vw - bmpW * scale
        val minPanY = vh - bmpH * scale
        // Only coerce when range is valid (min <= max); otherwise leave pan unchanged to avoid IllegalArgumentException.
        if (minPanX <= 0f) panX = panX.coerceIn(minPanX, 0f)
        if (minPanY <= 0f) panY = panY.coerceIn(minPanY, 0f)
    }
}

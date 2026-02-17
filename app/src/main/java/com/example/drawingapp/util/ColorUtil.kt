package com.example.drawingapp.util

import android.graphics.Bitmap

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Utility functions for color manipulation and pixel operations.
 */
object ColorUtil {
    /**
     * Converts HSV color space to Compose Color.
     * @param hue Hue in degrees (0-360)
     * @param saturation Saturation (0-1)
     * @param value Value/Brightness (0-1)
     * @return Compose Color object
     */
    fun colorFromHsv(hue: Float, saturation: Float, value: Float): Color {
        val h = hue.coerceIn(0f, 360f) / 60f
        val s = saturation.coerceIn(0f, 1f)
        val v = value.coerceIn(0f, 1f)
        val c = v * s
        val x = c * (1 - abs(h % 2f - 1f))
        val m = v - c
        val (r, g, b) = when {
            h < 1 -> Triple(c, x, 0f)
            h < 2 -> Triple(x, c, 0f)
            h < 3 -> Triple(0f, c, x)
            h < 4 -> Triple(0f, x, c)
            h < 5 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Color(
            red = (r + m).coerceIn(0f, 1f),
            green = (g + m).coerceIn(0f, 1f),
            blue = (b + m).coerceIn(0f, 1f),
            alpha = 1f
        )
    }

    /**
     * Returns HSV value (brightness) (0f..1f) for the given color, for brightness slider position.
     * @param color Compose Color
     * @return HSV value component (0-1)
     */
    fun colorToHsvValue(color: Color): Float {
        val argb = color.toArgb()
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(
            android.graphics.Color.red(argb),
            android.graphics.Color.green(argb),
            android.graphics.Color.blue(argb),
            hsv
        )
        return hsv[2]
    }

    /**
     * Converts ARGB integer to Compose Color.
     * @param argb ARGB color as integer
     * @return Compose Color object
     */
    fun argbToColor(argb: Int): Color = Color(argb)

    /**
     * Calculates RGB Euclidean distance between two ARGB colors.
     * Simple RGB Euclidean distance (0–441 max).
     * @param argb1 First color as ARGB integer
     * @param argb2 Second color as ARGB integer
     * @return Distance value (0-441)
     */
    fun colorDistance(argb1: Int, argb2: Int): Float {
        val r1 = (argb1 shr 16) and 0xFF
        val g1 = (argb1 shr 8) and 0xFF
        val b1 = argb1 and 0xFF
        val r2 = (argb2 shr 16) and 0xFF
        val g2 = (argb2 shr 8) and 0xFF
        val b2 = argb2 and 0xFF
        val dr = r1 - r2
        val dg = g1 - g2
        val db = b1 - b2
        return sqrt((dr * dr + dg * dg + db * db).toDouble()).toFloat()
    }

    /**
     * Extracts pixel color from bitmap at specified coordinates.
     * Coordinates are automatically clamped to valid bitmap bounds.
     * @param bitmap Source bitmap
     * @param x X coordinate
     * @param y Y coordinate
     * @return Compose Color object
     */
    fun getPixelColor(bitmap: Bitmap, x: Int, y: Int): Color {
        val xSafe = x.coerceIn(0, bitmap.width - 1)
        val ySafe = y.coerceIn(0, bitmap.height - 1)
        return argbToColor(bitmap.getPixel(xSafe, ySafe))
    }

    /**
     * Scanline flood fill on a locked int[] pixel array. Faster than per-pixel queue fill.
     * Supports color tolerance (Euclidean RGB). 4-connected. Stack-based, one seed per run above/below.
     * @param bitmap Bitmap to fill
     * @param startX Starting X coordinate
     * @param startY Starting Y coordinate
     * @param fillColorArgb Fill color as ARGB integer
     * @param tolerance Color tolerance for matching (default 0f)
     */
    fun floodFill(
        bitmap: Bitmap,
        startX: Int,
        startY: Int,
        fillColorArgb: Int,
        tolerance: Float = 0f
    ) {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return
        val x0 = startX.coerceIn(0, w - 1)
        val y0 = startY.coerceIn(0, h - 1)

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val targetColor = pixels[y0 * w + x0]
        if (colorDistance(targetColor, fillColorArgb) <= tolerance) return

        val visited = BooleanArray(w * h)
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.add(x0 to y0)
        visited[y0 * w + x0] = true

        while (stack.isNotEmpty()) {
            val (x, y) = stack.removeLast()

            var lx = x
            while (lx >= 0 && colorDistance(pixels[y * w + lx], targetColor) <= tolerance) {
                lx--
            }
            lx++

            var rx = x
            while (rx < w && colorDistance(pixels[y * w + rx], targetColor) <= tolerance) {
                rx++
            }
            rx--

            for (i in lx..rx) {
                pixels[y * w + i] = fillColorArgb
                visited[y * w + i] = true
            }

            val above = y - 1
            val below = y + 1

            if (above >= 0) {
                var sx = lx
                while (sx <= rx) {
                    val idx = above * w + sx
                    if (!visited[idx] && colorDistance(pixels[idx], targetColor) <= tolerance) {
                        visited[idx] = true
                        stack.add(sx to above)
                        sx++
                        while (sx <= rx && colorDistance(pixels[above * w + sx], targetColor) <= tolerance) {
                            sx++
                        }
                    } else {
                        sx++
                    }
                }
            }

            if (below < h) {
                var sx = lx
                while (sx <= rx) {
                    val idx = below * w + sx
                    if (!visited[idx] && colorDistance(pixels[idx], targetColor) <= tolerance) {
                        visited[idx] = true
                        stack.add(sx to below)
                        sx++
                        while (sx <= rx && colorDistance(pixels[below * w + sx], targetColor) <= tolerance) {
                            sx++
                        }
                    } else {
                        sx++
                    }
                }
            }
        }

        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    }
}

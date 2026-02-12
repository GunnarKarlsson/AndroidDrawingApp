package com.example.drawingapp.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import java.io.File
import java.io.FileOutputStream

private const val PREFS_NAME = "drawing_app"
private const val KEY_PAGE_IDS = "page_ids"
private const val KEY_STROKE_SIZE_PX = "stroke_size_px"
private const val KEY_STROKE_COLOR_ARGB = "stroke_color_argb"
private const val PAGES_DIR = "pages"
private const val META_FILE = "meta.json"

class LocalPageStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val pagesDir = File(context.filesDir, PAGES_DIR).also { it.mkdirs() }

    fun loadPageIds(): List<String> {
        val ids = prefs.getStringSet(KEY_PAGE_IDS, null) ?: return emptyList()
        return ids.toList().sorted()
    }

    fun savePageIds(ids: List<String>) {
        prefs.edit().putStringSet(KEY_PAGE_IDS, ids.toSet()).apply()
    }

    fun loadStrokeSizePx(defaultValue: Float): Float =
        prefs.getFloat(KEY_STROKE_SIZE_PX, defaultValue)

    fun saveStrokeSizePx(value: Float) {
        prefs.edit().putFloat(KEY_STROKE_SIZE_PX, value).apply()
    }

    fun loadStrokeColorArgb(defaultValue: Int): Int =
        prefs.getInt(KEY_STROKE_COLOR_ARGB, defaultValue)

    fun saveStrokeColorArgb(value: Int) {
        prefs.edit().putInt(KEY_STROKE_COLOR_ARGB, value).apply()
    }

    /** Legacy: load single bitmap (first layer or old pageId.png). */
    fun loadPageBitmap(pageId: String): Bitmap? {
        val pageDir = File(pagesDir, pageId)
        return if (pageDir.exists()) {
            val layer0 = File(pageDir, "layer_0.png")
            if (layer0.exists()) BitmapFactory.decodeFile(layer0.absolutePath) else null
        } else {
            val file = File(pagesDir, "$pageId.png")
            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        }
    }

    /** Legacy: save as single layer (for backward compat). */
    fun savePageBitmap(pageId: String, bitmap: Bitmap) {
        savePageLayers(pageId, listOf(bitmap))
    }

    fun loadPageLayers(pageId: String): List<Bitmap?> {
        val pageDir = File(pagesDir, pageId)
        return if (pageDir.exists()) {
            val meta = PageMeta.fromFile(File(pageDir, META_FILE)) ?: PageMeta(layerCount = 1)
            (0 until meta.layerCount).map { index ->
                val file = File(pageDir, "layer_$index.png")
                if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
            }
        } else {
            val legacy = File(pagesDir, "$pageId.png")
            if (legacy.exists()) listOf(BitmapFactory.decodeFile(legacy.absolutePath))
            else listOf(null)
        }
    }

    fun loadPageBackgroundColor(pageId: String): Int {
        val pageDir = File(pagesDir, pageId)
        if (!pageDir.exists()) return 0xFFFFFFFF.toInt()
        val meta = PageMeta.fromFile(File(pageDir, META_FILE)) ?: return 0xFFFFFFFF.toInt()
        return meta.backgroundColor
    }

    fun savePageBackgroundColor(pageId: String, backgroundColor: Int) {
        try {
            val pageDir = File(pagesDir, pageId)
            if (!pageDir.exists()) return
            val meta = PageMeta.fromFile(File(pageDir, META_FILE)) ?: return
            File(pageDir, META_FILE).writeText(meta.copy(backgroundColor = backgroundColor).toJson())
        } catch (e: Exception) {
            Log.e("LocalPageStore", "Failed to save background color for $pageId", e)
        }
    }

    fun savePageLayers(pageId: String, bitmaps: List<Bitmap>, backgroundColor: Int = 0xFFFFFFFF.toInt()) {
        try {
            val pageDir = File(pagesDir, pageId)
            pageDir.mkdirs()
            File(pageDir, META_FILE).writeText(PageMeta(layerCount = bitmaps.size, backgroundColor = backgroundColor).toJson())
            bitmaps.forEachIndexed { index, bitmap ->
                FileOutputStream(File(pageDir, "layer_$index.png")).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }
            for (i in bitmaps.size until 10) {
                File(pageDir, "layer_$i.png").takeIf { it.exists() }?.delete()
            }
        } catch (e: Exception) {
            Log.e("LocalPageStore", "Failed to save layers for $pageId", e)
        }
    }

    /**
     * Load a composite thumbnail of the page (background + all layers).
     * This is a scaled-down version of the full drawing: we load the same layer PNGs,
     * composite them onto a single bitmap, and scale so the longest side fits in maxSize.
     * Using a too-small maxSize makes strokes (2–4px) become sub-pixel and look like dots.
     */
    fun loadPageThumbnail(pageId: String, maxSize: Int = 768): Bitmap? {
        val layers = loadPageLayers(pageId)
        val bgColor = loadPageBackgroundColor(pageId)
        val firstLayer = layers.firstOrNull { it != null } ?: return null
        val width = firstLayer.width
        val height = firstLayer.height
        if (width <= 0 || height <= 0) return null
        val scale = (maxSize.toFloat() / maxOf(width, height)).coerceAtMost(1f)
        val thumbWidth = (width * scale).toInt().coerceAtLeast(1)
        val thumbHeight = (height * scale).toInt().coerceAtLeast(1)
        val thumbnail = Bitmap.createBitmap(thumbWidth, thumbHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(thumbnail)
        canvas.drawColor(bgColor)
        val srcRect = Rect(0, 0, width, height)
        val dstRect = Rect(0, 0, thumbWidth, thumbHeight)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        layers.forEach { layer ->
            layer?.let { canvas.drawBitmap(it, srcRect, dstRect, paint) }
        }
        return thumbnail
    }
}

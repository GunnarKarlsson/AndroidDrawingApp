package com.example.drawingapp.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

private const val PREFS_NAME = "drawing_app"
private const val NOTEBOOKS_FILE = "notebooks.json"
private const val ASSIGNMENTS_FILE = "notebook_assignments.json"
private const val KEY_PAGE_IDS = "page_ids"
private const val KEY_PAGE_TITLE_PREFIX = "page_title_"
private const val KEY_STROKE_SIZE_PX = "stroke_size_px"
private const val KEY_STROKE_COLOR_ARGB = "stroke_color_argb"
private const val KEY_STROKE_CAP = "stroke_cap" // 0 = ROUND, 1 = BUTT
private const val KEY_CURVE_SMOOTHING = "curve_smoothing"
private const val KEY_CURVE_CLOSING = "curve_closing"
private const val KEY_FAVORITE_COLORS = "favorite_colors"
private const val PAGES_DIR = "pages"
private const val META_FILE = "meta.json"
private const val THUMBNAIL_FILE = "thumbnail.png"

class LocalPageStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val pagesDir = File(context.filesDir, PAGES_DIR).also { it.mkdirs() }
    private val notebooksFile = File(context.filesDir, NOTEBOOKS_FILE)
    private val assignmentsFile = File(context.filesDir, ASSIGNMENTS_FILE)

    fun loadPageIds(): List<String> {
        val ids = prefs.getStringSet(KEY_PAGE_IDS, null) ?: return emptyList()
        return ids.toList().sorted()
    }

    fun savePageIds(ids: List<String>) {
        prefs.edit().putStringSet(KEY_PAGE_IDS, ids.toSet()).apply()
    }

    fun loadPageTitle(pageId: String): String? =
        prefs.getString(KEY_PAGE_TITLE_PREFIX + pageId, null)

    fun savePageTitle(pageId: String, title: String) {
        prefs.edit().putString(KEY_PAGE_TITLE_PREFIX + pageId, title.trim()).apply()
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

    fun loadStrokeCap(roundDefault: Int = 0): Int =
        prefs.getInt(KEY_STROKE_CAP, roundDefault)

    fun saveStrokeCap(value: Int) {
        prefs.edit().putInt(KEY_STROKE_CAP, value).apply()
    }

    fun loadCurveSmoothing(defaultValue: Boolean): Boolean =
        prefs.getBoolean(KEY_CURVE_SMOOTHING, defaultValue)

    fun saveCurveSmoothing(value: Boolean) {
        prefs.edit().putBoolean(KEY_CURVE_SMOOTHING, value).apply()
    }

    fun loadCurveClosing(defaultValue: Boolean): Boolean =
        prefs.getBoolean(KEY_CURVE_CLOSING, defaultValue)

    fun saveCurveClosing(value: Boolean) {
        prefs.edit().putBoolean(KEY_CURVE_CLOSING, value).apply()
    }

    private val defaultFavoriteColorsArgb = listOf(-16777216, -1) // black, white

    fun loadFavoriteColorsArgb(): List<Int> {
        val raw = prefs.getString(KEY_FAVORITE_COLORS, null) ?: return defaultFavoriteColorsArgb
        val list = raw.split(",").mapNotNull { it.trim().toIntOrNull() }.distinct()
        return if (list.isEmpty()) defaultFavoriteColorsArgb else list
    }

    fun saveFavoriteColorsArgb(list: List<Int>) {
        prefs.edit().putString(KEY_FAVORITE_COLORS, list.joinToString(",")).apply()
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

    fun loadPageLayerMetas(pageId: String): List<LayerMeta>? {
        val pageDir = File(pagesDir, pageId)
        if (!pageDir.exists()) return null
        val meta = PageMeta.fromFile(File(pageDir, META_FILE)) ?: return null
        return meta.layers
    }

    fun loadPageBackgroundColor(pageId: String): Int {
        val pageDir = File(pagesDir, pageId)
        if (!pageDir.exists()) return 0xFFFFFFFF.toInt()
        val meta = PageMeta.fromFile(File(pageDir, META_FILE)) ?: return 0xFFFFFFFF.toInt()
        return meta.backgroundColor
    }

    fun loadPageCurrentLayerIndex(pageId: String): Int {
        val pageDir = File(pagesDir, pageId)
        if (!pageDir.exists()) return 0
        return PageMeta.fromFile(File(pageDir, META_FILE))?.currentLayerIndex ?: 0
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

    fun savePageCurrentLayerIndex(pageId: String, index: Int) {
        try {
            val pageDir = File(pagesDir, pageId)
            if (!pageDir.exists()) return
            val meta = PageMeta.fromFile(File(pageDir, META_FILE)) ?: return
            File(pageDir, META_FILE).writeText(meta.copy(currentLayerIndex = index).toJson())
        } catch (e: Exception) {
            Log.e("LocalPageStore", "Failed to save current layer index for $pageId", e)
        }
    }

    fun savePageLayers(pageId: String, bitmaps: List<Bitmap>, backgroundColor: Int = 0xFFFFFFFF.toInt(), layerMetas: List<LayerMeta>? = null, currentLayerIndex: Int? = null) {
        try {
            val pageDir = File(pagesDir, pageId)
            pageDir.mkdirs()
            val existingMeta = PageMeta.fromFile(File(pageDir, META_FILE))
            val lastModified = System.currentTimeMillis()
            val thumbnailTimestamp = existingMeta?.thumbnailTimestamp ?: 0L
            val layerMetasToSave = layerMetas?.take(bitmaps.size)
            val currentLayer = currentLayerIndex ?: existingMeta?.currentLayerIndex ?: 0
            File(pageDir, META_FILE).writeText(PageMeta(layerCount = bitmaps.size, backgroundColor = backgroundColor, layers = layerMetasToSave, lastModified = lastModified, thumbnailTimestamp = thumbnailTimestamp, currentLayerIndex = currentLayer).toJson())
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
     * Load thumbnail for the page: if a cached thumbnail exists and is up to date
     * (thumbnailTimestamp >= lastModified), return it; otherwise build composite from layers.
     * For legacy pages (single pageId.png, no directory), builds a scaled thumbnail from that file.
     */
    fun loadPageThumbnail(pageId: String, maxSize: Int = 768): Bitmap? {
        val pageDir = File(pagesDir, pageId)
        if (!pageDir.exists()) {
            // Legacy: single PNG file, no per-page directory
            val full = loadPageBitmap(pageId) ?: return null
            return scaleBitmapToMax(full, maxSize)
        }
        val meta = PageMeta.fromFile(File(pageDir, META_FILE))
        val thumbFile = File(pageDir, THUMBNAIL_FILE)
        if (meta != null && meta.thumbnailTimestamp >= meta.lastModified && thumbFile.exists()) {
            return BitmapFactory.decodeFile(thumbFile.absolutePath)
        }
        buildCompositeThumbnail(pageId, maxSize)?.let { return it }
        // Fallback: e.g. missing layers or meta — try first layer or single bitmap
        return loadPageBitmap(pageId)?.let { scaleBitmapToMax(it, maxSize) }
    }

    private fun scaleBitmapToMax(source: Bitmap, maxSize: Int): Bitmap? {
        val width = source.width
        val height = source.height
        if (width <= 0 || height <= 0) return null
        val scale = (maxSize.toFloat() / maxOf(width, height)).coerceAtMost(1f)
        val thumbWidth = (width * scale).toInt().coerceAtLeast(1)
        val thumbHeight = (height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, thumbWidth, thumbHeight, true)
    }

    /**
     * Build a composite thumbnail from background + all layers (scaled to maxSize).
     * Used when no valid cached thumbnail exists.
     */
    private fun buildCompositeThumbnail(pageId: String, maxSize: Int = 768): Bitmap? {
        val layers = loadPageLayers(pageId)
        val layerMetas = loadPageLayerMetas(pageId)
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
        layers.forEachIndexed { index, layer ->
            // Skip hidden layers
            val isHidden = layerMetas?.getOrNull(index)?.isHidden ?: false
            if (!isHidden) {
                layer?.let { canvas.drawBitmap(it, srcRect, dstRect, paint) }
            }
        }
        return thumbnail
    }

    /**
     * Save a cached thumbnail bitmap and set thumbnailTimestamp in meta.
     */
    fun savePageThumbnail(pageId: String, bitmap: Bitmap) {
        try {
            val pageDir = File(pagesDir, pageId)
            if (!pageDir.exists()) return
            val meta = PageMeta.fromFile(File(pageDir, META_FILE)) ?: return
            FileOutputStream(File(pageDir, THUMBNAIL_FILE)).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val thumbnailTimestamp = System.currentTimeMillis()
            File(pageDir, META_FILE).writeText(meta.copy(thumbnailTimestamp = thumbnailTimestamp).toJson())
        } catch (e: Exception) {
            Log.e("LocalPageStore", "Failed to save thumbnail for $pageId", e)
        }
    }

    /**
     * If lastModified > thumbnailTimestamp, build composite and save as cached thumbnail.
     * Call when the user exits the drawing screen.
     */
    fun generateThumbnailIfNeeded(pageId: String, maxSize: Int = 768) {
        val pageDir = File(pagesDir, pageId)
        if (!pageDir.exists()) return
        val meta = PageMeta.fromFile(File(pageDir, META_FILE)) ?: return
        if (meta.lastModified <= meta.thumbnailTimestamp) return
        val composite = buildCompositeThumbnail(pageId, maxSize) ?: return
        savePageThumbnail(pageId, composite)
    }

    /**
     * Delete a page and all its associated files (layers, metadata, legacy PNG).
     */
    fun deletePage(pageId: String) {
        try {
            val pageDir = File(pagesDir, pageId)
            if (pageDir.exists() && pageDir.isDirectory) {
                // Delete the entire directory and all its contents
                pageDir.deleteRecursively()
            }
            // Also handle legacy single PNG file
            val legacyFile = File(pagesDir, "$pageId.png")
            if (legacyFile.exists()) {
                legacyFile.delete()
            }
        } catch (e: Exception) {
            Log.e("LocalPageStore", "Failed to delete page $pageId", e)
        }
    }

    // --- Notebooks ---

    fun loadNotebooks(): List<Notebook> {
        if (!notebooksFile.exists()) return emptyList()
        return try {
            val json = notebooksFile.readText()
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Notebook(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    color = if (obj.has("color") && !obj.isNull("color")) obj.getInt("color") else null
                )
            }
        } catch (e: Exception) {
            Log.e("LocalPageStore", "Failed to load notebooks", e)
            emptyList()
        }
    }

    fun saveNotebooks(notebooks: List<Notebook>) {
        try {
            val arr = JSONArray()
            notebooks.forEach { nb ->
                arr.put(JSONObject().apply {
                    put("id", nb.id)
                    put("name", nb.name)
                    put("createdAt", nb.createdAt)
                    nb.color?.let { put("color", it) } ?: put("color", JSONObject.NULL)
                })
            }
            notebooksFile.writeText(arr.toString())
        } catch (e: Exception) {
            Log.e("LocalPageStore", "Failed to save notebooks", e)
        }
    }

    fun loadNotebookIdForPage(pageId: String): String? {
        val map = loadAllPageNotebookAssignments()
        return map[pageId]
    }

    fun loadAllPageNotebookAssignments(): Map<String, String> {
        if (!assignmentsFile.exists()) return emptyMap()
        return try {
            val json = assignmentsFile.readText()
            val obj = JSONObject(json)
            obj.keys().asSequence().associateWith { obj.getString(it) }
        } catch (e: Exception) {
            Log.e("LocalPageStore", "Failed to load notebook assignments", e)
            emptyMap()
        }
    }

    fun savePageNotebook(pageId: String, notebookId: String?) {
        val map = loadAllPageNotebookAssignments().toMutableMap()
        if (notebookId != null) {
            map[pageId] = notebookId
        } else {
            map.remove(pageId)
        }
        saveAllPageNotebookAssignments(map)
    }

    fun saveAllPageNotebookAssignments(assignments: Map<String, String>) {
        try {
            val obj = JSONObject()
            assignments.forEach { (pageId, notebookId) ->
                obj.put(pageId, notebookId)
            }
            assignmentsFile.writeText(obj.toString())
        } catch (e: Exception) {
            Log.e("LocalPageStore", "Failed to save notebook assignments", e)
        }
    }
}

package com.example.drawingapp.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.io.FileOutputStream

private const val PREFS_NAME = "drawing_app"
private const val KEY_PAGE_IDS = "page_ids"
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
}

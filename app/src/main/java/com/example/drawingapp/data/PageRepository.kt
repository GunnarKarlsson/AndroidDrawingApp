package com.example.drawingapp.data

import android.graphics.Bitmap

class PageRepository(private val store: LocalPageStore) {
    private val _pages = mutableListOf<Page>()
    val pages: List<Page> get() = _pages.toList()

    fun loadPages() {
        val ids = store.loadPageIds()
        _pages.clear()
        _pages.addAll(ids.mapIndexed { index, id -> Page(id = id, title = "Page ${index + 1}") })
    }

    fun addPage(): Page {
        val page = Page(id = Page.newId(), title = "Page ${_pages.size + 1}")
        _pages.add(page)
        store.savePageIds(_pages.map { it.id })
        return page
    }

    fun getPageById(id: String): Page? = _pages.find { it.id == id }

    fun loadPageBitmap(pageId: String): Bitmap? = store.loadPageBitmap(pageId)

    fun loadPageThumbnail(pageId: String): Bitmap? = store.loadPageThumbnail(pageId)

    fun savePageBitmap(pageId: String, bitmap: Bitmap) {
        store.savePageBitmap(pageId, bitmap)
    }

    fun loadPageLayers(pageId: String): List<Bitmap?> = store.loadPageLayers(pageId)

    fun savePageLayers(pageId: String, bitmaps: List<Bitmap>, backgroundColor: Int = 0xFFFFFFFF.toInt()) {
        store.savePageLayers(pageId, bitmaps, backgroundColor)
    }

    fun loadPageBackgroundColor(pageId: String): Int = store.loadPageBackgroundColor(pageId)

    fun savePageBackgroundColor(pageId: String, color: Int) {
        store.savePageBackgroundColor(pageId, color)
    }

    fun loadStrokeSizePx(defaultValue: Float): Float = store.loadStrokeSizePx(defaultValue)

    fun saveStrokeSizePx(value: Float) {
        store.saveStrokeSizePx(value)
    }
}

package com.example.drawingapp.data

import android.graphics.Bitmap

class PageRepository(private val store: LocalPageStore) {
    private val _pages = mutableListOf<Page>()
    val pages: List<Page> get() = _pages.toList()

    /** In-memory page -> notebook assignment; missing key means default notebook. */
    private val _assignments = mutableMapOf<String, String>()

    fun loadPages() {
        val ids = store.loadPageIds()
        _pages.clear()
        _pages.addAll(ids.mapIndexed { index, id ->
            val title = store.loadPageTitle(id) ?: "Page ${index + 1}"
            Page(id = id, title = title)
        })
        _assignments.clear()
        _assignments.putAll(store.loadAllPageNotebookAssignments())
    }

    fun addPage(): Page {
        val page = Page(id = Page.newId(), title = "Page ${_pages.size + 1}")
        _pages.add(page)
        store.savePageIds(_pages.map { it.id })
        return page
    }

    fun getPageById(id: String): Page? = _pages.find { it.id == id }

    fun renamePage(pageId: String, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isBlank()) return
        val index = _pages.indexOfFirst { it.id == pageId }
        if (index >= 0) {
            _pages[index] = _pages[index].copy(title = trimmed)
            store.savePageTitle(pageId, trimmed)
        }
    }

    fun loadPageBitmap(pageId: String): Bitmap? = store.loadPageBitmap(pageId)

    fun loadPageThumbnail(pageId: String): Bitmap? = store.loadPageThumbnail(pageId)

    fun savePageBitmap(pageId: String, bitmap: Bitmap) {
        store.savePageBitmap(pageId, bitmap)
    }

    fun loadPageLayers(pageId: String): List<Bitmap?> = store.loadPageLayers(pageId)

    fun loadPageLayerMetas(pageId: String): List<LayerMeta>? = store.loadPageLayerMetas(pageId)

    fun savePageLayers(pageId: String, bitmaps: List<Bitmap>, backgroundColor: Int = 0xFFFFFFFF.toInt(), layerMetas: List<LayerMeta>? = null) {
        store.savePageLayers(pageId, bitmaps, backgroundColor, layerMetas)
    }

    fun loadPageBackgroundColor(pageId: String): Int = store.loadPageBackgroundColor(pageId)

    fun savePageBackgroundColor(pageId: String, color: Int) {
        store.savePageBackgroundColor(pageId, color)
    }

    fun loadStrokeSizePx(defaultValue: Float): Float = store.loadStrokeSizePx(defaultValue)

    fun saveStrokeSizePx(value: Float) {
        store.saveStrokeSizePx(value)
    }

    fun loadStrokeColorArgb(defaultValue: Int): Int = store.loadStrokeColorArgb(defaultValue)

    fun saveStrokeColorArgb(value: Int) {
        store.saveStrokeColorArgb(value)
    }

    fun loadStrokeCap(defaultValue: Int = 0): Int = store.loadStrokeCap(defaultValue)

    fun saveStrokeCap(value: Int) {
        store.saveStrokeCap(value)
    }

    fun loadCurveSmoothing(defaultValue: Boolean): Boolean = store.loadCurveSmoothing(defaultValue)

    fun saveCurveSmoothing(value: Boolean) {
        store.saveCurveSmoothing(value)
    }

    fun loadCurveClosing(defaultValue: Boolean): Boolean = store.loadCurveClosing(defaultValue)

    fun saveCurveClosing(value: Boolean) {
        store.saveCurveClosing(value)
    }

    fun deletePage(pageId: String) {
        _pages.removeAll { it.id == pageId }
        _assignments.remove(pageId)
        store.savePageIds(_pages.map { it.id })
        store.savePageNotebook(pageId, null)
        store.deletePage(pageId)
    }

    // --- Notebooks ---

    /** Returns all notebooks. On first run, creates default notebook and assigns all existing pages to it. */
    fun getNotebooks(): List<Notebook> {
        var list = store.loadNotebooks()
        if (list.isEmpty()) {
            val default = Notebook(id = Notebook.DEFAULT_ID, name = "All Pages", createdAt = System.currentTimeMillis())
            val pageIds = store.loadPageIds()
            val newMap = pageIds.associateWith { Notebook.DEFAULT_ID }
            store.saveAllPageNotebookAssignments(newMap)
            store.saveNotebooks(listOf(default))
            _assignments.clear()
            _assignments.putAll(newMap)
            list = listOf(default)
        }
        return list
    }

    fun createNotebook(name: String): Notebook {
        val nb = Notebook(id = Notebook.newId(), name = name.trim(), createdAt = System.currentTimeMillis())
        val list = store.loadNotebooks().toMutableList()
        list.add(nb)
        store.saveNotebooks(list)
        return nb
    }

    fun renameNotebook(notebookId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        val list = store.loadNotebooks().map { nb ->
            if (nb.id == notebookId) nb.copy(name = trimmed) else nb
        }
        store.saveNotebooks(list)
    }

    fun deleteNotebook(notebookId: String) {
        if (notebookId == Notebook.DEFAULT_ID) return
        val list = store.loadNotebooks().filter { it.id != notebookId }
        store.saveNotebooks(list)
        _assignments.keys.toList().forEach { pageId ->
            if (_assignments[pageId] == notebookId) {
                _assignments[pageId] = Notebook.DEFAULT_ID
                store.savePageNotebook(pageId, Notebook.DEFAULT_ID)
            }
        }
    }

    fun getPagesForNotebook(notebookId: String): List<Page> =
        _pages.filter { (_assignments[it.id] ?: Notebook.DEFAULT_ID) == notebookId }

    /**
     * Returns up to maxCount page thumbnails for the notebook (for cover preview).
     * Order follows getPagesForNotebook. Null entries for pages with no thumbnail yet.
     */
    fun getNotebookPreviewThumbnails(notebookId: String, maxCount: Int = 4): List<Bitmap?> {
        val pages = getPagesForNotebook(notebookId).take(maxCount)
        return pages.map { loadPageThumbnail(it.id) }
    }

    fun assignPageToNotebook(pageId: String, notebookId: String) {
        _assignments[pageId] = notebookId
        store.savePageNotebook(pageId, notebookId)
    }

    fun movePagesToNotebook(pageIds: List<String>, notebookId: String) {
        pageIds.forEach {
            _assignments[it] = notebookId
            store.savePageNotebook(it, notebookId)
        }
    }

    /**
     * If the page content changed since the last thumbnail, build and save a new thumbnail.
     * Call when the user exits the drawing screen (e.g. from DisposableEffect onDispose).
     */
    fun generateThumbnailIfNeeded(pageId: String) {
        store.generateThumbnailIfNeeded(pageId)
    }
}

# Technical Design Document: Implementing Notebook System for Drawing Organization in Android App

## 1. Overview
### 1.1 Purpose
This document outlines the technical implementation for adding a "Notebook" system to the existing drawing app. The goal is to allow users to organize drawings (referred to as "pages") into named notebooks, replacing or augmenting the current flat grid of thumbnails on the home screen (PageListScreen). Users will be able to:
- Create new notebooks and assign custom names.
- Move pages to notebooks and between notebooks.
- View notebooks on the home page, with the ability to drill down into a notebook to see its contained pages.

This enhances scalability for users with many drawings, drawing inspiration from apps like Concepts (projects as notebooks) and Ibis Paint X (folders/albums).

### 1.2 Scope
- **In Scope:** Data model updates, UI for notebook creation/viewing/navigation, moving pages, persistence.
- **Out of Scope:** Advanced features like sub-notebooks, sharing notebooks, or cloud sync (can be future extensions).
- **Assumptions:** The app uses Jetpack Compose for UI, Room/Kotlin Flows for data (based on PageRepository mention), and existing thumbnail loading logic remains intact.

### 1.3 High-Level Architecture
- **Current State:** Flat list of pages in a 2-column LazyVerticalGrid on PageListScreen.
- **New State:** Home screen shows a grid/list of notebooks. Tapping a notebook navigates to a similar grid showing only its pages.
- **Navigation:** Use Compose Navigation to add a new route for notebook contents (e.g., "notebook/{notebookId}").
- **Data Flow:** Update PageRepository to manage Notebook entities and associate pages with notebooks.

## 2. Data Model
### 2.1 Entities
Introduce a new `Notebook` entity and update `Page` to reference it.

- **Notebook** (new data class/entity):
  ```kotlin
  @Entity(tableName = "notebooks")
  data class Notebook(
      @PrimaryKey(autoGenerate = true) val id: Long = 0,
      val name: String,  // User-provided name, e.g., "Sketches 2026"
      val createdAt: Long = System.currentTimeMillis(),  // For sorting
      val color: Int? = null  // Optional: For custom cover colors (future-proof)
  )
  ```

- **Page** (updated existing entity):
  Add a foreign key to Notebook.
  ```kotlin
  @Entity(
      tableName = "pages",
      foreignKeys = [ForeignKey(
          entity = Notebook::class,
          parentColumns = ["id"],
          childColumns = ["notebookId"],
          onDelete = ForeignKey.CASCADE  // Delete pages if notebook is deleted
      )]
  )
  data class Page(
      @PrimaryKey(autoGenerate = true) val id: Long = 0,
      val title: String,
      // ... existing fields like thumbnailPath, content, etc.
      val notebookId: Long? = null  // Nullable for uncategorized pages (or default to a "Default" notebook)
  )
  ```

### 2.2 Repository Updates
Extend `PageRepository` or create a new `NotebookRepository`. Use Room DAOs for CRUD.

- **NotebookDao** (new):
  ```kotlin
  @Dao
  interface NotebookDao {
      @Query("SELECT * FROM notebooks ORDER BY createdAt DESC")
      fun getAllNotebooks(): Flow<List<Notebook>>

      @Insert(onConflict = OnConflictStrategy.REPLACE)
      suspend fun insertNotebook(notebook: Notebook): Long  // Returns inserted ID

      @Delete
      suspend fun deleteNotebook(notebook: Notebook)
  }
  ```

- **PageDao** (updated):
  Add queries for pages by notebook.
  ```kotlin
  @Query("SELECT * FROM pages WHERE notebookId = :notebookId ORDER BY /* your sort */")
  fun getPagesForNotebook(notebookId: Long): Flow<List<Page>>

  @Query("UPDATE pages SET notebookId = :newNotebookId WHERE id IN (:pageIds)")
  suspend fun movePagesToNotebook(pageIds: List<Long>, newNotebookId: Long)
  ```

- **Repository** (e.g., in `PageRepository` or new class):
  - `fun getNotebooks(): Flow<List<Notebook>>`
  - `suspend fun createNotebook(name: String): Long` (insert and return ID)
  - `fun getPagesForNotebook(notebookId: Long): Flow<List<Page>>`
  - `suspend fun movePages(pageIds: List<Long>, toNotebookId: Long)` (use the update query)

Handle a "Default" or "Uncategorized" notebook (ID 0 or auto-created on app start).

### 2.3 Migration
- Use Room Migration to add the `notebooks` table and `notebookId` column to `pages`.
- Initial migration: Create a default notebook and assign all existing pages to it.

## 3. UI Implementation
### 3.1 Home Screen (PageListScreen → NotebookListScreen)
Repurpose or rename to show notebooks instead of pages.

- **Layout:** Similar 2-column LazyVerticalGrid.
  ```kotlin
  @Composable
  fun NotebookListScreen(
      notebooks: List<Notebook>,
      onCreateNotebook: () -> Unit,
      onNotebookClick: (Notebook) -> Unit,
      onDeleteNotebook: (Notebook) -> Unit
  ) {
      Scaffold(
          floatingActionButton = { FloatingActionButton(onClick = onCreateNotebook) { Icon(Icons.Add, "Create Notebook") } }
      ) { padding ->
          LazyVerticalGrid(
              columns = GridCells.Fixed(2),
              contentPadding = PaddingValues(16.dp),
              horizontalArrangement = Arrangement.spacedBy(16.dp),
              verticalArrangement = Arrangement.spacedBy(16.dp)
          ) {
              items(notebooks, key = { it.id }) { notebook ->
                  NotebookCard(
                      notebook = notebook,
                      onClick = { onNotebookClick(notebook) },
                      onDelete = { onDeleteNotebook(notebook) }  // With confirmation dialog
                  )
              }
          }
      }
  }
  ```

- **NotebookCard** (new Composable):
  Similar to PageCard but for notebooks.
  - Square aspect ratio.
  - Display name centered.
  - Optional: Generate a cover thumbnail (e.g., first page's thumbnail or custom color).
  - Delete icon (bottom-right) with confirmation.

- **ViewModel:** `NotebookViewModel` observes `getNotebooks()` Flow, exposes as State.

### 3.2 Notebook Creation
- On FAB click: Show AlertDialog with TextField for name.
  ```kotlin
  @Composable
  fun CreateNotebookDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
      var name by remember { mutableStateOf("") }
      AlertDialog(
          onDismissRequest = onDismiss,
          title = { Text("New Notebook") },
          text = { TextField(value = name, onValueChange = { name = it }, label = { Text("Name") }) },
          confirmButton = { Button(onClick = { if (name.isNotBlank()) onConfirm(name) }) { Text("Create") } }
      )
  }
  ```
- In ViewModel: Call `createNotebook(name)` and refresh list.

### 3.3 Notebook Detail Screen (New: NotebookPagesScreen)
- Route: "notebook/{notebookId}"
- Layout: Reuse PageListScreen logic – 2-column grid of PageCards for pages in this notebook.
- Add FAB or top bar action for "Add Page" (create new drawing in this notebook).
- Observe `getPagesForNotebook(notebookId)` Flow.

### 3.4 Moving Pages
- **Selection Mode:** In NotebookPagesScreen, enable multi-select (long press on PageCard).
  - Use `rememberMultiSelectionState()` or similar.
  - Show contextual top bar with "Move" action.
- **Move Dialog:** List all notebooks; select target → call `movePages(selectedPageIds, targetNotebookId)`.
- **Drag-and-Drop (Advanced):** Use DragAndDrop APIs in Compose for moving between open notebooks (if multi-window support), but start with selection mode for simplicity.

### 3.5 Navigation
- In NavGraph: Add `composable("notebook/{notebookId}") { NotebookPagesScreen(...) }`
- From home: `navController.navigate("notebook/${notebook.id}")`
- Back navigation handles drill-down naturally.

### 3.6 Thumbnail Handling
- Reuse existing LaunchedEffect for page thumbnails in PageCard.
- For NotebookCard: Optionally load the first page's thumbnail as cover (query via DAO).

## 4. Edge Cases and Error Handling
- Empty notebooks: Show placeholder or "No pages yet".
- Deleting notebook: Confirm, cascade delete pages (or move to uncategorized).
- Name validation: Unique names optional; trim whitespace.
- Performance: Lazy grids handle large lists; use Paging3 if notebooks/pages exceed 1000s.
- Offline: All local (Room-based).

## 5. Testing
- Unit: Repository CRUD (insert notebook, move pages).
- UI: Compose Previews for NotebookCard, dialogs; instrumented tests for navigation and moves.
- Integration: End-to-end flow (create notebook, add/move pages).

## 6. Rollout and Future Enhancements
- **Migration:** Run on app update.
- **Future:** Sub-notebooks, search across notebooks, export notebook as PDF/ZIP, custom covers.
- **Estimated Effort:** 2-4 days for core (assuming existing repo setup), plus testing.

This design maintains the app's simple, grid-based UX while adding organization. Implement iteratively: Start with data model, then home screen, then details/moves.
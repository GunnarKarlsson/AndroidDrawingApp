To create a visually appealing **NotebookCard** (or folder cover) in Jetpack Compose that shows a preview of up to 3–4 contained page thumbnails in a collage-style layout inside the square card — with rules like:

- 1 image → large in upper-left (or centered/full-bleed)
- 2 images → split side-by-side or diagonal
- 3 images → 2 on top row + 1 bottom, or L-shape
- 4+ images → 2×2 grid + optional "..." overlay for more

This is a common pattern in apps like photo galleries, Ibis Paint X folders, or Concepts projects. Here's how to implement it efficiently in your existing 2-column square grid setup.

### Step 1: Prepare Data in ViewModel / Repository
Expose a list of preview thumbnails per notebook (keep it lightweight — load only 3–4 max).

In your `NotebookRepository` or `PageRepository`:

```kotlin
suspend fun getNotebookPreviewThumbnails(notebookId: Long, maxCount: Int = 4): List<Bitmap?> {
    // Query latest or random/first pages in notebook
    val pages = pageDao.getPagesForNotebook(notebookId)
        .first() // or .take(maxCount) if using Flow
        .sortedByDescending { it.createdAt } // or by order
        .take(maxCount)

    return pages.map { page ->
        thumbnailLoader.loadThumbnail(page.id) // your existing IO thumbnail loader
            ?: null // or placeholder bitmap
    }
}
```

- Cache results per notebookId if needed (e.g., in a `MutableStateMap<Long, List<Bitmap?>>` in ViewModel).
- Return `List<Bitmap?>` (nullable for missing thumbnails).

Pass this to the UI via ViewModel:

```kotlin
val notebookPreviews by notebookViewModel.notebookPreviews.collectAsState() // Map<Long, List<Bitmap?>>
```

### Step 2: NotebookCard Composable with Dynamic Preview
Make the card square (like your PageCard) and fill the preview area with a collage.

```kotlin
@Composable
fun NotebookCard(
    notebook: Notebook,
    previewBitmaps: List<Bitmap?>, // 0–4 items, from ViewModel
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)           // Square
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // 1. Background / Preview collage
            if (previewBitmaps.isNotEmpty()) {
                NotebookPreviewCollage(bitmaps = previewBitmaps)
            } else {
                // Empty state: color or icon
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }

            // 2. Gradient overlay at bottom for title readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.6f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.6f)
                        )
                    )
            )

            // 3. Title at bottom-left
            Text(
                text = notebook.name,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            )

            // 4. Delete icon (like your PageCard)
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete notebook",
                    tint = Color.White.copy(alpha = 0.8f)
                )
            }

            // Optional: Small count badge if >4
            if (previewBitmaps.size >= 4) {
                Text(
                    text = "+",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
```

### Step 3: The Collage Composable (Core Logic)
This is the flexible part — use a `Box` with positioned `Image`s.

```kotlin
@Composable
fun NotebookPreviewCollage(
    bitmaps: List<Bitmap?>,
    modifier: Modifier = Modifier
) {
    val images = bitmaps.take(4).filterNotNull() // max 4, skip nulls
    if (images.isEmpty()) return

    Box(modifier = modifier.fillMaxSize()) {

        when (images.size) {
            1 -> {
                // Single image: upper-left or full (your choice)
                AsyncImage(  // or use your LaunchedEffect + Image(bitmap = ...)
                    bitmap = images[0].asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize(0.8f)           // or .fillMaxSize()
                        .align(Alignment.TopStart)   // upper-left
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            2 -> {
                // Two side-by-side or diagonal
                Row(modifier = Modifier.fillMaxSize()) {
                    Image(
                        bitmap = images[0].asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Image(
                        bitmap = images[1].asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            3 -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f)) {
                        Image(
                            bitmap = images[0].asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(topStart = 8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Image(
                            bitmap = images[1].asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(topEnd = 8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Image(
                        bitmap = images[2].asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            else -> { // 4 or more → 2×2 grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = false // no scroll inside card
                ) {
                    items(4) { index ->
                        val bmp = images.getOrNull(index)
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(
                                        when (index) {
                                            0 -> RoundedCornerShape(topStart = 8.dp)
                                            1 -> RoundedCornerShape(topEnd = 8.dp)
                                            2 -> RoundedCornerShape(bottomStart = 8.dp)
                                            else -> RoundedCornerShape(bottomEnd = 8.dp)
                                        }
                                    ),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(modifier = Modifier
                                .aspectRatio(1f)
                                .background(Color.DarkGray))
                        }
                    }
                }
            }
        }
    }
}
```

### Optimizations & Tips
- **Loading**: Use Coil's `AsyncImage` with `rememberAsyncImagePainter` if thumbnails are file URIs/paths instead of Bitmaps (better memory). Or stick with your `LaunchedEffect` + local state per card.
- **Performance**: Limit to 3–4 previews. Pre-generate small thumbnails (e.g., 200×200) in repository. Use `remember` for bitmaps if possible.
- **Empty/missing**: Fallback to a gradient background or notebook color.
- **Polish**: Add subtle shadows/borders between images, or a faint white vignette.
- **Alternative (simpler)**: Always show a 2×2 grid, hide empty slots, and overlay "+" if >4. This is easier and looks consistent (like many gallery apps).

This keeps your home screen grid clean and glanceable while feeling premium like modern drawing/note apps. Test on different notebook sizes — start with the 4-image 2×2 case as default if you want consistency.
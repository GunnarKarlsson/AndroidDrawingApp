package com.example.drawingapp.ui.pagelist

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.drawingapp.data.Page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageListScreen(
    pages: List<Page>,
    onAddPage: () -> Unit,
    onPageClick: (Page) -> Unit,
    onDeletePage: (Page) -> Unit,
    onLoadThumbnail: (suspend (String) -> Bitmap?)? = null,
    onBackup: (suspend () -> Result<Unit>)? = null,
    onRestore: (suspend () -> Result<Unit>)? = null,
    onRestoreComplete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var pageToDelete by remember { mutableStateOf<Page?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Drawing App") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddPage,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add page")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (onBackup != null && onRestore != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        text = "Cloud backup",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val r = onBackup()
                                    Toast.makeText(
                                        context,
                                        if (r.isSuccess) "Backup complete" else "Backup failed: ${r.exceptionOrNull()?.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        ) { Text("Backup now") }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val r = onRestore()
                                    if (r.isSuccess) onRestoreComplete()
                                    Toast.makeText(
                                        context,
                                        if (r.isSuccess) "Restore complete" else "Restore failed: ${r.exceptionOrNull()?.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        ) { Text("Restore") }
                    }
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(pages, key = { it.id }) { page ->
                    PageCard(
                        page = page,
                        onLoadThumbnail = onLoadThumbnail,
                        onClick = { onPageClick(page) },
                        onDelete = { pageToDelete = page }
                    )
                }
            }
        }
        
        // Delete confirmation dialog
        pageToDelete?.let { page ->
            AlertDialog(
                onDismissRequest = { pageToDelete = null },
                title = { Text("Delete Image") },
                text = { Text("Are you sure you want to delete this image?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeletePage(page)
                            pageToDelete = null
                        }
                    ) {
                        Text("Confirm")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { pageToDelete = null }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun PageCard(
    page: Page,
    onLoadThumbnail: (suspend (String) -> Bitmap?)?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var thumbnail by remember(page.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(page.id) {
        thumbnail = onLoadThumbnail?.let { load -> withContext(Dispatchers.IO) { load(page.id) } }
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail!!.asImageBitmap(),
                    contentDescription = "Preview of ${page.title}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Text(
                    text = page.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                )
            } else {
                Text(
                    text = page.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            // Delete icon in lower right corner - grey icon on white rounded square
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(32.dp)
                    .background(
                        color = Color.White,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete ${page.title}",
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

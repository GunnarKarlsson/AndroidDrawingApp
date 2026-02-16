package com.example.drawingapp.ui.notebooklist

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import com.example.drawingapp.R
import com.example.drawingapp.data.Notebook

private val NOTEBOOK_LIST_BACKGROUND = Color(0xFF565563)
private val NOTEBOOK_LIST_ICON_COLOR = Color(0xFFE1D8D5)
private val DEFAULT_NOTEBOOK_CARD_COLOR = Color(0xFF6B7B8C)
private val COLLAGE_GAP = 4.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NotebookListScreen(
    notebooks: List<Notebook>,
    loadNotebookPreviews: suspend (String) -> List<Bitmap?>,
    onCreateNotebook: () -> Unit,
    onNotebookClick: (Notebook) -> Unit,
    onRenameNotebook: (Notebook, String) -> Unit,
    onDeleteNotebook: (Notebook) -> Unit,
    onSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var notebookToDelete by remember { mutableStateOf<Notebook?>(null) }
    var notebookToRename by remember { mutableStateOf<Notebook?>(null) }

    Scaffold(
        modifier = modifier,
        containerColor = NOTEBOOK_LIST_BACKGROUND,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NOTEBOOK_LIST_BACKGROUND,
                    titleContentColor = NOTEBOOK_LIST_ICON_COLOR,
                    actionIconContentColor = NOTEBOOK_LIST_ICON_COLOR
                ),
                title = { Text("DrawApp", color = NOTEBOOK_LIST_ICON_COLOR) },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_settings),
                            contentDescription = "Settings",
                            tint = NOTEBOOK_LIST_ICON_COLOR
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateNotebook,
                containerColor = NOTEBOOK_LIST_ICON_COLOR,
                contentColor = NOTEBOOK_LIST_BACKGROUND
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = "Add notebook"
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(NOTEBOOK_LIST_BACKGROUND)
                .padding(paddingValues)
        ) {
            if (notebooks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No notebooks",
                        style = MaterialTheme.typography.bodyLarge,
                        color = NOTEBOOK_LIST_ICON_COLOR
                    )
                }
            } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(notebooks, key = { it.id }) { notebook ->
                    var previewBitmaps by remember(notebook.id) { mutableStateOf<List<Bitmap?>>(emptyList()) }
                    LaunchedEffect(notebook.id) {
                        previewBitmaps = loadNotebookPreviews(notebook.id)
                    }
                    NotebookCard(
                        notebook = notebook,
                        previewBitmaps = previewBitmaps,
                        onClick = { onNotebookClick(notebook) },
                        onLongClick = { notebookToRename = notebook },
                        onDelete = { if (notebook.id != Notebook.DEFAULT_ID) notebookToDelete = notebook }
                    )
                }
            }
            }
        }

        notebookToDelete?.let { notebook ->
            AlertDialog(
                onDismissRequest = { notebookToDelete = null },
                containerColor = NOTEBOOK_LIST_BACKGROUND,
                titleContentColor = NOTEBOOK_LIST_ICON_COLOR,
                textContentColor = NOTEBOOK_LIST_ICON_COLOR,
                shape = RoundedCornerShape(0.dp),
                title = { Text("Delete Notebook", color = NOTEBOOK_LIST_ICON_COLOR) },
                text = {
                    Text(
                        "Are you sure? Pages in this notebook will be moved to All Pages.",
                        color = NOTEBOOK_LIST_ICON_COLOR
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeleteNotebook(notebook)
                            notebookToDelete = null
                        }
                    ) {
                        Text("Confirm", color = NOTEBOOK_LIST_ICON_COLOR)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { notebookToDelete = null }) {
                        Text("Cancel", color = NOTEBOOK_LIST_ICON_COLOR)
                    }
                }
            )
        }

        notebookToRename?.let { notebook ->
            RenameNotebookDialog(
                notebook = notebook,
                onConfirm = { newName ->
                    onRenameNotebook(notebook, newName)
                    notebookToRename = null
                },
                onDismiss = { notebookToRename = null }
            )
        }
    }
}

@Composable
fun NotebookPreviewCollage(
    bitmaps: List<Bitmap?>,
    gapColor: Color = DEFAULT_NOTEBOOK_CARD_COLOR,
    modifier: Modifier = Modifier
) {
    val images = bitmaps.take(4).filterNotNull()
    if (images.isEmpty()) return
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(gapColor)
            .padding(COLLAGE_GAP)
    ) {
        when (images.size) {
            1 -> {
                Image(
                    bitmap = images[0].asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            2 -> {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(COLLAGE_GAP)
                ) {
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
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(COLLAGE_GAP)
                ) {
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(COLLAGE_GAP)) {
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
            else -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(COLLAGE_GAP)
                ) {
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(COLLAGE_GAP)) {
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
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(COLLAGE_GAP)) {
                        Image(
                            bitmap = images[2].asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(bottomStart = 8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Image(
                            bitmap = images[3].asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(bottomEnd = 8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotebookCard(
    notebook: Notebook,
    previewBitmaps: List<Bitmap?> = emptyList(),
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardColor = notebook.color?.let { Color(it) } ?: DEFAULT_NOTEBOOK_CARD_COLOR
    val hasPreviews = previewBitmaps.any { it != null }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (hasPreviews) {
                NotebookPreviewCollage(bitmaps = previewBitmaps, gapColor = cardColor)
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
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(cardColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_notebook),
                        contentDescription = null,
                        tint = NOTEBOOK_LIST_ICON_COLOR,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            Text(
                text = notebook.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (hasPreviews) Color.White else NOTEBOOK_LIST_ICON_COLOR,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            )
            if (notebook.id != Notebook.DEFAULT_ID) {
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
                        contentDescription = "Delete ${notebook.name}",
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CreateNotebookDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NOTEBOOK_LIST_BACKGROUND,
        titleContentColor = NOTEBOOK_LIST_ICON_COLOR,
        textContentColor = NOTEBOOK_LIST_ICON_COLOR,
        shape = RoundedCornerShape(0.dp),
        title = { Text("New Notebook", color = NOTEBOOK_LIST_ICON_COLOR) },
        text = {
            CompositionLocalProvider(
                LocalTextSelectionColors provides TextSelectionColors(
                    handleColor = Color.White,
                    backgroundColor = Color.White.copy(alpha = 0.4f)
                )
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name", color = NOTEBOOK_LIST_ICON_COLOR) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                    colors = TextFieldDefaults.colors(
                        cursorColor = Color.White,
                        focusedIndicatorColor = Color.White,
                        unfocusedIndicatorColor = Color.White,
                        focusedContainerColor = NOTEBOOK_LIST_BACKGROUND,
                        unfocusedContainerColor = NOTEBOOK_LIST_BACKGROUND
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isNotBlank()) {
                        onConfirm(trimmed)
                    }
                }
            ) {
                Text("Create", color = NOTEBOOK_LIST_ICON_COLOR)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = NOTEBOOK_LIST_ICON_COLOR)
            }
        }
    )
}

@Composable
fun RenameNotebookDialog(
    notebook: Notebook,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(notebook.id) { mutableStateOf(notebook.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NOTEBOOK_LIST_BACKGROUND,
        titleContentColor = NOTEBOOK_LIST_ICON_COLOR,
        textContentColor = NOTEBOOK_LIST_ICON_COLOR,
        shape = RoundedCornerShape(0.dp),
        title = { Text("Rename Notebook", color = NOTEBOOK_LIST_ICON_COLOR) },
        text = {
            CompositionLocalProvider(
                LocalTextSelectionColors provides TextSelectionColors(
                    handleColor = Color.White,
                    backgroundColor = Color.White.copy(alpha = 0.4f)
                )
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name", color = NOTEBOOK_LIST_ICON_COLOR) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                    colors = TextFieldDefaults.colors(
                        cursorColor = Color.White,
                        focusedIndicatorColor = Color.White,
                        unfocusedIndicatorColor = Color.White,
                        focusedContainerColor = NOTEBOOK_LIST_BACKGROUND,
                        unfocusedContainerColor = NOTEBOOK_LIST_BACKGROUND
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isNotBlank()) {
                        onConfirm(trimmed)
                    }
                }
            ) {
                Text("Rename", color = NOTEBOOK_LIST_ICON_COLOR)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = NOTEBOOK_LIST_ICON_COLOR)
            }
        }
    )
}

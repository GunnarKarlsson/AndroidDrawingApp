package com.example.drawingapp.ui.notebooklist

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.drawingapp.R
import com.example.drawingapp.data.Notebook
import com.example.drawingapp.data.Page
import com.example.drawingapp.ui.pagelist.PageCard

private val NOTEBOOK_PAGES_BACKGROUND = Color(0xFF565563)
private val NOTEBOOK_PAGES_ICON_COLOR = Color(0xFFE1D8D5)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookPagesScreen(
    notebookName: String,
    pages: List<Page>,
    notebooks: List<Notebook>,
    currentNotebookId: String,
    onAddPage: () -> Unit,
    onPageClick: (Page) -> Unit,
    onDeletePage: (Page) -> Unit,
    onRenamePage: (Page, String) -> Unit,
    onMovePages: (List<String>, String) -> Unit,
    onLoadThumbnail: (suspend (String) -> Bitmap?)? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pageToDelete by remember { mutableStateOf<Page?>(null) }
    var pageToRename by remember { mutableStateOf<Page?>(null) }
    var pageWithMenu by remember { mutableStateOf<Page?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedPageIds by remember { mutableStateOf(setOf<String>()) }
    var showMoveDialog by remember { mutableStateOf(false) }

    fun exitSelectionMode() {
        selectionMode = false
        selectedPageIds = emptySet()
    }

    Scaffold(
        modifier = modifier,
        containerColor = NOTEBOOK_PAGES_BACKGROUND,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NOTEBOOK_PAGES_BACKGROUND,
                    titleContentColor = NOTEBOOK_PAGES_ICON_COLOR,
                    navigationIconContentColor = NOTEBOOK_PAGES_ICON_COLOR
                ),
                title = {
                    Text(
                        if (selectionMode) "${selectedPageIds.size} selected" else notebookName,
                        color = NOTEBOOK_PAGES_ICON_COLOR
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (selectionMode) exitSelectionMode() else onBack()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (selectionMode) "Cancel" else "Back"
                        )
                    }
                },
                actions = {
                    if (selectionMode) {
                        TextButton(
                            onClick = {
                                if (selectedPageIds.isNotEmpty()) showMoveDialog = true
                            }
                        ) {
                            Text("Move", color = NOTEBOOK_PAGES_ICON_COLOR)
                        }
                    } else {
                        TextButton(onClick = { selectionMode = true }) {
                            Text("Select", color = NOTEBOOK_PAGES_ICON_COLOR)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddPage,
                containerColor = NOTEBOOK_PAGES_ICON_COLOR,
                contentColor = NOTEBOOK_PAGES_BACKGROUND
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = "Add page"
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(NOTEBOOK_PAGES_BACKGROUND)
                .padding(paddingValues)
        ) {
            if (pages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No pages yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = NOTEBOOK_PAGES_ICON_COLOR
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
                    items(pages, key = { it.id }) { page ->
                        PageCard(
                            page = page,
                            onLoadThumbnail = onLoadThumbnail,
                            onClick = {
                                if (selectionMode) {
                                    selectedPageIds = if (page.id in selectedPageIds) {
                                        selectedPageIds - page.id
                                    } else {
                                        selectedPageIds + page.id
                                    }
                                } else {
                                    onPageClick(page)
                                }
                            },
                            onDelete = { if (!selectionMode) pageToDelete = page },
                            isSelected = page.id in selectedPageIds,
                            onLongClick = {
                                if (selectionMode) {
                                    selectedPageIds = selectedPageIds + page.id
                                } else {
                                    pageWithMenu = page
                                }
                            }
                        )
                    }
                }
            }
        }

        pageToDelete?.let { page ->
            AlertDialog(
                onDismissRequest = { pageToDelete = null },
                containerColor = NOTEBOOK_PAGES_BACKGROUND,
                titleContentColor = NOTEBOOK_PAGES_ICON_COLOR,
                textContentColor = NOTEBOOK_PAGES_ICON_COLOR,
                shape = RoundedCornerShape(0.dp),
                title = { Text("Delete Image", color = NOTEBOOK_PAGES_ICON_COLOR) },
                text = {
                    Text(
                        "Are you sure you want to delete this image?",
                        color = NOTEBOOK_PAGES_ICON_COLOR
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeletePage(page)
                            pageToDelete = null
                        }
                    ) {
                        Text("Confirm", color = NOTEBOOK_PAGES_ICON_COLOR)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pageToDelete = null }) {
                        Text("Cancel", color = NOTEBOOK_PAGES_ICON_COLOR)
                    }
                }
            )
        }

        pageWithMenu?.let { page ->
            PageOptionsDialog(
                page = page,
                onNameOrRename = {
                    pageWithMenu = null
                    pageToRename = page
                },
                onMove = {
                    pageWithMenu = null
                    selectionMode = true
                    selectedPageIds = setOf(page.id)
                    showMoveDialog = true
                },
                onDismiss = { pageWithMenu = null }
            )
        }

        pageToRename?.let { page ->
            RenamePageDialog(
                page = page,
                onConfirm = { newName ->
                    onRenamePage(page, newName)
                    pageToRename = null
                },
                onDismiss = { pageToRename = null }
            )
        }

        if (showMoveDialog) {
            MoveToNotebookDialog(
                notebooks = notebooks.filter { it.id != currentNotebookId },
                onSelect = { targetNotebookId ->
                    onMovePages(selectedPageIds.toList(), targetNotebookId)
                    exitSelectionMode()
                    showMoveDialog = false
                },
                onDismiss = { showMoveDialog = false }
            )
        }
    }
}

private val DEFAULT_PAGE_TITLE_PATTERN = Regex("^Page \\d+$")

@Composable
fun PageOptionsDialog(
    page: Page,
    onNameOrRename: () -> Unit,
    onMove: () -> Unit,
    onDismiss: () -> Unit
) {
    val nameOrRenameLabel = if (page.title.matches(DEFAULT_PAGE_TITLE_PATTERN)) "Name" else "Rename"
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NOTEBOOK_PAGES_BACKGROUND,
        titleContentColor = NOTEBOOK_PAGES_ICON_COLOR,
        textContentColor = NOTEBOOK_PAGES_ICON_COLOR,
        shape = RoundedCornerShape(0.dp),
        title = { Text("Page options", color = NOTEBOOK_PAGES_ICON_COLOR) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = onNameOrRename,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(nameOrRenameLabel, color = NOTEBOOK_PAGES_ICON_COLOR)
                }
                TextButton(
                    onClick = onMove,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Move to notebook", color = NOTEBOOK_PAGES_ICON_COLOR)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = NOTEBOOK_PAGES_ICON_COLOR)
            }
        }
    )
}

@Composable
fun MoveToNotebookDialog(
    notebooks: List<Notebook>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NOTEBOOK_PAGES_BACKGROUND,
        titleContentColor = NOTEBOOK_PAGES_ICON_COLOR,
        textContentColor = NOTEBOOK_PAGES_ICON_COLOR,
        shape = RoundedCornerShape(0.dp),
        title = { Text("Move to notebook", color = NOTEBOOK_PAGES_ICON_COLOR) },
        text = {
            Column {
                if (notebooks.isEmpty()) {
                    Text("No other notebooks", color = NOTEBOOK_PAGES_ICON_COLOR)
                } else {
                    notebooks.forEach { notebook ->
                        Text(
                            text = notebook.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(notebook.id)
                                }
                                .padding(vertical = 12.dp),
                            color = NOTEBOOK_PAGES_ICON_COLOR,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = NOTEBOOK_PAGES_ICON_COLOR)
            }
        }
    )
}

@Composable
fun RenamePageDialog(
    page: Page,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(page.id) { mutableStateOf(page.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NOTEBOOK_PAGES_BACKGROUND,
        titleContentColor = NOTEBOOK_PAGES_ICON_COLOR,
        textContentColor = NOTEBOOK_PAGES_ICON_COLOR,
        shape = RoundedCornerShape(0.dp),
        title = { Text("Rename drawing", color = NOTEBOOK_PAGES_ICON_COLOR) },
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
                    label = { Text("Name", color = NOTEBOOK_PAGES_ICON_COLOR) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                    colors = TextFieldDefaults.colors(
                        cursorColor = Color.White,
                        focusedIndicatorColor = Color.White,
                        unfocusedIndicatorColor = Color.White,
                        focusedContainerColor = NOTEBOOK_PAGES_BACKGROUND,
                        unfocusedContainerColor = NOTEBOOK_PAGES_BACKGROUND
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isNotBlank()) onConfirm(trimmed)
                }
            ) {
                Text("Rename", color = NOTEBOOK_PAGES_ICON_COLOR)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = NOTEBOOK_PAGES_ICON_COLOR)
            }
        }
    )
}

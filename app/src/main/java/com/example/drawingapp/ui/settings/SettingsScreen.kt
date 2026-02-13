package com.example.drawingapp.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import kotlinx.coroutines.launch

private val SETTINGS_BACKGROUND = Color(0xFF565563)
private val SETTINGS_ICON_COLOR = Color(0xFFE1D8D5)
private val SETTINGS_BUTTON_TEXT = Color.White

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onBackup: (suspend () -> Result<Unit>)? = null,
    onRestore: (suspend () -> Result<Unit>)? = null,
    onRestoreComplete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
        containerColor = SETTINGS_BACKGROUND,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SETTINGS_BACKGROUND,
                    titleContentColor = SETTINGS_ICON_COLOR,
                    navigationIconContentColor = SETTINGS_ICON_COLOR,
                    actionIconContentColor = SETTINGS_ICON_COLOR
                ),
                title = { Text("Settings", color = SETTINGS_ICON_COLOR) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = SETTINGS_ICON_COLOR
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SETTINGS_BACKGROUND)
                .padding(paddingValues)
        ) {
            if (onBackup != null && onRestore != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.12f))
                ) {
                    Text(
                        text = "Cloud backup",
                        style = MaterialTheme.typography.titleSmall,
                        color = SETTINGS_ICON_COLOR,
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
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SETTINGS_BUTTON_TEXT)
                        ) { Text("Backup now", color = SETTINGS_BUTTON_TEXT) }
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
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SETTINGS_BUTTON_TEXT)
                        ) { Text("Restore", color = SETTINGS_BUTTON_TEXT) }
                    }
                }
            }
        }
    }
}

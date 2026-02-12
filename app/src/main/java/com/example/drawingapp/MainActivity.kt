package com.example.drawingapp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.drawingapp.data.LocalPageStore
import com.example.drawingapp.data.Page
import com.example.drawingapp.data.PageRepository
import com.example.drawingapp.data.S3BackupRepository
import com.example.drawingapp.ui.drawing.DrawingScreen
import com.example.drawingapp.util.exportDrawingAsPng
import com.example.drawingapp.util.shareUri
import com.example.drawingapp.ui.pagelist.PageListScreen
import com.example.drawingapp.ui.theme.ExampleDrawingAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ExampleDrawingAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val context = LocalContext.current
                    val appContext = context.applicationContext
                    val localStore = remember { LocalPageStore(appContext) }
                    val repo = remember { PageRepository(localStore) }
                    val s3Backup = remember { S3BackupRepository(appContext, localStore) }
                    val pages = remember { mutableStateListOf<Page>() }
                    LaunchedEffect(Unit) {
                        repo.loadPages()
                        pages.clear()
                        pages.addAll(repo.pages)
                    }

                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "pagelist"
                    ) {
                        composable("pagelist") {
                            PageListScreen(
                                pages = pages,
                                onAddPage = {
                                    val page = repo.addPage()
                                    pages.clear()
                                    pages.addAll(repo.pages)
                                    navController.navigate("drawing/${page.id}")
                                },
                                onPageClick = { page ->
                                    navController.navigate("drawing/${page.id}")
                                },
                                onLoadThumbnail = { pageId -> withContext(Dispatchers.IO) { repo.loadPageThumbnail(pageId) } },
                                onBackup = { s3Backup.backup() },
                                onRestore = { s3Backup.restore() },
                                onRestoreComplete = {
                                    repo.loadPages()
                                    pages.clear()
                                    pages.addAll(repo.pages)
                                }
                            )
                        }
                        composable(
                            route = "drawing/{pageId}",
                            arguments = listOf(navArgument("pageId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val pageId = backStackEntry.arguments?.getString("pageId") ?: return@composable
                            val defaultStrokeSize = 20f
                            DrawingScreen(
                                pageId = pageId,
                                onLoadLayers = { repo.loadPageLayers(it) },
                                onSaveLayers = { id, bitmaps, bgColor -> repo.savePageLayers(id, bitmaps, bgColor) },
                                onLoadBackgroundColor = { repo.loadPageBackgroundColor(it) },
                                onSaveBackgroundColor = { id, color -> repo.savePageBackgroundColor(id, color) },
                                onExport = { bmp ->
                                    exportDrawingAsPng(context, bmp)?.let { uri ->
                                        Toast.makeText(context, "Saved to DrawingApp/images", Toast.LENGTH_SHORT).show()
                                        shareUri(context, uri)
                                    }
                                },
                                initialStrokeSizePx = remember { repo.loadStrokeSizePx(defaultStrokeSize) },
                                onSaveStrokeSizePx = { repo.saveStrokeSizePx(it) }
                            )
                        }
                    }
                }
            }
        }
    }
}

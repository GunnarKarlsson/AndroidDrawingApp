package com.example.drawingapp.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val EXPORT_SUBDIR = "AndroidDrawingApp"
private const val RELATIVE_PATH_PICTURES = "Pictures/$EXPORT_SUBDIR"

@Suppress("UNUSED_PARAMETER")
fun getExportDirectoryPath(context: Context): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        "Pictures / $EXPORT_SUBDIR"
    } else {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), EXPORT_SUBDIR)
        dir.absolutePath
    }
}

fun exportDrawingAsPng(context: Context, bitmap: Bitmap): Uri? {
    val name = "drawing_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.png"
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        exportViaMediaStore(context, bitmap, name)
    } else {
        exportViaLegacyFile(context, bitmap, name)
    }
}

@Suppress("DEPRECATION")
private fun exportViaLegacyFile(context: Context, bitmap: Bitmap, name: String): Uri? {
    val picturesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), EXPORT_SUBDIR)
    if (!picturesDir.exists() && !picturesDir.mkdirs()) return null
    val file = File(picturesDir, name)
    return try {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } catch (e: Exception) {
        null
    }
}

private fun exportViaMediaStore(context: Context, bitmap: Bitmap, name: String): Uri? {
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, RELATIVE_PATH_PICTURES)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return null
    return try {
        context.contentResolver.openOutputStream(uri)?.use { out: OutputStream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, contentValues, null, null)
        }
        uri
    } catch (e: Exception) {
        context.contentResolver.delete(uri, null, null)
        null
    }
}

fun shareUri(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Export drawing"))
}

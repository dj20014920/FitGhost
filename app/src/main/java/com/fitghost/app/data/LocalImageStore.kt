package com.fitghost.app.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/** PNG 저장 및 공유 URI 제공 */
class LocalImageStore(private val context: Context) {
    data class SaveResult(val file: File, val uri: Uri)

    fun saveTryOnPng(bitmap: Bitmap): SaveResult {
        val dir = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "tryon")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "tryon_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = FileProvider.getUriForFile(context, "com.fitghost.app.fileprovider", file)
        return SaveResult(file, uri)
    }
}

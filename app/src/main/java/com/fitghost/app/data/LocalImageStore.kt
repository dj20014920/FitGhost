package com.fitghost.app.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

// Try-On 결과 저장 유틸
// 저장 경로 예: getExternalFilesDir(Pictures)/tryon/ 내 PNG 파일들 (PRD 준수)
// 주의: 주석 내 "/*" 시퀀스는 컴파일러가 중첩 주석으로 해석할 수 있으므로 사용하지 않음
object LocalImageStore {
    private val timeFmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    private val listeners = CopyOnWriteArrayList<(List<File>) -> Unit>()

    fun saveTryOnPng(context: Context, bitmap: Bitmap): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
        val dir = File(base, "tryon").apply { if (!exists()) mkdirs() }
        val name = "tryon_${timeFmt.format(Date())}.png"
        val outFile = File(dir, name)
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        notifyChanged(context)
        return outFile
    }

    fun listTryOnFiles(context: Context): List<File> {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return emptyList()
        val dir = File(base, "tryon")
        return dir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun latestTryOnFile(context: Context): File? = listTryOnFiles(context).firstOrNull()

    fun deleteTryOnFile(context: Context, file: File): Boolean {
        val ok = file.exists() && file.delete()
        if (ok) notifyChanged(context)
        return ok
    }

    fun saveToMediaStore(
            context: Context,
            file: File,
            relativePath: String = "Pictures/TryOn",
            mimeType: String = "image/png",
            displayName: String = file.name
    ): Uri? {
        val resolver = context.contentResolver
        val values =
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            }
                    ?: return null
        }
        return uri
    }

    fun buildShareIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun addOnChangedListener(listener: (List<File>) -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    fun refresh(context: Context) {
        notifyChanged(context)
    }

    private fun notifyChanged(context: Context) {
        val current = listTryOnFiles(context)
        for (l in listeners) {
            l(current)
        }
    }
}

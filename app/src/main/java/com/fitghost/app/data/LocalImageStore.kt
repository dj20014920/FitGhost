package com.fitghost.app.data

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Try-On 결과 저장 유틸
// 저장 경로 예: getExternalFilesDir(Pictures)/tryon/ 내 PNG 파일들 (PRD 준수)
// 주의: 주석 내 "/*" 시퀀스는 컴파일러가 중첩 주석으로 해석할 수 있으므로 사용하지 않음
object LocalImageStore {
    private val timeFmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    fun saveTryOnPng(context: Context, bitmap: Bitmap): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: context.filesDir
        val dir = File(base, "tryon").apply { if (!exists()) mkdirs() }
        val name = "tryon_${timeFmt.format(Date())}.png"
        val outFile = File(dir, name)
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        return outFile
    }

    fun listTryOnFiles(context: Context): List<File> {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return emptyList()
        val dir = File(base, "tryon")
        return dir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
}

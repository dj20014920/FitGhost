package com.fitghost.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream

/**
 * 이미지 입출력 공통 유틸 (DRY)
 */
object ImageUtils {
    /** Uri → PNG 바이트 배열 */
    fun uriToPngBytes(context: Context, uri: Uri): ByteArray {
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open uri: $uri")
        input.use { stream ->
            val bmp = BitmapFactory.decodeStream(stream) ?: error("Invalid image: $uri")
            val bos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
            return bos.toByteArray()
        }
    }

    /** PNG 바이트 배열 → Bitmap */
    fun decodePng(bytes: ByteArray): Bitmap {
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error("Failed to decode PNG bytes")
    }
}


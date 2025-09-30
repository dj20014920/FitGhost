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

    /** Uri → PNG 바이트 배열 (가로세로 비율 유지, 최대 변 기본 1024px)
     *  주의: 런타임 구성값은 호출부에서 전달(BuildConfig.TRYON_MAX_SIDE_PX)하도록 하여
     *  이 유틸이 BuildConfig에 직접 의존하지 않게 유지(KISS/DRY).
     */
    fun uriToPngBytesCapped(context: Context, uri: Uri, maxSide: Int = 1024): ByteArray {
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open uri: $uri")
        input.use { stream ->
            // 1) 원본 메타만 읽어 inSampleSize 계산
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream, null, boundsOpts)
            val origW = boundsOpts.outWidth
            val origH = boundsOpts.outHeight
            require(origW > 0 && origH > 0) { "Invalid image bounds: $uri" }

            // 2) 필요한 축소 배율 계산(가로세로 비율 유지)
            val scale = if (origW <= maxSide && origH <= maxSide) 1.0 else maxOf(origW.toDouble() / maxSide, origH.toDouble() / maxSide)
            val targetW = (origW / scale).toInt().coerceAtLeast(1)
            val targetH = (origH / scale).toInt().coerceAtLeast(1)

            // 3) inSampleSize로 1차 다운샘플
            val sampleSize = computeInSampleSize(origW, origH, targetW, targetH)

            // decodeStream은 스트림을 소모하므로 다시 연다
            val input2 = context.contentResolver.openInputStream(uri) ?: error("Cannot reopen uri: $uri")
            input2.use { stream2 ->
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val sampled = BitmapFactory.decodeStream(stream2, null, decodeOpts) ?: error("Invalid image after sampling: $uri")
                val needExactScale = sampled.width != targetW || sampled.height != targetH
                val finalBmp = if (needExactScale) Bitmap.createScaledBitmap(sampled, targetW, targetH, true) else sampled

                val bos = ByteArrayOutputStream()
                finalBmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
                if (finalBmp !== sampled) sampled.recycle()
                return bos.toByteArray()
            }
        }
    }

    private fun computeInSampleSize(origW: Int, origH: Int, reqW: Int, reqH: Int): Int {
        var inSampleSize = 1
        if (origH > reqH || origW > reqW) {
            var halfHeight = origH / 2
            var halfWidth = origW / 2
            while ((halfHeight / inSampleSize) >= reqH && (halfWidth / inSampleSize) >= reqW) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    /** PNG 바이트 배열 → Bitmap */
    fun decodePng(bytes: ByteArray): Bitmap {
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error("Failed to decode PNG bytes")
    }
}


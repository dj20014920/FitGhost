package com.fitghost.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * 이미지 입출력 공통 유틸리티
 *
 * DRY 원칙에 따라 이미지 처리 로직을 중앙화합니다.
 * - URI → Bitmap 변환
 * - 해상도 제한 (비율 유지)
 * - PNG/JPEG 인코딩
 * - 파일 저장
 */
object ImageUtils {

    /**
     * URI → PNG 바이트 배열 (원본 크기 유지)
     *
     * @param context Android Context
     * @param uri 이미지 URI
     * @return PNG 포맷 바이트 배열
     */
    fun uriToPngBytes(context: Context, uri: Uri): ByteArray {
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open uri: $uri")
        input.use { stream ->
            val bmp = BitmapFactory.decodeStream(stream) ?: error("Invalid image: $uri")
            val bos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
            bmp.recycle()
            return bos.toByteArray()
        }
    }

    /**
     * URI → PNG 바이트 배열 (해상도 제한, 비율 유지)
     *
     * 긴 변을 기준으로 축소하되 가로세로 비율을 완벽히 유지합니다. API 과금 최적화를 위해 사용됩니다.
     *
     * @param context Android Context
     * @param uri 이미지 URI
     * @param maxSide 긴 변 최대 픽셀 (기본 1024)
     * @return PNG 포맷 바이트 배열
     */
    fun uriToPngBytesCapped(context: Context, uri: Uri, maxSide: Int = 1024): ByteArray {
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open uri: $uri")
        input.use { stream ->
            // 1) 원본 크기만 읽기 (메모리 효율적)
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream, null, boundsOpts)
            val origW = boundsOpts.outWidth
            val origH = boundsOpts.outHeight
            require(origW > 0 && origH > 0) { "Invalid image bounds: $uri" }

            // 2) 축소 배율 계산 (가로세로 비율 유지)
            val scale =
                    if (origW <= maxSide && origH <= maxSide) {
                        1.0
                    } else {
                        maxOf(origW.toDouble() / maxSide, origH.toDouble() / maxSide)
                    }
            val targetW = (origW / scale).toInt().coerceAtLeast(1)
            val targetH = (origH / scale).toInt().coerceAtLeast(1)

            // 3) inSampleSize로 1차 다운샘플 (메모리 효율적)
            val sampleSize = computeInSampleSize(origW, origH, targetW, targetH)

            // 스트림을 다시 열어야 함 (이미 소비됨)
            val input2 =
                    context.contentResolver.openInputStream(uri) ?: error("Cannot reopen uri: $uri")
            input2.use { stream2 ->
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val sampled =
                        BitmapFactory.decodeStream(stream2, null, decodeOpts)
                                ?: error("Invalid image after sampling: $uri")

                // 4) 정확한 크기로 2차 조정
                val needExactScale = sampled.width != targetW || sampled.height != targetH
                val finalBmp =
                        if (needExactScale) {
                            Bitmap.createScaledBitmap(sampled, targetW, targetH, true)
                        } else {
                            sampled
                        }

                // 5) PNG 인코딩
                val bos = ByteArrayOutputStream()
                finalBmp.compress(Bitmap.CompressFormat.PNG, 100, bos)

                // 메모리 정리
                if (finalBmp !== sampled) sampled.recycle()
                finalBmp.recycle()

                return bos.toByteArray()
            }
        }
    }

    /**
     * inSampleSize 계산 (2의 거듭제곱)
     *
     * BitmapFactory는 2의 거듭제곱 inSampleSize만 효율적으로 처리합니다.
     */
    private fun computeInSampleSize(origW: Int, origH: Int, reqW: Int, reqH: Int): Int {
        var inSampleSize = 1
        if (origH > reqH || origW > reqW) {
            val halfHeight = origH / 2
            val halfWidth = origW / 2
            while ((halfHeight / inSampleSize) >= reqH && (halfWidth / inSampleSize) >= reqW) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    /**
     * PNG 바이트 배열 → Bitmap
     *
     * @param bytes PNG 포맷 바이트 배열
     * @return Bitmap 객체
     */
    fun decodePng(bytes: ByteArray): Bitmap {
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: error("Failed to decode PNG bytes")
    }

    /**
     * URI → JPEG 파일 저장 (해상도 제한, 비율 유지)
     *
     * 옷장 이미지 저장 등에 사용됩니다.
     *
     * @param context Android Context
     * @param uri 원본 이미지 URI
     * @param outputFile 저장할 파일
     * @param maxDimension 긴 변 최대 픽셀 (기본 1280)
     * @param quality JPEG 품질 (0-100, 기본 85)
     * @return 성공 여부
     */
    fun saveAsJpeg(
            context: Context,
            uri: Uri,
            outputFile: File,
            maxDimension: Int = 1280,
            quality: Int = 85
    ): Boolean {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return false

            input.use { stream ->
                // 1) 원본 크기 확인
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, boundsOpts)
                val origW = boundsOpts.outWidth
                val origH = boundsOpts.outHeight

                if (origW <= 0 || origH <= 0) return false

                // 2) 축소 배율 계산
                val scale =
                        if (origW <= maxDimension && origH <= maxDimension) {
                            1.0
                        } else {
                            maxOf(origW.toDouble() / maxDimension, origH.toDouble() / maxDimension)
                        }
                val targetW = (origW / scale).toInt().coerceAtLeast(1)
                val targetH = (origH / scale).toInt().coerceAtLeast(1)

                // 3) inSampleSize 계산
                val sampleSize = computeInSampleSize(origW, origH, targetW, targetH)

                // 4) 스트림 재오픈 및 디코딩
                val input2 = context.contentResolver.openInputStream(uri) ?: return false
                input2.use { stream2 ->
                    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                    val sampled =
                            BitmapFactory.decodeStream(stream2, null, decodeOpts) ?: return false

                    // 5) 정확한 크기로 조정
                    val finalBmp =
                            if (sampled.width != targetW || sampled.height != targetH) {
                                val scaled =
                                        Bitmap.createScaledBitmap(sampled, targetW, targetH, true)
                                sampled.recycle()
                                scaled
                            } else {
                                sampled
                            }

                    // 6) JPEG 저장
                    FileOutputStream(outputFile).use { fos ->
                        finalBmp.compress(Bitmap.CompressFormat.JPEG, quality, fos)
                    }

                    finalBmp.recycle()
                    true
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Bitmap → JPEG 파일 저장
     *
     * @param bitmap Bitmap 객체
     * @param outputFile 저장할 파일
     * @param quality JPEG 품질 (0-100, 기본 85)
     * @return 성공 여부
     */
    fun saveBitmapAsJpeg(bitmap: Bitmap, outputFile: File, quality: Int = 85): Boolean {
        return try {
            FileOutputStream(outputFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}

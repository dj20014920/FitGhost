package com.fitghost.app.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.fitghost.app.data.nanobanana.GeminiClient

/**
 * Gemini 기반 Try-On 엔진 구현.
 *
 * 특성
 * - TryOnEngine 인터페이스를 구현하여 TryOnRepository에 플러그인처럼 연결됨.
 * - GeminiClient를 사용해 "이미지 편집(Edit)" 호출을 수행하고, 결과 이미지를 Bitmap으로 반환.
 * - API 키 미설정 또는 호출 오류 시, 기존 FakeTryOnEngine으로 우회하여 안정성 확보(KISS/DRY).
 *
 * 주의
 * - API 키는 ApiKeys 유틸리티를 통해 안전하게 저장/접근함.
 * - GeminiClient에서 예외 발생 시 FakeTryOnEngine으로 우회 처리하여 안정성 확보.
 */
class GeminiTryOnEngine : TryOnEngine {

    private val fallbackEngine = FakeTryOnEngine()

    override suspend fun preview(context: Context, image: Bitmap, part: String): Bitmap {
        // 기본 시그니처는 의류 이미지가 없는 흐름으로 위임
        return preview(context, image, part, garment = null)
    }

    override suspend fun preview(
            context: Context,
            image: Bitmap,
            part: String,
            garment: Bitmap?
    ): Bitmap {
        // 1) API 키 부재/호출 실패를 고려하여 항상 예외 가드
        return runCatching {
            val client = GeminiClient(context)

            // 2) 입력 이미지를 전송 가능한 크기/형식으로 준비
            //    - 네트워크/메모리 부담을 줄이기 위해 과대 이미지는 축소
            val prepared = image.coerceMaxDimension(maxDim = 1600)
            val subjectBytes = client.bitmapToPngBytes(prepared)

            // 선택 의류 이미지가 있으면 함께 전송
            val images: List<ByteArray> =
                    if (garment != null) {
                        val garmentPrepared = garment.coerceMaxDimension(maxDim = 1200)
                        val garmentBytes = client.bitmapToPngBytes(garmentPrepared)
                        listOf(subjectBytes, garmentBytes)
                    } else {
                        listOf(subjectBytes)
                    }

            // 3) 프롬프트 구성
            val koreanPart =
                    when (part.uppercase()) {
                        "TOP" -> "상의"
                        "BOTTOM" -> "하의"
                        else -> "의류"
                    }

            val prompt = buildString {
                append("Using the provided person image")
                if (garment != null) append(" and garment image")
                append(", generate a realistic try-on preview for the $koreanPart area. ")
                append(
                        "Keep the face and background unchanged. Maintain natural lighting, proportions, and textures. "
                )
                append("Align shoulder/waist/hips appropriately depending on category. ")
                append("Avoid body morphing and preserve skintone. ")
                append("If unsure, minimally adjust and keep realism.")
            }

            val systemPrompt = com.fitghost.app.data.nanobanana.VtoSystemPrompt.default()

            // 4) Gemini 이미지 편집 호출 (주 이미지 + 선택 의류 이미지)
            val outputs: List<ByteArray> =
                    client.editImage(
                            prompt = prompt,
                            images = images,
                            model = GeminiClient.DEFAULT_IMAGE_MODEL,
                            systemPrompt = systemPrompt
                    )

            // 5) 결과가 없으면 실패로 간주 → fallback
            val first = outputs.firstOrNull() ?: error("Gemini returned empty result")

            // 6) ByteArray → Bitmap
            BitmapFactory.decodeByteArray(first, 0, first.size)
                    ?: error("Failed to decode result image")
        }
                .getOrElse {
                    // API 키 미설정/네트워크 오류/디코드 실패 등 모든 경우에 대해
                    // 일관된 사용자 경험을 보장하기 위해 fallback 엔진으로 우회
                    fallbackEngine.preview(context, image, part)
                }
    }

    /** 큰 이미지는 네트워크/메모리 효율을 위해 축소 */
    private fun Bitmap.coerceMaxDimension(maxDim: Int): Bitmap {
        val w = width
        val h = height
        val maxCurrent = maxOf(w, h)
        if (maxCurrent <= maxDim) return this
        val scale = maxDim.toFloat() / maxCurrent.toFloat()
        val nw = (w * scale).toInt().coerceAtLeast(1)
        val nh = (h * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(this, nw, nh, true)
    }
}

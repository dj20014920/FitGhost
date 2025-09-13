package com.fitghost.app.engine

import android.content.Context
import android.graphics.Bitmap

/** TryOn 엔진 인터페이스 — 교체 가능 설계 */
interface TryOnEngine {
    /**
     * @param image 입력 이미지
     * @param part "TOP" or "BOTTOM"
     * @return 프리뷰 비트맵 (워터마크 포함 가능)
     */
    suspend fun preview(context: Context, image: Bitmap, part: String): Bitmap

    /**
     * 선택 의류 이미지를 함께 전달하는 오버로드. 기본 구현은 기존 시그니처로 위임하여 하위 호환을 유지한다.
     *
     * @param garment Try-On 합성을 유도할 선택적 의류 이미지(없으면 null)
     */
    suspend fun preview(context: Context, image: Bitmap, part: String, garment: Bitmap?): Bitmap {
        // 기본 구현: 기존 시그니처 호출(하위 호환)
        return preview(context, image, part)
    }
}

class FakeTryOnEngine : TryOnEngine {
    override suspend fun preview(context: Context, image: Bitmap, part: String): Bitmap {
        // 간단 톤 보정 + 상/하 반 워터마크 오버레이
        val w = image.width
        val h = image.height
        val out = image.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(out)
        val paint = android.graphics.Paint().apply { alpha = 160 }
        val rect =
                if (part == "TOP") android.graphics.Rect(0, 0, w, h / 2)
                else android.graphics.Rect(0, h / 2, w, h)
        canvas.drawRect(rect, paint)
        // 워터마크
        val textPaint =
                android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = (w * 0.06f)
                    isAntiAlias = true
                    setShadowLayer(4f, 2f, 2f, android.graphics.Color.BLACK)
                }
        canvas.drawText("AI PREVIEW", (w * 0.05f), (h * 0.95f), textPaint)
        return out
    }
}

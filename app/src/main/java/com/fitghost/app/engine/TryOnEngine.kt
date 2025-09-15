package com.fitghost.app.engine

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri

/**
 * Try-On 엔진 인터페이스 (PRD 4.A / 10.x 참조)
 * - 프리뷰/렌더 공정 중 필요한 최소 기능만 정의
 * - 온디바이스 처리, 외부 전송 금지
 */
interface TryOnEngine {
    /**
     * 선택된 모델 이미지와 의상 이미지를 사용해 프리뷰 비트맵을 생성한다.
     * 간단 합성 + 워터마크("AI PREVIEW")를 적용한다.
     */
    suspend fun renderPreview(
        context: Context,
        modelUri: Uri,
        clothingUri: Uri
    ): Bitmap
}

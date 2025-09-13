package com.fitghost.app.data

import android.content.Context
import android.graphics.Bitmap
import com.fitghost.app.engine.TryOnEngine

/**
 * Try-On 실행 리포지토리.
 *
 * 책임
 * - 크레딧 상태를 확인·소비
 * - 엔진(TryOnEngine)으로 프리뷰 생성
 * - 로컬 PNG 저장(LocalImageStore)
 *
 * 교체 포인트
 * - 엔진은 인터페이스 주입으로 교체 가능(서버리스/AI 엔진 대응)
 *
 * 보안/정책
 * - 이미지·메타데이터는 단말 로컬에만 저장/처리
 *
 * @property context Android Context (엔진 및 저장에 필요)
 * @property creditStore 주당 무료 10회/보너스 관리 DataStore
 * @property engine Try-On 엔진(교체 가능)
 * @property imageStore 로컬 PNG 저장/공유 URI 제공
 */
class TryOnRepository(
        private val context: Context,
        private val creditStore: CreditStore,
        private val engine: TryOnEngine,
        private val imageStore: LocalImageStore
) {
    /**
     * Try-On 수행 결과.
     *
     * - Success: 저장된 PNG의 content Uri
     * - NoCredit: 크레딧 부족(광고 보상 유도 필요)
     * - Error: 예외 래핑(저장 실패 등)
     */
    sealed interface Result {
        data class Success(val uri: android.net.Uri) : Result
        data object NoCredit : Result
        data class Error(val throwable: Throwable) : Result
    }

    /**
     * Try-On을 수행한다.
     *
     * 절차: 크레딧 소비 → 엔진 프리뷰 → PNG 저장 → URI 반환
     *
     * @param image 입력 비트맵
     * @param part "TOP" 또는 "BOTTOM"
     * @return [Result] Success/NoCredit/Error
     */
    suspend fun runTryOn(image: Bitmap, part: String): Result {
        // 기존 시그니처는 의류 이미지 없이 호출 → 확장 오버로드로 위임
        return runTryOn(image = image, part = part, garment = null)
    }

    /**
     * Try-On을 수행한다(의류 이미지 옵션 포함).
     *
     * 절차: 크레딧 소비 → 엔진 프리뷰 → PNG 저장 → URI 반환
     *
     * @param image 입력 비트맵(사용자 사진)
     * @param part "TOP" 또는 "BOTTOM"
     * @param garment 선택 의류 비트맵(null 가능)
     * @return [Result] Success/NoCredit/Error
     */
    suspend fun runTryOn(image: Bitmap, part: String, garment: Bitmap?): Result {
        try {
            val ok = creditStore.consumeOne()
            if (!ok) {
                return Result.NoCredit
            }
            val preview =
                    if (garment != null) {
                        engine.preview(context, image, part, garment)
                    } else {
                        engine.preview(context, image, part)
                    }
            val saved = imageStore.saveTryOnPng(preview)
            return Result.Success(saved.uri)
        } catch (t: Throwable) {
            return Result.Error(t)
        }
    }
}

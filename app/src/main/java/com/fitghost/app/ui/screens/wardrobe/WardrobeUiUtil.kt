package com.fitghost.app.ui.screens.wardrobe

import com.fitghost.app.constants.CategoryConstants

/**
 * Wardrobe UI 전용 공통 유틸리티.
 *
 * 목적 (DRY):
 * - 여러 화면(목록, 추가/편집 폼 등)에서 카테고리 관련 유틸리티 제공
 * - 동적 카테고리 시스템 지원 (기본 + 사용자 정의)
 *
 * 변경 사항 (v2):
 * - WardrobeCategory enum 제거
 * - String 기반 동적 카테고리로 전환
 * - CategoryEntity와 연동
 *
 * 설계 원칙:
 * - KISS: 단순 문자열 처리
 * - SRP: UI 표현용 유틸리티만 제공
 */
object WardrobeUiUtil {

    /**
     * 카테고리 ID → 사용자 노출 라벨 변환.
     * 
     * 동적 카테고리 시스템에서는 ID와 displayName이 동일하므로
     * 그대로 반환합니다. (향후 다국어 지원 시 확장 가능)
     *
     * @param categoryId 카테고리 ID (예: "상의", "양말")
     * @return 사용자 친화적 표시 문자열
     */
    @JvmStatic
    fun categoryLabel(categoryId: String): String = categoryId

    /**
     * 기본 카테고리 ID 목록 (하드코딩된 기본값)
     * Migration과 CategoryRepository에서 동일한 값 사용
     */
    @JvmStatic
    val DEFAULT_CATEGORY_IDS = CategoryConstants.DEFAULT_CATEGORY_IDS
}

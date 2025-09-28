package com.fitghost.app.ui.screens.wardrobe

import com.fitghost.app.data.db.WardrobeCategory

/**
 * Wardrobe UI 전용 공통 유틸리티.
 *
 * 목적 (DRY):
 * - 여러 화면(목록, 추가/편집 폼 등)에서 카테고리 ↔ 한글 라벨 매핑 로직을 단일화
 * - 같은 로직이 다른 파일에 중복 정의되는 것을 방지
 *
 * 확장 여지:
 * - 추후 다국어(i18n) 도입 시 이 레이어를 교체하거나 Locale 분기 추가
 * - 역방향 파싱(라벨→엔티티) 필요 시 safe parser 함수 추가 가능
 *
 * 설계 원칙:
 * - KISS: 단순 when 매핑
 * - YAGNI: 아직 필요 없는 포맷터/캐시/리소스 ID 참조는 추가하지 않음
 * - SRP: UI 표현용 문자열 변환만 수행 (비즈니스 로직과 분리)
 */
object WardrobeUiUtil {

    /**
     * WardrobeCategory → 사용자 노출 한글 라벨 변환.
     *
     * @param category WardrobeCategory enum 값
     * @return 사용자 친화적 한글 표시 문자열
     */
    @JvmStatic
    fun categoryLabel(category: WardrobeCategory): String =
            when (category) {
                WardrobeCategory.TOP -> "상의"
                WardrobeCategory.BOTTOM -> "하의"
                WardrobeCategory.OUTER -> "아우터"
                WardrobeCategory.SHOES -> "신발"
                WardrobeCategory.ACCESSORY -> "악세서리"
                WardrobeCategory.OTHER -> "기타"
            }

    /**
     * (선택적 확장용) 한글 라벨을 다시 enum 으로 역매핑해야 할 경우를 대비한 안전 파서. 현재 사용하지 않지만 중복 구현을 예방하기 위해 중심화 가능.
     *
     * 사용 전:
     * - 라벨이 UI 수정(문구 변경)으로 달라지면 역매핑이 실패할 수 있음
     * - 가능하면 내부 로직에서는 enum 자체를 전달하고, 문자열은 최종 노출 직전에만 변환
     *
     * @param label 한글 라벨 (categoryLabel() 반환값 기준)
     * @return 매칭되는 WardrobeCategory 또는 null
     */
    @JvmStatic
    fun parseCategoryOrNull(label: String): WardrobeCategory? =
            when (label.trim()) {
                "상의" -> WardrobeCategory.TOP
                "하의" -> WardrobeCategory.BOTTOM
                "아우터" -> WardrobeCategory.OUTER
                "신발" -> WardrobeCategory.SHOES
                "악세서리" -> WardrobeCategory.ACCESSORY
                "기타" -> WardrobeCategory.OTHER
                else -> null
            }
}

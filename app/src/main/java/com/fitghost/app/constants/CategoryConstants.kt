package com.fitghost.app.constants

import com.fitghost.app.data.db.CategoryEntity

/**
 * 카테고리 시스템 상수 및 헬퍼 (Single Source of Truth)
 * - 기본 카테고리 ID 목록
 * - 최대 개수 제한
 * - 폴백 카테고리(ID: "기타")
 * - Repository/DB Migration/UI에서 공통 참조하도록 제공
 */
object CategoryConstants {
    /** 기본 카테고리 ID (표시 이름과 동일) */
    val DEFAULT_CATEGORY_IDS: List<String> = listOf(
        "상의", "하의", "아우터", "신발", "악세서리", "기타"
    )

    /** 카테고리 최대 개수 */
    const val MAX_CATEGORIES: Int = 12

    /** 폴백 카테고리 (삭제/이동 시 기본 목적지) */
    const val FALLBACK_CATEGORY: String = "기타"

    /** 기본 카테고리를 CategoryEntity 리스트로 변환 */
    fun toEntities(isDefault: Boolean = false): List<CategoryEntity> =
        DEFAULT_CATEGORY_IDS.mapIndexed { index, id ->
            CategoryEntity(
                id = id,
                displayName = id,
                isDefault = isDefault,
                orderIndex = index
            )
        }

    /** Migration용 SQL VALUES 문자열 생성 (isDefault=0으로 고정) */
    fun toSqlInsertValues(timestamp: Long): String =
        DEFAULT_CATEGORY_IDS.mapIndexed { index, id ->
            "('" + id + "', '" + id + "', 0, $index, $timestamp)"
        }.joinToString(", ")
}


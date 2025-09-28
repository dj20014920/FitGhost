package com.fitghost.app.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * LEGACY MODELS (NanoBanana) — Frozen for compatibility. Use Gemini types for new code.
 *
 * 이 파일은 호환성 유지를 위해 유지됩니다. 신규 개발은 GeminiFashionService 및 관련 타입을 사용하세요.
 */

/** 나노바나나 API 요청 모델 */
@Deprecated(
        "LEGACY: Replaced by Gemini types. Do not use in new code.",
        level = DeprecationLevel.WARNING
)
@JsonClass(generateAdapter = true)
data class NanoBananaRequest(
        @Json(name = "prompt") val prompt: String,
        @Json(name = "model") val model: String = "nanobanana-fashion-v1",
        @Json(name = "max_tokens") val maxTokens: Int = 1000,
        @Json(name = "temperature") val temperature: Double = 0.7,
        @Json(name = "context") val context: NanoBananaContext? = null
)

/** 나노바나나 API 컨텍스트 정보 */
@Deprecated(
        "LEGACY: Replaced by Gemini types. Do not use in new code.",
        level = DeprecationLevel.WARNING
)
@JsonClass(generateAdapter = true)
data class NanoBananaContext(
        @Json(name = "user_preferences") val userPreferences: UserPreferences? = null,
        @Json(name = "wardrobe_items") val wardrobeItems: List<WardrobeItem>? = null,
        @Json(name = "weather_info") val weatherInfo: WeatherInfo? = null,
        @Json(name = "occasion") val occasion: String? = null
)

/** 사용자 선호도 정보 */
@Deprecated(
        "LEGACY: Replaced by Gemini types. Do not use in new code.",
        level = DeprecationLevel.WARNING
)
@JsonClass(generateAdapter = true)
data class UserPreferences(
        @Json(name = "style") val style: String? = null, // "casual", "formal", "street", etc.
        @Json(name = "colors") val colors: List<String>? = null,
        @Json(name = "brands") val brands: List<String>? = null,
        @Json(name = "price_range") val priceRange: PriceRange? = null
)

/** 가격 범위 */
@Deprecated(
        "LEGACY: Replaced by Gemini types. Do not use in new code.",
        level = DeprecationLevel.WARNING
)
@JsonClass(generateAdapter = true)
data class PriceRange(
        @Json(name = "min") val min: Int? = null,
        @Json(name = "max") val max: Int? = null
)

/** 옷장 아이템 정보 */
@Deprecated(
        "LEGACY: Replaced by Gemini types. Do not use in new code.",
        level = DeprecationLevel.WARNING
)
@JsonClass(generateAdapter = true)
data class WardrobeItem(
        @Json(name = "id") val id: String,
        @Json(name = "category") val category: String,
        @Json(name = "color") val color: String? = null,
        @Json(name = "brand") val brand: String? = null,
        @Json(name = "style") val style: String? = null
)

/** 날씨 정보 */
@Deprecated(
        "LEGACY: Replaced by Gemini types. Do not use in new code.",
        level = DeprecationLevel.WARNING
)
@JsonClass(generateAdapter = true)
data class WeatherInfo(
        @Json(name = "temperature") val temperature: Double,
        @Json(name = "condition") val condition: String, // "sunny", "rainy", "cloudy", etc.
        @Json(name = "humidity") val humidity: Double? = null
)

/** 나노바나나 API 응답 모델 */
@Deprecated(
        "LEGACY: Replaced by Gemini types. Do not use in new code.",
        level = DeprecationLevel.WARNING
)
@JsonClass(generateAdapter = true)
data class NanoBananaResponse(
        @Json(name = "id") val id: String,
        @Json(name = "object") val objectType: String,
        @Json(name = "created") val created: Long,
        @Json(name = "model") val model: String,
        @Json(name = "choices") val choices: List<NanoBananaChoice>,
        @Json(name = "usage") val usage: NanoBananaUsage? = null
)

/** 나노바나나 API 선택지 */
@Deprecated(
        "LEGACY: Replaced by Gemini types. Do not use in new code.",
        level = DeprecationLevel.WARNING
)
@JsonClass(generateAdapter = true)
data class NanoBananaChoice(
        @Json(name = "index") val index: Int,
        @Json(name = "message") val message: NanoBananaMessage,
        @Json(name = "finish_reason") val finishReason: String
)

/** 나노바나나 API 메시지 */
@Deprecated(
        "LEGACY: Replaced by Gemini types. Do not use in new code.",
        level = DeprecationLevel.WARNING
)
@JsonClass(generateAdapter = true)
data class NanoBananaMessage(
        @Json(name = "role") val role: String,
        @Json(name = "content") val content: String
)

/** 나노바나나 API 사용량 정보 */
@Deprecated(
        "LEGACY: Replaced by Gemini types. Do not use in new code.",
        level = DeprecationLevel.WARNING
)
@JsonClass(generateAdapter = true)
data class NanoBananaUsage(
        @Json(name = "prompt_tokens") val promptTokens: Int,
        @Json(name = "completion_tokens") val completionTokens: Int,
        @Json(name = "total_tokens") val totalTokens: Int
)

/** 나노바나나 API 에러 응답 */
@Deprecated(
        "LEGACY: Replaced by Gemini types. Do not use in new code.",
        level = DeprecationLevel.WARNING
)
@JsonClass(generateAdapter = true)
data class NanoBananaError(@Json(name = "error") val error: NanoBananaErrorDetail)

/** 나노바나나 API 에러 상세 정보 */
@Deprecated(
        "LEGACY: Replaced by Gemini types. Do not use in new code.",
        level = DeprecationLevel.WARNING
)
@JsonClass(generateAdapter = true)
data class NanoBananaErrorDetail(
        @Json(name = "message") val message: String,
        @Json(name = "type") val type: String,
        @Json(name = "code") val code: String? = null
)

/** 패션 추천 결과 (파싱된 응답) */
data class FashionRecommendation(
        val id: String,
        val title: String,
        val description: String,
        val recommendedItems: List<RecommendedItem>,
        val reasoning: String,
        val confidence: Float,
        val occasion: String? = null
)

/** 추천 아이템 */
data class RecommendedItem(
        val category: String,
        val description: String,
        val color: String? = null,
        val style: String? = null,
        val priceRange: String? = null,
        val searchKeywords: List<String> = emptyList()
)

package com.fitghost.app.domain

import com.fitghost.app.data.model.Garment
import com.fitghost.app.data.model.WeatherSnapshot

/**
 * 날씨 기반 추천 스코어러.
 *
 * 목적
 * - 현재 날씨 스냅샷과 로컬 옷장(Garment) 목록을 입력으로 받아 코디 조합에 점수를 부여하고 TOP N을 추출한다.
 * - ContextEngine을 활용하여 날씨/TPO/선호/금기 정보를 종합적으로 반영한 추천을 제공한다.
 *
 * 스코어 구성요소
 * - 기온/보온(warmth) 적합도
 * - 강수 시 방수(outer.waterResist) 가점/감점
 * - 풍속이 높을 때 겉옷 존재 가점
 * - 태그 일치도(동일 태그 중복수 기반 간단 가중)
 * - TPO(Time, Place, Occasion) 적합도
 * - 사용자 선호/금기 태그 반영
 *
 * 교체/확장
 * - 도메인 규칙 변경 시 score() 함수의 가중치/공식을 조정한다.
 * - 실험적 규칙은 새 함수로 분리해 테스트를 추가하고, recommend()에서 전략 주입을 고려한다.
 * - ContextEngine을 통해 다양한 컨텍스트 요소를 추가할 수 있다.
 *
 * 제약/가정
 * - type은 "T"(상의), "B"(하의), "O"(겉옷)만 사용한다.
 * - warmth 범위는 1..5를 가정한다.
 */
class OutfitRecommender(private val contextEngine: ContextEngine = ContextEngine()) {
    data class Outfit(val top: Garment?, val bottom: Garment?, val outer: Garment?)
    data class Scored(val outfit: Outfit, val score: Double)

    /**
     * 기본 추천 메서드 - 날씨 정보만 활용
     *
     * @param wardrobe 옷장 목록
     * @param weather 날씨 스냅샷
     * @param topN 추천할 상위 N개 코디
     * @return 점수가 높은 순으로 정렬된 상위 N개 코디 목록
     */
    fun recommend(wardrobe: List<Garment>, weather: WeatherSnapshot, topN: Int = 3): List<Scored> {
        val tops = wardrobe.filter { it.type == "T" }
        val bottoms = wardrobe.filter { it.type == "B" }
        val outers = listOf(null) + wardrobe.filter { it.type == "O" }
        val combos = mutableListOf<Scored>()
        for (t in tops) for (b in bottoms) for (o in outers) {
            val outfit = Outfit(t, b, o)
            val s = score(outfit, weather)
            combos += Scored(outfit, s)
        }
        return combos.sortedByDescending { it.score }.take(topN)
    }
    
    /**
     * 확장 추천 메서드 - ContextEngine을 활용하여 날씨/TPO/선호/금기 정보를 종합적으로 반영
     *
     * @param wardrobe 옷장 목록
     * @param weather 날씨 스냅샷
     * @param tpoText TPO 텍스트 정보 (선택적)
     * @param preferences 사용자 선호/금기 정보 (선택적)
     * @param topN 추천할 상위 N개 코디
     * @return 점수가 높은 순으로 정렬된 상위 N개 코디 목록
     */
    fun recommendWithContext(
        wardrobe: List<Garment>, 
        weather: WeatherSnapshot, 
        tpoText: String? = null,
        preferences: Pair<List<String>, List<String>>? = null,
        topN: Int = 3
    ): List<Scored> {
        // 컨텍스트 정보 구성
        val context = contextEngine.buildContext(weather, tpoText, preferences)
        
        val tops = wardrobe.filter { it.type == "T" }
        val bottoms = wardrobe.filter { it.type == "B" }
        val outers = listOf(null) + wardrobe.filter { it.type == "O" }
        val combos = mutableListOf<Scored>()
        
        for (t in tops) for (b in bottoms) for (o in outers) {
            val outfit = Outfit(t, b, o)
            // 기본 점수 계산
            val baseScore = score(outfit, weather)
            // 컨텍스트 기반 점수 조정
            val adjustedScore = contextEngine.adjustScoreByContext(outfit, context, baseScore)
            combos += Scored(outfit, adjustedScore)
        }
        
        return combos.sortedByDescending { it.score }.take(topN)
    }

    fun score(o: Outfit, w: WeatherSnapshot): Double {
        var s = 0.0
        val warmthSum = listOfNotNull(o.top?.warmth, o.bottom?.warmth, o.outer?.warmth).sum()
        val tempPenalty = kotlin.math.abs((w.temperatureC - 20) / 10.0 - (warmthSum / 10.0))
        s += 10 - tempPenalty * 10
        if (w.precipitationMm > 0.5) {
            if (o.outer?.waterResist == true) s += 5 else s -= 5
        }
        if (w.windSpeed > 6.0 && o.outer != null) s += 2
        // 태그 간단 가중: 동일 태그가 많을수록 +
        val tags = (o.top?.tags ?: emptyList()) + (o.bottom?.tags ?: emptyList()) + (o.outer?.tags ?: emptyList())
        s += tags.groupBy { it }.values.sumOf { it.size / 2.0 }
        return s
    }
}

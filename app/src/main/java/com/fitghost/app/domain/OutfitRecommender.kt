package com.fitghost.app.domain

import com.fitghost.app.data.db.WardrobeCategory
import com.fitghost.app.data.db.WardrobeItemEntity
import com.fitghost.app.data.weather.WeatherSnapshot
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class OutfitPlan(
    val id: String,
    val mainPieces: List<WardrobeItemEntity>,
    val outerLayer: WardrobeItemEntity?,
    val shoes: WardrobeItemEntity?,
    val accessories: List<WardrobeItemEntity>,
    val score: Double,
    val styleTags: Set<String>,
    val reasons: List<String>,
    val title: String,
    val summary: String
)

private data class ScoredItem(
    val item: WardrobeItemEntity,
    val score: Double,
    val styleTags: Set<String>,
    val reasons: List<String>
)

/**
 * 옷장 아이템과 날씨를 기반으로 최적의 코디 조합을 생성하는 도메인 컴포넌트.
 * - 가온/한랭/바람 조건을 고려하여 상하의/아우터를 선택
 * - 태그, 이름, 색상 정보를 활용해 스타일 태그 및 추천 사유 생성
 * - 날짜 기반 시드를 사용하여 매일 다른 추천 제공
 */
class OutfitRecommender {

    fun recommend(
        weather: WeatherSnapshot,
        wardrobe: List<WardrobeItemEntity>,
        maxOutfits: Int = 10,
        dailySeed: Long = System.currentTimeMillis() / (24 * 60 * 60 * 1000) // 날짜 기반 시드
    ): List<OutfitPlan> {
        if (wardrobe.isEmpty()) return emptyList()

        val categorized = wardrobe.groupBy { it.category }
        
        // 날짜 기반 시드로 다양성 추가
        val topScored = scoreItems(categorized[WardrobeCategory.TOP].orEmpty(), weather)
            .shuffleWithSeed(dailySeed).take(5)
        val bottomScored = scoreItems(categorized[WardrobeCategory.BOTTOM].orEmpty(), weather)
            .shuffleWithSeed(dailySeed + 1).take(5)
        val outerScored = scoreItems(categorized[WardrobeCategory.OUTER].orEmpty(), weather)
            .shuffleWithSeed(dailySeed + 2).take(5)
        val shoeScored = scoreItems(categorized[WardrobeCategory.SHOES].orEmpty(), weather)
            .shuffleWithSeed(dailySeed + 3).take(5)
        val accessoryScored = scoreItems(categorized[WardrobeCategory.ACCESSORY].orEmpty(), weather)
            .shuffleWithSeed(dailySeed + 4).take(5)
        val onePieceScored = scoreItems(categorized[WardrobeCategory.OTHER].orEmpty(), weather)
            .shuffleWithSeed(dailySeed + 5).take(5)

        val plans = mutableListOf<OutfitPlan>()
        val needsOuter = needsOuterLayer(weather)

        if (topScored.isNotEmpty() && bottomScored.isNotEmpty()) {
            val outerOptions = if (needsOuter && outerScored.isNotEmpty()) outerScored else listOf(null)
            val shoeOptions = if (shoeScored.isNotEmpty()) shoeScored else listOf(null)
            for (top in topScored) {
                for (bottom in bottomScored) {
                    for (outer in outerOptions) {
                        for (shoe in shoeOptions) {
                            val accessories = selectAccessories(accessoryScored, limit = 2)
                            val plan = buildPlan(weather, top, bottom, outer, shoe, accessories)
                            plans += plan
                        }
                    }
                }
            }
        } else if (onePieceScored.isNotEmpty()) {
            val shoeOptions = if (shoeScored.isNotEmpty()) shoeScored else listOf(null)
            for (onePiece in onePieceScored) {
                for (shoe in shoeOptions) {
                    val accessories = selectAccessories(accessoryScored, limit = 2)
                    val plan = buildOnePiecePlan(weather, onePiece, shoe, accessories)
                    plans += plan
                }
            }
        }

        // 점수 기반 정렬 후 상위권에서 다양성 추가
        val sortedPlans = plans
            .distinctBy { it.id }
            .sortedByDescending { it.score }
        
        // 상위 점수대(top 30%)에서 날짜 기반으로 선택하여 다양성 확보
        val topTierCount = (sortedPlans.size * 0.3).toInt().coerceAtLeast(maxOutfits)
        val topTier = sortedPlans.take(topTierCount)
        
        return topTier
            .shuffleWithSeed(dailySeed + 100)
            .take(maxOutfits)
    }
    
    /**
     * 시드 기반 셔플로 같은 날에는 일관된 결과, 다른 날에는 다른 결과 제공
     */
    private fun <T> List<T>.shuffleWithSeed(seed: Long): List<T> {
        val random = kotlin.random.Random(seed)
        return this.shuffled(random)
    }

    private fun scoreItems(
        items: List<WardrobeItemEntity>,
        weather: WeatherSnapshot
    ): List<ScoredItem> {
        return items.map { item ->
            val analysis = analyzeItem(item, weather)
            val score = analysis.score + (if (item.favorite) 0.4 else 0.0)
            val reasons = analysis.reasons.toMutableList()
            if (item.favorite) reasons += "즐겨찾는 아이템으로 안정적인 착용감"
            ScoredItem(
                item = item,
                score = score,
                styleTags = analysis.tags,
                reasons = reasons
            )
        }.sortedByDescending { it.score }
    }

    private data class ItemAnalysis(
        val score: Double,
        val tags: Set<String>,
        val reasons: List<String>
    )

    private fun analyzeItem(item: WardrobeItemEntity, weather: WeatherSnapshot): ItemAnalysis {
        val tokens = buildString {
            append(item.name.lowercase())
            item.color?.let { append(" $it") }
            item.brand?.let { append(" $it") }
            if (item.tags.isNotEmpty()) {
                append(" ")
                append(item.tags.joinToString(" ") { it.lowercase() })
            }
        }

        val tags = mutableSetOf<String>()
        val reasons = mutableListOf<String>()
        var score = 0.0

        val warmthBias = desiredWarmth(weather)

        when {
            containsAny(tokens, warmKeywords) -> {
                score += 1.8 + warmthBias.toDouble()
                tags += "warm"
                reasons += "보온성이 좋은 소재"
            }
            containsAny(tokens, coolKeywords) -> {
                score += 1.8 - abs(min(0, warmthBias)).toDouble()
                tags += "breezy"
                reasons += "통기성이 좋은 가벼운 소재"
            }
        }

        if (containsAny(tokens, layerKeywords)) {
            score += 1.2
            tags += "layering"
            reasons += "레이어드에 적합한 디자인"
        }

        if (containsAny(tokens, sportyKeywords)) {
            score += 0.8
            tags += "sporty"
            reasons += "활동적인 실루엣"
        }

        if (containsAny(tokens, formalKeywords)) {
            score += 0.9
            tags += "formal"
            reasons += "깔끔한 실루엣으로 포멀한 분위기"
        }

        if (containsAny(tokens, softKeywords)) {
            score += 0.6
            tags += "soft"
            reasons += "부드러운 촉감"
        }

        if (containsAny(tokens, trendKeywords)) {
            score += 0.5
            tags += "trendy"
            reasons += "트렌디한 디테일"
        }

        if (item.favorite) {
            tags += "favorite"
        }

        score += max(0.0, 2.5 - abs(weather.tempC - neutralTemperature(tokens))) * 0.15
        score += min(1.5, weather.windKph / 10.0) * if (containsAny(tokens, windKeywords)) 0.6 else 0.0

        if (item.tags.any { it.contains("방수") || it.contains("워터") }) {
            tags += "weatherproof"
            score += 0.7
        }

        return ItemAnalysis(score = score, tags = tags, reasons = reasons)
    }

    private fun buildPlan(
        weather: WeatherSnapshot,
        top: ScoredItem,
        bottom: ScoredItem,
        outer: ScoredItem?,
        shoes: ScoredItem?,
        accessories: List<ScoredItem>
    ): OutfitPlan {
        val pieces = mutableListOf(top.item, bottom.item)
        outer?.let { pieces += it.item }
        shoes?.let { pieces += it.item }

        val tags = mutableSetOf<String>()
        tags += top.styleTags
        tags += bottom.styleTags
        outer?.let { tags += it.styleTags }
        shoes?.let { tags += it.styleTags }
        accessories.forEach { tags += it.styleTags }

        val reasons = mutableListOf<String>()
        reasons += top.reasons
        reasons += bottom.reasons
        outer?.let { reasons += it.reasons }
        shoes?.let { reasons += it.reasons }
        accessories.forEach { reasons += it.reasons }

        val colorScore = colorHarmony(top.item.color, bottom.item.color)
        if (colorScore > 0.5) {
            reasons += "상·하의 색감이 자연스럽게 조화"
        }

        var score =
            top.score + bottom.score + colorScore + accessories.sumOf { it.score * 0.2 }
        outer?.let {
            score += it.score
        }
        shoes?.let {
            score += it.score * 0.8
        }

        if (needsOuterLayer(weather) && outer != null) {
            score += 1.0
            reasons += "찬 기온을 대비한 아우터 포함"
        } else if (!needsOuterLayer(weather) && outer == null) {
            score += 0.4
        }

        if (weather.windKph > 25 && outer == null) {
            reasons += "강한 바람이 예상되니 얇은 아우터를 고려해보세요."
            score -= 0.4
        }

        val accessoryItems = accessories.map { it.item }
        val title = generateTitle(tags)
        val summary = buildSummary(weather, tags, outer != null)

        return OutfitPlan(
            id = listOfNotNull(
                top.item.id,
                bottom.item.id,
                outer?.item?.id,
                shoes?.item?.id
            ).joinToString("-"),
            mainPieces = listOf(top.item, bottom.item),
            outerLayer = outer?.item,
            shoes = shoes?.item,
            accessories = accessoryItems,
            score = score,
            styleTags = tags,
            reasons = reasons.distinct(),
            title = title,
            summary = summary
        )
    }

    private fun buildOnePiecePlan(
        weather: WeatherSnapshot,
        onePiece: ScoredItem,
        shoes: ScoredItem?,
        accessories: List<ScoredItem>
    ): OutfitPlan {
        val tags = mutableSetOf<String>()
        tags += onePiece.styleTags
        shoes?.let { tags += it.styleTags }
        accessories.forEach { tags += it.styleTags }

        val reasons = mutableListOf<String>()
        reasons += onePiece.reasons
        shoes?.let { reasons += it.reasons }
        accessories.forEach { reasons += it.reasons }

        val score = onePiece.score +
            (shoes?.score ?: 0.0) * 0.8 +
            accessories.sumOf { it.score * 0.2 }

        val title = generateTitle(tags)
        val summary = buildSummary(weather, tags, outerIncluded = false)

        return OutfitPlan(
            id = listOfNotNull(onePiece.item.id, shoes?.item?.id).joinToString("-"),
            mainPieces = listOf(onePiece.item),
            outerLayer = null,
            shoes = shoes?.item,
            accessories = accessories.map { it.item },
            score = score,
            styleTags = tags,
            reasons = reasons.distinct(),
            title = title,
            summary = summary
        )
    }

    private fun selectAccessories(accessories: List<ScoredItem>, limit: Int): List<ScoredItem> {
        return accessories
            .sortedByDescending { it.score }
            .take(limit)
    }

    private fun colorHarmony(topColor: String?, bottomColor: String?): Double {
        if (topColor.isNullOrBlank() || bottomColor.isNullOrBlank()) return 0.3
        val topFamily = colorFamily(topColor)
        val bottomFamily = colorFamily(bottomColor)
        if (topFamily == bottomFamily) return 0.7
        return when (setOf(topFamily, bottomFamily)) {
            setOf("black", "white") -> 0.9
            setOf("navy", "white") -> 0.85
            setOf("white", "denim") -> 0.8
            setOf("gray", "black") -> 0.7
            setOf("beige", "brown") -> 0.75
            else -> 0.5
        }
    }

    private fun colorFamily(color: String): String {
        val normalized = color.lowercase()
        return when {
            normalized.contains("검") || normalized.contains("black") -> "black"
            normalized.contains("흰") || normalized.contains("화이트") || normalized.contains("white") -> "white"
            normalized.contains("회") || normalized.contains("그레이") || normalized.contains("gray") -> "gray"
            normalized.contains("네이비") || normalized.contains("파") || normalized.contains("청") || normalized.contains("denim") -> "navy"
            normalized.contains("청바지") -> "denim"
            normalized.contains("베이지") || normalized.contains("샌드") -> "beige"
            normalized.contains("갈") || normalized.contains("브라운") -> "brown"
            normalized.contains("초록") || normalized.contains("카키") || normalized.contains("그린") -> "green"
            normalized.contains("빨") || normalized.contains("레드") -> "red"
            normalized.contains("노랑") || normalized.contains("옐로우") -> "yellow"
            normalized.contains("분홍") || normalized.contains("핑크") -> "pink"
            else -> "neutral"
        }
    }

    private fun generateTitle(tags: Set<String>): String {
        return when {
            "formal" in tags && "warm" in tags -> "포멀한 윈터룩"
            "formal" in tags -> "모던 포멀 스타일"
            "sporty" in tags && "breezy" in tags -> "산뜻한 애슬레저"
            "sporty" in tags -> "스포티 캐주얼"
            "warm" in tags && "layering" in tags -> "레이어드 윈터룩"
            "warm" in tags -> "포근한 데일리룩"
            "trendy" in tags -> "트렌디 데일리 스타일"
            else -> "데일리 추천 코디"
        }
    }

    private fun buildSummary(
        weather: WeatherSnapshot,
        tags: Set<String>,
        outerIncluded: Boolean
    ): String {
        val temperature = when {
            weather.tempC <= 0 -> "매우 추운 날씨"
            weather.tempC <= 10 -> "쌀쌀한 날씨"
            weather.tempC <= 18 -> "선선한 날씨"
            weather.tempC <= 24 -> "온화한 날씨"
            else -> "더운 날씨"
        }

        val wind = when {
            weather.windKph >= 30 -> "강한 바람 예상"
            weather.windKph >= 18 -> "바람이 다소 있는 편"
            else -> "바람은 잔잔한 편"
        }

        val styleHighlight = when {
            "formal" in tags -> "세련된 실루엣으로 격식을 갖춘 자리에도 어울립니다."
            "sporty" in tags -> "활동성이 좋아 가벼운 외출이나 주말 일정에 적합합니다."
            "warm" in tags -> "보온성을 확보해 장시간 야외 활동에도 안정적입니다."
            "breezy" in tags -> "통풍이 좋아 실내외 온도 차에도 쾌적함을 유지합니다."
            else -> "데일리로 부담 없이 착용하기 좋은 조합입니다."
        }

        val outerTip = if (needsOuterLayer(weather) && !outerIncluded) {
            "얇은 아우터를 추가하면 보온성이 더 좋아집니다."
        } else {
            ""
        }

        return listOf(temperature, wind, styleHighlight, outerTip)
            .filter { it.isNotBlank() }
            .joinToString(" • ")
    }

    private fun desiredWarmth(weather: WeatherSnapshot): Int {
        return when {
            weather.tempC < 0 -> 3
            weather.tempC < 8 -> 2
            weather.tempC < 16 -> 1
            weather.tempC < 24 -> 0
            weather.tempC < 29 -> -1
            else -> -2
        }
    }

    private fun neutralTemperature(tokens: String): Double {
        return when {
            containsAny(tokens, warmKeywords) -> 8.0
            containsAny(tokens, coolKeywords) -> 24.0
            else -> 18.0
        }
    }

    private fun needsOuterLayer(weather: WeatherSnapshot): Boolean {
        return weather.tempC <= 16 || weather.windKph >= 25
    }

    private fun containsAny(target: String, candidates: Set<String>): Boolean =
        candidates.any { target.contains(it) }

    companion object Keywords {
        private val warmKeywords = setOf("패딩", "코트", "다운", "니트", "가디건", "울", "기모", "후드", "플리스", "스웨터")
        private val coolKeywords = setOf("반팔", "셔츠", "린넨", "블라우스", "시어서커", "실크", "슬리브리스", "시원", "통풍")
        private val sportyKeywords = setOf("조거", "트랙", "스포츠", "러닝", "테크", "후드티", "집업")
        private val formalKeywords = setOf("셔츠", "블레이저", "수트", "슬랙스", "자켓", "플리츠")
        private val softKeywords = setOf("캐시미어", "소프트", "플리스", "퍼", "벨벳")
        private val trendKeywords = setOf("오버사이즈", "스트라이프", "패턴", "크롭", "와이드", "트렌드")
        private val layerKeywords = setOf("가디건", "베스트", "조끼", "레이어드", "셔츠", "자켓")
        private val windKeywords = setOf("윈드", "바람막이", "재킷", "자켓", "트렌치", "코트")
    }
}

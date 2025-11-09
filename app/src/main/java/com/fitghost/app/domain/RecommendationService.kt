package com.fitghost.app.domain

import com.fitghost.app.data.db.WardrobeCategory
import com.fitghost.app.data.db.WardrobeItemEntity
import com.fitghost.app.data.repository.WardrobeRepository
import com.fitghost.app.data.model.OutfitRecommendation
import com.fitghost.app.data.model.Product
import com.fitghost.app.data.weather.WeatherRepo
import com.fitghost.app.data.weather.WeatherSnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class RecommendationParams(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val outfitLimit: Int = 6
)

data class WardrobeItemSummary(
    val id: Long,
    val name: String,
    val category: WardrobeCategory,
    val color: String?,
    val imageUri: String?,
    val favorite: Boolean
)

data class HomeOutfitRecommendation(
    val id: String,
    val title: String,
    val subtitle: String,
    val items: List<WardrobeItemSummary>,
    val styleTips: List<String>,
    val complementaryProducts: List<Product>,
    val score: Double,
    val shopQuery: String
)

data class HomeRecommendationResult(
    val weather: WeatherSnapshot,
    val outfits: List<HomeOutfitRecommendation>
)

class RecommendationService(
    private val wardrobeRepository: WardrobeRepository,
    private val weatherRepo: WeatherRepo,
    private val productSearchDataSource: ProductSearchDataSource,
    private val outfitRecommender: OutfitRecommender = OutfitRecommender(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    interface ProductSearchDataSource {
        suspend fun searchProducts(query: String, limit: Int = 10): List<Product>
    }

    suspend fun getHomeRecommendations(
        params: RecommendationParams = RecommendationParams()
    ): HomeRecommendationResult = withContext(ioDispatcher) {
        val weather = resolveWeather(params)
        val wardrobe = wardrobeRepository.observeAll().first()

        // 날짜 + 시간(10분 단위) 기반 시드로 새로고침 시 다양성 확보
        val currentTime = System.currentTimeMillis()
        val dailySeed = currentTime / (24 * 60 * 60 * 1000) // 날짜
        val timeSeed = (currentTime / (10 * 60 * 1000)) % 144 // 10분 단위 (하루 144개 슬롯)
        val combinedSeed = dailySeed * 1000 + timeSeed
        
        val plans = outfitRecommender.recommend(
            weather = weather,
            wardrobe = wardrobe,
            maxOutfits = params.outfitLimit,
            dailySeed = combinedSeed
        )

        val outfits = if (plans.isNotEmpty()) {
            plans.take(3).mapIndexed { index, plan ->
                // 각 추천마다 다른 시드 사용하여 다양한 상품 표시
                val searchSeed = dailySeed * 1000 + index
                val complementary = fetchComplementaryProducts(plan, limit = 4, searchSeed = searchSeed)
                HomeOutfitRecommendation(
                    id = plan.id,
                    title = plan.title,
                    subtitle = plan.summary,
                    items = buildWardrobeSummaries(plan),
                    styleTips = buildStyleTips(plan, weather),
                    complementaryProducts = complementary,
                    score = plan.score,
                    shopQuery = buildPrimaryQuery(plan, complementary)
                )
            }
        } else {
            fallbackHomeRecommendations(weather, dailySeed)
        }

        HomeRecommendationResult(
            weather = weather,
            outfits = outfits
        )
    }

    suspend fun getShopRecommendations(
        params: RecommendationParams = RecommendationParams()
    ): List<OutfitRecommendation> = withContext(ioDispatcher) {
        val weather = resolveWeather(params)
        val wardrobe = wardrobeRepository.observeAll().first()

        // 날짜 기반 시드 생성
        val dailySeed = System.currentTimeMillis() / (24 * 60 * 60 * 1000)
        
        val plans = outfitRecommender.recommend(
            weather = weather,
            wardrobe = wardrobe,
            maxOutfits = params.outfitLimit,
            dailySeed = dailySeed
        )

        if (plans.isNotEmpty()) {
            plans.take(params.outfitLimit).mapIndexed { index, plan ->
                val searchSeed = dailySeed * 1000 + index + 100
                val complementary = fetchComplementaryProducts(plan, limit = 6, searchSeed = searchSeed)
                OutfitRecommendation(
                    id = plan.id,
                    title = plan.title,
                    description = plan.summary,
                    recommendedProducts = complementary,
                    baseGarmentId = plan.mainPieces.firstOrNull()?.id?.toString(),
                    matchingReason = plan.reasons.joinToString(" • ")
                )
            }
        } else {
            fallbackShopRecommendations(weather, dailySeed)
        }
    }

    private suspend fun resolveWeather(params: RecommendationParams): WeatherSnapshot {
        val lat = params.latitude ?: DEFAULT_LATITUDE
        val lon = params.longitude ?: DEFAULT_LONGITUDE
        return weatherRepo.getCurrent(lat, lon)
    }

    private fun buildWardrobeSummaries(plan: OutfitPlan): List<WardrobeItemSummary> {
        val items = mutableListOf<WardrobeItemEntity>()
        items += plan.mainPieces
        plan.outerLayer?.let { items += it }
        plan.shoes?.let { items += it }
        items += plan.accessories
        return items.distinctBy { it.id }.map {
            WardrobeItemSummary(
                id = it.id,
                name = it.name,
                category = it.category,
                color = it.color,
                imageUri = it.imageUri,
                favorite = it.favorite
            )
        }
    }

    private fun buildStyleTips(plan: OutfitPlan, weather: WeatherSnapshot): List<String> {
        val tips = plan.reasons.toMutableList()
        if (weather.windKph >= 28 && plan.outerLayer == null) {
            tips += "강한 바람이 예상되니 가벼운 아우터를 추가하면 좋습니다."
        }
        if (weather.tempC >= 27 && plan.styleTags.contains("warm")) {
            tips += "기온이 높은 편이니 이너는 가볍게 조절하세요."
        }
        if (weather.tempC <= 6 && !plan.styleTags.contains("warm")) {
            tips += "한층 따뜻한 소재를 추가하면 체온 유지에 도움이 됩니다."
        }
        return tips.distinct()
    }

    private suspend fun fetchComplementaryProducts(
        plan: OutfitPlan,
        limit: Int,
        searchSeed: Long = System.currentTimeMillis() // 검색 다양성을 위한 시드
    ): List<Product> {
        val queries = buildSearchQueries(plan)
        if (queries.isEmpty()) return emptyList()

        val results = mutableListOf<Product>()
        
        // 각 검색어마다 다른 시드 사용하여 다양한 결과 확보
        queries.forEachIndexed { index, query ->
            if (results.size >= limit) return@forEachIndexed
            
            val products = productSearchDataSource.searchProducts(
                query, 
                limit = limit
            )
            
            for (product in products) {
                if (results.none { it.id == product.id }) {
                    results += product
                }
                if (results.size >= limit) break
            }
        }
        
        // 최종 결과를 시드 기반으로 셔플하여 다양성 추가
        return results
            .shuffled(kotlin.random.Random(searchSeed))
            .take(limit)
    }

    private fun buildSearchQueries(plan: OutfitPlan): List<String> {
        val queries = mutableListOf<String>()
        
        // 메인 아이템들로부터 다양한 검색어 생성
        plan.mainPieces.firstOrNull()?.let { item ->
            queries += buildVariedQueriesForItem(item)
        }
        
        // 아우터가 있으면 추가
        plan.outerLayer?.let { item ->
            queries += buildVariedQueriesForItem(item)
        }
        
        // 검색어가 부족하면 두 번째 메인 아이템 사용
        if (queries.size < 3) {
            plan.mainPieces.drop(1).firstOrNull()?.let { item ->
                queries += buildVariedQueriesForItem(item)
            }
        }
        
        return queries.distinct().take(5) // 최대 5개의 다양한 검색어
    }

    /**
     * 하나의 아이템으로부터 여러 검색어 조합 생성
     */
    private fun buildVariedQueriesForItem(item: WardrobeItemEntity): List<String> {
        val queries = mutableListOf<String>()
        val name = item.name
        val color = item.color
        val tags = item.tags
        
        // 1. 기본: 이름만
        queries += name
        
        // 2. 색상 + 이름
        if (!color.isNullOrBlank()) {
            queries += "$color $name"
        }
        
        // 3. 이름 + 첫 번째 태그
        if (tags.isNotEmpty()) {
            queries += "$name ${tags.first()}"
        }
        
        // 4. 색상 + 이름 + 태그 (전체 조합)
        if (!color.isNullOrBlank() && tags.isNotEmpty()) {
            queries += "$color $name ${tags.first()}"
        }
        
        // 5. 카테고리 기반 일반 검색어
        queries += getCategoryBasedQuery(item.category)
        
        return queries.distinct()
    }
    
    /**
     * 카테고리별 일반적인 검색어 생성
     */
    private fun getCategoryBasedQuery(category: com.fitghost.app.data.db.WardrobeCategory): String {
        return when (category) {
            com.fitghost.app.data.db.WardrobeCategory.TOP -> "티셔츠"
            com.fitghost.app.data.db.WardrobeCategory.BOTTOM -> "팬츠"
            com.fitghost.app.data.db.WardrobeCategory.OUTER -> "재킷"
            com.fitghost.app.data.db.WardrobeCategory.SHOES -> "스니커즈"
            com.fitghost.app.data.db.WardrobeCategory.ACCESSORY -> "악세서리"
            else -> "패션"
        }
    }

    private fun buildPrimaryQuery(plan: OutfitPlan, products: List<Product>): String {
        return plan.mainPieces.firstOrNull()?.let { item ->
            // 간단한 검색어 생성
            val color = item.color
            val name = item.name
            if (!color.isNullOrBlank()) "$color $name" else name
        } ?: products.firstOrNull()?.name ?: "패션 추천"
    }

    private suspend fun fallbackHomeRecommendations(
        weather: WeatherSnapshot,
        dailySeed: Long = System.currentTimeMillis() / (24 * 60 * 60 * 1000)
    ): List<HomeOutfitRecommendation> {
        val queries = defaultQueriesFor(weather)
        val outfits = mutableListOf<HomeOutfitRecommendation>()
        queries.forEachIndexed { index, query ->
            val searchSeed = dailySeed * 1000 + index + 500
            val products = productSearchDataSource.searchProducts(query, limit = 4)
                .shuffled(kotlin.random.Random(searchSeed))
                .take(4)
            
            if (products.isNotEmpty()) {
                outfits += HomeOutfitRecommendation(
                    id = "fallback_$index",
                    title = "${weatherSummary(weather)}에 어울리는 추천",
                    subtitle = "옷장 아이템이 부족해 외부 추천을 제안합니다.",
                    items = emptyList(),
                    styleTips = listOf("추천 상품을 참고하여 새로운 아이템을 추가해 보세요."),
                    complementaryProducts = products,
                    score = 0.0,
                    shopQuery = query
                )
            }
        }
        return outfits.take(3)
    }

    private suspend fun fallbackShopRecommendations(
        weather: WeatherSnapshot,
        dailySeed: Long = System.currentTimeMillis() / (24 * 60 * 60 * 1000)
    ): List<OutfitRecommendation> {
        val queries = defaultQueriesFor(weather)
        val recommendations = mutableListOf<OutfitRecommendation>()
        queries.forEachIndexed { index, query ->
            val searchSeed = dailySeed * 1000 + index + 200
            val products = productSearchDataSource.searchProducts(query, limit = 6)
                .shuffled(kotlin.random.Random(searchSeed))
                .take(6)
            
            if (products.isNotEmpty()) {
                recommendations += OutfitRecommendation(
                    id = "fallback_$index",
                    title = "${weatherSummary(weather)} 추천 아이템",
                    description = "옷장 데이터를 찾을 수 없어, 오늘 날씨에 어울리는 온라인 상품을 추천합니다.",
                    recommendedProducts = products,
                    baseGarmentId = null,
                    matchingReason = "날씨 조건을 고려한 기본 추천"
                )
            }
        }
        return recommendations
    }

    private fun defaultQueriesFor(weather: WeatherSnapshot): List<String> {
        return when {
            weather.tempC <= 0 -> listOf("패딩 코트", "기모 슬랙스", "방한 부츠")
            weather.tempC <= 10 -> listOf("울 코트", "니트 가디건", "방풍 재킷")
            weather.tempC <= 18 -> listOf("셔츠 자켓", "데님 팬츠", "로퍼")
            weather.tempC <= 24 -> listOf("린넨 셔츠", "치노 팬츠", "스니커즈")
            else -> listOf("반팔 티셔츠", "와이드 팬츠", "샌들")
        }
    }

    private fun weatherSummary(weather: WeatherSnapshot): String {
        return when {
            weather.tempC <= 0 -> "매우 추운 날씨"
            weather.tempC <= 10 -> "쌀쌀한 날씨"
            weather.tempC <= 18 -> "선선한 날씨"
            weather.tempC <= 24 -> "온화한 날씨"
            else -> "무더운 날씨"
        }
    }

    companion object {
        private const val DEFAULT_LATITUDE = 37.5665
        private const val DEFAULT_LONGITUDE = 126.9780
    }
}

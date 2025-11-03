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

        val plans = outfitRecommender.recommend(
            weather = weather,
            wardrobe = wardrobe,
            maxOutfits = params.outfitLimit
        )

        val outfits = if (plans.isNotEmpty()) {
            plans.take(3).map { plan ->
                val complementary = fetchComplementaryProducts(plan, limit = 4)
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
            fallbackHomeRecommendations(weather)
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

        val plans = outfitRecommender.recommend(
            weather = weather,
            wardrobe = wardrobe,
            maxOutfits = params.outfitLimit
        )

        if (plans.isNotEmpty()) {
            plans.take(params.outfitLimit).map { plan ->
                val complementary = fetchComplementaryProducts(plan, limit = 6)
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
            fallbackShopRecommendations(weather)
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
        limit: Int
    ): List<Product> {
        val queries = buildSearchQueries(plan)
        if (queries.isEmpty()) return emptyList()

        val results = mutableListOf<Product>()
        for (query in queries) {
            if (results.size >= limit) break
            val products = productSearchDataSource.searchProducts(query, limit = limit)
            for (product in products) {
                if (results.none { it.id == product.id }) {
                    results += product
                }
                if (results.size >= limit) break
            }
        }
        return results.take(limit)
    }

    private fun buildSearchQueries(plan: OutfitPlan): List<String> {
        val queries = mutableListOf<String>()
        plan.mainPieces.firstOrNull()?.let {
            queries += buildQueryForItem(it)
        }
        plan.outerLayer?.let { queries += buildQueryForItem(it) }
        if (queries.isEmpty()) {
            plan.mainPieces.drop(1).firstOrNull()?.let { queries += buildQueryForItem(it) }
        }
        return queries.distinct()
    }

    private fun buildQueryForItem(item: WardrobeItemEntity): String {
        val builder = StringBuilder()
        item.color?.let { builder.append(it).append(" ") }
        builder.append(item.name)
        if (item.tags.isNotEmpty()) {
            builder.append(" ")
            builder.append(item.tags.first())
        }
        return builder.toString().trim()
    }

    private fun buildPrimaryQuery(plan: OutfitPlan, products: List<Product>): String {
        return plan.mainPieces.firstOrNull()?.let { buildQueryForItem(it) }
            ?: products.firstOrNull()?.name
            ?: "패션 추천"
    }

    private suspend fun fallbackHomeRecommendations(weather: WeatherSnapshot): List<HomeOutfitRecommendation> {
        val queries = defaultQueriesFor(weather)
        val outfits = mutableListOf<HomeOutfitRecommendation>()
        queries.forEachIndexed { index, query ->
            val products = productSearchDataSource.searchProducts(query, limit = 4)
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

    private suspend fun fallbackShopRecommendations(weather: WeatherSnapshot): List<OutfitRecommendation> {
        val queries = defaultQueriesFor(weather)
        val recommendations = mutableListOf<OutfitRecommendation>()
        queries.forEachIndexed { index, query ->
            val products = productSearchDataSource.searchProducts(query, limit = 6)
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

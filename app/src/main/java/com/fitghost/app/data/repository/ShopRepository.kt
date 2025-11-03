package com.fitghost.app.data.repository

import com.fitghost.app.data.model.*
import com.fitghost.app.data.network.GeminiFashionService
import com.fitghost.app.ai.MatchingItemsGenerator
import com.fitghost.app.data.repository.ProductSearchEngine
import android.graphics.Bitmap
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
// Added for wishlist DataStore
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import android.util.Log
import com.fitghost.app.domain.RecommendationParams
import com.fitghost.app.domain.RecommendationService
import com.fitghost.app.data.weather.WeatherRepo
import org.json.JSONArray
import org.json.JSONObject

// DataStore for wishlist
private val Context.wishlistDataStore by preferencesDataStore(name = "wishlist_store")
private val KEY_WISHLIST = stringSetPreferencesKey("wishlist_products")

/** 상품 검색 및 추천 Repository */
interface ShopRepository {
    // 텍스트 검색
    suspend fun searchProducts(query: String): List<Product>
    
    // 이미지 기반 검색 (새 기능)
    suspend fun searchByImage(bitmap: Bitmap): Result<ImageSearchResult>
    
    // 옷장 아이템 기반 검색 (새 기능)
    suspend fun searchMatchingItems(itemDescription: String, itemCategory: String): Result<ImageSearchResult>
    
    // 추천
    suspend fun getRecommendations(): List<OutfitRecommendation>
    
    // 위시리스트
    suspend fun addToWishlist(product: Product)
    suspend fun removeFromWishlist(productId: String)
    fun wishlistProductsFlow(): Flow<List<Product>>
    
    // NanoBanana AI 기반 추천 기능
    suspend fun getAIRecommendations(prompt: String, context: NanoBananaContext? = null): Result<FashionRecommendation>
    suspend fun analyzeStyle(prompt: String, context: NanoBananaContext? = null): Result<FashionRecommendation>
    suspend fun matchOutfit(prompt: String, context: NanoBananaContext? = null): Result<FashionRecommendation>
}

/**
 * 이미지 검색 결과
 */
data class ImageSearchResult(
    val sourceImage: String? = null,  // 검색에 사용된 이미지 설명
    val matchingCategories: List<String>, // AI가 생성한 어울리는 카테고리
    val products: List<Product> // 실제 검색 결과
)

/** Shop Repository 구현체 - 실제 API 연동 */
class ShopRepositoryImpl(
    private val context: Context,
    private val nanoBananaService: GeminiFashionService = GeminiFashionService(),
    private val matchingItemsGenerator: MatchingItemsGenerator = MatchingItemsGenerator(context)
) : ShopRepository {

    companion object {
        private const val TAG = "ShopRepository"
    }

    private data class WishlistEntry(val product: Product, val savedAt: Long)

    private val wishlistEntriesFlow: Flow<List<WishlistEntry>> =
        context.wishlistDataStore.data.map { prefs ->
            prefs[KEY_WISHLIST]?.mapNotNull { deserializeProduct(it) } ?: emptyList()
        }

    private val wardrobeRepository: WardrobeRepository = WardrobeRepository.create(context)
    private val weatherRepo: WeatherRepo = WeatherRepo.create()
    private val recommendationService: RecommendationService by lazy {
        RecommendationService(
            wardrobeRepository = wardrobeRepository,
            weatherRepo = weatherRepo,
            productSearchDataSource = object : RecommendationService.ProductSearchDataSource {
                override suspend fun searchProducts(query: String, limit: Int): List<Product> {
                    return ProductSearchEngine.search(query, maxResults = limit)
                }
            }
        )
    }

    override fun wishlistProductsFlow(): Flow<List<Product>> =
        wishlistEntriesFlow.map { entries ->
            entries
                .sortedByDescending { it.savedAt }
                .map { it.product.copy(isWishlisted = true) }
        }

    override suspend fun searchProducts(query: String): List<Product> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val wishlistIds = getWishlistIds()
        ProductSearchEngine.search(query)
            .map { product -> product.copy(isWishlisted = wishlistIds.contains(product.id)) }
    }

    override suspend fun searchByImage(bitmap: Bitmap): Result<ImageSearchResult> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Searching by image")

            val taggerResult = com.fitghost.app.ai.cloud.GeminiTagger.tagImage(bitmap)
            if (taggerResult.isFailure) {
                return@withContext Result.failure(taggerResult.exceptionOrNull() ?: Exception("Tagging failed"))
            }

            val metadata = com.fitghost.app.ai.cloud.GeminiTagger.toClothingMetadata(taggerResult.getOrThrow())
            val itemDescription = "${metadata.color} ${metadata.detailType}"
            val itemCategory = metadata.category

            val categoriesResult = matchingItemsGenerator.generateMatchingCategories(
                itemDescription,
                itemCategory
            )

            val matchingCategories = categoriesResult.getOrElse { emptyList() }
            val searchResults = coroutineScope {
                matchingCategories.map { category ->
                    async { searchProducts(category) }
                }.map { it.await() }.flatten()
            }

            Result.success(
                ImageSearchResult(
                    sourceImage = itemDescription,
                    matchingCategories = matchingCategories,
                    products = searchResults.distinctBy { it.shopUrl }.take(20)
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in image search", e)
            Result.failure(e)
        }
    }

    override suspend fun searchMatchingItems(
        itemDescription: String,
        itemCategory: String
    ): Result<ImageSearchResult> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Searching matching items for: $itemDescription ($itemCategory)")
            val categoriesResult = matchingItemsGenerator.generateMatchingCategories(
                itemDescription,
                itemCategory
            )

            val matchingCategories = categoriesResult.getOrElse { emptyList() }
            val searchResults = coroutineScope {
                matchingCategories.map { category ->
                    async { searchProducts(category) }
                }.map { it.await() }.flatten()
            }

            Result.success(
                ImageSearchResult(
                    sourceImage = itemDescription,
                    matchingCategories = matchingCategories,
                    products = searchResults.distinctBy { it.shopUrl }.take(20)
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in matching items search", e)
            Result.failure(e)
        }
    }

    override suspend fun getRecommendations(): List<OutfitRecommendation> = withContext(Dispatchers.IO) {
        val wishlistIds = getWishlistIds()
        recommendationService.getShopRecommendations().map { recommendation ->
            recommendation.copy(
                recommendedProducts = recommendation.recommendedProducts.map { product ->
                    product.copy(isWishlisted = wishlistIds.contains(product.id))
                }
            )
        }
    }

    override suspend fun addToWishlist(product: Product) {
        withContext(Dispatchers.IO) {
            context.wishlistDataStore.edit { prefs ->
                val current = prefs[KEY_WISHLIST]?.mapNotNull { deserializeProduct(it) }?.toMutableList()
                    ?: mutableListOf()
                current.removeAll { it.product.id == product.id }
                current += WishlistEntry(product.copy(isWishlisted = true), System.currentTimeMillis())
                prefs[KEY_WISHLIST] = current.map { serializeProduct(it.product, it.savedAt) }.toSet()
            }
        }
    }

    override suspend fun removeFromWishlist(productId: String) {
        withContext(Dispatchers.IO) {
            context.wishlistDataStore.edit { prefs ->
                val remaining = prefs[KEY_WISHLIST]?.mapNotNull { deserializeProduct(it) }
                    ?.filterNot { it.product.id == productId }
                    ?: emptyList()
                prefs[KEY_WISHLIST] = remaining.map { serializeProduct(it.product, it.savedAt) }.toSet()
            }
        }
    }

    private suspend fun getWishlistIds(): Set<String> =
        wishlistEntriesFlow.first().map { it.product.id }.toSet()

    private fun serializeProduct(product: Product, savedAt: Long): String {
        val json = JSONObject()
            .put("id", product.id)
            .put("name", product.name)
            .put("price", product.price)
            .put("imageUrl", product.imageUrl)
            .put("category", product.category.name)
            .put("shopName", product.shopName)
            .put("shopUrl", product.shopUrl)
            .put("description", product.description)
            .put("source", product.source)
            .put("savedAt", savedAt)
        val tagsArray = JSONArray()
        product.tags.forEach { tagsArray.put(it) }
        json.put("tags", tagsArray)
        return json.toString()
    }

    private fun deserializeProduct(raw: String): WishlistEntry? {
        return try {
            val json = JSONObject(raw)
            val tagsJson = json.optJSONArray("tags")
            val tags = buildList {
                if (tagsJson != null) {
                    for (i in 0 until tagsJson.length()) {
                        add(tagsJson.optString(i))
                    }
                }
            }
            val product = Product(
                id = json.optString("id"),
                name = json.optString("name"),
                price = json.optInt("price"),
                imageUrl = json.optString("imageUrl"),
                category = runCatching { ProductCategory.valueOf(json.optString("category")) }
                    .getOrDefault(ProductCategory.OTHER),
                shopName = json.optString("shopName"),
                shopUrl = json.optString("shopUrl"),
                description = json.optString("description"),
                tags = tags,
                isWishlisted = true,
                source = json.optString("source")
            )
            val savedAt = json.optLong("savedAt", System.currentTimeMillis())
            WishlistEntry(product, savedAt)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deserialize wishlist entry", e)
            null
        }
    }
    
    // NanoBanana AI 기반 추천 기능 구현
    override suspend fun getAIRecommendations(prompt: String, context: NanoBananaContext?): Result<FashionRecommendation> {
        return try {
            Log.d("ShopRepository", "Requesting AI recommendations with prompt: $prompt")
            nanoBananaService.getFashionRecommendation(prompt, context)
        } catch (e: Exception) {
            Log.e("ShopRepository", "Error getting AI recommendations", e)
            Result.failure(e)
        }
    }
    
    override suspend fun analyzeStyle(prompt: String, context: NanoBananaContext?): Result<FashionRecommendation> {
        return try {
            Log.d("ShopRepository", "Requesting style analysis with prompt: $prompt")
            nanoBananaService.analyzeStyle(prompt, context)
        } catch (e: Exception) {
            Log.e("ShopRepository", "Error analyzing style", e)
            Result.failure(e)
        }
    }
    
    override suspend fun matchOutfit(prompt: String, context: NanoBananaContext?): Result<FashionRecommendation> {
        return try {
            Log.d("ShopRepository", "Requesting outfit matching with prompt: $prompt")
            nanoBananaService.matchOutfit(prompt, context)
        } catch (e: Exception) {
            Log.e("ShopRepository", "Error matching outfit", e)
            Result.failure(e)
        }
    }
}

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
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.awaitAll
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
    
    // 캐시 매니저
    private val cacheManager by lazy { com.fitghost.app.data.cache.CacheManager.getInstance(context) }

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
            context = context,
            productSearchDataSource = object : RecommendationService.ProductSearchDataSource {
                override suspend fun searchProducts(query: String, limit: Int): List<Product> {
                    // 여기서는 이미 성별 태그가 추가된 쿼리를 받으므로 그대로 전달
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

        // 캐시 키 생성
        val genderTag = com.fitghost.app.data.settings.UserSettings.getGenderKoTag(context)
        val enrichedQuery = if (!genderTag.isNullOrBlank()) "$genderTag $query" else query
        val cacheKey = cacheManager.generateKey("searchProducts", enrichedQuery)
        
        // 캐시 확인
        val cached = cacheManager.get(cacheKey)
        if (cached != null) {
            return@withContext deserializeProductList(cached)
        }

        // API 호출
        val wishlistIds = getWishlistIds()
        val products = ProductSearchEngine.search(enrichedQuery)
            .map { product -> product.copy(isWishlisted = wishlistIds.contains(product.id)) }
        
        // 캐시 저장
        cacheManager.put(cacheKey, serializeProductList(products))
        
        products
    }
    
    /**
     * Product 리스트 직렬화
     */
    private fun serializeProductList(products: List<Product>): String {
        val jsonArray = JSONArray()
        products.forEach { product ->
            jsonArray.put(serializeProduct(product))
        }
        return jsonArray.toString()
    }
    
    /**
     * Product 리스트 역직렬화
     */
    private fun deserializeProductList(json: String): List<Product> {
        val products = mutableListOf<Product>()
        try {
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                deserializeProduct(jsonArray.getJSONObject(i))?.let { products.add(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deserialize product list", e)
        }
        return products
    }
    
    /**
     * Product 직렬화 (JSON)
     */
    private fun serializeProduct(product: Product): JSONObject {
        return JSONObject()
            .put("id", product.id)
            .put("name", product.name)
            .put("price", product.price)
            .put("imageUrl", product.imageUrl)
            .put("category", product.category.name)
            .put("shopName", product.shopName)
            .put("shopUrl", product.shopUrl)
            .put("description", product.description)
            .put("source", product.source)
            .put("tags", JSONArray(product.tags))
            .put("isWishlisted", product.isWishlisted)
    }
    
    /**
     * Product 역직렬화 (JSON)
     */
    private fun deserializeProduct(json: JSONObject): Product? {
        return try {
            val tagsJson = json.optJSONArray("tags")
            val tags = mutableListOf<String>()
            if (tagsJson != null) {
                for (i in 0 until tagsJson.length()) {
                    tags.add(tagsJson.getString(i))
                }
            }
            
            Product(
                id = json.getString("id"),
                name = json.getString("name"),
                price = json.getInt("price"),
                imageUrl = json.getString("imageUrl"),
                category = runCatching { ProductCategory.valueOf(json.getString("category")) }
                    .getOrDefault(ProductCategory.OTHER),
                shopName = json.getString("shopName"),
                shopUrl = json.getString("shopUrl"),
                description = json.getString("description"),
                tags = tags,
                isWishlisted = json.getBoolean("isWishlisted"),
                source = json.getString("source")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deserialize product", e)
            null
        }
    }

    override suspend fun searchByImage(bitmap: Bitmap): Result<ImageSearchResult> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Searching by image (${bitmap.width}x${bitmap.height})")

            // 전체 프로세스에 대한 타임아웃 설정 (45초)
            val result = withTimeout(45_000L) {
                // 1. 이미지 태깅 (Gemini API 호출)
                val taggerResult = com.fitghost.app.ai.cloud.GeminiTagger.tagImage(bitmap)
                if (taggerResult.isFailure) {
                    Log.e(TAG, "Image tagging failed", taggerResult.exceptionOrNull())
                    return@withTimeout Result.failure(
                        taggerResult.exceptionOrNull() ?: Exception("Tagging failed")
                    )
                }

                // 2. 태깅 결과를 ClothingMetadata로 변환
                val metadata = com.fitghost.app.ai.cloud.GeminiTagger.toClothingMetadata(
                    taggerResult.getOrThrow()
                )
                val itemDescription = "${metadata.color} ${metadata.detailType}".trim()
                val itemCategory = metadata.category
                
                Log.d(TAG, "Tagged: $itemDescription ($itemCategory)")

                // 3. 어울리는 카테고리 생성 (온디바이스 AI 또는 클라우드 폴백)
                val categoriesResult = matchingItemsGenerator.generateMatchingCategories(
                    itemDescription.ifBlank { metadata.detailType },
                    itemCategory
                )

                val matchingCategories = categoriesResult.getOrElse { 
                    Log.w(TAG, "Failed to generate categories, using empty list")
                    emptyList() 
                }
                
                Log.d(TAG, "Matching categories: $matchingCategories")

                // 4. 각 카테고리로 상품 검색 (병렬 처리)
                val searchResults = coroutineScope {
                    matchingCategories.map { category ->
                        async { 
                            runCatching { searchProducts(category) }
                                .getOrElse { error ->
                                    Log.e(TAG, "Search failed for category: $category", error)
                                    emptyList()
                                }
                        }
                    }.awaitAll().flatten()
                }
                
                Log.d(TAG, "Found ${searchResults.size} total products")

                Result.success(
                    ImageSearchResult(
                        sourceImage = itemDescription.ifBlank { "이미지 검색" },
                        matchingCategories = matchingCategories,
                        products = searchResults.distinctBy { it.shopUrl }.take(20)
                    )
                )
            }
            
            result
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e(TAG, "Image search timeout after 45 seconds")
            Result.failure(Exception("검색 시간이 초과했습니다. 다시 시도해주세요."))
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
        // 캐시 키 생성
        val cacheKey = cacheManager.generateKey("getRecommendations")
        
        // 캐시 확인
        val cached = cacheManager.get(cacheKey)
        if (cached != null) {
            return@withContext deserializeRecommendationList(cached)
        }
        
        // API 호출
        val wishlistIds = getWishlistIds()
        val recommendations = recommendationService.getShopRecommendations().map { recommendation ->
            recommendation.copy(
                recommendedProducts = recommendation.recommendedProducts.map { product ->
                    product.copy(isWishlisted = wishlistIds.contains(product.id))
                }
            )
        }
        
        // 캐시 저장
        cacheManager.put(cacheKey, serializeRecommendationList(recommendations))
        
        recommendations
    }
    
    /**
     * OutfitRecommendation 리스트 직렬화
     */
    private fun serializeRecommendationList(recommendations: List<OutfitRecommendation>): String {
        val jsonArray = JSONArray()
        recommendations.forEach { rec ->
            val productsArray = JSONArray()
            rec.recommendedProducts.forEach { product ->
                productsArray.put(serializeProduct(product))
            }
            
            jsonArray.put(
                JSONObject()
                    .put("id", rec.id)
                    .put("title", rec.title)
                    .put("description", rec.description)
                    .put("matchingReason", rec.matchingReason)
                    .put("baseGarmentId", rec.baseGarmentId)
                    .put("score", rec.score)
                    .put("recommendedProducts", productsArray)
            )
        }
        return jsonArray.toString()
    }
    
    /**
     * OutfitRecommendation 리스트 역직렬화
     */
    private fun deserializeRecommendationList(json: String): List<OutfitRecommendation> {
        val recommendations = mutableListOf<OutfitRecommendation>()
        try {
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val productsArray = obj.getJSONArray("recommendedProducts")
                val products = mutableListOf<Product>()
                
                for (j in 0 until productsArray.length()) {
                    deserializeProduct(productsArray.getJSONObject(j))?.let { products.add(it) }
                }
                
                recommendations.add(
                    OutfitRecommendation(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        title = obj.getString("title"),
                        description = obj.optString("description", ""),
                        recommendedProducts = products,
                        baseGarmentId = obj.optString("baseGarmentId").takeIf { it.isNotEmpty() },
                        matchingReason = obj.getString("matchingReason"),
                        score = obj.optDouble("score", 0.0).toFloat()
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deserialize recommendation list", e)
        }
        return recommendations
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
            // 캐시 키 생성 (컨텍스트 포함)
            val contextStr = context?.let { 
                "${it.userPreferences?.style}_${it.userPreferences?.colors?.joinToString()}_${it.wardrobeItems?.size}"
            } ?: "no_context"
            val genderTag = com.fitghost.app.data.settings.UserSettings.getGenderKoTag(this@ShopRepositoryImpl.context)
            val promptWithGender = if (!genderTag.isNullOrBlank()) "[사용자 성별: $genderTag] $prompt" else prompt
            val cacheKey = cacheManager.generateKey("getAIRecommendations", promptWithGender, contextStr)
            
            // 캐시 확인
            val cached = cacheManager.get(cacheKey)
            if (cached != null) {
                val recommendation = deserializeFashionRecommendation(cached)
                if (recommendation != null) {
                    Log.d(TAG, "AI recommendation cache HIT")
                    return Result.success(recommendation)
                }
            }
            
            // API 호출
            Log.d(TAG, "Requesting AI recommendations with prompt: $promptWithGender")
            val result = nanoBananaService.getFashionRecommendation(promptWithGender, context)
            
            // 성공 시 캐시 저장
            result.onSuccess { recommendation ->
                cacheManager.put(cacheKey, serializeFashionRecommendation(recommendation))
            }
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error getting AI recommendations", e)
            Result.failure(e)
        }
    }
    
    override suspend fun analyzeStyle(prompt: String, context: NanoBananaContext?): Result<FashionRecommendation> {
        return try {
            // 캐시 키 생성
            val contextStr = context?.wardrobeItems?.size?.toString() ?: "no_context"
            val genderTag = com.fitghost.app.data.settings.UserSettings.getGenderKoTag(this@ShopRepositoryImpl.context)
            val promptWithGender = if (!genderTag.isNullOrBlank()) "[사용자 성별: $genderTag] $prompt" else prompt
            val cacheKey = cacheManager.generateKey("analyzeStyle", promptWithGender, contextStr)
            
            // 캐시 확인
            val cached = cacheManager.get(cacheKey)
            if (cached != null) {
                val analysis = deserializeFashionRecommendation(cached)
                if (analysis != null) {
                    Log.d(TAG, "Style analysis cache HIT")
                    return Result.success(analysis)
                }
            }
            
            // API 호출
            Log.d(TAG, "Requesting style analysis with prompt: $promptWithGender")
            val result = nanoBananaService.analyzeStyle(promptWithGender, context)
            
            // 성공 시 캐시 저장
            result.onSuccess { analysis ->
                cacheManager.put(cacheKey, serializeFashionRecommendation(analysis))
            }
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing style", e)
            Result.failure(e)
        }
    }
    
    override suspend fun matchOutfit(prompt: String, context: NanoBananaContext?): Result<FashionRecommendation> {
        return try {
            // 캐시 키 생성
            val contextStr = context?.wardrobeItems?.size?.toString() ?: "no_context"
            val genderTag = com.fitghost.app.data.settings.UserSettings.getGenderKoTag(this@ShopRepositoryImpl.context)
            val promptWithGender = if (!genderTag.isNullOrBlank()) "[사용자 성별: $genderTag] $prompt" else prompt
            val cacheKey = cacheManager.generateKey("matchOutfit", promptWithGender, contextStr)
            
            // 캐시 확인
            val cached = cacheManager.get(cacheKey)
            if (cached != null) {
                val matching = deserializeFashionRecommendation(cached)
                if (matching != null) {
                    Log.d(TAG, "Outfit matching cache HIT")
                    return Result.success(matching)
                }
            }
            
            // API 호출
            Log.d(TAG, "Requesting outfit matching with prompt: $promptWithGender")
            val result = nanoBananaService.matchOutfit(promptWithGender, context)
            
            // 성공 시 캐시 저장
            result.onSuccess { matching ->
                cacheManager.put(cacheKey, serializeFashionRecommendation(matching))
            }
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error matching outfit", e)
            Result.failure(e)
        }
    }
    
    /**
     * FashionRecommendation 직렬화
     */
    private fun serializeFashionRecommendation(recommendation: FashionRecommendation): String {
        val itemsArray = JSONArray()
        recommendation.recommendedItems.forEach { item ->
            itemsArray.put(
                JSONObject()
                    .put("category", item.category)
                    .put("description", item.description)
                    .put("color", item.color)
                    .put("style", item.style)
                    .put("priceRange", item.priceRange)
                    .put("searchKeywords", JSONArray(item.searchKeywords))
            )
        }
        
        val productsArray = JSONArray()
        recommendation.products.forEach { product ->
            productsArray.put(serializeProduct(product))
        }
        
        return JSONObject()
            .put("id", recommendation.id)
            .put("title", recommendation.title)
            .put("description", recommendation.description)
            .put("recommendedItems", itemsArray)
            .put("reasoning", recommendation.reasoning)
            .put("confidence", recommendation.confidence)
            .put("occasion", recommendation.occasion)
            .put("products", productsArray)
            .toString()
    }
    
    /**
     * FashionRecommendation 역직렬화
     */
    private fun deserializeFashionRecommendation(json: String): FashionRecommendation? {
        return try {
            val obj = JSONObject(json)
            
            // RecommendedItems 역직렬화
            val itemsArray = obj.getJSONArray("recommendedItems")
            val items = mutableListOf<RecommendedItem>()
            for (i in 0 until itemsArray.length()) {
                val itemObj = itemsArray.getJSONObject(i)
                val keywordsArray = itemObj.getJSONArray("searchKeywords")
                val keywords = mutableListOf<String>()
                for (j in 0 until keywordsArray.length()) {
                    keywords.add(keywordsArray.getString(j))
                }
                
                items.add(
                    RecommendedItem(
                        category = itemObj.getString("category"),
                        description = itemObj.getString("description"),
                        color = itemObj.optString("color").takeIf { it.isNotEmpty() },
                        style = itemObj.optString("style").takeIf { it.isNotEmpty() },
                        priceRange = itemObj.optString("priceRange").takeIf { it.isNotEmpty() },
                        searchKeywords = keywords
                    )
                )
            }
            
            // Products 역직렬화
            val productsArray = obj.getJSONArray("products")
            val products = mutableListOf<Product>()
            for (i in 0 until productsArray.length()) {
                deserializeProduct(productsArray.getJSONObject(i))?.let { products.add(it) }
            }
            
            FashionRecommendation(
                id = obj.getString("id"),
                title = obj.getString("title"),
                description = obj.getString("description"),
                recommendedItems = items,
                reasoning = obj.getString("reasoning"),
                confidence = obj.getDouble("confidence").toFloat(),
                occasion = obj.optString("occasion").takeIf { it.isNotEmpty() },
                products = products
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deserialize FashionRecommendation", e)
            null
        }
    }
}

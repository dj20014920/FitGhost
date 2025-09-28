package com.fitghost.app.data.repository

import com.fitghost.app.data.model.*
import com.fitghost.app.data.network.GeminiFashionService
import kotlinx.coroutines.delay
// Added for wishlist DataStore
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import android.util.Log

// DataStore for wishlist
private val Context.wishlistDataStore by preferencesDataStore(name = "wishlist_store")
private val KEY_WISHLIST = stringSetPreferencesKey("wishlist_ids")

/** 상품 검색 및 추천 Repository PRD: 네이버/구글 검색 API 연동 대비, 키 없을 때 우아한 폴백 */
interface ShopRepository {
    suspend fun searchProducts(query: String): List<Product>
    suspend fun getRecommendations(): List<OutfitRecommendation>
    suspend fun addToWishlist(productId: String)
    suspend fun removeFromWishlist(productId: String)
    // 위시리스트 탭을 위한 실시간 상품 스트림
    fun wishlistProductsFlow(): Flow<List<Product>>
    
    // NanoBanana AI 기반 추천 기능
    suspend fun getAIRecommendations(prompt: String, context: NanoBananaContext? = null): Result<FashionRecommendation>
    suspend fun analyzeStyle(prompt: String, context: NanoBananaContext? = null): Result<FashionRecommendation>
    suspend fun matchOutfit(prompt: String, context: NanoBananaContext? = null): Result<FashionRecommendation>
}

/** Shop Repository 구현체 현재: Mock 데이터, 추후: 실제 API 연동 */
class ShopRepositoryImpl(
    private val context: Context,
    private val nanoBananaService: GeminiFashionService = GeminiFashionService()
) : ShopRepository {

    private val mockProducts =
            listOf(
                    Product(
                            id = "1",
                            name = "클래식 화이트 셔츠",
                            price = 45000,
                            imageUrl = "",
                            category = ProductCategory.TOP,
                            shopName = "Fashion Store",
                            shopUrl = "https://example.com/1",
                            description = "깔끔한 화이트 셔츠",
                            tags = listOf("클래식", "화이트", "정장")
                    ),
                    Product(
                            id = "2",
                            name = "데님 청바지",
                            price = 89000,
                            imageUrl = "",
                            category = ProductCategory.BOTTOM,
                            shopName = "Jeans World",
                            shopUrl = "https://example.com/2",
                            description = "편안한 슬림핏 청바지",
                            tags = listOf("데님", "캐주얼", "슬림")
                    ),
                    Product(
                            id = "3",
                            name = "니트 가디건",
                            price = 65000,
                            imageUrl = "",
                            category = ProductCategory.OUTERWEAR,
                            shopName = "Knit House",
                            shopUrl = "https://example.com/3",
                            description = "부드러운 니트 가디건",
                            tags = listOf("니트", "가을", "따뜻")
                    )
            )

    private val wishlistIdsFlow: Flow<Set<String>> =
        context.wishlistDataStore.data.map { prefs -> prefs[KEY_WISHLIST] ?: emptySet() }

    override fun wishlistProductsFlow(): Flow<List<Product>> =
        wishlistIdsFlow.map { ids ->
            // 현재 데이터 소스는 mockProducts 이므로, 해당 ID 에 매칭되는 항목만 노출
            mockProducts
                .filter { ids.contains(it.id) }
                .map { it.copy(isWishlisted = true) }
        }

    override suspend fun searchProducts(query: String): List<Product> {
        // 실제 구현 시: Retrofit + 네이버/구글 API
        delay(500) // 네트워크 지연 시뮬레이션

        if (query.isBlank()) return emptyList()

        val filtered = mockProducts.filter {
            it.name.contains(query, ignoreCase = true) ||
                    it.tags.any { tag -> tag.contains(query, ignoreCase = true) }
        }
        // Apply wishlist flag from DataStore
        val wishlist = context.wishlistDataStore.data.map { it[KEY_WISHLIST] ?: emptySet() }.first()
        return filtered.map { it.copy(isWishlisted = wishlist.contains(it.id)) }
    }

    override suspend fun getRecommendations(): List<OutfitRecommendation> {
        // PRD: 옷장 데이터 기반 추천 로직
        delay(300)

        val base = listOf(
                OutfitRecommendation(
                        id = "rec1",
                        title = "스마트 캐주얼 코디",
                        description = "깔끔한 셔츠와 청바지의 완벽한 조합",
                        recommendedProducts = mockProducts.take(2),
                        baseGarmentId = "wardrobe_001", // 옷장의 바지 기준
                        matchingReason = "이 청바지에는 화이트 셔츠가 완벽하게 어울려요"
                ),
                OutfitRecommendation(
                        id = "rec2",
                        title = "가을 레이어드 스타일",
                        description = "니트 가디건으로 완성하는 따뜻한 코디",
                        recommendedProducts = listOf(mockProducts[0], mockProducts[2]),
                        baseGarmentId = "wardrobe_002",
                        matchingReason = "이 셔츠 위에 니트 가디건을 입으면 세련된 룩이 완성돼요"
                ),
                OutfitRecommendation(
                        id = "rec3",
                        title = "데일리 캐주얼 룩",
                        description = "편안하면서도 스타일리시한 일상 코디",
                        recommendedProducts = listOf(mockProducts[1]),
                        baseGarmentId = "wardrobe_003",
                        matchingReason = "이 상의와 청바지는 언제나 실패 없는 조합이에요"
                )
        )
        // Apply wishlist flag from DataStore
        val wishlist = context.wishlistDataStore.data.map { it[KEY_WISHLIST] ?: emptySet() }.first()
        return base.map { rec ->
            rec.copy(
                recommendedProducts = rec.recommendedProducts.map { p ->
                    p.copy(isWishlisted = wishlist.contains(p.id))
                }
            )
        }
    }

    override suspend fun addToWishlist(productId: String) {
        // 찜하기 로컬 저장 로직 - DataStore에 ID 저장
        context.wishlistDataStore.edit { prefs ->
            val set = prefs[KEY_WISHLIST] ?: emptySet()
            prefs[KEY_WISHLIST] = set + productId
        }
        delay(100)
    }

    override suspend fun removeFromWishlist(productId: String) {
        // 찜하기 제거 로직 - DataStore에서 ID 제거
        context.wishlistDataStore.edit { prefs ->
            val set = prefs[KEY_WISHLIST] ?: emptySet()
            prefs[KEY_WISHLIST] = set - productId
        }
        delay(100)
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

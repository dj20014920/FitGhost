package com.fitghost.app.data.repository

import android.util.Log
import com.fitghost.app.data.model.Product
import com.fitghost.app.data.model.ProductCategory
import com.fitghost.app.data.network.GoogleCseApi
import com.fitghost.app.data.network.GoogleSearchItem
import com.fitghost.app.data.network.NaverApi
import com.fitghost.app.data.network.NaverShopItem
import com.fitghost.app.data.network.SearchApiClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * 외부 쇼핑 검색 API(Naver/Google)를 통한 상품 검색 엔진.
 * ShopRepository 및 RecommendationService에서 공통으로 사용한다.
 */
object ProductSearchEngine {

    private const val TAG = "ProductSearchEngine"

    suspend fun search(
        query: String,
        maxResults: Int = 20,
        naverApi: NaverApi = SearchApiClient.naverApi,
        googleApi: GoogleCseApi = SearchApiClient.googleApi
    ): List<Product> {
        if (query.isBlank()) return emptyList()

        return try {
            coroutineScope {
                val naverDeferred = async { searchNaver(query, naverApi) }
                val googleDeferred = async { searchGoogle(query, googleApi) }

                val combined = (naverDeferred.await() + googleDeferred.await())
                    .distinctBy { it.shopUrl }
                    .sortedByDescending { it.price }
                    .take(maxResults)
                combined
            }
        } catch (e: Exception) {
            Log.e(TAG, "search failed for: $query", e)
            emptyList()
        }
    }

    private suspend fun searchNaver(
        query: String,
        api: NaverApi
    ): List<Product> {
        return try {
            api.searchShop(
                query = query,
                display = 20
            ).items.map { it.toProduct() }
        } catch (e: Exception) {
            Log.e(TAG, "Naver search failed", e)
            emptyList()
        }
    }

    private suspend fun searchGoogle(
        query: String,
        api: GoogleCseApi
    ): List<Product> {
        return try {
            api.search(
                query = query,
                num = 10
            ).items?.mapNotNull { it.toProduct() } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Google search failed", e)
            emptyList()
        }
    }

    private fun NaverShopItem.toProduct(): Product {
        return Product(
            id = productId,
            name = title.replace(Regex("<[^>]*>"), ""), // HTML 태그 제거
            price = lprice.toIntOrNull() ?: 0,
            imageUrl = image,
            category = fromStringToProductCategory(category1),
            shopName = mallName,
            shopUrl = link,
            description = "",
            tags = listOf(category1, category2, category3).filter { it.isNotBlank() }
        )
    }

    private fun GoogleSearchItem.toProduct(): Product? {
        val imageUrl = pagemap?.cse_image?.firstOrNull()?.src ?: return null
        val metatag = pagemap?.metatags?.firstOrNull()
        val price = metatag?.get("og:price:amount")?.toIntOrNull()
            ?: extractPriceFromSnippet(snippet)

        return Product(
            id = link.hashCode().toString(),
            name = title,
            price = price,
            imageUrl = imageUrl,
            category = ProductCategory.OTHER,
            shopName = displayLink,
            shopUrl = link,
            description = snippet,
            tags = emptyList()
        )
    }

    private fun extractPriceFromSnippet(text: String): Int {
        val priceRegex = Regex("""(\d{1,3}(?:,\d{3})*)\s*원""")
        return priceRegex.find(text)
            ?.groupValues?.get(1)
            ?.replace(",", "")
            ?.toIntOrNull() ?: 0
    }
}

internal fun fromStringToProductCategory(category: String): ProductCategory {
    return when (category.lowercase()) {
        "패션의류", "의류", "상의", "티셔츠", "셔츠" -> ProductCategory.TOP
        "하의", "바지", "청바지", "스커트" -> ProductCategory.BOTTOM
        "아우터", "재킷", "코트", "점퍼" -> ProductCategory.OUTERWEAR
        "신발", "운동화", "구두", "부츠" -> ProductCategory.SHOES
        "악세서리", "가방", "모자", "벨트" -> ProductCategory.ACCESSORIES
        else -> ProductCategory.OTHER
    }
}

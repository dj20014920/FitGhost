package com.fitghost.app.data.model

/**
 * 상품 정보 모델
 * PRD: 네이버/구글 검색 API 연동 대비 설계
 */
data class Product(
    val id: String,
    val name: String,
    val price: Int,
    val imageUrl: String,
    val category: ProductCategory,
    val shopName: String,
    val shopUrl: String,
    val description: String = "",
    val tags: List<String> = emptyList(),
    val isWishlisted: Boolean = false
)

/**
 * 상품 카테고리
 */
enum class ProductCategory(val displayName: String) {
    TOP("상의"),
    BOTTOM("하의"), 
    OUTERWEAR("아우터"),
    SHOES("신발"),
    ACCESSORIES("악세서리"),
    OTHER("기타")
}

/**
 * 장바구니 아이템
 * PRD: 몰별 그룹핑을 위한 설계
 */
data class CartItem(
    val id: String = "",
    val productId: String,
    val productName: String,
    val productPrice: Int,
    val productImageUrl: String,
    val shopName: String,
    val shopUrl: String,
    // PRD: source(nav er|google) + deeplink(url) 필드 추가
    val source: String = "", // "naver" | "google" | ""
    val deeplink: String = "",
    val quantity: Int = 1,
    val addedAt: Long = System.currentTimeMillis()
)

/**
 * 몰별 장바구니 그룹
 * PRD: 몰별 순차 결제를 위한 그룹핑
 */
data class CartGroup(
    val shopName: String,
    val shopUrl: String,
    val items: List<CartItem>,
    val totalPrice: Int = items.sumOf { it.productPrice * it.quantity }
)

/**
 * AI 추천 코디 정보
 * PRD: 옷장 기반 추천 로직 지원
 */
data class OutfitRecommendation(
    val id: String,
    val title: String,
    val description: String,
    val recommendedProducts: List<Product>,
    val baseGarmentId: String?, // 기준이 되는 옷장 아이템
    val matchingReason: String, // "이 바지에는 이 옷이 어울려요"
    val score: Float = 0.0f
)
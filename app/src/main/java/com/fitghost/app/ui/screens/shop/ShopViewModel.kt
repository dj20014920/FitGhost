package com.fitghost.app.ui.screens.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitghost.app.data.model.CartItem
import com.fitghost.app.data.model.OutfitRecommendation
import com.fitghost.app.data.model.Product
import com.fitghost.app.data.repository.CartRepository
import com.fitghost.app.data.repository.ShopRepository
import com.fitghost.app.data.model.NanoBananaRequest
import com.fitghost.app.data.model.NanoBananaContext
import com.fitghost.app.data.model.UserPreferences
import com.fitghost.app.data.model.PriceRange
import com.fitghost.app.data.model.WardrobeItem
import com.fitghost.app.data.model.WeatherInfo
import com.fitghost.app.data.model.FashionRecommendation
import com.fitghost.app.data.repository.ImageSearchResult
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import android.graphics.Bitmap

/** 상점 화면 ViewModel PRD: 검색 + 추천 상태 관리, API 연동 대비 */
class ShopViewModel(
        private val shopRepository: ShopRepository,
        private val cartRepository: CartRepository
) : ViewModel() {
    
    companion object {
        // 네비게이션 간 검색 파라미터 전달용
        private var pendingSearchParams: Pair<String, String>? = null
        private var pendingSearchQuery: String? = null
        
        fun setPendingSearch(itemDescription: String, itemCategory: String) {
            pendingSearchParams = Pair(itemDescription, itemCategory)
        }
        
        fun consumePendingSearch(): Pair<String, String>? {
            val params = pendingSearchParams
            pendingSearchParams = null
            return params
        }

        fun setPendingSearchQuery(query: String) {
            pendingSearchQuery = query
        }

        private fun consumePendingSearchQuery(): String? {
            val query = pendingSearchQuery
            pendingSearchQuery = null
            return query
        }
    }

    // 검색어 상태
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // 검색 결과
    private val _searchResults = MutableStateFlow<List<Product>>(emptyList())
    val searchResults: StateFlow<List<Product>> = _searchResults.asStateFlow()

    // AI 추천 결과
    private val _recommendations = MutableStateFlow<List<OutfitRecommendation>>(emptyList())
    val recommendations: StateFlow<List<OutfitRecommendation>> = _recommendations.asStateFlow()

    // 로딩 상태
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 위시리스트 상품 스트림
    private val _wishlistProducts = MutableStateFlow<List<Product>>(emptyList())
    val wishlistProducts: StateFlow<List<Product>> = _wishlistProducts.asStateFlow()

    // AI 추천 결과 (나노바나나)
    private val _aiRecommendations = MutableStateFlow<List<FashionRecommendation>>(emptyList())
    val aiRecommendations: StateFlow<List<FashionRecommendation>> = _aiRecommendations.asStateFlow()

    // AI 추천 로딩 상태
    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    // 이미지 검색 상태 (새 기능)
    private val _imageSearchResult = MutableStateFlow<ImageSearchResult?>(null)
    val imageSearchResult: StateFlow<ImageSearchResult?> = _imageSearchResult.asStateFlow()
    
    private val _isImageSearching = MutableStateFlow(false)
    val isImageSearching: StateFlow<Boolean> = _isImageSearching.asStateFlow()

    // UI 이벤트 (스낵바 등) 공통화
    sealed class ShopUiEvent {
        data class Snackbar(val message: String) : ShopUiEvent()
    }
    private val _events = MutableSharedFlow<ShopUiEvent>()
    val events: SharedFlow<ShopUiEvent> = _events.asSharedFlow()

    // 현재 모드 (검색 vs 추천)
    val isSearchMode: StateFlow<Boolean> =
            searchQuery
                    .map { it.isNotBlank() }
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    init {
        // 초기 추천 로드
        loadRecommendations()
        // 위시리스트 구독
        shopRepository.wishlistProductsFlow()
            .onEach { _wishlistProducts.value = it }
            .launchIn(viewModelScope)
        
        // 네비게이션에서 전달된 검색 파라미터 확인
        consumePendingSearch()?.let { (description, category) ->
            searchMatchingItems(description, category)
        }
        consumePendingSearchQuery()?.let { query ->
            updateSearchQuery(query)
            if (query.isNotBlank()) {
                searchProducts(query)
            }
        }
    }

    /** 검색어 업데이트 */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isNotBlank()) {
            searchProducts(query)
        } else {
            _searchResults.value = emptyList()
        }
    }

    /** 상품 검색 실행 */
    private fun searchProducts(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val results = shopRepository.searchProducts(query)
                _searchResults.value = results
            } catch (e: Exception) {
                _searchResults.value = emptyList()
                _events.emit(ShopUiEvent.Snackbar("검색 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."))
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** AI 추천 로드 */
    private fun loadRecommendations() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val recommendations = shopRepository.getRecommendations()
                _recommendations.value = recommendations
            } catch (e: Exception) {
                _recommendations.value = emptyList()
                _events.emit(ShopUiEvent.Snackbar("추천을 불러오는 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."))
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** 상품을 장바구니에 추가 */
    fun addToCart(product: Product) {
        viewModelScope.launch {
            val cartItem =
                    CartItem(
                            productId = product.id,
                            productName = product.name,
                            productPrice = product.price,
                            productImageUrl = product.imageUrl,
                            shopName = product.shopName,
                            shopUrl = product.shopUrl,
                            quantity = 1
                    )
            cartRepository.addToCart(cartItem)
            _events.emit(ShopUiEvent.Snackbar("장바구니에 추가되었습니다."))
        }
    }

    /** 찜하기 토글 - 낙관적 업데이트 + 성공/실패 스낵바 */
    fun toggleWishlist(product: Product) {
        viewModelScope.launch {
            val newValue = !product.isWishlisted
            // 1) 즉시 UI 반영 (검색 결과)
            _searchResults.update { list ->
                list.map { if (it.id == product.id) it.copy(isWishlisted = newValue) else it }
            }
            // 2) 즉시 UI 반영 (추천 상품들)
            _recommendations.update { recs ->
                recs.map { rec ->
                    rec.copy(
                        recommendedProducts = rec.recommendedProducts.map { p ->
                            if (p.id == product.id) p.copy(isWishlisted = newValue) else p
                        }
                    )
                }
            }
            try {
                if (newValue) {
                    shopRepository.addToWishlist(product.copy(isWishlisted = true))
                    _events.emit(ShopUiEvent.Snackbar("위시리스트에 추가되었습니다."))
                } else {
                    shopRepository.removeFromWishlist(product.id)
                    _events.emit(ShopUiEvent.Snackbar("위시리스트에서 제거되었습니다."))
                }
            } catch (e: Exception) {
                // 실패 시 원복
                _searchResults.update { list ->
                    list.map { if (it.id == product.id) it.copy(isWishlisted = product.isWishlisted) else it }
                }
                _recommendations.update { recs ->
                    recs.map { rec ->
                        rec.copy(
                            recommendedProducts = rec.recommendedProducts.map { p ->
                                if (p.id == product.id) p.copy(isWishlisted = product.isWishlisted) else p
                            }
                        )
                    }
                }
                _events.emit(ShopUiEvent.Snackbar("요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요."))
            }
        }
    }

    /** 추천 새로고침 */
    fun refreshRecommendations() {
        loadRecommendations()
    }

    /** AI 기반 패션 추천 요청 */
    fun getAIFashionRecommendation(
        userMessage: String,
        wardrobeItems: List<WardrobeItem> = emptyList(),
        userPreferences: UserPreferences? = null,
        priceRange: PriceRange? = null,
        weatherInfo: WeatherInfo? = null
    ) {
        viewModelScope.launch {
            _isAiLoading.value = true
            try {
                val context = NanoBananaContext(
                    wardrobeItems = wardrobeItems,
                    userPreferences = when {
                        userPreferences != null && priceRange != null -> userPreferences.copy(priceRange = priceRange)
                        userPreferences == null && priceRange != null -> UserPreferences(priceRange = priceRange)
                        else -> userPreferences
                    },
                    weatherInfo = weatherInfo
                )
                
                val result = shopRepository.getAIRecommendations(userMessage, context)
                result.fold(
                    onSuccess = { recommendation ->
                        // AI 추천 받은 후 실제 상품 검색
                        val enrichedRecommendation = enrichRecommendationWithProducts(recommendation)
                        _aiRecommendations.value = listOf(enrichedRecommendation)
                        _events.emit(ShopUiEvent.Snackbar("AI 추천이 완료되었습니다!"))
                    },
                    onFailure = { exception ->
                        _events.emit(ShopUiEvent.Snackbar("AI 추천 중 오류가 발생했습니다: ${exception.message}"))
                    }
                )
            } catch (e: Exception) {
                _events.emit(ShopUiEvent.Snackbar("AI 추천 중 오류가 발생했습니다: ${e.message}"))
            } finally {
                _isAiLoading.value = false
            }
        }
    }
    
    /** AI 추천에 실제 상품 정보 추가 */
    private suspend fun enrichRecommendationWithProducts(recommendation: FashionRecommendation): FashionRecommendation {
        val allProducts = mutableListOf<Product>()
        
        // 각 추천 아이템에 대해 상품 검색
        recommendation.recommendedItems.forEach { item ->
            val searchQuery = item.searchKeywords.joinToString(" ").ifBlank { 
                "${item.color ?: ""} ${item.description}".trim() 
            }
            
            if (searchQuery.isNotBlank()) {
                try {
                    val products = shopRepository.searchProducts(searchQuery)
                    allProducts.addAll(products.take(3)) // 각 아이템당 최대 3개 상품
                } catch (e: Exception) {
                    android.util.Log.e("ShopViewModel", "상품 검색 실패: $searchQuery", e)
                }
            }
        }
        
        return recommendation.copy(products = allProducts.distinctBy { it.shopUrl }.take(10))
    }

    /** 스타일 분석 요청 */
    fun analyzeStyle(
        userMessage: String,
        wardrobeItems: List<WardrobeItem> = emptyList()
    ) {
        viewModelScope.launch {
            _isAiLoading.value = true
            try {
                val context = NanoBananaContext(wardrobeItems = wardrobeItems)
                
                val result = shopRepository.analyzeStyle(userMessage, context)
                result.fold(
                    onSuccess = { analysis ->
                        val enrichedAnalysis = enrichRecommendationWithProducts(analysis)
                        _aiRecommendations.value = listOf(enrichedAnalysis)
                        _events.emit(ShopUiEvent.Snackbar("스타일 분석이 완료되었습니다!"))
                    },
                    onFailure = { exception ->
                        _events.emit(ShopUiEvent.Snackbar("스타일 분석 중 오류가 발생했습니다: ${exception.message}"))
                    }
                )
            } catch (e: Exception) {
                _events.emit(ShopUiEvent.Snackbar("스타일 분석 중 오류가 발생했습니다: ${e.message}"))
            } finally {
                _isAiLoading.value = false
            }
        }
    }

    /** 코디 매칭 요청 */
    fun matchOutfit(
        userMessage: String,
        wardrobeItems: List<WardrobeItem> = emptyList(),
        targetItem: WardrobeItem? = null
    ) {
        viewModelScope.launch {
            _isAiLoading.value = true
            try {
                val allItems = if (targetItem != null) {
                    wardrobeItems + targetItem
                } else {
                    wardrobeItems
                }
                
                val context = NanoBananaContext(wardrobeItems = allItems)
                
                val result = shopRepository.matchOutfit(userMessage, context)
                result.fold(
                    onSuccess = { matching ->
                        val enrichedMatching = enrichRecommendationWithProducts(matching)
                        _aiRecommendations.value = listOf(enrichedMatching)
                        _events.emit(ShopUiEvent.Snackbar("코디 매칭이 완료되었습니다!"))
                    },
                    onFailure = { exception ->
                        _events.emit(ShopUiEvent.Snackbar("코디 매칭 중 오류가 발생했습니다: ${exception.message}"))
                    }
                )
            } catch (e: Exception) {
                _events.emit(ShopUiEvent.Snackbar("코디 매칭 중 오류가 발생했습니다: ${e.message}"))
            } finally {
                _isAiLoading.value = false
            }
        }
    }

    /** AI 추천 결과 초기화 */
    fun clearAIRecommendations() {
        _aiRecommendations.value = emptyList()
    }

    /** 간편 AI 추천 - 기본 설정으로 추천 요청 */
    fun getQuickAIRecommendation(occasion: String = "일상") {
        val defaultMessage = "${occasion}에 어울리는 코디를 추천해주세요."
        val defaultPreferences = UserPreferences(
            style = "캐주얼",
            colors = listOf("블랙", "화이트", "네이비"),
            brands = emptyList(),
            priceRange = PriceRange(min = 30000, max = 120000)
        )
        
        getAIFashionRecommendation(
            userMessage = defaultMessage,
            userPreferences = defaultPreferences,
            priceRange = defaultPreferences.priceRange
        )
    }
    
    /** 이미지로 검색 (새 사진 업로드) */
    fun searchByImage(bitmap: Bitmap) {
        viewModelScope.launch {
            _isImageSearching.value = true
            try {
                val result = shopRepository.searchByImage(bitmap)
                result.fold(
                    onSuccess = { searchResult ->
                        _imageSearchResult.value = searchResult
                        _searchResults.value = searchResult.products
                        _events.emit(ShopUiEvent.Snackbar("${searchResult.products.size}개의 상품을 찾았습니다"))
                    },
                    onFailure = { exception ->
                        _events.emit(ShopUiEvent.Snackbar("이미지 검색 실패: ${exception.message}"))
                    }
                )
            } catch (e: Exception) {
                _events.emit(ShopUiEvent.Snackbar("이미지 검색 중 오류가 발생했습니다"))
            } finally {
                _isImageSearching.value = false
            }
        }
    }
    
    /** 옷장 아이템으로 유사 상품 찾기 */
    fun searchMatchingItems(itemDescription: String, itemCategory: String) {
        viewModelScope.launch {
            _isImageSearching.value = true
            try {
                val result = shopRepository.searchMatchingItems(itemDescription, itemCategory)
                result.fold(
                    onSuccess = { searchResult ->
                        _imageSearchResult.value = searchResult
                        _searchResults.value = searchResult.products
                        _events.emit(ShopUiEvent.Snackbar("${searchResult.products.size}개의 어울리는 상품을 찾았습니다"))
                    },
                    onFailure = { exception ->
                        _events.emit(ShopUiEvent.Snackbar("검색 실패: ${exception.message}"))
                    }
                )
            } catch (e: Exception) {
                _events.emit(ShopUiEvent.Snackbar("검색 중 오류가 발생했습니다"))
            } finally {
                _isImageSearching.value = false
            }
        }
    }
    
    /** 이미지 검색 결과 초기화 */
    fun clearImageSearch() {
        _imageSearchResult.value = null
        _searchResults.value = emptyList()
    }
}

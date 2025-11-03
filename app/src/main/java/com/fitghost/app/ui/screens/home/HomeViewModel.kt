package com.fitghost.app.ui.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fitghost.app.data.model.Product
import com.fitghost.app.data.weather.WeatherRepo
import com.fitghost.app.data.repository.WardrobeRepository
import com.fitghost.app.domain.HomeOutfitRecommendation
import com.fitghost.app.domain.HomeRecommendationResult
import com.fitghost.app.domain.RecommendationParams
import com.fitghost.app.domain.RecommendationService
import com.fitghost.app.data.repository.ProductSearchEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val weather: HomeRecommendationResult? = null,
    val outfits: List<HomeOutfitRecommendation> = emptyList(),
    val error: String? = null
)

class HomeViewModel(
    private val recommendationService: RecommendationService
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    private var lastParams: RecommendationParams = RecommendationParams()

    fun refresh(latitude: Double?, longitude: Double?) {
        lastParams = RecommendationParams(
            latitude = latitude,
            longitude = longitude,
            outfitLimit = 6
        )
        requestRecommendations(lastParams)
    }

    fun retry() {
        requestRecommendations(lastParams)
    }

    private fun requestRecommendations(params: RecommendationParams) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                recommendationService.getHomeRecommendations(params)
            }.onSuccess { result ->
                _uiState.value = HomeUiState(
                    isLoading = false,
                    weather = result,
                    outfits = result.outfits,
                    error = null
                )
            }.onFailure { throwable ->
                _uiState.value = HomeUiState(
                    isLoading = false,
                    weather = null,
                    outfits = emptyList(),
                    error = throwable.message ?: "추천 정보를 불러오지 못했습니다."
                )
            }
        }
    }
}

class HomeViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            val appContext = context.applicationContext
            val wardrobeRepository = WardrobeRepository.create(appContext)
            val weatherRepo = WeatherRepo.create()
            val recommendationService = RecommendationService(
                wardrobeRepository = wardrobeRepository,
                weatherRepo = weatherRepo,
                productSearchDataSource = object : RecommendationService.ProductSearchDataSource {
                    override suspend fun searchProducts(query: String, limit: Int): List<Product> {
                        return ProductSearchEngine.search(query, maxResults = limit)
                    }
                }
            )
            return HomeViewModel(recommendationService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

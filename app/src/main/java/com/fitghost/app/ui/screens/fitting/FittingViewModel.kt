package com.fitghost.app.ui.screens.fitting

import android.net.Uri
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 가상 피팅 화면 ViewModel
 * 장바구니에서 전달된 의상 이미지 URL을 관리
 */
class FittingViewModel : ViewModel() {
    
    private val _pendingClothingUrl = MutableStateFlow<String?>(null)
    val pendingClothingUrl: StateFlow<String?> = _pendingClothingUrl.asStateFlow()
    
    /**
     * 장바구니에서 의상 이미지 URL 설정
     */
    fun setPendingClothingUrl(url: String) {
        _pendingClothingUrl.value = url
    }
    
    /**
     * 의상 URL 소비 (한 번만 사용)
     */
    fun consumePendingClothingUrl(): String? {
        val url = _pendingClothingUrl.value
        _pendingClothingUrl.value = null
        return url
    }
    
    companion object {
        private var instance: FittingViewModel? = null
        
        fun getInstance(): FittingViewModel {
            if (instance == null) {
                instance = FittingViewModel()
            }
            return instance!!
        }
    }
}

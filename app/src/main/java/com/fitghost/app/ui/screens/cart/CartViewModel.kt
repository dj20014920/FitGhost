package com.fitghost.app.ui.screens.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitghost.app.data.model.CartGroup
import com.fitghost.app.data.model.CartItem
import com.fitghost.app.data.repository.CartRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 장바구니 화면 ViewModel PRD: 몰별 그룹핑 + 순차 결제 지원 */
class CartViewModel(private val cartRepository: CartRepository) : ViewModel() {

    // 장바구니 아이템들
    val cartItems: StateFlow<List<CartItem>> =
            cartRepository.cartItems.stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(),
                    emptyList()
            )

    // 몰별 그룹
    val cartGroups: StateFlow<List<CartGroup>> =
            cartRepository.cartGroups.stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(),
                    emptyList()
            )

    // 총 아이템 수
    val totalItemCount: StateFlow<Int> =
            cartRepository.totalItemCount.stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(),
                    0
            )

    /** 아이템 수량 변경 */
    fun updateQuantity(itemId: String, quantity: Int) {
        viewModelScope.launch { cartRepository.updateQuantity(itemId, quantity) }
    }

    /** 아이템 삭제 */
    fun removeItem(itemId: String) {
        viewModelScope.launch { cartRepository.removeFromCart(itemId) }
    }

    /** 특정 몰의 장바구니 비우기 */
    fun clearShopCart(shopName: String) {
        viewModelScope.launch { cartRepository.clearShopCart(shopName) }
    }

    /** 전체 장바구니 비우기 */
    fun clearAllCart() {
        viewModelScope.launch { cartRepository.clearCart() }
    }

    /** 몰별 순차 결제 시작 PRD: Custom Tabs로 순차 오픈 */
    fun startSequentialPayment(groups: List<CartGroup>): List<String> {
        // 결제할 몰들의 URL 리스트 반환
        return groups.map { it.shopUrl }
    }
}

package com.fitghost.app.data.repository

import com.fitghost.app.data.model.CartGroup
import com.fitghost.app.data.model.CartItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/** 장바구니 Repository PRD: 로컬 장바구니(몰별 그룹) + 순차 결제 지원 */
interface CartRepository {
    val cartItems: Flow<List<CartItem>>
    val cartGroups: Flow<List<CartGroup>>
    val totalItemCount: Flow<Int>

    suspend fun addToCart(item: CartItem)
    suspend fun removeFromCart(itemId: String)
    suspend fun updateQuantity(itemId: String, quantity: Int)
    suspend fun clearCart()
    suspend fun clearShopCart(shopName: String)
}

/** Cart Repository 구현체 현재: 메모리 저장, 추후: Room DB 연동 가능 */
class CartRepositoryImpl : CartRepository {

    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    override val cartItems: Flow<List<CartItem>> = _cartItems.asStateFlow()

    override val cartGroups: Flow<List<CartGroup>> =
            cartItems.map { items ->
                items
                        .groupBy { it.shopName }
                        .map { (shopName, shopItems) ->
                            CartGroup(
                                    shopName = shopName,
                                    shopUrl = shopItems.firstOrNull()?.shopUrl ?: "",
                                    items = shopItems
                            )
                        }
                        .sortedBy { it.shopName }
            }

    override val totalItemCount: Flow<Int> = cartItems.map { items -> items.sumOf { it.quantity } }

    override suspend fun addToCart(item: CartItem) {
        delay(100) // 네트워크 지연 시뮬레이션

        val currentItems = _cartItems.value.toMutableList()
        val existingItemIndex =
                currentItems.indexOfFirst {
                    it.productId == item.productId && it.shopName == item.shopName
                }

        if (existingItemIndex != -1) {
            // 기존 아이템이 있으면 수량 증가
            val existingItem = currentItems[existingItemIndex]
            currentItems[existingItemIndex] =
                    existingItem.copy(quantity = existingItem.quantity + item.quantity)
        } else {
            // 새 아이템 추가
            currentItems.add(item.copy(id = generateCartItemId()))
        }

        _cartItems.value = currentItems
    }

    override suspend fun removeFromCart(itemId: String) {
        delay(100)

        _cartItems.value = _cartItems.value.filter { it.id != itemId }
    }

    override suspend fun updateQuantity(itemId: String, quantity: Int) {
        delay(100)

        if (quantity <= 0) {
            removeFromCart(itemId)
            return
        }

        _cartItems.value =
                _cartItems.value.map { item ->
                    if (item.id == itemId) {
                        item.copy(quantity = quantity)
                    } else {
                        item
                    }
                }
    }

    override suspend fun clearCart() {
        delay(100)
        _cartItems.value = emptyList()
    }

    override suspend fun clearShopCart(shopName: String) {
        delay(100)
        _cartItems.value = _cartItems.value.filter { it.shopName != shopName }
    }

    private fun generateCartItemId(): String {
        return "cart_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }
}

// 전역에서 공유할 수 있는 CartRepository Provider (간단 DI)
object CartRepositoryProvider {
    val instance: CartRepository by lazy { CartRepositoryImpl() }
}

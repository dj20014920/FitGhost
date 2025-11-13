package com.fitghost.app.data.repository

import com.fitghost.app.data.local.CartDao
import com.fitghost.app.data.local.CartItemEntity
import com.fitghost.app.data.model.CartGroup
import com.fitghost.app.data.model.CartItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** 장바구니 Repository PRD: 로컬 장바구니(몰별 그룹) + 순차 결제 지원 + 영속성 저장 */
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

/** Cart Repository 구현체 - Room DB 기반 영속성 저장 */
class CartRepositoryImpl(private val cartDao: CartDao) : CartRepository {

    override val cartItems: Flow<List<CartItem>> = 
        cartDao.getAllCartItems().map { entities ->
            entities.map { it.toCartItem() }
        }

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

    override val totalItemCount: Flow<Int> = 
        cartItems.map { items -> items.sumOf { it.quantity } }

    override suspend fun addToCart(item: CartItem) {
        // Flow의 첫 번째 값만 가져오기
        val currentItems = cartDao.getAllCartItems().first()
        
        val existingItem = currentItems.firstOrNull {
            it.productId == item.productId && it.shopName == item.shopName
        }

        if (existingItem != null) {
            // 기존 아이템이 있으면 수량 증가
            val updated = existingItem.copy(
                quantity = existingItem.quantity + item.quantity
            )
            cartDao.updateCartItem(updated)
        } else {
            // 새 아이템 추가
            val newItem = item.copy(
                id = if (item.id.isEmpty()) generateCartItemId() else item.id
            )
            cartDao.insertCartItem(newItem.toEntity())
        }
    }

    override suspend fun removeFromCart(itemId: String) {
        cartDao.deleteCartItem(itemId)
    }

    override suspend fun updateQuantity(itemId: String, quantity: Int) {
        if (quantity <= 0) {
            removeFromCart(itemId)
            return
        }

        // Flow의 첫 번째 값만 가져오기
        val items = cartDao.getAllCartItems().first()
        val item = items.firstOrNull { it.id == itemId }
        if (item != null) {
            cartDao.updateCartItem(item.copy(quantity = quantity))
        }
    }

    override suspend fun clearCart() {
        cartDao.clearAllCartItems()
    }

    override suspend fun clearShopCart(shopName: String) {
        cartDao.deleteCartItemsByShop(shopName)
    }

    private fun generateCartItemId(): String {
        return "cart_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }
}

// Entity <-> Model 변환 확장 함수
private fun CartItemEntity.toCartItem(): CartItem = CartItem(
    id = id,
    productId = productId,
    productName = productName,
    productPrice = productPrice,
    productImageUrl = productImageUrl,
    shopName = shopName,
    shopUrl = shopUrl,
    source = source,
    deeplink = deeplink,
    quantity = quantity,
    addedAt = addedAt
)

private fun CartItem.toEntity(): CartItemEntity = CartItemEntity(
    id = id,
    productId = productId,
    productName = productName,
    productPrice = productPrice,
    productImageUrl = productImageUrl,
    shopName = shopName,
    shopUrl = shopUrl,
    source = source,
    deeplink = deeplink,
    quantity = quantity,
    addedAt = addedAt
)

// 전역에서 공유할 수 있는 CartRepository Provider (간단 DI)
object CartRepositoryProvider {
    private var _instance: CartRepository? = null
    
    fun initialize(cartDao: CartDao) {
        if (_instance == null) {
            _instance = CartRepositoryImpl(cartDao)
        }
    }
    
    val instance: CartRepository
        get() = _instance ?: throw IllegalStateException(
            "CartRepositoryProvider must be initialized first"
        )
}

package com.fitghost.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 장바구니 DAO
 * 영속성 저장을 위한 Room Database 인터페이스
 */
@Dao
interface CartDao {
    
    @Query("SELECT * FROM cart_items ORDER BY addedAt DESC")
    fun getAllCartItems(): Flow<List<CartItemEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCartItem(item: CartItemEntity)
    
    @Update
    suspend fun updateCartItem(item: CartItemEntity)
    
    @Query("DELETE FROM cart_items WHERE id = :itemId")
    suspend fun deleteCartItem(itemId: String)
    
    @Query("DELETE FROM cart_items WHERE shopName = :shopName")
    suspend fun deleteCartItemsByShop(shopName: String)
    
    @Query("DELETE FROM cart_items")
    suspend fun clearAllCartItems()
    
    @Query("SELECT COUNT(*) FROM cart_items")
    fun getCartItemCount(): Flow<Int>
}

/**
 * 장바구니 아이템 Entity
 */
@Entity(tableName = "cart_items")
data class CartItemEntity(
    @PrimaryKey
    val id: String,
    val productId: String,
    val productName: String,
    val productPrice: Int,
    val productImageUrl: String,
    val shopName: String,
    val shopUrl: String,
    val source: String,
    val deeplink: String,
    val quantity: Int,
    val addedAt: Long
)

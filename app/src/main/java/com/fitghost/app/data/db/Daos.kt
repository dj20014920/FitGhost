package com.fitghost.app.data.db

import androidx.room.*
import com.fitghost.app.data.model.CartItem
import com.fitghost.app.data.model.Garment
import kotlinx.coroutines.flow.Flow

@Dao
interface WardrobeDao {
    @Query("SELECT * FROM wardrobe ORDER BY id DESC") fun all(): Flow<List<Garment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(item: Garment): Long

    @Delete suspend fun delete(item: Garment)

    @Query("SELECT * FROM wardrobe WHERE type = :type")
    fun byType(type: String): Flow<List<Garment>>

    @Query(
            "SELECT * FROM wardrobe WHERE color LIKE '%' || :token || '%' OR pattern LIKE '%' || :token || '%' OR fabric LIKE '%' || :token || '%' OR type LIKE '%' || :token || '%' OR tags LIKE '%' || :token || '%' ORDER BY id DESC"
    )
    fun search(token: String): Flow<List<Garment>>
}

@Dao
interface CartDao {
    @Query("SELECT * FROM cart ORDER BY id DESC") fun all(): Flow<List<CartItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(item: CartItem): Long

    @Delete suspend fun delete(item: CartItem)

    @Query("UPDATE cart SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)
}

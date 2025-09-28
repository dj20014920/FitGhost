package com.fitghost.app.data.repository

import android.content.Context
import com.fitghost.app.data.db.WardrobeCategory
import com.fitghost.app.data.db.WardrobeDao
import com.fitghost.app.data.db.WardrobeDatabase
import com.fitghost.app.data.db.WardrobeItemEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * WardrobeRepository
 *
 * Abstraction layer over [WardrobeDao] that:
 * - Exposes simple, Compose-friendly Flow APIs
 * - Ensures DB operations run on the IO dispatcher
 * - Centralizes timestamp updates for write operations
 *
 * Usage: val repo = WardrobeRepository.create(context) repo.observeAll() //
 * Flow<List<WardrobeItemEntity>> repo.upsert(item) // Insert or update with proper timestamps
 */
class WardrobeRepository(
        private val dao: WardrobeDao,
        private val io: CoroutineDispatcher = Dispatchers.IO
) {

    // Observing

    fun observeAll(): Flow<List<WardrobeItemEntity>> = dao.observeAll()

    fun observeById(id: Long): Flow<WardrobeItemEntity?> = dao.observeById(id)

    fun observeByCategory(category: WardrobeCategory): Flow<List<WardrobeItemEntity>> =
            dao.observeByCategory(category)

    fun observeFavorites(): Flow<List<WardrobeItemEntity>> = dao.observeFavorites()

    fun search(query: String): Flow<List<WardrobeItemEntity>> = dao.search(query)

    // Mutations

    /**
     * Insert when id == 0L, otherwise update.
     * - Automatically manages createdAt/updatedAt timestamps.
     * - Returns the item id (generated or existing).
     */
    suspend fun upsert(item: WardrobeItemEntity): Long =
            withContext(io) {
                val now = System.currentTimeMillis()
                return@withContext if (item.id == 0L) {
                    dao.insert(item.copy(createdAt = now, updatedAt = now))
                } else {
                    dao.update(item.copy(updatedAt = now))
                    item.id
                }
            }

    /**
     * Insert a batch of items (all treated as new).
     * - createdAt/updatedAt are normalized to call time.
     * - Returns generated ids for each inserted item.
     */
    suspend fun insertAll(items: List<WardrobeItemEntity>): List<Long> =
            withContext(io) {
                val now = System.currentTimeMillis()
                dao.insertAll(items.map { it.copy(id = 0L, createdAt = now, updatedAt = now) })
            }

    /** Update an existing item; updates updatedAt automatically. */
    suspend fun update(item: WardrobeItemEntity) =
            withContext(io) { dao.update(item.copy(updatedAt = System.currentTimeMillis())) }

    /** Delete an item. */
    suspend fun delete(item: WardrobeItemEntity) = withContext(io) { dao.delete(item) }

    /** Toggle favorite flag; updates updatedAt automatically. */
    suspend fun setFavorite(id: Long, favorite: Boolean) =
            withContext(io) {
                dao.setFavorite(
                        id = id,
                        favorite = favorite,
                        updatedAt = System.currentTimeMillis()
                )
            }

    /** Danger: Clears all wardrobe items. Intended for dev/test usage. */
    suspend fun clearAll() = withContext(io) { dao.clearAll() }

    companion object {
        /** Repository factory using the singleton Room database instance. */
        fun create(context: Context, io: CoroutineDispatcher = Dispatchers.IO): WardrobeRepository {
            val db = WardrobeDatabase.getInstance(context)
            return WardrobeRepository(db.wardrobeDao(), io)
        }
    }
}

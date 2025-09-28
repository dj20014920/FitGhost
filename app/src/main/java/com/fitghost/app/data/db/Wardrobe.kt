package com.fitghost.app.data.db

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Wardrobe: Room Entity, DAO, and Database
 *
 * Scope:
 * - Minimal but extensible schema for "옷장" CRUD(+필터) 구현
 * - Designed for Compose + Flow usage
 *
 * Notes:
 * - 'imageUri' is stored as a String (content:// or file://, etc.)
 * - 'tags' persisted as a comma-separated string via TypeConverters
 * - 'category' persisted as a String via TypeConverters
 * - 'createdAt' and 'updatedAt' are epochMillis (Long). Update them in caller code when editing.
 */

/** Wardrobe item category */
enum class WardrobeCategory {
    TOP, // 상의
    BOTTOM, // 하의
    OUTER, // 아우터
    SHOES, // 신발
    ACCESSORY, // 악세서리
    OTHER // 기타
}

/** Room converters for custom types */
class WardrobeConverters {
    @TypeConverter fun fromCategory(value: WardrobeCategory?): String? = value?.name

    @TypeConverter
    fun toCategory(value: String?): WardrobeCategory? =
            value?.let { runCatching { WardrobeCategory.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun fromTags(value: List<String>?): String? = value?.joinToString(separator = ",") { it.trim() }

    @TypeConverter
    fun toTags(value: String?): List<String> =
            value?.split(",")?.mapNotNull { it.trim().ifEmpty { null } } ?: emptyList()
}

/** Entity representing a wardrobe item */
@androidx.room.Entity(
        tableName = "wardrobe_items",
        indices =
                [Index(value = ["category"]), Index(value = ["favorite"]), Index(value = ["name"])]
)
data class WardrobeItemEntity(
        @PrimaryKey(autoGenerate = true) val id: Long = 0L,
        @ColumnInfo(name = "name") val name: String,
        @ColumnInfo(name = "category") val category: WardrobeCategory = WardrobeCategory.OTHER,

        // Could be a content://, file://, or http(s) URL
        @ColumnInfo(name = "imageUri") val imageUri: String? = null,
        @ColumnInfo(name = "brand") val brand: String? = null,
        @ColumnInfo(name = "color") val color: String? = null,
        @ColumnInfo(name = "size") val size: String? = null,
        @ColumnInfo(name = "favorite") val favorite: Boolean = false,
        @ColumnInfo(name = "tags") val tags: List<String> = emptyList(),

        // epoch millis
        @ColumnInfo(name = "createdAt") val createdAt: Long = System.currentTimeMillis(),

        // epoch millis
        @ColumnInfo(name = "updatedAt") val updatedAt: Long = System.currentTimeMillis()
)

/** Data Access Object for Wardrobe items */
@Dao
interface WardrobeDao {

    // Create
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: WardrobeItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<WardrobeItemEntity>): List<Long>

    // Read
    @Query("SELECT * FROM wardrobe_items ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<WardrobeItemEntity>>

    @Query("SELECT * FROM wardrobe_items WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<WardrobeItemEntity?>

    @Query("SELECT * FROM wardrobe_items WHERE category = :category ORDER BY updatedAt DESC")
    fun observeByCategory(category: WardrobeCategory): Flow<List<WardrobeItemEntity>>

    @Query("SELECT * FROM wardrobe_items WHERE favorite = 1 ORDER BY updatedAt DESC")
    fun observeFavorites(): Flow<List<WardrobeItemEntity>>

    @Query(
            """
        SELECT * FROM wardrobe_items
        WHERE
            name LIKE '%' || :query || '%'
            OR brand LIKE '%' || :query || '%'
            OR color LIKE '%' || :query || '%'
        ORDER BY updatedAt DESC
        """
    )
    fun search(query: String): Flow<List<WardrobeItemEntity>>

    // Update
    @Update suspend fun update(item: WardrobeItemEntity)

    @Query("UPDATE wardrobe_items SET favorite = :favorite, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean, updatedAt: Long)

    // Delete
    @Delete suspend fun delete(item: WardrobeItemEntity)

    @Query("DELETE FROM wardrobe_items") suspend fun clearAll()
}

/** Room database for Wardrobe module */
@Database(entities = [WardrobeItemEntity::class], version = 1, exportSchema = false)
@TypeConverters(WardrobeConverters::class)
abstract class WardrobeDatabase : RoomDatabase() {
    abstract fun wardrobeDao(): WardrobeDao

    companion object {
        @Volatile private var INSTANCE: WardrobeDatabase? = null

        fun getInstance(context: Context): WardrobeDatabase {
            return INSTANCE
                    ?: synchronized(this) {
                        INSTANCE
                                ?: Room.databaseBuilder(
                                                context.applicationContext,
                                                WardrobeDatabase::class.java,
                                                "wardrobe.db"
                                        )
                                        .fallbackToDestructiveMigration() // Safe for early dev
                                        // phase; replace for
                                        // production migration
                                        .build()
                                        .also { INSTANCE = it }
                    }
        }
    }
}

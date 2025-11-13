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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import com.fitghost.app.constants.CategoryConstants

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

/**
 * 카테고리 엔티티: 기본 카테고리 + 사용자 정의 카테고리 관리
 * - 기본 카테고리(isDefault=false): 상의, 하의, 아우터, 신발, 악세서리, 기타 (모두 수정/삭제 가능)
 * - 사용자 정의 카테고리(isDefault=false): 사용자가 추가한 카테고리 (예: 양말, 모자 등)
 */
@androidx.room.Entity(
    tableName = "categories",
    indices = [Index(value = ["orderIndex"])]
)
data class CategoryEntity(
    @PrimaryKey val id: String, // 카테고리 ID (영문/한글 모두 가능, 예: "상의", "양말")
    @ColumnInfo(name = "displayName") val displayName: String, // 사용자에게 표시되는 이름
    @ColumnInfo(name = "isDefault") val isDefault: Boolean = false, // 기본 카테고리 여부
    @ColumnInfo(name = "orderIndex") val orderIndex: Int = 0, // 표시 순서 (낮을수록 먼저)
    @ColumnInfo(name = "createdAt") val createdAt: Long = System.currentTimeMillis()
)

/** Room converters for custom types */
class WardrobeConverters {
    // Category는 이제 String으로 직접 저장되므로 변환 불필요
    
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
        @ColumnInfo(name = "category") val category: String = "기타", // 카테고리 ID (CategoryEntity의 id와 매칭)

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
    fun observeByCategory(category: String): Flow<List<WardrobeItemEntity>>

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


/** Data Access Object for Categories */
@Dao
interface CategoryDao {
    
    // Create
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>): List<Long>
    
    // Read
    @Query("SELECT * FROM categories ORDER BY orderIndex ASC, displayName ASC")
    fun observeAll(): Flow<List<CategoryEntity>>
    
    @Query("SELECT * FROM categories WHERE isDefault = 1 ORDER BY orderIndex ASC")
    fun observeDefaultCategories(): Flow<List<CategoryEntity>>
    
    @Query("SELECT * FROM categories WHERE isDefault = 0 ORDER BY orderIndex ASC, displayName ASC")
    fun observeCustomCategories(): Flow<List<CategoryEntity>>
    
    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CategoryEntity?
    
    @Query("SELECT * FROM categories ORDER BY orderIndex ASC, displayName ASC")
    suspend fun getAll(): List<CategoryEntity>
    
    // Update
    @Update
    suspend fun update(category: CategoryEntity)

    @Update
    suspend fun updateAll(categories: List<CategoryEntity>)
    
    /**
     * 특정 카테고리를 사용하는 모든 아이템의 카테고리를 일괄 변경
     */
    @Query("UPDATE wardrobe_items SET category = :newCategoryId WHERE category = :oldCategoryId")
    suspend fun updateItemsCategory(oldCategoryId: String, newCategoryId: String)
    
    /**
     * 특정 카테고리에 속한 아이템 개수 조회
     */
    @Query("SELECT COUNT(*) FROM wardrobe_items WHERE category = :categoryId")
    suspend fun countItemsInCategory(categoryId: String): Int
    
    // Delete
    @Delete
    suspend fun delete(category: CategoryEntity)
    
    @Query("DELETE FROM categories WHERE isDefault = 0")
    suspend fun deleteAllCustomCategories()
    
    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: String)
}

/**
 * Migration from version 1 to 2:
 * - 카테고리 테이블 추가 (categories)
 * - wardrobe_items.category 타입을 enum에서 String으로 변경
 * - 기존 enum 값을 한글 문자열로 매핑
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. 카테고리 테이블 생성
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS categories (
                id TEXT PRIMARY KEY NOT NULL,
                displayName TEXT NOT NULL,
                isDefault INTEGER NOT NULL DEFAULT 0,
                orderIndex INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL
            )
        """)
        
        db.execSQL("CREATE INDEX IF NOT EXISTS index_categories_orderIndex ON categories(orderIndex)")
        
        // 2. 기본 카테고리 삽입 (모두 수정/삭제 가능하도록 isDefault=0)
        val now = System.currentTimeMillis()
        val values = CategoryConstants.toSqlInsertValues(now)
        db.execSQL(
            "INSERT INTO categories (id, displayName, isDefault, orderIndex, createdAt) VALUES $values"
        )
        
        // 3. wardrobe_items의 category 컬럼을 String으로 변환
        // 임시 테이블 생성
        db.execSQL("""
            CREATE TABLE wardrobe_items_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                category TEXT NOT NULL DEFAULT '기타',
                imageUri TEXT,
                brand TEXT,
                color TEXT,
                size TEXT,
                favorite INTEGER NOT NULL DEFAULT 0,
                tags TEXT NOT NULL DEFAULT '',
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
        """)
        
        // 인덱스 생성
        db.execSQL("CREATE INDEX IF NOT EXISTS index_wardrobe_items_new_category ON wardrobe_items_new(category)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_wardrobe_items_new_favorite ON wardrobe_items_new(favorite)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_wardrobe_items_new_name ON wardrobe_items_new(name)")
        
        // 데이터 복사 (enum 값을 한글로 매핑)
        db.execSQL("""
            INSERT INTO wardrobe_items_new (id, name, category, imageUri, brand, color, size, favorite, tags, createdAt, updatedAt)
            SELECT 
                id,
                name,
                CASE category
                    WHEN 'TOP' THEN '상의'
                    WHEN 'BOTTOM' THEN '하의'
                    WHEN 'OUTER' THEN '아우터'
                    WHEN 'SHOES' THEN '신발'
                    WHEN 'ACCESSORY' THEN '악세서리'
                    ELSE '기타'
                END,
                imageUri,
                brand,
                color,
                size,
                favorite,
                tags,
                createdAt,
                updatedAt
            FROM wardrobe_items
        """)
        
        // 기존 테이블 삭제
        db.execSQL("DROP TABLE wardrobe_items")
        
        // 새 테이블 이름 변경
        db.execSQL("ALTER TABLE wardrobe_items_new RENAME TO wardrobe_items")
    }
}

/** Room database for Wardrobe module */
@Database(entities = [WardrobeItemEntity::class, CategoryEntity::class], version = 2, exportSchema = false)
@TypeConverters(WardrobeConverters::class)
abstract class WardrobeDatabase : RoomDatabase() {
    abstract fun wardrobeDao(): WardrobeDao
    abstract fun categoryDao(): CategoryDao

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
                                        .addMigrations(MIGRATION_1_2)
                                        .build()
                                        .also { INSTANCE = it }
                    }
        }
    }
}

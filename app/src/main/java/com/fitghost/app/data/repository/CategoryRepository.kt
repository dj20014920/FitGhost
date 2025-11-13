package com.fitghost.app.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.fitghost.app.constants.CategoryConstants
import com.fitghost.app.data.db.CategoryDao
import com.fitghost.app.data.db.CategoryEntity
import com.fitghost.app.data.db.WardrobeDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * 카테고리 관리 Repository
 *
 * 책임:
 * - 카테고리 CRUD 작업
 * - 기본 카테고리 초기화
 * - 사용자 정의 카테고리 관리
 *
 * 설계 원칙:
 * - SRP: 카테고리 관련 데이터 작업만 담당
 * - DRY: 카테고리 관련 로직을 한 곳에 집중
 */
class CategoryRepository(context: Context) {

    private val db: WardrobeDatabase = WardrobeDatabase.getInstance(context)
    private val dao: CategoryDao = db.categoryDao()

    private suspend fun ensureUnderLimit() {
        val currentCount = dao.getAll().size
        if (currentCount >= CategoryConstants.MAX_CATEGORIES) {
            throw IllegalStateException("최대 ${CategoryConstants.MAX_CATEGORIES}개까지만 카테고리를 추가할 수 있습니다")
        }
    }

    /**
     * 모든 카테고리 조회 (기본 + 사용자 정의)
     * orderIndex 오름차순 정렬
     */
    fun observeAll(): Flow<List<CategoryEntity>> = dao.observeAll()

    /**
     * 기본 카테고리만 조회
     */
    fun observeDefaultCategories(): Flow<List<CategoryEntity>> = dao.observeDefaultCategories()

    /**
     * 사용자 정의 카테고리만 조회
     */
    fun observeCustomCategories(): Flow<List<CategoryEntity>> = dao.observeCustomCategories()

    /**
     * 특정 ID의 카테고리 조회
     */
    suspend fun getById(id: String): CategoryEntity? = dao.getById(id)

    /**
     * 모든 카테고리 조회 (동기)
     */
    suspend fun getAll(): List<CategoryEntity> = dao.getAll()

    suspend fun addCategory(id: String, displayName: String): Result<Long> {
        return try {
            // 최대 개수 제한 체크
            ensureUnderLimit()
            val allCategories = dao.getAll()
            
            // 중복 체크
            val existing = dao.getById(id)
            if (existing != null) {
                return Result.failure(IllegalArgumentException("이미 존재하는 카테고리입니다: $id"))
            }

            // 현재 최대 orderIndex 가져오기
            val maxOrder = allCategories.maxOfOrNull { it.orderIndex } ?: 0

            val newCategory = CategoryEntity(
                id = id,
                displayName = displayName,
                isDefault = false,
                orderIndex = maxOrder + 1
            )

            val insertedId = dao.insert(newCategory)
            Result.success(insertedId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 카테고리 업데이트
     */
    suspend fun update(category: CategoryEntity): Result<Unit> {
        return try {
            dao.update(category)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 카테고리 이름 변경
     * 카테고리와 해당 카테고리를 사용하는 모든 아이템을 함께 업데이트
     * 
     * @param oldId 기존 카테고리 ID
     * @param newId 새 카테고리 ID
     * @param newDisplayName 새 표시 이름
     * @return 변경 성공 여부
     */
    suspend fun renameCategory(oldId: String, newId: String, newDisplayName: String): Result<Unit> {
        return try {
            db.withTransaction {
                val category = dao.getById(oldId)
                    ?: throw IllegalArgumentException("존재하지 않는 카테고리입니다: $oldId")

                if (oldId == newId) {
                    // ID 변경 없음 → 표시 이름만 변경
                    dao.update(category.copy(displayName = newDisplayName))
                } else {
                    // 새 ID 중복 체크
                    if (dao.getById(newId) != null) {
                        throw IllegalArgumentException("이미 존재하는 카테고리 이름입니다: $newId")
                    }

                    // 새 카테고리 생성 (orderIndex, isDefault 유지)
                    dao.insert(
                        CategoryEntity(
                            id = newId,
                            displayName = newDisplayName,
                            isDefault = category.isDefault,
                            orderIndex = category.orderIndex
                        )
                    )

                    // 아이템 참조 업데이트
                    dao.updateItemsCategory(oldId, newId)

                    // 기존 카테고리 삭제
                    dao.deleteById(oldId)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteCategory(id: String): Result<Unit> {
        return try {
            db.withTransaction {
                val category = dao.getById(id)
                    ?: throw IllegalArgumentException("존재하지 않는 카테고리입니다: $id")

                val allCategories = dao.getAll()

                // 최소 1개 카테고리는 유지
                if (allCategories.size == 1) {
                    throw IllegalStateException("최소 1개의 카테고리는 유지해야 합니다.\n다른 카테고리를 추가한 후 삭제해주세요.")
                }

                // 폴백 카테고리 보장 (한도 체크 포함)
                if (id != CategoryConstants.FALLBACK_CATEGORY &&
                    dao.getById(CategoryConstants.FALLBACK_CATEGORY) == null
                ) {
                    // 삭제와 동시에 폴백을 생성하는 경우, 최종 개수는 유지되므로 별도 한도 체크 불필요
                    val maxOrder = allCategories.maxOfOrNull { it.orderIndex } ?: 0
                    dao.insert(
                        CategoryEntity(
                            id = CategoryConstants.FALLBACK_CATEGORY,
                            displayName = CategoryConstants.FALLBACK_CATEGORY,
                            isDefault = false,
                            orderIndex = maxOrder + 1
                        )
                    )
                }

                if (id != CategoryConstants.FALLBACK_CATEGORY) {
                    // 삭제 대상의 아이템을 폴백 카테고리로 이동
                    dao.updateItemsCategory(id, CategoryConstants.FALLBACK_CATEGORY)
                } else {
                    // 폴백(기타) 삭제 시 → 다른 카테고리로 재할당
                    val itemCount = dao.countItemsInCategory(CategoryConstants.FALLBACK_CATEGORY)
                    if (itemCount > 0) {
                        val target = dao.getAll().firstOrNull { it.id != CategoryConstants.FALLBACK_CATEGORY }
                            ?: throw IllegalStateException("재할당 대상 카테고리가 없습니다")
                        dao.updateItemsCategory(CategoryConstants.FALLBACK_CATEGORY, target.id)
                    }
                }

                // 카테고리 삭제
                dao.deleteById(id)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 모든 사용자 정의 카테고리 삭제
     */
    suspend fun deleteAllCustomCategories(): Result<Unit> {
        return try {
            dao.deleteAllCustomCategories()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 기본 카테고리 초기화
     * 앱 최초 실행 시 또는 카테고리가 없을 때 호출
     */
    suspend fun ensureDefaultCategories() {
        val existing = dao.getAll()
        if (existing.isEmpty()) {
            // isDefault=false 로 통일 (모두 편집/삭제 가능)
            dao.insertAll(CategoryConstants.toEntities(isDefault = false))
        }
    }

    /**
     * 카테고리 재정렬 (orderIndex 재배치)
     * @param newOrderIds 새 순서의 카테고리 ID 리스트 (전체 포함, 중복/누락 불가)
     */
    suspend fun reorderCategories(newOrderIds: List<String>): Result<Unit> {
        return try {
            db.withTransaction {
                val current = dao.getAll()
                val currentIds = current.map { it.id }
                if (newOrderIds.toSet() != currentIds.toSet() || newOrderIds.size != currentIds.size) {
                    throw IllegalArgumentException("재정렬 리스트가 현재 카테고리와 일치하지 않습니다")
                }
                val byId = current.associateBy { it.id }
                val reordered = newOrderIds.mapIndexed { idx, id ->
                    val base = byId[id] ?: error("카테고리를 찾을 수 없습니다: $id")
                    base.copy(orderIndex = idx)
                }
                dao.updateAll(reordered)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

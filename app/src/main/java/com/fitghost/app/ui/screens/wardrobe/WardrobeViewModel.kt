package com.fitghost.app.ui.screens.wardrobe

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fitghost.app.data.db.WardrobeCategory
import com.fitghost.app.data.db.WardrobeItemEntity
import com.fitghost.app.data.repository.WardrobeRepository
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * WardrobeViewModel
 *
 * Responsibilities:
 * - Hold UI state (items, filtered items, filters, loading/error)
 * - Provide filter/search logic (category, favoritesOnly, query)
 * - Expose simple APIs for CRUD actions via [WardrobeRepository]
 *
 * Usage: val vm: WardrobeViewModel = viewModel(factory = WardrobeViewModelFactory(context)) val
 * state by vm.uiState.collectAsState()
 */
class WardrobeViewModel(private val repo: WardrobeRepository) : ViewModel() {

    // UI filters
    data class WardrobeFilter(
            val category: WardrobeCategory? = null,
            val favoritesOnly: Boolean = false,
            val query: String = ""
    )

    // Complete UI state
    data class WardrobeUiState(
            val items: List<WardrobeItemEntity> = emptyList(),
            val filtered: List<WardrobeItemEntity> = emptyList(),
            val filter: WardrobeFilter = WardrobeFilter(),
            val isLoading: Boolean = false,
            val error: String? = null
    ) {
        val isEmpty: Boolean
            get() = filtered.isEmpty()
    }

    private val filter = MutableStateFlow(WardrobeFilter())

    // Base data source
    private val itemsFlow = repo.observeAll()

    // UI state exposed to the screen
    val uiState: StateFlow<WardrobeUiState> =
            combine(itemsFlow, filter) { items, f ->
                        // Apply filters in-memory; DAO already sorts by updatedAt DESC
                        val filtered =
                                items.asSequence()
                                        .filter { f.category == null || it.category == f.category }
                                        .filter { !f.favoritesOnly || it.favorite }
                                        .filter { f.query.isBlank() || matchesQuery(it, f.query) }
                                        .toList()

                        WardrobeUiState(
                                items = items,
                                filtered = filtered,
                                filter = f,
                                isLoading = false,
                                error = null
                        )
                    }
                    .stateIn(
                            scope = viewModelScope,
                            started = SharingStarted.WhileSubscribed(5_000),
                            initialValue = WardrobeUiState()
                    )

    // region Filter actions
    fun setCategory(category: WardrobeCategory?) {
        filter.update { it.copy(category = category) }
    }

    fun toggleFavoritesOnly() {
        filter.update { it.copy(favoritesOnly = !it.favoritesOnly) }
    }

    fun setSearchQuery(query: String) {
        filter.update { it.copy(query = query) }
    }

    fun clearFilters() {
        filter.value = WardrobeFilter()
    }
    // endregion

    // region Data mutations
    fun upsert(item: WardrobeItemEntity) {
        viewModelScope.launch { repo.upsert(item) }
    }

    fun update(item: WardrobeItemEntity) {
        viewModelScope.launch { repo.update(item) }
    }

    fun delete(item: WardrobeItemEntity) {
        viewModelScope.launch { repo.delete(item) }
    }

    fun setFavorite(id: Long, favorite: Boolean) {
        viewModelScope.launch { repo.setFavorite(id, favorite) }
    }

    fun clearAll() {
        viewModelScope.launch { repo.clearAll() }
    }
    // endregion

    // region Helpers
    private fun matchesQuery(item: WardrobeItemEntity, rawQuery: String): Boolean {
        val q = rawQuery.trim().lowercase(Locale.getDefault())
        if (q.isEmpty()) return true

        val name = item.name.lowercase(Locale.getDefault())
        val brand = item.brand?.lowercase(Locale.getDefault()).orEmpty()
        val color = item.color?.lowercase(Locale.getDefault()).orEmpty()
        val tags = item.tags.joinToString(",").lowercase(Locale.getDefault())

        return name.contains(q) || brand.contains(q) || color.contains(q) || tags.contains(q)
    }
    // endregion
}

/** Simple ViewModel factory creating a [WardrobeRepository] from [Context]. */
class WardrobeViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(clazz: Class<T>): T {
        if (clazz.isAssignableFrom(WardrobeViewModel::class.java)) {
            val repo = WardrobeRepository.create(context.applicationContext)
            return WardrobeViewModel(repo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${clazz.name}")
    }
}

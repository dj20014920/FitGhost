package com.fitghost.app.ui.screens.wardrobe

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.fitghost.app.ai.ModelManager
import com.fitghost.app.data.db.WardrobeItemEntity
import com.fitghost.app.data.db.CategoryEntity
import com.fitghost.app.ui.components.softClayInset
import com.fitghost.app.ui.theme.FitGhostColors
import com.fitghost.app.ui.theme.Spacing
import com.fitghost.app.ui.theme.IconSize
import com.fitghost.app.ui.theme.CornerRadius
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.fitghost.app.constants.CategoryConstants

/** 옷장 메인 화면 PRD: Wardrobe CRUD + 필터 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WardrobeScreen(
    onNavigateToAdd: () -> Unit = {},
    onNavigateToShop: (itemDescription: String, itemCategory: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val viewModel: WardrobeViewModel = viewModel(factory = WardrobeViewModelFactory(context))
    val state by viewModel.uiState.collectAsState()
    val categories by viewModel.categories.collectAsState()
    
    // 카테고리 추가 다이얼로그 상태
    var showCategoryManagementDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(FitGhostColors.BgPrimary)) {
        // 상단 앱바
        TopAppBar(
                title = {
                    Text(
                            text = "옷장",
                            style = MaterialTheme.typography.headlineLarge,
                            color = FitGhostColors.TextPrimary,
                            fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { showCategoryManagementDialog = true }) {
                        Icon(imageVector = Icons.Outlined.Category, contentDescription = "카테고리 관리")
                    }
                    IconButton(onClick = onNavigateToAdd) {
                        Icon(imageVector = Icons.Outlined.Add, contentDescription = "아이템 추가")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = FitGhostColors.BgGlass)
        )

        // 필터 & 검색
        Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 카테고리 필터 칩 (가로 스크롤 지원)
            LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    CategoryChip(
                            label = "전체",
                            selected = state.filter.category == null,
                            onClick = { viewModel.setCategory(null) }
                    )
                }
                items(categories) { cat ->
                    CategoryChip(
                            label = cat.displayName,
                            selected = state.filter.category == cat.id,
                            onClick = { viewModel.setCategory(cat.id) }
                    )
                }
            }

            // 즐겨찾기/초기화
            Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                        selected = state.filter.favoritesOnly,
                        onClick = { viewModel.toggleFavoritesOnly() },
                        label = { Text("즐겨찾기") },
                        leadingIcon = {
                            Icon(
                                    imageVector =
                                            if (state.filter.favoritesOnly) Icons.Outlined.Favorite
                                            else Icons.Outlined.FavoriteBorder,
                                    contentDescription = null
                            )
                        }
                )
                TextButton(onClick = { viewModel.clearFilters() }) { Text("필터 초기화") }
            }

            // 검색
            OutlinedTextField(
                    value = state.filter.query,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("검색 (이름/브랜드/색상/태그)") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Outlined.Search, contentDescription = null)
                    },
                    shape = RoundedCornerShape(12.dp)
            )
        }

        // 컨텐츠
        if (state.isEmpty) {
            EmptyWardrobe()
        } else {
            LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.filtered, key = { it.id }) { item ->
                    WardrobeItemRow(
                            item = item,
                            onToggleFavorite = { viewModel.setFavorite(item.id, !item.favorite) },
                            onDelete = { viewModel.delete(item) },
                            onClick = { /* 편집 네비게이션 연결용 콜백 확장 예정 */ },
                            onFindSimilar = {
                                val description = "${item.color} ${item.name}".trim()
                                val category = categoryLabel(item.category)
                                onNavigateToShop(description, category)
                            }
                    )
                }
            }
        }
    }
    
    // 카테고리 관리 다이얼로그
    if (showCategoryManagementDialog) {
        CategoryManagementDialog(
            categories = categories,
            onDismiss = { showCategoryManagementDialog = false },
            onAddCategory = { categoryName, onResult ->
                viewModel.addCategory(categoryName, onResult)
            },
            onRenameCategory = { oldId, newId, newDisplayName, onResult ->
                viewModel.renameCategory(oldId, newId, newDisplayName, onResult)
            },
            onDeleteCategory = { categoryId, onResult ->
                viewModel.deleteCategory(categoryId, onResult)
            },
            onReorderCategories = { newOrderIds, onResult ->
                viewModel.reorderCategories(newOrderIds, onResult)
            }
        )
    }
}



/**
 * 카테고리 추가 다이얼로그
 * 사용자가 새로운 카테고리를 생성할 수 있음
 */
@Composable
private fun AddCategoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    existingCategories: List<String>
) {
    var categoryName by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("새 카테고리 추가") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "새로운 카테고리 이름을 입력하세요 (예: 양말, 모자, 스카프)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FitGhostColors.TextSecondary
                )
                OutlinedTextField(
                    value = categoryName,
                    onValueChange = {
                        categoryName = it
                        errorMessage = null
                    },
                    label = { Text("카테고리 이름") },
                    singleLine = true,
                    isError = errorMessage != null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = categoryName.trim()
                    when {
                        trimmed.isEmpty() -> {
                            errorMessage = "카테고리 이름을 입력하세요"
                        }
                        existingCategories.contains(trimmed) -> {
                            errorMessage = "이미 존재하는 카테고리입니다"
                        }
                        else -> {
                            onConfirm(trimmed)
                        }
                    }
                }
            ) {
                Text("추가")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

/**
 * 카테고리 관리 다이얼로그
 * 카테고리 목록을 보여주고 추가/편집/삭제 기능 제공
 */
@Composable
private fun CategoryManagementDialog(
    categories: List<com.fitghost.app.data.db.CategoryEntity>,
    onDismiss: () -> Unit,
    onAddCategory: (String, (Result<Unit>) -> Unit) -> Unit,
    onRenameCategory: (String, String, String, (Result<Unit>) -> Unit) -> Unit,
    onDeleteCategory: (String, (Result<Unit>) -> Unit) -> Unit,
    onReorderCategories: (List<String>, (Result<Unit>) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf<com.fitghost.app.data.db.CategoryEntity?>(null) }
    var showDeleteDialog by remember { mutableStateOf<com.fitghost.app.data.db.CategoryEntity?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 로컬 순서 편집 상태 (최대 12개이므로 Column 기반으로 충분)
    val localOrder = remember(categories) {
        mutableStateListOf<String>().also { list -> list.addAll(categories.map { it.id }) }
    }
    val idToCategory = remember(categories) { categories.associateBy { it.id } }
    val isOrderChanged = remember(localOrder, categories) {
        localOrder.toList() != categories.map { it.id }
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text("카테고리 관리") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 안내 메시지
                androidx.compose.material3.Text(
                    "최대 ${CategoryConstants.MAX_CATEGORIES}개까지 카테고리를 만들 수 있습니다. (현재: ${categories.size}/${CategoryConstants.MAX_CATEGORIES})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FitGhostColors.TextSecondary
                )

                if (errorMessage != null) {
                    androidx.compose.material3.Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 카테고리 목록 (로컬 순서 표시)
                localOrder.forEachIndexed { index, id ->
                    val category = idToCategory[id] ?: return@forEachIndexed
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Text(
                            text = category.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // 순서 이동 (위/아래)
                            androidx.compose.material3.IconButton(
                                onClick = {
                                    if (index > 0) {
                                        localOrder.removeAt(index)
                                        localOrder.add(index - 1, id)
                                    }
                                },
                                enabled = index > 0
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Outlined.KeyboardArrowUp,
                                    contentDescription = "위로",
                                    tint = if (index > 0) FitGhostColors.AccentPrimary else FitGhostColors.TextTertiary
                                )
                            }
                            androidx.compose.material3.IconButton(
                                onClick = {
                                    if (index < localOrder.lastIndex) {
                                        localOrder.removeAt(index)
                                        localOrder.add(index + 1, id)
                                    }
                                },
                                enabled = index < localOrder.lastIndex
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = "아래로",
                                    tint = if (index < localOrder.lastIndex) FitGhostColors.AccentPrimary else FitGhostColors.TextTertiary
                                )
                            }
                            // 편집 버튼
                            androidx.compose.material3.IconButton(
                                onClick = { showEditDialog = category }
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Outlined.Edit,
                                    contentDescription = "편집",
                                    tint = FitGhostColors.AccentPrimary
                                )
                            }

                            // 삭제 버튼
                            androidx.compose.material3.IconButton(
                                onClick = { showDeleteDialog = category }
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Outlined.DeleteOutline,
                                    contentDescription = "삭제",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                // 새 카테고리 추가 버튼
                if (categories.size < CategoryConstants.MAX_CATEGORIES) {
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = androidx.compose.material.icons.Icons.Outlined.Add,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        androidx.compose.material3.Text("새 카테고리 추가")
                    }
                }

                // 순서 저장 버튼 (변경 시에만 활성화)
                if (isOrderChanged) {
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.material3.Button(
                        onClick = {
                            onReorderCategories(localOrder.toList()) { result ->
                                result.onSuccess {
                                    errorMessage = null
                                    Toast.makeText(context, "순서가 저장되었습니다", Toast.LENGTH_SHORT).show()
                                }.onFailure { e ->
                                    errorMessage = e.message
                                    Toast.makeText(
                                        context,
                                        e.message ?: "순서 저장 실패",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        androidx.compose.material3.Text("순서 저장")
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                androidx.compose.material3.Text("닫기")
            }
        }
    )

    // 카테고리 추가 다이얼로그
    if (showAddDialog) {
        val context = LocalContext.current
        AddCategoryDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { categoryName ->
                onAddCategory(categoryName) { result ->
                    result.onSuccess {
                        showAddDialog = false
                        errorMessage = null
                        Toast.makeText(context, "카테고리가 추가되었습니다", Toast.LENGTH_SHORT).show()
                    }.onFailure { e ->
                        errorMessage = e.message
                        Toast.makeText(
                            context,
                            e.message ?: "카테고리 추가 실패",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            },
            existingCategories = categories.map { it.id }
        )
    }

    // 카테고리 편집 다이얼로그
    showEditDialog?.let { category ->
        EditCategoryDialog(
            category = category,
            onDismiss = { showEditDialog = null },
            onConfirm = { newId, newDisplayName ->
                onRenameCategory(category.id, newId, newDisplayName) { result ->
                    result.onSuccess {
                        showEditDialog = null
                        errorMessage = null
                        Toast.makeText(context, "카테고리가 수정되었습니다", Toast.LENGTH_SHORT).show()
                    }.onFailure { e ->
                        errorMessage = e.message
                        Toast.makeText(
                            context,
                            e.message ?: "카테고리 수정 실패",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            },
            existingCategories = categories.map { it.id }.filter { it != category.id }
        )
    }

    // 카테고리 삭제 확인 다이얼로그
    showDeleteDialog?.let { category ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { androidx.compose.material3.Text("카테고리 삭제") },
            text = {
                val msg = if (category.id == CategoryConstants.FALLBACK_CATEGORY) {
                    "\"${category.displayName}\" 카테고리를 삭제하시겠습니까?\n\n" +
                    "이 카테고리에 속한 모든 아이템은 다른 카테고리로 이동됩니다."
                } else {
                    "\"${category.displayName}\" 카테고리를 삭제하시겠습니까?\n\n" +
                    "이 카테고리에 속한 모든 아이템은 \"${CategoryConstants.FALLBACK_CATEGORY}\" 카테고리로 이동됩니다."
                }
                androidx.compose.material3.Text(msg)
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        onDeleteCategory(category.id) { result ->
                            result.onSuccess {
                                showDeleteDialog = null
                                errorMessage = null
                                Toast.makeText(context, "카테고리가 삭제되었습니다", Toast.LENGTH_SHORT).show()
                            }.onFailure { e ->
                                errorMessage = e.message
                                showDeleteDialog = null
                                Toast.makeText(
                                    context,
                                    e.message ?: "카테고리 삭제 실패",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                ) {
                    androidx.compose.material3.Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteDialog = null }) {
                    androidx.compose.material3.Text("취소")
                }
            }
        )
    }
}

/**
 * 카테고리 편집 다이얼로그
 */
@Composable
private fun EditCategoryDialog(
    category: com.fitghost.app.data.db.CategoryEntity,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
    existingCategories: List<String>
) {
    var categoryName by remember { mutableStateOf(category.displayName) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text("카테고리 편집") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Text(
                    "카테고리 이름을 수정하세요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FitGhostColors.TextSecondary
                )
                androidx.compose.material3.OutlinedTextField(
                    value = categoryName,
                    onValueChange = {
                        categoryName = it
                        errorMessage = null
                    },
                    label = { androidx.compose.material3.Text("카테고리 이름") },
                    singleLine = true,
                    isError = errorMessage != null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMessage != null) {
                    androidx.compose.material3.Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = {
                    val trimmed = categoryName.trim()
                    when {
                        trimmed.isEmpty() -> {
                            errorMessage = "카테고리 이름을 입력하세요"
                        }
                        existingCategories.contains(trimmed) -> {
                            errorMessage = "이미 존재하는 카테고리입니다"
                        }
                        else -> {
                            onConfirm(trimmed, trimmed) // newId = newDisplayName
                        }
                    }
                }
            ) {
                androidx.compose.material3.Text("저장")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                androidx.compose.material3.Text("취소")
            }
        }
    )
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
            selected = selected,
            onClick = onClick,
            label = { Text(label) },
            leadingIcon = {
                if (selected) {
                    Icon(imageVector = Icons.Outlined.Check, contentDescription = null)
                }
            },
            shape = RoundedCornerShape(8.dp)
    )
}

private fun categoryLabel(categoryId: String): String = WardrobeUiUtil.categoryLabel(categoryId)

@Composable
private fun EmptyWardrobe() {
    Card(
            modifier = Modifier.fillMaxSize().padding(Spacing.lg).softClayInset(),
            colors = CardDefaults.cardColors(containerColor = FitGhostColors.BgPrimary),
            shape = RoundedCornerShape(24.dp)
    ) {
        Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
        ) {
            Icon(
                    imageVector = Icons.Outlined.Checkroom,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = FitGhostColors.TextTertiary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                    text = "아직 등록된 옷이 없어요",
                    style = MaterialTheme.typography.titleLarge,
                    color = FitGhostColors.TextSecondary,
                    fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                    text = "우측 상단 + 버튼으로 아이템을 추가해보세요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FitGhostColors.TextTertiary
            )
        }
    }
}

@Composable
private fun WardrobeItemRow(
        item: WardrobeItemEntity,
        onToggleFavorite: () -> Unit,
        onDelete: () -> Unit,
        onClick: () -> Unit,
        onFindSimilar: () -> Unit
) {
    Card(
            modifier = Modifier.fillMaxWidth().softClayInset(),
            colors = CardDefaults.cardColors(containerColor = FitGhostColors.BgSecondary),
            shape = RoundedCornerShape(Spacing.lg),
            onClick = onClick
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 썸네일 + 텍스트 영역 레이아웃 (왼쪽 이미지, 오른쪽 텍스트)
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 썸네일 박스
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(FitGhostColors.BgGlass),
                        contentAlignment = Alignment.Center
                    ) {
                        val uri = item.imageUri
                        if (!uri.isNullOrBlank()) {
                            AsyncImage(
                                model = uri,
                                contentDescription = "아이템 썸네일",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                placeholder = rememberVectorPainter(image = Icons.Outlined.Image),
                                error = rememberVectorPainter(image = Icons.Outlined.BrokenImage)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Checkroom,
                                contentDescription = null,
                                tint = FitGhostColors.TextTertiary
                            )
                        }
                    }

                    // 텍스트 정보
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                                text = item.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = FitGhostColors.TextPrimary,
                                fontWeight = FontWeight.Bold
                        )
                        val meta =
                                listOfNotNull(item.brand, item.color, item.size).filter { it.isNotBlank() }
                        if (meta.isNotEmpty()) {
                            Text(
                                    text = meta.joinToString(" • "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = FitGhostColors.TextSecondary
                            )
                        }
                    }
                }

                Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                                imageVector =
                                        if (item.favorite) Icons.Outlined.Favorite
                                        else Icons.Outlined.FavoriteBorder,
                                contentDescription = "즐겨찾기 토글"
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(imageVector = Icons.Outlined.DeleteOutline, contentDescription = "삭제")
                    }
                }
            }
            
            // 유사 상품 찾기 버튼
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onFindSimilar,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    modifier = Modifier.size(IconSize.md)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("유사 상품 찾기")
            }
        }
    }
}

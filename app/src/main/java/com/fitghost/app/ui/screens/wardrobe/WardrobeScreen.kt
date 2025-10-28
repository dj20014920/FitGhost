package com.fitghost.app.ui.screens.wardrobe

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.fitghost.app.data.db.WardrobeCategory
import com.fitghost.app.data.db.WardrobeItemEntity
import com.fitghost.app.ui.components.softClayInset
import com.fitghost.app.ui.theme.FitGhostColors
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter

/** 옷장 메인 화면 PRD: Wardrobe CRUD + 필터 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WardrobeScreen(onNavigateToAdd: () -> Unit = {}) {
    val context = LocalContext.current
    val viewModel: WardrobeViewModel = viewModel(factory = WardrobeViewModelFactory(context))
    val state by viewModel.uiState.collectAsState()

    val modelManager = remember { ModelManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    var modelState by remember { mutableStateOf(ModelManager.ModelState.NOT_READY) }
    var downloadProgress by remember { mutableStateOf<ModelManager.DownloadProgress?>(null) }
    var warmupTriggered by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val reconciled = modelManager.reconcileState()
        modelState = reconciled
        modelManager.observeModelState().collect { stateUpdate ->
            modelState = stateUpdate
        }
    }

    LaunchedEffect(modelState) {
        if (modelState == ModelManager.ModelState.READY && !warmupTriggered) {
            com.fitghost.app.ai.WardrobeAutoComplete(context).warmup()
            warmupTriggered = true
            downloadProgress = null
        }
        if (modelState != ModelManager.ModelState.DOWNLOADING && downloadProgress != null) {
            downloadProgress = null
        }
    }

    fun startModelDownload() {
        if (downloadProgress != null || modelState == ModelManager.ModelState.DOWNLOADING) return
        scope.launch {
            downloadProgress = ModelManager.DownloadProgress(0f, 1.5f, 0)
            val result = modelManager.downloadModel { progress ->
                downloadProgress = progress
            }
            result.onSuccess {
                downloadProgress = null
                warmupTriggered = false
                Toast.makeText(context, "AI 모델이 준비되었습니다!", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                downloadProgress = null
                Toast.makeText(
                        context,
                        "다운로드 실패: ${error.message ?: "알 수 없는 오류"}",
                        Toast.LENGTH_LONG
                )
                        .show()
            }
        }
    }

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
                    IconButton(onClick = onNavigateToAdd) {
                        Icon(imageVector = Icons.Outlined.Add, contentDescription = "아이템 추가")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = FitGhostColors.BgGlass)
        )

        // 온디바이스 모델 다운로드 배너
        ModelDownloadBanner(
                modelState = modelState,
                downloadProgress = downloadProgress,
                onDownload = { startModelDownload() }
        )

        // 필터 & 검색
        Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 카테고리 필터 칩
            Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryChip(
                        label = "전체",
                        selected = state.filter.category == null,
                        onClick = { viewModel.setCategory(null) }
                )
                WardrobeCategory.values().forEach { cat ->
                    CategoryChip(
                            label = categoryLabel(cat),
                            selected = state.filter.category == cat,
                            onClick = { viewModel.setCategory(cat) }
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
                            onClick = { /* 편집 네비게이션 연결용 콜백 확장 예정 */ }
                    )
                }
            }
        }
    }
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

private fun categoryLabel(cat: WardrobeCategory): String = WardrobeUiUtil.categoryLabel(cat)

@Composable
private fun EmptyWardrobe() {
    Card(
            modifier = Modifier.fillMaxSize().padding(16.dp).softClayInset(),
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
private fun ModelDownloadBanner(
        modelState: ModelManager.ModelState,
        downloadProgress: ModelManager.DownloadProgress?,
        onDownload: () -> Unit
) {
    val cardModifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)

    when {
        downloadProgress != null || modelState == ModelManager.ModelState.DOWNLOADING -> {
            Card(
                    modifier = cardModifier,
                    colors = CardDefaults.cardColors(containerColor = FitGhostColors.BgSecondary),
                    shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                            text = "AI 자동 완성 모델 준비 중",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = FitGhostColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    val progress = downloadProgress
                    if (progress != null) {
                        val downloadedText = String.format("%.1f", progress.downloadedMB)
                        val totalText =
                                if (progress.totalMB > 0) String.format("%.1f", progress.totalMB)
                                else null
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                    text = "${progress.percentage}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = FitGhostColors.AccentPrimary
                            )
                            Text(
                                    text =
                                    if (totalText != null) "$downloadedText MB / $totalText MB"
                                    else "$downloadedText MB 다운로드 중",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = FitGhostColors.TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                                progress = { progress.percentage / 100f },
                                modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                color = FitGhostColors.AccentPrimary
                        )
                    } else {
                        LinearProgressIndicator(
                                modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                color = FitGhostColors.AccentPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                            text = "다운로드가 끝나면 자동 완성을 바로 이용할 수 있습니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = FitGhostColors.TextSecondary
                    )
                }
            }
        }
        modelState == ModelManager.ModelState.READY -> {
            Card(
                    modifier = cardModifier,
                    colors = CardDefaults.cardColors(containerColor = FitGhostColors.BgSecondary),
                    shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = FitGhostColors.AccentPrimary
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                                text = "AI 자동 완성 준비 완료",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = FitGhostColors.TextPrimary
                        )
                        Text(
                                text = "사진을 추가하면 AI가 이름과 속성을 자동 채워줍니다.",
                                style = MaterialTheme.typography.bodySmall,
                                color = FitGhostColors.TextSecondary
                        )
                    }
                }
            }
        }
        else -> {
            Card(
                    modifier = cardModifier,
                    colors = CardDefaults.cardColors(containerColor = FitGhostColors.BgSecondary),
                    shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                            text = "AI 자동 완성 모델 다운로드",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = FitGhostColors.TextPrimary
                    )
                    Text(
                            text = "한 번만 받아두면 네트워크가 느린 환경에서도 빠르게 자동 완성을 사용할 수 있어요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = FitGhostColors.TextSecondary
                    )
                    FilledTonalButton(
                            onClick = onDownload,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = FitGhostColors.AccentPrimary.copy(alpha = 0.15f),
                                    contentColor = FitGhostColors.AccentPrimary
                            )
                    ) {
                        Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Outlined.CloudDownload, contentDescription = null)
                            Text(text = "AI 모델 준비하기")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WardrobeItemRow(
        item: WardrobeItemEntity,
        onToggleFavorite: () -> Unit,
        onDelete: () -> Unit,
        onClick: () -> Unit
) {
    Card(
            modifier = Modifier.fillMaxWidth().softClayInset(),
            colors = CardDefaults.cardColors(containerColor = FitGhostColors.BgSecondary),
            shape = RoundedCornerShape(16.dp),
            onClick = onClick
    ) {
        Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
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
    }
}

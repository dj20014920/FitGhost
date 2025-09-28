package com.fitghost.app.ui.screens.shop

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitghost.app.ui.components.softClay
import com.fitghost.app.ui.theme.FitGhostColors
import com.fitghost.app.data.repository.CartRepositoryProvider
import com.fitghost.app.data.model.FashionRecommendation
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopScreen(
        onNavigateToCart: () -> Unit = {},
        modifier: Modifier = Modifier,
        viewModel: ShopViewModel = viewModel(factory = ShopViewModelFactory())
) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val recommendations by viewModel.recommendations.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isSearchMode by viewModel.isSearchMode.collectAsStateWithLifecycle()
    val wishlist by viewModel.wishlistProducts.collectAsStateWithLifecycle()
    
    // AI 추천 상태
    val aiRecommendations by viewModel.aiRecommendations.collectAsStateWithLifecycle()
    val isAiLoading by viewModel.isAiLoading.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // UI 탭 상태: 검색/추천/AI추천/위시리스트
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("검색", "추천", "AI추천", "위시리스트")

    // 이벤트 구독 (스낵바)
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ShopViewModel.ShopUiEvent.Snackbar -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
            topBar = {
                TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                        text = "FitGhost Shop",
                                        fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                // Cart button with badge
                                IconButton(onClick = onNavigateToCart) {
                                    Icon(imageVector = Icons.Outlined.ShoppingCart, contentDescription = "장바구니")
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = FitGhostColors.BgGlass)
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(modifier = modifier.fillMaxSize().padding(padding)) {
            // Search Bar (검색 탭에서만 노출)
            if (selectedTab == 0) {
                SearchSection(
                        query = searchQuery,
                        onQueryChange = viewModel::updateSearchQuery,
                        modifier = Modifier.padding(16.dp)
                )
            }

            // 탭
            TabRow(selectedTabIndex = selectedTab, containerColor = FitGhostColors.BgSecondary) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            // 콘텐츠
            LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isLoading) {
                    item { LoadingSection() }
                } else {
                    when (selectedTab) {
                        0 -> {
                            // 검색 탭
                            if (isSearchMode) {
                                if (searchResults.isEmpty()) {
                                    item { EmptySearchResults(query = searchQuery) }
                                } else {
                                    items(searchResults) { product ->
                                        ProductCard(
                                                product = product,
                                                onAddToCart = { viewModel.addToCart(product) },
                                                onToggleWishlist = {
                                                    viewModel.toggleWishlist(product.id, product.isWishlisted)
                                                }
                                        )
                                    }
                                }
                            } else {
                                item {
                                    Text(
                                        text = "검색어를 입력해 주세요.",
                                        color = FitGhostColors.TextTertiary,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                        }

                        1 -> {
                            // 추천 탭
                            item { RecommendationHeader(onRefresh = viewModel::refreshRecommendations) }
                            items(recommendations) { recommendation ->
                                RecommendationCard(
                                        recommendation = recommendation,
                                        onAddToCart = { viewModel.addToCart(it) },
                                        onToggleWishlist = { product ->
                                            viewModel.toggleWishlist(product.id, product.isWishlisted)
                                        }
                                )
                            }
                        }

                        2 -> {
                            // AI 추천 탭
                            item { 
                                AIRecommendationHeader(
                                    onQuickRecommendation = { occasion ->
                                        viewModel.getQuickAIRecommendation(occasion)
                                    },
                                    onCustomRecommendation = { message ->
                                        viewModel.getAIFashionRecommendation(message)
                                    },
                                    isLoading = isAiLoading
                                )
                            }
                            
                            if (isAiLoading) {
                                item { 
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                            } else if (aiRecommendations.isEmpty()) {
                                item {
                                    Text(
                                        text = "AI 추천을 요청해보세요!",
                                        color = FitGhostColors.TextTertiary,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            } else {
                                items(aiRecommendations) { recommendation ->
                                    AIRecommendationCard(
                                        recommendation = recommendation,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            }
                        }

                        3 -> {
                            // 위시리스트 탭
                            if (wishlist.isEmpty()) {
                                item {
                                    Text(
                                        text = "위시리스트가 비어 있습니다.",
                                        color = FitGhostColors.TextTertiary,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            } else {
                                items(wishlist) { product ->
                                    ProductCard(
                                            product = product,
                                            onAddToCart = { viewModel.addToCart(product) },
                                            onToggleWishlist = {
                                                viewModel.toggleWishlist(product.id, product.isWishlisted)
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSection(
        query: String,
        onQueryChange: (String) -> Unit,
        modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("상품명, 태그로 검색") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) }
        )
    }
}

@Composable
private fun RecommendationHeader(onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    Row(
            modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "AI 추천", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onRefresh) {
            Icon(imageVector = Icons.Outlined.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "새로고침")
        }
    }
}

@Composable
private fun AIRecommendationHeader(
    onQuickRecommendation: (String) -> Unit,
    onCustomRecommendation: (String) -> Unit,
    isLoading: Boolean
) {
    var customMessage by remember { mutableStateOf("") }
    var showCustomInput by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = FitGhostColors.BgSecondary
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "AI 패션 추천",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = FitGhostColors.TextPrimary
                    )
                    Text(
                        text = "나노바나나 AI가 맞춤 스타일을 추천해드립니다",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FitGhostColors.TextSecondary
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = "AI",
                    tint = FitGhostColors.AccentPrimary
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 빠른 추천 버튼들
            Text(
                text = "빠른 추천",
                style = MaterialTheme.typography.labelMedium,
                color = FitGhostColors.TextSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val quickOptions = listOf("데이트", "출근", "운동", "여행")
                quickOptions.forEach { option ->
                    FilterChip(
                        onClick = { onQuickRecommendation(option) },
                        label = { Text(option) },
                        selected = false,
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 커스텀 메시지 입력
            if (showCustomInput) {
                OutlinedTextField(
                    value = customMessage,
                    onValueChange = { customMessage = it },
                    label = { Text("원하는 스타일을 설명해주세요") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    trailingIcon = {
                        Row {
                            IconButton(
                                onClick = { 
                                    if (customMessage.isNotBlank()) {
                                        onCustomRecommendation(customMessage)
                                        customMessage = ""
                                        showCustomInput = false
                                    }
                                },
                                enabled = !isLoading && customMessage.isNotBlank()
                            ) {
                                Icon(Icons.Outlined.Send, contentDescription = "전송")
                            }
                            IconButton(
                                onClick = { 
                                    showCustomInput = false
                                    customMessage = ""
                                }
                            ) {
                                Icon(Icons.Outlined.Close, contentDescription = "닫기")
                            }
                        }
                    }
                )
            } else {
                OutlinedButton(
                    onClick = { showCustomInput = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("맞춤 추천 요청하기")
                }
            }
        }
    }
}

@Composable
private fun AIRecommendationCard(
    recommendation: FashionRecommendation,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = FitGhostColors.BgSecondary
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 헤더
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = recommendation.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = FitGhostColors.TextPrimary
                    )
                    if (!recommendation.occasion.isNullOrBlank()) {
                        Text(
                            text = recommendation.occasion!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = FitGhostColors.TextSecondary
                        )
                    }
                }
                
                // 신뢰도 표시
                if (recommendation.confidence > 0) {
                    Surface(
                        color = when {
                            recommendation.confidence >= 0.8 -> FitGhostColors.Success.copy(alpha = 0.1f)
                            recommendation.confidence >= 0.6 -> FitGhostColors.Warning.copy(alpha = 0.1f)
                            else -> FitGhostColors.Error.copy(alpha = 0.1f)
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "${(recommendation.confidence * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                recommendation.confidence >= 0.8 -> FitGhostColors.Success
                                recommendation.confidence >= 0.6 -> FitGhostColors.Warning
                                else -> FitGhostColors.Error
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 설명
            Text(
                text = recommendation.description,
                style = MaterialTheme.typography.bodyMedium,
                color = FitGhostColors.TextPrimary
            )
            
            // 추천 아이템들
            if (recommendation.recommendedItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "추천 아이템",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = FitGhostColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                recommendation.recommendedItems.forEach { item ->
                    Row(
                        modifier = Modifier.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = FitGhostColors.AccentPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = FitGhostColors.TextPrimary
                        )
                    }
                }
            }
            
            // 스타일 태그들 (data.model.FashionRecommendation에는 컬렉션 없음)
            // 추천 아이템의 style/color를 태그로 보여주려면 필요한 경우 후속 작업으로 구현하세요.
            
            // 추론 과정 (접을 수 있는 형태)
            if (recommendation.reasoning.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                var showReasoning by remember { mutableStateOf(false) }
                
                TextButton(
                    onClick = { showReasoning = !showReasoning },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (showReasoning) "추론 과정 숨기기" else "추론 과정 보기",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (showReasoning) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                if (showReasoning) {
                    Surface(
                        color = FitGhostColors.BgPrimary,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = recommendation.reasoning,
                            style = MaterialTheme.typography.bodySmall,
                            color = FitGhostColors.TextSecondary,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

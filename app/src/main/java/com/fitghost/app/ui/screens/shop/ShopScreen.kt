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
import com.fitghost.app.data.model.Product
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.BitmapFactory
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopScreen(
        modifier: Modifier = Modifier,
        onNavigateToFitting: () -> Unit = {},
        viewModel: ShopViewModel = viewModel(factory = ShopViewModelFactory())
) {
    val context = LocalContext.current
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val recommendations by viewModel.recommendations.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isSearchMode by viewModel.isSearchMode.collectAsStateWithLifecycle()
    val wishlist by viewModel.wishlistProducts.collectAsStateWithLifecycle()
    
    // AI 추천 상태
    val aiRecommendations by viewModel.aiRecommendations.collectAsStateWithLifecycle()
    val isAiLoading by viewModel.isAiLoading.collectAsStateWithLifecycle()
    
    // 이미지 검색 상태
    val imageSearchResult by viewModel.imageSearchResult.collectAsStateWithLifecycle()
    val isImageSearching by viewModel.isImageSearching.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    // 이미지 선택 런처
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                viewModel.searchByImage(bitmap)
            } catch (e: Exception) {
                scope.launch {
                    snackbarHostState.showSnackbar("이미지를 불러올 수 없습니다")
                }
            }
        }
    }

    // UI 탭 상태: 검색/AI추천/위시리스트
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("검색", "AI추천", "위시리스트", "장바구니")

    // 장바구니 상태 (통합 탭용)
    val cartRepo = remember { CartRepositoryProvider.instance }
    val cartGroups by cartRepo.cartGroups.collectAsStateWithLifecycle(initialValue = emptyList())
    val cartTotalCount by cartRepo.totalItemCount.collectAsStateWithLifecycle(initialValue = 0)

    // 선택 결제 상태 (장바구니 탭)
    var selectedItemIds by remember { mutableStateOf(setOf<String>()) }

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
                                // Cart button -> 장바구니 탭으로 이동
                                IconButton(onClick = { selectedTab = 3 }) {
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
                        onImageSearchClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.padding(16.dp)
                )
                
                // 이미지 검색 결과 미리보기
                imageSearchResult?.let { result ->
                    ImageSearchPreview(
                        result = result,
                        onClear = viewModel::clearImageSearch,
                        onCategoryClick = { category ->
                            // 어울리는 아이템 클릭 시 해당 카테고리로 검색
                            viewModel.updateSearchQuery(category)
                        },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
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
                if (isLoading || isImageSearching) {
                    item { 
                        LoadingSection(
                            message = if (isImageSearching) "이미지를 분석하고 있어요..." else null
                        )
                    }
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
                                                    viewModel.toggleWishlist(product)
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
                            // AI 추천 탭 (통합)
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
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            CircularProgressIndicator()
                                            Text(
                                                text = "AI가 최적의 스타일을 찾고 있어요...",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = FitGhostColors.TextSecondary
                                            )
                                        }
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
                                        onAddToCart = { product -> viewModel.addToCart(product) },
                                        onToggleWishlist = { product -> viewModel.toggleWishlist(product) },
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            }
                            
                            // 구분선
                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                                Divider(
                                    color = FitGhostColors.BgTertiary,
                                    thickness = 1.dp,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            
                            // 기본 추천 (기존 추천 탭의 내용)
                            item { 
                                RecommendationHeader(
                                    onRefresh = viewModel::refreshRecommendations
                                ) 
                            }
                            items(recommendations) { recommendation ->
                                RecommendationCard(
                                        recommendation = recommendation,
                                        onAddToCart = { viewModel.addToCart(it) },
                                        onToggleWishlist = { product ->
                                            viewModel.toggleWishlist(product)
                                        }
                                )
                            }
                        }

                        2 -> {
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
                                                viewModel.toggleWishlist(product)
                                            }
                                    )
                                }
                            }
                        }
                        3 -> {
                            // 장바구니 탭 (CartScreen 통합 UI)
                            if (cartGroups.isEmpty()) {
                                item { com.fitghost.app.ui.screens.cart.EmptyCartContent() }
                            } else {
                                // 선택 툴바
                                item {
                                    val allIds = remember(cartGroups) {
                                        cartGroups.flatMap { it.items }.map { it.id }.toSet()
                                    }
                                    val allSelected = selectedItemIds.size == allIds.size && allIds.isNotEmpty()
                                    val selectedTotal = remember(selectedItemIds, cartGroups) {
                                        cartGroups.flatMap { it.items }
                                            .filter { selectedItemIds.contains(it.id) }
                                            .sumOf { it.productPrice * it.quantity }
                                    }
                                    SelectionToolbar(
                                        allSelected = allSelected,
                                        selectedCount = selectedItemIds.size,
                                        selectedTotal = selectedTotal,
                                        onToggleAll = { checked ->
                                            selectedItemIds = if (checked) allIds else emptySet()
                                        },
                                        onPaySelected = {
                                            val selectedShopUrls = cartGroups
                                                .filter { g -> g.items.any { selectedItemIds.contains(it.id) } }
                                                .map { it.shopUrl }
                                                .distinct()
                                            selectedShopUrls.forEach { url -> com.fitghost.app.util.Browser.open(context, url) }
                                        }
                                    )
                                }
                                // 요약 카드
                                item { com.fitghost.app.ui.screens.cart.CartSummaryCard(totalItems = cartTotalCount, totalGroups = cartGroups.size) }
                                // 몰별 그룹 카드
                                items(cartGroups) { group ->
                                    com.fitghost.app.ui.screens.cart.CartGroupCard(
                                        group = group,
                                        onUpdateQuantity = { itemId, quantity ->
                                            // repository로 직접 호출
                                            scope.launch { cartRepo.updateQuantity(itemId, quantity) }
                                        },
                                        onRemoveItem = { itemId ->
                                            scope.launch { cartRepo.removeFromCart(itemId) }
                                        },
                                        onClearShopCart = {
                                            scope.launch { cartRepo.clearShopCart(group.shopName) }
                                        },
                                        onNavigateToFitting = { imageUrl ->
                                            // FittingViewModel에 의상 이미지 URL 설정
                                            com.fitghost.app.ui.screens.fitting.FittingViewModel.getInstance()
                                                .setPendingClothingUrl(imageUrl)
                                            // 피팅 화면으로 이동
                                            onNavigateToFitting()
                                        },
                                        selectable = true,
                                        selectedItemIds = selectedItemIds,
                                        onToggleGroup = { check ->
                                            selectedItemIds = if (check) {
                                                selectedItemIds + group.items.map { it.id }
                                            } else {
                                                selectedItemIds - group.items.map { it.id }.toSet()
                                            }
                                        },
                                        onToggleItem = { id, check ->
                                            selectedItemIds = if (check) selectedItemIds + id else selectedItemIds - id
                                        }
                                    )
                                }
                                // 하단 결제 섹션
                                item {
                                    com.fitghost.app.ui.screens.cart.BottomPaymentSection(
                                        groups = cartGroups,
                                        onStartPayment = { groups ->
                                            groups.map { it.shopUrl }.forEach { url ->
                                                com.fitghost.app.util.Browser.open(context, url)
                                            }
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
private fun SelectionToolbar(
    allSelected: Boolean,
    selectedCount: Int,
    selectedTotal: Int,
    onToggleAll: (Boolean) -> Unit,
    onPaySelected: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = FitGhostColors.BgSecondary),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Checkbox(checked = allSelected, onCheckedChange = { onToggleAll(it) })
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "선택 ${selectedCount}개",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FitGhostColors.TextPrimary
                )
                val totalText = runCatching { NumberFormat.getCurrencyInstance(Locale.KOREA).format(selectedTotal) }
                    .getOrElse { "${selectedTotal}원" }
                Text(
                    text = totalText,
                    style = MaterialTheme.typography.titleMedium,
                    color = FitGhostColors.AccentPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            Button(
                onClick = onPaySelected,
                enabled = selectedCount > 0,
                shape = RoundedCornerShape(12.dp)
            ) { Text("선택 결제") }
        }
    }
}

@Composable
private fun SearchSection(
        query: String,
        onQueryChange: (String) -> Unit,
        onImageSearchClick: () -> Unit,
        modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("상품명, 태그로 검색") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) }
            )
            
            // 이미지 검색 버튼
            IconButton(
                onClick = onImageSearchClick,
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        color = FitGhostColors.AccentPrimary,
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Outlined.CameraAlt,
                    contentDescription = "사진으로 검색",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun ImageSearchPreview(
    result: com.fitghost.app.data.repository.ImageSearchResult,
    onClear: () -> Unit,
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = FitGhostColors.BgSecondary
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = null,
                        tint = FitGhostColors.AccentPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = result.sourceImage ?: "이미지 검색",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = FitGhostColors.TextPrimary
                    )
                }
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "검색 초기화",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            if (result.matchingCategories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "어울리는 아이템",
                    style = MaterialTheme.typography.labelSmall,
                    color = FitGhostColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    result.matchingCategories.take(4).forEach { category ->
                        Surface(
                            color = FitGhostColors.AccentPrimary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { 
                                onCategoryClick(category)
                            }
                        ) {
                            Text(
                                text = category,
                                style = MaterialTheme.typography.labelSmall,
                                color = FitGhostColors.AccentPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingSection(message: String? = null) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator()
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = FitGhostColors.TextSecondary
                )
            }
        }
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
                        text = "AI가 맞춤 스타일을 추천해드립니다",
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
    onAddToCart: (Product) -> Unit,
    onToggleWishlist: (Product) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = FitGhostColors.AccentPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = recommendation.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = FitGhostColors.TextPrimary
                        )
                    }
                    if (!recommendation.occasion.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
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
                            fontWeight = FontWeight.Bold,
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
            
            // 추천 아이템들 (AI 생성)
            if (recommendation.recommendedItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "AI 추천 아이템",
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
                        Column {
                            Text(
                                text = item.description,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = FitGhostColors.TextPrimary
                            )
                            if (item.color != null || item.style != null) {
                                Text(
                                    text = listOfNotNull(item.color, item.style).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = FitGhostColors.TextSecondary
                                )
                            }
                        }
                    }
                }
            }
            
            // 실제 상품 표시
            if (recommendation.products.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = FitGhostColors.BgTertiary)
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "추천 상품 ${recommendation.products.size}개",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = FitGhostColors.TextPrimary
                    )
                    Icon(
                        imageVector = Icons.Outlined.ShoppingBag,
                        contentDescription = null,
                        tint = FitGhostColors.AccentPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                
                // 상품 카드들
                recommendation.products.forEach { product ->
                    AIProductCard(
                        product = product,
                        onAddToCart = { onAddToCart(product) },
                        onToggleWishlist = { onToggleWishlist(product) },
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
            }
            
            // 추론 과정 (접을 수 있는 형태)
            if (recommendation.reasoning.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = FitGhostColors.BgTertiary)
                var showReasoning by remember { mutableStateOf(false) }
                
                TextButton(
                    onClick = { showReasoning = !showReasoning },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lightbulb,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = FitGhostColors.AccentPrimary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (showReasoning) "AI 추론 과정 숨기기" else "AI 추론 과정 보기",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.weight(1f))
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

@Composable
private fun AIProductCard(
    product: Product,
    onAddToCart: () -> Unit,
    onToggleWishlist: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = FitGhostColors.BgPrimary
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { 
                    // 상품 링크로 이동
                    try {
                        android.util.Log.d("AIProductCard", "Opening link: ${product.shopUrl}")
                        uriHandler.openUri(product.shopUrl)
                    } catch (e: Exception) {
                        android.util.Log.e("AIProductCard", "링크 열기 실패: ${product.shopUrl}", e)
                    }
                }
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 상품 이미지
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(FitGhostColors.BgTertiary),
                contentAlignment = Alignment.Center
            ) {
                if (product.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(product.imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = product.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Checkroom,
                        contentDescription = null,
                        tint = FitGhostColors.TextTertiary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            
            // 상품 정보
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = FitGhostColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${product.price.toString().replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "$1,")}원",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = FitGhostColors.AccentPrimary
                )
                Text(
                    text = product.shopName,
                    style = MaterialTheme.typography.bodySmall,
                    color = FitGhostColors.TextSecondary
                )
            }
            
            // 액션 버튼들
            Column(
                modifier = Modifier.clickable(
                    onClick = { }, // 클릭 이벤트 소비하여 상위로 전파 방지
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 찜하기 버튼 - 통일된 크기와 스타일
                IconButton(
                    onClick = { onToggleWishlist() },
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (product.isWishlisted) FitGhostColors.AccentPrimary.copy(alpha = 0.12f)
                            else FitGhostColors.BgTertiary,
                            RoundedCornerShape(10.dp)
                        )
                ) {
                    Icon(
                        imageVector = if (product.isWishlisted) Icons.Outlined.Favorite 
                                     else Icons.Outlined.FavoriteBorder,
                        contentDescription = "찜하기",
                        tint = if (product.isWishlisted) FitGhostColors.AccentPrimary
                              else FitGhostColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                // 장바구니 버튼 - 통일된 크기와 스타일
                IconButton(
                    onClick = { onAddToCart() },
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            FitGhostColors.AccentPrimary.copy(alpha = 0.12f),
                            RoundedCornerShape(10.dp)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ShoppingCart,
                        contentDescription = "장바구니",
                        tint = FitGhostColors.AccentPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

package com.fitghost.app.ui.screens

import android.os.Environment
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.rememberAsyncImagePainter
import com.fitghost.app.data.model.CartItem
import com.fitghost.app.ui.theme.NeumorphicButton
import com.fitghost.app.ui.theme.NeumorphicCard
import com.fitghost.app.ui.theme.NeumorphicOutlinedTextField
import com.fitghost.app.util.Browser
import com.fitghost.app.util.ServiceLocator
import java.io.File
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * DiscoverScreen
 *
 * 목적:
 * - 기존 하단 네비게이션의 "Shop / Cart / Gallery" 3개 기능을 단일 화면 내부 탭으로 통합하여
 * ```
 *    하단 탭 수를 줄이고(기능 통합) 탐색 비용/인지 부하를 감소.
 * ```
 * - Shop(검색/구매 후보) + Gallery(저장된 Try-On 결과) + Cart(장바구니) 세 섹션 전환.
 * - Cart는 전용 탭 + 전체 요약/그룹핑 시트(모달 바텀 시트) 제공.
 *
 * 설계 원칙:
 * - KISS/DRY/YAGNI: 최소 공통 UI/모델을 내장, 기존 화면 코드 중복 최소화.
 * - 서버리스 + 로컬 데이터: Cart/이미지 파일 로컬 접근 유지.
 * - 확장 포인트: 나중에 추천/매칭/필터칩/정렬 추가 시 내부 섹션 확장 용이.
 *
 * 구성:
 * - 내부 탭: Shop / Gallery / Cart
 * - Shop: 검색 → SearchRepository → 결과 or 샘플 폴백 → 장바구니 담기 / 외부 구매 / 찜(추후)
 * - Gallery: tryon 디렉토리 PNG 표시(Grid adaptive)
 * - Cart: 로컬 cart DB + "결제 진행" 시 매장(몰)별 그룹 목록 시트
 *
 * 주의:
 * - 실제 API 키 미설정 상태(검색 결과 없을 때 샘플).
 * - Gallery는 단순 파일명/이미지 뷰; 메타데이터 확장 시 DB 연동 고려.
 */
data class TabItem(val id: String, val label: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen() {
    val context = LocalContext.current
    val db = remember { ServiceLocator.db(context) }
    val cartDao = db.cartDao()
    val scope = rememberCoroutineScope()

    // -------------------- Tabs State --------------------
    // TabItem moved to top-level (see file header)
    val tabs =
            listOf(
                    TabItem("shop", "Shop", Icons.Filled.LocalMall),
                    TabItem("gallery", "Gallery", Icons.Filled.Image),
                    TabItem("cart", "Cart", Icons.Filled.ShoppingCart),
            )
    var selectedTabId by rememberSaveable { mutableStateOf("shop") }

    // -------------------- Cart State --------------------
    var cartItems by remember { mutableStateOf<List<CartItem>>(emptyList()) }
    LaunchedEffect(Unit) { cartDao.all().collectLatest { cartItems = it } }

    // -------------------- Gallery State --------------------
    var galleryImages by remember { mutableStateOf<List<File>>(emptyList()) }
    LaunchedEffect(selectedTabId) {
        if (selectedTabId == "gallery") {
            withContext(Dispatchers.IO) {
                val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "tryon")
                val imgs =
                        dir
                                .takeIf { it.exists() }
                                ?.listFiles { f ->
                                    f.isFile &&
                                            (f.name.endsWith(".png") ||
                                                    f.name.endsWith(".jpg") ||
                                                    f.name.endsWith(".webp"))
                                }
                                ?.sortedByDescending { it.lastModified() }
                                ?: emptyList()
                galleryImages = imgs
            }
        }
    }

    // -------------------- Shop State --------------------
    val searchRepo = remember { ServiceLocator.searchRepo(context) }
    var shopQuery by rememberSaveable { mutableStateOf("") }
    var shopResults by remember { mutableStateOf<List<ShopUiItem>>(emptyList()) }
    var shopLoading by remember { mutableStateOf(false) }
    var shopMessage by remember { mutableStateOf("") }

    // -------------------- Cart Summary Sheet --------------------
    var showCartSheet by remember { mutableStateOf(false) }
    val mallGrouped = remember(cartItems) { cartItems.groupBy { it.mall.ifBlank { "UNKNOWN" } } }

    // -------------------- Layout --------------------
    Scaffold(
            topBar = {
                DiscoverTopBar(
                        tabs = tabs,
                        selectedId = selectedTabId,
                        cartSize = cartItems.size,
                        gallerySize = galleryImages.size,
                        onSelect = { selectedTabId = it }
                )
            },
            floatingActionButton = {
                AnimatedVisibility(
                        visible = selectedTabId == "cart" && cartItems.isNotEmpty(),
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                ) {
                    ExtendedFloatingActionButton(
                            onClick = { showCartSheet = true },
                            icon = { Icon(Icons.Filled.ShoppingCart, contentDescription = "결제") },
                            text = { Text("결제(${mallGrouped.size}몰 / ${cartItems.size}개)") }
                    )
                }
            }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selectedTabId) {
                "shop" ->
                        ShopSection(
                                query = shopQuery,
                                onQueryChange = { shopQuery = it },
                                loading = shopLoading,
                                message = shopMessage,
                                items = shopResults,
                                onSearch = {
                                    scope.launch {
                                        shopLoading = true
                                        shopMessage = ""
                                        val r =
                                                searchRepo.search(
                                                        shopQuery.ifBlank { "" },
                                                        limit = 30
                                                )
                                        shopResults =
                                                if (r.isNotEmpty()) {
                                                    r.map {
                                                        ShopUiItem(
                                                                title = it.title,
                                                                price = it.price,
                                                                image = it.imageUrl,
                                                                mall = it.mallName ?: "",
                                                                link = it.link
                                                        )
                                                    }
                                                } else {
                                                    sampleShopItems().filter {
                                                        it.title.contains(shopQuery, true) ||
                                                                shopQuery.isBlank()
                                                    }
                                                }
                                        if (shopResults.isEmpty()) shopMessage = "검색 결과 없음"
                                        shopLoading = false
                                    }
                                },
                                onAddCart = { item ->
                                    scope.launch {
                                        val price = item.price ?: 0.0
                                        cartDao.upsert(
                                                CartItem(
                                                        title = item.title,
                                                        price = price,
                                                        image = item.image,
                                                        mall = item.mall,
                                                        link = item.link
                                                )
                                        )
                                    }
                                },
                                onBuyExternal = { Browser.open(context, it.link) }
                        )
                "gallery" -> GallerySection(files = galleryImages)
                "cart" ->
                        CartSection(
                                items = cartItems,
                                onDelete = { ci -> scope.launch { cartDao.delete(ci) } },
                                onOpen = { Browser.open(context, it.link) }
                        )
            }
        }
    }

    if (showCartSheet) {
        ModalBottomSheet(
                onDismissRequest = { showCartSheet = false },
                dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            CartSummarySheet(
                    grouped = mallGrouped,
                    onOpenMallSequential = {
                        // 순차 오픈 로직(간단 버전)
                        scope.launch {
                            mallGrouped.values.flatten().forEach { Browser.open(context, it.link) }
                        }
                    }
            )
        }
    }
}

/* -------------------- Data Model (UI) -------------------- */
private data class ShopUiItem(
        val title: String,
        val price: Double?,
        val image: String?,
        val mall: String,
        val link: String
)

/* -------------------- Top Bar with Tabs -------------------- */
@Composable
private fun DiscoverTopBar(
        tabs: List<TabItem>,
        selectedId: String,
        cartSize: Int,
        gallerySize: Int,
        onSelect: (String) -> Unit
) {
    Surface(shadowElevation = 4.dp, tonalElevation = 2.dp) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                    "Discover",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
            )
            ScrollableTabRow(
                    selectedTabIndex = tabs.indexOfFirst { it.id == selectedId }.coerceAtLeast(0),
                    edgePadding = 12.dp
            ) {
                tabs.forEach { t ->
                    val label =
                            when (t.id) {
                                "shop" -> "Shop"
                                "gallery" ->
                                        "Gallery${if (gallerySize > 0) " ($gallerySize)" else ""}"
                                "cart" -> "Cart${if (cartSize > 0) " ($cartSize)" else ""}"
                                else -> t.label
                            }
                    Tab(
                            selected = t.id == selectedId,
                            onClick = { onSelect(t.id) },
                            text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }
        }
    }
}

/* -------------------- Shop Section -------------------- */
@Composable
private fun ShopSection(
        query: String,
        onQueryChange: (String) -> Unit,
        loading: Boolean,
        message: String,
        items: List<ShopUiItem>,
        onSearch: () -> Unit,
        onAddCart: (ShopUiItem) -> Unit,
        onBuyExternal: (ShopUiItem) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row {
            NeumorphicOutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    label = { Text("검색어") },
                    modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            NeumorphicButton(onClick = onSearch) { Text("검색") }
        }
        Spacer(Modifier.height(12.dp))
        AnimatedVisibility(visible = loading, enter = fadeIn(), exit = fadeOut()) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        AnimatedVisibility(
                visible = message.isNotEmpty() && !loading,
                enter = fadeIn(),
                exit = fadeOut()
        ) {
            NeumorphicCard {
                Text(
                        message,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 150.dp)) {
            items(items) { item ->
                NeumorphicCard(Modifier.padding(6.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Image(
                                painter = rememberAsyncImagePainter(item.image),
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .height(120.dp)
                                                .background(Color(0xFFEFEFEF))
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                                item.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                        )
                        val priceFormatted =
                                item.price?.let {
                                    NumberFormat.getCurrencyInstance(Locale.KOREA).format(it)
                                }
                                        ?: "N/A"
                        Text(priceFormatted, style = MaterialTheme.typography.bodyMedium)
                        Text(
                                item.mall.ifBlank { "—" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(6.dp))
                        Row {
                            NeumorphicButton(
                                    onClick = { onAddCart(item) },
                                    modifier = Modifier.weight(1f)
                            ) { Text("장바구니") }
                            Spacer(Modifier.width(6.dp))
                            NeumorphicButton(
                                    onClick = { onBuyExternal(item) },
                                    modifier = Modifier.weight(1f)
                            ) { Text("구매") }
                        }
                    }
                }
            }
        }
    }
}

/* -------------------- Gallery Section -------------------- */
@Composable
private fun GallerySection(files: List<File>) {
    if (files.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            NeumorphicCard {
                Column(
                        Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                            Icons.Filled.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("저장된 Try-On 결과가 없습니다.", style = MaterialTheme.typography.bodyMedium)
                    Text("Try-On에서 프리뷰를 생성해보세요.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        return
    }
    LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            modifier = Modifier.fillMaxSize().padding(8.dp)
    ) {
        items(files) { f ->
            NeumorphicCard(Modifier.padding(6.dp)) {
                Column(Modifier.padding(4.dp)) {
                    Image(
                            painter = rememberAsyncImagePainter(f),
                            contentDescription = f.name,
                            modifier = Modifier.fillMaxWidth().height(140.dp),
                            contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                            f.name,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/* -------------------- Cart Section -------------------- */
@Composable
private fun CartSection(
        items: List<CartItem>,
        onDelete: (CartItem) -> Unit,
        onOpen: (CartItem) -> Unit
) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            NeumorphicCard {
                Column(
                        Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                            Icons.Filled.ShoppingCart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("장바구니가 비어 있습니다.", style = MaterialTheme.typography.bodyMedium)
                    Text("상품을 담아 보세요.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(items, key = { it.id }) { ci ->
            NeumorphicCard(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f).clickable { onOpen(ci) }) {
                        Text(
                                ci.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                        )
                        Text(
                                NumberFormat.getCurrencyInstance(Locale.KOREA).format(ci.price),
                                style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                                ci.mall,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                        )
                        if (!ci.image.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                        Icons.Filled.Tag,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("이미지 포함", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    IconButton(onClick = { onDelete(ci) }) {
                        Icon(
                                Icons.Filled.Delete,
                                contentDescription = "삭제",
                                tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(90.dp)) }
    }
}

/* -------------------- Cart Summary Sheet -------------------- */
@Composable
private fun CartSummarySheet(
        grouped: Map<String, List<CartItem>>,
        onOpenMallSequential: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("결제 요약", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        grouped.entries.forEach { (mall, list) ->
            NeumorphicCard(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(mall, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    list.forEach { ci ->
                        Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                    ci.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                            )
                            Text(
                                    NumberFormat.getCurrencyInstance(Locale.KOREA).format(ci.price),
                                    style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    val subtotal = list.sumOf { it.price }
                    Spacer(Modifier.height(4.dp))
                    Text(
                            "소계: ${
                            NumberFormat.getCurrencyInstance(Locale.KOREA)
                                .format(subtotal)
                        }",
                            style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
        val total = grouped.values.flatten().sumOf { it.price }
        Text(
                "총 결제 예정: ${
                NumberFormat.getCurrencyInstance(Locale.KOREA).format(total)
            }",
                style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(16.dp))
        NeumorphicButton(onClick = onOpenMallSequential, modifier = Modifier.fillMaxWidth()) {
            Text("모든 몰 순차 열기")
        }
        Spacer(Modifier.height(12.dp))
    }
}

/* -------------------- Sample Fallback Data -------------------- */
private fun sampleShopItems(): List<ShopUiItem> =
        listOf(
                ShopUiItem(
                        title = "화이트 슬림핏 셔츠",
                        price = 39900.0,
                        image = "https://picsum.photos/seed/shirt/300/200",
                        mall = "무신사",
                        link = "https://www.musinsa.com"
                ),
                ShopUiItem(
                        title = "블랙 데님 진",
                        price = 49000.0,
                        image = "https://picsum.photos/seed/jeans/300/200",
                        mall = "지그재그",
                        link = "https://www.zigzag.kr"
                ),
                ShopUiItem(
                        title = "라이트 윈드브레이커",
                        price = 89000.0,
                        image = "https://picsum.photos/seed/jacket/300/200",
                        mall = "네이버",
                        link = "https://shopping.naver.com"
                )
        )

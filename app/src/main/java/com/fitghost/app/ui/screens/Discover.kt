package com.fitghost.app.ui.screens

import android.content.Intent
import android.os.Environment
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.fitghost.app.data.model.CartItem
import com.fitghost.app.ui.theme.*
import com.fitghost.app.ui.components.NeumorphicSegmentedControl
import com.fitghost.app.util.Browser
import com.fitghost.app.util.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

data class TabItem(val id: String, val label: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen() {
    val context = LocalContext.current
    val db = remember { ServiceLocator.db(context) }
    val cartDao = db.cartDao()
    val scope = rememberCoroutineScope()

    val tabs = listOf(
        TabItem("shop", "Shop", Icons.Filled.LocalMall),
        TabItem("gallery", "Gallery", Icons.Filled.Image),
        TabItem("cart", "Cart", Icons.Filled.ShoppingCart),
    )
    var selectedTabId by rememberSaveable { mutableStateOf("shop") }

    var cartItems by remember { mutableStateOf<List<CartItem>>(emptyList()) }
    LaunchedEffect(Unit) { cartDao.all().collectLatest { cartItems = it } }

    var galleryImages by remember { mutableStateOf<List<File>>(emptyList()) }
    fun refreshGallery() {
        scope.launch(Dispatchers.IO) {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "tryon")
            val imgs = dir.takeIf { it.exists() }?.listFiles { f ->
                f.isFile && (f.name.endsWith(".png") || f.name.endsWith(".jpg") || f.name.endsWith(".webp"))
            }?.sortedByDescending { it.lastModified() } ?: emptyList()
            galleryImages = imgs
        }
    }
    LaunchedEffect(selectedTabId) {
        if (selectedTabId == "gallery") {
            refreshGallery()
        }
    }

    val searchRepo = remember { ServiceLocator.searchRepo(context) }
    var shopQuery by rememberSaveable { mutableStateOf("") }
    var shopResults by remember { mutableStateOf<List<ShopUiItem>>(emptyList()) }
    var shopLoading by remember { mutableStateOf(false) }
    var shopMessage by remember { mutableStateOf("") }

    var showCartSheet by remember { mutableStateOf(false) }
    val mallGrouped = remember(cartItems) { cartItems.groupBy { it.mall.ifBlank { "UNKNOWN" } } }

    Scaffold(
        topBar = {
            DiscoverTopBar(
                tabs = tabs,
                selectedId = selectedTabId,
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
                                val r = searchRepo.search(shopQuery.ifBlank { "" }, limit = 30)
                                shopResults = if (r.isNotEmpty()) {
                                    r.map {
                                        ShopUiItem(it.title, it.price, it.imageUrl, it.mallName ?: "", it.link)
                                    }
                                } else {
                                    sampleShopItems().filter { it.title.contains(shopQuery, true) || shopQuery.isBlank() }
                                }
                                if (shopResults.isEmpty()) shopMessage = "검색 결과 없음"
                                shopLoading = false
                            }
                        },
                        onAddCart = { item ->
                            scope.launch {
                                val price = item.price ?: 0.0
                                cartDao.upsert(CartItem(title = item.title, price = price, image = item.image, mall = item.mall, link = item.link))
                            }
                        },
                        onBuyExternal = { Browser.open(context, it.link) }
                    )
                "gallery" -> GallerySection(files = galleryImages, onRefresh = { refreshGallery() })
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
                    scope.launch {
                        mallGrouped.values.flatten().forEach { Browser.open(context, it.link) }
                    }
                }
            )
        }
    }
}

private data class ShopUiItem(val title: String, val price: Double?, val image: String?, val mall: String, val link: String)

@Composable
private fun DiscoverTopBar(
    tabs: List<TabItem>,
    selectedId: String,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Discover",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(Modifier.height(16.dp))
        NeumorphicSegmentedControl(
            options = tabs.map { it.label },
            selectedIndex = tabs.indexOfFirst { it.id == selectedId }.coerceAtLeast(0),
            onSelect = { index -> onSelect(tabs[index].id) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

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
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        NeumorphicOutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("상품 검색") },
            trailingIcon = {
                NeumorphicIconButton(onClick = onSearch) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                NeumorphicCircularProgress()
            }
        } else if (message.isNotEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(message, style = MaterialTheme.typography.titleLarge)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(items) { item ->
                    NeumorphicCard(modifier = Modifier.clickable { onBuyExternal(item) }) {
                        Column {
                            AsyncImage(
                                model = item.image,
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                            )
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                val priceFormatted = item.price?.let { NumberFormat.getCurrencyInstance(Locale.KOREA).format(it) } ?: "가격 문의"
                                Text(priceFormatted, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    NeumorphicIconButton(onClick = { /* TODO: Wishlist */ }) {
                                        Icon(Icons.Default.FavoriteBorder, contentDescription = "Wishlist")
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    NeumorphicIconButton(onClick = { onAddCart(item) }) {
                                        Icon(Icons.Default.AddShoppingCart, contentDescription = "Add to Cart")
                                    }
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
private fun GallerySection(files: List<File>, onRefresh: () -> Unit) {
    val context = LocalContext.current
    if (files.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("저장된 결과가 없습니다.", style = MaterialTheme.typography.titleLarge)
            }
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(files) { file ->
            NeumorphicCard(modifier = Modifier.clickable { /* TODO: Detail View */ }) {
                Column {
                    AsyncImage(
                        model = file,
                        contentDescription = file.name,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        contentScale = ContentScale.Crop
                    )
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date(file.lastModified())),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row {
                            NeumorphicIconButton(onClick = {
                                file.delete()
                                onRefresh()
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                            NeumorphicIconButton(onClick = {
                                val uri = FileProvider.getUriForFile(context, "com.fitghost.app.fileprovider", file)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    setDataAndType(uri, "image/jpeg")
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Image"))
                            }) {
                                Icon(Icons.Default.Share, contentDescription = "Share")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CartSection(
    items: List<CartItem>,
    onDelete: (CartItem) -> Unit,
    onOpen: (CartItem) -> Unit
) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("장바구니가 비어 있습니다.", style = MaterialTheme.typography.titleLarge)
            }
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(items, key = { it.id }) { item ->
            NeumorphicCard {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(item) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AsyncImage(
                        model = item.image,
                        contentDescription = item.title,
                        modifier = Modifier.size(80.dp).clip(RoundedCornerShape(12.dp))
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        Text(NumberFormat.getCurrencyInstance(Locale.KOREA).format(item.price), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    }
                    NeumorphicIconButton(onClick = { onDelete(item) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            }
        }
    }
}

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
                            NumberFormat.getCurrencyInstance(Locale.KOREA).format(subtotal)
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
package com.fitghost.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.fitghost.app.util.Browser
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.Button
import com.fitghost.app.ui.theme.NeumorphicButton
import com.fitghost.app.ui.theme.NeumorphicCard
import com.fitghost.app.ui.theme.NeumorphicOutlinedTextField

private data class ShopItem(val title: String, val price: String, val image: String, val mall: String, val link: String)

@Composable
fun ShopScreen(onCartClick: () -> Unit) {
    var q by remember { mutableStateOf("") }
    var items by remember { mutableStateOf(sampleItems()) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cartDao = remember { com.fitghost.app.util.ServiceLocator.db(context).cartDao() }
    val searchRepo = remember { com.fitghost.app.util.ServiceLocator.searchRepo(context) }

    Column(Modifier.padding(16.dp)) {
        Row {
            NeumorphicOutlinedTextField(
                value = q,
                onValueChange = { q = it },
                label = { Text("검색어") },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            NeumorphicButton(
                onClick = {
                    scope.launch {
                        val results = searchRepo.search(q, limit = 20)
                        items = if (results.isNotEmpty()) {
                            results.map {
                                ShopItem(
                                    title = it.title,
                                    price = it.price?.let { p -> "₩" + String.format("%,.0f", p) } ?: "가격 정보 없음",
                                    image = it.imageUrl ?: "https://picsum.photos/seed/fallback/300/200",
                                    mall = it.mallName ?: "",
                                    link = it.link
                                )
                            }
                        } else {
                            sampleItems().filter { it.title.contains(q, true) }
                        }
                    }
                }
            ) {
                Text("검색")
            }
            Spacer(Modifier.width(8.dp))
            NeumorphicButton(onClick = onCartClick) { Text("장바구니") }
        }
        Spacer(Modifier.height(12.dp))

        AnimatedVisibility(
            visible = items.isEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            NeumorphicCard {
                Column(Modifier.padding(16.dp)) {
                    Text("검색 결과가 없습니다", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    Text("다른 키워드로 검색해 보세요", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            content = {
                items(items) { it ->
                    NeumorphicCard(Modifier.padding(6.dp)) {
                        Column(Modifier.padding(8.dp)) {
                            Image(
                                painter = rememberAsyncImagePainter(it.image),
                                contentDescription = it.title,
                                modifier = Modifier
                                    .height(120.dp)
                                    .fillMaxWidth()
                            )
                            Text(it.title, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                            Text(it.price, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                            Row {
                                NeumorphicButton(
                                    onClick = { /* 찜 */ },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("찜")
                                }
                                Spacer(Modifier.width(6.dp))
                                NeumorphicButton(
                                    onClick = {
                                        scope.launch {
                                            val price = it.price.replace("[^\\d.]".toRegex(), "").replace(",", "").toDoubleOrNull() ?: 0.0
                                            cartDao.upsert(com.fitghost.app.data.model.CartItem(
                                                title = it.title,
                                                price = price,
                                                image = it.image,
                                                mall = it.mall,
                                                link = it.link
                                            ))
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("장바구니")
                                }
                                Spacer(Modifier.width(6.dp))
                                NeumorphicButton(
                                    onClick = { Browser.open(context, it.link) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("구매")
                                }
                            }
                        }
                    }
                }
            }
        )
    }
}

private fun sampleItems() = listOf(
    ShopItem("화이트 셔츠 슬림핏", "₩39,900", "https://picsum.photos/seed/shirt/300/200", "무신사", "https://www.musinsa.com"),
    ShopItem("블랙 진", "₩49,000", "https://picsum.photos/seed/jeans/300/200", "지그재그", "https://www.zigzag.kr"),
    ShopItem("라이트 재킷", "₩89,000", "https://picsum.photos/seed/jacket/300/200", "네이버", "https://shopping.naver.com")
)

package com.fitghost.app.ui.screens.cart

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitghost.app.ui.components.softClay
import com.fitghost.app.ui.components.softClayInset
import com.fitghost.app.ui.theme.FitGhostColors

/** 장바구니 화면 PRD: 몰별 그룹 + 순차 결제, 홈 화면과 유사한 UI/UX */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(
        onNavigateBack: () -> Unit = {},
        modifier: Modifier = Modifier,
        viewModel: CartViewModel = viewModel(factory = CartViewModelFactory())
) {
    val cartGroups by viewModel.cartGroups.collectAsStateWithLifecycle()
    val totalItemCount by viewModel.totalItemCount.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxSize().background(FitGhostColors.BgPrimary)) {
        // Header
        TopAppBar(
                title = {
                    Text(
                            text = "장바구니",
                            style = MaterialTheme.typography.headlineLarge,
                            color = FitGhostColors.TextPrimary,
                            fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    if (totalItemCount > 0) {
                        TextButton(
                                onClick = { viewModel.clearAllCart() },
                                colors =
                                        ButtonDefaults.textButtonColors(
                                                contentColor = FitGhostColors.TextSecondary
                                        )
                        ) { Text(text = "전체 삭제", style = MaterialTheme.typography.bodyMedium) }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Outlined.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = FitGhostColors.BgGlass)
        )

        // Content
        if (cartGroups.isEmpty()) {
            // 빈 장바구니
            EmptyCartContent()
        } else {
            // 장바구니 내용
            LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 요약 정보
                item { CartSummaryCard(totalItems = totalItemCount, totalGroups = cartGroups.size) }

                // 몰별 그룹들
                items(cartGroups) { group ->
                    CartGroupCard(
                            group = group,
                            onUpdateQuantity = viewModel::updateQuantity,
                            onRemoveItem = viewModel::removeItem,
                            onClearShopCart = { viewModel.clearShopCart(group.shopName) }
                    )
                }
            }

            // 하단 결제 버튼
            if (cartGroups.isNotEmpty()) {
                BottomPaymentSection(
                        groups = cartGroups,
                        onStartPayment = { groups ->
                            val urls = viewModel.startSequentialPayment(groups)
                            urls.forEach { url -> com.fitghost.app.util.Browser.open(context, url) }
                        }
                )
            }
        }
    }
}

/** 빈 장바구니 화면 */
@Composable
private fun EmptyCartContent() {
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
                    imageVector = Icons.Outlined.ShoppingCart,
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                    tint = FitGhostColors.TextTertiary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                    text = "장바구니가 비어있습니다",
                    style = MaterialTheme.typography.titleLarge,
                    color = FitGhostColors.TextSecondary,
                    fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                    text = "상점에서 마음에 드는 상품을 담아보세요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FitGhostColors.TextTertiary
            )
        }
    }
}

/** 장바구니 요약 카드 */
@Composable
private fun CartSummaryCard(totalItems: Int, totalGroups: Int, modifier: Modifier = Modifier) {
    Card(
            modifier = modifier.fillMaxWidth().softClay(),
            colors = CardDefaults.cardColors(containerColor = FitGhostColors.BgSecondary),
            shape = RoundedCornerShape(20.dp)
    ) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                        text = "🛒 장바구니 요약",
                        style = MaterialTheme.typography.headlineMedium,
                        color = FitGhostColors.TextPrimary,
                        fontWeight = FontWeight.Bold
                )
                Text(
                        text = "총 ${totalItems}개 상품 • ${totalGroups}개 쇼핑몰",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FitGhostColors.TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                )
            }

            Box(
                    modifier =
                            Modifier.size(48.dp)
                                    .background(
                                            FitGhostColors.AccentPrimary.copy(alpha = 0.1f),
                                            RoundedCornerShape(12.dp)
                                    ),
                    contentAlignment = Alignment.Center
            ) {
                Text(
                        text = totalItems.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        color = FitGhostColors.AccentPrimary,
                        fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

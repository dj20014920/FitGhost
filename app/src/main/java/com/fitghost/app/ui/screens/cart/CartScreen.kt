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
import com.fitghost.app.ui.components.TertiaryButton
import com.fitghost.app.ui.theme.FitGhostColors
import com.fitghost.app.ui.theme.Spacing

/** 장바구니 화면 PRD: 몰별 그룹 + 순차 결제, 홈 화면과 유사한 UI/UX */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(
        onNavigateBack: () -> Unit = {},
        onNavigateToFitting: (String) -> Unit = {},
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
                            color = FitGhostColors.TextPrimary
                    )
                },
                actions = {
                    if (totalItemCount > 0) {
                        TertiaryButton(
                                text = "전체 삭제",
                                onClick = { viewModel.clearAllCart() }
                        )
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
                    contentPadding = PaddingValues(Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xl)
            ) {
                // 요약 정보
                item { CartSummaryCard(totalItems = totalItemCount, totalGroups = cartGroups.size) }

                // 몰별 그룹들
                items(cartGroups) { group ->
                    CartGroupCard(
                            group = group,
                            onUpdateQuantity = viewModel::updateQuantity,
                            onRemoveItem = viewModel::removeItem,
                            onClearShopCart = { viewModel.clearShopCart(group.shopName) },
                            onNavigateToFitting = onNavigateToFitting
                    )
                }
                
                // 하단 여유 공간
                item { Spacer(modifier = Modifier.height(100.dp)) }
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

// 요약/Empty 컴포넌트는 공용 CartComponents.kt로 이동

package com.fitghost.app.ui.screens.cart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitghost.app.data.model.CartGroup
import com.fitghost.app.data.model.CartItem
import com.fitghost.app.ui.components.SoftClayButton
import com.fitghost.app.ui.components.softClay
import com.fitghost.app.ui.theme.FitGhostColors
import com.fitghost.app.ui.theme.Spacing
import com.fitghost.app.ui.theme.IconSize
import com.fitghost.app.ui.theme.CornerRadius

/**
 * 몰별 장바구니 그룹 카드 - 개선된 UI/UX
 * PRD: 몰별 그룹핑 + 순차 결제 지원
 */
@Composable
fun CartGroupCard(
    group: CartGroup,
    onUpdateQuantity: (String, Int) -> Unit,
    onRemoveItem: (String) -> Unit,
    onClearShopCart: () -> Unit,
    onNavigateToFitting: (String) -> Unit = {},
    // 선택 기능 (선택 결제)
    selectable: Boolean = false,
    selectedItemIds: Set<String> = emptySet(),
    onToggleGroup: ((Boolean) -> Unit)? = null,
    onToggleItem: ((String, Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .softClay(),
        colors = CardDefaults.cardColors(
            containerColor = FitGhostColors.BgSecondary
        ),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 쇼핑몰 헤더 - 깔끔하게 이름만 표시
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = group.shopName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = FitGhostColors.TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${group.items.size}개 상품 • ${group.totalPrice.toKrw()}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = FitGhostColors.TextSecondary
                    )
                }
                
                // 쇼핑몰 장바구니 삭제 버튼
                IconButton(
                    onClick = onClearShopCart,
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            FitGhostColors.BgTertiary,
                            RoundedCornerShape(CornerRadius.md)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "몰 장바구니 비우기",
                        tint = FitGhostColors.TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            
            // 상품 목록
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                group.items.forEach { item ->
                    CartItemCard(
                        item = item,
                        onUpdateQuantity = { quantity -> onUpdateQuantity(item.id, quantity) },
                        onRemove = { onRemoveItem(item.id) },
                        onNavigateToFitting = onNavigateToFitting,
                        selectable = selectable,
                        selected = selectedItemIds.contains(item.id),
                        onToggleSelected = { checked -> onToggleItem?.invoke(item.id, checked) }
                    )
                }
            }
            
            // 이 몰에서 결제하기 버튼
            val ctx = androidx.compose.ui.platform.LocalContext.current
            SoftClayButton(
                onClick = { 
                    com.fitghost.app.util.Browser.open(ctx, group.shopUrl)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                text = "${group.shopName}에서 결제하기 (${group.totalPrice.toKrw()})",
                shape = RoundedCornerShape(Spacing.lg)
            )
        }
    }
}

/**
 * 장바구니 아이템 카드 - 세로 레이아웃으로 깔끔하게 재구성
 */
@Composable
private fun CartItemCard(
    item: CartItem,
    onUpdateQuantity: (Int) -> Unit,
    onRemove: () -> Unit,
    onNavigateToFitting: (String) -> Unit,
    selectable: Boolean,
    selected: Boolean,
    onToggleSelected: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = FitGhostColors.BgPrimary
        ),
        shape = RoundedCornerShape(Spacing.lg.times(1.25f))
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg.times(1.25f)),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 상단: 선택 체크박스와 삭제 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectable) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onToggleSelected(it) }
                    )
                } else {
                    Spacer(modifier = Modifier.width(8.dp))
                }
                
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            FitGhostColors.BgTertiary,
                            RoundedCornerShape(10.dp)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "삭제",
                        tint = FitGhostColors.TextSecondary,
                        modifier = Modifier.size(IconSize.md)
                    )
                }
            }
            
            // 상품 이미지 - 중앙 정렬
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                coil.compose.AsyncImage(
                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(item.productImageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.productName,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(Spacing.lg))
                        .background(FitGhostColors.BgTertiary),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    error = androidx.compose.ui.graphics.painter.ColorPainter(FitGhostColors.BgTertiary),
                    placeholder = androidx.compose.ui.graphics.painter.ColorPainter(FitGhostColors.BgTertiary)
                )
            }
            
            // 상품 이름
            Text(
                text = item.productName,
                style = MaterialTheme.typography.bodyLarge,
                color = FitGhostColors.TextPrimary,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            
            // 가격과 수량
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.productPrice.toKrw(),
                    style = MaterialTheme.typography.titleLarge,
                    color = FitGhostColors.AccentPrimary,
                    fontWeight = FontWeight.Bold
                )
                
                if (item.quantity > 1) {
                    Text(
                        text = "× ${item.quantity}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = FitGhostColors.TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            // 가상피팅 버튼
            Button(
                onClick = {
                    // FittingViewModel에 의상 이미지 URL 설정
                    com.fitghost.app.ui.screens.fitting.FittingViewModel.getInstance()
                        .setPendingClothingUrl(item.productImageUrl)
                    // 피팅 화면으로 이동
                    onNavigateToFitting(item.productImageUrl)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FitGhostColors.AccentPrimary.copy(alpha = 0.1f),
                    contentColor = FitGhostColors.AccentPrimary
                ),
                shape = RoundedCornerShape(CornerRadius.md)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Checkroom,
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.md)
                    )
                    Text(
                        text = "가상 피팅 해보기",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/** 빈 장바구니 화면 (공용) - 개선된 UI */
@Composable
fun EmptyCartContent() {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg.times(1.25f))
            .softClay(),
        colors = CardDefaults.cardColors(containerColor = FitGhostColors.BgSecondary),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.ShoppingCart,
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                tint = FitGhostColors.TextTertiary
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "장바구니가 비어있습니다",
                style = MaterialTheme.typography.headlineMedium,
                color = FitGhostColors.TextPrimary,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "상점에서 마음에 드는 상품을 담아보세요",
                style = MaterialTheme.typography.bodyLarge,
                color = FitGhostColors.TextSecondary
            )
        }
    }
}

/** 장바구니 요약 카드 (공용) - 개선된 UI */
@Composable
fun CartSummaryCard(totalItems: Int, totalGroups: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .softClay(),
        colors = CardDefaults.cardColors(containerColor = FitGhostColors.BgSecondary),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.xl),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "🛒 장바구니 요약",
                    style = MaterialTheme.typography.headlineMedium,
                    color = FitGhostColors.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "총 ${totalItems}개 상품 • ${totalGroups}개 쇼핑몰",
                    style = MaterialTheme.typography.bodyLarge,
                    color = FitGhostColors.TextSecondary
                )
            }

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        FitGhostColors.AccentPrimary.copy(alpha = 0.12f),
                        RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = totalItems.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = FitGhostColors.AccentPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * 하단 결제 섹션 - 개선된 UI/UX
 * PRD: 몰별 순차 결제 버튼
 */
@Composable
fun BottomPaymentSection(
    groups: List<CartGroup>,
    onStartPayment: (List<CartGroup>) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalPrice = groups.sumOf { it.totalPrice }
    val totalItems = groups.sumOf { it.items.sumOf { item -> item.quantity } }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .softClay(),
        colors = CardDefaults.cardColors(
            containerColor = FitGhostColors.BgSecondary
        ),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 결제 요약
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "총 ${totalItems}개 상품",
                        style = MaterialTheme.typography.bodyLarge,
                        color = FitGhostColors.TextSecondary
                    )
                    Text(
                        text = totalPrice.toKrw(),
                        style = MaterialTheme.typography.headlineLarge,
                        color = FitGhostColors.TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${groups.size}개 쇼핑몰",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FitGhostColors.TextSecondary
                    )
                    Text(
                        text = "순차 결제",
                        style = MaterialTheme.typography.bodyLarge,
                        color = FitGhostColors.AccentPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // 순차 결제 버튼
            Button(
                onClick = { onStartPayment(groups) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FitGhostColors.AccentPrimary
                ),
                shape = RoundedCornerShape(Spacing.lg)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Payment,
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.lg)
                    )
                    Text(
                        text = "순차 결제 시작",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// 가격 KRW 포맷터
private fun Int.toKrw(): String = kotlin.runCatching {
    java.text.NumberFormat.getCurrencyInstance(java.util.Locale.KOREA).format(this)
}.getOrElse { "${this}원" }

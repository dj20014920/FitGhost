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

/**
 * 몰별 장바구니 그룹 카드
 * PRD: 몰별 그룹핑 + 순차 결제 지원
 */
@Composable
fun CartGroupCard(
    group: CartGroup,
    onUpdateQuantity: (String, Int) -> Unit,
    onRemoveItem: (String) -> Unit,
    onClearShopCart: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .softClay(),
        colors = CardDefaults.cardColors(
            containerColor = FitGhostColors.BgSecondary
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 쇼핑몰 헤더
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 쇼핑몰 아이콘
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                FitGhostColors.AccentPrimary.copy(alpha = 0.1f),
                                RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Store,
                            contentDescription = null,
                            tint = FitGhostColors.AccentPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    Column {
                        Text(
                            text = group.shopName,
                            style = MaterialTheme.typography.titleMedium,
                            color = FitGhostColors.TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${group.items.size}개 상품 • ${group.totalPrice}원",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FitGhostColors.TextSecondary
                        )
                    }
                }
                
                // 쇼핑몰 장바구니 삭제 버튼
                IconButton(
                    onClick = onClearShopCart,
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            FitGhostColors.BgTertiary,
                            RoundedCornerShape(8.dp)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "몰 장바구니 비우기",
                        tint = FitGhostColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            // 상품 목록
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                group.items.forEach { item ->
                    CartItemCard(
                        item = item,
                        onUpdateQuantity = { quantity -> onUpdateQuantity(item.id, quantity) },
                        onRemove = { onRemoveItem(item.id) }
                    )
                }
            }
            
            // 이 몰에서 결제하기 버튼
            val ctx = androidx.compose.ui.platform.LocalContext.current
            SoftClayButton(
                onClick = { 
                    com.fitghost.app.util.Browser.open(ctx, group.shopUrl)
                },
                modifier = Modifier.fillMaxWidth(),
                text = "${group.shopName}에서 결제하기 (${group.totalPrice}원)",
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

/**
 * 장바구니 아이템 카드
 */
@Composable
private fun CartItemCard(
    item: CartItem,
    onUpdateQuantity: (Int) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = FitGhostColors.BgPrimary
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 상품 이미지 placeholder
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(FitGhostColors.BgTertiary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Checkroom,
                    contentDescription = null,
                    tint = FitGhostColors.TextTertiary,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // 상품 정보
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.productName,
                    style = MaterialTheme.typography.titleMedium,
                    color = FitGhostColors.TextPrimary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.productPrice}원",
                    style = MaterialTheme.typography.titleMedium,
                    color = FitGhostColors.AccentPrimary,
                    fontWeight = FontWeight.Bold
                )
                
                // 수량 조절
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 수량 감소 버튼
                    IconButton(
                        onClick = { 
                            if (item.quantity > 1) {
                                onUpdateQuantity(item.quantity - 1)
                            }
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                FitGhostColors.BgTertiary,
                                RoundedCornerShape(6.dp)
                            ),
                        enabled = item.quantity > 1
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Remove,
                            contentDescription = "수량 감소",
                            tint = if (item.quantity > 1) FitGhostColors.TextPrimary 
                                  else FitGhostColors.TextTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    
                    // 수량 표시
                    Text(
                        text = item.quantity.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = FitGhostColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.widthIn(min = 24.dp)
                    )
                    
                    // 수량 증가 버튼
                    IconButton(
                        onClick = { onUpdateQuantity(item.quantity + 1) },
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                FitGhostColors.AccentPrimary.copy(alpha = 0.1f),
                                RoundedCornerShape(6.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = "수량 증가",
                            tint = FitGhostColors.AccentPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            
            // 삭제 버튼
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        FitGhostColors.BgTertiary,
                        RoundedCornerShape(8.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "삭제",
                    tint = FitGhostColors.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * 하단 결제 섹션
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
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 결제 요약
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "총 ${totalItems}개 상품",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FitGhostColors.TextSecondary
                    )
                    Text(
                        text = "${totalPrice}원",
                        style = MaterialTheme.typography.headlineMedium,
                        color = FitGhostColors.TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Text(
                    text = "${groups.size}개 몰에서 순차 결제",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FitGhostColors.AccentPrimary,
                    fontWeight = FontWeight.Medium
                )
            }
            
            // 순차 결제 버튼
            Button(
                onClick = { onStartPayment(groups) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp), // 충분한 터치 영역
                colors = ButtonDefaults.buttonColors(
                    containerColor = FitGhostColors.AccentPrimary
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Payment,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "순차 결제 시작",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
package com.fitghost.app.ui.screens.shop

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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fitghost.app.data.model.OutfitRecommendation
import com.fitghost.app.data.model.Product
import com.fitghost.app.ui.components.SoftClayButton
import com.fitghost.app.ui.components.softClay
import com.fitghost.app.ui.theme.FitGhostColors
import com.fitghost.app.ui.theme.Spacing
import com.fitghost.app.ui.theme.IconSize
import com.fitghost.app.ui.theme.CornerRadius
import com.fitghost.app.ui.theme.ComponentSize

/**
 * 추천 코디 카드
 * PRD: "이 바지에는 이 옷이 어울려요" 스타일 추천
 */
@Composable
fun RecommendationCard(
    recommendation: OutfitRecommendation,
    onAddToCart: (Product) -> Unit,
    onToggleWishlist: (Product) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .softClay(),
        colors = CardDefaults.cardColors(
            containerColor = FitGhostColors.BgSecondary
        ),
        shape = RoundedCornerShape(Spacing.lg.times(1.25f))
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg.times(1.25f)),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 추천 헤더
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 아이콘
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            FitGhostColors.AccentPrimary.copy(alpha = 0.1f),
                            RoundedCornerShape(CornerRadius.md)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🎨",
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = recommendation.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = FitGhostColors.TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = recommendation.matchingReason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = FitGhostColors.AccentPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            // 추천 상품들
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                recommendation.recommendedProducts.forEach { product ->
                    RecommendationProductItem(
                        product = product,
                        onAddToCart = { onAddToCart(product) },
                        onToggleWishlist = { onToggleWishlist(product) }
                    )
                }
            }
        }
    }
}

/**
 * 추천 상품 아이템
 */
@Composable
private fun RecommendationProductItem(
    product: Product,
    onAddToCart: () -> Unit,
    onToggleWishlist: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = FitGhostColors.BgPrimary
        ),
        shape = RoundedCornerShape(Spacing.lg)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 상품 이미지
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(CornerRadius.md))
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
                        modifier = Modifier.size(IconSize.lg)
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
                    style = MaterialTheme.typography.titleMedium,
                    color = FitGhostColors.TextPrimary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${product.price}원",
                    style = MaterialTheme.typography.titleMedium,
                    color = FitGhostColors.AccentPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = product.shopName,
                    style = MaterialTheme.typography.bodySmall,
                    color = FitGhostColors.TextSecondary
                )
            }
            
            // 액션 버튼들
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 찜하기 버튼
                IconButton(
                    onClick = onToggleWishlist,
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            FitGhostColors.BgTertiary,
                            RoundedCornerShape(CornerRadius.sm)
                        )
                        .semantics { contentDescription = "찜하기" }
                ) {
                    Icon(
                        imageVector = if (product.isWishlisted) Icons.Outlined.Favorite 
                                     else Icons.Outlined.FavoriteBorder,
                        contentDescription = "찜하기",
                        tint = if (product.isWishlisted) FitGhostColors.AccentPrimary 
                              else FitGhostColors.TextSecondary,
                        modifier = Modifier.size(IconSize.md)
                    )
                }
                
                // 장바구니 버튼
                Button(
                    onClick = onAddToCart,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FitGhostColors.AccentPrimary
                    ),
                    shape = RoundedCornerShape(CornerRadius.sm),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .semantics { contentDescription = "장바구니" }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ShoppingCart,
                        contentDescription = "장바구니",
                        modifier = Modifier.size(IconSize.sm)
                    )
                }
            }
        }
    }
}

/**
 * 일반 상품 카드 (검색 결과용)
 */
@Composable
fun ProductCard(
    product: Product,
    onAddToCart: () -> Unit,
    onToggleWishlist: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .softClay(),
        colors = CardDefaults.cardColors(
            containerColor = FitGhostColors.BgSecondary
        ),
        shape = RoundedCornerShape(Spacing.lg)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 상품 이미지
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(CornerRadius.md))
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
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            
            // 상품 정보
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = FitGhostColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${product.price}원",
                    style = MaterialTheme.typography.titleMedium,
                    color = FitGhostColors.AccentPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = product.shopName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = FitGhostColors.TextSecondary
                )
                
                // 액션 버튼들
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    // 찜하기 버튼
                    IconButton(
                        onClick = onToggleWishlist,
                        modifier = Modifier
                            .size(44.dp) // 최소 터치 타깃
                            .background(
                                FitGhostColors.BgTertiary,
                                RoundedCornerShape(CornerRadius.sm)
                            )
                            .semantics { contentDescription = "찜하기" }
                    ) {
                        Icon(
                            imageVector = if (product.isWishlisted) Icons.Outlined.Favorite 
                                         else Icons.Outlined.FavoriteBorder,
                            contentDescription = "찜하기",
                            tint = if (product.isWishlisted) FitGhostColors.AccentPrimary 
                                  else FitGhostColors.TextSecondary
                        )
                    }
                    
                    // 장바구니 버튼
                    Button(
                        onClick = onAddToCart,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FitGhostColors.AccentPrimary
                        ),
                        shape = RoundedCornerShape(CornerRadius.sm),
                        modifier = Modifier
                            .height(44.dp) // 최소 터치 타깃
                            .semantics { contentDescription = "장바구니" }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ShoppingCart,
                                contentDescription = "장바구니",
                                modifier = Modifier.size(IconSize.md)
                            )
                            Text(
                                text = "담기",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 위시리스트 전용 상품 카드 - 가상피팅 버튼 포함
 * 장바구니의 가상피팅 버튼 로직을 위시리스트에 통합
 */
@Composable
fun WishlistProductCard(
    product: Product,
    onAddToCart: () -> Unit,
    onToggleWishlist: () -> Unit,
    onNavigateToFitting: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .softClay(),
        colors = CardDefaults.cardColors(
            containerColor = FitGhostColors.BgSecondary
        ),
        shape = RoundedCornerShape(Spacing.lg)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 상품 이미지
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(CornerRadius.md))
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
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            
            // 상품 정보
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = FitGhostColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${product.price}원",
                    style = MaterialTheme.typography.titleMedium,
                    color = FitGhostColors.AccentPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = product.shopName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = FitGhostColors.TextSecondary
                )
                
                // 액션 버튼들 - 하트, 가상피팅, 담기 순서
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    // 찜하기 버튼 (하트)
                    IconButton(
                        onClick = onToggleWishlist,
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                if (product.isWishlisted) FitGhostColors.AccentPrimary.copy(alpha = 0.12f)
                                else FitGhostColors.BgTertiary,
                                RoundedCornerShape(10.dp)
                            )
                            .semantics { contentDescription = "찜하기" }
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
                    
                    // 가상피팅 버튼
                    IconButton(
                        onClick = {
                            // FittingViewModel에 의상 이미지 URL 설정
                            com.fitghost.app.ui.screens.fitting.FittingViewModel.getInstance()
                                .setPendingClothingUrl(product.imageUrl)
                            // 피팅 화면으로 이동
                            onNavigateToFitting(product.imageUrl)
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                FitGhostColors.AccentPrimary.copy(alpha = 0.12f),
                                RoundedCornerShape(10.dp)
                            )
                            .semantics { contentDescription = "가상피팅" }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Checkroom,
                            contentDescription = "가상피팅",
                            tint = FitGhostColors.AccentPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    // 장바구니 담기 버튼
                    IconButton(
                        onClick = onAddToCart,
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                FitGhostColors.AccentPrimary.copy(alpha = 0.12f),
                                RoundedCornerShape(10.dp)
                            )
                            .semantics { contentDescription = "장바구니" }
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
}

/**
 * 로딩 섹션
 */
@Composable
fun LoadingSection(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .softClay(),
        colors = CardDefaults.cardColors(
            containerColor = FitGhostColors.BgSecondary
        ),
        shape = RoundedCornerShape(Spacing.lg.times(1.25f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(40.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = FitGhostColors.AccentPrimary
            )
        }
    }
}

/**
 * 검색 결과 없음
 */
@Composable
fun EmptySearchResults(
    query: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .softClay(),
        colors = CardDefaults.cardColors(
            containerColor = FitGhostColors.BgSecondary
        ),
        shape = RoundedCornerShape(Spacing.lg.times(1.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.SearchOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = FitGhostColors.TextTertiary
            )
            
            Text(
                text = "'$query'에 대한 검색 결과가 없습니다",
                style = MaterialTheme.typography.titleLarge,
                color = FitGhostColors.TextSecondary,
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = "다른 키워드로 검색해보시거나\nAI 추천을 확인해보세요",
                style = MaterialTheme.typography.bodyMedium,
                color = FitGhostColors.TextTertiary
            )
        }
    }
}
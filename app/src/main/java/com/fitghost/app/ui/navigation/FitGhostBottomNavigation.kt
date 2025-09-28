package com.fitghost.app.ui.navigation

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.fitghost.app.ui.components.softClay
import com.fitghost.app.ui.theme.FitGhostColors
import com.fitghost.app.data.repository.CartRepositoryProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * FitGhost 하단 네비게이션 바
 * HTML의 footer navigation을 Compose로 구현
 */
@Composable
fun FitGhostBottomNavigation(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    // 장바구니 배지용 카운트
    val cartCount by CartRepositoryProvider.instance.totalItemCount.collectAsStateWithLifecycle(0)

    // Glassmorphism 효과를 위한 배경
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = FitGhostColors.BgGlass
            )
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
        ) {
            bottomNavItems.forEach { destination ->
                val isSelected = currentRoute == destination.route
                val (icon, label) = getNavItemData(destination)
                
                NavigationBarItem(
                    icon = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Shop 탭에만 배지 표시
                            if (destination == FitGhostDestination.Shop) {
                                val badgeText = if (cartCount > 99) "99+" else cartCount.toString()
                                BadgedBox(
                                    badge = {
                                        if (cartCount > 0) {
                                            Badge { Text(badgeText) }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = label,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    },
                    selected = isSelected,
                    onClick = {
                        if (currentRoute != destination.route) {
                            navController.navigate(destination.route) {
                                // 하단 네비게이션은 스택을 쌓지 않음
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = FitGhostColors.NavActive,
                        unselectedIconColor = FitGhostColors.NavInactive,
                        selectedTextColor = FitGhostColors.NavActive,
                        unselectedTextColor = FitGhostColors.NavInactive,
                        indicatorColor = Color.Transparent
                    )
                )
            }
        }
    }
}

/**
 * 네비게이션 아이템의 아이콘과 라벨을 반환
 */
private fun getNavItemData(destination: FitGhostDestination): Pair<ImageVector, String> {
    return when (destination) {
        FitGhostDestination.Fitting -> Icons.Outlined.Person to "피팅"
        FitGhostDestination.Wardrobe -> Icons.Outlined.Checkroom to "옷장"
        FitGhostDestination.Home -> Icons.Outlined.Home to "홈"
        FitGhostDestination.Shop -> Icons.Outlined.Store to "상점"
        FitGhostDestination.Gallery -> Icons.Outlined.PhotoLibrary to "갤러리"
        else -> Icons.Outlined.Home to "홈"
    }
}
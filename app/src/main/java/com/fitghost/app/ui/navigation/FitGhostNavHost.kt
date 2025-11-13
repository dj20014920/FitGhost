package com.fitghost.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.fitghost.app.ui.screens.cart.CartScreen
import com.fitghost.app.ui.screens.fitting.FittingScreen
import com.fitghost.app.ui.screens.gallery.GalleryScreen
import com.fitghost.app.ui.screens.home.HomeScreen
import com.fitghost.app.ui.screens.shop.ShopScreen
import com.fitghost.app.ui.screens.wardrobe.WardrobeAddScreen
import com.fitghost.app.ui.screens.wardrobe.WardrobeScreen as WardrobeScreenImpl

/** FitGhost 앱의 네비게이션 호스트 */
@Composable
fun FitGhostNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
            navController = navController,
            startDestination = FitGhostDestination.Home.route,
            modifier = modifier
    ) {
        // 홈 화면
        composable(FitGhostDestination.Home.route) {
            HomeScreen(
                    onNavigateToFitting = {
                        navController.navigate(FitGhostDestination.Fitting.route)
                    },
                    onNavigateToWardrobe = {
                        navController.navigate(FitGhostDestination.Wardrobe.route)
                    },
                    onNavigateToShop = { navController.navigate(FitGhostDestination.Shop.route) },
                    onNavigateToGallery = {
                        navController.navigate(FitGhostDestination.Gallery.route)
                    }
            )
        }

        // 가상 피팅 화면
        composable(FitGhostDestination.Fitting.route) {
            FittingScreen(
                    onNavigateToGallery = {
                        navController.navigate(FitGhostDestination.Gallery.route)
                    }
            )
        }

        // 옷장 메인 화면
        composable(FitGhostDestination.Wardrobe.route) {
            WardrobeScreenImpl(
                    onNavigateToAdd = {
                        navController.navigate(FitGhostDestination.WardrobeAdd.route)
                    },
                    onNavigateToShop = { itemDescription, itemCategory ->
                        // 옷장 아이템 기반 검색 파라미터 설정
                        com.fitghost.app.ui.screens.shop.ShopViewModel.setPendingSearch(
                            itemDescription, itemCategory
                        )
                        // Shop 화면으로 이동
                        navController.navigate(FitGhostDestination.Shop.route)
                    }
            )
        }

        // 옷장 추가 화면
        composable(FitGhostDestination.WardrobeAdd.route) {
            WardrobeAddScreen(
                    onSavedAndClose = { navController.popBackStack() },
                    onNavigateBack = { navController.popBackStack() }
            )
        }

        // 옷장 아이템 편집 화면 (동일 폼 재사용: itemId 전달 시 기존 값 로드 로직은 추후 확장)
        composable("wardrobe_edit/{itemId}") { backStackEntry ->
            // 현재 WardrobeAddScreen 은 아이템 로딩 파라미터가 없으므로
            // 추후 WardrobeAddScreen 을 WardrobeEditScreen 으로 일반화하거나
            // itemId 처리 가능한 형태로 확장 예정.
            WardrobeAddScreen(
                    onSavedAndClose = { navController.popBackStack() },
                    onNavigateBack = { navController.popBackStack() }
            )
        }

        // 상점 화면
        composable(FitGhostDestination.Shop.route) {
            ShopScreen(
                    onNavigateToFitting = {
                        navController.navigate(FitGhostDestination.Fitting.route)
                    }
            )
        }

        // 갤러리 화면
        composable(FitGhostDestination.Gallery.route) {
            GalleryScreen(
                    onNavigateToFitting = {
                        navController.navigate(FitGhostDestination.Fitting.route)
                    }
            )
        }
    }
}

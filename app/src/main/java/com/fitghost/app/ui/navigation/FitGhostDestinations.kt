package com.fitghost.app.ui.navigation

/** FitGhost 앱의 네비게이션 경로 정의 하단 네비게이션 순서: 피팅 - 옷장 - 홈 - 상점 - 갤러리 */
sealed class FitGhostDestination(val route: String) {
    object Home : FitGhostDestination("home")
    object Fitting : FitGhostDestination("fitting")
    object Wardrobe : FitGhostDestination("wardrobe")
    object WardrobeAdd : FitGhostDestination("wardrobe_add")
    object Shop : FitGhostDestination("shop")
    object Cart : FitGhostDestination("cart")
    object Gallery : FitGhostDestination("gallery")
}

/** 하단 네비게이션에 표시될 아이템들 */
val bottomNavItems =
        listOf(
                FitGhostDestination.Fitting,
                FitGhostDestination.Wardrobe,
                FitGhostDestination.Home,
                FitGhostDestination.Shop,
                FitGhostDestination.Gallery
        )

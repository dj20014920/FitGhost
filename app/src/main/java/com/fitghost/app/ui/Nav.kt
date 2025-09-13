package com.fitghost.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.fitghost.app.ui.screens.*

/**
 * 단일 AppNavHost
 *
 * 네비게이션 통합 전략:
 * - 6개 탭(Home, TryOn, Wardrobe, Shop, Cart, Gallery) → 4개 탭(Home, TryOn, Wardrobe, Discover)
 * - Discover 내부에서 Shop + Gallery + Cart를 통합 (Shop 검색/구매, Try-On 결과 갤러리, 장바구니)
 * - onNavigateIndex 인덱스 의미: 0 = Home 1 = TryOn 2 = Wardrobe 3 = Discover (Shop + Gallery + Cart 통합)
 *
 * 주의:
 * - 기존 ShopScreen/CartScreen/GalleryScreen 직접 접근 경로 제거
 * - 향후 필요 시 Deep Link나 상태로 Discover 내부 섹션 이동 (추가 파라미터/상태 hoisting)
 */
@Composable
fun AppNavHost(modifier: Modifier = Modifier, selectedIndex: Int, onNavigateIndex: (Int) -> Unit) {
    Box(modifier.fillMaxSize()) {
        when (selectedIndex) {
            0 ->
                    HomeScreen(
                            onNavigateTryOn = { onNavigateIndex(1) },
                            onNavigateShop = { onNavigateIndex(3) } // Discover 진입
                    )
            1 -> TryOnScreen()
            2 -> WardrobeScreen()
            3 -> DiscoverScreen()
            else -> Text("Unknown")
        }
    }
}

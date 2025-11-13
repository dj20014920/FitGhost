package com.fitghost.app.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * FitGhost 디자인 시스템 - Spacing & Sizing Tokens
 * Material 3 기반 일관된 레이아웃 구조
 */
object Spacing {
    /** 최소 간격 (4dp) - Divider, 미세 여백 */
    val xs: Dp = 4.dp
    
    /** 작은 간격 (8dp) - 인접 요소 간 여백 */
    val sm: Dp = 8.dp
    
    /** 기본 간격 (12dp) - 컴포넌트 내부 여백 */
    val md: Dp = 12.dp
    
    /** 큰 간격 (16dp) - 화면 패딩, 카드 여백 */
    val lg: Dp = 16.dp
    
    /** 매우 큰 간격 (24dp) - 섹션 구분 */
    val xl: Dp = 24.dp
    
    /** 최대 간격 (32dp) - 화면 상하단 패딩 */
    val xxl: Dp = 32.dp
}

object IconSize {
    /** 작은 아이콘 (16dp) - 인라인 아이콘, 상태 표시 */
    val sm: Dp = 16.dp
    
    /** 기본 아이콘 (20dp) - 버튼 내부, 칩 아이콘 */
    val md: Dp = 20.dp
    
    /** 큰 아이콘 (24dp) - 툴바, 탭바 */
    val lg: Dp = 24.dp
    
    /** 매우 큰 아이콘 (32dp) - Empty State */
    val xl: Dp = 32.dp
}

object ComponentSize {
    /** 최소 터치 영역 (44dp) - Android Accessibility */
    val minTouchTarget: Dp = 44.dp
    
    /** 작은 버튼 높이 (40dp) */
    val buttonSmall: Dp = 40.dp
    
    /** 기본 버튼 높이 (48dp) */
    val buttonMedium: Dp = 48.dp
    
    /** 큰 버튼 높이 (56dp) */
    val buttonLarge: Dp = 56.dp
    
    /** 이미지 썸네일 (64dp) */
    val thumbnail: Dp = 64.dp
    
    /** 상품 이미지 (80dp) */
    val productImage: Dp = 80.dp
    
    /** 대형 이미지 (120dp) */
    val largeImage: Dp = 120.dp
}

object CornerRadius {
    /** 작은 라운딩 (8dp) - 버튼, 칩 */
    val sm: Dp = 8.dp
    
    /** 기본 라운딩 (12dp) - 카드 */
    val md: Dp = 12.dp
    
    /** 큰 라운딩 (16dp) - 바텀시트 */
    val lg: Dp = 16.dp
    
    /** 완전 원형 (999dp) */
    val circular: Dp = 999.dp
}

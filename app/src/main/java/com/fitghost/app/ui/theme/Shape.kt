package com.fitghost.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// CSS 변수에서 가져온 Border Radius 값들
object FitGhostRadius {
    val Large = 24.dp     // --radius-lg: 1.5rem
    val Medium = 16.dp    // --radius-md: 1rem  
    val Small = 12.dp     // --radius-sm: 0.75rem
    val ExtraSmall = 8.dp
}

// Material3 Shapes for FitGhost
val FitGhostShapes = Shapes(
    extraSmall = RoundedCornerShape(FitGhostRadius.ExtraSmall),
    small = RoundedCornerShape(FitGhostRadius.Small),
    medium = RoundedCornerShape(FitGhostRadius.Medium),
    large = RoundedCornerShape(FitGhostRadius.Large),
    extraLarge = RoundedCornerShape(FitGhostRadius.Large)
)
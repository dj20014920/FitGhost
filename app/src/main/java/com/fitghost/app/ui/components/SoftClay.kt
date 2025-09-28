package com.fitghost.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fitghost.app.ui.theme.FitGhostColors

/**
 * Soft Clay Neumorphism 효과를 위한 Modifier
 * HTML의 --shadow-soft-clay를 Compose로 구현
 */
fun Modifier.softClay(
    radius: Dp = 16.dp,
    isPressed: Boolean = false
): Modifier = this.then(
    Modifier
        .clip(RoundedCornerShape(radius))
        .border(
            width = 0.5.dp,
            color = Color.White.copy(alpha = 0.7f),
            shape = RoundedCornerShape(radius)
        )
        .drawBehind {
            val shadowColor1 = if (isPressed) FitGhostColors.ShadowDark else FitGhostColors.ShadowLight
            val shadowColor2 = if (isPressed) FitGhostColors.ShadowLight else FitGhostColors.ShadowDark
            
            // Soft Clay shadow effect 구현
            // 상단 좌측 밝은 그림자
            drawIntoCanvas { canvas ->
                val paint1 = Paint().apply {
                    color = shadowColor1
                    isAntiAlias = true
                }
                
                // 하단 우측 어두운 그림자  
                val paint2 = Paint().apply {
                    color = shadowColor2
                    isAntiAlias = true
                }
                
                // Native shadow는 현재 Compose에서 제한적이므로
                // 기본 배경색으로 fallback
            }
        }
)

/**
 * Soft Clay Inset 효과 (HTML의 --shadow-soft-clay-inset)
 */
fun Modifier.softClayInset(
    radius: Dp = 16.dp
): Modifier = this.then(
    Modifier
        .clip(RoundedCornerShape(radius))
        .drawBehind {
            // Inset shadow 효과는 배경색을 조금 더 어둡게
            drawRect(
                color = FitGhostColors.BgPrimary.copy(alpha = 0.8f)
            )
        }
)
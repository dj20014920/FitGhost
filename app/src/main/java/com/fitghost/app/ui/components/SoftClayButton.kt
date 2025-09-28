package com.fitghost.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fitghost.app.ui.theme.FitGhostColors

/** Soft Clay 스타일의 버튼 컴포넌트 HTML의 .soft-clay 클래스를 Compose로 구현 */
@Composable
fun SoftClayButton(
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        icon: ImageVector? = null,
        text: String? = null,
        shape: RoundedCornerShape = RoundedCornerShape(16.dp)
) {
    var isPressed by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    Box(
            modifier =
                    modifier.background(color = FitGhostColors.BgPrimary, shape = shape)
                            .softClay(isPressed = isPressed)
                            .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    enabled = enabled
                            ) {
                                isPressed = true
                                onClick()
                                isPressed = false
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
    ) {
        Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            icon?.let {
                Icon(imageVector = it, contentDescription = null, tint = FitGhostColors.TextPrimary)
            }

            text?.let {
                Text(
                        text = it,
                        style = MaterialTheme.typography.labelLarge,
                        color = FitGhostColors.TextPrimary,
                        fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/** 원형 Soft Clay 버튼 (아이콘 전용) */
@Composable
fun SoftClayIconButton(
        onClick: () -> Unit,
        icon: ImageVector,
        modifier: Modifier = Modifier,
        size: Dp = 48.dp,
        contentDescription: String? = null,
        enabled: Boolean = true
) {
    var isPressed by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    Box(
            modifier =
                    modifier.size(size)
                            .background(color = FitGhostColors.BgPrimary, shape = CircleShape)
                            .softClay(radius = size.div(2), isPressed = isPressed)
                            .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    enabled = enabled
                            ) {
                                isPressed = true
                                onClick()
                                isPressed = false
                            },
            contentAlignment = Alignment.Center
    ) {
        Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = FitGhostColors.TextPrimary,
                modifier = Modifier.size(size.times(0.5f))
        )
    }
}

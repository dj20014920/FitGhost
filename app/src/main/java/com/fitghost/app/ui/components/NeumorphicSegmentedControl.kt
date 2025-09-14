package com.fitghost.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fitghost.app.ui.theme.liquidGlass
import com.fitghost.app.ui.theme.neumorphic

@Composable
fun NeumorphicSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    useLiquidGlass: Boolean = true
) {
    val shape = RoundedCornerShape(24.dp)
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .then(
                if (useLiquidGlass) {
                    Modifier.liquidGlass(
                        shape = shape,
                        blur = 20.dp,
                        alpha = 0.8f,
                        borderAlpha = 0.25f
                    )
                } else {
                    Modifier.neumorphic(
                        shape = shape,
                        elevation = 4.dp,
                        backgroundColor = MaterialTheme.colorScheme.surface,
                        isPressed = true
                    )
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEachIndexed { index, label ->
                val isSelected = index == selectedIndex
                LiquidSegmentItem(
                    label = label,
                    isSelected = isSelected,
                    onClick = { onSelect(index) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun LiquidSegmentItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    var isPressed by remember { mutableStateOf(false) }
    
    val animatedScale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.95f
            isSelected -> 1f
            else -> 0.98f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "segment_scale"
    )
    
    val animatedTextColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "segment_text_color"
    )

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            isPressed = when (interaction) {
                is androidx.compose.foundation.interaction.PressInteraction.Press -> true
                else -> false
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .scale(animatedScale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .then(
                if (isSelected) {
                    Modifier
                        .neumorphic(
                            shape = RoundedCornerShape(20.dp),
                            elevation = 6.dp,
                            backgroundColor = MaterialTheme.colorScheme.surface,
                            isPressed = false
                        )
                        .drawWithContent {
                            drawContent()
                            // Subtle glow for selected item
                            drawRoundRect(
                                color = Color(0xFF007AFF).copy(alpha = 0.1f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx())
                            )
                        }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Medium
            ),
            color = animatedTextColor,
            maxLines = 1
        )
    }
}

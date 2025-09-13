@file:OptIn(ExperimentalMaterial3Api::class)

package com.fitghost.app.ui.theme

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Neumorphic Card composable with soft shadows
 */
@Composable
fun NeumorphicCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    elevation: Dp = 6.dp,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = Color.White,
                spotColor = Color.Black.copy(alpha = 0.1f)
            )
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.1f),
                spotColor = Color.White
            )
            .background(color = backgroundColor, shape = shape),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Neumorphic Button with press animation
 */
@Composable
fun NeumorphicButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(12.dp),
    elevation: Dp = 6.dp,
    pressedElevation: Dp = 2.dp,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable RowScope.() -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    
    Surface(
        onClick = { if (enabled) { isPressed = true; onClick() } },
        modifier = modifier,
        shape = shape,
        color = backgroundColor,
        contentColor = contentColor,
        enabled = enabled,
        shadowElevation = if (isPressed) pressedElevation else elevation,
        tonalElevation = if (isPressed) pressedElevation else elevation
    ) {
        Box(
            modifier = Modifier
                .shadow(
                    elevation = if (isPressed) pressedElevation else elevation,
                    shape = shape,
                    ambientColor = Color.White,
                    spotColor = Color.Black.copy(alpha = 0.1f)
                )
                .shadow(
                    elevation = if (isPressed) pressedElevation else elevation,
                    shape = shape,
                    ambientColor = Color.Black.copy(alpha = 0.1f),
                    spotColor = Color.White
                )
                .background(color = backgroundColor, shape = shape)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
    
    // Reset pressed state after a short delay
    LaunchedEffect(isPressed) {
        if (isPressed) {
            kotlinx.coroutines.delay(150)
            isPressed = false
        }
    }
}

/**
 * Neumorphic Outlined TextField
 */
@Composable
fun NeumorphicOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(12.dp),
    elevation: Dp = 4.dp,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    isError: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = Color.White,
                spotColor = Color.Black.copy(alpha = 0.1f)
            )
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.1f),
                spotColor = Color.White
            ),
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = shape,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        isError = isError,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            disabledBorderColor = Color.Transparent,
            errorBorderColor = Color.Transparent
        )
    )
}

/**
 * Neumorphic Icon Button with press animation
 */
@Composable
fun NeumorphicIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(12.dp),
    elevation: Dp = 6.dp,
    pressedElevation: Dp = 2.dp,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable BoxScope.() -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clickable(enabled = enabled) {
                isPressed = true
                onClick()
            }
            .shadow(
                elevation = if (isPressed) pressedElevation else elevation,
                shape = shape,
                ambientColor = Color.White,
                spotColor = Color.Black.copy(alpha = 0.1f)
            )
            .shadow(
                elevation = if (isPressed) pressedElevation else elevation,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.1f),
                spotColor = Color.White
            )
            .background(color = backgroundColor, shape = shape)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            kotlinx.coroutines.delay(150)
            isPressed = false
        }
    }
}

/**
 * Neumorphic Switch
 */
@Composable
fun NeumorphicSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    thumbColor: Color = Color.White,
    trackColor: Color = MaterialTheme.colorScheme.primary
) {
    val switchWidth = 60.dp
    val switchHeight = 34.dp
    val thumbSize = 28.dp
    val thumbPadding = 3.dp

    val transition = updateTransition(targetState = checked, label = "Switch")
    val thumbOffset by transition.animateDp(label = "ThumbOffset") { isChecked ->
        if (isChecked) switchWidth - thumbSize - thumbPadding else thumbPadding
    }
    val trackBgColor by transition.animateColor(label = "TrackColor") { isChecked ->
        if (isChecked) trackColor else MaterialTheme.colorScheme.surfaceVariant
    }

    Box(
        modifier = modifier
            .width(switchWidth)
            .height(switchHeight)
            .clip(RoundedCornerShape(17.dp))
            .background(trackBgColor)
            .clickable { onCheckedChange(!checked) }
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(thumbSize)
                .padding(2.dp)
                .align(Alignment.CenterStart)
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(14.dp),
                    clip = true
                )
                .background(thumbColor, RoundedCornerShape(14.dp))
        )
    }
}

/**
 * Neumorphic Slider
 */
@Composable
fun NeumorphicSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    steps: Int = 0,
    enabled: Boolean = true,
    thumbColor: Color = Color.White,
    activeTrackColor: Color = MaterialTheme.colorScheme.primary,
    inactiveTrackColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val interactionSource = remember { MutableInteractionSource() }
    val colors = SliderDefaults.colors(
        thumbColor = thumbColor,
        activeTrackColor = activeTrackColor,
        inactiveTrackColor = inactiveTrackColor,
    )
    
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        valueRange = valueRange,
        onValueChangeFinished = onValueChangeFinished,
        steps = steps,
        enabled = enabled,
        interactionSource = interactionSource,
        thumb = {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .shadow(
                        elevation = 4.dp,
                        shape = RoundedCornerShape(12.dp),
                        clip = true
                    )
                    .background(thumbColor, RoundedCornerShape(12.dp))
            )
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                modifier = Modifier.height(8.dp),
                colors = colors,
                enabled = enabled
            )
        }
    )
}

/**
 * Neumorphic Circular Progress Indicator
 */
@Composable
fun NeumorphicCircularProgress(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 4.dp
) {
    CircularProgressIndicator(
        modifier = modifier,
        color = color,
        strokeWidth = strokeWidth
    )
}

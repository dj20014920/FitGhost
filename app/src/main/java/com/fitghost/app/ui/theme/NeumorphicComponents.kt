package com.fitghost.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
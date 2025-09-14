package com.fitghost.app.ui.theme

import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import android.graphics.BlurMaskFilter

// Liquid Glass Effect Modifier
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(16.dp),
    blur: Dp = 20.dp,
    alpha: Float = 0.8f,
    borderAlpha: Float = 0.2f
) = this
    .clip(shape)
    .background(
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = alpha * 0.2f),
                Color.White.copy(alpha = alpha * 0.1f)
            )
        )
    )
    .drawBehind {
        // Glass border effect
        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                style = PaintingStyle.Stroke
                strokeWidth = 1.dp.toPx()
                color = Color.White.copy(alpha = borderAlpha)
            }
            when (shape) {
                is RoundedCornerShape -> {
                    val radius = 16.dp.toPx()
                    canvas.drawRoundRect(
                        left = 0f,
                        top = 0f,
                        right = size.width,
                        bottom = size.height,
                        radiusX = radius,
                        radiusY = radius,
                        paint = paint
                    )
                }
            }
        }
    }

// Enhanced Neumorphic Effect
fun Modifier.neumorphic(
    shape: Shape = RoundedCornerShape(16.dp),
    elevation: Dp = 8.dp,
    backgroundColor: Color,
    isPressed: Boolean = false,
    lightShadowColor: Color = Color.White.copy(alpha = 0.6f),
    darkShadowColor: Color = Color.Black.copy(alpha = 0.25f)
) = this
    .drawBehind {
        drawIntoCanvas { canvas ->
            val paint = Paint()
            val frameworkPaint = paint.asFrameworkPaint()
            val elevationPx = elevation.toPx()
            val shadowOffset = if (isPressed) elevationPx * 0.3f else elevationPx
            
            // Dark shadow (bottom-right)
            frameworkPaint.color = darkShadowColor.toArgb()
            frameworkPaint.maskFilter = BlurMaskFilter(elevationPx, BlurMaskFilter.Blur.NORMAL)
            
            when (shape) {
                is RoundedCornerShape -> {
                    val radius = 16.dp.toPx()
                    canvas.drawRoundRect(
                        left = shadowOffset,
                        top = shadowOffset,
                        right = size.width + shadowOffset,
                        bottom = size.height + shadowOffset,
                        radiusX = radius,
                        radiusY = radius,
                        paint = paint
                    )
                    
                    // Light shadow (top-left)
                    frameworkPaint.color = lightShadowColor.toArgb()
                    canvas.drawRoundRect(
                        left = -shadowOffset,
                        top = -shadowOffset,
                        right = size.width - shadowOffset,
                        bottom = size.height - shadowOffset,
                        radiusX = radius,
                        radiusY = radius,
                        paint = paint
                    )
                }
            }
        }
    }
    .background(color = backgroundColor, shape = shape)
    .clip(shape)

fun Modifier.glow(color: Color, radius: Dp) = this.drawBehind {
    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            this.color = color
            asFrameworkPaint().apply {
                maskFilter = BlurMaskFilter(radius.toPx(), BlurMaskFilter.Blur.NORMAL)
            }
        }
        canvas.drawCircle(center, size.minDimension / 2, paint)
    }
}

@Composable
fun NeumorphicCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    elevation: Dp = 12.dp,
    useGlassEffect: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val animatedElevation by animateDpAsState(
        targetValue = elevation,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "elevation"
    )

    Box(
        modifier = modifier
            .then(
                if (useGlassEffect) {
                    Modifier.liquidGlass(shape = shape)
                } else {
                    Modifier.neumorphic(
                        shape = shape,
                        elevation = animatedElevation,
                        backgroundColor = MaterialTheme.colorScheme.surface
                    )
                }
            )
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun NeumorphicButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(16.dp),
    elevation: Dp = 8.dp,
    content: @Composable RowScope.() -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    
    val animatedElevation by animateDpAsState(
        targetValue = if (isPressed) elevation * 0.3f else elevation,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "button_elevation"
    )

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            isPressed = when (interaction) {
                is androidx.compose.foundation.interaction.PressInteraction.Press -> true
                else -> false
            }
        }
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .neumorphic(
                shape = shape,
                elevation = animatedElevation,
                backgroundColor = MaterialTheme.colorScheme.surface,
                isPressed = isPressed
            ),
        enabled = enabled,
        interactionSource = interactionSource,
        color = Color.Transparent,
        shape = shape
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
fun NeumorphicOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(16.dp),
    enabled: Boolean = true,
    readOnly: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .neumorphic(
                shape = shape,
                elevation = 4.dp,
                backgroundColor = MaterialTheme.colorScheme.surface
            ),
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
        readOnly = readOnly,
        shape = shape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            disabledBorderColor = Color.Transparent,
            errorBorderColor = Color.Transparent
        )
    )
}

@Composable
fun NeumorphicCircularProgress(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 4.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "progress")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = modifier
            .size(48.dp)
            .neumorphic(
                shape = CircleShape,
                elevation = 8.dp,
                backgroundColor = MaterialTheme.colorScheme.surface
            ),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .size(32.dp)
                .rotate(angle),
            color = color,
            strokeWidth = strokeWidth
        )
    }
}

@Composable
fun NeumorphicIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    elevation: Dp = 12.dp,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    useGlassEffect: Boolean = false,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    var isPressed by remember { mutableStateOf(false) }
    var isHovered by remember { mutableStateOf(false) }

    val animatedElevation by animateDpAsState(
        targetValue = when {
            isPressed -> elevation * 0.4f
            isHovered -> elevation * 1.2f
            else -> elevation
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "icon_button_elevation"
    )

    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "icon_button_scale"
    )

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is androidx.compose.foundation.interaction.PressInteraction.Press -> isPressed = true
                is androidx.compose.foundation.interaction.PressInteraction.Release -> isPressed = false
                is androidx.compose.foundation.interaction.PressInteraction.Cancel -> isPressed = false
                is androidx.compose.foundation.interaction.HoverInteraction.Enter -> isHovered = true
                is androidx.compose.foundation.interaction.HoverInteraction.Exit -> isHovered = false
            }
        }
    }

    Box(
        modifier = modifier
            .size(48.dp)
            .scale(animatedScale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .then(
                if (useGlassEffect) {
                    Modifier.liquidGlass(shape = shape)
                } else {
                    Modifier.neumorphic(
                        shape = shape,
                        elevation = animatedElevation,
                        backgroundColor = MaterialTheme.colorScheme.surface,
                        isPressed = isPressed
                    )
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}
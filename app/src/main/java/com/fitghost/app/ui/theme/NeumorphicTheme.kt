package com.fitghost.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Neumorphic color scheme
private val NeumorphicLightColorScheme = lightColorScheme(
    primary = Color(0xFF0A84FF), // iOS Blue
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E9FF),
    onPrimaryContainer = Color(0xFF00224D),
    secondary = Color(0xFF8E8E93), // iOS Gray
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEAEAEC),
    onSecondaryContainer = Color(0xFF1C1C1E),
    tertiary = Color(0xFF34C759), // iOS Green
    onTertiary = Color.Black,
    background = Color(0xFFEFEFF4), // iOS grouped bg
    onBackground = Color(0xFF1C1C1E),
    surface = Color(0xFFEFEFF4),
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFF7F7F9),
    onSurfaceVariant = Color(0xFF3A3A3C),
    outline = Color(0xFFAEAEB2),
    error = Color(0xFFFF3B30),
    onError = Color.White,
    errorContainer = Color(0xFFFFE5E3),
    onErrorContainer = Color(0xFF410E0B),
)

private val NeumorphicDarkColorScheme = darkColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF0A84FF).copy(alpha = 0.2f),
    onPrimaryContainer = Color(0xFFD6E9FF),
    secondary = Color(0xFF98989F),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF2C2C2E),
    onSecondaryContainer = Color(0xFFEAEAEC),
    tertiary = Color(0xFF30D158),
    onTertiary = Color.Black,
    background = Color(0xFF0D0D0D),
    onBackground = Color(0xFFEAEAEA),
    surface = Color(0xFF0D0D0D),
    onSurface = Color(0xFFEAEAEA),
    surfaceVariant = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFFC7C7CC),
    outline = Color(0xFF2C2C2E),
    error = Color(0xFFFF453A),
    onError = Color.Black,
    errorContainer = Color(0xFF3A1917),
    onErrorContainer = Color(0xFFFFB4A9),
)

// Neumorphic typography
private val NeumorphicTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    )
)

// Neumorphic shapes
private val NeumorphicShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp)
)

@Composable
fun NeumorphicTheme(content: @Composable () -> Unit) {
    val isDark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (isDark) NeumorphicDarkColorScheme else NeumorphicLightColorScheme,
        typography = NeumorphicTypography,
        shapes = NeumorphicShapes,
        content = content
    )
}
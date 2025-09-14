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
import androidx.compose.foundation.isSystemInDarkTheme

// Liquid Glass + Neumorphism Color Schemes
val LiquidGlassLightColorScheme = lightColorScheme(
    primary = Color(0xFF1A1A1A), // Pure black for contrast
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1A1A1A).copy(alpha = 0.08f),
    onPrimaryContainer = Color(0xFF1A1A1A),
    secondary = Color(0xFF6B6B6B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF6B6B6B).copy(alpha = 0.12f),
    onSecondaryContainer = Color(0xFF1A1A1A),
    tertiary = Color(0xFF007AFF), // iOS blue accent
    onTertiary = Color.White,
    background = Color(0xFFF8F9FA), // Slightly warm white
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFFFFFFF), // Pure white surface
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFF0F0F3),
    onSurfaceVariant = Color(0xFF3C3C43),
    outline = Color(0xFFE5E5EA),
    error = Color(0xFFFF3B30),
    onError = Color.White,
    errorContainer = Color(0xFFFF3B30).copy(alpha = 0.1f),
    onErrorContainer = Color(0xFFFF3B30),
)

val LiquidGlassDarkColorScheme = darkColorScheme(
    primary = Color(0xFF00BFFF), // DeepSkyBlue as a vibrant accent
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF00BFFF).copy(alpha = 0.2f),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF4E4E4E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF333333),
    onSecondaryContainer = Color(0xFFEAEAEA),
    tertiary = Color(0xFF00BFFF),
    onTertiary = Color.Black,
    background = Color(0xFF1A1A1A), // Very dark gray, almost black
    onBackground = Color(0xFFEAEAEA),
    surface = Color(0xFF202020), // Slightly lighter gray for components
    onSurface = Color(0xFFEAEAEA),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFFC7C7CC),
    outline = Color(0xFF404040),
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
fun NeumorphicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        LiquidGlassDarkColorScheme
    } else {
        LiquidGlassLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NeumorphicTypography,
        shapes = NeumorphicShapes,
        content = content
    )
}
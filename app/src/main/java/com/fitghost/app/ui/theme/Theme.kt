package com.fitghost.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// FitGhost Light Color Scheme (Soft Clay 기반)
private val FitGhostLightColorScheme = lightColorScheme(
    // Primary colors (Accent blue)
    primary = FitGhostColors.AccentPrimary,
    onPrimary = FitGhostColors.TextInverse,
    primaryContainer = FitGhostColors.BgSecondary,
    onPrimaryContainer = FitGhostColors.TextPrimary,
    
    // Secondary colors
    secondary = FitGhostColors.TextSecondary,
    onSecondary = FitGhostColors.TextInverse,
    secondaryContainer = FitGhostColors.BgTertiary,
    onSecondaryContainer = FitGhostColors.TextPrimary,
    
    // Background colors (Soft Clay)
    background = FitGhostColors.BgPrimary,
    onBackground = FitGhostColors.TextPrimary,
    surface = FitGhostColors.BgSecondary,
    onSurface = FitGhostColors.TextPrimary,
    surfaceVariant = FitGhostColors.BgTertiary,
    onSurfaceVariant = FitGhostColors.TextSecondary,
    
    // Outline colors
    outline = FitGhostColors.BorderPrimary,
    outlineVariant = FitGhostColors.BorderSecondary,
    
    // Error colors
    error = FitGhostColors.Error,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    
    // Surface tint
    surfaceTint = FitGhostColors.AccentPrimary
)

@Composable
fun FitGhostTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = FitGhostLightColorScheme,
        typography = FitGhostTypography,
        shapes = FitGhostShapes,
        content = content
    )
}
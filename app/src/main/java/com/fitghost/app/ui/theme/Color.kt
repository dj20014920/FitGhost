package com.fitghost.app.ui.theme

import androidx.compose.ui.graphics.Color

object FitGhostColors {
    // Background Colors (from CSS variables)
    val BgPrimary = Color(0xFFF0F2F5)      // --bg-primary
    val BgSecondary = Color(0xFFFFFFFF)    // --bg-secondary  
    val BgTertiary = Color(0xFFE4E6EB)     // --bg-tertiary
    val BgInverse = Color(0xFF1C1C1E)      // --bg-inverse
    val BgGlass = Color(0x99F0F2F5)        // --bg-glass (0.6 alpha)
    
    // Text Colors
    val TextPrimary = Color(0xFF1C1E21)    // --text-primary
    val TextSecondary = Color(0xFF65676B)  // --text-secondary
    val TextTertiary = Color(0xFF8A8D91)   // --text-tertiary
    val TextInverse = Color(0xFFFFFFFF)    // --text-inverse
    
    // Border Colors
    val BorderPrimary = Color(0xFFE4E4E7)  // --border-primary
    val BorderSecondary = Color(0xFFD4D4D8) // --border-secondary
    
    // Accent Colors
    val AccentPrimary = Color(0xFF1877F2)  // --accent-primary (Facebook blue)
    
    // Soft Clay Shadow Colors (for neumorphism effect)
    val ShadowLight = Color(0xFFFFFFFF)    // Light shadow
    val ShadowDark = Color(0xFFD1D3D6)     // Dark shadow
    
    // Navigation Colors
    val NavActive = TextPrimary
    val NavInactive = TextSecondary
    
    // Error and Success
    val Error = Color(0xFFDC3545)
    val Success = Color(0xFF28A745)
    val Warning = Color(0xFFFFC107)
}
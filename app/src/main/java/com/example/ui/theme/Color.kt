package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Playful Brand Accents (Consistent across both light and dark themes)
val PlayfulPurple = Color(0xFF7C3AED)        // Main energetic purple
val PlayfulPurpleLight = Color(0xFFEDE9FE)
val PlayfulPurpleDark = Color(0xFF4C1D95)

val PlayfulPink = Color(0xFFEC4899)          // XO accent & celebration
val PlayfulPinkLight = Color(0xFFFCE7F3)

val PlayfulOrange = Color(0xFFF97316)        // Tug of War / Highlights / P1
val PlayfulOrangeLight = Color(0xFFFFEDD5)

val PlayfulYellow = Color(0xFFFBBF24)        // Warm sunshine stars/trophies
val PlayfulYellowLight = Color(0xFFFEF3C7)

val PlayfulGreen = Color(0xFF10B981)         // Snakes & Ladders / Success
val PlayfulGreenLight = Color(0xFFD1FAE5)

val PlayfulBlue = Color(0xFF0EA5E9)          // Casual sky blue
val PlayfulBlueLight = Color(0xFFE0F2FE)

val PlayfulWood = Color(0xFFB45309)          // Chess warm wood
val PlayfulWoodLight = Color(0xFFFEF3C7)

data class AppThemeColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val border: Color,
    val borderLight: Color,
    val accent: Color,
    val selectedSurface: Color,
    val button: Color,
    val buttonText: Color,
    val icon: Color,
    val iconSecondary: Color,
    val shadow: Color,
    val isDark: Boolean,

    // Game Brand Accents (Preserved across both themes)
    val xoAccent: Color = Color(0xFFF43F5E),       // Red / Orange
    val tugAccent: Color = Color(0xFFF97316),      // Orange
    val snakesAccent: Color = Color(0xFF10B981),   // Green
    val chessAccent: Color = Color(0xFF64748B)     // Slate
)

val DarkThemeColors = AppThemeColors(
    background = Color(0xFF0B1120),        // Deep Midnight Navy / near-black
    surface = Color(0xFF1E293B),           // Elevated crisp dark navy card
    surfaceVariant = Color(0xFF283548),    // Secondary dark container
    primaryText = Color(0xFFF8FAFC),       // Crisp white
    secondaryText = Color(0xFF94A3B8),     // Soft cool slate gray
    border = Color(0xFF475569),            // Clear, visible slate border
    borderLight = Color(0xFF334155),
    accent = Color(0xFFA78BFA),            // Bright playful violet
    selectedSurface = Color(0xFF7C3AED).copy(alpha = 0.28f),
    button = Color(0xFF7C3AED),
    buttonText = Color.White,
    icon = Color(0xFFF8FAFC),
    iconSecondary = Color(0xFF94A3B8),
    shadow = Color(0x80000000),
    isDark = true
)

val LightThemeColors = AppThemeColors(
    background = Color(0xFFF1F5F9),        // Light cool slate-gray canvas
    surface = Color(0xFFFFFFFF),           // Pure clean white card
    surfaceVariant = Color(0xFFE2E8F0),    // Subtle distinct secondary surface
    primaryText = Color(0xFF0F172A),       // Deep navy / charcoal - rich contrast
    secondaryText = Color(0xFF475569),     // Muted slate gray - readable, not pale pastel
    border = Color(0xFFCBD5E1),            // Medium-light neutral gray border (clear definition)
    borderLight = Color(0xFFE2E8F0),
    accent = Color(0xFF7C3AED),            // Vibrant royal purple
    selectedSurface = Color(0xFF7C3AED).copy(alpha = 0.12f),
    button = Color(0xFF7C3AED),
    buttonText = Color.White,
    icon = Color(0xFF0F172A),              // Deep charcoal icon
    iconSecondary = Color(0xFF64748B),
    shadow = Color(0x1A0F172A),
    isDark = false
)

val LocalAppThemeColors = staticCompositionLocalOf { LightThemeColors }

object ChillTheme {
    val colors: AppThemeColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppThemeColors.current
}

// Dynamic theme-aware border for all existing screens
val ToyboxBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = ChillTheme.colors.border

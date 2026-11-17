package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Brand & Game Accents
val PlayfulPurple = Color(0xFF7C4DFF)        // Energetic electric violet
val PlayfulPurpleLight = Color(0xFFEDE9FE)
val PlayfulPurpleDark = Color(0xFF4C1D95)

val PlayfulCyan = Color(0xFF00CFFF)          // Crisp cyan highlight
val PlayfulCyanLight = Color(0xFFCFFAFE)

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
    val secondaryBackground: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val border: Color,
    val borderLight: Color,
    val accent: Color,
    val secondaryAccent: Color,
    val selectedSurface: Color,
    val button: Color,
    val buttonText: Color,
    val icon: Color,
    val iconSecondary: Color,
    val shadow: Color,
    val success: Color = Color(0xFF16A34A),
    val warning: Color = Color(0xFFD97706),
    val error: Color = Color(0xFFDC2626),
    val coffeeAccent: Color = Color(0xFFD97706),
    val coffeeContainer: Color = Color(0xFFFFFBEB),
    val coffeeBorder: Color = Color(0xFFF59E0B),
    val isDark: Boolean,

    // Game Brand Accents (Preserved across both themes)
    val xoAccent: Color = Color(0xFFF43F5E),       // Red / Orange
    val tugAccent: Color = Color(0xFFF97316),      // Orange
    val snakesAccent: Color = Color(0xFF10B981),   // Green
    val chessAccent: Color = Color(0xFF64748B)     // Slate
)

val DarkThemeColors = AppThemeColors(
    background = Color(0xFF070B18),              // Deep midnight gaming canvas
    secondaryBackground = Color(0xFF0D1528),     // Secondary elevated background
    surface = Color(0xFF142238),                 // Rich navy card surface
    surfaceVariant = Color(0xFF1B2B45),          // Inner chips & secondary containers
    primaryText = Color(0xFFFFFFFF),             // Pure crisp white
    secondaryText = Color(0xFFA6B4C8),           // Cool blue-gray secondary text
    border = Color(0xFF233554),                  // Subtle blue-gray border
    borderLight = Color(0xFF192740),             // Subtle hairline divider
    accent = Color(0xFF7C4DFF),                  // Primary electric purple accent
    secondaryAccent = Color(0xFF00CFFF),         // Secondary cyan accent
    selectedSurface = Color(0xFF7C4DFF).copy(alpha = 0.22f),
    button = Color(0xFF7C4DFF),
    buttonText = Color.White,
    icon = Color(0xFFFFFFFF),
    iconSecondary = Color(0xFFA6B4C8),
    shadow = Color(0x99000000),
    success = Color(0xFF22C55E),
    warning = Color(0xFFFBBF24),
    error = Color(0xFFF87171),
    coffeeAccent = Color(0xFFFBBF24),
    coffeeContainer = Color(0xFF271D12),
    coffeeBorder = Color(0xFFF59E0B).copy(alpha = 0.50f),
    isDark = true
)

val LightThemeColors = AppThemeColors(
    background = Color(0xFFF5F7FC),              // Clean airy light canvas
    secondaryBackground = Color(0xFFFFFFFF),     // Crisp white secondary background
    surface = Color(0xFFFFFFFF),                 // Pure white card surface
    surfaceVariant = Color(0xFFEDF1FA),          // Subtle tinted container for chips/pills
    primaryText = Color(0xFF172033),             // Deep slate-navy text
    secondaryText = Color(0xFF64748B),           // Balanced slate gray
    border = Color(0xFFE2E8F0),                  // Soft crisp border
    borderLight = Color(0xFFF1F5F9),             // Hairline divider
    accent = Color(0xFF6941C6),                  // Refined deep royal purple accent
    secondaryAccent = Color(0xFF0891B2),         // Refined cyan-teal secondary accent
    selectedSurface = Color(0xFF6941C6).copy(alpha = 0.10f),
    button = Color(0xFF6941C6),
    buttonText = Color.White,
    icon = Color(0xFF172033),
    iconSecondary = Color(0xFF64748B),
    shadow = Color(0x16172033),
    success = Color(0xFF16A34A),
    warning = Color(0xFFD97706),
    error = Color(0xFFDC2626),
    coffeeAccent = Color(0xFFB45309),
    coffeeContainer = Color(0xFFFFF7ED),
    coffeeBorder = Color(0xFFD97706).copy(alpha = 0.40f),
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

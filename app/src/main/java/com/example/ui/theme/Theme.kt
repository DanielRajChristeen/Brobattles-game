package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
fun ChillGamesTheme(
    themePreference: String = "SYSTEM",
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themePreference.uppercase()) {
        "DARK" -> true
        "LIGHT" -> false
        else -> systemDark
    }

    val themeColors = if (isDark) DarkThemeColors else LightThemeColors

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> darkColorScheme(
            primary = themeColors.accent,
            onPrimary = themeColors.buttonText,
            primaryContainer = Color(0xFF4C1D95),
            onPrimaryContainer = Color(0xFFEDE9FE),
            secondary = themeColors.xoAccent,
            onSecondary = Color.White,
            tertiary = themeColors.tugAccent,
            onTertiary = Color.White,
            background = themeColors.background,
            onBackground = themeColors.primaryText,
            surface = themeColors.surface,
            onSurface = themeColors.primaryText,
            surfaceVariant = themeColors.surfaceVariant,
            onSurfaceVariant = themeColors.secondaryText,
            outline = themeColors.border,
            outlineVariant = themeColors.borderLight
        )
        else -> lightColorScheme(
            primary = themeColors.accent,
            onPrimary = themeColors.buttonText,
            primaryContainer = PlayfulPurpleLight,
            onPrimaryContainer = PlayfulPurpleDark,
            secondary = themeColors.xoAccent,
            onSecondary = Color.White,
            tertiary = themeColors.tugAccent,
            onTertiary = Color.White,
            background = themeColors.background,
            onBackground = themeColors.primaryText,
            surface = themeColors.surface,
            onSurface = themeColors.primaryText,
            surfaceVariant = themeColors.surfaceVariant,
            onSurfaceVariant = themeColors.secondaryText,
            outline = themeColors.border,
            outlineVariant = themeColors.borderLight
        )
    }

    CompositionLocalProvider(
        LocalAppThemeColors provides themeColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

// Backward compatibility alias
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    ChillGamesTheme(
        themePreference = if (darkTheme) "DARK" else "LIGHT",
        dynamicColor = dynamicColor,
        content = content
    )
}

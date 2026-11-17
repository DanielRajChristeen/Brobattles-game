package com.example.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
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

    val targetColors = if (isDark) DarkThemeColors else LightThemeColors
    val animSpec = tween<Color>(durationMillis = 220)

    val animBg by animateColorAsState(targetColors.background, animSpec, label = "theme_bg")
    val animSecBg by animateColorAsState(targetColors.secondaryBackground, animSpec, label = "theme_sec_bg")
    val animSurface by animateColorAsState(targetColors.surface, animSpec, label = "theme_surface")
    val animSurfaceVar by animateColorAsState(targetColors.surfaceVariant, animSpec, label = "theme_surface_var")
    val animPrimaryText by animateColorAsState(targetColors.primaryText, animSpec, label = "theme_primary_text")
    val animSecondaryText by animateColorAsState(targetColors.secondaryText, animSpec, label = "theme_secondary_text")
    val animBorder by animateColorAsState(targetColors.border, animSpec, label = "theme_border")
    val animBorderLight by animateColorAsState(targetColors.borderLight, animSpec, label = "theme_border_light")
    val animAccent by animateColorAsState(targetColors.accent, animSpec, label = "theme_accent")
    val animSecAccent by animateColorAsState(targetColors.secondaryAccent, animSpec, label = "theme_sec_accent")

    val themeColors = targetColors.copy(
        background = animBg,
        secondaryBackground = animSecBg,
        surface = animSurface,
        surfaceVariant = animSurfaceVar,
        primaryText = animPrimaryText,
        secondaryText = animSecondaryText,
        border = animBorder,
        borderLight = animBorderLight,
        accent = animAccent,
        secondaryAccent = animSecAccent
    )

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> darkColorScheme(
            primary = themeColors.accent,
            onPrimary = themeColors.buttonText,
            primaryContainer = Color(0xFF2B1E5C),
            onPrimaryContainer = Color(0xFFEDE9FE),
            secondary = themeColors.secondaryAccent,
            onSecondary = Color(0xFF031724),
            secondaryContainer = Color(0xFF0A3248),
            onSecondaryContainer = Color(0xFFCFFAFE),
            tertiary = themeColors.tugAccent,
            onTertiary = Color.White,
            background = themeColors.background,
            onBackground = themeColors.primaryText,
            surface = themeColors.surface,
            onSurface = themeColors.primaryText,
            surfaceVariant = themeColors.surfaceVariant,
            onSurfaceVariant = themeColors.secondaryText,
            surfaceContainerLowest = themeColors.background,
            surfaceContainerLow = themeColors.secondaryBackground,
            surfaceContainer = themeColors.surface,
            surfaceContainerHigh = themeColors.surfaceVariant,
            outline = themeColors.border,
            outlineVariant = themeColors.borderLight,
            error = themeColors.error
        )
        else -> lightColorScheme(
            primary = themeColors.accent,
            onPrimary = themeColors.buttonText,
            primaryContainer = PlayfulPurpleLight,
            onPrimaryContainer = PlayfulPurpleDark,
            secondary = themeColors.secondaryAccent,
            onSecondary = Color.White,
            secondaryContainer = PlayfulCyanLight,
            onSecondaryContainer = Color(0xFF083344),
            tertiary = themeColors.tugAccent,
            onTertiary = Color.White,
            background = themeColors.background,
            onBackground = themeColors.primaryText,
            surface = themeColors.surface,
            onSurface = themeColors.primaryText,
            surfaceVariant = themeColors.surfaceVariant,
            onSurfaceVariant = themeColors.secondaryText,
            surfaceContainerLowest = themeColors.background,
            surfaceContainerLow = themeColors.secondaryBackground,
            surfaceContainer = themeColors.surface,
            surfaceContainerHigh = themeColors.surfaceVariant,
            outline = themeColors.border,
            outlineVariant = themeColors.borderLight,
            error = themeColors.error
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

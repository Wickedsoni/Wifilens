package com.wickedcoder.wifilens.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalNothingColors = staticCompositionLocalOf { NothingDarkColors }

/**
 * Nothing-inspired design system theme. Monochrome is the canvas: no dynamic color, no Material
 * baseline palette — [WifiLensTheme.colors] exposes the raw semantic tokens directly.
 */
object WifiLensTheme {
    val colors: NothingColors
        @Composable get() = LocalNothingColors.current
}

@Composable
fun WifiLensTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) NothingDarkColors else NothingLightColors

    // Material3 components we still rely on (Scaffold, ripple, etc.) read colorScheme, so it is
    // mapped from the same tokens to stay visually consistent with the rest of the screen.
    val materialScheme = if (darkTheme) {
        darkColorScheme(
            background = colors.black,
            surface = colors.surface,
            surfaceVariant = colors.surfaceRaised,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary,
            primary = colors.textDisplay,
            onPrimary = colors.black,
            secondary = colors.textSecondary,
            error = colors.accent,
            outline = colors.borderVisible,
            outlineVariant = colors.border,
        )
    } else {
        lightColorScheme(
            background = colors.black,
            surface = colors.surface,
            surfaceVariant = colors.surfaceRaised,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary,
            primary = colors.textDisplay,
            onPrimary = colors.black,
            secondary = colors.textSecondary,
            error = colors.accent,
            outline = colors.borderVisible,
            outlineVariant = colors.border,
        )
    }

    CompositionLocalProvider(LocalNothingColors provides colors) {
        MaterialTheme(
            colorScheme = materialScheme,
            content = content,
        )
    }
}

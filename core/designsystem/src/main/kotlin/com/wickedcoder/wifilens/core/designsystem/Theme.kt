package com.wickedcoder.wifilens.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalNothingColors = staticCompositionLocalOf { NothingDarkColors }

/**
 * Nothing-inspired design system theme. Monochrome is the canvas. The Material 3 scheme, typography
 * and shapes are all generated from the same tokens ([toMaterialScheme]); [WifiLensTheme.colors]
 * still exposes the raw semantic tokens for signal and heat-map colours.
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

    CompositionLocalProvider(LocalNothingColors provides colors) {
        MaterialTheme(
            colorScheme = colors.toMaterialScheme(),
            typography = NothingMaterialTypography,
            shapes = NothingMaterialShapes,
            content = content,
        )
    }
}

package com.wickedcoder.wifilens.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

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
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) NothingDarkColors else NothingLightColors

    // Wallpaper-derived scheme only for stock Material components; the Nothing tokens (signal and heat-map
    // colours included) keep their fixed meaning either way.
    val context = LocalContext.current
    val materialScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> {
            colors.toMaterialScheme()
        }
    }

    CompositionLocalProvider(LocalNothingColors provides colors) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = NothingMaterialTypography,
            shapes = NothingMaterialShapes,
            content = content,
        )
    }
}

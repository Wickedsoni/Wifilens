package com.wickedcoder.wifilens.core.designsystem

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private const val MEDIUM_CONTRAST = 0.33f
private const val HIGH_CONTRAST = 0.66f

/**
 * WifiLens theme: Material 3 Expressive. Wallpaper-based dynamic colour is on by default (API 31+). Otherwise the
 * brand scheme is used, matched to the system contrast setting (API 34+). Motion uses the expressive spring
 * scheme. Signal colours (`success`, `warning`, `danger` in Color.kt) are fixed and never follow the wallpaper.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WifiLensTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> {
            brandScheme(darkTheme, systemContrast(context))
        }
    }
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = WifiLensTypography,
        content = content,
    )
}

private fun brandScheme(darkTheme: Boolean, contrast: Float): ColorScheme = when {
    contrast >= HIGH_CONTRAST -> if (darkTheme) brandDarkHighContrastScheme else brandLightHighContrastScheme
    contrast >= MEDIUM_CONTRAST -> if (darkTheme) brandDarkMediumContrastScheme else brandLightMediumContrastScheme
    else -> if (darkTheme) brandDarkScheme else brandLightScheme
}

/** System contrast level in -1..1 (API 34+); 0 (standard) on older releases. */
private fun systemContrast(context: Context): Float =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        context.getSystemService(UiModeManager::class.java)?.contrast ?: 0f
    } else {
        0f
    }

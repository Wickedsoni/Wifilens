package com.wickedcoder.wifilens.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * Token values from the Nothing-inspired design system (references/tokens.md).
 * Identical across modes: accent red, status colors, interactive-dark differs from interactive-light.
 */
object NothingColorTokens {

    // Dark mode
    val blackDark = Color(0xFF000000)
    val surfaceDark = Color(0xFF111111)
    val surfaceRaisedDark = Color(0xFF1A1A1A)
    val borderDark = Color(0xFF222222)
    val borderVisibleDark = Color(0xFF333333)
    val textDisabledDark = Color(0xFF666666)
    val textSecondaryDark = Color(0xFF999999)
    val textPrimaryDark = Color(0xFFE8E8E8)
    val textDisplayDark = Color(0xFFFFFFFF)
    val interactiveDark = Color(0xFF5B9BF6)

    // Light mode
    val blackLight = Color(0xFFF5F5F5)
    val surfaceLight = Color(0xFFFFFFFF)
    val surfaceRaisedLight = Color(0xFFF0F0F0)
    val borderLight = Color(0xFFE8E8E8)
    val borderVisibleLight = Color(0xFFCCCCCC)
    val textDisabledLight = Color(0xFF999999)
    val textSecondaryLight = Color(0xFF666666)
    val textPrimaryLight = Color(0xFF1A1A1A)
    val textDisplayLight = Color(0xFF000000)
    val interactiveLight = Color(0xFF007AFF)

    // Identical across modes
    val accent = Color(0xFFD71921)
    val accentSubtle = Color(0x26D71921) // rgba(215,25,33,0.15)
    val success = Color(0xFF4A9E5C)
    val warning = Color(0xFFD4A843)
    val error = accent
}

/**
 * Full set of semantic tokens for one mode. Read via [LocalNothingColors] / `WifiLensTheme.colors`.
 */
data class NothingColors(
    val black: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val border: Color,
    val borderVisible: Color,
    val textDisabled: Color,
    val textSecondary: Color,
    val textPrimary: Color,
    val textDisplay: Color,
    val interactive: Color,
    val accent: Color = NothingColorTokens.accent,
    val accentSubtle: Color = NothingColorTokens.accentSubtle,
    val success: Color = NothingColorTokens.success,
    val warning: Color = NothingColorTokens.warning,
    val error: Color = NothingColorTokens.error,
    /** True for the dark palette; canvases that pick their own tints (room fills) branch on it. */
    val isDark: Boolean = true,
)

val NothingDarkColors = NothingColors(
    black = NothingColorTokens.blackDark,
    surface = NothingColorTokens.surfaceDark,
    surfaceRaised = NothingColorTokens.surfaceRaisedDark,
    border = NothingColorTokens.borderDark,
    borderVisible = NothingColorTokens.borderVisibleDark,
    textDisabled = NothingColorTokens.textDisabledDark,
    textSecondary = NothingColorTokens.textSecondaryDark,
    textPrimary = NothingColorTokens.textPrimaryDark,
    textDisplay = NothingColorTokens.textDisplayDark,
    interactive = NothingColorTokens.interactiveDark,
)

val NothingLightColors = NothingColors(
    black = NothingColorTokens.blackLight,
    surface = NothingColorTokens.surfaceLight,
    surfaceRaised = NothingColorTokens.surfaceRaisedLight,
    border = NothingColorTokens.borderLight,
    borderVisible = NothingColorTokens.borderVisibleLight,
    textDisabled = NothingColorTokens.textDisabledLight,
    textSecondary = NothingColorTokens.textSecondaryLight,
    textPrimary = NothingColorTokens.textPrimaryLight,
    textDisplay = NothingColorTokens.textDisplayLight,
    interactive = NothingColorTokens.interactiveLight,
    isDark = false,
)

/** Data status color: green = good, amber = moderate, red = over limit, neutral = in range. */
enum class SignalStatus { Good, Moderate, Poor }

fun NothingColors.forSignalStatus(status: SignalStatus): Color = when (status) {
    SignalStatus.Good -> success
    SignalStatus.Moderate -> warning
    SignalStatus.Poor -> accent
}

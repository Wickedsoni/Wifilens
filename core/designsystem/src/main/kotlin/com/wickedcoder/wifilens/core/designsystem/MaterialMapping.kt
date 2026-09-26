package com.wickedcoder.wifilens.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Darker blue for text-bearing tertiary roles on light surfaces: the interactive token is only ~3.5:1 there. */
private val TertiaryOnLight = Color(0xFF0062CC)

/**
 * Maps the Nothing tokens onto every Material 3 colour role, so stock M3 components (NavigationBar,
 * Switch, chips, Snackbar...) come out looking like the rest of the app without per-call styling.
 * Signal and heat-map colours are NOT part of this: they stay in [NothingColors] so their meaning
 * never follows a wallpaper or a scheme.
 */
fun NothingColors.toMaterialScheme(): ColorScheme = if (isDark) {
    darkColorScheme(
        primary = textDisplay,
        onPrimary = black,
        primaryContainer = surfaceRaised,
        onPrimaryContainer = textDisplay,
        inversePrimary = black,
        secondary = textSecondary,
        onSecondary = black,
        secondaryContainer = surfaceRaised,
        onSecondaryContainer = textDisplay,
        tertiary = interactive,
        onTertiary = black,
        tertiaryContainer = surfaceRaised,
        onTertiaryContainer = interactive,
        background = black,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceRaised,
        onSurfaceVariant = textSecondary,
        surfaceTint = textDisplay,
        inverseSurface = textDisplay,
        inverseOnSurface = black,
        error = accent,
        onError = black,
        errorContainer = surfaceRaised,
        onErrorContainer = accent,
        outline = borderVisible,
        outlineVariant = border,
        scrim = Color.Black,
        surfaceBright = surfaceRaised,
        surfaceDim = black,
        surfaceContainerLowest = black,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceRaised,
        surfaceContainerHighest = surfaceRaised,
    )
} else {
    lightColorScheme(
        primary = textDisplay,
        onPrimary = surface,
        primaryContainer = surfaceRaised,
        onPrimaryContainer = textDisplay,
        inversePrimary = surface,
        secondary = textSecondary,
        onSecondary = surface,
        secondaryContainer = surfaceRaised,
        onSecondaryContainer = textDisplay,
        tertiary = TertiaryOnLight,
        onTertiary = surface,
        tertiaryContainer = surfaceRaised,
        onTertiaryContainer = TertiaryOnLight,
        background = black,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceRaised,
        onSurfaceVariant = textSecondary,
        surfaceTint = textDisplay,
        inverseSurface = textDisplay,
        inverseOnSurface = surface,
        error = accent,
        onError = Color.White,
        errorContainer = surfaceRaised,
        onErrorContainer = accent,
        outline = borderVisible,
        outlineVariant = border,
        scrim = Color.Black,
        surfaceBright = surface,
        surfaceDim = black,
        surfaceContainerLowest = surface,
        surfaceContainerLow = black,
        surfaceContainer = surfaceRaised,
        surfaceContainerHigh = surfaceRaised,
        surfaceContainerHighest = border,
    )
}

/** Display -> Doto, headline/title/body -> Space Grotesk, label -> Space Mono (the app's type scale). */
val NothingMaterialTypography = Typography(
    displayLarge = NothingType.displayXl,
    displayMedium = NothingType.displayLg,
    displaySmall = NothingType.displayMd,
    headlineLarge = NothingType.heading,
    headlineMedium = NothingType.heading,
    headlineSmall = NothingType.subheading,
    titleLarge = NothingType.subheading,
    titleMedium = NothingType.body,
    titleSmall = NothingType.bodySmall,
    bodyLarge = NothingType.body,
    bodyMedium = NothingType.bodySmall,
    bodySmall = NothingType.caption,
    labelLarge = NothingType.label,
    labelMedium = NothingType.label,
    labelSmall = NothingType.label,
)

/** Shape scale matching the corner radii the custom components already use. */
val NothingMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

package com.wickedcoder.wifilens.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.wickedcoder.wifilens.core.model.FAIR_SIGNAL_DBM
import com.wickedcoder.wifilens.core.model.GOOD_SIGNAL_DBM

// Brand fallback schemes, used when dynamic colour is off or unavailable (API < 31). Generated with
// Material Color Utilities (SchemeFidelity, seed #2F6FDE "WifiLens blue") at standard, medium and high
// contrast, so the fallback honours the system contrast setting. Regenerate with the same tool rather than
// editing individual roles by hand.

val brandLightScheme = lightColorScheme(
    primary = Color(0xFF0055C1),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF2F6FDE),
    onPrimaryContainer = Color(0xFFFCFAFF),
    inversePrimary = Color(0xFFAFC6FF),
    secondary = Color(0xFF4B5E89),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFBCCEFF),
    onSecondaryContainer = Color(0xFF455782),
    tertiary = Color(0xFF944400),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBA5700),
    onTertiaryContainer = Color(0xFFFFFAF8),
    background = Color(0xFFFAF8FF),
    onBackground = Color(0xFF191B22),
    surface = Color(0xFFFAF8FF),
    onSurface = Color(0xFF191B22),
    surfaceVariant = Color(0xFFDEE2F2),
    onSurfaceVariant = Color(0xFF424753),
    surfaceTint = Color(0xFF0159C7),
    inverseSurface = Color(0xFF2E3038),
    inverseOnSurface = Color(0xFFEFF0FA),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF737785),
    outlineVariant = Color(0xFFC2C6D5),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFAF8FF),
    surfaceContainer = Color(0xFFEDEDF7),
    surfaceContainerHigh = Color(0xFFE7E7F1),
    surfaceContainerHighest = Color(0xFFE1E2EC),
    surfaceContainerLow = Color(0xFFF2F3FD),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFD9D9E3),
)

val brandDarkScheme = darkColorScheme(
    primary = Color(0xFFAFC6FF),
    onPrimary = Color(0xFF002D6D),
    primaryContainer = Color(0xFF2F6FDE),
    onPrimaryContainer = Color(0xFFFCFAFF),
    inversePrimary = Color(0xFF0159C7),
    secondary = Color(0xFFB3C6F7),
    onSecondary = Color(0xFF1B2F57),
    secondaryContainer = Color(0xFF354872),
    onSecondaryContainer = Color(0xFFA5B8E8),
    tertiary = Color(0xFFFFB68B),
    onTertiary = Color(0xFF522300),
    tertiaryContainer = Color(0xFFBA5700),
    onTertiaryContainer = Color(0xFFFFFAF8),
    background = Color(0xFF11131A),
    onBackground = Color(0xFFE1E2EC),
    surface = Color(0xFF11131A),
    onSurface = Color(0xFFE1E2EC),
    surfaceVariant = Color(0xFF424753),
    onSurfaceVariant = Color(0xFFC2C6D5),
    surfaceTint = Color(0xFFAFC6FF),
    inverseSurface = Color(0xFFE1E2EC),
    inverseOnSurface = Color(0xFF2E3038),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8C909F),
    outlineVariant = Color(0xFF424753),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF373940),
    surfaceContainer = Color(0xFF1D1F26),
    surfaceContainerHigh = Color(0xFF272A31),
    surfaceContainerHighest = Color(0xFF32353C),
    surfaceContainerLow = Color(0xFF191B22),
    surfaceContainerLowest = Color(0xFF0C0E15),
    surfaceDim = Color(0xFF11131A),
)

val brandLightMediumContrastScheme = lightColorScheme(
    primary = Color(0xFF003378),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF2568D7),
    onPrimaryContainer = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFFAFC6FF),
    secondary = Color(0xFF22355E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF5A6C98),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFF5B2700),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFB05200),
    onTertiaryContainer = Color(0xFFFFFFFF),
    background = Color(0xFFFAF8FF),
    onBackground = Color(0xFF191B22),
    surface = Color(0xFFFAF8FF),
    onSurface = Color(0xFF0F1118),
    surfaceVariant = Color(0xFFDEE2F2),
    onSurfaceVariant = Color(0xFF323642),
    surfaceTint = Color(0xFF0159C7),
    inverseSurface = Color(0xFF2E3038),
    inverseOnSurface = Color(0xFFEFF0FA),
    error = Color(0xFF740006),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFCF2C27),
    onErrorContainer = Color(0xFFFFFFFF),
    outline = Color(0xFF4E525F),
    outlineVariant = Color(0xFF686D7A),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFAF8FF),
    surfaceContainer = Color(0xFFE7E7F1),
    surfaceContainerHigh = Color(0xFFDBDCE6),
    surfaceContainerHighest = Color(0xFFD0D1DB),
    surfaceContainerLow = Color(0xFFF2F3FD),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFC5C6CF),
)

val brandDarkMediumContrastScheme = darkColorScheme(
    primary = Color(0xFFD0DCFF),
    onPrimary = Color(0xFF002358),
    primaryContainer = Color(0xFF548DFE),
    onPrimaryContainer = Color(0xFF000000),
    inversePrimary = Color(0xFF00439B),
    secondary = Color(0xFFD0DCFF),
    onSecondary = Color(0xFF0F244C),
    secondaryContainer = Color(0xFF7D90BE),
    onSecondaryContainer = Color(0xFF000000),
    tertiary = Color(0xFFFFD3BC),
    onTertiary = Color(0xFF421A00),
    tertiaryContainer = Color(0xFFDF7324),
    onTertiaryContainer = Color(0xFF000000),
    background = Color(0xFF11131A),
    onBackground = Color(0xFFE1E2EC),
    surface = Color(0xFF11131A),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF424753),
    onSurfaceVariant = Color(0xFFD8DCEC),
    surfaceTint = Color(0xFFAFC6FF),
    inverseSurface = Color(0xFFE1E2EC),
    inverseOnSurface = Color(0xFF272A31),
    error = Color(0xFFFFD2CC),
    onError = Color(0xFF540003),
    errorContainer = Color(0xFFFF5449),
    onErrorContainer = Color(0xFF000000),
    outline = Color(0xFFAEB1C1),
    outlineVariant = Color(0xFF8C909E),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF42444C),
    surfaceContainer = Color(0xFF25282F),
    surfaceContainerHigh = Color(0xFF30323A),
    surfaceContainerHighest = Color(0xFF3B3D45),
    surfaceContainerLow = Color(0xFF1B1D24),
    surfaceContainerLowest = Color(0xFF05070D),
    surfaceDim = Color(0xFF11131A),
)

val brandLightHighContrastScheme = lightColorScheme(
    primary = Color(0xFF002964),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF00459E),
    onPrimaryContainer = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFFAFC6FF),
    secondary = Color(0xFF172B53),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF364872),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFF4C1F00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF783600),
    onTertiaryContainer = Color(0xFFFFFFFF),
    background = Color(0xFFFAF8FF),
    onBackground = Color(0xFF191B22),
    surface = Color(0xFFFAF8FF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFDEE2F2),
    onSurfaceVariant = Color(0xFF000000),
    surfaceTint = Color(0xFF0159C7),
    inverseSurface = Color(0xFF2E3038),
    inverseOnSurface = Color(0xFFFFFFFF),
    error = Color(0xFF600004),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF98000A),
    onErrorContainer = Color(0xFFFFFFFF),
    outline = Color(0xFF272C38),
    outlineVariant = Color(0xFF454956),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFAF8FF),
    surfaceContainer = Color(0xFFE1E2EC),
    surfaceContainerHigh = Color(0xFFD3D4DD),
    surfaceContainerHighest = Color(0xFFC5C6CF),
    surfaceContainerLow = Color(0xFFEFF0FA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFB7B8C1),
)

val brandDarkHighContrastScheme = darkColorScheme(
    primary = Color(0xFFECEFFF),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFFA9C2FF),
    onPrimaryContainer = Color(0xFF000A24),
    inversePrimary = Color(0xFF00439B),
    secondary = Color(0xFFECEFFF),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFFAFC2F3),
    onSecondaryContainer = Color(0xFF000A24),
    tertiary = Color(0xFFFFECE3),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFFFFB182),
    onTertiaryContainer = Color(0xFF190600),
    background = Color(0xFF11131A),
    onBackground = Color(0xFFE1E2EC),
    surface = Color(0xFF11131A),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF424753),
    onSurfaceVariant = Color(0xFFFFFFFF),
    surfaceTint = Color(0xFFAFC6FF),
    inverseSurface = Color(0xFFE1E2EC),
    inverseOnSurface = Color(0xFF000000),
    error = Color(0xFFFFECE9),
    onError = Color(0xFF000000),
    errorContainer = Color(0xFFFFAEA4),
    onErrorContainer = Color(0xFF220001),
    outline = Color(0xFFECEFFF),
    outlineVariant = Color(0xFFBEC2D2),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF4E5058),
    surfaceContainer = Color(0xFF2E3038),
    surfaceContainerHigh = Color(0xFF393B43),
    surfaceContainerHighest = Color(0xFF44464E),
    surfaceContainerLow = Color(0xFF1D1F26),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceDim = Color(0xFF11131A),
)

/** True for dark schemes (dynamic or brand). Pure function of the scheme, so it also works inside `DrawScope`. */
val ColorScheme.isDark: Boolean
    get() = background.luminance() < 0.5f

// Signal colours carry meaning (good/moderate/poor) and must NOT follow the wallpaper, so they are fixed per
// mode instead of being scheme roles. Each value meets WCAG AA (4.5:1) as text on every surface container of its
// mode (HCT tone 34 light / 74 dark, computed against all six brand schemes); ContrastTest guards that.

/** Good signal / success. */
val ColorScheme.success: Color
    get() = if (isDark) Color(0xFF81C779) else Color(0xFF185C1C)

/** Moderate signal / warning. */
val ColorScheme.warning: Color
    get() = if (isDark) Color(0xFFE6AD17) else Color(0xFF684B00)

/** Poor signal / destructive. Distinct from [ColorScheme.error] so it never shifts with dynamic colour. */
val ColorScheme.danger: Color
    get() = if (isDark) Color(0xFFFF9B90) else Color(0xFF9B1F1B)

/** At or above: good signal (reliable video calls). The domain's [GOOD_SIGNAL_DBM], for drawing code. */
const val GOOD_RSSI_DBM = GOOD_SIGNAL_DBM

/** At or above (and below [GOOD_RSSI_DBM]): fair signal. Below: poor. */
const val FAIR_RSSI_DBM = FAIR_SIGNAL_DBM

/** Colour for a measured or predicted RSSI; every screen uses this scale so the colours always agree. */
fun ColorScheme.signalColor(rssi: Float): Color = when {
    rssi >= GOOD_RSSI_DBM -> success
    rssi >= FAIR_RSSI_DBM -> warning
    else -> danger
}

/** Signal quality buckets shared by Analyze and Diagnose. */
enum class SignalStatus { Good, Moderate, Poor }

fun ColorScheme.forSignalStatus(status: SignalStatus): Color = when (status) {
    SignalStatus.Good -> success
    SignalStatus.Moderate -> warning
    SignalStatus.Poor -> danger
}

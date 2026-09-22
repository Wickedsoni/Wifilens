package com.wickedcoder.wifilens.core.designsystem

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Font stack (references/tokens.md Section 1): Doto (display), Space Grotesk (body/UI), Space
 * Mono (data/labels), all from Google Fonts.
 *
 * Space Mono ships as separate static Regular/Bold files upstream. Space Grotesk and Doto are
 * published as variable fonts only (no static Medium file exists for Space Grotesk) — the
 * Light/Medium instances below are the *same* bundled file with a weight axis override via
 * [FontVariation], not separate resources.
 */
object NothingFonts {
    val display: FontFamily = FontFamily(Font(R.font.doto_regular))

    val body: FontFamily = FontFamily(
        Font(
            R.font.space_grotesk_regular,
            weight = FontWeight.Light,
            variationSettings = FontVariation.Settings(FontVariation.weight(300)),
        ),
        Font(R.font.space_grotesk_regular, weight = FontWeight.Normal),
        Font(
            R.font.space_grotesk_regular,
            weight = FontWeight.Medium,
            variationSettings = FontVariation.Settings(FontVariation.weight(500)),
        ),
    )

    val mono: FontFamily = FontFamily(
        Font(R.font.space_mono_regular, weight = FontWeight.Normal),
        Font(R.font.space_mono_bold, weight = FontWeight.Bold),
    )
}

/** Type scale (references/tokens.md Section 1). */
object NothingType {
    val displayXl = TextStyle(
        fontFamily = NothingFonts.display,
        fontWeight = FontWeight.Normal,
        fontSize = 72.sp,
        lineHeight = 72.sp,
        letterSpacing = (-0.03).em,
    )
    val displayLg = TextStyle(
        fontFamily = NothingFonts.display,
        fontWeight = FontWeight.Normal,
        fontSize = 48.sp,
        lineHeight = 50.sp,
        letterSpacing = (-0.02).em,
    )
    val displayMd = TextStyle(
        fontFamily = NothingFonts.display,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.02).em,
    )
    val heading = TextStyle(
        fontFamily = NothingFonts.body,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 29.sp,
        letterSpacing = (-0.01).em,
    )
    val subheading = TextStyle(
        fontFamily = NothingFonts.body,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 23.sp,
    )
    val body = TextStyle(
        fontFamily = NothingFonts.body,
        fontWeight = FontWeight.Light,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    )
    val bodySmall = TextStyle(
        fontFamily = NothingFonts.body,
        fontWeight = FontWeight.Light,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.01.em,
    )
    val caption = TextStyle(
        fontFamily = NothingFonts.mono,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.04.em,
    )

    /** Always Space Mono, ALL CAPS at the call site — 0.06-0.1em tracking, "instrument panel" labels. */
    val label = TextStyle(
        fontFamily = NothingFonts.mono,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.08.em,
    )
}

package com.wickedcoder.wifilens.core.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** WCAG 2.x contrast guard: every text colour must reach AA (4.5:1) on the surfaces it is drawn on. */
class ContrastTest {

    private fun channel(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(c: Color): Double =
        0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = luminance(a).let { la -> luminance(b).let { lb -> maxOf(la, lb) to minOf(la, lb) } }
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun check(name: String, colors: NothingColors) {
        val backgrounds = mapOf("background" to colors.black, "surface" to colors.surface, "raised" to colors.surfaceRaised)
        val texts = mapOf(
            "textPrimary" to colors.textPrimary,
            "textSecondary" to colors.textSecondary,
            "textDisabled" to colors.textDisabled,
            "accent" to colors.accent,
            "success" to colors.success,
            "warning" to colors.warning,
        )
        val failures = buildList {
            texts.forEach { (textName, text) ->
                backgrounds.forEach { (bgName, bg) ->
                    val ratio = contrast(text, bg)
                    if (ratio < 4.5) add("$name $textName on $bgName = ${"%.2f".format(ratio)}")
                }
            }
        }
        assertTrue("Below AA: $failures", failures.isEmpty())
    }

    @Test
    fun darkThemeTextMeetsAA() = check("dark", NothingDarkColors)

    @Test
    fun lightThemeTextMeetsAA() = check("light", NothingLightColors)

    @Test
    fun disabledIsStillDistinctFromSecondary() {
        assertTrue(NothingDarkColors.textDisabled != NothingDarkColors.textSecondary)
        assertTrue(NothingLightColors.textDisabled != NothingLightColors.textSecondary)
    }
}

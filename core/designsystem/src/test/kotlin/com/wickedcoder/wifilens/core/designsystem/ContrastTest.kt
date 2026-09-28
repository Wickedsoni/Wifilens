package com.wickedcoder.wifilens.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * WCAG 2.x contrast guard (AA, 4.5:1) for the brand fallback schemes and the fixed signal colours. Dynamic
 * schemes are tone-based and Material guarantees their on-/container pairs, so this covers what we own.
 */
class ContrastTest {
    private fun channel(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(c: Color): Double =
        0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private val schemes = mapOf(
        "light" to brandLightScheme,
        "dark" to brandDarkScheme,
        "light-medium" to brandLightMediumContrastScheme,
        "dark-medium" to brandDarkMediumContrastScheme,
        "light-high" to brandLightHighContrastScheme,
        "dark-high" to brandDarkHighContrastScheme,
    )

    private fun surfaces(s: ColorScheme) = mapOf(
        "surface" to s.surface,
        "containerLow" to s.surfaceContainerLow,
        "container" to s.surfaceContainer,
        "containerHigh" to s.surfaceContainerHigh,
        "containerHighest" to s.surfaceContainerHighest,
    )

    private fun assertAll(failures: List<String>) = assertTrue(failures.joinToString("\n"), failures.isEmpty())

    @Test
    fun `text roles reach AA on every surface in every brand scheme`() = assertAll(
        buildList {
            schemes.forEach { (name, s) ->
                surfaces(s).forEach { (bgName, bg) ->
                    listOf("onSurface" to s.onSurface, "onSurfaceVariant" to s.onSurfaceVariant).forEach { (fgName, fg) ->
                        val r = contrast(fg, bg)
                        if (r < 4.5) add("$name $fgName on $bgName = ${"%.2f".format(r)}")
                    }
                }
            }
        },
    )

    @Test
    fun `on-colour pairs reach AA in every brand scheme`() = assertAll(
        buildList {
            schemes.forEach { (name, s) ->
                listOf(
                    "primary" to (s.onPrimary to s.primary),
                    "primaryContainer" to (s.onPrimaryContainer to s.primaryContainer),
                    "secondaryContainer" to (s.onSecondaryContainer to s.secondaryContainer),
                    "tertiaryContainer" to (s.onTertiaryContainer to s.tertiaryContainer),
                    "error" to (s.onError to s.error),
                    "errorContainer" to (s.onErrorContainer to s.errorContainer),
                ).forEach { (pair, colors) ->
                    val r = contrast(colors.first, colors.second)
                    if (r < 4.5) add("$name on-$pair = ${"%.2f".format(r)}")
                }
            }
        },
    )

    @Test
    fun `fixed signal colours reach AA on every surface`() = assertAll(
        buildList {
            schemes.forEach { (name, s) ->
                surfaces(s).forEach { (bgName, bg) ->
                    listOf("success" to s.success, "warning" to s.warning, "danger" to s.danger).forEach { (fgName, fg) ->
                        val r = contrast(fg, bg)
                        if (r < 4.5) add("$name $fgName on $bgName = ${"%.2f".format(r)}")
                    }
                }
            }
        },
    )

    @Test
    fun `isDark follows the scheme`() {
        assertTrue(brandDarkScheme.isDark)
        assertTrue(!brandLightScheme.isDark)
    }
}

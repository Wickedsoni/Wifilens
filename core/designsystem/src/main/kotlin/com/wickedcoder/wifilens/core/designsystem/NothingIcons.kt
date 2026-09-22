package com.wickedcoder.wifilens.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * 24x24-viewport glyphs drawn straight from path data. material-icons-extended is ~30MB of
 * dependency for three arrows, so the few icons the app needs live here instead.
 */
enum class NothingIcon(internal val pathData: String) {
    Undo(
        "M12.5,8c-2.65,0 -5.05,0.99 -6.9,2.6L2,7v9h9l-3.62,-3.62c1.39,-1.16 3.16,-1.88 5.12,-1.88 " +
            "3.54,0 6.55,2.31 7.6,5.5l2.37,-0.78C21.08,11.03 17.15,8 12.5,8z",
    ),
    Redo(
        "M18.4,10.6C16.55,8.99 14.15,8 11.5,8c-4.65,0 -8.58,3.03 -9.96,7.22L3.9,16c1.05,-3.19 4.05,-5.5 " +
            "7.6,-5.5 1.95,0 3.73,0.72 5.12,1.88L13,16h9V7l-3.6,3.6z",
    ),
    Refresh(
        "M17.65,6.35C16.2,4.9 14.21,4 12,4c-4.42,0 -7.99,3.58 -7.99,8s3.57,8 7.99,8c3.73,0 6.84,-2.55 " +
            "7.73,-6h-2.08c-0.82,2.33 -3.04,4 -5.65,4 -3.31,0 -6,-2.69 -6,-6s2.69,-6 6,-6c1.66,0 3.14,0.69 " +
            "4.22,1.78L13,11h7V4l-2.35,2.35z",
    ),
}

/** Bottom-nav glyphs, drawn from primitives on a 24-unit grid (2-unit round strokes). */
enum class NothingNavIcon { Analyze, Map, Diagnose, More }

@Composable
fun NothingNavGlyph(icon: NothingNavIcon, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val u = size.width / 24f
        val line = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (icon) {
            // Wi-Fi: three arcs opening upward from a dot (the same mark as the app icon).
            NothingNavIcon.Analyze -> {
                listOf(4f, 8f, 12f).forEach { r ->
                    drawArc(
                        color = tint,
                        startAngle = 210f,
                        sweepAngle = 120f,
                        useCenter = false,
                        topLeft = Offset((12f - r) * u, (18f - r) * u),
                        size = Size(2f * r * u, 2f * r * u),
                        style = line,
                    )
                }
                drawCircle(tint, radius = 1.8f * u, center = Offset(12f * u, 18f * u))
            }

            // Folded map: outline with two fold lines.
            NothingNavIcon.Map -> {
                val outline = Path().apply {
                    moveTo(3f * u, 6f * u)
                    lineTo(9f * u, 4f * u)
                    lineTo(15f * u, 6f * u)
                    lineTo(21f * u, 4f * u)
                    lineTo(21f * u, 18f * u)
                    lineTo(15f * u, 20f * u)
                    lineTo(9f * u, 18f * u)
                    lineTo(3f * u, 20f * u)
                    close()
                }
                drawPath(outline, tint, style = line)
                drawLine(tint, Offset(9f * u, 4f * u), Offset(9f * u, 18f * u), strokeWidth = 2f * u, cap = StrokeCap.Round)
                drawLine(tint, Offset(15f * u, 6f * u), Offset(15f * u, 20f * u), strokeWidth = 2f * u, cap = StrokeCap.Round)
            }

            // Crosshair: ring, centre dot, four ticks.
            NothingNavIcon.Diagnose -> {
                drawCircle(tint, radius = 7f * u, center = Offset(12f * u, 12f * u), style = line)
                drawCircle(tint, radius = 1.8f * u, center = Offset(12f * u, 12f * u))
                listOf(
                    Offset(12f, 2f) to Offset(12f, 5f),
                    Offset(12f, 19f) to Offset(12f, 22f),
                    Offset(2f, 12f) to Offset(5f, 12f),
                    Offset(19f, 12f) to Offset(22f, 12f),
                ).forEach { (a, b) ->
                    drawLine(tint, Offset(a.x * u, a.y * u), Offset(b.x * u, b.y * u), strokeWidth = 2f * u, cap = StrokeCap.Round)
                }
            }

            NothingNavIcon.More -> listOf(5f, 12f, 19f).forEach { x ->
                drawCircle(tint, radius = 1.9f * u, center = Offset(x * u, 12f * u))
            }
        }
    }
}

/** 40dp touch target; disabled = alpha 0.4 and not clickable. */
@Composable
fun NothingIconButton(
    icon: NothingIcon,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = WifiLensTheme.colors.textPrimary,
) {
    val path = remember(icon) { PathParser().parsePathString(icon.pathData).toPath() }
    Box(
        modifier = modifier
            .size(40.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .semantics { this.contentDescription = contentDescription }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(24.dp)) {
            scale(scale = size.width / 24f, pivot = Offset.Zero) {
                drawPath(path, color = tint)
            }
        }
    }
}

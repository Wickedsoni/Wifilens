package com.wickedcoder.wifilens.core.designsystem

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntOffset

private const val SLIDE_MILLIS = 300

/**
 * Screen-change motion (user decision after B-35): no fades anywhere. Tabs switch instantly, and child screens
 * slide side by side at full width, so the two screens never overlap or ghost. The system animator-duration
 * scale (including "Remove animations") is honoured automatically by Compose.
 */
object WifiLensTransitions {
    /** Top-level tab switches: instant. */
    val none: EnterTransition = EnterTransition.None
    val noneExit: ExitTransition = ExitTransition.None

    private val spec = tween<IntOffset>(SLIDE_MILLIS, easing = FastOutSlowInEasing)

    /** Forward (open a child): the new screen comes in from the right... */
    val pushEnter: EnterTransition = slideInHorizontally(spec) { fullWidth -> fullWidth }

    /** ...pushing the current one out to the left. */
    val pushExit: ExitTransition = slideOutHorizontally(spec) { fullWidth -> -fullWidth }

    /** Back: the parent comes back in from the left... */
    val popEnter: EnterTransition = slideInHorizontally(spec) { fullWidth -> -fullWidth }

    /** ...while the child leaves to the right. */
    val popExit: ExitTransition = slideOutHorizontally(spec) { fullWidth -> fullWidth }
}

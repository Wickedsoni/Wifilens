package com.wickedcoder.wifilens.core.designsystem

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

private const val FADE_OUT_MILLIS = 90
private const val FADE_IN_MILLIS = 210
private const val AXIS_MILLIS = 300
private const val FADE_THROUGH_INITIAL_SCALE = 0.92f
private const val AXIS_OFFSET_DIVISOR = 10 // slide by 10% of the width, the Material shared-axis distance

/**
 * Material motion patterns for screen changes. In both, the outgoing screen is gone (90 ms) before the incoming one
 * fades in. That avoids the double-exposure of Navigation Compose's default 700 ms cross-fade (B-35). The system
 * animator-duration scale (including "Remove animations") is honoured automatically by Compose.
 */
object WifiLensTransitions {
    /** Fade through: switching between top-level tabs (unrelated destinations). */
    val fadeThroughEnter: EnterTransition =
        fadeIn(tween(FADE_IN_MILLIS, delayMillis = FADE_OUT_MILLIS, easing = LinearOutSlowInEasing)) +
            scaleIn(
                animationSpec = tween(FADE_IN_MILLIS, delayMillis = FADE_OUT_MILLIS, easing = LinearOutSlowInEasing),
                initialScale = FADE_THROUGH_INITIAL_SCALE,
            )

    val fadeThroughExit: ExitTransition = fadeOut(tween(FADE_OUT_MILLIS, easing = FastOutLinearInEasing))

    /** Shared axis X, forward: opening a child screen (More then Settings). */
    val sharedAxisForwardEnter: EnterTransition =
        slideInHorizontally(tween(AXIS_MILLIS, easing = FastOutSlowInEasing)) { it / AXIS_OFFSET_DIVISOR } +
            fadeIn(tween(FADE_IN_MILLIS, delayMillis = FADE_OUT_MILLIS, easing = LinearOutSlowInEasing))

    val sharedAxisForwardExit: ExitTransition =
        slideOutHorizontally(tween(AXIS_MILLIS, easing = FastOutSlowInEasing)) { -it / AXIS_OFFSET_DIVISOR } +
            fadeOut(tween(FADE_OUT_MILLIS, easing = FastOutLinearInEasing))

    /** Shared axis X, back: returning to the parent (the same motion, reversed). */
    val sharedAxisBackEnter: EnterTransition =
        slideInHorizontally(tween(AXIS_MILLIS, easing = FastOutSlowInEasing)) { -it / AXIS_OFFSET_DIVISOR } +
            fadeIn(tween(FADE_IN_MILLIS, delayMillis = FADE_OUT_MILLIS, easing = LinearOutSlowInEasing))

    val sharedAxisBackExit: ExitTransition =
        slideOutHorizontally(tween(AXIS_MILLIS, easing = FastOutSlowInEasing)) { it / AXIS_OFFSET_DIVISOR } +
            fadeOut(tween(FADE_OUT_MILLIS, easing = FastOutLinearInEasing))
}

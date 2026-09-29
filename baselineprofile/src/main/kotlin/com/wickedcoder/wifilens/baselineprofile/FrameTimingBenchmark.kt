package com.wickedcoder.wifilens.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame timing on the two most animated screens, with the Baseline Profile. Target (ROADMAP Sprint 12): fewer than
 * 5% of frames over their deadline (frameOverrunMs > 0).
 */
@RunWith(AndroidJUnit4::class)
class FrameTimingBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun networkListScroll() = frames(setup = { openNetworks() }) { flingNetworks() }

    @Test
    fun mapZoom() = frames(setup = { openMap3d() }) { pinchMap() }

    /** Only [gesture]'s frames are measured; navigating to the screen happens in [setup]. */
    private fun frames(
        setup: MacrobenchmarkScope.() -> Unit,
        gesture: MacrobenchmarkScope.() -> Unit,
    ) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        startupMode = StartupMode.WARM,
        iterations = ITERATIONS,
        setupBlock = {
            grantLocation()
            pressHome()
            startActivityAndWait()
            awaitHome()
            setup()
        },
    ) {
        gesture()
    }

    private companion object {
        const val ITERATIONS = 5
    }
}

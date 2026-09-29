package com.wickedcoder.wifilens.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

/**
 * The user journeys the profile and the benchmarks share. Each Gradle run installs WifiLens fresh and uninstalls it
 * afterwards (AGP's connected-test lifecycle), so the journeys set up what they need, such as an empty plan.
 */
internal const val TARGET_PACKAGE = "com.wickedcoder.wifilens"

private const val WAIT_MS = 5_000L

/** Fraction of the width kept clear on each side of a fling, away from the system back-gesture edges. */
private const val GESTURE_MARGIN_FRACTION = 5

/** How far each pinch travels, as a fraction of the 3D view. */
private const val PINCH_PERCENT = 0.6f

/** Scanning needs precise location; granting it up front skips the permission gate, like a returning user. */
internal fun MacrobenchmarkScope.grantLocation() {
    device.executeShellCommand("pm grant $packageName android.permission.ACCESS_FINE_LOCATION")
    device.executeShellCommand("pm grant $packageName android.permission.ACCESS_COARSE_LOCATION")
}

/** Waits for the bottom navigation, i.e. the first real screen, after a launch. */
internal fun MacrobenchmarkScope.awaitHome() {
    check(device.wait(Until.hasObject(By.text("Map")), WAIT_MS)) { "WifiLens didn't reach its tabs" }
}

internal fun MacrobenchmarkScope.openTab(label: String) {
    device.findObject(By.text(label))?.click()
    device.waitForIdle()
}

/** Analyze → Networks, ready to scroll. */
internal fun MacrobenchmarkScope.openNetworks() {
    openTab("Analyze")
    device.findObject(By.text("Networks"))?.click()
    device.wait(Until.hasObject(By.scrollable(true)), WAIT_MS)
}

/** Fling the network list down and back up (a short list mostly shows the overscroll stretch). */
internal fun MacrobenchmarkScope.flingNetworks() {
    val list = device.findObject(By.scrollable(true)) ?: return // no networks in range: nothing to scroll
    list.setGestureMargin(device.displayWidth / GESTURE_MARGIN_FRACTION)
    repeat(2) {
        list.fling(Direction.DOWN)
        device.waitForIdle()
        list.fling(Direction.UP)
        device.waitForIdle()
    }
}

/**
 * Map → 3D view, creating an empty plan first on a fresh install. The 3D view is read-only, so pinching it can't
 * change the plan (a pinch on the 2D editor could paint a tile).
 */
internal fun MacrobenchmarkScope.openMap3d() {
    openTab("Map")
    device.findObject(By.text("Create plan"))?.let { createPlan ->
        createPlan.click()
        device.wait(Until.findObject(By.text("Create")), WAIT_MS)?.click()
        device.waitForIdle()
    }
    device.wait(Until.findObject(By.text("3D")), WAIT_MS)?.click()
    device.wait(Until.hasObject(By.desc(ISO_VIEW)), WAIT_MS)
    device.waitForIdle()
}

/** Pinch the 3D model in and out twice. */
internal fun MacrobenchmarkScope.pinchMap() {
    val model = device.findObject(By.desc(ISO_VIEW)) ?: return
    repeat(2) {
        model.pinchOpen(PINCH_PERCENT)
        device.waitForIdle()
        model.pinchClose(PINCH_PERCENT)
        device.waitForIdle()
    }
}

private const val ISO_VIEW = "3D view of the floor plan"

/** Diagnose → Coverage, the heaviest static screen (per-tile prediction). */
internal fun MacrobenchmarkScope.openDiagnose() {
    openTab("Diagnose")
    device.findObject(By.text("Coverage"))?.click()
    device.waitForIdle()
}

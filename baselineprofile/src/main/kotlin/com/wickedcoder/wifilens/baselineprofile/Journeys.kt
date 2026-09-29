package com.wickedcoder.wifilens.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

/**
 * The user journeys the profile and the benchmarks share. They only read: they never paint, move pins or delete,
 * because the benchmark build installs over the phone's WifiLens and keeps its data.
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

/** Analyze → Networks: fling the network list down and back up. */
internal fun MacrobenchmarkScope.scrollNetworks() {
    openTab("Analyze")
    device.findObject(By.text("Networks"))?.click()
    device.wait(Until.hasObject(By.scrollable(true)), WAIT_MS)
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
 * Map → 3D view: pinch the isometric model in and out. The 3D view is read-only, so this can't change the plan
 * (a pinch on the 2D editor could paint a tile). Skipped when the phone has no plan yet.
 */
internal fun MacrobenchmarkScope.zoomMap() {
    openTab("Map")
    val threeD = device.wait(Until.findObject(By.text("3D")), WAIT_MS) ?: return
    threeD.click()
    device.waitForIdle()
    val model = device.wait(Until.findObject(By.desc("3D view of the floor plan")), WAIT_MS) ?: return
    repeat(2) {
        model.pinchOpen(PINCH_PERCENT)
        device.waitForIdle()
        model.pinchClose(PINCH_PERCENT)
        device.waitForIdle()
    }
    device.findObject(By.text("2D"))?.click()
    device.waitForIdle()
}

/** Diagnose → Coverage, the heaviest static screen (per-tile prediction). */
internal fun MacrobenchmarkScope.openDiagnose() {
    openTab("Diagnose")
    device.findObject(By.text("Coverage"))?.click()
    device.waitForIdle()
}

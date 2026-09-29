package com.wickedcoder.wifilens.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the classes and methods WifiLens runs at startup and on its main screens, so they ship precompiled.
 * Run with `./gradlew :app:generateBaselineProfile` (device connected); the result lands in
 * `app/src/release/generated/baselineProfiles/` and is committed.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
        grantLocation()
        pressHome()
        startActivityAndWait()
        awaitHome()
        scrollNetworks()
        zoomMap()
        openDiagnose()
        openTab("More")
    }
}

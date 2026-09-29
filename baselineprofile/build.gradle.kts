// Generates the app's Baseline Profile and runs the macrobenchmarks (startup, frame timing) against :app.
// Both need a real device: `./gradlew :app:generateBaselineProfile` and
// `./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest`.
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.wickedcoder.wifilens.baselineprofile"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 28 // Baseline Profile generation needs API 28+
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"
}

baselineProfile {
    useConnectedDevices = true // the Moto Edge 40; no Gradle-managed emulator
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.uiautomator)
}

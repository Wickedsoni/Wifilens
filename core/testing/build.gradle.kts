plugins {
    id("wifilens.jvm.library")
}

// Shared unit-test helpers. Only ever added as `testImplementation`.
dependencies {
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
}

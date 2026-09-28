plugins {
    id("wifilens.android.library")
    id("wifilens.android.hilt")
}

android {
    namespace = "com.wickedcoder.wifilens.core.wifi"
}

dependencies {
    api(project(":core:model")) // connection and speed-test types are part of this module's API
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(project(":core:common"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

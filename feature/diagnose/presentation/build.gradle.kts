plugins {
    id("wifilens.android.feature")
    id("wifilens.android.hilt")
}

android {
    namespace = "com.wickedcoder.wifilens.feature.diagnose.presentation"

    testOptions {
        // The optimizer logs its timing via android.util.Log; without this, JVM tests throw "not mocked".
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":feature:diagnose:domain"))
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.core)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(project(":core:common"))
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}

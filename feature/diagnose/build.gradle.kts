plugins {
    id("wifilens.android.feature")
}

android {
    namespace = "com.wickedcoder.wifilens.feature.diagnose"

    testOptions {
        // The optimizer logs its timing via android.util.Log; without this, JVM tests throw "not mocked".
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core:rf"))
    implementation(project(":core:database"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:wifi"))
    // Read-only: only for the shared DevicePin domain type. GridPlan itself lives in :core:rf,
    // already depended on above — :feature:map does not depend back on :feature:diagnose.
    implementation(project(":feature:map"))

    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.core)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}

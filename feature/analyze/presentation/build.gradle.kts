plugins {
    id("wifilens.android.feature")
}

android {
    namespace = "com.wickedcoder.wifilens.feature.analyze.presentation"
}

dependencies {
    implementation(project(":feature:analyze:domain"))
    implementation(project(":core:model"))
    implementation(project(":core:wifi"))
    implementation(project(":core:designsystem"))
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
}

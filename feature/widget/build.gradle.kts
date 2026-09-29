plugins {
    id("wifilens.android.feature")
    id("wifilens.android.hilt")
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.wickedcoder.wifilens.feature.widget"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem")) // SignalPalette: the widget's signal colours match the app's

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}

plugins {
    id("wifilens.android.library")
    id("wifilens.android.hilt")
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.wickedcoder.wifilens.core.history"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.lifecycle.runtime.ktx) // repeatOnLifecycle: record only while the app is in the foreground
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

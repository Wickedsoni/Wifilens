plugins {
    id("wifilens.android.library")
}

android {
    namespace = "com.wickedcoder.wifilens.feature.diagnose.data"
}

dependencies {
    implementation(project(":feature:diagnose:domain"))
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.koin.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

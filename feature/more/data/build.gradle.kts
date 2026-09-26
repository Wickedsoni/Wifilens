plugins {
    id("wifilens.android.library")
}

android {
    namespace = "com.wickedcoder.wifilens.feature.more.data"
}

dependencies {
    implementation(project(":feature:more:domain"))
    implementation(project(":core:database"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.koin.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

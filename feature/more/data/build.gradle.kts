plugins {
    id("wifilens.android.library")
    id("wifilens.android.hilt")
}

android {
    namespace = "com.wickedcoder.wifilens.feature.more.data"
}

dependencies {
    implementation(project(":feature:more:domain"))
    implementation(project(":core:database"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(project(":core:common"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

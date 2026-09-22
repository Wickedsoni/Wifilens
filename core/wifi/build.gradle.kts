plugins {
    id("wifilens.android.library")
}

android {
    namespace = "com.wickedcoder.wifilens.core.wifi"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
}
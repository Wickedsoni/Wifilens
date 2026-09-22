plugins {
    id("wifilens.android.library")
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.wickedcoder.wifilens.core.database"
}

dependencies {
    implementation(project(":core:rf"))

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.koin.android)
    implementation(libs.androidx.datastore.preferences)
}

plugins {
    id("wifilens.android.library")
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.wickedcoder.wifilens.core.database"
}

ksp {
    // Committed schema history; MigrationTestHelper (in :app androidTest) reads these JSON files.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    api(project(":core:model")) // settings + domain types appear in this module's public API
    implementation(project(":core:rf"))

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.koin.android)
    implementation(libs.androidx.datastore.preferences)
}

plugins {
    id("wifilens.jvm.library")
}

dependencies {
    // Pure Kotlin: no Android, no Room. Only the shared model and the RF math.
    api(project(":core:model"))
    implementation(project(":core:rf"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

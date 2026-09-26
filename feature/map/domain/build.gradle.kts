plugins {
    id("wifilens.jvm.library")
}

dependencies {
    // Pure Kotlin: no Android, no Room.
    api(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

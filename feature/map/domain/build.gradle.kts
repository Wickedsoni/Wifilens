plugins {
    id("wifilens.jvm.library")
}

dependencies {
    implementation(libs.javax.inject) // @Inject constructors on use-cases
    // Pure Kotlin: no Android, no Room.
    api(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

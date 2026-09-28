plugins {
    id("wifilens.jvm.library")
}

dependencies {
    implementation(libs.javax.inject) // @Inject constructors on use-cases
    // Pure Kotlin: no Android, no Room. Only the shared model and the RF math.
    api(project(":core:model"))
    implementation(project(":core:rf"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

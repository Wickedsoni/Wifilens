plugins {
    id("wifilens.jvm.library")
}

dependencies {
    implementation(libs.javax.inject) // @Inject constructors on use-cases
    // Pure Kotlin: only the shared model (scan and connection types).
    api(project(":core:model"))

    testImplementation(kotlin("test"))
}

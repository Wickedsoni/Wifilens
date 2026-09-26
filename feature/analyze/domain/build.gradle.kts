plugins {
    id("wifilens.jvm.library")
}

dependencies {
    // Pure Kotlin: only the shared model (scan and connection types).
    api(project(":core:model"))

    testImplementation(kotlin("test"))
}

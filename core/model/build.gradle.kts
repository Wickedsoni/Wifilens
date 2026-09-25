plugins {
    id("wifilens.jvm.library")
}

dependencies {
    implementation(libs.kotlinx.coroutines.core) // Flow in the repository interfaces

    testImplementation(kotlin("test"))
}

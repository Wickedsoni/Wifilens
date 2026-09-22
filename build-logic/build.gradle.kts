plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}
gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "wifilens.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidFeature") {
            id = "wifilens.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("jvmLibrary") {
            id = "wifilens.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}

dependencies {
    compileOnly("com.android.tools.build:gradle:9.3.0-alpha12")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10")
}
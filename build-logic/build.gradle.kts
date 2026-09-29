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
        register("androidHilt") {
            id = "wifilens.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
        register("jvmLibrary") {
            id = "wifilens.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}

dependencies {
    compileOnly("com.android.tools.build:gradle:9.4.1")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    compileOnly("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.4.20")
}

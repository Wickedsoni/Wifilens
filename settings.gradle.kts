pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Wifilens"
include(":app")
include(":core:wifi")
include(":feature:analyze:presentation")
include(":core:model")
include(":core:rf")
include(":core:designsystem")
include(":core:database")
include(":feature:map")
include(":feature:diagnose:domain")
include(":feature:diagnose:data")
include(":feature:diagnose:presentation")
include(":feature:more")

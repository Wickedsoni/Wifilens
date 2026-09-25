// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

// Formatting (ktlint via Spotless). `ratchetFrom` means only files changed since origin/main are checked,
// so the existing code base is not reformatted wholesale; touched files must be clean. Rules are tuned in
// .editorconfig. Run `./gradlew spotlessApply` to fix, `spotlessCheck` runs in CI.
spotless {
    ratchetFrom("origin/main")
    kotlin {
        target("**/src/**/*.kt")
        targetExclude("**/build/**")
        ktlint()
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint()
    }
}

// Static analysis (detekt). Existing findings live in config/detekt/baseline.xml and may only shrink:
// regenerate it with `./gradlew detektBaseline` only when deliberately accepting a finding.
detekt {
    buildUponDefaultConfig = true
    config.setFrom("$rootDir/config/detekt/detekt.yml")
    baseline = file("$rootDir/config/detekt/baseline.xml")
    source.setFrom(
        fileTree(rootDir) {
            include("**/src/**/*.kt")
            exclude("**/build/**")
        },
    )
    parallel = true
}

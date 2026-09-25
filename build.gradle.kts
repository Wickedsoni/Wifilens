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

// Architecture guard (docs/adr/0001): fails the build on an illegal module dependency, so the layering
// cannot erode unnoticed. Rules: nothing depends on :app; :core:* never depends on :feature:*; features
// never depend on other features; :core:model depends on nothing and :core:rf only on :core:model.
val checkModuleGraph = tasks.register("checkModuleGraph") {
    group = "verification"
    description = "Fails if a module depends on something the architecture forbids."
}
gradle.projectsEvaluated {
    val edges: Map<String, List<String>> = subprojects.associate { module ->
        module.path to module.configurations
            .flatMap { configuration -> configuration.dependencies.withType(ProjectDependency::class.java).map { it.path } }
            .filter { it != module.path }
            .distinct()
            .sorted()
    }
    checkModuleGraph.configure {
        inputs.property("edges", edges)
        doLast {
            fun featureOf(path: String) = path.removePrefix(":feature:").substringBefore(':')

            // ":feature:<name>:<layer>" -> layer, for features split into domain / data / presentation.
            fun layerOf(path: String): String? =
                path
                    .takeIf { it.startsWith(":feature:") }
                    ?.split(':')
                    ?.filter { it.isNotEmpty() }
                    ?.takeIf { it.size == 3 }
                    ?.last()

            fun isAllowedInDomain(target: String) =
                target in setOf(":core:model", ":core:rf", ":core:common") ||
                    (target.startsWith(":feature:") && layerOf(target) == "domain")
            val violations = buildList {
                edges.forEach { (from, targets) ->
                    targets.forEach { to ->
                        when {
                            to == ":app" -> {
                                add("$from -> $to: nothing may depend on :app")
                            }
                            from.startsWith(":core:") && to.startsWith(":feature:") -> {
                                add("$from -> $to: core modules must not depend on features")
                            }
                            from.startsWith(":feature:") && to.startsWith(":feature:") && featureOf(from) != featureOf(to) -> {
                                add("$from -> $to: features must not depend on other features")
                            }
                            from == ":core:model" -> {
                                add("$from -> $to: :core:model must stay dependency-free")
                            }
                            layerOf(from) == "domain" && !isAllowedInDomain(to) -> {
                                add("$from -> $to: a domain module may only use :core:model, :core:rf and its own feature")
                            }
                            layerOf(from) == "presentation" && layerOf(to) == "data" && featureOf(from) == featureOf(to) -> {
                                add("$from -> $to: presentation must not depend on data (only :app wires them together)")
                            }
                            layerOf(from) == "data" && layerOf(to) == "presentation" -> {
                                add("$from -> $to: data must not depend on presentation")
                            }
                            from == ":core:rf" && to != ":core:model" -> {
                                add("$from -> $to: :core:rf may only use :core:model")
                            }
                        }
                    }
                }
            }
            check(violations.isEmpty()) {
                "Illegal module dependencies:\n" + violations.joinToString("\n") { " - $it" }
            }
        }
    }
}

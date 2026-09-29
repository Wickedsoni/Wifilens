import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

/** Android library + Compose (`wifilens.android.feature`): builds on [AndroidLibraryConventionPlugin]
 * and turns on `buildFeatures.compose`. Used by every module that has its own UI —
 * `:core:designsystem` and every `:feature:*` module. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("wifilens.android.library")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            extensions.configure<LibraryExtension> {
                buildFeatures.compose = true
            }
            extensions.configure<ComposeCompilerGradlePluginExtension> {
                stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose_stability.conf"))
            }
            // `./gradlew assembleRelease -PcomposeReports` writes the Compose compiler's stability and skippability
            // reports to build/compose-reports; off by default because they slow every build.
            if (providers.gradleProperty("composeReports").isPresent) {
                extensions.configure<ComposeCompilerGradlePluginExtension> {
                    reportsDestination.set(layout.buildDirectory.dir("compose-reports"))
                    metricsDestination.set(layout.buildDirectory.dir("compose-reports"))
                }
            }
        }
    }
}

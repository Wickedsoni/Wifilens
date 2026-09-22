import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure



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
        }
    }
}
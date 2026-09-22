import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Base Android library setup (`wifilens.android.library`): applies the Android Gradle plugin and
 * sets compile/min SDK once, so every module doesn't repeat them. Modules with no Compose UI
 * (`:core:wifi`, `:core:database`) apply this directly; UI modules go through
 * [AndroidFeatureConventionPlugin] instead, which layers Compose on top of this. */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")

            extensions.configure<LibraryExtension> {
                compileSdk = 37
                defaultConfig.minSdk = 26
            }
        }
    }
}
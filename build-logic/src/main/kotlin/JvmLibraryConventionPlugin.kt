import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType

/** Plain-Kotlin (`wifilens.jvm.library`): the plain `kotlin.jvm` plugin, no Android plugin at all.
 * This is what makes `:core:rf`'s "zero Android imports" constraint a compiler-enforced fact rather
 * than a convention someone could accidentally break — an `android.*` import there simply won't
 * resolve, because there's no Android classpath in this module to resolve it against. Also wires up
 * JUnit5 for the module's tests. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")

            dependencies {
                add("testImplementation", "org.junit.jupiter:junit-jupiter:5.11.0")
            }

            tasks.withType<Test> {
                useJUnitPlatform()
            }
        }
    }
}
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** Plain-Kotlin (`wifilens.jvm.library`): the plain `kotlin.jvm` plugin, no Android plugin at all.
 * This is what makes `:core:rf`'s "zero Android imports" constraint a compiler-enforced fact rather
 * than a convention someone could accidentally break — an `android.*` import there simply won't
 * resolve, because there's no Android classpath in this module to resolve it against. Also wires up
 * JUnit5 for the module's tests. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")

            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            extensions.configure<KotlinJvmProjectExtension> {
                compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
            }

            dependencies {
                add("testImplementation", "org.junit.jupiter:junit-jupiter:5.11.0")
            }

            tasks.withType<Test> {
                useJUnitPlatform()
            }
        }
    }
}

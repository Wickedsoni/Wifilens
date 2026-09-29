package com.wickedcoder.wifilens

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architecture guard (Sprint 5): user-visible text lives in `strings.xml`, never as literals in composables.
 * Android lint's HardcodedText only checks XML layouts, so this scans the Kotlin UI sources. Preview files are
 * exempt (sample data); a deliberate exception can be marked with an `// allow-literal` comment on the line.
 */
class NoHardcodedUiTextTest {
    private val uiArgument = Regex(
        """(?:\bText\(\s*|\b(?:text|title|description|label|contentDescription|headline|message)\s*=\s*)"[^"$]*[A-Za-z]{2}""",
    )

    private fun uiSourceRoots(): List<File> {
        val repo = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
        val featureUis = File(repo, "feature").listFiles().orEmpty().map { File(it, "presentation/src/main") }
        return featureUis + File(repo, "app/src/main") + File(repo, "core/designsystem/src/main")
    }

    @Test
    fun `composables take their text from string resources`() {
        val offenders = uiSourceRoots()
            .filter { it.exists() }
            .flatMap { root -> root.walkTopDown().filter { it.extension == "kt" && !it.name.contains("Preview") }.toList() }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    val code = line.trimStart()
                    val isComment = code.startsWith("//") || code.startsWith("*")
                    // animate*AsState/Transition `label`s are debug names for tooling, never shown to users.
                    val isAnimationLabel = "animate" in line || "Transition" in line
                    val flagged = !isComment && !isAnimationLabel && "allow-literal" !in line && uiArgument.containsMatchIn(line)
                    if (flagged) "${file.name}:${index + 1}: $code" else null
                }
            }
        assertTrue("Hard-coded UI text; move it to strings.xml:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }
}

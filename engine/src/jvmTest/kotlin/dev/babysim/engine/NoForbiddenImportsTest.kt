package dev.babysim.engine

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Enforces CLAUDE.md engine rules: shipped engine code has zero Android dependencies and no JVM-only APIs.
 * Belt-and-braces with the detekt ForbiddenImport rule in config/detekt/engine.yml.
 */
class NoForbiddenImportsTest {
    private val forbiddenPrefixes = listOf("android.", "androidx.", "java.", "javax.")

    // Source sets that ship in the engine. jvmTest is tooling and may use JVM APIs.
    private val shippedSourceSets = listOf("commonMain", "jvmMain")

    @Test
    fun shippedEngineSourcesHaveNoForbiddenImports() {
        val root =
            System.getProperty("engine.sourceRoot")
                ?: fail("engine.sourceRoot system property not set (configured in engine/build.gradle.kts)")

        val files =
            shippedSourceSets
                .map { File(root, it) }
                .filter { it.isDirectory }
                .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }

        assertTrue(files.isNotEmpty(), "expected engine sources under $root")

        val violations =
            files.flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    val trimmed = line.trim()
                    val imported = trimmed.removePrefix("import ").takeIf { trimmed.startsWith("import ") }
                    if (imported != null && forbiddenPrefixes.any { imported.startsWith(it) }) {
                        "${file.relativeTo(File(root))}:${index + 1}: $trimmed"
                    } else {
                        null
                    }
                }
            }

        assertTrue(violations.isEmpty(), "Forbidden imports in :engine:\n" + violations.joinToString("\n"))
    }
}

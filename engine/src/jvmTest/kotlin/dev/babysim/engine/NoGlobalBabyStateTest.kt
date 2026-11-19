package dev.babysim.engine

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * CLAUDE.md rule 3: no singletons, `object`s with mutable state, companion caches or static mutable state in
 * the engine, so nothing can hold a "current baby". Scans shipped engine sources: the declarations directly in
 * every `object` body (named or companion) and at the top level must hold no `var`, `lateinit` or mutable
 * collection. Function bodies, nested classes and constructor parameters are per-call or per-instance state.
 */
class NoGlobalBabyStateTest {
    private val mutableMarkers =
        listOf(
            Regex("""\bvar\b"""),
            Regex("""\blateinit\b"""),
            Regex("""\bmutable(List|Map|Set)Of\b"""),
            Regex("""\b(ArrayList|HashMap|HashSet|LinkedHashMap)\b"""),
        )

    @Test
    fun objectsAndTopLevelDeclarationsHoldNoMutableState() {
        val root = System.getProperty("engine.sourceRoot") ?: fail("engine.sourceRoot not set")
        val files = File(root, "commonMain").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(files.isNotEmpty())

        val violations =
            files.flatMap { file ->
                val text = stripComments(file.readText())
                objectBodies(text).flatMap { body -> findings(file, "object", ownLevel(body)) } +
                    findings(file, "top level", ownLevel(text))
            }
        assertTrue(violations.isEmpty(), "Mutable global state in :engine:\n" + violations.joinToString("\n"))
    }

    @Test
    fun theScannerCatchesAMutableObject() {
        val text = "object Cache {\n    var current: String? = null\n    val seen = mutableListOf<String>()\n}\n"
        assertTrue(objectBodies(text).flatMap { findings(File("x.kt"), "object", ownLevel(it)) }.size == 2)
        val local = "object Planner {\n    fun plan(): Int {\n        var i = 0\n        return i\n    }\n}\n"
        assertTrue(objectBodies(local).flatMap { findings(File("x.kt"), "object", ownLevel(it)) }.isEmpty())
        val top = "var current = 1\nclass A(private var s: Long) { var ok = 2 }\n"
        assertTrue(findings(File("x.kt"), "top level", ownLevel(top)).size == 1)
    }

    private fun findings(
        file: File,
        where: String,
        code: String,
    ): List<String> =
        mutableMarkers.filter { it.containsMatchIn(code) }.map { "${file.name}: $where uses ${it.pattern}" }

    /** Bodies of `object X {` and `companion object {` declarations. */
    private fun objectBodies(text: String): List<String> =
        Regex("""\bobject\b[^{=(]*\{""").findAll(text).map { body(text, it.range.last) }.toList()

    /** Only the text outside every `{…}` and `(…)` block: the declarations at this level. */
    private fun ownLevel(text: String): String {
        val out = StringBuilder()
        var braces = 0
        var parens = 0
        for (c in text) {
            when (c) {
                '{' -> braces++
                '(' -> parens++
            }
            val outside = braces == 0 && parens == 0
            if (outside && c !in "})") out.append(c)
            when (c) {
                '}' -> braces--
                ')' -> parens--
            }
        }
        return out.toString()
    }

    /** The text between the brace at [openIndex] and its matching close brace. */
    private fun body(
        text: String,
        openIndex: Int,
    ): String {
        var depth = 0
        for (i in openIndex until text.length) {
            if (text[i] == '{') depth++
            if (text[i] == '}') depth--
            if (depth == 0) return text.substring(openIndex + 1, i)
        }
        return text.substring(openIndex + 1)
    }

    private fun stripComments(text: String): String =
        text
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""//[^\n]*"""), "")
            .replace(Regex("\"\"\".*?\"\"\"", RegexOption.DOT_MATCHES_ALL), "\"\"")
            .replace(Regex(""""(\\.|[^"\\])*""""), "\"\"")
}

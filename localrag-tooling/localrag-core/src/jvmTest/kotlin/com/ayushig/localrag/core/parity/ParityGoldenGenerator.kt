package com.ayushig.localrag.core.parity

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Writes the JVM golden for ParityTest. Enabled only when UPDATE_PARITY_GOLDEN=1 with PARITY_OUT
 * set to the absolute path of ParityExpected.kt; otherwise this test is a no-op that passes.
 * It reads nothing and writes nothing else.
 */
class ParityGoldenGenerator {

    @Test
    fun generateParityGolden() {
        if (System.getenv("UPDATE_PARITY_GOLDEN") != "1") return
        val out = requireNotNull(System.getenv("PARITY_OUT")) {
            "PARITY_OUT must be the absolute path of ParityExpected.kt"
        }
        val result = ParityRun.compute()
        result.parseIssueCounts.forEachIndexed { index, count ->
            assertEquals(0, count, "parity document $index failed to parse")
        }
        File(out).writeText(render(result))
    }

    private fun render(result: ParityResult): String = buildString {
        appendLine("package com.ayushig.localrag.core.parity")
        appendLine()
        appendLine("// Generated on the JVM by ParityGoldenGenerator; do not edit by hand.")
        appendLine("object ParityExpected {")
        appendStringLists("tokens", result.tokens)
        appendStringLists("chunkIds", result.chunkIds)
        appendIntList("parseIssueCounts", result.parseIssueCounts)
        appendIntList("chunkIssueCounts", result.chunkIssueCounts)
        appendStringLists("rankingIds", result.rankingIds)
        appendIntLists("rankingScores", result.rankingScores)
        appendLine("}")
    }

    private fun StringBuilder.appendStringLists(name: String, rows: List<List<String>>) {
        appendLine("    val $name: List<List<String>> = listOf(")
        for (row in rows) {
            append("        listOf(")
            append(row.joinToString(", ") { "\"${escaped(it)}\"" })
            appendLine("),")
        }
        appendLine("    )")
    }

    private fun StringBuilder.appendIntList(name: String, row: List<Int>) {
        appendLine("    val $name: List<Int> = listOf(${row.joinToString(", ")})")
    }

    private fun StringBuilder.appendIntLists(name: String, rows: List<List<Int>>) {
        appendLine("    val $name: List<List<Int>> = listOf(")
        for (row in rows) {
            appendLine("        listOf(${row.joinToString(", ")}),")
        }
        appendLine("    )")
    }

    private fun escaped(value: String): String = buildString {
        for (c in value) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c.code in 0x20..0x7E) {
                    append(c)
                } else {
                    append("\\u" + c.code.toString(16).padStart(4, '0').uppercase())
                }
            }
        }
    }
}

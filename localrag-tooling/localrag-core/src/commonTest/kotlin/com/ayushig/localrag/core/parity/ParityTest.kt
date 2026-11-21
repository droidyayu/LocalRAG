package com.ayushig.localrag.core.parity

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Cross-platform parity: runs the real commonMain pipeline over the Unicode corpus and compares
 * element by element to the JVM-generated golden in ParityExpected. A failure message names the
 * case index and the escaped input so a platform mismatch reads as the trap that caught it.
 */
class ParityTest {

    @Test
    fun tokensMatchJvmGolden() {
        val actual = ParityRun.compute().tokens
        assertEquals(ParityExpected.tokens.size, actual.size, "token case count changed")
        for (index in actual.indices) {
            assertEquals(
                ParityExpected.tokens[index],
                actual[index],
                "token case $index input ${shortEscape(ParityCases.tokenInputs[index])}",
            )
        }
    }

    @Test
    fun chunkIdsMatchJvmGolden() {
        val actual = ParityRun.compute()
        assertEquals(ParityExpected.chunkIds.size, actual.chunkIds.size, "document count changed")
        assertEquals(
            ParityExpected.parseIssueCounts,
            actual.parseIssueCounts,
            "parse issue counts differ",
        )
        assertEquals(
            ParityExpected.chunkIssueCounts,
            actual.chunkIssueCounts,
            "chunk issue counts differ",
        )
        for (index in actual.chunkIds.indices) {
            assertEquals(
                ParityExpected.chunkIds[index],
                actual.chunkIds[index],
                "chunk ids of document $index differ",
            )
        }
    }

    @Test
    fun rankingMatchesJvmGolden() {
        val actual = ParityRun.compute()
        assertEquals(ParityExpected.rankingIds.size, actual.rankingIds.size, "query count changed")
        for (index in actual.rankingIds.indices) {
            val label = "query $index ${shortEscape(ParityCases.queries[index])}"
            assertEquals(ParityExpected.rankingIds[index], actual.rankingIds[index], "$label ids")
            assertEquals(
                ParityExpected.rankingScores[index],
                actual.rankingScores[index],
                "$label scores",
            )
        }
    }

    /** Escaped input for failure messages, truncated so the 5,000-character case stays readable. */
    private fun shortEscape(value: String): String {
        val head = if (value.length > 80) value.substring(0, 80) else value
        val suffix = if (value.length > 80) "..." else ""
        return "len=${value.length} ${escapeForMessage(head)}$suffix"
    }

    /** Common-only escaping: printable ASCII as-is, everything else as unicode escapes. */
    private fun escapeForMessage(value: String): String = buildString {
        for (c in value) {
            when (c) {
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                else -> if (c.code in 0x20..0x7E) {
                    append(c)
                } else {
                    append("\\u")
                    val code = c.code
                    append(HEX_DIGITS[(code shr 12) and 0xF])
                    append(HEX_DIGITS[(code shr 8) and 0xF])
                    append(HEX_DIGITS[(code shr 4) and 0xF])
                    append(HEX_DIGITS[code and 0xF])
                }
            }
        }
    }

    private companion object {
        const val HEX_DIGITS = "0123456789ABCDEF"
    }
}

package com.ayushig.localrag.demo.core

import java.io.File
import kotlin.random.Random
import kotlin.test.Test

/** Writes FormattersRandomGolden.kt from the reference implementation. Runs only with UPDATE_RANDOM_GOLDEN=1. */
class RandomGoldenGenerator {
    @Test
    fun generate() {
        if (System.getenv("UPDATE_RANDOM_GOLDEN") != "1") return
        val out = File(requireNotNull(System.getenv("PARITY_OUT")))
        val rnd = Random(7)
        val values = buildList {
            repeat(500) { add(rnd.nextDouble(-1e7, 1e7)) }
            repeat(400) { add(Math.round(rnd.nextDouble(-1e6, 1e6) * 1000) / 1000.0) }
            repeat(400) { add(Math.round(rnd.nextDouble(-1e4, 1e4) * 200) / 200.0) }
            repeat(200) { add(rnd.nextDouble(-1.0, 1.0)) }
            repeat(200) { add(rnd.nextLong(-5000000000L, 5000000000L) / 8.0) }
        }
        val lines = values.map { v ->
            listOf(
                java.lang.Double.doubleToRawLongBits(v).toString(),
                OriginalFormatters.currency(v), OriginalFormatters.percent(v), OriginalFormatters.quantity(v),
                OriginalFormatters.price(v, 2), OriginalFormatters.price(v, 4),
            ).joinToString("|")
        }
        // JVM string constants are limited to 65535 bytes, so the rows go in several chunks.
        val chunks = mutableListOf<String>()
        var current = StringBuilder()
        for (line in lines) {
            if (current.length + line.length + 1 > 20000) { chunks += current.toString(); current = StringBuilder() }
            current.append(line).append('\n')
        }
        chunks += current.toString()
        val sb = StringBuilder()
        sb.append("package com.ayushig.localrag.demo.core\n\n")
        sb.append("// Generated on the JVM from the original java Formatters. Do not edit by hand.\n")
        sb.append("// One row per line: raw double bits | currency | percent | quantity | price(2) | price(4).\n")
        sb.append("internal object FormattersRandomGolden {\n")
        chunks.forEachIndexed { i, c -> sb.append("    private const val CHUNK$i = \"").append(c.replace("\\", "\\\\").replace("$", "\\$").replace("\n", "\\n")).append("\"\n") }
        sb.append("\n    val rows: List<List<String>> = listOf(" + chunks.indices.joinToString(", ") { "CHUNK$it" } + ")\n")
        sb.append("        .flatMap { it.split('\\n') }.filter { it.isNotEmpty() }.map { it.split('|') }\n}\n")
        out.writeText(sb.toString())
    }
}

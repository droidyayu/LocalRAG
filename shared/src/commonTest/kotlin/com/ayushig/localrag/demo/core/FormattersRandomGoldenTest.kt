package com.ayushig.localrag.demo.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Random-value parity: every platform must print exactly what the original JVM formatter printed. */
class FormattersRandomGoldenTest {
    @Test
    fun matchesTheJvmOriginalOnRandomValues() {
        assertTrue(FormattersRandomGolden.rows.size >= 1500, "the golden must not be empty or truncated")
        for ((index, row) in FormattersRandomGolden.rows.withIndex()) {
            val value = Double.fromBits(row[0].toLong())
            assertEquals(row[1], Formatters.currency(value), "currency row $index value $value")
            assertEquals(row[2], Formatters.percent(value), "percent row $index value $value")
            assertEquals(row[3], Formatters.quantity(value), "quantity row $index value $value")
            assertEquals(row[4], Formatters.price(value, 2), "price2 row $index value $value")
            assertEquals(row[5], Formatters.price(value, 4), "price4 row $index value $value")
        }
    }
}

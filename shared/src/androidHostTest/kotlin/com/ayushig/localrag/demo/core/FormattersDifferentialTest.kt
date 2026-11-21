package com.ayushig.localrag.demo.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Differential test against the pre-migration java implementation (OriginalFormatters, kept as a
 * JVM-only reference). The displayed strings must not change for any realistic money value
 * (below 1e12): a price like 136918.395 has always shown as 136,918.40, never 136,918.39.
 */
class FormattersDifferentialTest {
    @Test fun matchesTheOriginalOnRandomValues() {
        val rnd = Random(42)
        var n = 0
        val bad = sortedMapOf<String, Int>()
        val samples = mutableMapOf<String, String>()
        fun eq(name: String, exp: String, act: String, v: Double) {
            if (exp != act) { bad.merge(name, 1, Int::plus); samples.putIfAbsent(name, "$v exp=$exp act=$act") }
        }
        fun check(v: Double) {
            eq("currency", OriginalFormatters.currency(v), Formatters.currency(v), v)
            eq("signedCurrency", OriginalFormatters.signedCurrency(v), Formatters.signedCurrency(v), v)
            eq("percent", OriginalFormatters.percent(v), Formatters.percent(v), v)
            eq("signedPercent", OriginalFormatters.signedPercent(v), Formatters.signedPercent(v), v)
            eq("quantity", OriginalFormatters.quantity(v), Formatters.quantity(v), v)
            eq("grams", OriginalFormatters.grams(v), Formatters.grams(v), v)
            for (d in 0..4) eq("price$d", OriginalFormatters.price(v, d), Formatters.price(v, d), v)
            n++
        }
        repeat(60000) { check(rnd.nextDouble(-1e7, 1e7)) }
        repeat(60000) { check(Math.round(rnd.nextDouble(-1e6, 1e6) * 1000) / 1000.0) }   // 3-decimal ties
        repeat(60000) { check(Math.round(rnd.nextDouble(-1e4, 1e4) * 200) / 200.0) }     // .005 grid
        repeat(20000) { check(rnd.nextDouble(-1.0, 1.0)) }
        repeat(20000) { check(rnd.nextLong(-5000000000L, 5000000000L) / 8.0) }            // exact binary ties
        repeat(5000) { check(Math.pow(10.0, rnd.nextDouble(-8.0, 11.0)) * (if (rnd.nextBoolean()) 1 else -1)) }
        println("fuzzed $n values; mismatches=$bad"); samples.forEach { (k, v) -> println("SAMPLE $k $v") }
        assertEquals(emptyMap<String, Int>(), bad.toMap())
    }
}

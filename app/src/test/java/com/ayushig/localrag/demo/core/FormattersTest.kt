package com.ayushig.localrag.demo.core

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {

    @Test
    fun `currency groups thousands and keeps two decimals`() {
        assertEquals("$169,363.92", Formatters.currency(169_363.92))
        assertEquals("$0.00", Formatters.currency(0.0))
    }

    @Test
    fun `currency rounds to two decimals`() {
        assertEquals("$3,616.67", Formatters.currency(3_616.6666666))
    }

    @Test
    fun `signed currency marks gains with a plus and losses with a minus`() {
        assertEquals("+$8,693.25", Formatters.signedCurrency(8_693.25))
        assertEquals("-$990.00", Formatters.signedCurrency(-990.0))
    }

    @Test
    fun `exactly zero carries no sign`() {
        assertEquals("$0.00", Formatters.signedCurrency(0.0))
        assertEquals("0.00%", Formatters.signedPercent(0.0))
    }

    @Test
    fun `percent keeps two decimals`() {
        assertEquals("5.41%", Formatters.percent(5.4106))
        assertEquals("+7.34%", Formatters.signedPercent(7.3419))
        assertEquals("-2.15%", Formatters.signedPercent(-2.15))
    }

    @Test
    fun `price honours the requested precision`() {
        assertEquals("1.0925", Formatters.price(1.0925, decimals = 4))
        assertEquals("5,318.50", Formatters.price(5_318.5))
    }

    @Test
    fun `share counts are pluralised`() {
        assertEquals("120 shares", Formatters.shares(120))
        assertEquals("1 share", Formatters.shares(1))
    }

    @Test
    fun `quantity trims trailing zeros`() {
        assertEquals("100,000", Formatters.quantity(100_000.0))
        assertEquals("20.5", Formatters.quantity(20.5))
    }

    @Test
    fun `grams and leverage read as a user would say them`() {
        assertEquals("250 g", Formatters.grams(250.0))
        assertEquals("1:30", Formatters.leverage(30))
    }

    @Test
    fun `as of renders the snapshot time in the given zone`() {
        assertEquals(
            "13 Sep 2026, 08:30",
            Formatters.asOf(Instant.parse("2026-09-13T08:30:00Z"), ZoneId.of("UTC")),
        )
    }
}

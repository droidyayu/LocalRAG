package com.ayushig.localrag.demo.core

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every figure string the common Formatters produces must equal the string the original JVM
 * implementation produced for the same input (FormattersGolden, generated on the JVM). The
 * output gate compares figure strings verbatim, so a changed separator, sign or rounding would
 * make the gate reject true answers.
 */
class FormattersGoldenTest {

    @Test
    fun currencyMatchesJvmGolden() {
        val actual = FormattersGoldenGrid.doubles.map { Formatters.currency(it) }
        assertEquals(FormattersGolden.currency.size, actual.size, "grid size changed")
        actual.forEachIndexed { index, text ->
            assertEquals(FormattersGolden.currency[index], text, "currency row $index")
        }
    }

    @Test
    fun signedCurrencyMatchesJvmGolden() {
        val actual = FormattersGoldenGrid.doubles.map { Formatters.signedCurrency(it) }
        assertEquals(FormattersGolden.signedCurrency.size, actual.size, "grid size changed")
        actual.forEachIndexed { index, text ->
            assertEquals(FormattersGolden.signedCurrency[index], text, "signedCurrency row $index")
        }
    }

    @Test
    fun percentMatchesJvmGolden() {
        val actual = FormattersGoldenGrid.doubles.map { Formatters.percent(it) }
        assertEquals(FormattersGolden.percent.size, actual.size, "grid size changed")
        actual.forEachIndexed { index, text ->
            assertEquals(FormattersGolden.percent[index], text, "percent row $index")
        }
    }

    @Test
    fun signedPercentMatchesJvmGolden() {
        val actual = FormattersGoldenGrid.doubles.map { Formatters.signedPercent(it) }
        assertEquals(FormattersGolden.signedPercent.size, actual.size, "grid size changed")
        actual.forEachIndexed { index, text ->
            assertEquals(FormattersGolden.signedPercent[index], text, "signedPercent row $index")
        }
    }

    @Test
    fun quantityMatchesJvmGolden() {
        val actual = FormattersGoldenGrid.doubles.map { Formatters.quantity(it) }
        assertEquals(FormattersGolden.quantity.size, actual.size, "grid size changed")
        actual.forEachIndexed { index, text ->
            assertEquals(FormattersGolden.quantity[index], text, "quantity row $index")
        }
    }

    @Test
    fun gramsMatchesJvmGolden() {
        val actual = FormattersGoldenGrid.doubles.map { Formatters.grams(it) }
        assertEquals(FormattersGolden.grams.size, actual.size, "grid size changed")
        actual.forEachIndexed { index, text ->
            assertEquals(FormattersGolden.grams[index], text, "grams row $index")
        }
    }

    @Test
    fun priceMatchesJvmGolden() {
        val goldens = listOf(
            FormattersGolden.price0,
            FormattersGolden.price1,
            FormattersGolden.price2,
            FormattersGolden.price4,
        )
        FormattersGoldenGrid.priceDecimals.forEachIndexed { decimalsIndex, decimals ->
            val actual = FormattersGoldenGrid.priceValues.map { Formatters.price(it, decimals) }
            assertEquals(goldens[decimalsIndex].size, actual.size, "price grid size changed")
            actual.forEachIndexed { index, text ->
                assertEquals(
                    goldens[decimalsIndex][index],
                    text,
                    "price decimals $decimals row $index",
                )
            }
        }
    }

    @Test
    fun sharesMatchesJvmGolden() {
        val actual = FormattersGoldenGrid.shareCounts.map { Formatters.shares(it) }
        assertEquals(FormattersGolden.shares.size, actual.size, "shares grid size changed")
        actual.forEachIndexed { index, text ->
            assertEquals(FormattersGolden.shares[index], text, "shares row $index")
        }
    }

    @Test
    fun leverageMatchesJvmGolden() {
        val actual = FormattersGoldenGrid.leverageValues.map { Formatters.leverage(it) }
        assertEquals(FormattersGolden.leverage.size, actual.size, "leverage grid size changed")
        actual.forEachIndexed { index, text ->
            assertEquals(FormattersGolden.leverage[index], text, "leverage row $index")
        }
    }

    @Test
    fun asOfMatchesJvmGolden() {
        val zones = mapOf(
            "UTC" to FormattersGolden.asOfUTC,
            "Asia/Kolkata" to FormattersGolden.asOfAsiaKolkata,
        )
        for ((zoneId, golden) in zones) {
            val zone = TimeZone.of(zoneId)
            val actual = FormattersGoldenGrid.asOfInstants.map { iso ->
                Formatters.asOf(Instant.parse(iso), zone)
            }
            assertEquals(golden.size, actual.size, "asOf grid size changed for $zoneId")
            actual.forEachIndexed { index, text ->
                assertEquals(golden[index], text, "asOf $zoneId row $index")
            }
        }
    }
}

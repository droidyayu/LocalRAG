package com.ayushig.localrag.demo.core

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The single place a number becomes text.
 *
 * Nothing else in the app formats a value. The assistant will render answers through these same
 * functions, so a spoken figure and the row on screen can never disagree.
 *
 * Signed variants always emit an explicit + or -, because colour alone must never carry the sign.
 */
object Formatters {

    private val locale: Locale = Locale.US

    private val currencyFormat: NumberFormat = NumberFormat.getCurrencyInstance(locale).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    private val decimalFormat: NumberFormat = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    private val asOfFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", locale)

    /** "$169,363.92". Negatives read "-$990.00". */
    fun currency(amount: Double): String = currencyFormat.format(amount)

    /** "+$8,693.25" / "-$990.00". Exactly zero has no sign. */
    fun signedCurrency(amount: Double): String = when {
        amount > 0.0 -> "+${currency(amount)}"
        else -> currency(amount)
    }

    /** "5.41%". */
    fun percent(value: Double): String = "${decimalFormat.format(value)}%"

    /** "+5.41%" / "-2.15%". Exactly zero has no sign. */
    fun signedPercent(value: Double): String = when {
        value > 0.0 -> "+${percent(value)}"
        else -> percent(value)
    }

    /** A price quoted to as many decimals as the instrument needs, e.g. 4 for FX. */
    fun price(value: Double, decimals: Int = 2): String =
        String.format(locale, "%,.${decimals}f", value)

    /** Whole units where the count is an integer, e.g. "120 shares". */
    fun shares(quantity: Int): String =
        "${NumberFormat.getIntegerInstance(locale).format(quantity)} ${if (quantity == 1) "share" else "shares"}"

    /** Trailing zeros trimmed: 100000.0 reads "100,000", 20.5 reads "20.5". */
    fun quantity(value: Double): String = NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = 2
    }.format(value)

    fun grams(value: Double): String = "${quantity(value)} g"

    /** 30 reads "1:30". */
    fun leverage(value: Int): String = "1:$value"

    /** "13 Sep 2026, 08:30". */
    fun asOf(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        asOfFormat.format(instant.atZone(zone))
}

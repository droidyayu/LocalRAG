package com.ayushig.localrag.demo.core

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The single place a number becomes text.
 *
 * Nothing else in the app formats a value. The assistant will render answers through these same
 * functions, so a spoken figure and the row on screen can never disagree.
 *
 * Signed variants always emit an explicit + or -, because colour alone must never carry the sign.
 *
 * Hand-written fixed-locale formatting: the JDK formatters (NumberFormat, String.format) are
 * JVM-only. The NumberFormat paths format the exact binary value of the double, HALF_EVEN;
 * price formats the shortest round-trip decimal (the Double.toString digits), HALF_UP, which is
 * what java.util.Formatter rounds. FormattersGoldenTest pins every string against the JVM golden.
 */
object Formatters {

    /** \"$169,363.92\". Negatives read \"-$990.00\". */
    fun currency(amount: Double): String =
        withSign(amount, "$" + fixed(amount, decimals = 2, mode = Rounding.HALF_EVEN))

    /** \"+$8,693.25\" / \"-$990.00\". Exactly zero has no sign. */
    fun signedCurrency(amount: Double): String = when {
        amount > 0.0 -> "+${currency(amount)}"
        else -> currency(amount)
    }

    /** \"5.41%\". */
    fun percent(value: Double): String =
        withSign(value, "${fixed(value, decimals = 2, mode = Rounding.HALF_EVEN)}%")

    /** \"+5.41%\" / \"-2.15%\". Exactly zero has no sign. */
    fun signedPercent(value: Double): String = when {
        value > 0.0 -> "+${percent(value)}"
        else -> percent(value)
    }

    /** A price quoted to as many decimals as the instrument needs, e.g. 4 for FX. */
    fun price(value: Double, decimals: Int = 2): String =
        withSign(value, fixedShortest(value, decimals))

    /** Whole units where the count is an integer, e.g. \"120 shares\". */
    fun shares(quantity: Int): String =
        "${grouped(quantity.toLong())} ${if (quantity == 1) "share" else "shares"}"

    /** Trailing zeros trimmed: 100000.0 reads \"100,000\", 20.5 reads \"20.5\". */
    fun quantity(value: Double): String =
        withSign(value, fixed(value, decimals = 2, minDecimals = 0, mode = Rounding.HALF_EVEN))

    fun grams(value: Double): String = "${quantity(value)} g"

    /** 30 reads \"1:30\". */
    fun leverage(value: Int): String = "1:$value"

    /** \"13 Sep 2026, 08:30\". */
    fun asOf(instant: Instant, zone: TimeZone = TimeZone.currentSystemDefault()): String {
        val local = instant.toLocalDateTime(zone)
        return "${local.dayOfMonth} ${MONTHS[local.month.ordinal]} ${local.year}, " +
            "${local.hour.twoDigits()}:${local.minute.twoDigits()}"
    }

    private enum class Rounding { HALF_EVEN, HALF_UP }

    private val MONTHS = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )

    private fun Int.twoDigits(): String = if (this < 10) "0$this" else toString()

    /**
     * The input sign (including -0.0) is preserved, matching the JDK formatters. The sign bit
     * is read from the raw bits so negative zero keeps its minus.
     */
    private fun withSign(value: Double, unsigned: String): String =
        if (value.toRawBits() < 0L && !unsigned.startsWith('-')) "-$unsigned" else unsigned

    /**
     * Fixed-point rendering of the shortest round-trip decimal of the double (the digits of
     * Double.toString in plain or E notation), rounded HALF_UP. This is what java.util.Formatter
     * rounds for %f: it works from the shortest decimal that maps back to the double, not from
     * the exact binary expansion, so 136918.395 reads 136,918.40 rather than 136,918.39.
     * Returns the unsigned body; callers add the sign with [withSign].
     */
    private fun fixedShortest(value: Double, decimals: Int): String {
        if (!value.isFinite()) {
            // No portfolio figure is ever non-finite; render plainly rather than guessing.
            return value.toString()
        }
        val text = value.toString()
        val unsigned = if (text.startsWith('-')) text.substring(1) else text
        val eIndex = unsigned.indexOf('E')
        val mantissa = if (eIndex < 0) unsigned else unsigned.substring(0, eIndex)
        val sciExp = if (eIndex < 0) 0 else unsigned.substring(eIndex + 1).toInt()
        val point = mantissa.indexOf('.')
        val intDigits = if (point < 0) mantissa else mantissa.substring(0, point)
        val fracDigits = if (point < 0) "" else mantissa.substring(point + 1)
        val stripped = (intDigits + fracDigits).trimStart('0')
        // The integer value of the digit string is unchanged by stripping leading zeros, so the
        // decimal exponent below still holds: value = stripped * 10^exp.
        val exp = sciExp - fracDigits.length
        if (stripped.isEmpty()) {
            return if (decimals == 0) "0" else "0." + "0".repeat(decimals)
        }
        val shift = exp + decimals
        val rounded: String = if (shift >= 0) {
            stripped + "0".repeat(shift)
        } else {
            val drop = -shift
            val full = stripped.padStart(drop + 1, '0')
            val kept = full.substring(0, full.length - drop)
            val dropped = full.substring(full.length - drop)
            val half = "5" + "0".repeat(drop - 1)
            if (dropped >= half) increment(kept) else kept
        }
        return if (decimals == 0) {
            grouped(rounded)
        } else if (rounded.length <= decimals) {
            "0." + rounded.padStart(decimals, '0')
        } else {
            grouped(rounded.substring(0, rounded.length - decimals)) + "." +
                rounded.substring(rounded.length - decimals)
        }
    }

    /** Adds one to a non-empty digit string. */
    private fun increment(digits: String): String {
        val chars = digits.toCharArray()
        var index = chars.size - 1
        while (index >= 0) {
            if (chars[index] == '9') {
                chars[index] = '0'
                index -= 1
            } else {
                chars[index] = chars[index] + 1
                return chars.concatToString()
            }
        }
        return "1" + chars.concatToString()
    }

    /**
     * Fixed-point rendering of the exact binary value, US grouping, explicit rounding mode.
     * Returns the unsigned body; callers add the sign with [withSign].
     */
    private fun fixed(
        value: Double,
        decimals: Int,
        minDecimals: Int = decimals,
        mode: Rounding,
    ): String {
        if (!value.isFinite()) {
            // No portfolio figure is ever non-finite; render plainly rather than guessing.
            return value.toString()
        }
        val rounded = roundToScaled(value, decimals, mode)
        val digits = rounded.toString()
        val splitAt = digits.length - decimals
        val intPart = if (splitAt > 0) digits.substring(0, splitAt) else "0"
        var fracPart = if (splitAt > 0) digits.substring(splitAt) else digits.padStart(decimals, '0')
        while (fracPart.length > minDecimals && fracPart.endsWith('0')) {
            fracPart = fracPart.dropLast(1)
        }
        return if (fracPart.isEmpty()) {
            grouped(intPart)
        } else {
            "${grouped(intPart)}.$fracPart"
        }
    }

    /**
     * Rounds value * 10^decimals to an integer using the exact binary expansion of the double,
     * which is what both NumberFormat and String.format round.
     */
    private fun roundToScaled(value: Double, decimals: Int, mode: Rounding): BigUint {
        val bits = value.toRawBits()
        val exponentBits = ((bits ushr 52) and 0x7FFL).toInt()
        val fraction = bits and 0xFFFFFFFFFFFFFL
        if (exponentBits == 0 && fraction == 0L) {
            // Positive or negative zero; the sign is applied by the caller.
            return BigUint.zero()
        }
        val (mantissa, exponent) = if (exponentBits == 0) {
            fraction to -1074
        } else {
            (fraction or (1L shl 52)) to (exponentBits - 1075)
        }
        var scaled = BigUint.fromLong(mantissa)
        repeat(decimals) { scaled = scaled.mulSmall(10) }
        if (exponent >= 0) {
            repeat(exponent) { scaled = scaled.mulSmall(2) }
            return scaled
        }
        val shift = -exponent
        val kept = scaled.shr(shift)
        val dropped = scaled.lowBits(shift)
        if (dropped.isZero()) {
            return kept
        }
        val twiceDropped = dropped.shl(1)
        val half = BigUint.pow2(shift)
        val comparison = twiceDropped.compareTo(half)
        val roundUp = when {
            comparison > 0 -> true
            comparison < 0 -> false
            mode == Rounding.HALF_UP -> true
            else -> kept.isOdd()
        }
        return if (roundUp) kept.plusOne() else kept
    }

    private fun grouped(intPart: String): String {
        if (intPart.length <= 3) {
            return intPart
        }
        val out = StringBuilder()
        val head = intPart.length % 3
        if (head > 0) {
            out.append(intPart.substring(0, head))
        }
        var index = head
        while (index < intPart.length) {
            if (out.isNotEmpty()) {
                out.append(',')
            }
            out.append(intPart.substring(index, index + 3))
            index += 3
        }
        return out.toString()
    }

    private fun grouped(value: Long): String {
        val negative = value < 0L
        val digits = if (negative) (-value).toString() else value.toString()
        return if (negative) "-${grouped(digits)}" else grouped(digits)
    }

    /**
     * Unsigned integer, base 2^30 limbs, little-endian. Just enough for exact binary-decimal
     * conversion of a double: multiply by small ints, shift, mask, compare, add one, render.
     */
    private class BigUint private constructor(private val limbs: IntArray) {

        fun isZero(): Boolean = limbs.size == 1 && limbs[0] == 0

        fun isOdd(): Boolean = limbs[0] and 1 == 1

        fun mulSmall(factor: Int): BigUint {
            if (isZero() || factor == 1) {
                return this
            }
            val out = IntArray(limbs.size + 1)
            var carry = 0L
            for (index in limbs.indices) {
                val product = limbs[index].toLong() * factor + carry
                out[index] = (product and MASK).toInt()
                carry = product ushr SHIFT
            }
            var size = limbs.size
            if (carry > 0L) {
                out[size] = carry.toInt()
                size += 1
            }
            return BigUint(canonical(out, size))
        }

        fun shl(bits: Int): BigUint {
            if (isZero() || bits == 0) {
                return this
            }
            val wordShift = bits / SHIFT
            val bitShift = bits % SHIFT
            val out = IntArray(limbs.size + wordShift + 1)
            var carry = 0
            for (index in limbs.indices) {
                val combined = (limbs[index].toLong() shl bitShift) or carry.toLong()
                out[index + wordShift] = (combined and MASK).toInt()
                carry = (combined ushr SHIFT).toInt()
            }
            out[limbs.size + wordShift] = carry
            return BigUint(canonical(out, out.size))
        }

        fun shr(bits: Int): BigUint {
            val wordShift = bits / SHIFT
            if (wordShift >= limbs.size) {
                return zero()
            }
            val bitShift = bits % SHIFT
            if (bitShift == 0) {
                return BigUint(canonical(limbs.copyOfRange(wordShift, limbs.size), limbs.size - wordShift))
            }
            val out = IntArray(limbs.size - wordShift)
            var carry = 0
            for (index in limbs.indices.reversed()) {
                if (index < wordShift) {
                    break
                }
                val limb = limbs[index]
                out[index - wordShift] = ((limb ushr bitShift) or carry)
                carry = (limb and ((1 shl bitShift) - 1)) shl (SHIFT - bitShift)
            }
            return BigUint(canonical(out, out.size))
        }

        fun lowBits(count: Int): BigUint {
            if (count <= 0) {
                return zero()
            }
            val words = (count + SHIFT - 1) / SHIFT
            if (words > limbs.size) {
                return this
            }
            val out = limbs.copyOf(words)
            val excess = words * SHIFT - count
            if (excess > 0) {
                out[words - 1] = out[words - 1] and ((1 shl (SHIFT - excess)) - 1)
            }
            return BigUint(canonical(out, out.size))
        }

        fun compareTo(other: BigUint): Int {
            if (limbs.size != other.limbs.size) {
                return limbs.size.compareTo(other.limbs.size)
            }
            for (index in limbs.indices.reversed()) {
                if (limbs[index] != other.limbs[index]) {
                    return limbs[index].compareTo(other.limbs[index])
                }
            }
            return 0
        }

        fun plusOne(): BigUint {
            val out = limbs.copyOf()
            var index = 0
            while (true) {
                if (index == out.size) {
                    return BigUint(out + 1)
                }
                val sum = out[index] + 1
                out[index] = sum and MASK.toInt()
                if (sum <= MASK) {
                    return BigUint(out)
                }
                index += 1
            }
        }

        override fun toString(): String {
            if (isZero()) {
                return "0"
            }
            var current = this
            val chunks = ArrayDeque<Int>()
            while (!current.isZero()) {
                val (quotient, remainder) = current.divmodSmall(DECIMAL_CHUNK)
                chunks.addFirst(remainder)
                current = quotient
            }
            val out = StringBuilder()
            out.append(chunks.removeFirst())
            for (chunk in chunks) {
                out.append(chunk.toString().padStart(9, '0'))
            }
            return out.toString()
        }

        private fun divmodSmall(divisor: Int): Pair<BigUint, Int> {
            val out = IntArray(limbs.size)
            var remainder = 0L
            for (index in limbs.indices.reversed()) {
                val current = remainder * BASE + limbs[index]
                out[index] = (current / divisor).toInt()
                remainder = current % divisor
            }
            return BigUint(canonical(out, out.size)) to remainder.toInt()
        }

        companion object {
            const val SHIFT = 30
            const val MASK = (1L shl SHIFT) - 1L
            const val BASE = 1L shl SHIFT
            const val DECIMAL_CHUNK = 1_000_000_000

            fun zero(): BigUint = BigUint(intArrayOf(0))

            fun fromLong(value: Long): BigUint {
                require(value >= 0L)
                if (value == 0L) {
                    return zero()
                }
                val out = IntArray(3)
                var rest = value
                var size = 0
                while (rest > 0L) {
                    out[size] = (rest and MASK).toInt()
                    rest = rest ushr SHIFT
                    size += 1
                }
                return BigUint(out.copyOf(size))
            }

            fun pow2(bit: Int): BigUint = fromLong(1L).shl(bit)

            private fun canonical(limbs: IntArray, size: Int): IntArray {
                var end = size
                while (end > 1 && limbs[end - 1] == 0) {
                    end -= 1
                }
                return limbs.copyOf(end)
            }
        }
    }
}

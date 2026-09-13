package com.ayushig.localrag.core.index

/**
 * Keeps chunks whose declared app version range contains the installed version.
 *
 * Applied after fusion, never before: filtering first would let a filtered-out chunk consume a
 * slot in one ranking and distort the fused order.
 */
object VersionFilter {

    fun matches(appVersion: String, minimum: String?, maximum: String?): Boolean {
        val installed = parse(appVersion) ?: return true
        val lower = minimum?.let(::parse)
        val upper = maximum?.let(::parse)
        if (lower != null && compare(installed, lower) < 0) return false
        if (upper != null && compare(installed, upper) > 0) return false
        return true
    }

    /** Lenient by design: an unparseable version must not hide documentation. */
    private fun parse(version: String): List<Int>? {
        val numeric = version.trim().substringBefore(Char(45)).substringBefore(Char(43))
        if (numeric.isEmpty()) return null
        val parts = numeric.split(Char(46)).map { it.toIntOrNull() ?: return null }
        return parts.ifEmpty { null }
    }

    private fun compare(left: List<Int>, right: List<Int>): Int {
        val size = maxOf(left.size, right.size)
        for (index in 0 until size) {
            val comparison = (left.getOrElse(index) { 0 }).compareTo(right.getOrElse(index) { 0 })
            if (comparison != 0) return comparison
        }
        return 0
    }
}

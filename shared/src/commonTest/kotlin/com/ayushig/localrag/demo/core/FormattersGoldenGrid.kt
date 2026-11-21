package com.ayushig.localrag.demo.core

/**
 * The input grid both the JVM golden generator and FormattersGoldenTest iterate in the same
 * order. One input per trap: signed and unsigned zeros, values that round to zero from either
 * side, exact binary ties, large grouped values, and zone-sensitive instants.
 */
object FormattersGoldenGrid {

    val doubles: List<Double> = listOf(
        0.0,
        -0.0,
        0.004,
        -0.004,
        0.005,
        0.015,
        0.025,
        0.035,
        0.045,
        0.125,
        0.375,
        0.5,
        1.005,
        2.675,
        8693.25,
        169363.92,
        -990.0,
        100000.0,
        20.5,
        999999.995,
        1234567.891,
        1.0E9,
        1.0E15,
        -1234.5,
        1.0E-7,
    )

    val priceValues: List<Double> = listOf(
        1234.5678,
        0.99995,
        -0.5,
        1.0E6,
    )

    val priceDecimals: List<Int> = listOf(0, 1, 2, 4)

    val shareCounts: List<Int> = listOf(0, 1, 2, 120, 1234567)

    val leverageValues: List<Int> = listOf(1, 30, 500)

    val asOfInstants: List<String> = listOf(
        "1970-01-01T00:00:00Z",
        "2026-09-13T08:30:00Z",
        "2026-12-31T23:59:00Z",
        "2026-03-08T01:30:00Z",
    )

    val zones: List<String> = listOf("UTC", "Asia/Kolkata")
}

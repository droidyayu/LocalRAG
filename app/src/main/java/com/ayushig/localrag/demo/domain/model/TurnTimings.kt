package com.ayushig.localrag.demo.domain.model

/** Where one assistant turn's wall-clock time went, in milliseconds. */
data class TurnTimings(
    val totalMs: Long,
    val generateMs: Long,
    val toolMs: Long,
    val rounds: Int,
)

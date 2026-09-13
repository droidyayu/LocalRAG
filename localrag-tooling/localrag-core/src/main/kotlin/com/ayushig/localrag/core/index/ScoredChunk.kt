package com.ayushig.localrag.core.index

/** A chunk index paired with the score that retrieved it. */
data class ScoredChunk(val chunkIndex: Int, val score: Float)

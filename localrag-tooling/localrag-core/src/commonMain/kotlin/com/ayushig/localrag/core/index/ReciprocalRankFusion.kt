package com.ayushig.localrag.core.index

/**
 * Combines ranked lists by rank rather than score.
 *
 * BM25 scores and cosine similarities are not on a comparable scale, and normalizing them against
 * each other needs a corpus-specific constant that goes stale. Reciprocal rank fusion sidesteps
 * that entirely: only the ordering matters.
 */
object ReciprocalRankFusion {

    const val DEFAULT_K: Int = 60

    fun fuse(vararg rankings: List<ScoredChunk>, k: Int = DEFAULT_K): List<ScoredChunk> =
        fuse(rankings.toList(), k)

    fun fuse(rankings: List<List<ScoredChunk>>, k: Int = DEFAULT_K): List<ScoredChunk> {
        val fused = mutableMapOf<Int, Float>()
        for (ranking in rankings) {
            ranking.forEachIndexed { position, scored ->
                fused[scored.chunkIndex] =
                    (fused[scored.chunkIndex] ?: 0f) + 1f / (k + position + 1).toFloat()
            }
        }
        return fused.entries
            .map { ScoredChunk(it.key, it.value) }
            .sortedWith(compareByDescending<ScoredChunk> { it.score }.thenBy { it.chunkIndex })
    }
}

package com.ayushig.localrag.android.internal

import com.ayushig.localrag.android.MatchSource
import com.ayushig.localrag.android.Passage
import com.ayushig.localrag.core.bundle.Bundle
import com.ayushig.localrag.core.index.ReciprocalRankFusion
import com.ayushig.localrag.core.index.ScoredChunk
import com.ayushig.localrag.core.index.VectorIndex
import com.ayushig.localrag.core.index.VersionFilter
import com.ayushig.localrag.core.text.Tokenizer

/**
 * Turns a query into passages.
 *
 * Version and category filtering run after fusion, never before: a chunk removed early would
 * still have occupied a rank in one list and skewed the fused order.
 */
internal class Retriever(
    private val bundle: Bundle,
    private val vectorIndex: VectorIndex?,
    private val appVersion: String,
) {

    fun retrieve(
        query: String,
        topK: Int,
        queryVector: FloatArray?,
        categories: Set<String>,
    ): List<Passage> {
        val terms = Tokenizer.tokenize(query)
        if (terms.isEmpty()) return emptyList()

        // Over-fetch, because filtering afterwards will discard some of what was retrieved.
        val candidateCount = topK * CANDIDATE_MULTIPLIER
        val lexical = bundle.bm25.search(terms, candidateCount)
        val semantic = if (vectorIndex != null && queryVector != null) {
            vectorIndex.search(queryVector, candidateCount)
        } else {
            emptyList()
        }

        val fused = if (semantic.isEmpty()) {
            lexical
        } else {
            ReciprocalRankFusion.fuse(lexical, semantic)
        }

        val lexicalHits = lexical.mapTo(mutableSetOf()) { it.chunkIndex }
        val semanticHits = semantic.mapTo(mutableSetOf()) { it.chunkIndex }

        return fused.asSequence()
            .filter { keep(it, categories) }
            .take(topK)
            .map { scored -> passage(scored, lexicalHits, semanticHits) }
            .toList()
    }

    private fun keep(scored: ScoredChunk, categories: Set<String>): Boolean {
        val chunk = bundle.chunks[scored.chunkIndex]
        if (categories.isNotEmpty() && chunk.category !in categories) return false
        return VersionFilter.matches(appVersion, chunk.appVersionMin, chunk.appVersionMax)
    }

    private fun passage(
        scored: ScoredChunk,
        lexicalHits: Set<Int>,
        semanticHits: Set<Int>,
    ): Passage {
        val chunk = bundle.chunks[scored.chunkIndex]
        val source = when {
            scored.chunkIndex in lexicalHits && scored.chunkIndex in semanticHits -> MatchSource.HYBRID
            scored.chunkIndex in semanticHits -> MatchSource.VECTOR
            else -> MatchSource.BM25
        }
        return Passage(
            chunkId = chunk.chunkId,
            docId = chunk.docId,
            title = chunk.title,
            heading = chunk.heading,
            text = chunk.text,
            screenLink = chunk.screen,
            score = scored.score,
            source = source,
        )
    }

    private companion object {
        const val CANDIDATE_MULTIPLIER = 5
    }
}

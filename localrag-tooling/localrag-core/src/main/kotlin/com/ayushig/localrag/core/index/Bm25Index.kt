package com.ayushig.localrag.core.index

import com.ayushig.localrag.core.document.Chunk
import com.ayushig.localrag.core.text.Tokenizer
import kotlinx.serialization.Serializable

/**
 * A plain inverted index with BM25 scoring.
 *
 * Written in Kotlin rather than delegating to SQLite FTS5 on purpose: a prebuilt FTS5 index
 * depends on the SQLite build and tokenizer present on the user device, so a mismatch fails in the
 * field instead of in CI. This is deterministic on every device and unit-testable without an
 * emulator.
 *
 * Field weights exist because an alias is the cheapest retrieval win there is — a user who types
 * "standing order" should find the document titled "What is a GTT order?".
 */
class Bm25Index(
    private val postings: Map<String, List<Posting>>,
    private val chunkLengths: FloatArray,
    private val averageChunkLength: Float,
    private val k1: Float = DEFAULT_K1,
    private val b: Float = DEFAULT_B,
) {

    @Serializable
    data class Posting(val chunkIndex: Int, val weightedFrequency: Float)

    /** Serializable form of the whole index; the bundle stores exactly this. */
    @Serializable
    data class Snapshot(
        val k1: Float,
        val b: Float,
        val averageChunkLength: Float,
        val chunkLengths: List<Float>,
        val postings: Map<String, List<Posting>>,
    )

    val chunkCount: Int get() = chunkLengths.size

    fun search(queryTerms: List<String>, topK: Int): List<ScoredChunk> {
        if (topK <= 0 || chunkCount == 0) return emptyList()

        val scores = FloatArray(chunkCount)
        for (term in queryTerms.distinct()) {
            val termPostings = postings[term] ?: continue
            val documentFrequency = termPostings.size
            val idf = inverseDocumentFrequency(documentFrequency)
            for (posting in termPostings) {
                val frequency = posting.weightedFrequency
                val length = chunkLengths[posting.chunkIndex]
                val denominator = frequency + k1 * (1f - b + b * length / averageChunkLength)
                scores[posting.chunkIndex] += idf * (frequency * (k1 + 1f)) / denominator
            }
        }

        return scores.asSequence()
            .mapIndexed { index, score -> ScoredChunk(index, score) }
            .filter { it.score > 0f }
            .sortedWith(compareByDescending<ScoredChunk> { it.score }.thenBy { it.chunkIndex })
            .take(topK)
            .toList()
    }

    /** Standard BM25 probabilistic idf, floored so a term in every chunk cannot score negative. */
    private fun inverseDocumentFrequency(documentFrequency: Int): Float {
        val numerator = chunkCount - documentFrequency + 0.5
        val denominator = documentFrequency + 0.5
        return maxOf(kotlin.math.ln(1.0 + numerator / denominator), 0.0).toFloat()
    }

    fun snapshot(): Snapshot = Snapshot(
        k1 = k1,
        b = b,
        averageChunkLength = averageChunkLength,
        chunkLengths = chunkLengths.toList(),
        postings = postings,
    )

    companion object {
        const val DEFAULT_K1 = 1.2f
        const val DEFAULT_B = 0.75f

        const val ALIAS_WEIGHT = 3.0f
        const val TITLE_WEIGHT = 2.0f
        const val BODY_WEIGHT = 1.0f

        fun build(
            chunks: List<Chunk>,
            k1: Float = DEFAULT_K1,
            b: Float = DEFAULT_B,
        ): Bm25Index {
            val postings = mutableMapOf<String, MutableList<Posting>>()
            val lengths = FloatArray(chunks.size)

            chunks.forEachIndexed { chunkIndex, chunk ->
                val weighted = mutableMapOf<String, Float>()
                fun add(text: String, weight: Float) {
                    for (term in Tokenizer.tokenize(text)) {
                        weighted[term] = (weighted[term] ?: 0f) + weight
                    }
                }

                chunk.aliases.forEach { add(it, ALIAS_WEIGHT) }
                add(chunk.title, TITLE_WEIGHT)
                chunk.heading?.let { add(it, TITLE_WEIGHT) }
                add(chunk.displayText, BODY_WEIGHT)

                lengths[chunkIndex] = weighted.values.sum()
                for ((term, frequency) in weighted) {
                    postings.getOrPut(term) { mutableListOf() }
                        .add(Posting(chunkIndex, frequency))
                }
            }

            val average = if (lengths.isEmpty()) 1f else lengths.average().toFloat()
            return Bm25Index(
                postings = postings.mapValues { it.value.toList() },
                chunkLengths = lengths,
                // An empty corpus must not divide by zero.
                averageChunkLength = if (average <= 0f) 1f else average,
                k1 = k1,
                b = b,
            )
        }

        fun from(snapshot: Snapshot): Bm25Index = Bm25Index(
            postings = snapshot.postings,
            chunkLengths = snapshot.chunkLengths.toFloatArray(),
            averageChunkLength = snapshot.averageChunkLength,
            k1 = snapshot.k1,
            b = snapshot.b,
        )
    }
}

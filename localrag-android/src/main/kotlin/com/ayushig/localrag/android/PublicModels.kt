package com.ayushig.localrag.android

/** Where a passage came from in the retrieval pipeline. */
enum class MatchSource { BM25, VECTOR, HYBRID, PRECOMPUTED }

/** How an answer was produced. */
enum class AnswerMode { PRECOMPUTED, GENERATED, EXTRACTIVE }

/**
 * One retrieved section of documentation, ready to show.
 *
 * [text] is the display form, so it keeps its Markdown. [screenLink] lets the host app offer a
 * jump to the screen the passage describes.
 */
data class Passage(
    val chunkId: String,
    val docId: String,
    val title: String,
    val heading: String?,
    val text: String,
    val screenLink: String?,
    val score: Float,
    val source: MatchSource,
)

data class QueryMetrics(
    val retrievalMs: Long,
    val generationMs: Long,
    val candidateCount: Int,
    val passageCount: Int,
)

sealed interface AnswerChunk {
    /**
     * Emitted first, before any token. Retrieval is fast and generation is not, so the host app
     * can render a source card immediately and stream text underneath it.
     */
    data class Sources(val passages: List<Passage>) : AnswerChunk

    data class Token(val text: String) : AnswerChunk

    data class Done(val metrics: QueryMetrics, val mode: AnswerMode) : AnswerChunk

    data class Error(val message: String) : AnswerChunk
}

sealed interface LocalRagState {
    data object Idle : LocalRagState

    data object Loading : LocalRagState

    /**
     * Ready to answer. [usingVectors] is false whenever no embedder is configured or the bundle
     * vectors failed the parity check, in which case retrieval is BM25 only.
     */
    data class Ready(
        val chunkCount: Int,
        val contentVersion: Int,
        val usingVectors: Boolean,
        val canGenerate: Boolean,
    ) : LocalRagState

    data class Failed(val reason: String) : LocalRagState
}

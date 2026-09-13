package com.ayushig.localrag.android.internal

import com.ayushig.localrag.android.AnswerChunk
import com.ayushig.localrag.android.AnswerMode
import com.ayushig.localrag.android.Passage
import com.ayushig.localrag.android.QueryMetrics
import com.ayushig.localrag.core.answer.ClusterMatcher
import com.ayushig.localrag.core.answer.OutputGate
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Retrieval and answering, with no Android dependency.
 *
 * Split out of [com.ayushig.localrag.android.LocalRag] so the degradation matrix can be asserted
 * on the JVM with fake models. Testing it only on a device would mean the matrix is checked in the
 * configuration least likely to break.
 */
internal class AnswerPipeline(
    private val retriever: Retriever,
    private val clusters: ClusterMatcher,
    private val embedder: Embedder?,
    private val generator: TextGenerator?,
    private val topK: Int,
    private val categories: Set<String>,
    private val maxContextTokens: Int,
    private val chunkCount: Int,
    private val onRejected: (OutputGate.Verdict.Rejected) -> Unit = {},
) {

    fun retrieve(query: String): List<Passage> = retriever.retrieve(
        query = query,
        topK = topK,
        queryVector = embedder?.embed(query),
        categories = categories,
    )

    /**
     * The collection currently inside [answer], if any. A new query cancels it: the native
     * conversation is not safe for concurrent use, and answering a stale question after the user
     * already asked another is never what is wanted.
     */
    private val flightMutex = Mutex()
    private var inFlight: Job? = null

    fun answer(query: String): Flow<AnswerChunk> = flow {
        val self = currentCoroutineContext()[Job]
        flightMutex.withLock {
            inFlight?.takeIf { it !== self }?.cancel()
            inFlight = self
        }
        try {
            answerUnguarded(query)
        } finally {
            flightMutex.withLock {
                if (inFlight === self) inFlight = null
            }
        }
    }

    private suspend fun FlowCollector<AnswerChunk>.answerUnguarded(query: String) {
        val retrievalStart = System.currentTimeMillis()
        val passages = retrieve(query)
        val retrievalMs = System.currentTimeMillis() - retrievalStart

        // Sources first, always: retrieval is fast and generation is not.
        emit(AnswerChunk.Sources(passages))

        // A human wrote this answer, so it beats anything generated when it fits.
        clusters.match(query)?.let { cluster ->
            emit(AnswerChunk.Token(cluster.answer))
            emit(AnswerChunk.Done(metrics(retrievalMs, 0, passages), AnswerMode.PRECOMPUTED))
            return
        }

        if (passages.isEmpty()) {
            emit(AnswerChunk.Done(metrics(retrievalMs, 0, passages), AnswerMode.EXTRACTIVE))
            return
        }

        val active = generator
        if (active != null) {
            val start = System.currentTimeMillis()
            val generated = active.generate(PromptBuilder.build(query, passages, maxContextTokens))
            val generationMs = System.currentTimeMillis() - start

            // Buffered and judged whole. A wrong figure already on screen cannot be withdrawn.
            val verdict = generated?.let {
                OutputGate.check(it, passages.map { passage -> passage.text })
            }
            when {
                generated != null && verdict is OutputGate.Verdict.Allowed -> {
                    emit(AnswerChunk.Token(generated))
                    emit(
                        AnswerChunk.Done(
                            metrics(retrievalMs, generationMs, passages),
                            AnswerMode.GENERATED,
                        ),
                    )
                    return
                }
                verdict is OutputGate.Verdict.Rejected -> onRejected(verdict)
            }
        }

        // The best passage, verbatim. It can be the wrong passage for the question, but it cannot
        // invent a number.
        emit(AnswerChunk.Token(passages.first().text))
        emit(AnswerChunk.Done(metrics(retrievalMs, 0, passages), AnswerMode.EXTRACTIVE))
    }

    private fun metrics(retrievalMs: Long, generationMs: Long, passages: List<Passage>) =
        QueryMetrics(
            retrievalMs = retrievalMs,
            generationMs = generationMs,
            candidateCount = chunkCount,
            passageCount = passages.size,
        )
}

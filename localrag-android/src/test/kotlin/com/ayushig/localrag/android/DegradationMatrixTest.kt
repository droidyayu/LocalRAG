package com.ayushig.localrag.android

import com.ayushig.localrag.android.internal.AnswerPipeline
import com.ayushig.localrag.android.internal.Embedder
import com.ayushig.localrag.android.internal.Retriever
import com.ayushig.localrag.android.internal.TextGenerator
import com.ayushig.localrag.core.answer.ClusterMatcher
import com.ayushig.localrag.core.bundle.Bm25Params
import com.ayushig.localrag.core.bundle.Bundle
import com.ayushig.localrag.core.bundle.BundleManifest
import com.ayushig.localrag.core.bundle.Cluster
import com.ayushig.localrag.core.bundle.StoredChunk
import com.ayushig.localrag.core.document.Chunk
import com.ayushig.localrag.core.index.Bm25Index
import com.ayushig.localrag.core.index.VectorIndex
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The four degradation states, asserted on the JVM with fake models.
 *
 * The spec asked for instrumented tests. These are stronger: an instrumented test would only ever
 * run in whichever configuration the device happens to support, and the states that matter most
 * are the ones where a model is absent.
 */
class DegradationMatrixTest {

    private val chunks = listOf(
        Chunk(
            chunkId = "margin#what", docId = "margin", title = "How margin works",
            heading = "What margin is", category = "orders", aliases = emptyList(), screen = null,
            appVersionMin = null, appVersionMax = null,
            embeddedText = "How margin works — What margin is\n\nMargin is collateral.",
            displayText = "Margin is collateral held against an open leveraged position.",
            tokenCount = 9,
        ),
        Chunk(
            chunkId = "gold#store", docId = "gold", title = "Where is my gold stored?",
            heading = "Allocated storage", category = "account", aliases = emptyList(), screen = null,
            appVersionMin = null, appVersionMax = null,
            embeddedText = "Where is my gold stored? — Allocated storage\n\nVault.",
            displayText = "Metal is held allocated in an insured vault.",
            tokenCount = 8,
        ),
    )

    private val clusters = listOf(
        Cluster(
            id = "withdrawal-timing",
            questions = listOf("how long does a withdrawal take", "when will my withdrawal arrive"),
            answer = "Most withdrawals settle within two working days.",
            chunkIds = emptyList(),
        ),
    )

    private val bundle = Bundle(
        manifest = BundleManifest(
            contentVersion = 1, builtAt = "1970-01-01T00:00:00Z", chunkCount = chunks.size,
            chunkerVersion = 3, bm25 = Bm25Params(1.2f, 0.75f, 9f),
        ),
        chunks = chunks.map(StoredChunk::from),
        bm25 = Bm25Index.build(chunks),
        vectors = floatArrayOf(1f, 0f, 0f, 1f),
        clusters = clusters,
    )

    /** Points at chunk 0, so a working embedder pulls the margin passage semantically. */
    private class FakeEmbedder : Embedder {
        override val dimensions = 2
        override fun embed(text: String) = floatArrayOf(1f, 0f)
        override fun close() = Unit
    }

    private class FakeGenerator(private val reply: String?) : TextGenerator {
        var calls = 0
        override suspend fun generate(prompt: String): String? {
            calls++
            return reply
        }
        override fun reset() = Unit
        override fun close() = Unit
    }

    private fun pipeline(embedder: Embedder?, generator: TextGenerator?) = AnswerPipeline(
        retriever = Retriever(
            bundle = bundle,
            vectorIndex = if (embedder == null) null else VectorIndex(bundle.vectors!!, 2),
            appVersion = "4.5",
        ),
        clusters = ClusterMatcher(clusters),
        embedder = embedder,
        generator = generator,
        topK = 4,
        categories = emptySet(),
        maxContextTokens = 1200,
        chunkCount = chunks.size,
    )

    private suspend fun answer(embedder: Embedder?, generator: TextGenerator?, query: String)
        : Triple<String, AnswerMode, List<Passage>> {
        val chunksOut = pipeline(embedder, generator).answer(query).toList()
        val sources = (chunksOut.first() as AnswerChunk.Sources).passages
        val text = chunksOut.filterIsInstance<AnswerChunk.Token>().joinToString("") { it.text }
        val mode = (chunksOut.last() as AnswerChunk.Done).mode
        return Triple(text, mode, sources)
    }

    // Grounded: every digit and most words come from the passage, so the gate allows it.
    private val grounded = "Margin is collateral held against an open leveraged position."

    @Test
    fun `state 1 - embedder yes, generator yes - hybrid retrieval, generated answer`() = runTest {
        val (text, mode, sources) = answer(FakeEmbedder(), FakeGenerator(grounded), "what is margin")
        assertEquals(AnswerMode.GENERATED, mode)
        assertEquals(grounded, text)
        assertTrue(sources.any { it.source == MatchSource.HYBRID })
    }

    @Test
    fun `state 2 - embedder no, generator yes - bm25 retrieval, generated answer`() = runTest {
        val (text, mode, sources) = answer(null, FakeGenerator(grounded), "what is margin")
        assertEquals(AnswerMode.GENERATED, mode)
        assertEquals(grounded, text)
        assertTrue(sources.all { it.source == MatchSource.BM25 })
    }

    @Test
    fun `state 3 - embedder yes, generator no - hybrid retrieval, best passage verbatim`() = runTest {
        val (text, mode, sources) = answer(FakeEmbedder(), null, "what is margin")
        assertEquals(AnswerMode.EXTRACTIVE, mode)
        assertEquals(chunks[0].displayText, text)
        assertTrue(sources.any { it.source == MatchSource.HYBRID })
    }

    @Test
    fun `state 4 - embedder no, generator no - bm25 retrieval, best passage verbatim`() = runTest {
        val (text, mode, sources) = answer(null, null, "what is margin")
        assertEquals(AnswerMode.EXTRACTIVE, mode)
        assertEquals(chunks[0].displayText, text)
        assertTrue(sources.all { it.source == MatchSource.BM25 })
    }

    @Test
    fun `a rejected generation falls back to extractive, never to an error`() = runTest {
        // An invented figure: the gate must discard it silently.
        val generator = FakeGenerator("Margin must stay above 250% at all times.")
        val (text, mode, _) = answer(null, generator, "what is margin")
        assertEquals(1, generator.calls)
        assertEquals(AnswerMode.EXTRACTIVE, mode)
        assertEquals(chunks[0].displayText, text)
    }

    @Test
    fun `a generation failure falls back to extractive, never to an error`() = runTest {
        val chunksOut = pipeline(null, FakeGenerator(null)).answer("what is margin").toList()
        assertTrue(chunksOut.none { it is AnswerChunk.Error })
        assertEquals(AnswerMode.EXTRACTIVE, (chunksOut.last() as AnswerChunk.Done).mode)
    }

    @Test
    fun `precomputed answers work with no models at all`() = runTest {
        val (text, mode, _) = answer(null, null, "how long does a withdrawal take")
        assertEquals(AnswerMode.PRECOMPUTED, mode)
        assertEquals("Most withdrawals settle within two working days.", text)
    }

    @Test
    fun `a precomputed answer beats generation`() = runTest {
        val generator = FakeGenerator(grounded)
        val (_, mode, _) = answer(FakeEmbedder(), generator, "how long does a withdrawal take")
        assertEquals(AnswerMode.PRECOMPUTED, mode)
        assertEquals(0, generator.calls)
    }

    @Test
    fun `sources are always emitted before any token`() = runTest {
        val emissions = pipeline(FakeEmbedder(), FakeGenerator(grounded))
            .answer("what is margin").toList()
        assertTrue(emissions.first() is AnswerChunk.Sources)
        val firstToken = emissions.indexOfFirst { it is AnswerChunk.Token }
        assertEquals(0, emissions.indexOfFirst { it is AnswerChunk.Sources })
        assertTrue(firstToken > 0)
    }
}

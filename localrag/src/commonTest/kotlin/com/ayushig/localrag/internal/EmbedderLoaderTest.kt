package com.ayushig.localrag.internal

import com.ayushig.localrag.EmbeddingBackend
import com.ayushig.localrag.EmbeddingEngine
import com.ayushig.localrag.EmbeddingSpec
import com.ayushig.localrag.core.bundle.EmbedderDescriptor
import com.ayushig.localrag.core.bundle.EmbeddingInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The parity contract on the embedding side: a bundle's vectors are only used with an embedder
 * that provably matches them, and any load failure degrades to BM25 rather than erroring.
 */
class EmbedderLoaderTest {

    private val manifest = EmbeddingInfo(
        modelId = "embed-v1",
        dimensions = 4,
        normalized = true,
        queryPrefix = "query: ",
        documentPrefix = "passage: ",
    )
    private val matching = EmbedderDescriptor(
        modelId = "embed-v1",
        dimensions = 4,
        normalized = true,
        queryPrefix = "query: ",
        documentPrefix = "passage: ",
    )

    private class FakeEmbedding(override val dimensions: Int) : EmbeddingEngine {
        override fun embed(text: String): FloatArray? = FloatArray(dimensions) { 1f }

        override fun close() = Unit
    }

    private class FakeBackend(private val fail: Boolean = false) : EmbeddingBackend {
        var loads = 0
        var lastSpec: EmbeddingSpec? = null

        override fun load(modelPath: String, cacheDir: String, spec: EmbeddingSpec): EmbeddingEngine {
            loads++
            lastSpec = spec
            if (fail) error("embedding engine failed")
            return FakeEmbedding(spec.dimensions)
        }
    }

    private fun create(
        backend: EmbeddingBackend,
        runtime: EmbedderDescriptor = matching,
        reasons: MutableList<String> = mutableListOf(),
    ): EmbeddingEngine? = EmbedderLoader.createIfCompatible(
        backend = backend,
        modelPath = "/models/e.tflite",
        cacheDir = "/cache",
        manifest = manifest,
        runtime = runtime,
        onIncompatible = { reasons += it },
    )

    @Test
    fun aMatchingEmbedderLoadsWithTheRuntimeSpec() {
        val backend = FakeBackend()
        val engine = assertNotNull(create(backend))

        assertEquals(1, backend.loads)
        assertEquals(EmbeddingSpec(normalize = true, dimensions = 4, queryPrefix = "query: "), backend.lastSpec)
        assertEquals(4, engine.dimensions)
    }

    @Test
    fun aDimensionMismatchDropsTheEmbedderWithoutLoadingTheModel() {
        val backend = FakeBackend()
        val reasons = mutableListOf<String>()

        assertNull(create(backend, matching.copy(dimensions = 8), reasons))

        assertEquals(0, backend.loads)
        assertTrue(reasons.single().contains("dimensions"), reasons.single())
    }

    @Test
    fun aPrefixMismatchDropsTheEmbedder() {
        val backend = FakeBackend()
        val reasons = mutableListOf<String>()

        assertNull(create(backend, matching.copy(queryPrefix = "search: "), reasons))

        assertEquals(0, backend.loads)
        assertTrue(reasons.single().contains("query prefix"), reasons.single())
    }

    @Test
    fun everyMismatchIsReportedTogether() {
        val reasons = mutableListOf<String>()

        assertNull(create(FakeBackend(), matching.copy(modelId = "other", normalized = false), reasons))

        val message = reasons.single()
        assertTrue(message.contains("model id") && message.contains("normalized"), message)
    }

    @Test
    fun aLoadFailureDegradesToNullWithAReason() {
        val reasons = mutableListOf<String>()

        assertNull(create(FakeBackend(fail = true), reasons = reasons))

        assertTrue(reasons.single().contains("embedding engine failed"), reasons.single())
    }
}

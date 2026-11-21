package com.ayushig.localrag.core

import com.ayushig.localrag.core.bundle.EmbedderDescriptor
import com.ayushig.localrag.core.bundle.EmbeddingInfo
import com.ayushig.localrag.core.bundle.EmbeddingParity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EmbeddingParityTest {

    private val manifest = EmbeddingInfo(
        modelId = "embeddinggemma-300m-seq256",
        dimensions = 256,
        normalized = true,
        queryPrefix = "task: search result | query: ",
        documentPrefix = "title: none | text: ",
    )

    private val runtime = EmbedderDescriptor(
        modelId = manifest.modelId,
        dimensions = manifest.dimensions,
        normalized = manifest.normalized,
        queryPrefix = manifest.queryPrefix,
        documentPrefix = manifest.documentPrefix,
    )

    @Test
    fun `identical configuration is compatible`() {
        assertEquals(EmbeddingParity.Result.Compatible, EmbeddingParity.check(manifest, runtime))
    }

    @Test
    fun `a dimension mismatch is caught`() {
        val result = assertIs<EmbeddingParity.Result.Incompatible>(
            EmbeddingParity.check(manifest, runtime.copy(dimensions = 128)),
        )
        assertTrue(result.reasons.single().contains("dimensions: bundle 256, runtime 128"))
    }

    @Test
    fun `a query prefix mismatch is caught`() {
        // The failure this whole mechanism exists for: nothing errors, results just get worse.
        val result = assertIs<EmbeddingParity.Result.Incompatible>(
            EmbeddingParity.check(manifest, runtime.copy(queryPrefix = "query: ")),
        )
        assertTrue(result.reasons.single().contains("query prefix"))
    }

    @Test
    fun `a document prefix mismatch is caught`() {
        assertIs<EmbeddingParity.Result.Incompatible>(
            EmbeddingParity.check(manifest, runtime.copy(documentPrefix = "text: ")),
        )
    }

    @Test
    fun `a model id mismatch is caught`() {
        assertIs<EmbeddingParity.Result.Incompatible>(
            EmbeddingParity.check(manifest, runtime.copy(modelId = "some-other-model")),
        )
    }

    @Test
    fun `unnormalized vectors are caught`() {
        assertIs<EmbeddingParity.Result.Incompatible>(
            EmbeddingParity.check(manifest, runtime.copy(normalized = false)),
        )
    }

    @Test
    fun `every mismatch is reported, not just the first`() {
        val result = assertIs<EmbeddingParity.Result.Incompatible>(
            EmbeddingParity.check(
                manifest,
                runtime.copy(dimensions = 128, queryPrefix = "q: ", modelId = "other"),
            ),
        )
        assertEquals(3, result.reasons.size, result.reasons.toString())
    }
}

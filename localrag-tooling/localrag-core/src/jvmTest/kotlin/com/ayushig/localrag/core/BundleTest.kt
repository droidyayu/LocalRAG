package com.ayushig.localrag.core

import com.ayushig.localrag.core.bundle.BundleEntries
import com.ayushig.localrag.core.bundle.BundleFormatException
import com.ayushig.localrag.core.bundle.BundleReader
import com.ayushig.localrag.core.bundle.BundleWriter
import com.ayushig.localrag.core.bundle.EmbeddingInfo
import com.ayushig.localrag.core.document.Chunker
import com.ayushig.localrag.core.index.VectorIndex
import com.ayushig.localrag.core.text.Tokenizer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BundleTest {

    private val builtAt = "2026-09-13T10:00:00Z"

    private fun write(
        vectors: FloatArray? = null,
        embedding: EmbeddingInfo? = null,
    ): ByteArray = ByteArrayOutputStream().also { out ->
        BundleWriter().write(
            output = out,
            chunks = Fixture.chunks,
            vectors = vectors,
            clusters = emptyList(),
            contentVersion = 47,
            builtAt = builtAt,
            embedding = embedding,
        )
    }.toByteArray()

    private fun entries(bytes: ByteArray): List<String> = buildList {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                add(entry.name)
                zip.closeEntry()
            }
        }
    }

    @Test
    fun `a bm25-only bundle omits vectors and the embedding block`() {
        val bytes = write()
        assertEquals(
            listOf(
                BundleEntries.MANIFEST,
                BundleEntries.CHUNKS,
                BundleEntries.BM25,
                BundleEntries.CLUSTERS,
            ),
            entries(bytes),
        )

        val bundle = BundleReader().read(ByteArrayInputStream(bytes))
        assertNull(bundle.manifest.embedding)
        assertNull(bundle.vectors)
        assertEquals(Fixture.chunks.size, bundle.manifest.chunkCount)
        assertEquals(Chunker.VERSION, bundle.manifest.chunkerVersion)
        assertEquals(47, bundle.manifest.contentVersion)
    }

    @Test
    fun `a bundle with vectors round trips`() {
        val dimensions = 4
        val embedding = EmbeddingInfo(
            modelId = "embeddinggemma-300m-seq256",
            dimensions = dimensions,
            normalized = true,
            queryPrefix = "task: search result | query: ",
            documentPrefix = "title: none | text: ",
        )
        val vectors = FloatArray(Fixture.chunks.size * dimensions) { (it % 7) / 7f }
        val bytes = write(vectors, embedding)

        assertTrue(entries(bytes).contains(BundleEntries.VECTORS))
        val bundle = BundleReader().read(ByteArrayInputStream(bytes))
        assertEquals(embedding, bundle.manifest.embedding)
        val restored = assertNotNull(bundle.vectors)
        assertEquals(vectors.size, restored.size)
        vectors.indices.forEach { assertTrue(abs(vectors[it] - restored[it]) < 1e-6f) }
    }

    @Test
    fun `vectors without an embedding block are refused at write time`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            write(vectors = FloatArray(4), embedding = null)
        }
        assertTrue(failure.message!!.contains("parity"))
    }

    @Test
    fun `a vector count that disagrees with the chunk count is refused`() {
        val embedding = EmbeddingInfo("m", 4, true, "q", "d")
        assertFailsWith<IllegalArgumentException> { write(FloatArray(8), embedding) }
    }

    @Test
    fun `a truncated bundle fails loudly`() {
        assertFailsWith<BundleFormatException> {
            BundleReader().read(ByteArrayInputStream(ByteArrayOutputStream().toByteArray()))
        }
    }

    @Test
    fun `scores survive the bundle, not just the snapshot`() {
        // The regression guard for the build/runtime split: index here, read it back, compare.
        val bundle = BundleReader().read(ByteArrayInputStream(write()))
        val direct = com.ayushig.localrag.core.index.Bm25Index.build(Fixture.chunks)

        for (query in listOf("standing order", "withdraw money", "margin level", "kyc")) {
            val terms = Tokenizer.tokenize(query)
            val fromBundle = bundle.bm25.search(terms, 5)
            val fromMemory = direct.search(terms, 5)
            assertEquals(fromMemory.map { it.chunkIndex }, fromBundle.map { it.chunkIndex }, query)
            fromMemory.zip(fromBundle).forEach { (a, b) ->
                assertTrue(abs(a.score - b.score) < 1e-6f, "score drift on $query")
            }
        }
    }

    @Test
    fun `chunk order in the bundle matches vector row order`() {
        val bundle = BundleReader().read(ByteArrayInputStream(write()))
        assertEquals(Fixture.chunks.map { it.chunkId }, bundle.chunks.map { it.chunkId })
    }

    @Test
    fun `identical input produces a byte-identical bundle`() {
        // Golden test: catches an accidental chunker or tokenizer change.
        assertTrue(write().contentEquals(write()))
    }

    @Test
    fun `normalized vectors keep cosine as a dot product through the bundle`() {
        val dimensions = 3
        val embedding = EmbeddingInfo("m", dimensions, true, "q", "d")
        val vectors = FloatArray(Fixture.chunks.size * dimensions)
        for (chunk in Fixture.chunks.indices) {
            val unit = VectorIndex.normalize(floatArrayOf(1f + chunk, 2f, 3f))
            unit.copyInto(vectors, chunk * dimensions)
        }
        val bundle = BundleReader().read(ByteArrayInputStream(write(vectors, embedding)))
        val index = VectorIndex(assertNotNull(bundle.vectors), dimensions)
        val best = index.search(VectorIndex.normalize(floatArrayOf(1f, 2f, 3f)), topK = 1).single()
        assertEquals(0, best.chunkIndex)
        assertTrue(best.score <= 1.0001f)
    }
}

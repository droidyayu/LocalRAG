package com.ayushig.localrag.android

import com.ayushig.localrag.android.internal.Retriever
import com.ayushig.localrag.core.bundle.Bundle
import com.ayushig.localrag.core.bundle.BundleManifest
import com.ayushig.localrag.core.bundle.Bm25Params
import com.ayushig.localrag.core.bundle.StoredChunk
import com.ayushig.localrag.core.document.Chunk
import com.ayushig.localrag.core.index.Bm25Index
import com.ayushig.localrag.core.index.VectorIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plain JVM tests: the retriever needs no Android context, which is the point of keeping the
 * scoring in core and the platform concerns out of it.
 */
class RetrieverTest {

    private fun chunk(
        id: String,
        title: String,
        heading: String,
        text: String,
        category: String = "orders",
        aliases: List<String> = emptyList(),
        minVersion: String? = null,
        maxVersion: String? = null,
    ) = Chunk(
        chunkId = id,
        docId = id.substringBefore(Char(35)),
        title = title,
        heading = heading,
        category = category,
        aliases = aliases,
        screen = null,
        appVersionMin = minVersion,
        appVersionMax = maxVersion,
        embeddedText = "$title — $heading\n\n$text",
        displayText = text,
        tokenCount = 10,
    )

    private val chunks = listOf(
        chunk("gtt#what", "What is a GTT order?", "What it does", "A GTT order stays pending.",
            aliases = listOf("standing order")),
        chunk("kyc#status", "Is my KYC complete?", "Where to check", "Your KYC status is on profile.",
            category = "kyc"),
        chunk("new#feature", "A newer feature", "Overview", "Only on recent builds.",
            minVersion = "9.0"),
        chunk("old#feature", "A retired feature", "Overview", "Removed after version four.",
            maxVersion = "4.0"),
    )

    private fun bundle(vectors: FloatArray? = null, dimensions: Int = 0): Bundle = Bundle(
        manifest = BundleManifest(
            contentVersion = 1,
            builtAt = "1970-01-01T00:00:00Z",
            chunkCount = chunks.size,
            chunkerVersion = 3,
            bm25 = Bm25Params(1.2f, 0.75f, 10f),
        ),
        chunks = chunks.map(StoredChunk::from),
        bm25 = Bm25Index.build(chunks),
        vectors = vectors,
        clusters = emptyList(),
    )

    private fun retriever(
        appVersion: String = "4.5",
        vectors: FloatArray? = null,
        dimensions: Int = 0,
    ) = Retriever(
        bundle = bundle(vectors, dimensions),
        vectorIndex = if (vectors == null) null else VectorIndex(vectors, dimensions),
        appVersion = appVersion,
    )

    @Test
    fun `retrieves by alias and reports the bm25 source`() {
        val passages = retriever().retrieve("standing order", 4, null, emptySet())
        assertEquals("gtt#what", passages.first().chunkId)
        assertEquals(MatchSource.BM25, passages.first().source)
    }

    @Test
    fun `an empty query retrieves nothing`() {
        assertTrue(retriever().retrieve("   ", 4, null, emptySet()).isEmpty())
        // Stopwords only: nothing left to search for.
        assertTrue(retriever().retrieve("is the a", 4, null, emptySet()).isEmpty())
    }

    @Test
    fun `a chunk above the installed version is filtered out`() {
        val passages = retriever(appVersion = "4.5").retrieve("feature", 4, null, emptySet())
        assertTrue(passages.none { it.chunkId == "new#feature" })
    }

    @Test
    fun `a chunk below its maximum version is filtered out`() {
        val passages = retriever(appVersion = "4.5").retrieve("feature", 4, null, emptySet())
        assertTrue(passages.none { it.chunkId == "old#feature" })
    }

    @Test
    fun `a newer install sees the newer chunk`() {
        val passages = retriever(appVersion = "9.1").retrieve("feature", 4, null, emptySet())
        assertTrue(passages.any { it.chunkId == "new#feature" })
    }

    @Test
    fun `category filtering keeps only the requested category`() {
        val passages = retriever().retrieve("complete status order", 4, null, setOf("kyc"))
        assertTrue(passages.isNotEmpty())
        assertTrue(passages.all { it.docId == "kyc" })
    }

    @Test
    fun `topK caps the result count`() {
        assertEquals(1, retriever().retrieve("order pending status", 1, null, emptySet()).size)
    }

    @Test
    fun `a vector hit that bm25 also found is reported as hybrid`() {
        val dimensions = 2
        // Chunk 0 points the same way as the query; the rest point elsewhere.
        val vectors = floatArrayOf(1f, 0f, 0f, 1f, 0f, 1f, 0f, 1f)
        val passages = retriever(vectors = vectors, dimensions = dimensions)
            .retrieve("standing order", 4, floatArrayOf(1f, 0f), emptySet())

        val first = passages.first { it.chunkId == "gtt#what" }
        assertEquals(MatchSource.HYBRID, first.source)
    }

    @Test
    fun `a vector only hit is reported as vector`() {
        val dimensions = 2
        val vectors = floatArrayOf(0f, 1f, 1f, 0f, 0f, 1f, 0f, 1f)
        // "standing order" matches chunk 0 lexically; the query vector points at chunk 1.
        val passages = retriever(vectors = vectors, dimensions = dimensions)
            .retrieve("standing order", 4, floatArrayOf(1f, 0f), emptySet())

        assertEquals(MatchSource.VECTOR, passages.first { it.chunkId == "kyc#status" }.source)
    }

    @Test
    fun `passages carry the display text, not the embedded text`() {
        val passage = retriever().retrieve("standing order", 1, null, emptySet()).single()
        assertEquals("A GTT order stays pending.", passage.text)
        assertEquals("What it does", passage.heading)
    }
}

package com.ayushig.localrag.core

import com.ayushig.localrag.core.index.Bm25Index
import com.ayushig.localrag.core.index.ReciprocalRankFusion
import com.ayushig.localrag.core.index.ScoredChunk
import com.ayushig.localrag.core.index.VectorIndex
import com.ayushig.localrag.core.index.VersionFilter
import com.ayushig.localrag.core.text.Tokenizer
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RetrievalTest {

    private val index = Bm25Index.build(Fixture.chunks)

    private fun top(query: String, n: Int = 3): List<String> =
        index.search(Tokenizer.tokenize(query), n).map { Fixture.chunks[it.chunkIndex].chunkId }

    @Test
    fun `an alias retrieves the document that declares it`() {
        // "standing order" appears nowhere in the GTT body - only in its aliases.
        assertEquals("gtt-order-basics", top("standing order").first().substringBefore("#"))
    }

    @Test
    fun `aliases outrank an incidental body mention`() {
        assertEquals("order-types", top("bracket order").first().substringBefore("#"))
    }

    @Test
    fun `a plain question finds its document`() {
        assertEquals("order-pending", top("why is my order still pending").first().substringBefore("#"))
        assertEquals("withdrawal-pending", top("why is my withdrawal pending").first().substringBefore("#"))
        assertEquals("kyc-status", top("is my kyc complete").first().substringBefore("#"))
    }

    @Test
    fun `the draft document is never indexed`() {
        assertTrue(Fixture.chunks.none { it.docId == "app-notifications" })
        assertTrue(top("price alert", 5).none { it.startsWith("app-notifications") })
    }

    @Test
    fun `an unmatched query returns nothing rather than noise`() {
        assertTrue(index.search(Tokenizer.tokenize("zzzz qqqq"), 5).isEmpty())
    }

    @Test
    fun `results are deterministic and ordered by score`() {
        val first = index.search(Tokenizer.tokenize("margin"), 5)
        val second = index.search(Tokenizer.tokenize("margin"), 5)
        assertEquals(first, second)
        assertEquals(first.sortedByDescending { it.score }.map { it.chunkIndex }, first.map { it.chunkIndex })
    }

    @Test
    fun `an index survives a snapshot round trip with identical scores`() {
        // The parity guard for the whole build/runtime split.
        val restored = Bm25Index.from(index.snapshot())
        for (query in listOf("margin level", "withdraw money", "kyc documents", "brokerage")) {
            val terms = Tokenizer.tokenize(query)
            val before = index.search(terms, 5)
            val after = restored.search(terms, 5)
            assertEquals(before.map { it.chunkIndex }, after.map { it.chunkIndex }, query)
            before.zip(after).forEach { (a, b) ->
                assertTrue(abs(a.score - b.score) < 1e-6f, "score drift on $query")
            }
        }
    }

    @Test
    fun `vector search ranks by cosine similarity`() {
        val vectors = floatArrayOf(
            1f, 0f,
            0f, 1f,
            0.7071f, 0.7071f,
        )
        val results = VectorIndex(vectors, dimensions = 2).search(floatArrayOf(1f, 0f), topK = 3)
        assertEquals(listOf(0, 2, 1), results.map { it.chunkIndex })
    }

    @Test
    fun `normalization makes a dot product a cosine`() {
        val normalized = VectorIndex.normalize(floatArrayOf(3f, 4f))
        assertTrue(abs(normalized[0] - 0.6f) < 1e-6f)
        assertTrue(abs(normalized[1] - 0.8f) < 1e-6f)
        assertEquals(floatArrayOf(0f, 0f).toList(), VectorIndex.normalize(floatArrayOf(0f, 0f)).toList())
    }

    @Test
    fun `fusion rewards agreement between the two rankings`() {
        val bm25 = listOf(ScoredChunk(1, 9f), ScoredChunk(2, 8f), ScoredChunk(3, 7f))
        val vector = listOf(ScoredChunk(3, 0.9f), ScoredChunk(1, 0.8f), ScoredChunk(9, 0.7f))
        val fused = ReciprocalRankFusion.fuse(bm25, vector).map { it.chunkIndex }
        // 1 is first in one list and second in the other, so it wins overall.
        assertEquals(1, fused.first())
        assertTrue(fused.indexOf(3) < fused.indexOf(2))
        assertTrue(fused.contains(9))
    }

    @Test
    fun `version filter keeps chunks inside the installed range`() {
        assertTrue(VersionFilter.matches("4.2", minimum = "4.2", maximum = null))
        assertTrue(VersionFilter.matches("4.10.1", minimum = "4.2", maximum = null))
        assertFalse(VersionFilter.matches("4.1", minimum = "4.2", maximum = null))
        assertFalse(VersionFilter.matches("5.0", minimum = null, maximum = "4.9"))
        assertTrue(VersionFilter.matches("4.5", minimum = null, maximum = null))
    }

    @Test
    fun `an unparseable version never hides documentation`() {
        assertTrue(VersionFilter.matches("nightly", minimum = "4.2", maximum = null))
    }
}

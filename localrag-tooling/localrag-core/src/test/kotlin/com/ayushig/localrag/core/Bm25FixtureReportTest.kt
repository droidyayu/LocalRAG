package com.ayushig.localrag.core

import com.ayushig.localrag.core.index.Bm25Index
import com.ayushig.localrag.core.text.Tokenizer
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Prints the BM25 ranking over the frozen corpus so a human can eyeball it.
 *
 * It asserts only that every query retrieves something; the value is the printed table, which is
 * how the corpus and the weights get sanity-checked by a person rather than by a threshold.
 */
class Bm25FixtureReportTest {

    private val queries = listOf(
        "standing order",
        "why is my order still pending",
        "why was my order rejected",
        "how much margin am i using",
        "is my kyc complete",
        "which documents do i need",
        "how do i add funds",
        "why is my withdrawal pending",
        "what do i pay to trade",
        "overnight financing",
        "where is my gold stored",
        "two factor authentication",
        "close my account",
        "change my bank details",
        "managed portfolio risk",
    )

    @Test
    fun `report top three per query`() {
        val index = Bm25Index.build(Fixture.chunks)
        println("corpus: ${Fixture.published.size} published documents, ${Fixture.chunks.size} chunks")
        println("query | rank 1 | rank 2 | rank 3")
        for (query in queries) {
            val hits = index.search(Tokenizer.tokenize(query), 3)
            assertTrue(hits.isNotEmpty(), "no hit for: $query")
            val rendered = hits.joinToString(" | ") { hit ->
                val chunk = Fixture.chunks[hit.chunkIndex]
                chunk.chunkId + " " + String.format("%.2f", hit.score)
            }
            println(query + " | " + rendered)
        }
    }
}

package com.ayushig.localrag.core

import com.ayushig.localrag.core.index.Bm25Index
import com.ayushig.localrag.core.text.Tokenizer
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The retrieval evaluation harness.
 *
 * It measures the same scoring path the device uses, so a regression shows up here rather than in
 * a user complaint. Expectations are recorded per document rather than per chunk: BM25 with
 * document-level field weights picks the right document reliably but orders sections within it
 * almost arbitrarily, and asserting a chunk would be asserting noise. The vector leg is what will
 * make section choice meaningful, and this harness is how that improvement gets measured.
 */
class EvalHarnessTest {

    private data class Case(val query: String, val expectedDocId: String)

    private val cases: List<Case> by lazy {
        val url = requireNotNull(javaClass.classLoader.getResource("eval/queries.tsv"))
        File(url.toURI()).readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line ->
                val parts = line.split(Char(9))
                Case(parts[0].trim(), parts[1].trim())
            }
    }

    @Test
    fun `report hit rate at one and at four`() {
        val index = Bm25Index.build(Fixture.chunks)
        val docIds = Fixture.chunks.map { it.docId }

        var hitAt1 = 0
        var hitAt4 = 0
        val misses = mutableListOf<String>()

        for (case in cases) {
            val ranked = index.search(Tokenizer.tokenize(case.query), 8)
                .map { docIds[it.chunkIndex] }
                .distinct()

            if (ranked.firstOrNull() == case.expectedDocId) hitAt1++
            if (ranked.take(4).contains(case.expectedDocId)) {
                hitAt4++
            } else {
                misses += "${case.query} -> expected ${case.expectedDocId}, got ${ranked.take(4)}"
            }
        }

        val total = cases.size
        println("eval: $total queries over ${Fixture.published.size} documents")
        println("hit-rate@1: %d/%d (%.1f%%)".format(hitAt1, total, hitAt1 * 100.0 / total))
        println("hit-rate@4: %d/%d (%.1f%%)".format(hitAt4, total, hitAt4 * 100.0 / total))
        if (misses.isNotEmpty()) {
            println("misses at 4:")
            misses.forEach { println("  $it") }
        }

        // A floor, not a target. It exists so a change that quietly wrecks retrieval fails the
        // build; the printed numbers are what a human reads.
        assertTrue(hitAt1 * 100 / total >= 70, "hit-rate@1 fell to ${hitAt1 * 100 / total}%")
        assertTrue(hitAt4 * 100 / total >= 85, "hit-rate@4 fell to ${hitAt4 * 100 / total}%")
    }

    @Test
    fun `every expected document exists in the corpus`() {
        val known = Fixture.published.mapTo(mutableSetOf()) { it.id }
        val unknown = cases.map { it.expectedDocId }.distinct().filter { it !in known }
        assertTrue(unknown.isEmpty(), "eval set references unknown documents: $unknown")
    }
}

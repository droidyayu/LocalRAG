package com.ayushig.localrag.demo.assistant

import com.ayushig.localrag.android.MatchSource
import com.ayushig.localrag.android.Passage
import com.ayushig.localrag.demo.data.assistant.DocumentationContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pre-searched documentation replaces the old search tool call.
 *
 * The contract: greetings match nothing and inject nothing, real questions keep
 * their top passages inside a tight budget, and the top passage always rides
 * along — a follow-up without evidence is unanswerable, while a slightly long
 * context is merely expensive.
 */
class DocumentationContextTest {

    private fun passage(id: String, text: String) = Passage(
        chunkId = id,
        docId = "doc",
        title = "Title $id",
        heading = "Heading",
        text = text,
        screenLink = null,
        score = 1.0f,
        source = MatchSource.BM25,
    )

    private val context = DocumentationContext { emptyList() }

    @Test
    fun `empty retrieval injects nothing`() {
        assertTrue(context.select(emptyList()).isEmpty())
    }

    @Test
    fun `keeps at most two passages`() {
        val selected = context.select(
            listOf(passage("a", "x".repeat(100)), passage("b", "y".repeat(100)), passage("c", "z".repeat(100))),
        )
        assertEquals(listOf("a", "b"), selected.map { passage -> passage.chunkId })
    }

    @Test
    fun `top passage rides along even past the char budget`() {
        val selected = context.select(listOf(passage("a", "x".repeat(5000))))
        assertEquals(listOf("a"), selected.map { passage -> passage.chunkId })
    }

    @Test
    fun `second passage drops past the char budget`() {
        val selected = context.select(
            listOf(passage("a", "x".repeat(1000)), passage("b", "y".repeat(1000))),
        )
        assertEquals(listOf("a"), selected.map { passage -> passage.chunkId })
    }

    @Test
    fun `two small passages both ride along`() {
        val selected = context.select(
            listOf(passage("a", "x".repeat(100)), passage("b", "y".repeat(100))),
        )
        assertEquals(listOf("a", "b"), selected.map { passage -> passage.chunkId })
    }
}

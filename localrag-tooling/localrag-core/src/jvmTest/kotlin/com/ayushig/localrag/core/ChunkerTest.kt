package com.ayushig.localrag.core

import com.ayushig.localrag.core.document.ChunkerConfig
import com.ayushig.localrag.core.document.Chunker
import com.ayushig.localrag.core.document.DocumentIssue
import com.ayushig.localrag.core.document.DocumentStatus
import com.ayushig.localrag.core.document.ParsedDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChunkerTest {

    private fun document(body: String) = ParsedDocument(
        id = "gtt-order-basics",
        title = "What is a GTT order?",
        category = "orders",
        lastReviewed = "2026-09-01",
        status = DocumentStatus.PUBLISHED,
        body = body,
        sourcePath = "gtt.md",
    )

    @Test
    fun `splits on level two headings only`() {
        val chunks = Chunker().chunk(
            document(
                """
                ## What it does
                Stays pending.

                ### A deeper heading
                Still part of the same section.

                ## How to place one
                Open the instrument page.
                """.trimIndent(),
            ),
        ).chunks

        assertEquals(2, chunks.size)
        assertEquals(listOf("What it does", "How to place one"), chunks.map { it.heading })
        assertTrue(chunks[0].displayText.contains("A deeper heading"))
    }

    @Test
    fun `content before the first heading becomes a chunk with no heading`() {
        val chunks = Chunker().chunk(
            document("An introduction.\n\n## What it does\nStays pending."),
        ).chunks

        assertNull(chunks.first().heading)
        assertEquals("gtt-order-basics#intro", chunks.first().chunkId)
    }

    @Test
    fun `embedded text carries the title and heading, displayed text does not`() {
        val chunk = Chunker().chunk(document("## How to place one\nTap **Confirm**.")).chunks.single()

        assertEquals("What is a GTT order? — How to place one\n\nTap Confirm.", chunk.embeddedText)
        assertEquals("Tap **Confirm**.", chunk.displayText)
    }

    @Test
    fun `chunk ids are stable and slugified`() {
        val chunk = Chunker().chunk(document("## How long it lasts?\nOne year.")).chunks.single()
        assertEquals("gtt-order-basics#how-long-it-lasts", chunk.chunkId)
    }

    @Test
    fun `an oversized section warns and names the heading`() {
        val body = "## Long one\n" + "word ".repeat(30)
        val issues = Chunker(ChunkerConfig(maxChunkTokens = 20)).chunk(document(body)).issues
        val issue = issues.single()
        assertEquals(DocumentIssue.Severity.WARNING, issue.severity)
        assertTrue(issue.message.contains("Long one"), issue.message)
        assertEquals("gtt.md", issue.sourcePath)
    }

    @Test
    fun `a section over twice the limit is an error, never split silently`() {
        val body = "## Very long one\n" + "word ".repeat(60)
        val result = Chunker(ChunkerConfig(maxChunkTokens = 20)).chunk(document(body))
        assertEquals(DocumentIssue.Severity.ERROR, result.issues.single().severity)
        assertEquals(1, result.chunks.size)
    }

    @Test
    fun `empty sections are dropped`() {
        val chunks = Chunker().chunk(document("## Empty\n\n## Real\nSomething.")).chunks
        assertEquals(listOf("Real"), chunks.map { it.heading })
    }

    @Test
    fun `chunker version is pinned`() {
        assertEquals(3, Chunker.VERSION)
    }
}

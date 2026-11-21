package com.ayushig.localrag.core

import com.ayushig.localrag.core.document.DocumentIssue
import com.ayushig.localrag.core.document.DocumentStatus
import com.ayushig.localrag.core.document.FrontMatterParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FrontMatterParserTest {

    private val valid = """
        ---
        id: gtt-order-basics
        title: What is a GTT order?
        category: orders
        aliases: [good till triggered, standing order]
        screen: app://orders/gtt
        app_version_min: "4.2"
        app_version_max: null
        last_reviewed: 2026-09-01
        status: published
        ---

        ## What it does
        A GTT order stays pending.
    """.trimIndent()

    @Test
    fun `parses every field`() {
        val result = FrontMatterParser.parse("gtt.md", valid)
        val document = assertNotNull(result.document)
        assertTrue(result.issues.isEmpty(), result.issues.toString())
        assertEquals("gtt-order-basics", document.id)
        assertEquals("What is a GTT order?", document.title)
        assertEquals("orders", document.category)
        assertEquals(listOf("good till triggered", "standing order"), document.aliases)
        assertEquals("app://orders/gtt", document.screen)
        assertEquals("4.2", document.appVersionMin)
        assertNull(document.appVersionMax)
        assertEquals(DocumentStatus.PUBLISHED, document.status)
        assertTrue(document.body.startsWith("## What it does"))
    }

    @Test
    fun `reports a missing front matter block against line 1`() {
        val result = FrontMatterParser.parse("bad.md", "# Just a heading\n")
        assertNull(result.document)
        assertEquals(1, result.issues.single().line)
        assertTrue(result.issues.single().message.contains("missing front matter"))
    }

    @Test
    fun `reports an unclosed front matter block`() {
        val result = FrontMatterParser.parse("bad.md", "---\nid: x\ntitle: y\n")
        assertNull(result.document)
        assertTrue(result.issues.single().message.contains("never closed"))
    }

    @Test
    fun `names the missing field`() {
        val result = FrontMatterParser.parse(
            "bad.md",
            "---\nid: x\ntitle: y\nlast_reviewed: 2026-01-01\nstatus: published\n---\nbody",
        )
        assertNull(result.document)
        assertTrue(result.issues.any { it.message.contains("`category`") }, result.issues.toString())
    }

    @Test
    fun `reports an unknown status with its line number`() {
        val result = FrontMatterParser.parse(
            "bad.md",
            "---\nid: x\ntitle: y\ncategory: orders\nlast_reviewed: 2026-01-01\nstatus: live\n---\nbody",
        )
        val issue = result.issues.single { it.message.contains("unknown status") }
        assertEquals(6, issue.line)
    }

    @Test
    fun `rejects an id that is not kebab-case`() {
        val result = FrontMatterParser.parse(
            "bad.md",
            "---\nid: GTT_Order\ntitle: y\ncategory: orders\nlast_reviewed: 2026-01-01\nstatus: published\n---\nbody",
        )
        assertTrue(result.issues.any { it.message.contains("kebab-case") })
    }

    @Test
    fun `rejects a malformed review date`() {
        val result = FrontMatterParser.parse(
            "bad.md",
            "---\nid: x\ntitle: y\ncategory: orders\nlast_reviewed: yesterday\nstatus: published\n---\nbody",
        )
        assertTrue(result.issues.any { it.message.contains("ISO date") })
    }

    @Test
    fun `issue renders as file line message`() {
        val issue = DocumentIssue("docs/a.md", 7, "boom", DocumentIssue.Severity.ERROR)
        assertEquals("docs/a.md:7: error: boom", issue.toString())
    }
}

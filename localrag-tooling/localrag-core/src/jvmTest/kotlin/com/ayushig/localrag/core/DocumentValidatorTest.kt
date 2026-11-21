package com.ayushig.localrag.core

import com.ayushig.localrag.core.document.DocumentStatus
import com.ayushig.localrag.core.document.DocumentValidator
import com.ayushig.localrag.core.document.ParsedDocument
import com.ayushig.localrag.core.document.ValidationConfig
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocumentValidatorTest {

    private val today = LocalDate.parse("2026-09-13")

    private fun document(
        id: String = "a-doc",
        category: String = "orders",
        screen: String? = null,
        lastReviewed: String = "2026-09-01",
        path: String = "a.md",
    ) = ParsedDocument(
        id = id,
        title = "Title",
        category = category,
        screen = screen,
        lastReviewed = lastReviewed,
        status = DocumentStatus.PUBLISHED,
        body = "## H\ntext",
        sourcePath = path,
    )

    private val validator = DocumentValidator(
        ValidationConfig(
            categories = listOf("orders", "kyc"),
            screenUriPattern = "^app://[a-z0-9/-]+$",
            staleAfterDays = 180,
        ),
    )

    @Test
    fun `a clean corpus produces no issues`() {
        assertTrue(validator.validate(listOf(document()), today).isEmpty())
    }

    @Test
    fun `duplicate ids name both files`() {
        val issues = validator.validate(
            listOf(document(path = "a.md"), document(path = "b.md")),
            today,
        )
        assertEquals(2, issues.size)
        assertTrue(issues.any { it.sourcePath == "a.md" && it.message.contains("b.md") })
        assertTrue(issues.any { it.sourcePath == "b.md" && it.message.contains("a.md") })
    }

    @Test
    fun `an unknown category lists the configured ones`() {
        val issue = validator.validate(listOf(document(category = "weather")), today).single()
        assertTrue(issue.message.contains("orders, kyc"), issue.message)
    }

    @Test
    fun `a screen link must match the configured pattern`() {
        assertTrue(validator.validate(listOf(document(screen = "app://orders/gtt")), today).isEmpty())
        val issue = validator.validate(listOf(document(screen = "https://x.com")), today).single()
        assertTrue(issue.message.contains("does not match"))
    }

    @Test
    fun `a stale document fails with its age`() {
        val issue = validator.validate(listOf(document(lastReviewed = "2025-01-01")), today).single()
        assertTrue(issue.message.contains("620 days ago"), issue.message)
    }

    @Test
    fun `the whole fixture corpus is valid`() {
        assertTrue(
            DocumentValidator(
                ValidationConfig(
                    categories = listOf("orders", "account", "funds", "charges", "kyc"),
                    screenUriPattern = "^app://[a-z0-9/-]+$",
                    staleAfterDays = 3650,
                ),
            ).validate(Fixture.documents, today).isEmpty(),
        )
    }
}

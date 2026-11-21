package com.ayushig.localrag.core.document

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Date handling that has to agree on every platform the validator runs on. */
class DocumentValidatorDatesTest {

    private val today = LocalDate.parse("2026-09-13")
    private val validator = DocumentValidator(ValidationConfig(staleAfterDays = 180))

    private fun document(lastReviewed: String) = ParsedDocument(
        id = "a-doc",
        title = "Title",
        category = "orders",
        screen = null,
        lastReviewed = lastReviewed,
        status = DocumentStatus.PUBLISHED,
        body = "## H\ntext",
        sourcePath = "a.md",
    )

    @Test
    fun `an impossible or malformed date is reported, not thrown`() {
        for (bad in listOf("2026-13-45", "not-a-date", "2026-02-30", "")) {
            val issue = validator.validate(listOf(document(bad)), today).single()
            assertTrue(issue.message.contains("is not a date"), "$bad -> ${issue.message}")
        }
    }

    @Test
    fun `a review date in the future is not stale`() {
        assertTrue(validator.validate(listOf(document("2027-01-01")), today).isEmpty())
    }

    @Test
    fun `exactly the limit is allowed and one day over is stale`() {
        // 2026-09-13 minus 180 days is 2026-03-17.
        assertTrue(validator.validate(listOf(document("2026-03-17")), today).isEmpty())
        val issue = validator.validate(listOf(document("2026-03-16")), today).single()
        assertTrue(issue.message.contains("181 days ago"), issue.message)
        assertEquals(1, validator.validate(listOf(document("2026-03-16")), today).size)
    }
}

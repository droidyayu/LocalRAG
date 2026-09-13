package com.ayushig.localrag.core

import com.ayushig.localrag.core.answer.OutputGate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OutputGateTest {

    private val passages = listOf(
        "A GTT order remains active for one year from the day you place it.",
        "Withdrawals settle within 2 working days. Brokerage is 0.25% per executed order.",
    )

    private fun check(answer: String) = OutputGate.check(answer, passages)

    @Test
    fun `grounded text passes`() {
        assertEquals(
            OutputGate.Verdict.Allowed,
            check("A GTT order remains active for one year from the day you place it."),
        )
    }

    @Test
    fun `a digit not in the passages is rejected`() {
        val verdict = assertIs<OutputGate.Verdict.Rejected>(
            check("Withdrawals settle within 5 working days."),
        )
        assertEquals(OutputGate.Reason.UNGROUNDED_DIGIT, verdict.reason)
        assertTrue(verdict.detail.contains("5"))
    }

    @Test
    fun `a digit present in the passages is allowed`() {
        assertEquals(
            OutputGate.Verdict.Allowed,
            check("Withdrawals settle within 2 working days."),
        )
    }

    @Test
    fun `a rate restated with the wrong figure is rejected`() {
        val verdict = assertIs<OutputGate.Verdict.Rejected>(
            check("Brokerage is 0.35% per executed order."),
        )
        assertEquals(OutputGate.Reason.UNGROUNDED_DIGIT, verdict.reason)
    }

    @Test
    fun `advisory language is rejected`() {
        listOf(
            "You should place a GTT order for one year.",
            "We recommend a GTT order that remains active for one year.",
            "This is a good time to place a GTT order for one year.",
        ).forEach { answer ->
            val verdict = assertIs<OutputGate.Verdict.Rejected>(check(answer), answer)
            assertEquals(OutputGate.Reason.ADVISORY_LANGUAGE, verdict.reason)
        }
    }

    @Test
    fun `text that wandered off source is rejected`() {
        val verdict = assertIs<OutputGate.Verdict.Rejected>(
            check("Photosynthesis converts sunlight into chemical energy inside plant cells."),
        )
        assertEquals(OutputGate.Reason.LOW_OVERLAP, verdict.reason)
    }

    @Test
    fun `truncated text is rejected`() {
        val verdict = assertIs<OutputGate.Verdict.Rejected>(
            check("A GTT order remains active for one year from the day"),
        )
        assertEquals(OutputGate.Reason.TRUNCATED, verdict.reason)
    }

    @Test
    fun `empty text is rejected`() {
        assertEquals(
            OutputGate.Reason.EMPTY,
            assertIs<OutputGate.Verdict.Rejected>(check("   ")).reason,
        )
    }

    @Test
    fun `over-long text is rejected`() {
        val long = "A GTT order remains active for one year. ".repeat(40)
        assertEquals(
            OutputGate.Reason.TOO_LONG,
            assertIs<OutputGate.Verdict.Rejected>(check(long)).reason,
        )
    }

    @Test
    fun `a question mark still counts as a finished sentence`() {
        assertEquals(
            OutputGate.Verdict.Allowed,
            check("Did you know a GTT order remains active for one year?"),
        )
    }
}

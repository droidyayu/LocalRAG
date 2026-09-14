package com.ayushig.localrag.demo.ui.chat

import com.ayushig.localrag.android.AgentEvent
import com.ayushig.localrag.demo.domain.model.TurnTimings
import org.junit.Assert.assertEquals
import org.junit.Test

/** The status line under the running bubble says what the turn is doing, in plain words. */
class AgentActivityTextTest {

    @Test
    fun `thinking status`() {
        assertEquals("Thinking…", AgentEvent.Thinking.statusText())
    }

    @Test
    fun `calling status humanizes the tool name`() {
        assertEquals(
            "Calling get portfolio summary…",
            AgentEvent.CallingTool("GET_PORTFOLIO_SUMMARY", emptyMap()).statusText(),
        )
        assertEquals(
            "Calling search documentation…",
            AgentEvent.CallingTool("search_documentation", mapOf("query" to "upi")).statusText(),
        )
    }

    @Test
    fun `finished and judging statuses`() {
        assertEquals(
            "Reading results…",
            AgentEvent.ToolFinished("get_portfolio_summary", 120, emptyList()).statusText(),
        )
        assertEquals("Verifying answer…", AgentEvent.JudgingAnswer.statusText())
    }

    @Test
    fun `humanize drops blanks and lowercases`() {
        assertEquals("x", humanizeToolName("X"))
        assertEquals("find holding", humanizeToolName("FIND_HOLDING"))
    }

    @Test
    fun `args render empty when absent`() {
        assertEquals("no arguments", formatToolArgs(emptyMap()))
    }

    @Test
    fun `arg values flatten newlines and cap length`() {
        assertEquals(
            "query = apple stock",
            formatToolArgs(mapOf("query" to "apple stock")),
        )
        assertEquals(
            "query = line one line two",
            formatToolArgs(mapOf("query" to "line one\nline two")),
        )
        assertEquals(
            "category = " + "M".repeat(120),
            formatToolArgs(mapOf("category" to "M".repeat(200))),
        )
    }

    @Test
    fun `turn total reads in seconds`() {
        assertEquals(
            "answered in 12s",
            formatTurnTotal(TurnTimings(totalMs = 12_000, generateMs = 11_800, toolMs = 4, rounds = 2)),
        )
        assertEquals(
            "answered in 850ms",
            formatTurnTotal(TurnTimings(totalMs = 850, generateMs = 840, toolMs = 1, rounds = 1)),
        )
    }

    @Test
    fun `turn breakdown names model time, function time, and steps`() {
        assertEquals(
            "Model 11.8s · Functions 4ms · 2 steps",
            formatTurnBreakdown(
                TurnTimings(totalMs = 12_000, generateMs = 11_800, toolMs = 4, rounds = 2),
            ),
        )
        assertEquals(
            "Model 840ms · Functions 1ms · 1 step",
            formatTurnBreakdown(
                TurnTimings(totalMs = 850, generateMs = 840, toolMs = 1, rounds = 1),
            ),
        )
    }
}

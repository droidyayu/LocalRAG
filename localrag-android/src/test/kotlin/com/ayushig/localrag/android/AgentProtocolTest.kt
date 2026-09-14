package com.ayushig.localrag.android

import com.ayushig.localrag.android.internal.AgentProtocol
import com.ayushig.localrag.android.internal.AgentProtocol.Parsed.FinalAnswer
import com.ayushig.localrag.android.internal.AgentProtocol.Parsed.ToolCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The strict grammar between the loop and the model.
 *
 * Greedy decoding plus the host app's few-shot prompt make conforming output the common case;
 * this pins that everything else is rejected rather than repaired, because repairing model
 * output with heuristics is how echoes come back.
 */
class AgentProtocolTest {

    private val tools = setOf("get_portfolio_summary", "get_category_summary", "find_holding")

    @Test
    fun `tool call with no args`() {
        assertEquals(
            ToolCall("get_portfolio_summary", emptyMap()),
            AgentProtocol.parse("TOOL: get_portfolio_summary", tools),
        )
    }

    @Test
    fun `tool call with args`() {
        assertEquals(
            ToolCall("get_category_summary", mapOf("category" to "METALS")),
            AgentProtocol.parse("TOOL: get_category_summary | category=METALS", tools),
        )
    }

    @Test
    fun `tool call tolerates surrounding whitespace and blank lines`() {
        assertEquals(
            ToolCall("find_holding", mapOf("query" to "apple stock")),
            AgentProtocol.parse("\n  TOOL: find_holding | query=apple stock  \n", tools),
        )
    }

    @Test
    fun `prefixes and names match case-insensitively, values keep case`() {
        assertEquals(
            ToolCall("get_portfolio_summary", emptyMap()),
            AgentProtocol.parse("Tool: get_portfolio_summary", tools),
        )
        assertEquals(
            ToolCall("get_category_summary", mapOf("category" to "METALS")),
            AgentProtocol.parse("TOOL: GET_CATEGORY_SUMMARY | Category=METALS", tools),
        )
        assertEquals(
            FinalAnswer("Hello!"),
            AgentProtocol.parse("answer: Hello!", tools),
        )
    }

    @Test
    fun `answer may span lines`() {
        assertEquals(
            FinalAnswer("Your portfolio is worth $12,340.00.\nThat is up 5.41% overall."),
            AgentProtocol.parse(
                "ANSWER: Your portfolio is worth $12,340.00.\nThat is up 5.41% overall.",
                tools,
            ),
        )
    }

    @Test
    fun `non-grammar output is rejected`() {
        assertNull(AgentProtocol.parse(null, tools))
        assertNull(AgentProtocol.parse("", tools))
        assertNull(AgentProtocol.parse("   \n  ", tools))
        assertNull(AgentProtocol.parse("Here is what I found: ...", tools))
        assertNull(AgentProtocol.parse("REFUSE", tools))
        assertNull(AgentProtocol.parse("ANSWER:", tools))
        assertNull(AgentProtocol.parse("TOOL:", tools))
    }

    @Test
    fun `tools outside the configured set are rejected`() {
        assertNull(AgentProtocol.parse("TOOL: delete_portfolio", tools))
        assertNull(AgentProtocol.parse("TOOL: search_documentation_typo | query=x", tools))
    }

    @Test
    fun `malformed args are rejected`() {
        assertNull(AgentProtocol.parse("TOOL: get_category_summary | category", tools))
        assertNull(AgentProtocol.parse("TOOL: get_category_summary | category=", tools))
        assertNull(AgentProtocol.parse("TOOL: get_category_summary | =METALS", tools))
    }

    @Test
    fun `directive detection spans lines and ignores prose`() {
        assertEquals(false, AgentProtocol.containsDirective("Hello! Ask me anything."))
        assertEquals(false, AgentProtocol.containsDirective("The TOOL: prefix is how calls start."))
        assertEquals(true, AgentProtocol.containsDirective("Hello!\nTOOL: find_holding | query=x"))
        assertEquals(true, AgentProtocol.containsDirective("  answer: sure, one moment.  "))
    }
}

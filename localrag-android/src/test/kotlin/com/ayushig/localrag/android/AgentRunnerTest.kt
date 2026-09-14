package com.ayushig.localrag.android

import com.ayushig.localrag.android.internal.AgentRunner
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The loop with a scripted model and stubbed tools.
 *
 * Each test drives one honest outcome: grounded answers pass the gate, invented figures and
 * advisory language do not, and anything off-grammar ends the turn instead of guessing.
 */
class AgentRunnerTest {

    private val figures = ToolResult(
        tool = "get_portfolio_summary",
        text = "total value: $12,340.00\ntotal profit and loss: +$1,240.00",
    )

    private fun config(observations: Map<String, ToolResult>): AgentConfig {
        val tools = observations.map { (name, observation) ->
            ToolDefinition(
                name = name,
                description = "test tool $name",
                argSpec = "(no args)",
                execute = { observation },
            )
        }
        return AgentConfig(
            systemPrompt = "Test assistant. Reply TOOL: <name> or ANSWER: <text>.",
            tools = tools,
        )
    }

    private fun scripted(vararg replies: String?): suspend (String) -> String? {
        val queue = ArrayDeque(replies.toList())
        return { queue.removeFirstOrNull() }
    }

    private val calls = mutableListOf<String>()
    private fun recording(config: AgentConfig): AgentConfig = config.copy(
        tools = config.tools.map { tool ->
            tool.copy(execute = {
                calls += tool.name
                tool.execute(it)
            })
        },
    )

    @Test
    fun `direct answer with no tools passes when conversational`() = runTest {
        val answer = AgentRunner().answer(
            generate = scripted("Hi there! Ask me about your portfolio or the help documentation."),
            query = "hi",
            config = config(emptyMap()),
        )
        // No observations, so the gate cannot confirm it — but there are no digits to invent
        // either, and the shape is a short sentence: bounded small talk, allowed.
        assertTrue(answer is AgentOutcome.Final)
        answer as AgentOutcome.Final
        assertEquals(emptyList<String>(), answer.sources)
        assertEquals(emptyList<String>(), answer.usedTools)
    }

    @Test
    fun `plain prose with digits stays unresolved`() = runTest {
        val answer = AgentRunner().answer(
            generate = scripted("Your balance is 50000 rupees."),
            query = "my balance",
            config = config(emptyMap()),
        )
        assertEquals(AgentOutcome.Unresolved, answer)
    }

    @Test
    fun `tool observation grounds the final answer`() = runTest {
        calls.clear()
        val answer = AgentRunner().answer(
            generate = scripted(
                "TOOL: get_portfolio_summary",
                "ANSWER: Your portfolio is worth $12,340.00.",
            ),
            query = "what is my portfolio worth",
            config = recording(config(mapOf("get_portfolio_summary" to figures))),
        )
        assertTrue(answer is AgentOutcome.Final)
        answer as AgentOutcome.Final
        assertEquals("Your portfolio is worth $12,340.00.", answer.text)
        assertEquals(listOf("get_portfolio_summary"), answer.usedTools)
        assertEquals(listOf("get_portfolio_summary"), calls)
    }

    @Test
    fun `invented figures end the turn unresolved`() = runTest {
        val answer = AgentRunner().answer(
            generate = scripted(
                "TOOL: get_portfolio_summary",
                "ANSWER: Your portfolio is worth $99,999.00.",
            ),
            query = "what is my portfolio worth",
            config = config(mapOf("get_portfolio_summary" to figures)),
        )
        assertEquals(AgentOutcome.Unresolved, answer)
    }

    @Test
    fun `advisory language is refused even with no tools`() = runTest {
        val answer = AgentRunner().answer(
            generate = scripted("ANSWER: You should buy more stocks."),
            query = "should i buy stocks",
            config = config(emptyMap()),
        )
        assertEquals(AgentOutcome.Refused, answer)
    }

    @Test
    fun `off-grammar output ends the turn unresolved`() = runTest {
        val answer = AgentRunner().answer(
            generate = scripted("Let me think about that..."),
            query = "what is my portfolio worth",
            config = config(mapOf("get_portfolio_summary" to figures)),
        )
        assertEquals(AgentOutcome.Unresolved, answer)
    }

    @Test
    fun `unknown tool ends the turn unresolved`() = runTest {
        val answer = AgentRunner().answer(
            generate = scripted("TOOL: delete_portfolio"),
            query = "close my account",
            config = config(emptyMap()),
        )
        assertEquals(AgentOutcome.Unresolved, answer)
    }

    @Test
    fun `model silence ends the turn unresolved`() = runTest {
        val answer = AgentRunner().answer(
            generate = scripted(null as String?),
            query = "hello",
            config = config(emptyMap()),
        )
        assertEquals(AgentOutcome.Unresolved, answer)
    }

    @Test
    fun `three tool rounds then a forced answer`() = runTest {
        var calls = 0
        val answer = AgentRunner().answer(
            generate = {
                calls++
                if (calls <= 3) "TOOL: get_portfolio_summary" else "ANSWER: Total $12,340.00."
            },
            query = "summarise everything three times",
            config = config(mapOf("get_portfolio_summary" to figures)),
        )
        assertEquals(4, calls)
        assertTrue(answer is AgentOutcome.Final)
    }
}

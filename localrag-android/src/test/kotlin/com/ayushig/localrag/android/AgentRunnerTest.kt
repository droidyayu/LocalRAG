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
        assertTrue(answer is AgentOutcome.Unresolved)
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
        assertTrue(answer is AgentOutcome.Unresolved)
    }

    @Test
    fun `advisory language is refused even with no tools`() = runTest {
        val answer = AgentRunner().answer(
            generate = scripted("ANSWER: You should buy more stocks."),
            query = "should i buy stocks",
            config = config(emptyMap()),
        )
        assertTrue(answer is AgentOutcome.Refused)
    }

    @Test
    fun `off-grammar output ends the turn unresolved`() = runTest {
        val answer = AgentRunner().answer(
            generate = scripted("Let me think about that..."),
            query = "what is my portfolio worth",
            config = config(mapOf("get_portfolio_summary" to figures)),
        )
        assertTrue(answer is AgentOutcome.Unresolved)
    }

    @Test
    fun `unknown tool ends the turn unresolved`() = runTest {
        val answer = AgentRunner().answer(
            generate = scripted("TOOL: delete_portfolio"),
            query = "close my account",
            config = config(emptyMap()),
        )
        assertTrue(answer is AgentOutcome.Unresolved)
    }

    @Test
    fun `model silence ends the turn unresolved`() = runTest {
        val answer = AgentRunner().answer(
            generate = scripted(null as String?),
            query = "hello",
            config = config(emptyMap()),
        )
        assertTrue(answer is AgentOutcome.Unresolved)
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

    @Test
    fun `prose smuggling a directive ends the turn unresolved`() = runTest {
        val answer = AgentRunner().answer(
            generate = scripted("Hello!\nTOOL: get_portfolio_summary."),
            query = "hi",
            config = config(mapOf("get_portfolio_summary" to figures)),
        )
        assertTrue(answer is AgentOutcome.Unresolved)
    }

    @Test
    fun `answer smuggling a directive ends the turn unresolved`() = runTest {
        val answer = AgentRunner().answer(
            generate = scripted(
                "ANSWER: Your portfolio is worth $12,340.00.\nTOOL: find_holding | query=x.",
            ),
            query = "what is my portfolio worth",
            config = config(mapOf("get_portfolio_summary" to figures)),
        )
        assertTrue(answer is AgentOutcome.Unresolved)
    }

    @Test
    fun `follow-up resolves with history in context`() = runTest {
        val history = listOf(
            AgentMessage(AgentRole.USER, "what is my portfolio worth"),
            AgentMessage(AgentRole.MODEL, "Your portfolio is worth $12,340.00."),
        )
        val answer = AgentRunner().answer(
            generate = scripted(
                "TOOL: get_portfolio_summary",
                "ANSWER: The profit is $1,240.00.",
            ),
            query = "and the profit",
            config = config(mapOf("get_portfolio_summary" to figures)),
            history = history,
        )
        assertTrue(answer is AgentOutcome.Final)
    }

    @Test
    fun `history digits never count as grounding evidence`() = runTest {
        val history = listOf(
            AgentMessage(AgentRole.MODEL, "Your code is 1234."),
        )
        val answer = AgentRunner().answer(
            generate = scripted("ANSWER: Your code is 1234."),
            query = "what is my code",
            config = config(emptyMap()),
            history = history,
        )
        assertTrue(answer is AgentOutcome.Unresolved)
    }

    @Test
    fun `turn emits progress events in order`() = runTest {
        val events = mutableListOf<AgentEvent>()
        AgentRunner().answer(
            generate = scripted(
                "TOOL: get_portfolio_summary",
                "ANSWER: Your portfolio is worth $12,340.00.",
            ),
            query = "what is my portfolio worth",
            config = config(mapOf("get_portfolio_summary" to figures)),
            onEvent = { events += it },
        )
        assertEquals(
            listOf(
                AgentEvent.Thinking,
                AgentEvent.CallingTool("get_portfolio_summary", emptyMap()),
                AgentEvent.ToolFinished("get_portfolio_summary", figures.text.length, emptyList()),
                AgentEvent.Thinking,
                AgentEvent.JudgingAnswer,
            ),
            events,
        )
    }

    @Test
    fun `every outcome reports where the time went`() = runTest {
        val final = AgentRunner().answer(
            generate = scripted(
                "TOOL: get_portfolio_summary",
                "ANSWER: Your portfolio is worth $12,340.00.",
            ),
            query = "what is my portfolio worth",
            config = config(mapOf("get_portfolio_summary" to figures)),
        ) as AgentOutcome.Final
        assertEquals(2, final.timings.rounds)
        assertTrue(final.timings.generateMs >= 0)
        assertTrue(final.timings.toolMs >= 0)
        assertTrue(final.timings.totalMs >= final.timings.generateMs)
        assertTrue(final.timings.totalMs >= final.timings.toolMs)

        val unresolved = AgentRunner().answer(
            generate = scripted("Let me think about that..."),
            query = "what is my portfolio worth",
            config = config(mapOf("get_portfolio_summary" to figures)),
        ) as AgentOutcome.Unresolved
        assertEquals(1, unresolved.timings.rounds)
        assertTrue(unresolved.timings.totalMs >= unresolved.timings.generateMs)
    }

    @Test
    fun `pre-searched passages ground the answer with no tool call`() = runTest {
        val passage = Passage(
            chunkId = "deposit-funds#1",
            docId = "deposit-funds",
            title = "How do I add funds?",
            heading = "overview",
            text = "Transfer from a bank account held in your own name. " +
                "Funds usually arrive within one working day.",
            screenLink = null,
            score = 13.8f,
            source = MatchSource.BM25,
        )
        val answer = AgentRunner().answer(
            generate = scripted(
                "ANSWER: Transfer from a bank account held in your own name. " +
                    "Funds usually arrive within one working day.",
            ),
            query = "how do I deposit funds",
            config = config(emptyMap()).copy(documentation = listOf(passage)),
        ) as AgentOutcome.Final
        // Single round: no tool was planned, none ran, the passages did the grounding.
        assertEquals(1, answer.timings.rounds)
        assertTrue(answer.usedTools.isEmpty())
        assertEquals(listOf("How do I add funds? — overview"), answer.sources)
    }

    @Test
    fun `a repeated tool call forces the answer instead of looping`() = runTest {
        var executions = 0
        val answer = AgentRunner().answer(
            generate = scripted(
                "TOOL: get_portfolio_summary",
                "TOOL: get_portfolio_summary",
                "TOOL: get_portfolio_summary",
                "ANSWER: Your portfolio is worth $12,340.00.",
            ),
            query = "what is my portfolio worth",
            config = AgentConfig(
                systemPrompt = "Test assistant. Reply TOOL: <name> or ANSWER: <text>.",
                tools = listOf(
                    ToolDefinition(
                        name = "get_portfolio_summary",
                        description = "test tool",
                        argSpec = "(no args)",
                        execute = {
                            executions++
                            figures
                        },
                    ),
                ),
            ),
        )
        assertTrue(answer is AgentOutcome.Final)
        // One execution and a forced answer: the second identical call ends the rounds.
        assertEquals(1, executions)
        assertEquals(3, (answer as AgentOutcome.Final).timings.rounds)
    }
}

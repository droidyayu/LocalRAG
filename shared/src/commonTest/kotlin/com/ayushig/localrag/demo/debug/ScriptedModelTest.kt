package com.ayushig.localrag.demo.debug

import com.ayushig.localrag.demo.data.assistant.AGENT_SYSTEM_PROMPT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The scripted model replaces the on-device model in acceptance runs (Maestro) so every platform
 * is tested deterministically, with the real bundle, retrieval, tools and output gate. It must read
 * the transcript the agent loop builds and answer in the loop's grammar, quoting observations and
 * passages verbatim so the output gate lets the answers through.
 */
class ScriptedModelTest {

    private fun prompt(
        question: String,
        docs: String? = null,
        vararg observations: Pair<String, String>,
    ): String = buildString {
        append(AGENT_SYSTEM_PROMPT)
        if (docs != null) {
            append("Documentation pre-searched for this question (use it if it answers the question, ignore it otherwise):\n")
            append(docs)
            append("\n")
        }
        for ((tool, text) in observations) {
            append("TOOL: $tool\nOBSERVATION [$tool]:\n$text\n")
        }
        append("\nQuestion: $question\n")
    }

    @Test
    fun aPortfolioQuestionCallsTheSummaryTool() {
        assertEquals("TOOL: get_portfolio_summary", ScriptedModel.reply(prompt("What is my portfolio worth?")))
    }

    @Test
    fun aCategoryQuestionCallsTheCategoryToolWithTheCategory() {
        assertEquals(
            "TOOL: get_category_summary | category=METALS",
            ScriptedModel.reply(prompt("How are my metals doing?")),
        )
    }

    @Test
    fun aMarginQuestionCallsTheMarginTool() {
        assertEquals("TOOL: get_margin_status", ScriptedModel.reply(prompt("What is my margin status?")))
    }

    @Test
    fun aNamedHoldingQuestionCallsFindHoldingWithTheName() {
        assertEquals("TOOL: find_holding | query=gold", ScriptedModel.reply(prompt("Do I hold any gold?")))
    }

    @Test
    fun aHoldingQuestionWithNoNameCallsFindHoldingWithNoArguments() {
        assertEquals("TOOL: find_holding", ScriptedModel.reply(prompt("Find my holding")))
    }

    @Test
    fun afterAnObservationTheAnswerQuotesItVerbatim() {
        val observation = "total value: \$169,363.92\ntotal profit and loss: +\$8,693.25"
        val reply = ScriptedModel.reply(
            prompt("What is my portfolio worth?", null, "get_portfolio_summary" to observation),
        )

        assertTrue(reply.startsWith("ANSWER: "), reply)
        assertTrue(reply.contains("\$169,363.92"), reply)
        assertTrue(reply.contains("+\$8,693.25"), reply)
        assertEquals(1, reply.lines().size, "the answer must be one line")
    }

    @Test
    fun aDocumentationQuestionAnswersFromThePreSearchedPassage() {
        val docs = "Deposit funds — overview\nTransfer from a bank account held in your own name. Funds usually arrive within one working day."
        val reply = ScriptedModel.reply(prompt("How do I deposit funds?", docs))

        assertEquals(
            "ANSWER: Transfer from a bank account held in your own name. Funds usually arrive within one working day.",
            reply,
        )
    }

    @Test
    fun anUncoveredQuestionGetsAnAnswerTheGateWillRefuse() {
        val reply = ScriptedModel.reply(prompt("How do I reset my password?"))

        assertTrue(reply.startsWith("ANSWER: "), reply)
        assertTrue(reply.any(Char::isDigit), "the answer carries an unsupported figure so the gate rejects it")
    }

    @Test
    fun aGreetingGetsAShortPlainReply() {
        val reply = ScriptedModel.reply(prompt("Hello"))

        assertTrue(reply.startsWith("ANSWER: "), reply)
        assertTrue(reply.endsWith("."), reply)
        assertTrue(reply.none(Char::isDigit), reply)
    }

    @Test
    fun anAdviceQuestionGetsTheRefusalLine() {
        assertEquals(
            "ANSWER: I can't advise on buying or selling.",
            ScriptedModel.reply(prompt("Should I buy more stocks?")),
        )
    }

    @Test
    fun theExampleTurnsInTheSystemPromptNeverLeakIntoTheAnswer() {
        // The system prompt itself contains a worked example with an OBSERVATION; only what comes
        // after the prompt counts.
        val reply = ScriptedModel.reply(prompt("What is my portfolio worth?"))

        assertTrue(!reply.contains("12,340"), reply)
    }

    @Test
    fun theProbeAsksForExactlyOk() {
        assertEquals("OK", ScriptedModel.reply("ping", systemInstruction = "Reply with exactly: OK"))
    }

    // The app pre-searches documentation for EVERY question, so a docs block is present even for
    // portfolio questions; the scripted model must decide by the question, not by the block.
    private val unrelatedDocs =
        "Managed portfolios — overview\nYou can add at any time. Withdrawals settle after the portfolio rebalances."

    @Test
    fun aPortfolioQuestionStillCallsTheToolWhenDocumentationWasPreSearched() {
        assertEquals(
            "TOOL: get_portfolio_summary",
            ScriptedModel.reply(prompt("What is my portfolio worth?", unrelatedDocs)),
        )
    }

    @Test
    fun aGreetingGetsTheGreetingEvenWhenDocumentationWasPreSearched() {
        val reply = ScriptedModel.reply(prompt("Hello", unrelatedDocs))

        assertTrue(reply.startsWith("ANSWER: Hello!"), reply)
    }

    @Test
    fun aPasswordQuestionGetsTheGateRefusedAnswerEvenWhenDocumentationWasPreSearched() {
        val reply = ScriptedModel.reply(prompt("How do I reset my password?", unrelatedDocs))

        assertTrue(reply.startsWith("ANSWER: "), reply)
        assertTrue(reply.any(Char::isDigit), "the answer carries an unsupported figure so the gate rejects it")
        assertTrue(!reply.contains("add at any time"), reply)
    }

    @Test
    fun anAdviceQuestionStillGetsTheRefusalWhenDocumentationWasPreSearched() {
        assertEquals(
            "ANSWER: I can't advise on buying or selling.",
            ScriptedModel.reply(prompt("Should I buy more stocks?", unrelatedDocs)),
        )
    }

    @Test
    fun aDocumentationQuestionStillAnswersFromTheFirstPassageAfterTheReorder() {
        val reply = ScriptedModel.reply(prompt("What are the brokerage charges?", "Brokerage charges — overview\nBrokerage is 0.03 percent per order."))

        assertEquals("ANSWER: Brokerage is 0.03 percent per order.", reply)
    }

    // The output gate rejects an answer that does not end a sentence (truncated output), so a
    // quoted observation or passage must end with a full stop; the figures inside stay verbatim.
    @Test
    fun anAnswerQuotedFromAnObservationEndsWithAFullStop() {
        val observation = "total value: \$169,363.92\ntotal profit and loss: +\$8,693.25"
        val reply = ScriptedModel.reply(prompt("What is my portfolio worth?", null, "get_portfolio_summary" to observation))

        assertTrue(reply.endsWith("."), reply)
        assertTrue(reply.contains("+\$8,693.25."), "the full stop follows the figure without changing it: $reply")
    }

    @Test
    fun anAnswerQuotedFromAPassageWithoutAFullStopGetsOne() {
        val reply = ScriptedModel.reply(prompt("What are the brokerage charges?", "Brokerage charges — overview\nBrokerage is 0.03 percent per order"))

        assertEquals("ANSWER: Brokerage is 0.03 percent per order.", reply)
    }

    @Test
    fun anAnswerThatAlreadyEndsASentenceIsLeftAlone() {
        val reply = ScriptedModel.reply(prompt("What are the brokerage charges?", "Brokerage charges — overview\nBrokerage is 0.03 percent per order!"))

        assertEquals("ANSWER: Brokerage is 0.03 percent per order!", reply)
    }
}

package com.ayushig.localrag.demo.assistant

import com.ayushig.localrag.demo.data.assistant.AGENT_SYSTEM_PROMPT
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the repeat-question fix: the model sometimes answered a follow-up from history
 * without calling a tool, and the output gate (rightly) rejects history-grounded figures,
 * so the turn fell back with zero tool calls. The prompt must order a fresh tool call.
 */
class AgentPromptsTest {

    @Test
    fun `prompt orders a fresh tool call on every question`() {
        assertTrue(
            AGENT_SYSTEM_PROMPT.contains(
                "call the tool again for fresh figures even if earlier turns already show them",
            ),
        )
    }

    @Test
    fun `prompt never names the removed documentation tool`() {
        assertTrue(!AGENT_SYSTEM_PROMPT.contains("search_documentation"))
    }
}

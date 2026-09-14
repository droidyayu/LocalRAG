package com.ayushig.localrag.android.internal

import android.util.Log
import com.ayushig.localrag.android.AgentConfig
import com.ayushig.localrag.android.AgentOutcome
import com.ayushig.localrag.core.answer.OutputGate

/**
 * The function-calling machinery, owned by the SDK and configured by the host app.
 *
 * The SDK plans in a strict grammar, executes app-supplied tools, pastes observations back,
 * and gates the final text before anyone sees it. It carries no words of its own: prompts,
 * tool set, and policy copy all arrive in [AgentConfig]. Anything off-grammar, any unknown
 * tool, and any gate rejection ends the turn as [AgentOutcome.Unresolved] rather than a guess.
 */
internal class AgentRunner {

    suspend fun answer(
        generate: suspend (String) -> String?,
        query: String,
        config: AgentConfig,
    ): AgentOutcome {
        val allowedTools = config.tools.associateBy { it.name.lowercase() }
        val transcript = StringBuilder()
        val observations = mutableListOf<String>()
        val sources = mutableListOf<String>()
        val usedTools = mutableListOf<String>()

        Log.d(TAG, "turn start: \"$query\"")
        repeat(config.maxToolRounds) { round ->
            val raw = generate(transcript.prompt(query, config))
            Log.d(TAG, "round $round model said: ${raw?.lineSequence()?.firstOrNull().orEmpty().take(160)}")
            val parsed = AgentProtocol.parse(raw, allowedTools.keys)
            if (parsed == null) {
                // Plain prose with no observations is a greeting, not a plan: judge it as
                // conversational small talk (no digits allowed, advisory still refuses).
                // Prose after tools ran stays unresolved — unverifiable claims must not pass.
                val prose = raw?.trim().orEmpty()
                if (prose.isEmpty() || observations.isNotEmpty() || !isConversationalShape(prose, config)) {
                    Log.w(TAG, "round $round: off-grammar output, ending unresolved")
                    return AgentOutcome.Unresolved
                }
                Log.d(TAG, "round $round: plain prose accepted as conversational")
                return judge(prose, query, observations, sources, usedTools, config)
            }
            when (parsed) {
                is AgentProtocol.Parsed.ToolCall -> {
                    val tool = allowedTools[parsed.name]!!
                    val observation = runCatching { tool.execute(parsed.args) }.getOrNull()
                    if (observation == null) {
                        Log.w(TAG, "round $round: tool ${parsed.name} failed, ending unresolved")
                        return AgentOutcome.Unresolved
                    }
                    Log.d(TAG, "round $round: tool ${parsed.name} ok (${observation.text.length} chars)")
                    observations += observation.text
                    sources += observation.sourceTitles
                    usedTools += observation.tool
                    transcript.observe(parsed, observation)
                }

                is AgentProtocol.Parsed.FinalAnswer ->
                    return judge(parsed.text, query, observations, sources, usedTools, config)
            }
        }

        val forcedRaw = generate(transcript.forceAnswer(query))
        Log.d(TAG, "forced answer model said: ${forcedRaw?.lineSequence()?.firstOrNull().orEmpty().take(160)}")
        val forced = AgentProtocol.parse(forcedRaw, allowedTools.keys)
        if (forced !is AgentProtocol.Parsed.FinalAnswer) {
            Log.w(TAG, "forced answer off-grammar, ending unresolved")
            return AgentOutcome.Unresolved
        }
        return judge(forced.text, query, observations, sources, usedTools, config)
    }

    /**
     * The shared output gate, pointed at tool observations instead of passages. Digits in the
     * query are blanked first: the question may contain a number ("my 500 shares") that the
     * answer legitimately repeats, but only observations may introduce one.
     */
    private fun judge(
        text: String,
        query: String,
        observations: List<String>,
        sources: List<String>,
        usedTools: List<String>,
        config: AgentConfig,
    ): AgentOutcome {
        val answer = text.trim()
        if (answer.isEmpty()) {
            Log.w(TAG, "judge: empty answer, unresolved")
            return AgentOutcome.Unresolved
        }
        val evidence = observations + query.filterNot(Char::isDigit)
        return when (val verdict = OutputGate.check(answer, evidence)) {
            OutputGate.Verdict.Allowed -> {
                Log.d(TAG, "judge: allowed (${answer.length} chars, ${observations.size} observations)")
                AgentOutcome.Final(answer, sources.distinct(), usedTools.distinct())
            }

            is OutputGate.Verdict.Rejected ->
                when {
                    verdict.reason == OutputGate.Reason.ADVISORY_LANGUAGE -> {
                        Log.w(TAG, "judge: refused (${verdict.detail})")
                        AgentOutcome.Refused
                    }

                    // No observations and no digits means no facts to hallucinate — a short,
                    // well-formed greeting or signpost is safe. Advisory language still
                    // refuses above; anything else here is bounded small talk.
                    observations.isEmpty() && isConversationalShape(answer, config) -> {
                        Log.d(TAG, "judge: conversational shape allowed")
                        AgentOutcome.Final(answer, emptyList(), emptyList())
                    }

                    else -> {
                        Log.w(TAG, "judge: unresolved (${verdict.reason}: ${verdict.detail})")
                        AgentOutcome.Unresolved
                    }
                }
        }
    }

    private fun isConversationalShape(answer: String, config: AgentConfig): Boolean {
        if (answer.length > config.maxConversationalChars) return false
        if (answer.any(Char::isDigit)) return false
        return answer.last() == '.' || answer.last() == '!' || answer.last() == '?'
    }

    private fun StringBuilder.prompt(query: String, config: AgentConfig): String {
        if (isEmpty()) append(config.systemPrompt)
        return toString() + "\nQuestion: $query\n"
    }

    private fun StringBuilder.observe(
        call: AgentProtocol.Parsed.ToolCall,
        observation: com.ayushig.localrag.android.ToolResult,
    ) {
        append("TOOL: ${call.name}\n")
        append("OBSERVATION [${observation.tool}]:\n${observation.text}\n")
    }

    private fun StringBuilder.forceAnswer(query: String): String =
        toString() + "\nQuestion: $query\n" + FORCE_ANSWER_SUFFIX

    private companion object {
        const val TAG = "LocalRagAgent"

        const val FORCE_ANSWER_SUFFIX =
            "No more tool calls. Reply now with one ANSWER: line using only the observations above.\n"
    }
}

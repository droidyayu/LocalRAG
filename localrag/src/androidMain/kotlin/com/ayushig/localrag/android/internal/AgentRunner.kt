package com.ayushig.localrag.android.internal

import android.util.Log
import com.ayushig.localrag.android.AgentConfig
import com.ayushig.localrag.android.AgentEvent
import com.ayushig.localrag.android.AgentMessage
import com.ayushig.localrag.android.AgentOutcome
import com.ayushig.localrag.android.AgentRole
import com.ayushig.localrag.android.AgentTimings
import com.ayushig.localrag.android.Passage
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
        history: List<AgentMessage> = emptyList(),
        onEvent: (AgentEvent) -> Unit = {},
    ): AgentOutcome {
        val allowedTools = config.tools.associateBy { it.name.lowercase() }
        // System prompt once, then the recent past: every round after the first re-reads
        // this whole transcript, so the grammar reminder rides along with the history.
        val transcript = StringBuilder(config.systemPrompt)
        transcript.append(historyBlock(history, config))
        val observations = mutableListOf<String>()
        val sources = mutableListOf<String>()
        val usedTools = mutableListOf<String>()
        // Pre-searched documentation rides along like an observation that cost no round:
        // pasted once above the question, counted as evidence, sourced by title. The host
        // decides what qualifies; the loop treats it exactly like a tool result.
        if (config.documentation.isNotEmpty()) {
            transcript.append(documentationBlock(config.documentation))
            observations += config.documentation.map { it.text }
            sources += config.documentation.map { passage ->
                listOfNotNull(passage.title, passage.heading).joinToString(" — ")
            }
        }
        var lastCall: Pair<String, Map<String, String>>? = null

        // Wall-clock accounting for the turn, reported on every outcome so the host can
        // show where the time went. currentTimeMillis, not elapsedRealtime: this also runs
        // on the JVM under unit tests, where the Android clock is a stub that throws.
        val started = now()
        var generateMs = 0L
        var toolMs = 0L
        var rounds = 0
        fun snapshot() = AgentTimings(now() - started, generateMs, toolMs, rounds)

        /** One model call: what it said, plus what it cost. */
        suspend fun callModel(prompt: String): Pair<String?, Long> {
            val call = now()
            val raw = generate(prompt)
            val took = now() - call
            generateMs += took
            rounds++
            return raw to took
        }

        Log.d(TAG, "turn start: \"$query\"")
        for (round in 0 until config.maxToolRounds) {
            onEvent(AgentEvent.Thinking)
            val (raw, took) = callModel(transcript.prompt(query))
            Log.d(TAG, "round $round model said (${took}ms): ${raw?.lineSequence()?.firstOrNull().orEmpty().take(160)}")
            val parsed = AgentProtocol.parse(raw, allowedTools.keys)
            if (parsed == null) {
                // Plain prose with no observations is a greeting, not a plan: judge it as
                // conversational small talk (no digits allowed, advisory still refuses).
                // Prose after tools ran stays unresolved — unverifiable claims must not pass.
                // Prose smuggling a directive line is not prose at all.
                val prose = raw?.trim().orEmpty()
                if (prose.isEmpty() || observations.isNotEmpty() ||
                    AgentProtocol.containsDirective(prose) ||
                    !isConversationalShape(prose, config)
                ) {
                    Log.w(TAG, "round $round: off-grammar output, ending unresolved")
                    return AgentOutcome.Unresolved(snapshot())
                }
                Log.d(TAG, "round $round: plain prose accepted as conversational")
                onEvent(AgentEvent.JudgingAnswer)
                return judge(prose, query, observations, sources, usedTools, config, snapshot())
            }
            when (parsed) {
                is AgentProtocol.Parsed.ToolCall -> {
                    val call = parsed.name to parsed.args
                    if (call == lastCall) {
                        // The model is re-asking for what it already has: the observation is
                        // in the transcript, so another round trip only burns a full prefill
                        // (tens of seconds on CPU) to learn nothing. Answer from it now.
                        Log.w(TAG, "round $round: repeating tool ${parsed.name}, forcing the answer")
                        break
                    }
                    lastCall = call
                    val tool = allowedTools[parsed.name]!!
                    onEvent(AgentEvent.CallingTool(parsed.name, parsed.args))
                    val toolCall = now()
                    val observation = runCatching { tool.execute(parsed.args) }.getOrNull()
                    val toolTook = now() - toolCall
                    toolMs += toolTook
                    if (observation == null) {
                        Log.w(TAG, "round $round: tool ${parsed.name} failed, ending unresolved")
                        return AgentOutcome.Unresolved(snapshot())
                    }
                    Log.d(TAG, "round $round: tool ${parsed.name} ok (${observation.text.length} chars, ${toolTook}ms)")
                    onEvent(
                        AgentEvent.ToolFinished(
                            parsed.name,
                            observation.text.length,
                            observation.sourceTitles,
                        ),
                    )
                    observations += observation.text
                    sources += observation.sourceTitles
                    usedTools += observation.tool
                    transcript.observe(parsed, observation)
                }

                is AgentProtocol.Parsed.FinalAnswer -> {
                    if (AgentProtocol.containsDirective(parsed.text)) {
                        Log.w(TAG, "round $round: answer smuggles a directive, ending unresolved")
                        return AgentOutcome.Unresolved(snapshot())
                    }
                    onEvent(AgentEvent.JudgingAnswer)
                    return judge(parsed.text, query, observations, sources, usedTools, config, snapshot())
                }
            }
        }

        onEvent(AgentEvent.Thinking)
        val (forcedRaw, forcedTook) = callModel(transcript.forceAnswer(query))
        Log.d(TAG, "forced answer model said (${forcedTook}ms): ${forcedRaw?.lineSequence()?.firstOrNull().orEmpty().take(160)}")
        val forced = AgentProtocol.parse(forcedRaw, allowedTools.keys)
        if (forced !is AgentProtocol.Parsed.FinalAnswer ||
            AgentProtocol.containsDirective(forced.text)
        ) {
            Log.w(TAG, "forced answer off-grammar, ending unresolved")
            return AgentOutcome.Unresolved(snapshot())
        }
        onEvent(AgentEvent.JudgingAnswer)
        return judge(forced.text, query, observations, sources, usedTools, config, snapshot())
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
        timings: AgentTimings,
    ): AgentOutcome {
        val answer = text.trim()
        if (answer.isEmpty()) {
            Log.w(TAG, "judge: empty answer, unresolved")
            return AgentOutcome.Unresolved(timings)
        }
        val evidence = observations + query.filterNot(Char::isDigit)
        return when (val verdict = OutputGate.check(answer, evidence)) {
            OutputGate.Verdict.Allowed -> {
                Log.d(TAG, "judge: allowed (${answer.length} chars, ${observations.size} observations)")
                AgentOutcome.Final(answer, sources.distinct(), usedTools.distinct(), timings)
            }

            is OutputGate.Verdict.Rejected ->
                when {
                    verdict.reason == OutputGate.Reason.ADVISORY_LANGUAGE -> {
                        Log.w(TAG, "judge: refused (${verdict.detail})")
                        AgentOutcome.Refused(timings)
                    }

                    // No observations and no digits means no facts to hallucinate — a short,
                    // well-formed greeting or signpost is safe. Advisory language still
                    // refuses above; anything else here is bounded small talk.
                    observations.isEmpty() && isConversationalShape(answer, config) -> {
                        Log.d(TAG, "judge: conversational shape allowed")
                        AgentOutcome.Final(answer, emptyList(), emptyList(), timings)
                    }

                    else -> {
                        Log.w(TAG, "judge: unresolved (${verdict.reason}: ${verdict.detail})")
                        AgentOutcome.Unresolved(timings)
                    }
                }
        }
    }

    private fun isConversationalShape(answer: String, config: AgentConfig): Boolean {
        if (answer.length > config.maxConversationalChars) return false
        if (answer.any(Char::isDigit)) return false
        return answer.last() == '.' || answer.last() == '!' || answer.last() == '?'
    }

    private fun StringBuilder.prompt(query: String): String =
        toString() + "\nQuestion: $query\n"

    /**
     * Earlier turns, newest dropped first past the budget, so a long chat cannot blow the
     * on-device context window. Labeled plainly and kept out of the evidence: the gate
     * judges the answer against fresh observations, never against what was said before.
     */
    private fun historyBlock(history: List<AgentMessage>, config: AgentConfig): String {
        val kept = mutableListOf<AgentMessage>()
        var chars = 0
        for (message in history.asReversed()) {
            if (message.text.isBlank()) continue
            if (kept.isNotEmpty() && chars + message.text.length > config.maxHistoryChars) break
            // The newest turn always rides along, even over budget: a follow-up without its
            // parent is unanswerable, while a slightly long context is merely expensive.
            kept.add(message)
            chars += message.text.length
        }
        if (kept.isEmpty()) return ""
        return kept.asReversed().joinToString(
            separator = "\n",
            prefix = "Earlier in this conversation:\n",
            postfix = "\n",
        ) { message ->
            (if (message.role == AgentRole.USER) "User: " else "Assistant: ") + message.text.trim()
        }
    }

    /**
     * The pre-searched block, in the same title-text shape the documentation tool used
     * to paste, so tuned behavior carries over: the model has seen this exact layout.
     */
    private fun documentationBlock(passages: List<Passage>): String =
        passages.joinToString(
            separator = "\n---\n",
            prefix = "Documentation pre-searched for this question " +
                "(use it if it answers the question, ignore it otherwise):\n",
            postfix = "\n",
        ) { passage ->
            "${passage.title} — ${passage.heading ?: "overview"}\n${passage.text}"
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

        /** Wall clock in ms. currentTimeMillis, not the Android clock: see the note in answer(). */
        fun now(): Long = System.currentTimeMillis()

        const val FORCE_ANSWER_SUFFIX =
            "No more tool calls. Reply now with one ANSWER: line using only the observations above.\n"
    }
}

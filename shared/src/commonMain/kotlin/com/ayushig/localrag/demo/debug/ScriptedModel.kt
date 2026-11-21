package com.ayushig.localrag.demo.debug

import com.ayushig.localrag.demo.data.assistant.AGENT_SYSTEM_PROMPT

/**
 * The scripted stand-in for the on-device model in acceptance runs (Maestro).
 *
 * Everything real stays — the docs bundle, retrieval, the agent loop, the app's tools and figures,
 * the output gate and the UI — only the model is replaced. This reads the transcript the agent
 * loop builds and answers in the loop's grammar: one line, either a TOOL call or an ANSWER that
 * quotes observations and passages verbatim so the output gate lets it through.
 *
 * Only the text AFTER [AGENT_SYSTEM_PROMPT] counts: the prompt itself carries a worked example
 * with its own Question and OBSERVATION lines that must never leak into an answer.
 *
 * Plain Kotlin string logic only, no regex: the same code runs on Android and on Kotlin/Native.
 */
object ScriptedModel {

    fun reply(prompt: String, systemInstruction: String? = null): String {
        if (prompt == PROBE_PROMPT) return PROBE_REPLY
        val tail = if (prompt.contains(AGENT_SYSTEM_PROMPT)) {
            prompt.substringAfter(AGENT_SYSTEM_PROMPT)
        } else {
            prompt
        }
        // The live question is the last one; earlier "Question:" lines are history or the example.
        val markerAt = tail.lastIndexOf(QUESTION_MARKER)
        val beforeQuestion = if (markerAt < 0) tail else tail.substring(0, markerAt)
        val question = if (markerAt < 0) {
            ""
        } else {
            tail.substring(markerAt + QUESTION_MARKER.length).substringBefore("\n").trim()
        }

        // Decide by the question, not by the pre-searched block: the app pre-searches
        // documentation for EVERY question, so a docs block is present even when it is
        // irrelevant. Only an observation (fresh tool evidence) outranks the question.
        observationText(beforeQuestion)?.let { return "ANSWER: $it" }

        val q = question.lowercase()
        if (q.contains("should i buy") || q.contains("should i sell") || q.contains("should i hold")) {
            return ADVICE_REFUSAL
        }
        if (q.contains("portfolio") || q.contains("worth")) return "TOOL: get_portfolio_summary"
        if (q.contains("metal")) return "TOOL: get_category_summary | category=METALS"
        if (q.contains("stock")) return "TOOL: get_category_summary | category=STOCKS"
        if (q.contains("wealth")) return "TOOL: get_category_summary | category=WEALTH"
        if (q.contains("leverag")) return "TOOL: get_category_summary | category=LEVERAGED"
        if (q.contains("margin")) return "TOOL: get_margin_status"
        if (q.contains("hold")) {
            findNameWord(q)?.let { return "TOOL: find_holding | query=$it" }
            if (q.contains("find")) return "TOOL: find_holding"
        }
        if (isGreeting(q)) return GREETING
        // The known uncovered topic of this corpus: refuse with an unsupported figure.
        if (q.contains("password")) return NO_EVIDENCE_FALLBACK
        documentationText(beforeQuestion)?.let { return "ANSWER: $it" }
        return NO_EVIDENCE_FALLBACK
    }

    /**
     * The freshest tool result, verbatim on one line. The loop appends observations in order, so
     * the last block is the one the pending question waits on.
     */
    private fun observationText(beforeQuestion: String): String? {
        val start = beforeQuestion.lastIndexOf(OBSERVATION_MARKER)
        if (start < 0) return null
        val after = beforeQuestion.substring(start)
        val headerEnd = after.indexOf("\n")
        if (headerEnd < 0) return null
        var text = after.substring(headerEnd + 1)
        val nextTool = text.indexOf("\n$TOOL_MARKER")
        if (nextTool >= 0) text = text.substring(0, nextTool)
        text = text.trim()
        if (text.isEmpty()) return null
        return withSentenceEnding(text.replace("\n", "; "))
    }

    /** The first pre-searched passage's text (never its title line), flattened to one line. */
    private fun documentationText(beforeQuestion: String): String? {
        val start = beforeQuestion.indexOf(DOCS_MARKER)
        if (start < 0) return null
        val afterHeader = beforeQuestion.substring(start).substringAfter("\n")
        val titleEnd = afterHeader.indexOf("\n")
        if (titleEnd < 0) return null
        var text = afterHeader.substring(titleEnd + 1)
        for (cut in DOCS_CUTS) {
            val cutAt = text.indexOf(cut)
            if (cutAt >= 0) text = text.substring(0, cutAt)
        }
        text = text.trim()
        if (text.isEmpty()) return null
        return withSentenceEnding(text.replace("\n", "; "))
    }

    /**
     * A quoted observation or passage must end a sentence or the output gate rejects it as
     * truncated. The full stop follows the last figure without changing any figure itself.
     */
    private fun withSentenceEnding(text: String): String {
        val last = text.lastOrNull() ?: return text
        return if (last == '.' || last == '!' || last == '?') text else "$text."
    }

    /** Earliest holding name mentioned, so "do I hold any gold" finds gold. */
    private fun findNameWord(q: String): String? {
        var best: String? = null
        var bestIndex = Int.MAX_VALUE
        for (name in HOLDING_NAMES) {
            val at = q.indexOf(name)
            if (at >= 0 && at < bestIndex) {
                best = name
                bestIndex = at
            }
        }
        return best
    }

    /** Whole words only: "hi" must not match "this", "hey" must not match "they". */
    private fun isGreeting(q: String): Boolean {
        for (word in wordsOf(q)) {
            if (word == "hello" || word == "hi" || word == "hey") return true
        }
        return false
    }

    private fun wordsOf(text: String): List<String> {
        val words = mutableListOf<String>()
        val current = StringBuilder()
        for (ch in text) {
            if (ch in 'a'..'z' || ch in '0'..'9') {
                current.append(ch)
            } else if (current.isNotEmpty()) {
                words.add(current.toString())
                current.clear()
            }
        }
        if (current.isNotEmpty()) words.add(current.toString())
        return words
    }

    private const val PROBE_PROMPT = "ping"
    private const val PROBE_REPLY = "OK"
    private const val QUESTION_MARKER = "\nQuestion: "
    private const val OBSERVATION_MARKER = "OBSERVATION ["
    private const val TOOL_MARKER = "TOOL: "
    private const val DOCS_MARKER = "Documentation pre-searched"

    private val DOCS_CUTS = listOf("\n---\n", "\n$TOOL_MARKER")

    private const val ADVICE_REFUSAL = "ANSWER: I can't advise on buying or selling."
    private const val GREETING = "ANSWER: Hello! I can help with your portfolio and the help documentation."

    /**
     * Deliberately carries a figure with no evidence behind it: the output gate rejects it,
     * which is how the real model's "no information" case ends.
     */
    private const val NO_EVIDENCE_FALLBACK = "ANSWER: Reset it from Settings, Security, option 3."

    private val HOLDING_NAMES = listOf(
        "gold", "silver",
        "apple", "aapl", "microsoft", "msft", "nvidia", "nvda", "coca", "cola", "ko",
        "tesla", "tsla",
        "euro", "eurusd", "pound", "gbpusd", "us500", "xauusd",
        "balanced", "growth", "capital", "preservation", "emerging", "tech",
    )
}

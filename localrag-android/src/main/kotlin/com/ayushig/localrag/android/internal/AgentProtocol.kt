package com.ayushig.localrag.android.internal

/**
 * The strict grammar between the loop and the model. Greedy decoding plus the host app's
 * few-shot prompt make conforming output the common case; everything else is rejected rather
 * than repaired, because repairing model output with heuristics is how echoes come back.
 *
 * Prefixes and tool names match case-insensitively — on-device logs show small models writing
 * "Tool:". Argument values keep their case for retrieval.
 */
internal object AgentProtocol {

    sealed interface Parsed {
        data class ToolCall(val name: String, val args: Map<String, String>) : Parsed

        data class FinalAnswer(val text: String) : Parsed
    }

    fun parse(raw: String?, allowedTools: Set<String>): Parsed? {
        val line = raw?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim() ?: return null
        return when {
            line.startsWith(TOOL_PREFIX, ignoreCase = true) ->
                parseTool(line.substringAfter(":"), allowedTools)

            line.startsWith(ANSWER_PREFIX, ignoreCase = true) ->
                raw.substringAfter(":").trim()
                    .takeIf { it.isNotEmpty() }
                    ?.let(Parsed::FinalAnswer)

            else -> null
        }
    }

    /**
     * True when any line reads as a turn directive. The conversational path only accepts prose,
     * so a directive hiding below small talk is off-grammar, not chat — without this a reply
     * could carry a raw TOOL: line into the transcript UI.
     */
    fun containsDirective(text: String): Boolean =
        text.lineSequence().any { line ->
            val trimmed = line.trim()
            trimmed.startsWith(TOOL_PREFIX, ignoreCase = true) ||
                trimmed.startsWith(ANSWER_PREFIX, ignoreCase = true)
        }

    private fun parseTool(body: String, allowedTools: Set<String>): Parsed.ToolCall? {
        val name = body.substringBefore("|").trim().lowercase()
        if (name.isEmpty() || name !in allowedTools) return null
        val args = mutableMapOf<String, String>()
        val argString = body.substringAfter("|", "")
        if (argString.isNotBlank()) {
            for (part in argString.split(";")) {
                if (part.isBlank()) continue
                val key = part.substringBefore("=").trim().lowercase()
                val value = part.substringAfter("=", "").trim()
                if (key.isEmpty() || value.isEmpty()) return null
                args[key] = value
            }
        }
        return Parsed.ToolCall(name, args)
    }

    private const val TOOL_PREFIX = "TOOL:"
    private const val ANSWER_PREFIX = "ANSWER:"
}

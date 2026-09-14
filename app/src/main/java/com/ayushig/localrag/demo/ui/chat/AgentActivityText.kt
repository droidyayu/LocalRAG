package com.ayushig.localrag.demo.ui.chat

import com.ayushig.localrag.android.AgentEvent

/** The one-line status shown with the assistant bubble while a turn runs. Pure, tested. */
internal fun AgentEvent.statusText(): String = when (this) {
    AgentEvent.Thinking -> "Thinking…"
    is AgentEvent.CallingTool -> "Calling ${humanizeToolName(name)}…"
    is AgentEvent.ToolFinished -> "Reading results…"
    AgentEvent.JudgingAnswer -> "Verifying answer…"
}

/** Tool names read as function names to the model but not to a person. */
internal fun humanizeToolName(name: String): String =
    name.lowercase().split('_').filter { it.isNotEmpty() }.joinToString(" ")

/**
 * One line per argument for the turn details. Values come from the model, so they are
 * flattened to a single line and capped: a stray newline in a value must never read as a
 * protocol line in the transcript UI.
 */
internal fun formatToolArgs(args: Map<String, String>): String {
    if (args.isEmpty()) return "no arguments"
    return args.entries.joinToString(" · ") { (key, value) ->
        val flat = value.replace(Regex("\\s+"), " ").trim().take(MAX_ARG_CHARS)
        "$key = $flat"
    }
}

private const val MAX_ARG_CHARS = 120

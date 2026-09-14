package com.ayushig.localrag.demo.ui.chat

import com.ayushig.localrag.android.AgentEvent
import com.ayushig.localrag.demo.domain.model.TurnTimings

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

/**
 * The compact line under a finished assistant bubble: "answered in 12s". Dynamic, so the
 * acceptance suite never asserts it — only the code-owned source labels above it.
 */
internal fun formatTurnTotal(timings: TurnTimings): String =
    "answered in ${formatDuration(timings.totalMs)}"

/**
 * The expanded breakdown in turn details: "Model 11.8s · Functions 4ms · 2 steps". Model
 * time is every generation call added together; functions read local memory and normally
 * round to single milliseconds, which is itself the diagnosis when generation feels slow.
 */
internal fun formatTurnBreakdown(timings: TurnTimings): String =
    "Model ${formatDuration(timings.generateMs)} · " +
        "Functions ${formatDuration(timings.toolMs)} · " +
        "${timings.rounds} step" + if (timings.rounds == 1) "" else "s"

private fun formatDuration(ms: Long): String {
    if (ms < 1000) return "${ms}ms"
    val tenths = (ms / 100).toInt()
    return if (tenths % 10 == 0) "${tenths / 10}s" else "${tenths / 10}.${tenths % 10}s"
}

private const val MAX_ARG_CHARS = 120

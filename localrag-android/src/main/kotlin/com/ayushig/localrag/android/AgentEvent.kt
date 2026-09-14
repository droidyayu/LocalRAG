package com.ayushig.localrag.android

/**
 * Progress signals from an agent turn, for hosts that want to show what the assistant is
 * doing while the turn runs. Informational only: ignoring them changes nothing about the
 * outcome. Emitted in order on the calling coroutine — thinking, then one call/finish pair
 * per executed tool, then judging before the outcome returns.
 */
sealed interface AgentEvent {
    /** The model is being consulted for the next plan step or the final text. */
    data object Thinking : AgentEvent

    /** A planned tool call is about to execute. [args] is what the model supplied. */
    data class CallingTool(val name: String, val args: Map<String, String>) : AgentEvent

    /** A tool returned. [sourceTitles] names the documents a search tool drew on, if any. */
    data class ToolFinished(
        val name: String,
        val observationChars: Int,
        val sourceTitles: List<String>,
    ) : AgentEvent

    /** All observations are in; the final text is being checked against them. */
    data object JudgingAnswer : AgentEvent
}

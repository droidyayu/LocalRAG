package com.ayushig.localrag.android

/** Where a passage came from in the retrieval pipeline. */
enum class MatchSource { BM25, VECTOR, HYBRID, PRECOMPUTED }

/**
 * A function the agent may call, defined and implemented by the host app. The SDK plans the
 * calls and judges the results; everything domain-specific — what the function reads, how
 * figures are rendered — lives app-side.
 *
 * @param argSpec short usage text shown to the model, e.g. "category=METALS, STOCKS, WEALTH or LEVERAGED".
 * @param execute runs the function. Null means misuse or a failed read: the turn ends honestly.
 */
data class ToolDefinition(
    val name: String,
    val description: String,
    val argSpec: String,
    val execute: suspend (args: Map<String, String>) -> ToolResult?,
)

/** One executed tool call, ready to paste back into the model's context. */
data class ToolResult(
    val tool: String,
    val text: String,
    val sourceTitles: List<String> = emptyList(),
)

/**
 * Everything the agent loop needs from the host app. Prompts, tool set, and policy copy are
 * configuration: the SDK owns the grammar, the rounds, and the output gate, but no words.
 */
data class AgentConfig(
    val systemPrompt: String,
    val tools: List<ToolDefinition>,
    val maxToolRounds: Int = 3,
    val maxConversationalChars: Int = 200,
)

/** What an agent turn produced. The host app renders Final and shows fixed text for the rest. */
sealed interface AgentOutcome {
    data class Final(
        val text: String,
        val sources: List<String>,
        val usedTools: List<String>,
    ) : AgentOutcome

    /** Malformed output, failed tools, or a gate rejection: say the fixed fallback instead. */
    data object Unresolved : AgentOutcome

    /** Advisory language in the final text: show the fixed refusal instead. */
    data object Refused : AgentOutcome
}

/**
 * One retrieved section of documentation, ready to show.
 *
 * [text] is the display form, so it keeps its Markdown. [screenLink] lets the host app offer a
 * jump to the screen the passage describes.
 */
data class Passage(
    val chunkId: String,
    val docId: String,
    val title: String,
    val heading: String?,
    val text: String,
    val screenLink: String?,
    val score: Float,
    val source: MatchSource,
)

sealed interface LocalRagState {
    data object Idle : LocalRagState

    data object Loading : LocalRagState

    /**
     * Ready to answer. [usingVectors] is false whenever no embedder is configured or the bundle
     * vectors failed the parity check, in which case retrieval is BM25 only.
     */
    data class Ready(
        val chunkCount: Int,
        val contentVersion: Int,
        val usingVectors: Boolean,
        val canGenerate: Boolean,
    ) : LocalRagState

    data class Failed(val reason: String) : LocalRagState
}

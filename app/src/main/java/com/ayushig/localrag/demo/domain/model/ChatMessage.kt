package com.ayushig.localrag.demo.domain.model

/** Who produced a message in the chat transcript. */
enum class Role { USER, MODEL }

/**
 * Where a reply's content came from. The agent's answer is labelled by which tools grounded it,
 * so a wrong answer can be traced to the wrong source.
 */
enum class MessageSource {
    MODEL,
    PORTFOLIO_DATA,
    DOCUMENTATION,

    /** The agent turn resolved to nothing; the reply says exactly that. */
    NO_INFORMATION,
}

data class ChatMessage(
    val id: String,
    val role: Role,
    val text: String,
    val isStreaming: Boolean = false,
    val source: MessageSource = MessageSource.MODEL,
    /** Titles of the passages that grounded a documentation answer. */
    val sources: List<String> = emptyList(),
    /** The function calls behind an assistant turn, in execution order. */
    val tools: List<ToolCallRecord> = emptyList(),
)

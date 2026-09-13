package com.ayushig.localrag.domain.model

/** Who produced a message in the chat transcript. */
enum class Role { USER, MODEL }

/**
 * Where a reply's content came from. Portfolio answers are templated from repository data and
 * never pass through the model, so the two are labelled differently in the transcript.
 */
enum class MessageSource { MODEL, PORTFOLIO_DATA }

/**
 * Timings for a single generation, measured in the data layer around the LiteRT-LM calls.
 *
 * [approxTokenCount] counts emitted stream chunks. One chunk is not guaranteed to be one token,
 * so every surface that shows it must label it approximate.
 */
data class GenerationMetrics(
    val timeToFirstTokenMs: Long,
    val totalTimeMs: Long,
    val approxTokenCount: Int,
    val tokensPerSecond: Double,
)

data class ChatMessage(
    val id: String,
    val role: Role,
    val text: String,
    val isStreaming: Boolean = false,
    val metrics: GenerationMetrics? = null,
    val source: MessageSource = MessageSource.MODEL,
)

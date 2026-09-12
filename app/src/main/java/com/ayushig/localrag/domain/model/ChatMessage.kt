package com.ayushig.localrag.domain.model

/** Who produced a message in the chat transcript. */
enum class Role { USER, MODEL }

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
)

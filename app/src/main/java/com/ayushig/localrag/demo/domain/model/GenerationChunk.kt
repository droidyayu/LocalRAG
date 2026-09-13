package com.ayushig.localrag.demo.domain.model

/** One event in a streaming generation. */
sealed interface GenerationChunk {
    data class Token(val text: String) : GenerationChunk

    data class Done(val metrics: GenerationMetrics) : GenerationChunk

    data class Error(val message: String) : GenerationChunk
}

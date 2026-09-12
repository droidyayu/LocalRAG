package com.ayushig.localrag.domain.model

/** Which compute backend the engine runs on. */
enum class LlmBackend { CPU, GPU }

/**
 * Everything the debug panel can change. Altering [backend] rebuilds the engine; altering
 * anything else rebuilds only the conversation.
 */
data class GenerationSettings(
    val systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION,
    val temperature: Float = 0.0f,
    val topK: Int = 1,
    val topP: Float = 1.0f,
    val maxOutputToken: Int = 256,
    val backend: LlmBackend = LlmBackend.CPU,
) {
    companion object {
        /** Baseline instruction. Keep verbatim so runs stay comparable. */
        const val DEFAULT_SYSTEM_INSTRUCTION: String =
            "You are a support assistant inside a stock trading app. Answer only using the\n" +
                "information given to you. Keep answers under three sentences. If the information\n" +
                "does not cover the question, say you don't have that information. Never give\n" +
                "investment advice or opinions about buying or selling."
    }
}

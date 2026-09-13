package com.ayushig.localrag.demo.domain.repository

import com.ayushig.localrag.demo.domain.model.EngineState
import com.ayushig.localrag.demo.domain.model.GenerationChunk
import com.ayushig.localrag.demo.domain.model.GenerationSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface LlmRepository {
    val engineState: StateFlow<EngineState>

    /** Absolute path the model file is expected at, for the model-missing screen. */
    val expectedModelPath: String

    fun isModelPresent(): Boolean

    suspend fun initialize()

    fun generate(prompt: String): Flow<GenerationChunk>

    suspend fun resetConversation()

    /** Applies new settings, rebuilding engine or conversation as needed. */
    suspend fun applySettings(settings: GenerationSettings)

    fun release()
}

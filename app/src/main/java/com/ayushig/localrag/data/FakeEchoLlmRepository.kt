package com.ayushig.localrag.data

import com.ayushig.localrag.domain.model.EngineState
import com.ayushig.localrag.domain.model.GenerationChunk
import com.ayushig.localrag.domain.model.GenerationMetrics
import com.ayushig.localrag.domain.model.GenerationSettings
import com.ayushig.localrag.domain.repository.LlmRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

/**
 * Phase 1 stand-in: streams the prompt back one word at a time so the UI, cancellation and the
 * metrics plumbing can be exercised before the real engine exists. Replaced in Phase 3 by the
 * LiteRT-LM backed implementation.
 */
@Singleton
class FakeEchoLlmRepository @Inject constructor(
    private val modelFileLocator: ModelFileLocator,
) : LlmRepository {

    private val _engineState = MutableStateFlow<EngineState>(EngineState.Idle)
    override val engineState: StateFlow<EngineState> = _engineState.asStateFlow()

    override val expectedModelPath: String get() = modelFileLocator.absolutePath

    override fun isModelPresent(): Boolean = modelFileLocator.isPresent()

    override suspend fun initialize() {
        if (_engineState.value is EngineState.Ready) return
        _engineState.value = EngineState.Loading
        delay(FAKE_LOAD_MS)
        _engineState.value = EngineState.Ready(loadTimeMs = FAKE_LOAD_MS)
    }

    override fun generate(prompt: String): Flow<GenerationChunk> = flow {
        val start = System.currentTimeMillis()
        delay(FAKE_FIRST_TOKEN_MS)
        val firstTokenAt = System.currentTimeMillis()
        val words = "Echo: $prompt".split(" ")
        words.forEachIndexed { index, word ->
            emit(GenerationChunk.Token(if (index == 0) word else " $word"))
            delay(FAKE_PER_TOKEN_MS)
        }
        val total = System.currentTimeMillis() - start
        val ttft = firstTokenAt - start
        val decodeSeconds = (total - ttft).coerceAtLeast(1L) / 1000.0
        emit(
            GenerationChunk.Done(
                GenerationMetrics(
                    timeToFirstTokenMs = ttft,
                    totalTimeMs = total,
                    approxTokenCount = words.size,
                    tokensPerSecond = words.size / decodeSeconds,
                )
            )
        )
    }

    override suspend fun resetConversation() = Unit

    override suspend fun applySettings(settings: GenerationSettings) = Unit

    override fun release() {
        _engineState.value = EngineState.Idle
    }

    private companion object {
        const val FAKE_LOAD_MS = 1_200L
        const val FAKE_FIRST_TOKEN_MS = 400L
        const val FAKE_PER_TOKEN_MS = 60L
    }
}

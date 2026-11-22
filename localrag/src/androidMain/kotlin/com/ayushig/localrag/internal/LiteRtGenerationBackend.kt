package com.ayushig.localrag.internal

import com.ayushig.localrag.ComputeBackend
import com.ayushig.localrag.GenerationBackend
import com.ayushig.localrag.GenerationEngine
import com.ayushig.localrag.GenerationRequest
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LiteRtLmJniException
import com.google.ai.edge.litertlm.SamplerConfig
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.toList

/**
 * Loads the LiteRT-LM generation engine onto one compute backend.
 *
 * Load failures propagate as exceptions; the shared [GenerationLoader] policy decides the
 * fallback. Generation is collected in full before anything is returned. The output gate has to
 * see a whole answer to judge it, and a token already shown to a user cannot be withdrawn.
 */
internal class LiteRtGenerationBackend : GenerationBackend {
    override suspend fun load(
        modelPath: String,
        cacheDir: String,
        compute: ComputeBackend,
    ): GenerationEngine {
        val engine = Engine(
            EngineConfig(
                modelPath = modelPath,
                backend = when (compute) {
                    ComputeBackend.GPU -> Backend.GPU()
                    ComputeBackend.CPU -> Backend.CPU()
                },
                cacheDir = cacheDir,
            ),
        )
        engine.initialize()
        return LiteRtGenerationEngine(engine)
    }

    /**
     * Null on any failure, so the caller falls back rather than erroring. Stateless: every call
     * decodes on a fresh conversation that is closed before returning, so no turn can leak into
     * the next answer.
     */
    private class LiteRtGenerationEngine(private val engine: Engine) : GenerationEngine {
        override suspend fun generate(request: GenerationRequest): String? = try {
            val conversation = newConversation(request)
            try {
                conversation.sendMessageAsync(request.prompt).toList().joinToString("") { it.toString() }
            } finally {
                runCatching { conversation.close() }
            }
        } catch (cancelled: CancellationException) {
            // On the JVM CancellationException is an IllegalStateException: it must propagate so
            // a superseded turn ends cancelled, never as a null-based Unresolved result.
            throw cancelled
        } catch (failure: LiteRtLmJniException) {
            null
        } catch (failure: IllegalStateException) {
            null
        }

        /** Idempotent: unload paths close more than once. */
        override fun close() {
            if (closed.compareAndSet(false, true)) {
                runCatching { engine.close() }
            }
        }

        private val closed = AtomicBoolean(false)

        private fun newConversation(request: GenerationRequest): Conversation = engine.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(request.systemInstruction),
                // Greedy: the answer must be reproducible, and creativity has no value when the
                // model is only allowed to restate a passage.
                samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0),
                maxOutputToken = request.maxOutputTokens,
            ),
        )
    }
}

package com.ayushig.localrag.android.internal

import com.google.ai.edge.litertlm.Backend
import java.util.concurrent.atomic.AtomicBoolean
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LiteRtLmJniException
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.flow.toList

/**
 * Wraps the LiteRT-LM generation engine.
 *
 * One Engine per process; a second instance means a native out-of-memory kill rather than a
 * catchable error. The engine is closed explicitly instead of through a use block, because it
 * outlives any single call.
 *
 * Generation is collected in full before anything is returned. The output gate has to see a whole
 * answer to judge it, and a token already shown to a user cannot be withdrawn.
 */
internal class Generator private constructor(
    private val engine: Engine,
    private val systemInstruction: String,
    private val maxOutputTokens: Int,
) {

    /**
     * Null on any failure, so the caller falls back rather than erroring. Stateless: every
     * call decodes on a fresh conversation that is closed before returning, so no turn can
     * leak into the next answer. One engine per process; never a fresh engine per call.
     */
    suspend fun generate(prompt: String): String? = try {
        val conversation = newConversation()
        try {
            conversation.sendMessageAsync(prompt).toList().joinToString("") { it.toString() }
        } finally {
            runCatching { conversation.close() }
        }
    } catch (failure: LiteRtLmJniException) {
        null
    } catch (failure: IllegalStateException) {
        null
    }

    /**
     * Idempotent: unload paths call this more than once, and the process slot must be released
     * exactly once per acquired engine.
     */
    fun close() {
        if (closed.compareAndSet(false, true)) {
            runCatching { engine.close() }
            EngineSlot.release()
        }
    }

    private val closed = AtomicBoolean(false)

    private fun newConversation(): Conversation = engine.createConversation(
        ConversationConfig(
            systemInstruction = Contents.of(systemInstruction),
            // Greedy: the answer must be reproducible, and creativity has no value when the model
            // is only allowed to restate a passage.
            samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0),
            maxOutputToken = maxOutputTokens,
        ),
    )

    companion object {

        /**
         * Blocking for several seconds. Callers must already be off the main thread.
         */
        fun createOrNull(
            modelPath: String,
            cacheDir: String,
            systemInstruction: String,
            maxOutputTokens: Int,
            onFailure: (String) -> Unit,
        ): Generator? {
            if (!EngineSlot.acquire()) {
                onFailure(
                    "a generation engine already exists in this process; refusing a second " +
                        "instance and running without a generator",
                )
                return null
            }
            return try {
                val engine = Engine(
                    EngineConfig(
                        modelPath = modelPath,
                        backend = Backend.CPU(),
                        cacheDir = cacheDir,
                    ),
                )
                engine.initialize()
                Generator(engine, systemInstruction, maxOutputTokens)
            } catch (failure: LiteRtLmJniException) {
                EngineSlot.release()
                onFailure("generation engine failed to load: ${failure.message}")
                null
            } catch (failure: IllegalStateException) {
                EngineSlot.release()
                onFailure("generation engine failed to load: ${failure.message}")
                null
            }
        }
    }
}


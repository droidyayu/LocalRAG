package com.ayushig.localrag.android.internal

import com.ayushig.localrag.android.Passage
import com.google.ai.edge.litertlm.Backend
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
) : TextGenerator {

    private var conversation: Conversation? = null

    /** Null on any failure, so the caller falls back to an extractive answer rather than an error. */
    override suspend fun generate(prompt: String): String? = try {
        val active = conversation ?: newConversation().also { conversation = it }
        active.sendMessageAsync(prompt).toList().joinToString("") { it.toString() }
    } catch (failure: LiteRtLmJniException) {
        null
    } catch (failure: IllegalStateException) {
        null
    }

    /** A fresh conversation from the same engine. Never a fresh engine. */
    override fun reset() {
        runCatching { conversation?.close() }
        conversation = null
    }

    override fun close() {
        reset()
        runCatching { engine.close() }
    }

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
        ): Generator? = try {
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
            onFailure("generation engine failed to load: ${failure.message}")
            null
        } catch (failure: IllegalStateException) {
            onFailure("generation engine failed to load: ${failure.message}")
            null
        }
    }
}

/**
 * Builds the prompt: system instruction, the retrieved passages, the question. Nothing else, and
 * no conversation history, so the model has no opportunity to carry an earlier mistake forward.
 */
internal object PromptBuilder {

    fun build(query: String, passages: List<Passage>, maxContextTokens: Int): String {
        val budget = maxContextTokens * APPROXIMATE_CHARACTERS_PER_TOKEN
        val context = StringBuilder()
        for ((index, passage) in passages.withIndex()) {
            val block = buildString {
                append(passage.title)
                passage.heading?.let { append(" — ").append(it) }
                append("\n").append(passage.text).append("\n\n")
            }
            if (context.length + block.length <= budget) {
                context.append(block)
                continue
            }
            // Never send a prompt with no documentation in it. A budget too small for even one
            // passage truncates that passage instead of asking the model to answer from nothing.
            if (index == 0) context.append(block.take(budget.coerceAtLeast(MINIMUM_CONTEXT_CHARS)))
            break
        }
        return "Documentation:\n$context\nQuestion: $query"
    }

    private const val APPROXIMATE_CHARACTERS_PER_TOKEN = 4
    private const val MINIMUM_CONTEXT_CHARS = 200
}

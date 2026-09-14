package com.ayushig.localrag.android.internal

import android.util.Log
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
        private const val TAG = "LocalRagGenerator"

        /**
         * Blocking for several seconds, plus one tiny probe generation when trying GPU.
         * Callers must already be off the main thread.
         *
         * GPU first when asked: prefill and decode both run several times faster there, and
         * this turn's whole cost is the model. A GPU engine that loads is not necessarily
         * one that generates — on some devices it decodes gibberish or nothing, which would
         * turn every turn unresolved — so the probe below decides, and anything but an
         * exact OK falls back to CPU inside this call.
         */
        suspend fun createOrNull(
            modelPath: String,
            cacheDir: String,
            systemInstruction: String,
            maxOutputTokens: Int,
            preferGpuBackend: Boolean,
            onFailure: (String) -> Unit,
        ): Generator? {
            if (!EngineSlot.acquire()) {
                onFailure(
                    "a generation engine already exists in this process; refusing a second " +
                        "instance and running without a generator",
                )
                return null
            }
            // Null when this backend cannot carry the model. Slot ownership stays with
            // this caller: it releases exactly once, after the last backend has been tried.
            var lastError = "unknown load error"
            fun loadOn(backend: Backend): Engine? = try {
                val engine = Engine(
                    EngineConfig(
                        modelPath = modelPath,
                        backend = backend,
                        cacheDir = cacheDir,
                    ),
                )
                engine.initialize()
                engine
            } catch (failure: LiteRtLmJniException) {
                lastError = failure.message ?: "native load failed"
                null
            } catch (failure: IllegalStateException) {
                lastError = failure.message ?: "load failed"
                null
            }
            if (preferGpuBackend) {
                val gpu = loadOn(Backend.GPU())
                if (gpu != null && probeBackend(gpu)) {
                    Log.i(TAG, "generation engine on GPU backend")
                    return Generator(gpu, systemInstruction, maxOutputTokens)
                }
                if (gpu != null) {
                    Log.w(TAG, "GPU backend failed its probe, closing it and falling back to CPU")
                    runCatching { gpu.close() }
                } else {
                    Log.w(TAG, "GPU backend failed ($lastError), falling back to CPU")
                }
            }
            val engine = loadOn(Backend.CPU()) ?: run {
                EngineSlot.release()
                onFailure("generation engine failed to load: $lastError")
                return null
            }
            Log.i(TAG, "generation engine on CPU backend")
            return Generator(engine, systemInstruction, maxOutputTokens)
        }

        /**
         * One tiny generation on a throwaway conversation: a working backend reads back
         * OK, a broken one decodes gibberish or nothing. Bounded to 8 output tokens, so
         * even a slow backend answers in seconds. False means fall back, never retry.
         */
        private suspend fun probeBackend(engine: Engine): Boolean = try {
            val probe = engine.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of("Reply with exactly: OK"),
                    samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0),
                    maxOutputToken = 8,
                ),
            )
            try {
                val out = probe.sendMessageAsync("ping").toList().joinToString("") { it.toString() }
                val ok = out.contains("OK")
                if (ok) {
                    Log.i(TAG, "GPU probe passed")
                } else {
                    Log.w(TAG, "GPU probe replied: ${out.take(80)}")
                }
                ok
            } finally {
                runCatching { probe.close() }
            }
        } catch (failure: LiteRtLmJniException) {
            Log.w(TAG, "GPU probe failed: ${failure.message}")
            false
        } catch (failure: IllegalStateException) {
            Log.w(TAG, "GPU probe failed: ${failure.message}")
            false
        }
    }
}


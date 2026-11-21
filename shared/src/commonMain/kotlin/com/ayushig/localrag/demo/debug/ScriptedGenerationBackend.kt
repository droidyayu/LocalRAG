package com.ayushig.localrag.demo.debug

import com.ayushig.localrag.ComputeBackend
import com.ayushig.localrag.GenerationBackend
import com.ayushig.localrag.GenerationEngine
import com.ayushig.localrag.GenerationRequest
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A [GenerationBackend] that answers from [ScriptedModel] instead of an on-device model, for
 * deterministic acceptance runs. Retrieval, tools and the output gate all stay real; only the
 * wording and tool choices are scripted.
 *
 * @param slowMillis how long a non-probe generation waits before replying. Zero answers at once;
 * the "scripted-slow" launch mode waits long enough that a test can cancel the turn mid-flight.
 */
class ScriptedGenerationBackend(private val slowMillis: Long = 0) : GenerationBackend {

    override suspend fun load(
        modelPath: String,
        cacheDir: String,
        compute: ComputeBackend,
    ): GenerationEngine = ScriptedEngine(slowMillis)

    @OptIn(ExperimentalAtomicApi::class)
    private class ScriptedEngine(private val slowMillis: Long) : GenerationEngine {
        private val currentStop = AtomicReference<CompletableDeferred<Unit>?>(null)
        private val closed = AtomicBoolean(false)

        override suspend fun generate(request: GenerationRequest): String? {
            // The load-policy probe must answer at once, never after the slow delay.
            if (request.prompt == PROBE_PROMPT) {
                return ScriptedModel.reply(request.prompt, request.systemInstruction)
            }
            val stop = CompletableDeferred<Unit>()
            currentStop.store(stop)
            try {
                // Ended early by cancelGeneration, by the timeout, or by caller cancellation:
                // the same seam the Swift engine exposes, so the shared cancel path is testable.
                withTimeoutOrNull(slowMillis) { stop.await() }
            } finally {
                currentStop.compareAndSet(stop, null)
            }
            return ScriptedModel.reply(request.prompt, request.systemInstruction)
        }

        override fun cancelGeneration() {
            currentStop.load()?.complete(Unit)
        }

        override fun close() {
            if (closed.compareAndSet(false, true)) {
                currentStop.load()?.complete(Unit)
            }
        }
    }

    private companion object {
        const val PROBE_PROMPT = "ping"
    }
}

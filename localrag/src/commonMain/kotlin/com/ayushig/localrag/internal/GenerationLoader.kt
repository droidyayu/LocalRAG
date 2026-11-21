package com.ayushig.localrag.internal

import com.ayushig.localrag.ComputeBackend
import com.ayushig.localrag.GenerationBackend
import com.ayushig.localrag.GenerationEngine
import com.ayushig.localrag.GenerationRequest
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlinx.coroutines.CancellationException

/**
 * The load policy shared by every platform: GPU first when asked, a tiny probe that must read
 * back OK, CPU as the fallback, one engine per process, and a slot that is released exactly once.
 *
 * Ported from the previous Android-local load policy; backends only load a generation model
 * onto one compute backend and throw on any load failure.
 */
internal object GenerationLoader {
    private const val TAG = "LocalRagGenerator"

    /**
     * Blocking for several seconds, plus one tiny probe generation when trying GPU. Callers must
     * already be off the main thread.
     *
     * GPU first when asked: prefill and decode both run several times faster there, and this
     * turn's whole cost is the model. A GPU engine that loads is not necessarily one that
     * generates — on some devices it decodes gibberish or nothing, which would turn every turn
     * unresolved — so the probe below decides, and anything but an exact OK falls back to CPU
     * inside this call.
     */
    suspend fun loadOrNull(
        backend: GenerationBackend,
        modelPath: String,
        cacheDir: String,
        preferGpu: Boolean,
        onFailure: (String) -> Unit,
    ): GenerationEngine? {
        if (!EngineSlot.acquire()) {
            onFailure(
                "a generation engine already exists in this process; refusing a second " +
                    "instance and running without a generator",
            )
            return null
        }
        // Null when this backend cannot carry the model. Slot ownership stays with this caller:
        // it releases exactly once, after the last backend has been tried.
        var lastError = "unknown load error"
        suspend fun loadOn(compute: ComputeBackend): GenerationEngine? = try {
            backend.load(modelPath, cacheDir, compute)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            lastError = failure.message ?: "load failed"
            null
        }
        try {
            if (preferGpu) {
                val gpu = loadOn(ComputeBackend.GPU)
                if (gpu != null) {
                    val ok = try {
                        probeBackend(gpu)
                    } catch (cancelled: CancellationException) {
                        runCatching { gpu.close() }
                        throw cancelled
                    }
                    if (ok) {
                        PlatformLog.log(LogLevel.INFO, TAG, "generation engine on GPU backend")
                        return LoadedEngine(gpu)
                    }
                    PlatformLog.log(
                        LogLevel.WARN,
                        TAG,
                        "GPU backend failed its probe, closing it and falling back to CPU",
                    )
                    runCatching { gpu.close() }
                } else {
                    PlatformLog.log(LogLevel.WARN, TAG, "GPU backend failed ($lastError), falling back to CPU")
                }
            }
            val engine = loadOn(ComputeBackend.CPU) ?: run {
                EngineSlot.release()
                onFailure("generation engine failed to load: $lastError")
                return null
            }
            PlatformLog.log(LogLevel.INFO, TAG, "generation engine on CPU backend")
            return LoadedEngine(engine)
        } catch (cancelled: CancellationException) {
            EngineSlot.release()
            throw cancelled
        }
    }

    /**
     * One tiny generation on the loaded engine: a working backend reads back OK, a broken one
     * decodes gibberish or nothing. Bounded to 8 output tokens, so even a slow backend answers
     * in seconds. False means fall back, never retry.
     */
    private suspend fun probeBackend(engine: GenerationEngine): Boolean = try {
        val out = engine.generate(
            GenerationRequest(
                prompt = "ping",
                systemInstruction = "Reply with exactly: OK",
                maxOutputTokens = 8,
            ),
        )
        val ok = out?.contains("OK") == true
        if (ok) {
            PlatformLog.log(LogLevel.INFO, TAG, "GPU probe passed")
        } else {
            PlatformLog.log(LogLevel.WARN, TAG, "GPU probe replied: ${out?.take(80)}")
        }
        ok
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        PlatformLog.log(LogLevel.WARN, TAG, "GPU probe failed: ${failure.message}")
        false
    }

    /**
     * Idempotent: unload paths call close more than once, and the process slot must be released
     * exactly once per acquired engine.
     */
    @OptIn(ExperimentalAtomicApi::class)
    private class LoadedEngine(private val inner: GenerationEngine) : GenerationEngine {
        private val closed = AtomicBoolean(false)

        override suspend fun generate(request: GenerationRequest): String? = inner.generate(request)

        override fun close() {
            if (closed.compareAndSet(false, true)) {
                runCatching { inner.close() }
                EngineSlot.release()
            }
        }
    }
}

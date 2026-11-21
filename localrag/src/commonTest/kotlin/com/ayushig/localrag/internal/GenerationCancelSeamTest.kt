package com.ayushig.localrag.internal

import com.ayushig.localrag.ComputeBackend
import com.ayushig.localrag.GenerationBackend
import com.ayushig.localrag.GenerationEngine
import com.ayushig.localrag.GenerationRequest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

/**
 * An engine implemented in Swift cannot see Kotlin cancellation inside its suspend call. The shared
 * wrapper must therefore ask the engine to stop (cancelGeneration) as soon as the caller is
 * cancelled, wait until the engine's own call has ended, and only then rethrow the cancellation.
 */
class GenerationCancelSeamTest {

    @AfterTest
    fun resetSlot() = EngineSlot.release()

    /** generate() ignores cancellation (like a Swift call) and ends only when cancelGeneration() runs. */
    private class UncancellableEngine : GenerationEngine {
        val events = mutableListOf<String>()
        private val stop = CompletableDeferred<Unit>()
        var cancelCalls = 0
            private set

        override suspend fun generate(request: GenerationRequest): String? {
            if (request.prompt == "ping") return "OK"
            events += "start"
            withContext(NonCancellable) { stop.await() }
            events += "end"
            return null
        }

        override fun cancelGeneration() {
            cancelCalls++
            stop.complete(Unit)
        }

        override fun close() = Unit
    }

    private suspend fun load(engine: GenerationEngine): GenerationEngine {
        val backend = object : GenerationBackend {
            override suspend fun load(modelPath: String, cacheDir: String, compute: ComputeBackend) = engine
        }
        return requireNotNull(GenerationLoader.loadOrNull(backend, "/m", "/c", preferGpu = false, onFailure = {}))
    }

    @Test
    fun cancellingTheCallerStopsTheEngineAndWaitsForItsCallToEnd() = runTest {
        val inner = UncancellableEngine()
        val engine = load(inner)

        val turn = async { engine.generate(GenerationRequest("question", "sys", 16)) }
        advanceUntilIdle()
        turn.cancel()
        advanceUntilIdle()

        assertEquals(1, inner.cancelCalls, "the engine must be asked to stop exactly once")
        assertEquals(listOf("start", "end"), inner.events, "the engine call must have ended before the caller returns")
        assertTrue(turn.isCancelled, "the caller still sees its cancellation")
    }

    @Test
    fun aCallThatFinishesNormallyNeverAsksTheEngineToStop() = runTest {
        val inner = object : GenerationEngine {
            var cancelCalls = 0

            override suspend fun generate(request: GenerationRequest): String? = "answer"

            override fun cancelGeneration() {
                cancelCalls++
            }

            override fun close() = Unit
        }
        val engine = load(inner)

        assertEquals("answer", engine.generate(GenerationRequest("question", "sys", 16)))

        assertEquals(0, inner.cancelCalls)
    }
}

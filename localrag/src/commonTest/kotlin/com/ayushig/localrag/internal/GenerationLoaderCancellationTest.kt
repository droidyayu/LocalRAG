package com.ayushig.localrag.internal

import com.ayushig.localrag.ComputeBackend
import com.ayushig.localrag.GenerationBackend
import com.ayushig.localrag.GenerationEngine
import com.ayushig.localrag.GenerationRequest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/** A load cancelled while the GPU probe is running must close the engine and free the slot. */
class GenerationLoaderCancellationTest {

    @AfterTest
    fun resetSlot() = EngineSlot.release()

    private class HangingProbeEngine : GenerationEngine {
        var closeCalls = 0

        override suspend fun generate(request: GenerationRequest): String? = awaitCancellation()

        override fun close() {
            closeCalls++
        }
    }

    @Test
    fun cancellationDuringTheProbeClosesTheEngineAndFreesTheSlot() = runTest {
        val engine = HangingProbeEngine()
        val backend = object : GenerationBackend {
            override suspend fun load(modelPath: String, cacheDir: String, compute: ComputeBackend) = engine
        }

        val job = launch {
            GenerationLoader.loadOrNull(backend, "/m", "/c", preferGpu = true, onFailure = {})
        }
        advanceUntilIdle()
        job.cancel()
        advanceUntilIdle()

        assertTrue(job.isCancelled)
        assertEquals(1, engine.closeCalls)
        assertTrue(EngineSlot.acquire(), "the slot must be free after a cancelled load")
    }
}

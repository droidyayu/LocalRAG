package com.ayushig.localrag.internal

import com.ayushig.localrag.ComputeBackend
import com.ayushig.localrag.GenerationBackend
import com.ayushig.localrag.GenerationEngine
import com.ayushig.localrag.GenerationRequest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest

/**
 * The load policy shared by every platform: GPU first when asked, a tiny probe that must read
 * back OK, CPU as the fallback, one engine per process, and a slot that is released exactly once.
 */
class GenerationLoaderTest {

    @AfterTest
    fun resetSlot() = EngineSlot.release()

    private class FakeEngine(
        private val backend: FakeBackend,
        val compute: ComputeBackend,
        private val probeReply: String?,
    ) : GenerationEngine {
        var closeCalls = 0
        val requests = mutableListOf<GenerationRequest>()

        override suspend fun generate(request: GenerationRequest): String? {
            requests += request
            return if (request.prompt == PROBE_PROMPT) probeReply else "answer"
        }

        override fun close() {
            closeCalls++
            if (closeCalls == 1) backend.live--
        }
    }

    private class FakeBackend(
        private val gpuFails: Boolean = false,
        private val cpuFails: Boolean = false,
        private val gpuProbeReply: String? = "OK",
    ) : GenerationBackend {
        val loads = mutableListOf<ComputeBackend>()
        val engines = mutableListOf<FakeEngine>()
        var live = 0
        var maxLive = 0

        override suspend fun load(
            modelPath: String,
            cacheDir: String,
            compute: ComputeBackend,
        ): GenerationEngine {
            loads += compute
            if (compute == ComputeBackend.GPU && gpuFails) error("gpu load failed")
            if (compute == ComputeBackend.CPU && cpuFails) error("cpu load failed")
            val engine = FakeEngine(this, compute, if (compute == ComputeBackend.GPU) gpuProbeReply else "OK")
            engines += engine
            live++
            if (live > maxLive) maxLive = live
            return engine
        }
    }

    private suspend fun load(
        backend: GenerationBackend,
        preferGpu: Boolean = true,
        failures: MutableList<String> = mutableListOf(),
    ): GenerationEngine? = GenerationLoader.loadOrNull(
        backend = backend,
        modelPath = "/models/m.litertlm",
        cacheDir = "/cache",
        preferGpu = preferGpu,
        onFailure = { failures += it },
    )

    @Test
    fun gpuThatLoadsAndPassesTheProbeIsUsed() = runTest {
        val backend = FakeBackend()
        val engine = assertNotNull(load(backend))

        assertEquals(listOf(ComputeBackend.GPU), backend.loads)
        assertEquals(ComputeBackend.GPU, backend.engines.single().compute)
        assertEquals("answer", engine.generate(GenerationRequest("hello", "sys", 16)))
    }

    @Test
    fun theProbeAsksForExactlyOkWithAtMostEightTokens() = runTest {
        val backend = FakeBackend()
        load(backend)

        val probe = backend.engines.single().requests.first()
        assertEquals(PROBE_PROMPT, probe.prompt)
        assertEquals("Reply with exactly: OK", probe.systemInstruction)
        assertTrue(probe.maxOutputTokens <= 8)
    }

    @Test
    fun aGpuLoadFailureFallsBackToCpu() = runTest {
        val backend = FakeBackend(gpuFails = true)
        val engine = assertNotNull(load(backend))

        assertEquals(listOf(ComputeBackend.GPU, ComputeBackend.CPU), backend.loads)
        assertEquals("answer", engine.generate(GenerationRequest("hello", "sys", 16)))
    }

    @Test
    fun aGpuEngineThatFailsTheProbeIsClosedBeforeCpuLoads() = runTest {
        val backend = FakeBackend(gpuProbeReply = "gibberish")
        val engine = assertNotNull(load(backend))

        assertEquals(listOf(ComputeBackend.GPU, ComputeBackend.CPU), backend.loads)
        assertEquals(1, backend.engines.first().closeCalls)
        assertEquals(1, backend.maxLive, "two engines must never be alive at once")
        assertEquals("answer", engine.generate(GenerationRequest("hello", "sys", 16)))
    }

    @Test
    fun aProbeThatReturnsNothingFallsBack() = runTest {
        val backend = FakeBackend(gpuProbeReply = null)
        assertNotNull(load(backend))

        assertEquals(listOf(ComputeBackend.GPU, ComputeBackend.CPU), backend.loads)
    }

    @Test
    fun withoutGpuPreferenceOnlyCpuLoadsAndNothingIsProbed() = runTest {
        val backend = FakeBackend()
        val engine = assertNotNull(load(backend, preferGpu = false))

        assertEquals(listOf(ComputeBackend.CPU), backend.loads)
        assertTrue(backend.engines.single().requests.isEmpty())
        assertEquals("answer", engine.generate(GenerationRequest("hello", "sys", 16)))
    }

    @Test
    fun whenEveryBackendFailsTheResultIsNullAndTheSlotIsFree() = runTest {
        val failures = mutableListOf<String>()
        val result = load(FakeBackend(gpuFails = true, cpuFails = true), failures = failures)

        assertNull(result)
        assertEquals(1, failures.size)
        assertTrue(failures.single().contains("cpu load failed"), failures.single())
        assertTrue(EngineSlot.acquire(), "a failed load must release the process slot")
    }

    @Test
    fun aSecondEngineIsRefusedWhileOneIsLive() = runTest {
        val first = FakeBackend()
        assertNotNull(load(first))

        val second = FakeBackend()
        val failures = mutableListOf<String>()
        assertNull(load(second, failures = failures))
        assertTrue(second.loads.isEmpty(), "a refused load must not touch the backend")
        assertTrue(failures.single().contains("already exists"), failures.single())
    }

    @Test
    fun closingTwiceClosesTheEngineAndFreesTheSlotOnce() = runTest {
        val backend = FakeBackend()
        val engine = assertNotNull(load(backend))

        engine.close()
        engine.close()

        assertEquals(1, backend.engines.single().closeCalls)
        assertTrue(EngineSlot.acquire(), "close must release the slot")
        EngineSlot.release()
    }

    @Test
    fun aModelSwitchNeverHasTwoLiveEngines() = runTest {
        val backend = FakeBackend()
        val first = assertNotNull(load(backend))
        first.close()
        val second = assertNotNull(load(backend))
        second.close()

        assertEquals(1, backend.maxLive)
    }

    @Test
    fun cancellationIsNotSwallowedAndTheSlotIsFreed() = runTest {
        val cancelling = object : GenerationBackend {
            override suspend fun load(
                modelPath: String,
                cacheDir: String,
                compute: ComputeBackend,
            ): GenerationEngine = throw CancellationException("cancelled")
        }
        var thrown = false
        try {
            load(cancelling)
        } catch (e: CancellationException) {
            thrown = true
        }
        assertTrue(thrown, "CancellationException must propagate")
        assertTrue(EngineSlot.acquire(), "a cancelled load must release the slot")
    }

    private companion object {
        const val PROBE_PROMPT = "ping"
    }
}

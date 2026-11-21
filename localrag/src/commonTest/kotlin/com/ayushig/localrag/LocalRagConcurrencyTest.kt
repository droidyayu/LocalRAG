package com.ayushig.localrag

import com.ayushig.localrag.internal.EngineSlot
import com.ayushig.localrag.internal.SignatureVerifier
import com.ayushig.localrag.testing.ControllableGenerationBackend
import com.ayushig.localrag.testing.FakePlatform
import com.ayushig.localrag.testing.NoEmbeddingBackend
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/**
 * The races a phone produces and a unit test normally never does: a memory warning in the middle
 * of a turn, a second question while the first is still decoding, a model switch, two callers
 * initializing at once. Each one must end with at most one live engine, no engine closed under a
 * running generation, and no coroutine cancellation swallowed.
 */
class LocalRagConcurrencyTest {

    @AfterTest
    fun resetSlot() = EngineSlot.release()

    private val agentConfig = AgentConfig(systemPrompt = "Test assistant.", tools = emptyList())

    private fun config(model: String? = "/models/a.litertlm") = LocalRag.Config(
        generationModelPath = model,
        cacheDir = "/tmp/localrag-test-cache",
        appVersion = "1.0.0",
        preferGpuBackend = false,
    )

    private fun TestScope.newRag(
        backend: ControllableGenerationBackend,
        platform: FakePlatform = FakePlatform(),
        config: LocalRag.Config = config(),
    ): LocalRag = LocalRag(
        platform = platform,
        config = config,
        signatureVerifier = SignatureVerifier.RejectAll,
        generationBackend = backend,
        embeddingBackend = NoEmbeddingBackend,
        ioDispatcher = StandardTestDispatcher(testScheduler),
    )

    @Test
    fun unloadWaitsForAnInFlightTurn() = runTest {
        val backend = ControllableGenerationBackend()
        val rag = newRag(backend)
        rag.initialize()
        backend.holdGenerate = true

        val turn = async { rag.runAgent("hello", agentConfig) }
        backend.engine.started.receive()
        rag.unloadModels()
        advanceUntilIdle()
        assertEquals(0, backend.engine.closeCalls, "the engine must stay open while a turn is running")

        backend.releaseAll()
        val outcome = turn.await()
        advanceUntilIdle()

        assertIs<AgentOutcome.Final>(outcome)
        assertEquals(1, backend.engine.closeCalls)
        assertFalse(backend.engine.closedWhileGenerating)
        assertFalse(assertIs<LocalRagState.Ready>(rag.state.value).canGenerate)
    }

    @Test
    fun aNewTurnCancelsTheOldOneAndNeverOverlapsIt() = runTest {
        val backend = ControllableGenerationBackend()
        val rag = newRag(backend)
        rag.initialize()
        backend.holdGenerate = true

        val first = async { rag.runAgent("hello", agentConfig) }
        backend.engine.started.receive()
        val second = async { rag.runAgent("hello again", agentConfig) }
        advanceUntilIdle()

        assertTrue(first.isCancelled, "the superseded turn must be cancelled, not answered with null")
        backend.engine.started.receive()
        backend.releaseAll()
        assertIs<AgentOutcome.Final>(second.await())
        assertEquals(1, backend.maxConcurrentGenerates, "two generations must never overlap")
    }

    @Test
    fun aModelSwitchNeverHasTwoLiveEngines() = runTest {
        val backend = ControllableGenerationBackend()
        val rag = newRag(backend)
        rag.initialize()

        rag.updateConfig(config(model = "/models/b.litertlm"))
        rag.initialize()
        advanceUntilIdle()

        assertEquals(listOf("/models/a.litertlm", "/models/b.litertlm"), backend.loads)
        assertEquals(1, backend.maxLive)
        assertEquals(1, backend.engines.first().closeCalls)
    }

    @Test
    fun twoCallersInitializingAtOnceLoadOnceAndBothSeeReady() = runTest {
        val backend = ControllableGenerationBackend()
        val rag = newRag(backend)

        val a = async { rag.initialize() }
        val b = async { rag.initialize() }
        a.await()
        assertIs<LocalRagState.Ready>(rag.state.value)
        b.await()
        assertIs<LocalRagState.Ready>(rag.state.value)

        assertEquals(1, backend.loads.size)
    }

    @Test
    fun aMemoryWarningDropsTheModelButRetrievalKeepsWorking() = runTest {
        val backend = ControllableGenerationBackend()
        val platform = FakePlatform()
        val rag = newRag(backend, platform)
        rag.initialize()

        platform.memoryHandler!!.invoke()
        advanceUntilIdle()

        val state = assertIs<LocalRagState.Ready>(rag.state.value)
        assertFalse(state.canGenerate)
        assertFalse(state.usingVectors)
        assertEquals(1, backend.engine.closeCalls)
        assertTrue(rag.retrieveOnly("brokerage").isNotEmpty())
    }

    @Test
    fun aTurnAfterAnUnloadEndsUnresolvedInsteadOfCrashing() = runTest {
        val backend = ControllableGenerationBackend()
        val rag = newRag(backend)
        rag.initialize()
        rag.unloadModels()
        advanceUntilIdle()

        assertIs<AgentOutcome.Unresolved>(rag.runAgent("hello", agentConfig))
    }

    @Test
    fun releaseStopsListeningForMemoryWarningsAndGoesIdle() = runTest {
        val backend = ControllableGenerationBackend()
        val platform = FakePlatform()
        val rag = newRag(backend, platform)
        rag.initialize()

        rag.release()
        advanceUntilIdle()

        assertNull(platform.memoryHandler)
        assertEquals(LocalRagState.Idle, rag.state.value)
        assertEquals(1, backend.engine.closeCalls)
    }

    @Test
    fun withoutAGenerationModelRetrievalStillAnswers() = runTest {
        val backend = ControllableGenerationBackend()
        val rag = newRag(backend, config = config(model = null))
        rag.initialize()

        assertFalse(assertIs<LocalRagState.Ready>(rag.state.value).canGenerate)
        assertTrue(backend.loads.isEmpty())
        assertTrue(rag.retrieveOnly("brokerage").isNotEmpty())
    }
}

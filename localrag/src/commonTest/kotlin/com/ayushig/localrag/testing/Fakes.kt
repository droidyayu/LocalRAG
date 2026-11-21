package com.ayushig.localrag.testing

import com.ayushig.localrag.ComputeBackend
import com.ayushig.localrag.EmbeddingBackend
import com.ayushig.localrag.EmbeddingEngine
import com.ayushig.localrag.EmbeddingSpec
import com.ayushig.localrag.GenerationBackend
import com.ayushig.localrag.GenerationEngine
import com.ayushig.localrag.GenerationRequest
import com.ayushig.localrag.LocalRagPlatform
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel

/** A platform with a real bundle in memory, no downloads, and a memory-warning handle tests can fire. */
internal class FakePlatform(private val bundle: ByteArray = SdkTestBundle.bytes()) : LocalRagPlatform {
    var memoryHandler: (() -> Unit)? = null
        private set

    override fun readBundledBytes(path: String): ByteArray = bundle

    override val downloadsDirectory: String = "/nonexistent/localrag-test-downloads"

    override fun onMemoryPressure(handler: () -> Unit): () -> Unit {
        memoryHandler = handler
        return { memoryHandler = null }
    }
}

/** An embedding backend nobody should call: tests configure no embedding model. */
internal object NoEmbeddingBackend : EmbeddingBackend {
    override fun load(modelPath: String, cacheDir: String, spec: EmbeddingSpec): EmbeddingEngine =
        error("no embedding model is configured in these tests")
}

/**
 * A generation backend whose engines can be held mid-generation, so tests can create the races
 * that matter: a memory warning during a turn, a second turn during a turn, a model switch.
 */
internal class ControllableGenerationBackend : GenerationBackend {
    val loads = mutableListOf<String>()
    val engines = mutableListOf<ControllableEngine>()
    var live = 0
    var maxLive = 0

    /** When true, every non-probe generation waits for [releaseAll]. */
    var holdGenerate = false
    var concurrentGenerates = 0
    var maxConcurrentGenerates = 0
    private var gate = CompletableDeferred<Unit>()

    val engine: ControllableEngine get() = engines.single()

    suspend fun awaitGate() = gate.await()

    fun releaseAll() {
        holdGenerate = false
        gate.complete(Unit)
        gate = CompletableDeferred()
    }

    override suspend fun load(modelPath: String, cacheDir: String, compute: ComputeBackend): GenerationEngine {
        loads += modelPath
        live++
        if (live > maxLive) maxLive = live
        return ControllableEngine(this).also { engines += it }
    }
}

internal class ControllableEngine(private val backend: ControllableGenerationBackend) : GenerationEngine {
    var closeCalls = 0
        private set
    var closedWhileGenerating = false
        private set
    private var generating = 0

    /** One signal per generation that started and is not a probe. */
    val started = Channel<Unit>(Channel.UNLIMITED)

    override suspend fun generate(request: GenerationRequest): String? {
        if (request.prompt == "ping") return "OK"
        generating++
        backend.concurrentGenerates++
        if (backend.concurrentGenerates > backend.maxConcurrentGenerates) {
            backend.maxConcurrentGenerates = backend.concurrentGenerates
        }
        try {
            started.trySend(Unit)
            if (backend.holdGenerate) backend.awaitGate()
            return "Hello there."
        } finally {
            generating--
            backend.concurrentGenerates--
        }
    }

    override fun close() {
        closeCalls++
        if (closeCalls == 1) {
            backend.live--
            if (generating > 0) closedWhileGenerating = true
        }
    }
}

/** An embedding backend whose engine runs a hook in the middle of embed(), then reports whether it was already closed. */
internal class ReentrantEmbeddingBackend(private val duringEmbed: () -> Unit) : EmbeddingBackend {
    val engine = ReentrantEmbeddingEngine(duringEmbed)

    override fun load(modelPath: String, cacheDir: String, spec: EmbeddingSpec): EmbeddingEngine {
        engine.dimensions = spec.dimensions
        return engine
    }
}

internal class ReentrantEmbeddingEngine(private val duringEmbed: () -> Unit) : EmbeddingEngine {
    override var dimensions: Int = 0
    var closeCalls = 0
        private set
    var closedDuringEmbed = false
        private set

    override fun embed(text: String): FloatArray? {
        duringEmbed()
        if (closeCalls > 0) closedDuringEmbed = true
        return FloatArray(dimensions) { 0.1f }
    }

    override fun close() {
        closeCalls++
    }
}

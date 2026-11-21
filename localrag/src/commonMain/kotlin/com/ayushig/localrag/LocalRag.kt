package com.ayushig.localrag

import com.ayushig.localrag.core.bundle.Bundle
import com.ayushig.localrag.core.bundle.EmbedderDescriptor
import com.ayushig.localrag.core.index.VectorIndex
import com.ayushig.localrag.internal.AgentRunner
import com.ayushig.localrag.internal.BundleLoader
import com.ayushig.localrag.internal.EmbedderLoader
import com.ayushig.localrag.internal.GenerationLoader
import com.ayushig.localrag.internal.defaultIoDispatcher
import com.ayushig.localrag.internal.LogLevel
import com.ayushig.localrag.internal.PlatformLog
import com.ayushig.localrag.internal.Retriever
import com.ayushig.localrag.internal.SignatureVerifier
import com.ayushig.localrag.internal.SingleFlight
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Answers help questions from documentation shipped inside the host app.
 *
 * No LiteRT type crosses this boundary. The host app supplies model paths and gets back passages
 * and text; whether an embedder or a generator is present changes the quality of the answer, never
 * the shape of the API.
 *
 * Concurrency model: one [Mutex] guards every field a second thread can touch (the loaded bundle,
 * the retriever, both engines, the config, whether a turn is generating, the memory listener and
 * every state update), and no section ever suspends while holding it. A turn snapshots the
 * generator under that mutex and then generates without it, so [unloadModels] (which OS memory
 * callbacks invoke from any thread and which therefore cannot suspend) never closes an engine
 * mid-turn: when a turn is active it only records an atomic request flag and the turn's own end,
 * under the mutex, performs the unload, release or model switch. A new [runAgent] turn cancels
 * the previous one through [SingleFlight], which then waits until that turn has fully ended before
 * the new turn starts, so two generations never overlap and the cancelled caller always receives
 * a [CancellationException]. Two [initialize] calls serialize on a second mutex: the loser waits
 * for the winner and accepts its result, so models load once. Every deferred cleanup runs
 * cleans up under [NonCancellable], so cancelling a turn can never leave the SDK locked or an
 * engine unclosed.
 */
@OptIn(ExperimentalAtomicApi::class)
class LocalRag internal constructor(
    private val platform: LocalRagPlatform,
    private var config: Config,
    private val signatureVerifier: SignatureVerifier,
    private val generationBackend: GenerationBackend,
    private val embeddingBackend: EmbeddingBackend,
    private val ioDispatcher: CoroutineDispatcher = defaultIoDispatcher,
) {

    data class Config(
        val bundleAssetPath: String = "localrag/docs.localrag",
        /** Null means BM25-only retrieval. */
        val embeddingModelPath: String? = null,
        /** Null means no generation: agent turns fall back and only retrieval is available. */
        val generationModelPath: String? = null,
        val cacheDir: String,
        val appVersion: String,
        /**
         * What this app believes its embedder does, compared field by field against the bundle
         * manifest. Required alongside [embeddingModelPath]: without it there is nothing to
         * compare the manifest to, and the parity check cannot do its job.
         */
        val embedder: EmbedderDescriptor? = null,
        val maxOutputTokens: Int = 256,
        /**
         * Try the GPU backend first and fall back to CPU inside the load call. The whole
         * turn cost is the model, and both prefill and decode run several times faster on
         * GPU. A GPU engine that loads but generates gibberish fails a tiny probe
         * generation and falls back too, so the preference never costs a turn.
         */
        val preferGpuBackend: Boolean = true,
        val topK: Int = 4,
        val categories: Set<String> = emptySet(),
        val systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION,
    )

    private val _state = MutableStateFlow<LocalRagState>(LocalRagState.Idle)
    val state: StateFlow<LocalRagState> = _state.asStateFlow()

    private val mutex = Mutex()
    private val initMutex = Mutex()
    private var bundle: Bundle? = null
    private var retriever: Retriever? = null
    private var embedder: EmbeddingEngine? = null
    private var generator: GenerationEngine? = null
    private var turnActive = false
    private var stopListening: (() -> Unit)? = null
    private val unloadRequested = AtomicBoolean(false)
    private val releaseRequested = AtomicBoolean(false)
    private val pendingConfig = AtomicReference<Config?>(null)

    /**
     * The only shared mutable state the SDK needs beyond [mutex]: which coroutine currently owns
     * the engine. Everything else hot here is a turn-local snapshot (the captured generator) or a
     * read-only resource (loaded bundle, models). Conversations are per-call and never retained,
     * so calls are stateless beyond this guard.
     */
    private val flight = SingleFlight()

    suspend fun initialize() {
        if (_state.value is LocalRagState.Ready) return
        if (!initMutex.tryLock()) {
            // Another call is already loading: wait for it and accept whatever it produced
            // (Ready or Failed) instead of loading the models a second time.
            initMutex.withLock { }
            return
        }
        try {
            if (_state.value is LocalRagState.Ready) return
            val loadConfig = mutex.withLock {
                _state.value = LocalRagState.Loading
                if (stopListening == null) {
                    stopListening = platform.onMemoryPressure { unloadModels() }
                }
                config
            }
            var newEmbedder: EmbeddingEngine? = null
            try {
                val loaded = withContext(ioDispatcher) {
                    BundleLoader(platform, loadConfig.bundleAssetPath, signatureVerifier).load()
                }
                newEmbedder = withContext(ioDispatcher) {
                    createEmbedder(loaded.bundle, loadConfig)
                }
                val vectors = usableVectors(loaded.bundle, newEmbedder)
                val newGenerator = withContext(ioDispatcher) {
                    createGenerator(loadConfig)
                }
                mutex.withLock {
                    embedder = newEmbedder
                    generator = newGenerator
                    bundle = loaded.bundle
                    retriever = Retriever(loaded.bundle, vectors, loadConfig.appVersion)
                    newEmbedder = null

                    _state.value = LocalRagState.Ready(
                        chunkCount = loaded.bundle.manifest.chunkCount,
                        contentVersion = loaded.bundle.manifest.contentVersion,
                        usingVectors = vectors != null,
                        canGenerate = newGenerator != null,
                    )
                    drainLocked()
                }
            } catch (cancelled: CancellationException) {
                val partial = newEmbedder
                newEmbedder = null
                mutex.withLock {
                    if (partial != null) runCatching { partial.close() }
                    if (_state.value is LocalRagState.Loading) _state.value = LocalRagState.Idle
                }
                throw cancelled
            } catch (failure: Exception) {
                val partial = newEmbedder
                newEmbedder = null
                mutex.withLock {
                    if (partial != null) runCatching { partial.close() }
                    _state.value = LocalRagState.Failed(failure.message ?: "could not load the bundle")
                }
            }
        } finally {
            initMutex.unlock()
        }
    }

    /**
     * Retrieval without generation.
     *
     * Not a debug convenience: this is how the evaluation harness measures the library and how an
     * app team inspects a bad answer without the generator in the way.
     */
    suspend fun retrieveOnly(text: String): List<Passage> = withContext(ioDispatcher) {
        val snapshot = mutex.withLock {
            drainLocked()
            Snapshot(retriever, embedder, config.topK, config.categories)
        }
        snapshot.retriever?.retrieve(
            query = text,
            topK = snapshot.topK,
            queryVector = snapshot.embedder?.embed(text),
            categories = snapshot.categories,
        ).orEmpty()
    }

    /**
     * The configurable agent turn: the SDK plans tool calls in a strict grammar, executes the
     * host app's [AgentConfig.tools], and gates the final text against the observations. All
     * words — prompts, tools, policy — arrive in [config]; the SDK owns only the machinery.
     * Stateless like everything else here: the transcript lives for this call alone. [history]
     * carries earlier turns in so follow-ups resolve; the SDK retains nothing, and history
     * never counts as grounding evidence. Progress arrives through [onEvent] for hosts that
     * show the turn's working; ignoring it changes nothing about the outcome.
     */
    suspend fun runAgent(
        query: String,
        config: AgentConfig,
        history: List<AgentMessage> = emptyList(),
        onEvent: (AgentEvent) -> Unit = {},
    ): AgentOutcome =
        withContext(ioDispatcher) {
            flight.run {
                val engineSnapshot: GenerationEngine?
                val activeConfig: Config
                mutex.withLock {
                    drainLocked()
                    turnActive = true
                    engineSnapshot = generator
                    activeConfig = this@LocalRag.config
                }
                try {
                    AgentRunner().answer(
                        generate = { prompt ->
                            engineSnapshot?.generate(
                                GenerationRequest(
                                    prompt,
                                    activeConfig.systemInstruction,
                                    activeConfig.maxOutputTokens,
                                ),
                            )
                        },
                        query = query,
                        config = config,
                        history = history,
                        onEvent = onEvent,
                    )
                } finally {
                    withContext(NonCancellable) {
                        mutex.withLock {
                            turnActive = false
                            drainLocked()
                        }
                    }
                }
            }
        }

    /**
     * Drops both models but keeps the bundle, so the library keeps retrieving from BM25.
     * Call from onTrimMemory(TRIM_MEMORY_COMPLETE).
     *
     * Never suspends, so an OS memory callback can invoke it from any thread. When a turn is
     * generating it only records the request; the turn's end performs the unload, flips the
     * state and only then lets the next turn start.
     */
    fun unloadModels() {
        unloadRequested.store(true)
        drainFromCallback()
    }

    fun release() {
        releaseRequested.store(true)
        drainFromCallback()
    }

    /**
     * Replaces the config, unloading everything first. The next [initialize] loads under
     * the new config — this is how the host switches models without rebuilding the engine
     * owner. A turn in flight finishes first; only then does the switch take effect.
     */
    fun updateConfig(config: Config) {
        pendingConfig.store(config)
        releaseRequested.store(true)
        drainFromCallback()
    }

    /**
     * Runs a request recorded by [unloadModels], [release] or [updateConfig] when no turn is
     * generating. Callers hold [mutex] and never suspend inside it; the request flags are atomic
     * so a non-suspending callback thread can record them without the mutex.
     */
    private fun drainLocked() {
        if (turnActive) return
        if (releaseRequested.compareAndSet(true, false)) {
            val next = pendingConfig.exchange(null)
            if (next != null) config = next
            closeEnginesLocked()
            bundle = null
            retriever = null
            stopListeningLocked()
            _state.value = LocalRagState.Idle
            unloadRequested.store(false)
        } else if (unloadRequested.compareAndSet(true, false)) {
            unloadLocked()
        }
    }

    /**
     * Best-effort drain from a thread that cannot suspend. Every [mutex] section is short and
     * suspension-free, so the lock is either free or frees within microseconds; the bounded
     * retries close the lost-wakeup window when a holder is mid-section. Whatever remains
     * pending is picked up by the next turn boundary, publish or public entry, each of which
     * drains under the mutex.
     */
    private fun drainFromCallback() {
        var attempts = 0
        while (!mutex.tryLock()) {
            attempts++
            if (attempts >= MAX_DRAIN_SPINS) return
        }
        try {
            drainLocked()
        } finally {
            mutex.unlock()
        }
    }

    private fun unloadLocked() {
        closeEnginesLocked()
        // Keep retrieving from BM25 once the models are gone.
        bundle?.let { loaded ->
            retriever = Retriever(loaded, null, config.appVersion)
        }
        (_state.value as? LocalRagState.Ready)?.let { ready ->
            _state.value = ready.copy(usingVectors = false, canGenerate = false)
        }
    }

    private fun closeEnginesLocked() {
        embedder?.close()
        embedder = null
        generator?.close()
        generator = null
    }

    private fun stopListeningLocked() {
        val stop = stopListening
        stopListening = null
        if (stop != null) runCatching { stop() }
    }

    private fun createEmbedder(bundle: Bundle, config: Config): EmbeddingEngine? {
        val modelPath = config.embeddingModelPath ?: return null
        val embedding = bundle.manifest.embedding ?: run {
            PlatformLog.log(LogLevel.WARN, TAG, "an embedding model is configured but the bundle carries no vectors")
            return null
        }
        val descriptor = config.embedder ?: run {
            PlatformLog.log(
                LogLevel.WARN,
                TAG,
                "an embedding model is configured but Config.embedder was not supplied, so the " +
                    "bundle vectors cannot be verified; retrieving with BM25 only",
            )
            return null
        }
        return EmbedderLoader.createIfCompatible(
            backend = embeddingBackend,
            modelPath = modelPath,
            cacheDir = config.cacheDir,
            manifest = embedding,
            runtime = descriptor,
        ) { reason -> PlatformLog.log(LogLevel.WARN, TAG, reason) }
    }

    private suspend fun createGenerator(config: Config): GenerationEngine? {
        val modelPath = config.generationModelPath ?: return null
        return GenerationLoader.loadOrNull(
            backend = generationBackend,
            modelPath = modelPath,
            cacheDir = config.cacheDir,
            preferGpu = config.preferGpuBackend,
        ) { reason -> PlatformLog.log(LogLevel.WARN, TAG, reason) }
    }

    /**
     * The parity contract. A bundle built with different prefixes, dimensions or model produces
     * vectors that describe a different space; using them would not error, it would silently
     * return worse results. So a mismatch drops the vectors and says so loudly.
     */
    private fun usableVectors(bundle: Bundle, embedder: EmbeddingEngine?): VectorIndex? {
        val vectors = bundle.vectors ?: return null
        val embedding = bundle.manifest.embedding ?: return null

        // Vectors are only usable once an embedder exists that provably matches them. Without
        // one they are dead weight in the bundle, which is fine: one bundle serves every tier.
        val active = embedder ?: return null
        if (active.dimensions != embedding.dimensions) return null
        return VectorIndex(vectors, embedding.dimensions)
    }

    private data class Snapshot(
        val retriever: Retriever?,
        val embedder: EmbeddingEngine?,
        val topK: Int,
        val categories: Set<String>,
    )

    companion object {
        private const val TAG = "LocalRag"
        private const val MAX_DRAIN_SPINS = 1000

        const val DEFAULT_SYSTEM_INSTRUCTION: String =
            "You are a help assistant inside an app. Answer only using the information given to " +
                "you. Keep answers under three sentences. If the information does not cover the " +
                "question, say you do not have that information."
    }

}

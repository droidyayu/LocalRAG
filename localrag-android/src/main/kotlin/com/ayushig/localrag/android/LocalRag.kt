package com.ayushig.localrag.android

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import com.ayushig.localrag.android.internal.BundleLoader
import com.ayushig.localrag.android.internal.AgentRunner
import com.ayushig.localrag.android.internal.Generator
import com.ayushig.localrag.android.internal.QueryEmbedder
import com.ayushig.localrag.android.internal.SingleFlight
import com.ayushig.localrag.android.internal.Retriever
import com.ayushig.localrag.android.internal.SignatureVerifier
import com.ayushig.localrag.core.bundle.Bundle
import com.ayushig.localrag.core.bundle.EmbedderDescriptor
import com.ayushig.localrag.core.index.VectorIndex
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Answers help questions from documentation shipped inside the host app.
 *
 * No LiteRT type crosses this boundary. The host app supplies model paths and gets back passages
 * and text; whether an embedder or a generator is present changes the quality of the answer, never
 * the shape of the API.
 */
class LocalRag private constructor(
    private val context: Context,
    private val config: Config,
    private val signatureVerifier: SignatureVerifier,
) {

    data class Config(
        val bundleAssetPath: String = "localrag/docs.localrag",
        /** Null means BM25-only retrieval. */
        val embeddingModelPath: String? = null,
        /** Null means no generation: agent turns fall back and only retrieval is available. */
        val generationModelPath: String? = null,
        val cacheDir: File,
        val appVersion: String,
        /**
         * What this app believes its embedder does, compared field by field against the bundle
         * manifest. Required alongside [embeddingModelPath]: without it there is nothing to
         * compare the manifest to, and the parity check cannot do its job.
         */
        val embedder: EmbedderDescriptor? = null,
        val maxOutputTokens: Int = 256,
        val topK: Int = 4,
        val categories: Set<String> = emptySet(),
        val systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION,
    )

    /**
     * Registered by the library rather than left to the host: a model that stays resident under
     * memory pressure gets the whole app killed, and retrieval keeps working without it anyway.
     */
    private val memoryCallback = object : ComponentCallbacks2 {
        override fun onTrimMemory(level: Int) {
            if (level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE) unloadModels()
        }

        override fun onConfigurationChanged(newConfig: Configuration) = Unit

        @Deprecated("Required by ComponentCallbacks2 but never used")
        override fun onLowMemory() = unloadModels()
    }

    private val _state = MutableStateFlow<LocalRagState>(LocalRagState.Idle)
    val state: StateFlow<LocalRagState> = _state.asStateFlow()

    private var bundle: Bundle? = null
    private var retriever: Retriever? = null
    private var embedder: QueryEmbedder? = null
    private var generator: Generator? = null

    /**
     * The only shared mutable state in the SDK: which coroutine currently owns the engine.
     * Everything else hot here is a read-only resource (loaded bundle, models). Conversations
     * are per-call and never retained, so calls are stateless beyond this guard.
     */
    private val flight = SingleFlight()

    suspend fun initialize() {
        if (_state.value is LocalRagState.Ready || _state.value is LocalRagState.Loading) return
        _state.value = LocalRagState.Loading
        context.registerComponentCallbacks(memoryCallback)

        withContext(Dispatchers.IO) {
            runCatching {
                val loaded = BundleLoader(context, config.bundleAssetPath, signatureVerifier).load()
                embedder = createEmbedder(loaded.bundle)
                val vectors = usableVectors(loaded.bundle)
                generator = createGenerator()

                bundle = loaded.bundle
                retriever = Retriever(loaded.bundle, vectors, config.appVersion)

                _state.value = LocalRagState.Ready(
                    chunkCount = loaded.bundle.manifest.chunkCount,
                    contentVersion = loaded.bundle.manifest.contentVersion,
                    usingVectors = vectors != null,
                    canGenerate = generator != null,
                )
            }.onFailure { failure ->
                _state.value = LocalRagState.Failed(failure.message ?: "could not load the bundle")
            }
        }
    }

    /**
     * Retrieval without generation.
     *
     * Not a debug convenience: this is how the evaluation harness measures the library and how an
     * app team inspects a bad answer without the generator in the way.
     */
    suspend fun retrieveOnly(text: String): List<Passage> = withContext(Dispatchers.IO) {
        retriever?.retrieve(
            query = text,
            topK = config.topK,
            queryVector = embedder?.embed(text),
            categories = config.categories,
        ).orEmpty()
    }

    /**
     * The configurable agent turn: the SDK plans tool calls in a strict grammar, executes the
     * host app's [AgentConfig.tools], and gates the final text against the observations. All
     * words — prompts, tools, policy — arrive in [config]; the SDK owns only the machinery.
     * Stateless like everything else here: the transcript lives for this call alone.
     */
    suspend fun runAgent(query: String, config: AgentConfig): AgentOutcome =
        withContext(Dispatchers.IO) {
            flight.run {
                AgentRunner().answer(
                    generate = { prompt -> generator?.generate(prompt) },
                    query = query,
                    config = config,
                )
            }
        }

    /**
     * Drops both models but keeps the bundle, so the library keeps retrieving from BM25.
     * Call from onTrimMemory(TRIM_MEMORY_COMPLETE).
     */
    fun unloadModels() {
        embedder?.close()
        embedder = null
        generator?.close()
        generator = null
        // Keep retrieving from BM25 once the models are gone.
        bundle?.let { loaded ->
            retriever = Retriever(loaded, null, config.appVersion)
        }
        (_state.value as? LocalRagState.Ready)?.let { ready ->
            _state.value = ready.copy(usingVectors = false, canGenerate = false)
        }
    }

    fun release() {
        runCatching { context.unregisterComponentCallbacks(memoryCallback) }
        unloadModels()
        retriever = null
        bundle = null
        _state.value = LocalRagState.Idle
    }

    private fun createEmbedder(bundle: Bundle): QueryEmbedder? {
        val modelPath = config.embeddingModelPath ?: return null
        val embedding = bundle.manifest.embedding ?: run {
            Log.w(TAG, "an embedding model is configured but the bundle carries no vectors")
            return null
        }
        val descriptor = config.embedder ?: run {
            Log.w(
                TAG,
                "an embedding model is configured but Config.embedder was not supplied, so the " +
                    "bundle vectors cannot be verified; retrieving with BM25 only",
            )
            return null
        }
        return QueryEmbedder.createIfCompatible(
            modelPath = modelPath,
            cacheDir = config.cacheDir.path,
            manifest = embedding,
            runtime = descriptor,
        ) { reason -> Log.w(TAG, reason) }
    }

    private fun createGenerator(): Generator? {
        val modelPath = config.generationModelPath ?: return null
        return Generator.createOrNull(
            modelPath = modelPath,
            cacheDir = config.cacheDir.path,
            systemInstruction = config.systemInstruction,
            maxOutputTokens = config.maxOutputTokens,
        ) { reason -> Log.w(TAG, reason) }
    }

    /**
     * The parity contract. A bundle built with different prefixes, dimensions or model produces
     * vectors that describe a different space; using them would not error, it would silently
     * return worse results. So a mismatch drops the vectors and says so loudly.
     */
    private fun usableVectors(bundle: Bundle): VectorIndex? {
        val vectors = bundle.vectors ?: return null
        val embedding = bundle.manifest.embedding ?: return null

        // Vectors are only usable once an embedder exists that provably matches them. Without
        // one they are dead weight in the bundle, which is fine: one bundle serves every tier.
        val active = embedder ?: return null
        if (active.dimensions != embedding.dimensions) return null
        return VectorIndex(vectors, embedding.dimensions)
    }

    companion object {
        private const val TAG = "LocalRag"

        const val DEFAULT_SYSTEM_INSTRUCTION: String =
            "You are a help assistant inside an app. Answer only using the information given to " +
                "you. Keep answers under three sentences. If the information does not cover the " +
                "question, say you do not have that information."

        fun create(
            context: Context,
            config: Config,
            signatureVerifier: SignatureVerifier = SignatureVerifier.RejectAll,
        ): LocalRag = LocalRag(context.applicationContext, config, signatureVerifier)
    }
}

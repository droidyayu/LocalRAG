package com.ayushig.localrag.android

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import com.ayushig.localrag.android.internal.AnswerPipeline
import com.ayushig.localrag.android.internal.BundleLoader
import com.ayushig.localrag.android.internal.Generator
import com.ayushig.localrag.android.internal.PromptBuilder
import com.ayushig.localrag.android.internal.QueryEmbedder
import com.ayushig.localrag.android.internal.Retriever
import com.ayushig.localrag.android.internal.SignatureVerifier
import com.ayushig.localrag.core.answer.ClusterMatcher
import com.ayushig.localrag.core.answer.OutputGate
import com.ayushig.localrag.core.bundle.Bundle
import com.ayushig.localrag.core.bundle.EmbedderDescriptor
import com.ayushig.localrag.core.index.VectorIndex
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
        /** Null means extractive answers: the best passage, verbatim. */
        val generationModelPath: String? = null,
        val cacheDir: File,
        val appVersion: String,
        /**
         * What this app believes its embedder does, compared field by field against the bundle
         * manifest. Required alongside [embeddingModelPath]: without it there is nothing to
         * compare the manifest to, and the parity check cannot do its job.
         */
        val embedder: EmbedderDescriptor? = null,
        val maxContextTokens: Int = 1200,
        val maxOutputTokens: Int = 256,
        val topK: Int = 4,
        val categories: Set<String> = emptySet(),
        val systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION,
    )

    /**
     * Registered by the library rather than left to the host: a model that stays resident under
     * memory pressure gets the whole app killed, and the fallback to extractive answers is
     * invisible to the user anyway.
     */
    private val memoryCallback = object : ComponentCallbacks2 {
        override fun onTrimMemory(level: Int) {
            if (level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE) unloadModels()
        }

        override fun onConfigurationChanged(newConfig: Configuration) = Unit

        @Deprecated("Required by ComponentCallbacks2 but never used")
        override fun onLowMemory() = unloadModels()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<LocalRagState>(LocalRagState.Idle)
    val state: StateFlow<LocalRagState> = _state.asStateFlow()

    private var bundle: Bundle? = null
    private var retriever: Retriever? = null
    private var embedder: QueryEmbedder? = null
    private var pipeline: AnswerPipeline? = null
    private var generator: Generator? = null
    private var clusters: ClusterMatcher? = null

    suspend fun initialize() {
        if (_state.value is LocalRagState.Ready) return
        _state.value = LocalRagState.Loading
        context.registerComponentCallbacks(memoryCallback)

        withContext(Dispatchers.IO) {
            runCatching {
                val loaded = BundleLoader(context, config.bundleAssetPath, signatureVerifier).load()
                embedder = createEmbedder(loaded.bundle)
                val vectors = usableVectors(loaded.bundle)
                generator = createGenerator()

                bundle = loaded.bundle
                val activeRetriever = Retriever(loaded.bundle, vectors, config.appVersion)
                retriever = activeRetriever
                val activeClusters = ClusterMatcher(loaded.bundle.clusters)
                clusters = activeClusters
                pipeline = AnswerPipeline(
                    retriever = activeRetriever,
                    clusters = activeClusters,
                    embedder = embedder,
                    generator = generator,
                    topK = config.topK,
                    categories = config.categories,
                    maxContextTokens = config.maxContextTokens,
                    chunkCount = loaded.bundle.manifest.chunkCount,
                    onRejected = { verdict ->
                        Log.w(TAG, "generated answer rejected: ${verdict.reason} (${verdict.detail})")
                    },
                )

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
        pipeline?.retrieve(text).orEmpty()
    }

    fun query(text: String): Flow<AnswerChunk> {
        val active = pipeline ?: return flow {
            emit(AnswerChunk.Error("LocalRag is not initialized"))
        }
        return active.answer(text).flowOn(Dispatchers.IO)
    }

    /** Clears the conversation without discarding the engine, which would cost seconds to reload. */
    fun resetConversation() {
        generator?.reset()
    }

    /**
     * Drops both models but keeps the bundle, so the library keeps answering extractively.
     * Call from onTrimMemory(TRIM_MEMORY_COMPLETE).
     */
    fun unloadModels() {
        embedder?.close()
        embedder = null
        generator?.close()
        generator = null
        // Keep answering extractively from BM25 once the models are gone.
        bundle?.let { loaded ->
            val plain = Retriever(loaded, null, config.appVersion)
            retriever = plain
            pipeline = AnswerPipeline(
                retriever = plain,
                clusters = clusters ?: ClusterMatcher(loaded.clusters),
                embedder = null,
                generator = null,
                topK = config.topK,
                categories = config.categories,
                maxContextTokens = config.maxContextTokens,
                chunkCount = loaded.manifest.chunkCount,
            )
        }
        (_state.value as? LocalRagState.Ready)?.let { ready ->
            _state.value = ready.copy(usingVectors = false, canGenerate = false)
        }
    }

    fun release() {
        runCatching { context.unregisterComponentCallbacks(memoryCallback) }
        unloadModels()
        pipeline = null
        retriever = null
        clusters = null
        bundle = null
        _state.value = LocalRagState.Idle
        scope.cancel()
    }

    private fun embedQuery(text: String): FloatArray? = embedder?.embed(text)

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

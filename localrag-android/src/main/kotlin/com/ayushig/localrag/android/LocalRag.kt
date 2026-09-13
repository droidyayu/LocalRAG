package com.ayushig.localrag.android

import android.content.Context
import android.util.Log
import com.ayushig.localrag.android.internal.BundleLoader
import com.ayushig.localrag.android.internal.Generator
import com.ayushig.localrag.android.internal.PromptBuilder
import com.ayushig.localrag.android.internal.QueryEmbedder
import com.ayushig.localrag.android.internal.Retriever
import com.ayushig.localrag.android.internal.SignatureVerifier
import com.ayushig.localrag.core.answer.ClusterMatcher
import com.ayushig.localrag.core.answer.OutputGate
import com.ayushig.localrag.core.bundle.Bundle
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
        /** Checked against the bundle manifest; a mismatch drops the vectors. */
        val embeddingModelId: String? = null,
        val maxContextTokens: Int = 1200,
        val maxOutputTokens: Int = 256,
        val topK: Int = 4,
        val categories: Set<String> = emptySet(),
        val systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<LocalRagState>(LocalRagState.Idle)
    val state: StateFlow<LocalRagState> = _state.asStateFlow()

    private var bundle: Bundle? = null
    private var retriever: Retriever? = null
    private var embedder: QueryEmbedder? = null
    private var generator: Generator? = null
    private var clusters: ClusterMatcher? = null

    suspend fun initialize() {
        if (_state.value is LocalRagState.Ready) return
        _state.value = LocalRagState.Loading

        withContext(Dispatchers.IO) {
            runCatching {
                val loaded = BundleLoader(context, config.bundleAssetPath, signatureVerifier).load()
                embedder = createEmbedder(loaded.bundle)
                val vectors = usableVectors(loaded.bundle)
                generator = createGenerator()

                bundle = loaded.bundle
                retriever = Retriever(loaded.bundle, vectors, config.appVersion)
                clusters = ClusterMatcher(loaded.bundle.clusters)

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
        val active = retriever ?: return@withContext emptyList()
        active.retrieve(
            query = text,
            topK = config.topK,
            queryVector = embedQuery(text),
            categories = config.categories,
        )
    }

    fun query(text: String): Flow<AnswerChunk> = flow {
        val active = retriever
        if (active == null) {
            emit(AnswerChunk.Error("LocalRag is not initialized"))
            return@flow
        }

        val retrievalStart = System.currentTimeMillis()
        val passages = active.retrieve(
            query = text,
            topK = config.topK,
            queryVector = embedQuery(text),
            categories = config.categories,
        )
        val retrievalMs = System.currentTimeMillis() - retrievalStart

        // Sources first, always. Retrieval takes milliseconds and generation does not, so the
        // host app can render a source card while text arrives underneath.
        emit(AnswerChunk.Sources(passages))

        // A precomputed answer was written by a human and beats anything generated when it fits.
        // Matched by BM25, so it works on a device with no models at all.
        val cluster = clusters?.match(text)
        if (cluster != null) {
            emit(AnswerChunk.Token(cluster.answer))
            emit(AnswerChunk.Done(metrics(retrievalMs, 0, passages), AnswerMode.PRECOMPUTED))
            return@flow
        }

        if (passages.isEmpty()) {
            emit(AnswerChunk.Done(metrics(retrievalMs, 0, passages), AnswerMode.EXTRACTIVE))
            return@flow
        }

        val activeGenerator = generator
        if (activeGenerator != null) {
            val generationStart = System.currentTimeMillis()
            val generated = activeGenerator.generate(
                PromptBuilder.build(text, passages, config.maxContextTokens),
            )
            val generationMs = System.currentTimeMillis() - generationStart

            // Buffered and checked before a single token is shown. A wrong figure that has
            // already been displayed cannot be withdrawn.
            val verdict = generated?.let {
                OutputGate.check(it, passages.map { passage -> passage.text })
            }
            if (generated != null && verdict is OutputGate.Verdict.Allowed) {
                emit(AnswerChunk.Token(generated))
                emit(
                    AnswerChunk.Done(
                        metrics(retrievalMs, generationMs, passages),
                        AnswerMode.GENERATED,
                    ),
                )
                return@flow
            }
            if (verdict is OutputGate.Verdict.Rejected) {
                Log.w(TAG, "generated answer rejected: ${verdict.reason} (${verdict.detail})")
            }
        }

        // Extractive fallback: the best passage, verbatim. It can be the wrong passage for the
        // question, but it cannot invent a number.
        emit(AnswerChunk.Token(passages.first().text))
        emit(AnswerChunk.Done(metrics(retrievalMs, 0, passages), AnswerMode.EXTRACTIVE))
    }.flowOn(Dispatchers.IO)

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
        bundle?.let { retriever = Retriever(it, null, config.appVersion) }
        (_state.value as? LocalRagState.Ready)?.let { ready ->
            _state.value = ready.copy(usingVectors = false, canGenerate = false)
        }
    }

    private fun metrics(retrievalMs: Long, generationMs: Long, passages: List<Passage>) =
        QueryMetrics(
            retrievalMs = retrievalMs,
            generationMs = generationMs,
            candidateCount = bundle?.manifest?.chunkCount ?: 0,
            passageCount = passages.size,
        )

    fun release() {
        unloadModels()
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
        return QueryEmbedder.createIfCompatible(
            modelPath = modelPath,
            cacheDir = config.cacheDir.path,
            embedding = embedding,
            configuredModelId = config.embeddingModelId,
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

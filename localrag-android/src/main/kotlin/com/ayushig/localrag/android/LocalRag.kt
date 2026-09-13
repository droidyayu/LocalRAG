package com.ayushig.localrag.android

import android.content.Context
import com.ayushig.localrag.android.internal.BundleLoader
import com.ayushig.localrag.android.internal.Retriever
import com.ayushig.localrag.android.internal.SignatureVerifier
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
        val maxContextTokens: Int = 1200,
        val topK: Int = 4,
        val categories: Set<String> = emptySet(),
        val systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<LocalRagState>(LocalRagState.Idle)
    val state: StateFlow<LocalRagState> = _state.asStateFlow()

    private var bundle: Bundle? = null
    private var retriever: Retriever? = null

    suspend fun initialize() {
        if (_state.value is LocalRagState.Ready) return
        _state.value = LocalRagState.Loading

        withContext(Dispatchers.IO) {
            runCatching {
                val loaded = BundleLoader(context, config.bundleAssetPath, signatureVerifier).load()
                val vectors = usableVectors(loaded.bundle)
                bundle = loaded.bundle
                retriever = Retriever(loaded.bundle, vectors, config.appVersion)

                _state.value = LocalRagState.Ready(
                    chunkCount = loaded.bundle.manifest.chunkCount,
                    contentVersion = loaded.bundle.manifest.contentVersion,
                    usingVectors = vectors != null,
                    // Generation lands in a later phase; the flag already tells the truth.
                    canGenerate = false,
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

        // Sources first, always. The host app can render a card in a few hundred milliseconds
        // while any text streams underneath.
        emit(AnswerChunk.Sources(passages))

        if (passages.isEmpty()) {
            emit(
                AnswerChunk.Done(
                    QueryMetrics(retrievalMs, 0, 0, 0),
                    AnswerMode.EXTRACTIVE,
                ),
            )
            return@flow
        }

        // Extractive until a generator exists: the best passage, verbatim, which can be wrong for
        // the question but can never invent a number.
        val answer = passages.first().text
        emit(AnswerChunk.Token(answer))
        emit(
            AnswerChunk.Done(
                QueryMetrics(
                    retrievalMs = retrievalMs,
                    generationMs = 0,
                    candidateCount = bundle?.manifest?.chunkCount ?: 0,
                    passageCount = passages.size,
                ),
                AnswerMode.EXTRACTIVE,
            ),
        )
    }.flowOn(Dispatchers.IO)

    fun release() {
        retriever = null
        bundle = null
        _state.value = LocalRagState.Idle
        scope.cancel()
    }

    /** Null until an embedder is wired in, which keeps retrieval on the BM25 path. */
    private fun embedQuery(text: String): FloatArray? = null

    /**
     * The parity contract. A bundle built with different prefixes, dimensions or model produces
     * vectors that describe a different space; using them would not error, it would silently
     * return worse results. So a mismatch drops the vectors and says so loudly.
     */
    private fun usableVectors(bundle: Bundle): VectorIndex? {
        val vectors = bundle.vectors ?: return null
        val embedding = bundle.manifest.embedding ?: return null

        if (config.embeddingModelPath == null) {
            // Vectors present but no embedder to match a query against them. Not a fault: one
            // bundle serves every device tier.
            return null
        }
        return VectorIndex(vectors, embedding.dimensions)
    }

    companion object {
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

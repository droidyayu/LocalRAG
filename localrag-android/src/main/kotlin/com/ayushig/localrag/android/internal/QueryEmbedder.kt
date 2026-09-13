package com.ayushig.localrag.android.internal

import com.ayushig.localrag.core.bundle.EmbedderDescriptor
import com.ayushig.localrag.core.bundle.EmbeddingInfo
import com.ayushig.localrag.core.bundle.EmbeddingParity
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.EmbeddingEngine
import com.google.ai.edge.litertlm.EmbeddingEngineConfig
import com.google.ai.edge.litertlm.EmbeddingOptions
import com.google.ai.edge.litertlm.InputData
import com.google.ai.edge.litertlm.LiteRtLmJniException

/**
 * Embeds a query with the same engine that embedded the corpus at build time.
 *
 * The only LiteRT type that reaches this file stops here; nothing above it knows an embedder
 * exists beyond a nullable float array.
 */
internal class QueryEmbedder private constructor(
    private val engine: EmbeddingEngine,
    private val options: EmbeddingOptions,
    private val queryPrefix: String,
    override val dimensions: Int,
) : Embedder {

    /** Null on any failure: a query that cannot be embedded falls back to BM25, never to an error. */
    override fun embed(text: String): FloatArray? = try {
        val response = engine.computeEmbedding(
            listOf(InputData.Text(queryPrefix + text)),
            options,
        )
        response.embedding.takeIf { it.size == dimensions }
    } catch (failure: LiteRtLmJniException) {
        null
    } catch (failure: IllegalStateException) {
        null
    }

    override fun close() {
        runCatching { engine.close() }
    }

    companion object {

        /**
         * Builds an embedder only when it provably matches the bundle.
         *
         * The manifest records the model, dimensions, normalization and prefixes the vectors were
         * built with. A mismatch does not error at query time, it silently searches a different
         * space, so a disagreement here drops the vectors instead.
         */
        fun createIfCompatible(
            modelPath: String,
            cacheDir: String,
            manifest: EmbeddingInfo,
            runtime: EmbedderDescriptor,
            onIncompatible: (String) -> Unit,
        ): QueryEmbedder? {
            // Compared field by field against what the host says its embedder does. Reading the
            // manifest into the embedder instead would make agreement automatic and the check
            // worthless.
            val parity = EmbeddingParity.check(manifest, runtime)
            if (parity is EmbeddingParity.Result.Incompatible) {
                onIncompatible(
                    "bundle vectors do not match this embedder, retrieving with BM25 only: " +
                        parity.reasons.joinToString("; "),
                )
                return null
            }

            return try {
                val engine = EmbeddingEngine(
                    EmbeddingEngineConfig(
                        modelPath = modelPath,
                        backend = Backend.CPU(),
                        cacheDir = cacheDir,
                    ),
                )
                engine.initialize()
                QueryEmbedder(
                    engine = engine,
                    options = EmbeddingOptions(
                        normalize = runtime.normalized,
                        outputSize = runtime.dimensions,
                    ),
                    queryPrefix = runtime.queryPrefix,
                    dimensions = runtime.dimensions,
                )
            } catch (failure: LiteRtLmJniException) {
                onIncompatible("embedding engine failed to load: ${failure.message}")
                null
            } catch (failure: IllegalStateException) {
                onIncompatible("embedding engine failed to load: ${failure.message}")
                null
            }
        }
    }
}

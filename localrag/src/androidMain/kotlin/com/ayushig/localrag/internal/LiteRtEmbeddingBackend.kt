package com.ayushig.localrag.internal

import com.ayushig.localrag.EmbeddingBackend
import com.ayushig.localrag.EmbeddingEngine
import com.ayushig.localrag.EmbeddingSpec
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.EmbeddingEngine as LiteRtEmbeddingEngine
import com.google.ai.edge.litertlm.EmbeddingEngineConfig
import com.google.ai.edge.litertlm.EmbeddingOptions
import com.google.ai.edge.litertlm.InputData
import com.google.ai.edge.litertlm.LiteRtLmJniException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Loads the LiteRT-LM embedding engine.
 *
 * Load failures propagate as exceptions; the shared [EmbedderLoader] policy degrades to BM25.
 * The only LiteRT type that reaches this file stops here; nothing above it knows an embedder
 * exists beyond a nullable float array.
 */
internal class LiteRtEmbeddingBackend : EmbeddingBackend {
    override fun load(modelPath: String, cacheDir: String, spec: EmbeddingSpec): EmbeddingEngine {
        val engine = LiteRtEmbeddingEngine(
            EmbeddingEngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU(),
                cacheDir = cacheDir,
            ),
        )
        engine.initialize()
        return LiteRtEmbedding(
            engine = engine,
            options = EmbeddingOptions(
                normalize = spec.normalize,
                outputSize = spec.dimensions,
            ),
            queryPrefix = spec.queryPrefix,
            dimensions = spec.dimensions,
        )
    }

    private class LiteRtEmbedding(
        private val engine: LiteRtEmbeddingEngine,
        private val options: EmbeddingOptions,
        private val queryPrefix: String,
        override val dimensions: Int,
    ) : EmbeddingEngine {

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

        /** Idempotent: unload paths close more than once. */
        override fun close() {
            if (closed.compareAndSet(false, true)) {
                runCatching { engine.close() }
            }
        }

        private val closed = AtomicBoolean(false)
    }
}

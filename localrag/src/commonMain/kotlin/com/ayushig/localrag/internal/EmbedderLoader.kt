package com.ayushig.localrag.internal

import com.ayushig.localrag.EmbeddingBackend
import com.ayushig.localrag.EmbeddingEngine
import com.ayushig.localrag.EmbeddingSpec
import com.ayushig.localrag.core.bundle.EmbedderDescriptor
import com.ayushig.localrag.core.bundle.EmbeddingInfo
import com.ayushig.localrag.core.bundle.EmbeddingParity
import kotlinx.coroutines.CancellationException

/**
 * Builds an embedder only when it provably matches the bundle.
 *
 * The manifest records the model, dimensions, normalization and prefixes the vectors were built
 * with. A mismatch does not error at query time, it silently searches a different space, so a
 * disagreement here drops the vectors instead. Ported from the previous Android-local embedder
 * policy; the backend only loads an embedding model and throws on any load failure.
 */
internal object EmbedderLoader {
    fun createIfCompatible(
        backend: EmbeddingBackend,
        modelPath: String,
        cacheDir: String,
        manifest: EmbeddingInfo,
        runtime: EmbedderDescriptor,
        onIncompatible: (String) -> Unit,
    ): EmbeddingEngine? {
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
            backend.load(modelPath, cacheDir, EmbeddingSpec(runtime.normalized, runtime.dimensions, runtime.queryPrefix))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            onIncompatible("embedding engine failed to load: ${failure.message}")
            null
        }
    }
}

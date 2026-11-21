package com.ayushig.localrag.core.bundle

/**
 * What the runtime believes its embedder does. Compared field by field against the manifest.
 *
 * The host declares this rather than reading it from the bundle. Adopting the manifest would make
 * agreement automatic and the check meaningless: the whole point is to notice when the embedder on
 * the device is not the one that built the vectors.
 */
data class EmbedderDescriptor(
    val modelId: String,
    val dimensions: Int,
    val normalized: Boolean = true,
    val queryPrefix: String,
    val documentPrefix: String,
)

object EmbeddingParity {

    sealed interface Result {
        data object Compatible : Result

        /** Vectors must be ignored and retrieval must fall back to BM25. */
        data class Incompatible(val reasons: List<String>) : Result
    }

    /**
     * A prefix or dimension mismatch does not fail at query time; it silently searches a different
     * space and quietly returns worse answers. Comparing every field is the only thing that
     * catches it, so every field is compared.
     */
    fun check(manifest: EmbeddingInfo, runtime: EmbedderDescriptor): Result {
        val reasons = buildList {
            if (manifest.modelId != runtime.modelId) {
                add("model id: bundle ${manifest.modelId}, runtime ${runtime.modelId}")
            }
            if (manifest.dimensions != runtime.dimensions) {
                add("dimensions: bundle ${manifest.dimensions}, runtime ${runtime.dimensions}")
            }
            if (manifest.normalized != runtime.normalized) {
                add("normalized: bundle ${manifest.normalized}, runtime ${runtime.normalized}")
            }
            if (manifest.queryPrefix != runtime.queryPrefix) {
                add("query prefix: bundle \"${manifest.queryPrefix}\", runtime \"${runtime.queryPrefix}\"")
            }
            if (manifest.documentPrefix != runtime.documentPrefix) {
                add(
                    "document prefix: bundle \"${manifest.documentPrefix}\", " +
                        "runtime \"${runtime.documentPrefix}\"",
                )
            }
        }
        return if (reasons.isEmpty()) Result.Compatible else Result.Incompatible(reasons)
    }
}

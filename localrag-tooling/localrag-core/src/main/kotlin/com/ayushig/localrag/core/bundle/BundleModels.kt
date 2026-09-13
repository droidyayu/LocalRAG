package com.ayushig.localrag.core.bundle

import com.ayushig.localrag.core.document.Chunk
import com.ayushig.localrag.core.index.Bm25Index
import kotlinx.serialization.Serializable

/**
 * The parity contract between the machine that built the index and the device that reads it.
 *
 * On load the runtime compares every field here against its own embedder configuration. A
 * mismatched prefix or dimension count does not error — it silently destroys retrieval quality —
 * so this check is the only thing that catches it, and any mismatch must drop the vectors and fall
 * back to BM25.
 */
@Serializable
data class EmbeddingInfo(
    val modelId: String,
    val dimensions: Int,
    val normalized: Boolean,
    val queryPrefix: String,
    val documentPrefix: String,
)

@Serializable
data class Bm25Params(val k1: Float, val b: Float, val avgDocLength: Float)

@Serializable
data class BundleManifest(
    val bundleFormatVersion: Int = CURRENT_FORMAT_VERSION,
    val contentVersion: Int,
    val builtAt: String,
    val chunkCount: Int,
    val chunkerVersion: Int,
    val bm25: Bm25Params,
    val embedding: EmbeddingInfo? = null,
) {
    companion object {
        const val CURRENT_FORMAT_VERSION: Int = 1
    }
}

/** A chunk as stored in the bundle. Mirrors [Chunk] minus anything derivable. */
@Serializable
data class StoredChunk(
    val chunkId: String,
    val docId: String,
    val title: String,
    val heading: String? = null,
    val category: String,
    val aliases: List<String> = emptyList(),
    val screen: String? = null,
    val appVersionMin: String? = null,
    val appVersionMax: String? = null,
    val text: String,
) {
    companion object {
        fun from(chunk: Chunk) = StoredChunk(
            chunkId = chunk.chunkId,
            docId = chunk.docId,
            title = chunk.title,
            heading = chunk.heading,
            category = chunk.category,
            aliases = chunk.aliases,
            screen = chunk.screen,
            appVersionMin = chunk.appVersionMin,
            appVersionMax = chunk.appVersionMax,
            text = chunk.displayText,
        )
    }
}

/** A question answered ahead of time; matched by BM25 so it works with no models present. */
@Serializable
data class Cluster(
    val id: String,
    val questions: List<String>,
    val answer: String,
    val chunkIds: List<String>,
)

/** A bundle after loading. [vectors] is absent whenever the build produced no embeddings. */
class Bundle(
    val manifest: BundleManifest,
    val chunks: List<StoredChunk>,
    val bm25: Bm25Index,
    val vectors: FloatArray?,
    val clusters: List<Cluster>,
)

object BundleEntries {
    const val MANIFEST = "manifest.json"
    const val CHUNKS = "chunks.json"
    const val BM25 = "bm25.json"
    const val VECTORS = "vectors.bin"
    const val CLUSTERS = "clusters.json"
}

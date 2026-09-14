package com.ayushig.localrag.android.internal

/**
 * The model-backed query capability, behind an interface so retrieval degrades to BM25 when no
 * embedder is configured rather than failing.
 */
internal interface Embedder : AutoCloseable {
    val dimensions: Int

    /** Null when the query cannot be embedded, so retrieval falls back rather than failing. */
    fun embed(text: String): FloatArray?
}

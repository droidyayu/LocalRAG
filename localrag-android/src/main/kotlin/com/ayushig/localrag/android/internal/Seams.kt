package com.ayushig.localrag.android.internal

/**
 * The two model-backed capabilities, behind interfaces.
 *
 * They exist so the four degradation states can be tested on the JVM. Asserting them only through
 * an instrumented test would mean the matrix is checked on a device that happens to have models,
 * which is the configuration least likely to break.
 */
internal interface Embedder : AutoCloseable {
    val dimensions: Int

    /** Null when the query cannot be embedded, so retrieval falls back rather than failing. */
    fun embed(text: String): FloatArray?
}

internal interface TextGenerator : AutoCloseable {
    /** Null on any failure, so the caller falls back to an extractive answer. */
    suspend fun generate(prompt: String): String?

    fun reset()
}

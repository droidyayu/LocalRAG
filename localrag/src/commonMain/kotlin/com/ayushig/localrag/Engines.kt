package com.ayushig.localrag

/**
 * The on-device model capabilities the SDK needs, as plain Kotlin interfaces.
 *
 * No LiteRT type appears here or anywhere in the public API. Android implements these by wrapping
 * LiteRT-LM; iOS implements them in Swift (also LiteRT-LM) and hands them to the SDK at creation.
 * The load policy (GPU first, probe, CPU fallback, one engine per process) lives in common code so
 * both platforms behave the same.
 */

/** One generation call. Each call decodes on a fresh conversation; nothing carries over. */
data class GenerationRequest(
    val prompt: String,
    val systemInstruction: String,
    val maxOutputTokens: Int,
)

enum class ComputeBackend { GPU, CPU }

/** A loaded generation model. Closing is idempotent. */
interface GenerationEngine {
    /** Null on any failure so the caller falls back instead of erroring. */
    suspend fun generate(request: GenerationRequest): String?

    /**
     * Stops the generation in progress as soon as possible, so its [generate] call returns soon.
     * Safe to call from any thread, when nothing is running, and more than once. Engines whose
     * [generate] already reacts to coroutine cancellation (Android) keep the default no-op; an
     * engine implemented in Swift, whose suspend calls cannot observe Kotlin cancellation, must
     * implement it (cancel its current Task).
     */
    fun cancelGeneration() {}

    fun close()
}

/** Loads a generation model onto one compute backend. Throws on any load failure. */
interface GenerationBackend {
    suspend fun load(modelPath: String, cacheDir: String, compute: ComputeBackend): GenerationEngine
}

/** How a query is embedded; must match what the bundle was built with (see EmbeddingParity). */
data class EmbeddingSpec(val normalize: Boolean, val dimensions: Int, val queryPrefix: String)

/** A loaded embedding model. Closing is idempotent. */
interface EmbeddingEngine {
    val dimensions: Int

    /** Null when the query cannot be embedded, so retrieval falls back to BM25. */
    fun embed(text: String): FloatArray?

    fun close()
}

/** Loads an embedding model. Throws on any load failure. */
interface EmbeddingBackend {
    fun load(modelPath: String, cacheDir: String, spec: EmbeddingSpec): EmbeddingEngine
}

package com.ayushig.localrag

import com.ayushig.localrag.internal.IosPlatform
import com.ayushig.localrag.internal.SignatureVerifier

/**
 * Creates the iOS SDK facade. The Swift host supplies the LiteRT-LM backends; a null backend
 * means that capability is unavailable, in which case its loader throws, the load policy degrades
 * gracefully, and retrieval stays BM25-only.
 */
fun LocalRag.Companion.create(
    config: LocalRag.Config,
    generationBackend: GenerationBackend? = null,
    embeddingBackend: EmbeddingBackend? = null,
    signatureVerifier: SignatureVerifier = SignatureVerifier.RejectAll,
): LocalRag = LocalRag(
    platform = IosPlatform(),
    config = config,
    signatureVerifier = signatureVerifier,
    generationBackend = generationBackend ?: UnavailableGenerationBackend,
    embeddingBackend = embeddingBackend ?: UnavailableEmbeddingBackend,
)

private object UnavailableGenerationBackend : GenerationBackend {
    override suspend fun load(
        modelPath: String,
        cacheDir: String,
        compute: ComputeBackend,
    ): GenerationEngine =
        throw UnsupportedOperationException("generation is unavailable: the Swift host supplied no backend")
}

private object UnavailableEmbeddingBackend : EmbeddingBackend {
    override fun load(modelPath: String, cacheDir: String, spec: EmbeddingSpec): EmbeddingEngine =
        throw UnsupportedOperationException("embedding is unavailable: the Swift host supplied no backend")
}

package com.ayushig.localrag

import android.content.Context
import com.ayushig.localrag.internal.AndroidPlatform
import com.ayushig.localrag.internal.LiteRtEmbeddingBackend
import com.ayushig.localrag.internal.LiteRtGenerationBackend
import com.ayushig.localrag.internal.SignatureVerifier

/**
 * Creates the Android SDK facade over LiteRT-LM: APK assets supply the bundled bytes and the
 * LiteRT backends supply generation and embedding.
 */
fun LocalRag.Companion.create(
    context: Context,
    config: LocalRag.Config,
    signatureVerifier: SignatureVerifier = SignatureVerifier.RejectAll,
): LocalRag = LocalRag(
    platform = AndroidPlatform(context.applicationContext),
    config = config,
    signatureVerifier = signatureVerifier,
    generationBackend = LiteRtGenerationBackend(),
    embeddingBackend = LiteRtEmbeddingBackend(),
)

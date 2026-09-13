package com.ayushig.localrag.gradle

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Nested

/** How build-time embeddings are produced, if at all. */
enum class EmbeddingStrategy {
    /** Shell out to a pinned script that loads the embedding model and returns vectors. */
    SIDECAR,

    /** Run the embedder inside the Gradle JVM. Not implemented; see the README. */
    JVM,

    /** No vectors. The bundle is BM25 only, which is a supported runtime state. */
    NONE,
}

abstract class EmbeddingSpec {
    /** When false the bundle carries no vectors and no embedding block. */
    abstract val enabled: Property<Boolean>

    abstract val modelId: Property<String>
    abstract val dimensions: Property<Int>
    abstract val strategy: Property<EmbeddingStrategy>

    /** Command and arguments; the chunk JSONL arrives on stdin and vectors leave on stdout. */
    abstract val sidecarCommand: ListProperty<String>

    /** Written into the manifest so the runtime can refuse a mismatched embedder. */
    abstract val queryPrefix: Property<String>
    abstract val documentPrefix: Property<String>
}

abstract class LocalRagExtension {
    abstract val docsDir: DirectoryProperty

    /** Optional JSON array of precomputed answers, matched before retrieval at runtime. */
    abstract val clustersFile: RegularFileProperty

    /** Subdirectory of assets the bundle is written into. */
    abstract val outputAssetDir: Property<String>

    abstract val categories: ListProperty<String>
    abstract val screenUriPattern: Property<String>
    abstract val staleAfterDays: Property<Int>
    abstract val maxChunkTokens: Property<Int>

    /** Bumped by the host app when content changes; the runtime prefers the higher version. */
    abstract val contentVersion: Property<Int>

    @get:Nested
    abstract val embedding: EmbeddingSpec

    fun embedding(configure: EmbeddingSpec.() -> Unit) {
        embedding.configure()
    }
}

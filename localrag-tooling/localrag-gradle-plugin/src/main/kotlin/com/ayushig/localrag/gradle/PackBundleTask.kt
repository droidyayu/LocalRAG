package com.ayushig.localrag.gradle

import com.ayushig.localrag.core.bundle.BundleWriter
import com.ayushig.localrag.core.bundle.Cluster
import com.ayushig.localrag.core.bundle.EmbeddingInfo
import com.ayushig.localrag.core.document.Chunk
import com.ayushig.localrag.core.index.VectorIndex
import java.io.File
import java.time.Instant
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Zips the per-document pieces into the single bundle the runtime loads.
 *
 * Cheap and non-incremental: it only reads what the earlier tasks already produced. The output is
 * a directory rather than a file because that is what AGP asset generation consumes.
 */
@CacheableTask
abstract class PackBundleTask : DefaultTask() {

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputDirectory
    abstract val chunksDir: DirectoryProperty

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputDirectory
    @get:Optional
    abstract val vectorsDir: DirectoryProperty

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFile
    @get:Optional
    abstract val clustersFile: RegularFileProperty

    @get:OutputDirectory
    abstract val assetDir: DirectoryProperty

    @get:Input
    abstract val assetSubdirectory: Property<String>

    @get:Input
    abstract val contentVersion: Property<Int>

    @get:Input
    abstract val embeddingEnabled: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val modelId: Property<String>

    @get:Input
    @get:Optional
    abstract val dimensions: Property<Int>

    @get:Input
    @get:Optional
    abstract val queryPrefix: Property<String>

    @get:Input
    @get:Optional
    abstract val documentPrefix: Property<String>

    @TaskAction
    fun run() {
        val chunkFiles = chunksDir.get().asFile.listFiles().orEmpty()
            .filter { it.isFile }
            .sortedBy { it.name }

        val chunks = mutableListOf<Chunk>()
        val vectorValues = mutableListOf<Float>()
        val embedding = embeddingInfo()

        for (chunkFile in chunkFiles) {
            val documentChunks = JSON.decodeFromString<List<Chunk>>(chunkFile.readText())
            chunks += documentChunks
            if (embedding == null) continue

            val vectorFile = File(vectorsDir.get().asFile, chunkFile.name)
            if (!vectorFile.isFile) {
                throw GradleException("LocalRAG: no vectors for ${chunkFile.name}")
            }
            val documentVectors = JSON.decodeFromString<List<List<Float>>>(vectorFile.readText())
            if (documentVectors.size != documentChunks.size) {
                throw GradleException(
                    "LocalRAG: ${chunkFile.name} has ${documentChunks.size} chunks but " +
                        "${documentVectors.size} vectors",
                )
            }
            // Normalized at build time so the runtime cosine is a plain dot product.
            documentVectors.forEach { vectorValues += VectorIndex.normalize(it.toFloatArray()).toList() }
        }

        val target = File(assetDir.get().asFile, assetSubdirectory.get()).also { it.mkdirs() }
        val bundle = File(target, BUNDLE_NAME)

        bundle.outputStream().use { output ->
            BundleWriter().write(
                output = output,
                chunks = chunks,
                vectors = if (embedding == null) null else vectorValues.toFloatArray(),
                clusters = clusters(),
                contentVersion = contentVersion.get(),
                builtAt = Instant.EPOCH.toString(),
                embedding = embedding,
            )
        }

        logger.lifecycle(
            "LocalRAG: wrote ${bundle.name}, ${chunks.size} chunks, " +
                (if (embedding == null) "BM25 only" else "with ${embedding.dimensions}d vectors") +
                ", ${clusters().size} precomputed answers" +
                ", ${bundle.length()} bytes",
        )
    }

    /** Answers written ahead of time. Absent is normal; the runtime simply never matches one. */
    private fun clusters(): List<Cluster> {
        val file = clustersFile.orNull?.asFile ?: return emptyList()
        if (!file.isFile) return emptyList()
        return JSON.decodeFromString<List<Cluster>>(file.readText())
    }

    private fun embeddingInfo(): EmbeddingInfo? {
        if (!embeddingEnabled.get()) return null
        return EmbeddingInfo(
            modelId = modelId.get(),
            dimensions = dimensions.get(),
            normalized = true,
            queryPrefix = queryPrefix.get(),
            documentPrefix = documentPrefix.get(),
        )
    }

    private companion object {
        const val BUNDLE_NAME = "docs.localrag"
        val JSON = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    }
}

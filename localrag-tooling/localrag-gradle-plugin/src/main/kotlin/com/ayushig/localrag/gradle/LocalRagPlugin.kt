package com.ayushig.localrag.gradle

import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider

/**
 * Builds a retrieval bundle from the host app documentation and packs it into assets.
 *
 * Three tasks rather than one, because a single task producing a single bundle cannot be
 * incremental: there is no partial output to reuse. Parsing is cheap and runs per document,
 * embedding is expensive and skips unchanged documents, and packing just zips what exists.
 */
class LocalRagPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val extension = target.extensions.create("localRag", LocalRagExtension::class.java)
        applyConventions(target, extension)

        val buildDir = target.layout.buildDirectory
        val parse = target.tasks.register("parseLocalRagDocs", ParseDocsTask::class.java) { task ->
            task.group = TASK_GROUP
            task.description = "Parses and chunks the LocalRAG documentation"
            task.docsDir.set(extension.docsDir)
            task.chunksDir.set(buildDir.dir("localrag/chunks"))
            task.categories.set(extension.categories)
            task.screenUriPattern.set(extension.screenUriPattern)
            task.staleAfterDays.set(extension.staleAfterDays)
            task.maxChunkTokens.set(extension.maxChunkTokens)
        }

        val embed = target.tasks.register("embedLocalRagChunks", EmbedChunksTask::class.java) { task ->
            task.group = TASK_GROUP
            task.description = "Embeds changed LocalRAG chunks through the configured sidecar"
            task.chunksDir.set(parse.flatMap { it.chunksDir })
            task.vectorsDir.set(buildDir.dir("localrag/vectors"))
            task.executable.set(extension.embedding.sidecarExecutable)
            task.script.set(extension.embedding.sidecarScript)
            task.arguments.set(extension.embedding.sidecarArguments)
            task.modelId.set(extension.embedding.modelId)
            task.dimensions.set(extension.embedding.dimensions)
            task.documentPrefix.set(extension.embedding.documentPrefix)
            task.onlyIf { embeddingIsOn(extension) }
        }

        val pack = target.tasks.register("generateLocalRagBundle", PackBundleTask::class.java) { task ->
            task.group = TASK_GROUP
            task.description = "Packs the LocalRAG bundle into the app assets"
            task.chunksDir.set(parse.flatMap { it.chunksDir })
            // Left unset when embedding is off: the embed task never runs, so its output
            // directory never exists and an unset optional input is the honest description.
            if (embeddingIsOn(extension)) {
                task.vectorsDir.set(embed.flatMap { it.vectorsDir })
                task.dependsOn(embed)
            }
            task.clustersFile.set(extension.clustersFile)
            task.assetDir.set(buildDir.dir("localrag/assets"))
            task.assetSubdirectory.set(extension.outputAssetDir)
            task.contentVersion.set(extension.contentVersion)
            task.embeddingEnabled.set(target.provider { embeddingIsOn(extension) })
            task.modelId.set(extension.embedding.modelId)
            task.dimensions.set(extension.embedding.dimensions)
            task.queryPrefix.set(extension.embedding.queryPrefix)
            task.documentPrefix.set(extension.embedding.documentPrefix)
        }

        wireIntoAssets(target, pack)
    }

    /**
     * Registers the bundle as generated assets through the AGP Variant API rather than hanging off
     * mergeAssets by name: it wires the task dependency and the source set for us, and stays
     * configuration-cache safe.
     */
    private fun wireIntoAssets(target: Project, pack: TaskProvider<PackBundleTask>) {
        listOf("com.android.application", "com.android.library").forEach { pluginId ->
            target.plugins.withId(pluginId) {
                val components = target.extensions.getByType(AndroidComponentsExtension::class.java)
                components.onVariants { variant ->
                    variant.sources.assets?.addGeneratedSourceDirectory(pack) { it.assetDir }
                }
            }
        }
    }

    private fun embeddingIsOn(extension: LocalRagExtension): Boolean {
        if (!extension.embedding.enabled.get()) return false
        return when (extension.embedding.strategy.get()) {
            EmbeddingStrategy.NONE -> false
            EmbeddingStrategy.SIDECAR -> true
            // Behaving like SIDECAR here would embed with a different toolchain than the one the
            // build asked for, which is exactly the mismatch the manifest parity block exists to
            // catch. Better to stop.
            EmbeddingStrategy.JVM -> throw org.gradle.api.GradleException(
                "localRag.embedding.strategy = JVM is not implemented. Use SIDECAR, or NONE to " +
                    "build a BM25-only bundle.",
            )
        }
    }

    private fun applyConventions(target: Project, extension: LocalRagExtension) {
        extension.docsDir.convention(target.layout.projectDirectory.dir("src/main/docs"))
        extension.outputAssetDir.convention("localrag")
        extension.staleAfterDays.convention(180)
        extension.maxChunkTokens.convention(400)
        extension.contentVersion.convention(1)
        extension.categories.convention(emptyList())

        // BM25 only unless the host app opts in: a bundle with no vectors is a supported state,
        // not a broken build.
        extension.embedding.enabled.convention(false)
        extension.embedding.strategy.convention(EmbeddingStrategy.SIDECAR)
        extension.embedding.modelId.convention("embeddinggemma-300m-seq256")
        extension.embedding.dimensions.convention(256)
        extension.embedding.sidecarExecutable.convention("python3")
        extension.embedding.sidecarArguments.convention(emptyList())
        extension.embedding.queryPrefix.convention("task: search result | query: ")
        extension.embedding.documentPrefix.convention("title: none | text: ")
    }

    private companion object {
        const val TASK_GROUP = "localrag"
    }
}

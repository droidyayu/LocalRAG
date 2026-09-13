package com.ayushig.localrag.gradle

import com.ayushig.localrag.core.document.Chunk
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.ChangeType
import org.gradle.work.Incremental
import org.gradle.work.InputChanges

/**
 * Turns each document chunk file into a matching vector file by shelling out to the sidecar.
 *
 * Incremental on purpose. Embedding is by far the slowest step, and re-embedding an unchanged
 * corpus on every build is how a plugin gets switched off. Documents whose chunk file did not
 * change keep the vectors already on disk.
 *
 * The sidecar protocol is deliberately dull: one JSON object per line on stdin carrying the text,
 * one JSON array of floats per line on stdout, in the same order.
 */
@CacheableTask
abstract class EmbedChunksTask : DefaultTask() {

    @get:Incremental
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputDirectory
    abstract val chunksDir: DirectoryProperty

    @get:OutputDirectory
    abstract val vectorsDir: DirectoryProperty

    @get:Input
    abstract val command: ListProperty<String>

    @get:Input
    abstract val modelId: Property<String>

    @get:Input
    abstract val dimensions: Property<Int>

    @get:Input
    abstract val documentPrefix: Property<String>

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun run(changes: InputChanges) {
        val outputDir = vectorsDir.get().asFile
        outputDir.mkdirs()

        val toEmbed = mutableListOf<File>()
        if (changes.isIncremental) {
            for (change in changes.getFileChanges(chunksDir)) {
                if (change.file.isDirectory) continue
                val vectorFile = File(outputDir, change.file.name)
                when (change.changeType) {
                    ChangeType.REMOVED -> vectorFile.delete()
                    else -> toEmbed += change.file
                }
            }
        } else {
            outputDir.listFiles()?.forEach { it.delete() }
            toEmbed += chunksDir.get().asFile.listFiles().orEmpty().filter { it.isFile }
        }

        if (toEmbed.isEmpty()) {
            logger.lifecycle("LocalRAG: no documents changed, reusing every vector on disk")
            return
        }

        // One sidecar invocation for every changed document, not one per document. A real
        // embedder loads a model measured in gigabytes; paying that startup cost per file would
        // make the plugin unusable on a corpus of any size.
        val ordered = toEmbed.sortedBy { it.name }
        val perFile = ordered.map { file ->
            file to JSON.decodeFromString<List<Chunk>>(file.readText())
        }
        val texts = perFile.flatMap { (_, chunks) ->
            chunks.map { documentPrefix.get() + it.embeddedText }
        }
        logger.lifecycle(
            "LocalRAG: embedding ${texts.size} chunks from ${ordered.size} changed document(s) " +
                "with ${modelId.get()}",
        )

        val vectors = if (texts.isEmpty()) emptyList() else embed(texts)

        var cursor = 0
        for ((file, chunks) in perFile) {
            val slice = vectors.subList(cursor, cursor + chunks.size)
            cursor += chunks.size
            File(outputDir, file.name).writeText(JSON.encodeToString(slice))
        }
    }

    private fun embed(texts: List<String>): List<List<Float>> {
        // Built by hand rather than from a @Serializable class: this module has no
        // serialization compiler plugin and one JSON object does not justify adding one.
        val stdin = texts.joinToString("\n") { text ->
            JSON.encodeToString(JsonObject.serializer(), JsonObject(mapOf("text" to JsonPrimitive(text))))
        }
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()

        val result = execOperations.exec { spec ->
            spec.commandLine(command.get())
            spec.standardInput = stdin.byteInputStream()
            spec.standardOutput = stdout
            spec.errorOutput = stderr
            spec.isIgnoreExitValue = true
        }
        if (result.exitValue != 0) {
            throw GradleException(
                "LocalRAG embedding sidecar failed with exit ${result.exitValue}:\n" +
                    stderr.toString(Charsets.UTF_8),
            )
        }

        val vectors = stdout.toString(Charsets.UTF_8)
            .lineSequence()
            .filter { it.isNotBlank() }
            .map { JSON.decodeFromString<List<Float>>(it) }
            .toList()

        if (vectors.size != texts.size) {
            throw GradleException(
                "LocalRAG embedding sidecar returned ${vectors.size} vectors for ${texts.size} chunks",
            )
        }
        val expected = dimensions.get()
        vectors.forEachIndexed { index, vector ->
            if (vector.size != expected) {
                throw GradleException(
                    "LocalRAG embedding sidecar returned ${vector.size} dimensions for chunk " +
                        "$index, but the configuration declares $expected",
                )
            }
        }
        return vectors
    }

    private companion object {
        val JSON = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    }
}

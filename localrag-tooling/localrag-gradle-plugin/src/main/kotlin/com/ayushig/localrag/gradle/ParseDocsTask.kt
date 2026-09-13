package com.ayushig.localrag.gradle

import com.ayushig.localrag.core.document.Chunk
import com.ayushig.localrag.core.document.Chunker
import com.ayushig.localrag.core.document.ChunkerConfig
import com.ayushig.localrag.core.document.DocumentIssue
import com.ayushig.localrag.core.document.DocumentStatus
import com.ayushig.localrag.core.document.DocumentValidator
import com.ayushig.localrag.core.document.FrontMatterParser
import com.ayushig.localrag.core.document.ParsedDocument
import com.ayushig.localrag.core.document.ValidationConfig
import java.io.File
import java.time.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.ChangeType
import org.gradle.work.Incremental
import org.gradle.work.InputChanges

/**
 * Parses and chunks each Markdown document into its own JSON file.
 *
 * One output file per document rather than one combined file, so a later embedding step can skip
 * documents that did not change. Validation still runs across the whole corpus, because rules like
 * duplicate ids are only visible globally.
 */
@CacheableTask
abstract class ParseDocsTask : DefaultTask() {

    @get:Incremental
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputDirectory
    abstract val docsDir: DirectoryProperty

    @get:OutputDirectory
    abstract val chunksDir: DirectoryProperty

    @get:Input
    abstract val categories: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val screenUriPattern: Property<String>

    @get:Input
    abstract val staleAfterDays: Property<Int>

    @get:Input
    abstract val maxChunkTokens: Property<Int>

    @TaskAction
    fun run(changes: InputChanges) {
        val outputDir = chunksDir.get().asFile
        outputDir.mkdirs()

        // Removed sources must take their chunk file with them, or the bundle keeps stale content.
        if (changes.isIncremental) {
            changes.getFileChanges(docsDir)
                .filter { it.changeType == ChangeType.REMOVED }
                .forEach { File(outputDir, chunkFileName(it.file)).delete() }
        } else {
            outputDir.listFiles()?.forEach { it.delete() }
        }

        val sources = docsDir.get().asFile.walkTopDown()
            .filter { it.isFile && it.extension == "md" }
            .sortedBy { it.name }
            .toList()

        val issues = mutableListOf<DocumentIssue>()
        val documents = mutableListOf<ParsedDocument>()
        for (source in sources) {
            val relative = source.relativeTo(docsDir.get().asFile).path
            val result = FrontMatterParser.parse(relative, source.readText())
            issues += result.issues
            result.document?.let(documents::add)
        }

        issues += DocumentValidator(
            ValidationConfig(
                categories = categories.get(),
                screenUriPattern = screenUriPattern.orNull,
                staleAfterDays = staleAfterDays.get(),
            ),
        ).validate(documents, LocalDate.now())

        val chunker = Chunker(ChunkerConfig(maxChunkTokens = maxChunkTokens.get()))
        // Only published documents reach the bundle; drafts stay invisible to users.
        val published = documents.filter { it.status == DocumentStatus.PUBLISHED }

        val expected = mutableSetOf<String>()
        for (document in published) {
            val result = chunker.chunk(document)
            issues += result.issues
            val target = File(outputDir, chunkFileName(File(docsDir.get().asFile, document.sourcePath)))
            target.writeText(JSON.encodeToString(result.chunks))
            expected += target.name
        }

        // A document flipped to draft or deleted upstream must not leave its chunks behind.
        // The non-incremental path wiped the directory above; this covers the incremental one.
        outputDir.listFiles().orEmpty()
            .filter { it.isFile && it.extension == "json" && it.name !in expected }
            .forEach { it.delete() }

        report(issues)
        logger.lifecycle(
            "LocalRAG: ${published.size} published documents, " +
                "${documents.size - published.size} skipped, " +
                "${chunkCount(outputDir)} chunks",
        )
    }

    private fun chunkCount(outputDir: File): Int =
        outputDir.listFiles().orEmpty().sumOf { file ->
            JSON.decodeFromString<List<Chunk>>(file.readText()).size
        }

    private fun report(issues: List<DocumentIssue>) {
        issues.filter { it.severity == DocumentIssue.Severity.WARNING }
            .forEach { logger.warn("LocalRAG: $it") }

        val errors = issues.filter { it.severity == DocumentIssue.Severity.ERROR }
        if (errors.isNotEmpty()) {
            throw GradleException(
                "LocalRAG found ${errors.size} problem(s) in the documentation:\n" +
                    errors.joinToString("\n") { "  $it" },
            )
        }
    }

    private fun chunkFileName(source: File): String = source.name.removeSuffix(".md") + ".json"

    private companion object {
        val JSON = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    }
}

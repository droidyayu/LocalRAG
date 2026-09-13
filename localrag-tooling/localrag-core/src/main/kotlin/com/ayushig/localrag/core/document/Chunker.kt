package com.ayushig.localrag.core.document

import com.ayushig.localrag.core.text.Markdown
import com.ayushig.localrag.core.text.Tokenizer

data class ChunkerConfig(
    val maxChunkTokens: Int = 400,
)

/**
 * Splits a document on level-two headings.
 *
 * Authors control the boundaries, which is far more reliable than a token-window splitter: a human
 * puts the break where the topic changes. Deeper headings stay inside their parent chunk, and
 * anything before the first `##` becomes a chunk with a null heading.
 *
 * An oversized chunk is never split silently — the author is told to fix it, because an automatic
 * split would put the fix out of their sight and out of their control.
 */
class Chunker(private val config: ChunkerConfig = ChunkerConfig()) {

    fun chunk(document: ParsedDocument): ChunkResult {
        val issues = mutableListOf<DocumentIssue>()
        val sections = splitSections(document.body)

        val chunks = sections.mapNotNull { section ->
            val displayText = section.body.trim()
            if (displayText.isEmpty()) return@mapNotNull null

            val context = listOfNotNull(document.title, section.heading).joinToString(" — ")
            val embeddedText = context + "\n\n" + Markdown.strip(displayText)
            val tokenCount = Tokenizer.tokenize(embeddedText).size

            when {
                tokenCount > config.maxChunkTokens * 2 -> issues += DocumentIssue(
                    document.sourcePath,
                    section.line,
                    "section ${describe(section.heading)} is $tokenCount tokens, more than twice " +
                        "the ${config.maxChunkTokens} token limit; split it under its own heading",
                    DocumentIssue.Severity.ERROR,
                )

                tokenCount > config.maxChunkTokens -> issues += DocumentIssue(
                    document.sourcePath,
                    section.line,
                    "section ${describe(section.heading)} is $tokenCount tokens, over the " +
                        "${config.maxChunkTokens} token limit; consider splitting it",
                    DocumentIssue.Severity.WARNING,
                )
            }

            Chunk(
                chunkId = document.id + "#" + slugify(section.heading),
                docId = document.id,
                title = document.title,
                heading = section.heading,
                category = document.category,
                aliases = document.aliases,
                screen = document.screen,
                appVersionMin = document.appVersionMin,
                appVersionMax = document.appVersionMax,
                embeddedText = embeddedText,
                displayText = displayText,
                tokenCount = tokenCount,
            )
        }

        return ChunkResult(chunks, issues)
    }

    private fun splitSections(body: String): List<Section> {
        val sections = mutableListOf<Section>()
        var heading: String? = null
        var startLine = 1
        val current = StringBuilder()

        body.lines().forEachIndexed { index, line ->
            val match = HEADING_TWO.matchEntire(line)
            if (match != null) {
                sections += Section(heading, current.toString(), startLine)
                current.setLength(0)
                heading = match.groupValues[1].trim()
                startLine = index + 1
            } else {
                current.appendLine(line)
            }
        }
        sections += Section(heading, current.toString(), startLine)
        return sections
    }

    private fun describe(heading: String?): String =
        if (heading == null) "before the first heading" else "\"" + heading + "\""

    /** Stable across rebuilds: the same heading always yields the same chunk id. */
    private fun slugify(heading: String?): String {
        if (heading == null) return "intro"
        val slug = heading.lowercase()
            .map { if (it.isLetterOrDigit()) it else Char(45) }
            .joinToString("")
            .trim(Char(45))
            .replace(Regex("-+"), "-")
        return slug.ifEmpty { "section" }
    }

    private data class Section(val heading: String?, val body: String, val line: Int)

    data class ChunkResult(val chunks: List<Chunk>, val issues: List<DocumentIssue>)

    companion object {
        /** Written into the bundle manifest so a runtime can detect an index built by older rules. */
        const val VERSION: Int = 3

        private val HEADING_TWO = Regex("^##\\s+(.+?)\\s*$")
    }
}

package com.ayushig.localrag.core

import com.ayushig.localrag.core.document.Chunk
import com.ayushig.localrag.core.document.Chunker
import com.ayushig.localrag.core.document.DocumentStatus
import com.ayushig.localrag.core.document.FrontMatterParser
import com.ayushig.localrag.core.document.ParsedDocument
import java.io.File

/**
 * The frozen 20-document corpus.
 *
 * It is a copy rather than a reference to the demo app docs on purpose: the golden-bundle test
 * must fail when the chunker or tokenizer changes, not when a content author edits a sentence.
 */
object Fixture {

    private val corpusDir: File by lazy {
        val url = requireNotNull(Fixture::class.java.classLoader.getResource("corpus")) {
            "test corpus is missing from resources"
        }
        File(url.toURI())
    }

    val documents: List<ParsedDocument> by lazy {
        corpusDir.listFiles()!!
            .filter { it.extension == "md" }
            .sortedBy { it.name }
            .map { file ->
                val result = FrontMatterParser.parse("corpus/" + file.name, file.readText())
                check(result.issues.isEmpty()) { "fixture is not clean: ${result.issues}" }
                requireNotNull(result.document)
            }
    }

    val published: List<ParsedDocument> by lazy {
        documents.filter { it.status == DocumentStatus.PUBLISHED }
    }

    val chunks: List<Chunk> by lazy {
        val chunker = Chunker()
        published.flatMap { chunker.chunk(it).chunks }
    }
}

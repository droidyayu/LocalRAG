package com.ayushig.localrag.core.document

/**
 * One retrievable unit: a document section.
 *
 * [embeddedText] is what gets indexed and embedded and always carries its context, because a
 * chunk reading only "Tap Confirm" is useless to a search engine. [displayText] is the body alone,
 * which is what a reader should see.
 */
data class Chunk(
    val chunkId: String,
    val docId: String,
    val title: String,
    val heading: String?,
    val category: String,
    val aliases: List<String>,
    val screen: String?,
    val appVersionMin: String?,
    val appVersionMax: String?,
    val embeddedText: String,
    val displayText: String,
    val tokenCount: Int,
)

package com.ayushig.localrag.core.document

/** Publication state. Only [PUBLISHED] documents enter a bundle. */
enum class DocumentStatus {
    DRAFT,
    PUBLISHED,
    DEPRECATED,
    ;

    companion object {
        fun from(raw: String): DocumentStatus? =
            entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
    }
}

/**
 * One documentation file after its front matter has been parsed.
 *
 * [body] is the Markdown below the front matter block, unmodified.
 */
data class ParsedDocument(
    val id: String,
    val title: String,
    val category: String,
    val aliases: List<String> = emptyList(),
    val screen: String? = null,
    val appVersionMin: String? = null,
    val appVersionMax: String? = null,
    val lastReviewed: String,
    val status: DocumentStatus,
    val body: String,
    val sourcePath: String,
)

/**
 * A validation problem in a source document.
 *
 * Content authors are not engineers, so every message names the file and the line it came from.
 */
data class DocumentIssue(
    val sourcePath: String,
    val line: Int,
    val message: String,
    val severity: Severity,
) {
    enum class Severity { WARNING, ERROR }

    override fun toString(): String = "$sourcePath:$line: ${severity.name.lowercase()}: $message"
}

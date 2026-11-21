package com.ayushig.localrag.core.document

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class ValidationConfig(
    val categories: List<String> = emptyList(),
    val screenUriPattern: String? = null,
    val staleAfterDays: Int = 180,
)

/**
 * Rules that fail a build rather than shipping a broken corpus.
 *
 * Every message names the file and the line, because the people who hit these are content authors
 * rather than engineers and "validation failed" tells them nothing they can act on.
 */
class DocumentValidator(private val config: ValidationConfig) {

    fun validate(documents: List<ParsedDocument>, today: LocalDate): List<DocumentIssue> {
        val issues = mutableListOf<DocumentIssue>()
        val screenPattern = config.screenUriPattern?.let(::Regex)

        val byId = documents.groupBy { it.id }
        for ((id, duplicates) in byId) {
            if (duplicates.size <= 1) continue
            val others = duplicates.map { it.sourcePath }.sorted()
            for (document in duplicates) {
                issues += error(
                    document,
                    "duplicate id `$id`; also declared in " +
                        others.filter { it != document.sourcePath }.joinToString(", ") +
                        ". Ids are stable analytics keys, so rename the newer document",
                )
            }
        }

        for (document in documents) {
            if (config.categories.isNotEmpty() && document.category !in config.categories) {
                issues += error(
                    document,
                    "unknown category `${document.category}`; configured categories are " +
                        config.categories.joinToString(", "),
                )
            }

            val screen = document.screen
            if (screen != null && screenPattern != null && !screenPattern.matches(screen)) {
                issues += error(
                    document,
                    "screen `$screen` does not match the configured pattern " +
                        "`${config.screenUriPattern}`",
                )
            }

            val reviewed = runCatching { LocalDate.parse(document.lastReviewed) }.getOrNull()
            if (reviewed == null) {
                issues += error(document, "last_reviewed `${document.lastReviewed}` is not a date")
            } else {
                val age = ChronoUnit.DAYS.between(reviewed, today)
                if (age > config.staleAfterDays) {
                    issues += error(
                        document,
                        "last reviewed $age days ago, over the ${config.staleAfterDays} day limit; " +
                            "re-read it and update last_reviewed",
                    )
                }
            }
        }

        return issues
    }

    /** The front matter block starts at line 1, so pointing there points at the offending field. */
    private fun error(document: ParsedDocument, message: String) =
        DocumentIssue(document.sourcePath, 1, message, DocumentIssue.Severity.ERROR)
}

package com.ayushig.localrag.core.document

/**
 * Reads the YAML front matter block at the top of a Markdown file.
 *
 * This is a deliberately small subset of YAML — scalars, quoted scalars, inline `[a, b]` lists and
 * `null` — because the schema is fixed and a full YAML dependency would be the largest thing in a
 * library whose whole point is being small. Anything outside the subset is reported as an error
 * against its line rather than silently misread.
 */
object FrontMatterParser {

    private const val DELIMITER = "---"

    fun parse(sourcePath: String, content: String): ParseResult {
        val lines = content.lines()
        val issues = mutableListOf<DocumentIssue>()

        val firstDelimiter = lines.indexOfFirst { it.isNotBlank() }
        if (firstDelimiter == -1 || lines[firstDelimiter].trim() != DELIMITER) {
            return ParseResult(
                null,
                listOf(error(sourcePath, 1, "missing front matter: the file must start with ---")),
            )
        }

        val closing = (firstDelimiter + 1 until lines.size)
            .firstOrNull { lines[it].trim() == DELIMITER }
        if (closing == null) {
            return ParseResult(
                null,
                listOf(error(sourcePath, firstDelimiter + 1, "front matter is never closed by ---")),
            )
        }

        val fields = mutableMapOf<String, Pair<String, Int>>()
        for (index in (firstDelimiter + 1) until closing) {
            val raw = lines[index]
            val lineNumber = index + 1
            if (raw.isBlank() || raw.trimStart().startsWith("#")) continue

            val separator = raw.indexOf(":")
            if (separator <= 0) {
                issues += error(sourcePath, lineNumber, "expected `key: value`, found `${raw.trim()}`")
                continue
            }
            val key = raw.substring(0, separator).trim()
            val value = raw.substring(separator + 1).trim()
            if (fields.containsKey(key)) {
                issues += error(sourcePath, lineNumber, "duplicate field `$key`")
                continue
            }
            fields[key] = value to lineNumber
        }

        val body = lines.drop(closing + 1).joinToString("\n").trim()
        val document = buildDocument(sourcePath, fields, issues, body)
        return ParseResult(document, issues)
    }

    private fun buildDocument(
        sourcePath: String,
        fields: Map<String, Pair<String, Int>>,
        issues: MutableList<DocumentIssue>,
        body: String,
    ): ParsedDocument? {
        fun required(key: String): String? {
            val entry = fields[key]
            if (entry == null || scalar(entry.first) == null) {
                issues += error(sourcePath, entry?.second ?: 1, "missing required field `$key`")
                return null
            }
            return scalar(entry.first)
        }

        val id = required("id")
        val title = required("title")
        val category = required("category")
        val lastReviewed = required("last_reviewed")
        val statusRaw = required("status")

        val status = statusRaw?.let { raw ->
            DocumentStatus.from(raw) ?: run {
                issues += error(
                    sourcePath,
                    fields.getValue("status").second,
                    "unknown status `$raw`; expected draft, published or deprecated",
                )
                null
            }
        }

        if (id != null && !ID_PATTERN.matches(id)) {
            issues += error(
                sourcePath,
                fields.getValue("id").second,
                "id `$id` must be kebab-case: lowercase letters, digits and hyphens",
            )
        }

        if (lastReviewed != null && !DATE_PATTERN.matches(lastReviewed)) {
            issues += error(
                sourcePath,
                fields.getValue("last_reviewed").second,
                "last_reviewed `$lastReviewed` must be an ISO date, for example 2026-09-01",
            )
        }

        if (id == null || title == null || category == null || lastReviewed == null || status == null) {
            return null
        }

        return ParsedDocument(
            id = id,
            title = title,
            category = category,
            aliases = fields["aliases"]?.let { inlineList(it.first) } ?: emptyList(),
            screen = fields["screen"]?.let { scalar(it.first) },
            appVersionMin = fields["app_version_min"]?.let { scalar(it.first) },
            appVersionMax = fields["app_version_max"]?.let { scalar(it.first) },
            lastReviewed = lastReviewed,
            status = status,
            body = body,
            sourcePath = sourcePath,
        )
    }

    /** Unquotes a scalar and maps an explicit `null` or an empty value to absent. */
    private fun scalar(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed == "null" || trimmed == "~") return null
        return trimmed.removeSurrounding("\"").removeSurrounding("'")
    }

    private fun inlineList(raw: String): List<String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed == "null" || trimmed == "[]") return emptyList()
        return trimmed.removeSurrounding("[", "]")
            .split(",")
            .mapNotNull { scalar(it) }
    }

    private fun error(sourcePath: String, line: Int, message: String) =
        DocumentIssue(sourcePath, line, message, DocumentIssue.Severity.ERROR)

    private val ID_PATTERN = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")
    private val DATE_PATTERN = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    data class ParseResult(val document: ParsedDocument?, val issues: List<DocumentIssue>)
}

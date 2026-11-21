package com.ayushig.localrag.core.text

/**
 * Strips Markdown formatting so the indexed text carries words rather than syntax.
 *
 * Only what the corpus actually uses: headings, emphasis, inline code, fenced code, links, list
 * markers and blockquotes. The displayed text keeps its formatting; this output is for the index
 * and the embedder.
 */
object Markdown {

    fun strip(markdown: String): String {
        val withoutFences = FENCED_CODE.replace(markdown) { match ->
            // Keep the code itself: help content puts real values inside fences.
            match.groupValues[1]
        }
        return withoutFences
            .lineSequence()
            .map { line ->
                line
                    .replace(HEADING, "")
                    .replace(BLOCKQUOTE, "")
                    .replace(LIST_MARKER, "")
                    .replace(IMAGE, "")
                    .replace(LINK, "$1")
                    .replace(ASTERISK_EMPHASIS, "$1")
                    .replace(UNDERSCORE_EMPHASIS, "$1")
                    .replace(INLINE_CODE, "$1")
                    .trim()
            }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .trim()
    }

    private val FENCED_CODE = Regex("```[a-zA-Z0-9]*\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)
    private val HEADING = Regex("^#{1,6}\\s+")
    private val BLOCKQUOTE = Regex("^>\\s?")
    private val LIST_MARKER = Regex("^\\s*([-*+]|\\d+\\.)\\s+")
    private val IMAGE = Regex("!\\[[^\\]]*\\]\\([^)]*\\)")
    private val LINK = Regex("\\[([^\\]]*)\\]\\([^)]*\\)")
    private val ASTERISK_EMPHASIS = Regex("\\*{1,3}([^*]+)\\*{1,3}")
    private val UNDERSCORE_EMPHASIS = Regex("_{1,3}([^_]+)_{1,3}")
    private val INLINE_CODE = Regex("`([^`]*)`")
}

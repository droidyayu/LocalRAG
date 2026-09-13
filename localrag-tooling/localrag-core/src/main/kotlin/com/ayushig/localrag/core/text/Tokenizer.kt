package com.ayushig.localrag.core.text

/**
 * The parity linchpin: the build and the runtime both tokenize through this object.
 *
 * If the two ever diverge, an index built on CI stops matching queries typed on a phone and
 * nothing errors — retrieval quality just quietly degrades. Keep it dependency-free and boring.
 *
 * Lowercase, drop punctuation except & and %, split on whitespace, remove a small stopword list.
 * No stemming in v1.
 */
object Tokenizer {

    fun tokenize(text: String): List<String> = buildList {
        val token = StringBuilder()
        for (character in text) {
            if (character.isLetterOrDigit() || character == AMPERSAND || character == PERCENT) {
                token.append(character.lowercaseChar())
            } else {
                emit(token, this)
            }
        }
        emit(token, this)
    }

    private fun emit(token: StringBuilder, into: MutableList<String>) {
        if (token.isEmpty()) return
        val word = token.toString()
        token.setLength(0)
        if (word !in STOPWORDS) into.add(word)
    }

    private const val AMPERSAND = '&'
    private const val PERCENT = '%'

    /**
     * Deliberately short. An aggressive list removes words that carry real meaning in help
     * content, and BM25 already discounts common terms through inverse document frequency.
     */
    val STOPWORDS: Set<String> = setOf(
        "a", "an", "and", "are", "as", "at", "be", "but", "by", "for", "if", "in", "into", "is",
        "it", "of", "on", "or", "such", "that", "the", "their", "then", "there", "these", "they",
        "this", "to", "was", "will", "with",
    )
}

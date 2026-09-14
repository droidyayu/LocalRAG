package com.ayushig.localrag.core.answer

import com.ayushig.localrag.core.text.Tokenizer

/**
 * Decides whether generated text is allowed to reach the user.
 *
 * The generation is buffered and checked before a single token is emitted, because a wrong number
 * that has already been shown cannot be taken back. Anything that fails is discarded silently and
 * the caller falls back to quoting a passage: a passage that does not answer the question is a
 * poor answer, while an invented fee is a harmful one.
 */
object OutputGate {

    sealed interface Verdict {
        data object Allowed : Verdict

        data class Rejected(val reason: Reason, val detail: String) : Verdict
    }

    enum class Reason {
        UNGROUNDED_DIGIT,
        LOW_OVERLAP,
        ADVISORY_LANGUAGE,
        TRUNCATED,
        TOO_LONG,
        EMPTY,
    }

    /**
     * [minimumOverlap] is the share of the answer content words that must appear in the supplied
     * passages. Below it, the model has wandered off source.
     */
    data class Config(
        val minimumOverlap: Float = 0.5f,
        val maxCharacters: Int = 1200,
    )

    fun check(
        answer: String,
        passages: List<String>,
        config: Config = Config(),
    ): Verdict {
        val trimmed = answer.trim()
        if (trimmed.isEmpty()) return Verdict.Rejected(Reason.EMPTY, "no text")
        if (trimmed.length > config.maxCharacters) {
            return Verdict.Rejected(Reason.TOO_LONG, "${trimmed.length} characters")
        }

        // A help corpus is full of fees, rates and limits. A small model restating one incorrectly
        // is the worst thing this library can do, so every digit must be traceable to a passage.
        val sourceNumbers = passages.flatMapTo(mutableSetOf()) { NUMBER.findAll(it).map { m -> m.value.figureToken() } }
        val invented = NUMBER.findAll(trimmed)
            .map { it.value.figureToken() }
            .filter { it !in sourceNumbers }
            .toList()
        if (invented.isNotEmpty()) {
            return Verdict.Rejected(
                Reason.UNGROUNDED_DIGIT,
                "not present in the passages: " + invented.distinct().joinToString(", "),
            )
        }

        for (phrase in ADVISORY) {
            if (trimmed.contains(phrase, ignoreCase = true)) {
                return Verdict.Rejected(Reason.ADVISORY_LANGUAGE, phrase)
            }
        }

        if (!trimmed.endsWithSentence()) {
            return Verdict.Rejected(Reason.TRUNCATED, "does not end a sentence")
        }

        val answerTerms = Tokenizer.tokenize(trimmed)
        if (answerTerms.isEmpty()) return Verdict.Rejected(Reason.EMPTY, "no content words")
        val sourceTerms = passages.flatMapTo(mutableSetOf()) { Tokenizer.tokenize(it) }
        val overlap = answerTerms.count { it in sourceTerms }.toFloat() / answerTerms.size
        if (overlap < config.minimumOverlap) {
            return Verdict.Rejected(
                Reason.LOW_OVERLAP,
                "%.0f%% of terms appear in the passages".format(overlap * 100),
            )
        }

        return Verdict.Allowed
    }

    private fun String.endsWithSentence(): Boolean = last() in SENTENCE_ENDINGS

    /** Digits with their separators, so 1,200 and 4.2 are single tokens rather than fragments. */
    private val NUMBER = Regex("\\d[\\d,.]*")

    /**
     * The number pattern also swallows a sentence-final period, so a figure ending a sentence
     * ("worth $12,340.00.") tokenizes with the period attached. That period is punctuation,
     * not part of the figure: without trimming it, every correctly restated sentence-final
     * figure reads as invented. Applied on both sides so the comparison stays symmetric.
     */
    private fun String.figureToken(): String = trimEnd('.')

    private val SENTENCE_ENDINGS = setOf(Char(46), Char(33), Char(63))

    /**
     * Forward-looking or advisory phrasing. The library answers what the documentation says; it
     * does not tell anyone what to do with their money.
     */
    val ADVISORY: List<String> = listOf(
        "should you", "you should", "we recommend", "i recommend", "our advice",
        "will rise", "will fall", "will go up", "will go down", "good time to",
        "best time to", "worth buying", "worth selling", "i suggest", "we suggest",
        "you ought to", "is likely to increase", "is likely to decrease", "guaranteed",
    )
}

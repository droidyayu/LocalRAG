package com.ayushig.localrag.domain.model.assistant

sealed interface AnswerResult {
    /**
     * A templated answer. Every figure in [text] came from the repository, not the model, and
     * [text] already ends with the snapshot time.
     */
    data class Answer(val text: String) : AnswerResult

    /** An advice question, declined. */
    data class Refusal(val text: String) : AnswerResult

    /** Nothing in the account matched; the caller should fall back to the model. */
    data object NoMatch : AnswerResult

    /** A repository read failed. Never a partial answer. */
    data class Failure(val text: String) : AnswerResult
}

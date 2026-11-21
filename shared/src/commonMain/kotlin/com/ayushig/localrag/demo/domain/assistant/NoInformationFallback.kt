package com.ayushig.localrag.demo.domain.assistant

/**
 * The reply when a question is neither about the account nor covered by the documentation.
 *
 * Fixed text on purpose: the one thing this answer must never do is reflect the question back
 * at the user, which is both an echo bug and a way to leak their own words into a context they
 * did not expect. Nothing is interpolated, so there is nothing to get wrong per query.
 */
object NoInformationFallback {

    const val TEXT: String =
        "I don't have information on that yet. I can answer questions about your portfolio " +
            "and the help documentation — try rephrasing, or check the Retrieval tab to see " +
            "what the docs cover."

    /**
     * Safety policy, not knowledge: advisory language in a model answer is replaced with this
     * sentence rather than repaired. One shared refusal beats two invented ones.
     */
    const val ADVICE_REFUSAL: String =
        "I can show you what's in your account, but I can't advise on buying or selling."
}

package com.ayushig.localrag.domain.usecase.assistant

import com.ayushig.localrag.domain.assistant.IntentResolver
import com.ayushig.localrag.domain.assistant.PortfolioAnswerRenderer
import com.ayushig.localrag.domain.model.assistant.AnswerResult
import javax.inject.Inject

/**
 * Answers a chat query from account data, or reports [AnswerResult.NoMatch] so the caller can
 * fall back to the model. No model is involved in this path.
 */
class AnswerPortfolioQueryUseCase @Inject constructor(
    private val intentResolver: IntentResolver,
    private val renderer: PortfolioAnswerRenderer,
) {
    suspend operator fun invoke(query: String): AnswerResult =
        renderer.render(intentResolver.resolve(query))
}

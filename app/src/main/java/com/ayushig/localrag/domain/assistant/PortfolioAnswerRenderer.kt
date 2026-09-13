package com.ayushig.localrag.domain.assistant

import com.ayushig.localrag.domain.model.assistant.AnswerResult
import com.ayushig.localrag.domain.model.assistant.PortfolioIntent

interface PortfolioAnswerRenderer {
    suspend fun render(intent: PortfolioIntent): AnswerResult
}

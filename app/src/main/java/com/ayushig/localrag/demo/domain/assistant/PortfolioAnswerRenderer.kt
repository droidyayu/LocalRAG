package com.ayushig.localrag.demo.domain.assistant

import com.ayushig.localrag.demo.domain.model.assistant.AnswerResult
import com.ayushig.localrag.demo.domain.model.assistant.PortfolioIntent

interface PortfolioAnswerRenderer {
    suspend fun render(intent: PortfolioIntent): AnswerResult
}

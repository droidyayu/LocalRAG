package com.ayushig.localrag.domain.assistant

import com.ayushig.localrag.domain.model.assistant.PortfolioIntent

fun interface IntentResolver {
    fun resolve(query: String): PortfolioIntent
}

package com.ayushig.localrag.demo.domain.assistant

import com.ayushig.localrag.demo.domain.model.assistant.PortfolioIntent

fun interface IntentResolver {
    fun resolve(query: String): PortfolioIntent
}

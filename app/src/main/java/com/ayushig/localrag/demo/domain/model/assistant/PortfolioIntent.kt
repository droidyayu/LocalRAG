package com.ayushig.localrag.demo.domain.model.assistant

import com.ayushig.localrag.demo.domain.model.portfolio.AssetCategory

/** What the user is asking about their account. Resolved by rules, never by the model. */
sealed interface PortfolioIntent {
    data object TotalValue : PortfolioIntent

    data object TotalPnl : PortfolioIntent

    data object AvailableBalance : PortfolioIntent

    data object MarginStatus : PortfolioIntent

    data class CategoryValue(val category: AssetCategory) : PortfolioIntent

    data class CategoryPnl(val category: AssetCategory) : PortfolioIntent

    /** Carries the raw query; the renderer resolves it to a holding, or reports no match. */
    data class HoldingDetail(val query: String) : PortfolioIntent

    data object BestPerformer : PortfolioIntent

    data object WorstPerformer : PortfolioIntent

    data object ListHoldings : PortfolioIntent

    /** Anything asking whether to buy, sell or hold. Always refused. */
    data object AdviceRequest : PortfolioIntent

    data object Unknown : PortfolioIntent
}

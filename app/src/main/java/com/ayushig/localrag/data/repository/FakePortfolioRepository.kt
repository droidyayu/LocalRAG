package com.ayushig.localrag.data.repository

import com.ayushig.localrag.data.portfolio.DummyPortfolioData
import com.ayushig.localrag.data.portfolio.PortfolioCalculations
import com.ayushig.localrag.domain.model.portfolio.AssetCategory
import com.ayushig.localrag.domain.model.portfolio.CategorySummary
import com.ayushig.localrag.domain.model.portfolio.HoldingSearchResult
import com.ayushig.localrag.domain.model.portfolio.LeveragedPosition
import com.ayushig.localrag.domain.model.portfolio.MarginStatus
import com.ayushig.localrag.domain.model.portfolio.MetalHolding
import com.ayushig.localrag.domain.model.portfolio.PortfolioSummary
import com.ayushig.localrag.domain.model.portfolio.StockHolding
import com.ayushig.localrag.domain.model.portfolio.WealthInvestment
import com.ayushig.localrag.domain.repository.PortfolioRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

/**
 * Hardcoded portfolio, no network and no storage.
 *
 * Deterministic by design: no randomness anywhere, so the assistant answering the same question
 * twice gets the same numbers twice. The delay exists only to give the UI a real loading state.
 */
@Singleton
class FakePortfolioRepository @Inject constructor() : PortfolioRepository {

    /**
     * The holdings never change, so the rollups are derived once and reused. Determinism is a
     * requirement, not an optimisation: the assistant must give the same answer twice.
     */
    private val categorySummaries: List<CategorySummary> by lazy {
        AssetCategory.entries.map(::summariseCategory)
    }

    private val totalHoldingsValue: Double by lazy { categorySummaries.sumOf { it.currentValue } }

    private val totalMarginUsed: Double by lazy {
        DummyPortfolioData.leveragedPositions.sumOf { it.marginUsed }
    }

    override suspend fun getPortfolioSummary(): PortfolioSummary {
        simulateLatency()
        val categories = categorySummaries
        val totalValue = categories.sumOf { it.currentValue }
        val totalPnl = categories.sumOf { it.totalPnl }
        val totalCost = totalValue - totalPnl
        return PortfolioSummary(
            totalValue = totalValue,
            totalPnl = totalPnl,
            totalPnlPct = PortfolioCalculations.pnlPct(totalPnl, totalCost),
            availableBalance = DummyPortfolioData.AVAILABLE_BALANCE,
            currency = DummyPortfolioData.CURRENCY,
            categories = categories,
            asOf = DummyPortfolioData.AS_OF,
        )
    }

    override suspend fun getCategorySummary(category: AssetCategory): CategorySummary {
        simulateLatency()
        return categorySummaries.first { it.category == category }
    }

    override suspend fun getLeveragedPositions(): List<LeveragedPosition> {
        simulateLatency()
        return DummyPortfolioData.leveragedPositions
    }

    override suspend fun getMetalHoldings(): List<MetalHolding> {
        simulateLatency()
        return DummyPortfolioData.metalHoldings
    }

    override suspend fun getWealthInvestments(): List<WealthInvestment> {
        simulateLatency()
        return DummyPortfolioData.wealthInvestments
    }

    override suspend fun getStockHoldings(): List<StockHolding> {
        simulateLatency()
        return DummyPortfolioData.stockHoldings
    }

    override suspend fun getMarginStatus(): MarginStatus {
        simulateLatency()
        return PortfolioCalculations.marginStatus(
            availableBalance = DummyPortfolioData.AVAILABLE_BALANCE,
            totalHoldingsValue = totalHoldingsValue,
            marginUsed = totalMarginUsed,
        )
    }

    override suspend fun findHoldingBySymbol(query: String): HoldingSearchResult? {
        simulateLatency()
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return null

        DummyPortfolioData.leveragedPositions
            .firstOrNull { it.symbol.matches(needle) || it.displayName.matches(needle) }
            ?.let { return HoldingSearchResult.Leveraged(it) }

        DummyPortfolioData.stockHoldings
            .firstOrNull { it.symbol.matches(needle) || it.companyName.matches(needle) }
            ?.let { return HoldingSearchResult.Stock(it) }

        DummyPortfolioData.metalHoldings
            .firstOrNull { it.metal.displayName.matches(needle) }
            ?.let { return HoldingSearchResult.MetalHoldingMatch(it) }

        DummyPortfolioData.wealthInvestments
            .firstOrNull { it.name.matches(needle) }
            ?.let { return HoldingSearchResult.Wealth(it) }

        return null
    }

    /** Loose enough that "apple" finds "Apple Inc.", strict enough that "a" does not. */
    private fun String.matches(needle: String): Boolean = lowercase().contains(needle)

    private fun summariseCategory(category: AssetCategory): CategorySummary = when (category) {
        AssetCategory.LEVERAGED -> PortfolioCalculations.summarise(
            category = category,
            holdings = DummyPortfolioData.leveragedPositions,
            value = PortfolioCalculations::positionValue,
            cost = PortfolioCalculations::positionCost,
        )
        AssetCategory.METALS -> PortfolioCalculations.summarise(
            category = category,
            holdings = DummyPortfolioData.metalHoldings,
            value = PortfolioCalculations::metalValue,
            cost = PortfolioCalculations::metalCost,
        )
        AssetCategory.WEALTH -> PortfolioCalculations.summarise(
            category = category,
            holdings = DummyPortfolioData.wealthInvestments,
            value = PortfolioCalculations::wealthValue,
            cost = PortfolioCalculations::wealthCost,
        )
        AssetCategory.STOCKS -> PortfolioCalculations.summarise(
            category = category,
            holdings = DummyPortfolioData.stockHoldings,
            value = PortfolioCalculations::stockValue,
            cost = PortfolioCalculations::stockCost,
        )
    }

    private suspend fun simulateLatency() = delay(SIMULATED_LATENCY_MS)

    private companion object {
        const val SIMULATED_LATENCY_MS = 300L
    }
}

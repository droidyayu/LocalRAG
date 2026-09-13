package com.ayushig.localrag.assistant

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

/** Fails every read, to prove a broken repository produces no partial answer. */
class ThrowingPortfolioRepository : PortfolioRepository {

    override suspend fun getPortfolioSummary(): PortfolioSummary = fail()

    override suspend fun getCategorySummary(category: AssetCategory): CategorySummary = fail()

    override suspend fun getLeveragedPositions(): List<LeveragedPosition> = fail()

    override suspend fun getMetalHoldings(): List<MetalHolding> = fail()

    override suspend fun getWealthInvestments(): List<WealthInvestment> = fail()

    override suspend fun getStockHoldings(): List<StockHolding> = fail()

    override suspend fun getMarginStatus(): MarginStatus = fail()

    override suspend fun findHoldingBySymbol(query: String): HoldingSearchResult? = fail()

    private fun fail(): Nothing = throw IllegalStateException("portfolio unavailable")
}

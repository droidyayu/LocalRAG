package com.ayushig.localrag.domain.repository

import com.ayushig.localrag.domain.model.portfolio.AssetCategory
import com.ayushig.localrag.domain.model.portfolio.CategorySummary
import com.ayushig.localrag.domain.model.portfolio.HoldingSearchResult
import com.ayushig.localrag.domain.model.portfolio.LeveragedPosition
import com.ayushig.localrag.domain.model.portfolio.MarginStatus
import com.ayushig.localrag.domain.model.portfolio.MetalHolding
import com.ayushig.localrag.domain.model.portfolio.PortfolioSummary
import com.ayushig.localrag.domain.model.portfolio.StockHolding
import com.ayushig.localrag.domain.model.portfolio.WealthInvestment

/**
 * The portfolio read API.
 *
 * Function names are deliberately phrased the way a user would ask, because the on-device
 * assistant will expose these as tools. Every implementation must be deterministic: the same call
 * returns the same values, so an answer given twice is the same answer twice.
 */
interface PortfolioRepository {
    suspend fun getPortfolioSummary(): PortfolioSummary

    suspend fun getCategorySummary(category: AssetCategory): CategorySummary

    suspend fun getLeveragedPositions(): List<LeveragedPosition>

    suspend fun getMetalHoldings(): List<MetalHolding>

    suspend fun getWealthInvestments(): List<WealthInvestment>

    suspend fun getStockHoldings(): List<StockHolding>

    suspend fun getMarginStatus(): MarginStatus

    /** Case-insensitive match against symbol and display or company name. */
    suspend fun findHoldingBySymbol(query: String): HoldingSearchResult?
}

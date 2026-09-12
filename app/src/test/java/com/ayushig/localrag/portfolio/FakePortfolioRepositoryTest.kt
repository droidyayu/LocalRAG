package com.ayushig.localrag.portfolio

import com.ayushig.localrag.data.repository.FakePortfolioRepository
import com.ayushig.localrag.domain.model.portfolio.AssetCategory
import com.ayushig.localrag.domain.model.portfolio.HoldingSearchResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** runTest skips the repository's 300 ms delays via virtual time. */
class FakePortfolioRepositoryTest {

    private val repository = FakePortfolioRepository()
    private val delta = 0.01

    @Test
    fun `portfolio summary totals match the sum of its categories`() = runTest {
        val summary = repository.getPortfolioSummary()
        assertEquals(summary.categories.sumOf { it.currentValue }, summary.totalValue, delta)
        assertEquals(summary.categories.sumOf { it.totalPnl }, summary.totalPnl, delta)
    }

    @Test
    fun `portfolio summary matches the hand computed totals`() = runTest {
        val summary = repository.getPortfolioSummary()
        // 21,563.17 leveraged + 22,660.00 metals + 57,430.00 wealth + 67,710.75 stocks
        assertEquals(169_363.92, summary.totalValue, delta)
        // 52.00 + 1,580.00 + 2,430.00 + 4,631.25
        assertEquals(8_693.25, summary.totalPnl, delta)
        // 8,693.25 on a cost of 160,670.67
        assertEquals(5.4106, summary.totalPnlPct, 0.0001)
        assertEquals(12_480.35, summary.availableBalance, delta)
        assertEquals("USD", summary.currency)
        assertEquals(4, summary.categories.size)
    }

    @Test
    fun `every call returns the same values`() = runTest {
        assertEquals(repository.getPortfolioSummary(), repository.getPortfolioSummary())
        assertEquals(repository.getMarginStatus(), repository.getMarginStatus())
    }

    @Test
    fun `as of timestamp is fixed`() = runTest {
        assertEquals(repository.getPortfolioSummary().asOf, repository.getPortfolioSummary().asOf)
    }

    @Test
    fun `holding counts match the underlying lists`() = runTest {
        val summary = repository.getPortfolioSummary()
        fun countOf(category: AssetCategory) =
            summary.categories.first { it.category == category }.holdingCount

        assertEquals(repository.getLeveragedPositions().size, countOf(AssetCategory.LEVERAGED))
        assertEquals(repository.getMetalHoldings().size, countOf(AssetCategory.METALS))
        assertEquals(repository.getWealthInvestments().size, countOf(AssetCategory.WEALTH))
        assertEquals(repository.getStockHoldings().size, countOf(AssetCategory.STOCKS))
    }

    @Test
    fun `category summary agrees with the portfolio summary`() = runTest {
        val fromPortfolio = repository.getPortfolioSummary().categories
        AssetCategory.entries.forEach { category ->
            assertEquals(
                fromPortfolio.first { it.category == category },
                repository.getCategorySummary(category),
            )
        }
    }

    @Test
    fun `margin status reflects the open leveraged positions`() = runTest {
        val status = repository.getMarginStatus()
        assertEquals(repository.getLeveragedPositions().sumOf { it.marginUsed }, status.marginUsed, delta)
        assertEquals(status.equity - status.marginUsed, status.freeMargin, delta)
        assertTrue(status.marginLevelPct > 0.0)
    }

    @Test
    fun `search finds a stock by symbol regardless of case`() = runTest {
        val result = repository.findHoldingBySymbol("aapl")
        assertTrue(result is HoldingSearchResult.Stock)
        assertEquals("AAPL", (result as HoldingSearchResult.Stock).holding.symbol)
    }

    @Test
    fun `search finds a stock by company name`() = runTest {
        val result = repository.findHoldingBySymbol("Apple")
        assertEquals("AAPL", (result as HoldingSearchResult.Stock).holding.symbol)
    }

    @Test
    fun `search finds a metal by name`() = runTest {
        val result = repository.findHoldingBySymbol("silver")
        assertTrue(result is HoldingSearchResult.MetalHoldingMatch)
    }

    @Test
    fun `search returns null for something not held`() = runTest {
        assertNull(repository.findHoldingBySymbol("ZZZZ"))
        assertNull(repository.findHoldingBySymbol("   "))
    }
}

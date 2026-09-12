package com.ayushig.localrag.portfolio

import com.ayushig.localrag.data.portfolio.DummyPortfolioData
import com.ayushig.localrag.data.portfolio.PortfolioCalculations
import com.ayushig.localrag.domain.model.portfolio.Direction
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The checkpoint tests. Expected values are worked out by hand from the raw holdings in
 * [DummyPortfolioData] and written down here as literals on purpose: if a calculation drifts, the
 * literal disagrees and the test fails, which is the whole point.
 */
class PortfolioCalculationsTest {

    private val delta = 0.01

    @Test
    fun `margin is notional over leverage`() {
        // 100,000 units at 1.0850 = 108,500 notional, at 1:30 = 3,616.67 collateral.
        assertEquals(3_616.67, PortfolioCalculations.marginFor(100_000.0, 1.0850, 30), delta)
    }

    @Test
    fun `a buy profits when price rises`() {
        assertEquals(
            750.00,
            PortfolioCalculations.unrealisedPnlFor(Direction.BUY, 100_000.0, 1.0850, 1.0925),
            delta,
        )
    }

    @Test
    fun `a sell loses when price rises`() {
        assertEquals(
            -990.00,
            PortfolioCalculations.unrealisedPnlFor(Direction.SELL, 60_000.0, 1.2740, 1.2905),
            delta,
        )
    }

    @Test
    fun `leveraged positions reconcile`() {
        val positions = DummyPortfolioData.leveragedPositions
        // 3,616.67 + 3,822.00 + 10,480.00 + 3,592.50
        assertEquals(21_511.17, positions.sumOf { it.marginUsed }, delta)
        // +750.00 - 990.00 + 1,570.00 - 1,278.00
        assertEquals(52.00, positions.sumOf { it.unrealisedPnl }, delta)
    }

    @Test
    fun `metal holdings reconcile`() {
        val metals = DummyPortfolioData.metalHoldings
        // gold 250 x 75.60 = 18,900 ; silver 4,000 x 0.94 = 3,760
        assertEquals(22_660.00, metals.sumOf(PortfolioCalculations::metalValue), delta)
        // gold 250 x 71.20 = 17,800 ; silver 4,000 x 0.82 = 3,280
        assertEquals(21_080.00, metals.sumOf(PortfolioCalculations::metalCost), delta)
    }

    @Test
    fun `stock holdings reconcile`() {
        val stocks = DummyPortfolioData.stockHoldings
        // 25,626 + 17,528 + 6,258 + 12,770 + 5,528.75
        assertEquals(67_710.75, stocks.sumOf(PortfolioCalculations::stockValue), delta)
        // 21,408 + 16,084 + 7,125 + 12,240 + 6,222.50
        assertEquals(63_079.50, stocks.sumOf(PortfolioCalculations::stockCost), delta)
    }

    @Test
    fun `wealth investments reconcile`() {
        val wealth = DummyPortfolioData.wealthInvestments
        assertEquals(57_430.00, wealth.sumOf(PortfolioCalculations::wealthValue), delta)
        assertEquals(55_000.00, wealth.sumOf(PortfolioCalculations::wealthCost), delta)
    }

    @Test
    fun `pnl percent is return on cost`() {
        assertEquals(7.3419, PortfolioCalculations.pnlPct(4_631.25, 63_079.50), 0.0001)
    }

    @Test
    fun `pnl percent of a zero cost position is zero not infinity`() {
        assertEquals(0.0, PortfolioCalculations.pnlPct(100.0, 0.0), delta)
    }

    @Test
    fun `margin status derives equity free margin and level`() {
        val status = PortfolioCalculations.marginStatus(
            availableBalance = 12_480.35,
            totalHoldingsValue = 169_363.92,
            marginUsed = 21_511.17,
        )
        assertEquals(181_844.27, status.equity, delta)
        assertEquals(160_333.10, status.freeMargin, delta)
        assertEquals(845.35, status.marginLevelPct, delta)
    }

    @Test
    fun `margin level of an unlevered account is zero not infinity`() {
        val status = PortfolioCalculations.marginStatus(1_000.0, 5_000.0, 0.0)
        assertEquals(0.0, status.marginLevelPct, delta)
    }
}

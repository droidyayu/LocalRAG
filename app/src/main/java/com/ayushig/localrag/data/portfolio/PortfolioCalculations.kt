package com.ayushig.localrag.data.portfolio

import com.ayushig.localrag.domain.model.portfolio.AssetCategory
import com.ayushig.localrag.domain.model.portfolio.CategorySummary
import com.ayushig.localrag.domain.model.portfolio.Direction
import com.ayushig.localrag.domain.model.portfolio.LeveragedPosition
import com.ayushig.localrag.domain.model.portfolio.MarginStatus
import com.ayushig.localrag.domain.model.portfolio.MetalHolding
import com.ayushig.localrag.domain.model.portfolio.StockHolding
import com.ayushig.localrag.domain.model.portfolio.WealthInvestment

/**
 * Every derived number in the portfolio comes from here.
 *
 * Nothing downstream stores a total, a profit or a percentage of its own: one wrong hardcoded
 * total would become a wrong assistant answer, so totals are recomputed from the raw holdings on
 * every read.
 */
object PortfolioCalculations {

    // --- Leveraged -------------------------------------------------------------------------

    /** Collateral tied up by a position: notional divided by leverage. */
    fun marginFor(units: Double, entryPrice: Double, leverage: Int): Double =
        units * entryPrice / leverage

    /** Open profit or loss. A SELL profits when the price falls. */
    fun unrealisedPnlFor(
        direction: Direction,
        units: Double,
        entryPrice: Double,
        currentPrice: Double,
    ): Double = when (direction) {
        Direction.BUY -> (currentPrice - entryPrice) * units
        Direction.SELL -> (entryPrice - currentPrice) * units
    }

    /** What the position is worth to the account today: collateral plus open profit or loss. */
    fun positionValue(position: LeveragedPosition): Double =
        position.marginUsed + position.unrealisedPnl

    fun positionCost(position: LeveragedPosition): Double = position.marginUsed

    // --- Metals ----------------------------------------------------------------------------

    fun metalValue(holding: MetalHolding): Double = holding.grams * holding.currentPricePerGram

    fun metalCost(holding: MetalHolding): Double = holding.grams * holding.avgBuyPricePerGram

    // --- Wealth ----------------------------------------------------------------------------

    fun wealthValue(investment: WealthInvestment): Double = investment.currentValue

    fun wealthCost(investment: WealthInvestment): Double = investment.amountInvested

    // --- Stocks ----------------------------------------------------------------------------

    fun stockValue(holding: StockHolding): Double = holding.quantity * holding.lastPrice

    fun stockCost(holding: StockHolding): Double = holding.quantity * holding.avgCost

    // --- Rollups ---------------------------------------------------------------------------

    /** Percentage return on cost. Zero cost yields zero rather than infinity. */
    fun pnlPct(pnl: Double, cost: Double): Double = if (cost == 0.0) 0.0 else pnl / cost * 100.0

    fun <T> summarise(
        category: AssetCategory,
        holdings: List<T>,
        value: (T) -> Double,
        cost: (T) -> Double,
    ): CategorySummary {
        val totalValue = holdings.sumOf(value)
        val totalCost = holdings.sumOf(cost)
        val pnl = totalValue - totalCost
        return CategorySummary(
            category = category,
            currentValue = totalValue,
            totalPnl = pnl,
            pnlPct = pnlPct(pnl, totalCost),
            holdingCount = holdings.size,
        )
    }

    fun marginStatus(
        availableBalance: Double,
        totalHoldingsValue: Double,
        marginUsed: Double,
    ): MarginStatus {
        val equity = availableBalance + totalHoldingsValue
        return MarginStatus(
            equity = equity,
            marginUsed = marginUsed,
            freeMargin = equity - marginUsed,
            // Margin level is undefined with nothing on margin; report 0 rather than infinity.
            marginLevelPct = if (marginUsed == 0.0) 0.0 else equity / marginUsed * 100.0,
        )
    }
}

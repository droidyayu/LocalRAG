package com.ayushig.localrag.data.portfolio

import com.ayushig.localrag.domain.model.portfolio.Direction
import com.ayushig.localrag.domain.model.portfolio.LeveragedPosition
import com.ayushig.localrag.domain.model.portfolio.Metal
import com.ayushig.localrag.domain.model.portfolio.MetalHolding
import com.ayushig.localrag.domain.model.portfolio.RiskLevel
import com.ayushig.localrag.domain.model.portfolio.StockHolding
import com.ayushig.localrag.domain.model.portfolio.WealthInvestment
import java.time.Instant

/**
 * The invented portfolio. Raw holdings only.
 *
 * Margin and open profit or loss are computed by [PortfolioCalculations] rather than typed in, so
 * the stored fields can never drift from the prices beside them. Values are fixed, never random:
 * the assistant's answers have to be reproducible.
 */
object DummyPortfolioData {

    /** Fixed snapshot time. This drives the "as of" line and, later, a staleness badge. */
    val AS_OF: Instant = Instant.parse("2026-09-13T08:30:00Z")

    const val CURRENCY = "USD"

    const val AVAILABLE_BALANCE = 12_480.35

    val leveragedPositions: List<LeveragedPosition> = listOf(
        leveraged(
            symbol = "EURUSD",
            displayName = "Euro / US Dollar",
            direction = Direction.BUY,
            units = 100_000.0,
            leverage = 30,
            entryPrice = 1.0850,
            currentPrice = 1.0925,
            openedAt = Instant.parse("2026-08-28T09:15:00Z"),
        ),
        leveraged(
            symbol = "GBPUSD",
            displayName = "British Pound / US Dollar",
            direction = Direction.SELL,
            units = 60_000.0,
            leverage = 20,
            entryPrice = 1.2740,
            currentPrice = 1.2905,
            openedAt = Instant.parse("2026-09-02T14:40:00Z"),
        ),
        leveraged(
            symbol = "US500",
            displayName = "US 500 Index",
            direction = Direction.BUY,
            units = 20.0,
            leverage = 10,
            entryPrice = 5_240.00,
            currentPrice = 5_318.50,
            openedAt = Instant.parse("2026-07-19T13:05:00Z"),
        ),
        leveraged(
            symbol = "XAUUSD",
            displayName = "Gold Spot / US Dollar",
            direction = Direction.BUY,
            units = 30.0,
            leverage = 20,
            entryPrice = 2_395.00,
            currentPrice = 2_352.40,
            openedAt = Instant.parse("2026-09-08T07:50:00Z"),
        ),
    )

    val metalHoldings: List<MetalHolding> = listOf(
        MetalHolding(
            metal = Metal.GOLD,
            grams = 250.0,
            avgBuyPricePerGram = 71.20,
            currentPricePerGram = 75.60,
            storageLocation = "Vault - Zurich",
            purity = "99.99%",
        ),
        MetalHolding(
            metal = Metal.SILVER,
            grams = 4_000.0,
            avgBuyPricePerGram = 0.82,
            currentPricePerGram = 0.94,
            storageLocation = "Vault - Singapore",
            purity = "99.9%",
        ),
    )

    val wealthInvestments: List<WealthInvestment> = listOf(
        WealthInvestment(
            id = "wealth-balanced-growth",
            name = "Balanced Growth Portfolio",
            riskLevel = RiskLevel.MODERATE,
            amountInvested = 25_000.00,
            currentValue = 27_850.00,
            annualisedReturnPct = 8.4,
            startedAt = Instant.parse("2024-03-11T00:00:00Z"),
        ),
        WealthInvestment(
            id = "wealth-capital-preservation",
            name = "Capital Preservation Fund",
            riskLevel = RiskLevel.LOW,
            amountInvested = 18_000.00,
            currentValue = 18_640.00,
            annualisedReturnPct = 3.2,
            startedAt = Instant.parse("2023-11-02T00:00:00Z"),
        ),
        WealthInvestment(
            id = "wealth-emerging-tech",
            name = "Emerging Tech Opportunities",
            riskLevel = RiskLevel.HIGH,
            amountInvested = 12_000.00,
            currentValue = 10_940.00,
            annualisedReturnPct = -6.1,
            startedAt = Instant.parse("2025-05-20T00:00:00Z"),
        ),
    )

    val stockHoldings: List<StockHolding> = listOf(
        StockHolding("AAPL", "Apple Inc.", 120, 178.40, 213.55, 0.84),
        StockHolding("MSFT", "Microsoft Corporation", 40, 402.10, 438.20, -0.42),
        StockHolding("NVDA", "NVIDIA Corporation", 60, 118.75, 104.30, -2.15),
        StockHolding("KO", "The Coca-Cola Company", 200, 61.20, 63.85, 0.31),
        StockHolding("TSLA", "Tesla, Inc.", 25, 248.90, 221.15, -1.08),
    )

    private fun leveraged(
        symbol: String,
        displayName: String,
        direction: Direction,
        units: Double,
        leverage: Int,
        entryPrice: Double,
        currentPrice: Double,
        openedAt: Instant,
    ) = LeveragedPosition(
        symbol = symbol,
        displayName = displayName,
        direction = direction,
        units = units,
        leverage = leverage,
        entryPrice = entryPrice,
        currentPrice = currentPrice,
        marginUsed = PortfolioCalculations.marginFor(units, entryPrice, leverage),
        unrealisedPnl = PortfolioCalculations.unrealisedPnlFor(
            direction = direction,
            units = units,
            entryPrice = entryPrice,
            currentPrice = currentPrice,
        ),
        openedAt = openedAt,
    )
}

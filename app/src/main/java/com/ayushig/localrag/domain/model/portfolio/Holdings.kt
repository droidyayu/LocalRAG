package com.ayushig.localrag.domain.model.portfolio

import java.time.Instant

/**
 * A margin position. [marginUsed] is the collateral tied up; [unrealisedPnl] is the open profit or
 * loss implied by [entryPrice], [currentPrice], [units] and [direction].
 */
data class LeveragedPosition(
    val symbol: String,
    val displayName: String,
    val direction: Direction,
    val units: Double,
    val leverage: Int,
    val entryPrice: Double,
    val currentPrice: Double,
    val marginUsed: Double,
    val unrealisedPnl: Double,
    val openedAt: Instant,
)

data class MetalHolding(
    val metal: Metal,
    val grams: Double,
    val avgBuyPricePerGram: Double,
    val currentPricePerGram: Double,
    val storageLocation: String,
    val purity: String,
)

data class WealthInvestment(
    val id: String,
    val name: String,
    val riskLevel: RiskLevel,
    val amountInvested: Double,
    val currentValue: Double,
    val annualisedReturnPct: Double,
    val startedAt: Instant,
)

data class StockHolding(
    val symbol: String,
    val companyName: String,
    val quantity: Int,
    val avgCost: Double,
    val lastPrice: Double,
    val dayChangePct: Double,
)

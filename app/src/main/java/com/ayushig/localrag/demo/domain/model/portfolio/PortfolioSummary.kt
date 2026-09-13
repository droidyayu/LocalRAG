package com.ayushig.localrag.demo.domain.model.portfolio

import java.time.Instant

data class CategorySummary(
    val category: AssetCategory,
    val currentValue: Double,
    val totalPnl: Double,
    val pnlPct: Double,
    val holdingCount: Int,
)

data class PortfolioSummary(
    val totalValue: Double,
    val totalPnl: Double,
    val totalPnlPct: Double,
    val availableBalance: Double,
    val currency: String,
    val categories: List<CategorySummary>,
    val asOf: Instant,
)

data class MarginStatus(
    val equity: Double,
    val marginUsed: Double,
    val freeMargin: Double,
    val marginLevelPct: Double,
)

package com.ayushig.localrag.domain.model.portfolio

/** The four asset types the portfolio is split across. */
enum class AssetCategory(val displayName: String) {
    LEVERAGED("Leveraged Trading"),
    METALS("Physical Gold/Silver"),
    WEALTH("Wealth Investment"),
    STOCKS("Stocks"),
}

enum class Direction { BUY, SELL }

enum class Metal(val displayName: String) {
    GOLD("Gold"),
    SILVER("Silver"),
}

enum class RiskLevel(val displayName: String) {
    LOW("Low"),
    MODERATE("Moderate"),
    HIGH("High"),
}

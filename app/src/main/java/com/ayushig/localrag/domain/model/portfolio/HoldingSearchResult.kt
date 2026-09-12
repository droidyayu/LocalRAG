package com.ayushig.localrag.domain.model.portfolio

/**
 * A single holding matched by name or symbol. Each case carries its [category] so a caller can
 * render or describe the match without a second lookup.
 */
sealed interface HoldingSearchResult {
    val category: AssetCategory

    data class Leveraged(val position: LeveragedPosition) : HoldingSearchResult {
        override val category: AssetCategory = AssetCategory.LEVERAGED
    }

    data class MetalHoldingMatch(val holding: MetalHolding) : HoldingSearchResult {
        override val category: AssetCategory = AssetCategory.METALS
    }

    data class Wealth(val investment: WealthInvestment) : HoldingSearchResult {
        override val category: AssetCategory = AssetCategory.WEALTH
    }

    data class Stock(val holding: StockHolding) : HoldingSearchResult {
        override val category: AssetCategory = AssetCategory.STOCKS
    }
}

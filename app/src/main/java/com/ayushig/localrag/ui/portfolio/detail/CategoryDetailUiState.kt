package com.ayushig.localrag.ui.portfolio.detail

import androidx.compose.runtime.Immutable
import com.ayushig.localrag.domain.model.portfolio.AssetCategory
import com.ayushig.localrag.domain.model.portfolio.CategorySummary
import com.ayushig.localrag.domain.model.portfolio.LeveragedPosition
import com.ayushig.localrag.domain.model.portfolio.MetalHolding
import com.ayushig.localrag.domain.model.portfolio.StockHolding
import com.ayushig.localrag.domain.model.portfolio.WealthInvestment

/** The holdings for one category. Exactly one list is populated, matching [CategorySummary.category]. */
@Immutable
sealed interface CategoryHoldings {
    data class Leveraged(val positions: List<LeveragedPosition>) : CategoryHoldings

    data class Metals(val holdings: List<MetalHolding>) : CategoryHoldings

    data class Wealth(val investments: List<WealthInvestment>) : CategoryHoldings

    data class Stocks(val holdings: List<StockHolding>) : CategoryHoldings
}

@Immutable
sealed interface CategoryDetailUiState {
    val category: AssetCategory

    data class Loading(override val category: AssetCategory) : CategoryDetailUiState

    data class Content(
        override val category: AssetCategory,
        val summary: CategorySummary,
        val holdings: CategoryHoldings,
    ) : CategoryDetailUiState

    data class Error(
        override val category: AssetCategory,
        val message: String,
    ) : CategoryDetailUiState
}

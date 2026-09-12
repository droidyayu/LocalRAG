package com.ayushig.localrag.ui.portfolio.home

import androidx.compose.runtime.Immutable
import com.ayushig.localrag.domain.model.portfolio.MarginStatus
import com.ayushig.localrag.domain.model.portfolio.PortfolioSummary

@Immutable
sealed interface PortfolioHomeUiState {
    data object Loading : PortfolioHomeUiState

    data class Content(
        val summary: PortfolioSummary,
        val margin: MarginStatus,
        val isRefreshing: Boolean = false,
    ) : PortfolioHomeUiState

    data class Error(val message: String) : PortfolioHomeUiState
}

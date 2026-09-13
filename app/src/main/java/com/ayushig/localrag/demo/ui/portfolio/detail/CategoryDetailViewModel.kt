package com.ayushig.localrag.demo.ui.portfolio.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayushig.localrag.demo.domain.model.portfolio.AssetCategory
import com.ayushig.localrag.demo.domain.usecase.portfolio.GetCategorySummaryUseCase
import com.ayushig.localrag.demo.domain.usecase.portfolio.GetLeveragedPositionsUseCase
import com.ayushig.localrag.demo.domain.usecase.portfolio.GetMetalHoldingsUseCase
import com.ayushig.localrag.demo.domain.usecase.portfolio.GetStockHoldingsUseCase
import com.ayushig.localrag.demo.domain.usecase.portfolio.GetWealthInvestmentsUseCase
import com.ayushig.localrag.demo.ui.navigation.CategoryDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class CategoryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getCategorySummary: GetCategorySummaryUseCase,
    private val getLeveragedPositions: GetLeveragedPositionsUseCase,
    private val getMetalHoldings: GetMetalHoldingsUseCase,
    private val getWealthInvestments: GetWealthInvestmentsUseCase,
    private val getStockHoldings: GetStockHoldingsUseCase,
) : ViewModel() {

    private val category: AssetCategory =
        AssetCategory.valueOf(checkNotNull(savedStateHandle[CategoryDetailRoute.ARG_CATEGORY]))

    private val _uiState =
        MutableStateFlow<CategoryDetailUiState>(CategoryDetailUiState.Loading(category))
    val uiState: StateFlow<CategoryDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun retry() {
        _uiState.value = CategoryDetailUiState.Loading(category)
        load()
    }

    private fun load() {
        viewModelScope.launch {
            runCatching {
                // Independent reads, so pay 300 ms once rather than twice.
                coroutineScope {
                    val summary = async { getCategorySummary(category) }
                    val holdings = async { holdingsFor(category) }
                    summary.await() to holdings.await()
                }
            }
                .onSuccess { (summary, holdings) ->
                    _uiState.value = CategoryDetailUiState.Content(category, summary, holdings)
                }
                .onFailure { throwable ->
                    _uiState.value = CategoryDetailUiState.Error(
                        category,
                        throwable.message ?: "Could not load these holdings.",
                    )
                }
        }
    }

    private suspend fun holdingsFor(category: AssetCategory): CategoryHoldings = when (category) {
        AssetCategory.LEVERAGED -> CategoryHoldings.Leveraged(getLeveragedPositions())
        AssetCategory.METALS -> CategoryHoldings.Metals(getMetalHoldings())
        AssetCategory.WEALTH -> CategoryHoldings.Wealth(getWealthInvestments())
        AssetCategory.STOCKS -> CategoryHoldings.Stocks(getStockHoldings())
    }
}
